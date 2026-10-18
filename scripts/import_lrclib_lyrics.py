"""Import conservatively matched LRCLIB LRC sidecars; never modify audio or existing lyrics.

Only public song metadata is sent to LRCLIB. Responses/lyrics/reports stay in the
private .qa directory by default. No Navidrome account or authentication is read.
"""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import re
import sqlite3
import tempfile
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

MAX_RESPONSE = 2 * 1024 * 1024
AUDIO_EXTENSIONS = {'.flac', '.mp3', '.ogg', '.opus', '.m4a', '.wav', '.aac'}
STAMP = re.compile(r'\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?\]')
OFFSET = re.compile(r'^\[offset:([+-]?\d+)\]$', re.IGNORECASE)


def normalized(value):
    return ''.join(c for c in unicodedata.normalize('NFKC', str(value)).casefold() if c.isalnum())


@dataclass(frozen=True)
class Track:
    id: str
    path: Path
    title: str
    artist: str
    album: str
    duration: float
    library_root: Path


def valid_lrc(text, duration):
    if not isinstance(text, str) or len(text) > MAX_RESPONSE // 2 or '\x00' in text:
        return False
    offset = 0
    for line in text.splitlines():
        match = OFFSET.fullmatch(line.strip())
        if match:
            offset = int(match.group(1))
            if abs(offset) > 5000:
                return False
    times = []
    for line in text.splitlines():
        matches = list(STAMP.finditer(line))
        if not matches or matches[0].start() != 0:
            continue
        # Timestamp-looking malformed tags must not be interpreted as lyric words.
        end = matches[-1].end()
        if not line[end:].strip():
            continue
        if any(m.start() != (0 if n == 0 else matches[n - 1].end()) for n, m in enumerate(matches)):
            return False
        for match in matches:
            fraction = (match.group(3) or '').ljust(3, '0')
            milliseconds = (int(match.group(1)) * 60 + int(match.group(2))) * 1000 + int(fraction or 0) + offset
            times.append(milliseconds)
    return len(times) >= 2 and min(times) >= -5000 and max(times) <= (duration + 3) * 1000


def select_record(track, records):
    candidates = []
    for item in records:
        if not isinstance(item, dict) or item.get('instrumental'):
            continue
        try:
            duration = float(item['duration'])
        except (KeyError, ValueError, TypeError):
            continue
        if not math.isfinite(duration) or abs(duration - track.duration) > 2:
            continue
        if (normalized(item.get('trackName', '')) != normalized(track.title)
                or normalized(item.get('artistName', '')) != normalized(track.artist)):
            continue
        if valid_lrc(item.get('syncedLyrics'), track.duration):
            candidates.append(item)
    if not candidates:
        return None
    album = normalized(track.album)
    if album and album not in {'unknownalbum', 'unknown'}:
        exact = [r for r in candidates if normalized(r.get('albumName', '')) == album]
        if exact:
            candidates = exact
    candidates.sort(key=lambda r: (abs(float(r['duration']) - track.duration), str(r.get('id', ''))))
    if len(candidates) == 1:
        return candidates[0]
    bodies = {r['syncedLyrics'].replace('\r\n', '\n').strip() for r in candidates}
    if len(bodies) == 1:
        return candidates[0]
    # A slightly closer duration cannot prove which conflicting transcription is correct.
    return None


def load_tracks(database):
    database = Path(database).resolve(strict=True)
    connection = sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)
    connection.row_factory = sqlite3.Row
    try:
        libraries = {r['id']: Path(r['path']).resolve() for r in connection.execute('SELECT id,path FROM library')}
        rows = connection.execute('SELECT id,path,title,artist,album,duration,library_id '
                                  'FROM media_file WHERE missing=0').fetchall()
    finally:
        connection.close()
    by_stem = defaultdict(list)
    for row in rows:
        root = libraries.get(row['library_id'])
        if root is None:
            continue
        raw_path = root / row['path']
        path = raw_path.resolve()
        if (raw_path.is_symlink() or not path.is_relative_to(root) or not path.is_file()
                or path.suffix.lower() not in AUDIO_EXTENSIONS):
            continue
        title, artist = [(row[k] or '').strip() for k in ('title', 'artist')]
        duration = float(row['duration'] or 0)
        if (not title or not artist or not math.isfinite(duration) or duration <= 0
                or any('\ufffd' in v or '\x00' in v or len(v) > 512 for v in (title, artist))):
            continue
        item = Track(str(row['id']), path, title, artist, row['album'] or '', duration, root)
        by_stem[str(path.with_suffix('')).casefold()].append(item)
    result = []
    for tracks in by_stem.values():
        first = tracks[0]
        if any(normalized(t.title) != normalized(first.title) or normalized(t.artist) != normalized(first.artist)
               or abs(t.duration - first.duration) > 2 for t in tracks[1:]):
            continue
        result.append(first)
    return result


def write_sidecar(track, item, apply=False):
    path, root = track.path.resolve(), track.library_root.resolve()
    if not path.is_relative_to(root) or not path.is_file() or track.path.is_symlink():
        raise ValueError('Audio path is outside the selected library or unavailable')
    destination = path.with_suffix('.lrc')
    if destination.exists():
        return 'existing'
    if select_record(track, [item]) is None:
        raise ValueError('Record does not match this audio or has no valid synced lyrics')
    if not apply:
        return 'ready'
    created = False
    try:
        with destination.open('x', encoding='utf-8', newline='\n') as output:
            created = True
            output.write(item['syncedLyrics'].replace('\r\n', '\n').strip() + '\n')
    except FileExistsError:
        return 'existing'
    except BaseException:
        if created:
            destination.unlink(missing_ok=True)
        raise
    return 'written'


class LyricsClient:
    def __init__(self, cache, interval=0.5):
        self.cache = Path(cache); self.cache.mkdir(parents=True, exist_ok=True)
        self.interval = interval; self.lock = threading.Lock(); self.last_request = 0.0
        self.signature_errors = Counter()

    def query(self, endpoint, parameters):
        query = urllib.parse.urlencode(parameters)
        key = hashlib.sha256((endpoint + '?' + query).encode()).hexdigest()
        cache_file = self.cache / (key + '.json')
        if cache_file.exists() and cache_file.stat().st_size <= MAX_RESPONSE * 2:
            try:
                saved = json.loads(cache_file.read_text(encoding='utf-8'))
                if 0 <= time.time() - saved['saved_at'] < 86400:
                    return saved['data']
            except (OSError, ValueError, KeyError, TypeError):
                pass
        request = urllib.request.Request('https://lrclib.net/api/' + endpoint + '?' + query,
            headers={'User-Agent': 'SeaShelf-Lyrics/0.4.0 (https://github.com/xhwmy/Sea-Isle-music)',
                     'Accept': 'application/json'})
        attempts = 1 if endpoint == 'get' else 2
        for attempt in range(attempts):
            with self.lock:
                time.sleep(max(0, self.interval - (time.monotonic() - self.last_request)))
                self.last_request = time.monotonic()
            try:
                with urllib.request.urlopen(request, timeout=15) as response:
                    data = response.read(MAX_RESPONSE + 1)
                    if len(data) > MAX_RESPONSE:
                        raise ValueError('Lyrics response exceeds size limit')
                    result = json.loads(data)
                break
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    error.close(); result = None; break
                if error.code in (429, 500, 502, 503, 504) and attempt + 1 < attempts:
                    try:
                        delay = max(2, float(error.headers.get('Retry-After', 2)))
                    except (ValueError, TypeError):
                        delay = 2
                    if delay <= 15:
                        error.close(); time.sleep(delay); continue
                raise
        temporary = None
        try:
            with tempfile.NamedTemporaryFile('w', encoding='utf-8', dir=self.cache, delete=False) as output:
                temporary = Path(output.name)
                json.dump({'saved_at': time.time(), 'data': result}, output, ensure_ascii=False)
            temporary.replace(cache_file)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
        return result

    def find(self, track):
        parameters = dict(track_name=track.title, artist_name=track.artist,
                          album_name=track.album, duration=str(round(track.duration, 3)))
        try:
            exact = self.query('get', parameters)
        except urllib.error.HTTPError as unavailable:
            if unavailable.code not in (500, 502, 503, 504):
                raise
            # Signature lookup may depend on optional external providers. The public
            # catalogue can still work; its failure must remain a real error itself.
            with self.lock:
                self.signature_errors[unavailable.code] += 1
            exact = None
        candidate = select_record(track, [exact] if isinstance(exact, dict) else [])
        if candidate is not None:
            return candidate
        records = self.query('search', dict(track_name=track.title, artist_name=track.artist))
        return select_record(track, records if isinstance(records, list) else [])


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, default=root.parent / 'Navidrome/data/navidrome.db')
    parser.add_argument('--cache', type=Path, default=root / '.qa/lyrics-import/cache')
    parser.add_argument('--report', type=Path, default=root / '.qa/lyrics-import/report.json')
    parser.add_argument('--apply', action='store_true', help='write new LRCs; default only reports matches')
    parser.add_argument('--workers', type=int, default=3)
    args = parser.parse_args()
    tracks = load_tracks(args.database)
    client = LyricsClient(args.cache)
    counts = Counter(); entries = []

    def process(item):
        if item.path.with_suffix('.lrc').exists():
            return dict(id=item.id, status='existing')
        try:
            found = client.find(item)
            if found is None:
                return dict(id=item.id, status='unmatched')
            return dict(id=item.id, status=write_sidecar(item, found, args.apply), source_id=found.get('id'))
        except Exception as failure:
            # Status/type is sufficient; never echo lyric bodies, complete URLs or local paths.
            return dict(id=item.id, status='error', error=type(failure).__name__,
                        http_status=getattr(failure, 'code', None))

    with ThreadPoolExecutor(max_workers=max(1, min(3, args.workers))) as pool:
        for future in as_completed([pool.submit(process, item) for item in tracks]):
            result = future.result(); entries.append(result); counts[result['status']] += 1
            if len(entries) % 10 == 0 or len(entries) == len(tracks):
                print(json.dumps(dict(completed=len(entries), total=len(tracks), counts=dict(counts))), flush=True)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(dict(applied=args.apply, counts=dict(counts),
        signature_lookup_errors=dict(client.signature_errors), entries=entries), indent=2), encoding='utf-8')
    return 1 if counts['error'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
