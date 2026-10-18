"""Import matching KuGou KRC lyric cache into sidecar LRC files for Navidrome."""

import argparse
import re
import unicodedata
import zlib
from dataclasses import dataclass
from pathlib import Path


KEY = bytes([0x40, 0x47, 0x61, 0x77, 0x5E, 0x32, 0x74, 0x47,
             0x51, 0x36, 0x31, 0x2D, 0xCE, 0xD2, 0x6E, 0x69])
CACHE_SUFFIX = re.compile(r'-[0-9a-f]{32}-\d+-\d+$', re.IGNORECASE)
TIMED_LINE = re.compile(r'^\[(\d+),(\d+)\](.*)$')
WORD_TIME = re.compile(r'<\d+,\d+,\d+>')
OFFSET = re.compile(r'^\[offset:([+-]?\d+)\]$', re.IGNORECASE)
MUSIC_EXTENSIONS = {'.flac', '.mp3', '.ogg'}


def parse_krc(data: bytes) -> str:
    if not data.startswith(b'krc1'):
        raise ValueError('not a KRC file')
    decoded = bytes(value ^ KEY[index % len(KEY)] for index, value in enumerate(data[4:]))
    unpacker = zlib.decompressobj()
    content = unpacker.decompress(decoded, 2_000_000)
    if unpacker.unconsumed_tail or not unpacker.eof:
        raise ValueError('KRC data incomplete or too large')
    source = content.decode('utf-8-sig')
    offset = 0
    for line in source.splitlines():
        match = OFFSET.match(line)
        if match:
            offset = int(match.group(1))
            break
    result = []
    for line in source.splitlines():
        match = TIMED_LINE.match(line)
        if not match:
            continue
        text = WORD_TIME.sub('', match.group(3)).strip()
        if not text:
            continue
        total_centiseconds = max(0, (int(match.group(1)) + offset) // 10)
        minutes, rest = divmod(total_centiseconds, 6000)
        seconds, centiseconds = divmod(rest, 100)
        result.append(f'[{minutes:02d}:{seconds:02d}.{centiseconds:02d}]{text}')
    if not result:
        raise ValueError('no timed lyrics found')
    return '\n'.join(result) + '\n'


def normalized(name: str) -> str:
    name = unicodedata.normalize('NFKC', name).casefold()
    name = re.sub(r'\(\d+\)$', '', name)
    return ''.join(character for character in name if character.isalnum())


def artist_and_title(name: str) -> tuple[str, str]:
    parts = name.split(' - ', 1)
    return (parts[0], parts[1]) if len(parts) == 2 else ('', name)


def artist_matches(song_artist: str, cached_artist: str) -> bool:
    key = normalized(song_artist)
    if len(key) < 2:
        return False
    participants = re.split(r'[、,，/&;]+', cached_artist)
    return key == normalized(cached_artist) or any(key == normalized(name) for name in participants)


@dataclass
class ImportResult:
    ready: int = 0
    written: int = 0
    existing: int = 0
    unmatched: int = 0
    invalid: int = 0


def import_lyrics(music: Path, cache: Path, apply: bool = False) -> ImportResult:
    exact = {}
    titles = {}
    for file in cache.rglob('*.krc'):
        stripped = CACHE_SUFFIX.sub('', file.stem)
        if stripped == file.stem:
            continue
        artist, title = artist_and_title(stripped)
        exact.setdefault(normalized(stripped), []).append(file)
        titles.setdefault(normalized(title), []).append((artist, file))
    result = ImportResult()
    for song in music.rglob('*'):
        if not song.is_file() or song.suffix.lower() not in MUSIC_EXTENSIONS:
            continue
        destination = song.with_suffix('.lrc')
        if destination.exists():
            result.existing += 1
            continue
        candidates = exact.get(normalized(song.stem), [])
        if len(candidates) != 1:
            song_artist, song_title = artist_and_title(song.stem)
            candidates = [file for artist, file in titles.get(normalized(song_title), [])
                          if artist_matches(song_artist, artist)]
        if len(candidates) != 1:
            result.unmatched += 1
            continue
        try:
            lyrics = parse_krc(candidates[0].read_bytes())
        except (OSError, ValueError, zlib.error, UnicodeDecodeError):
            result.invalid += 1
            continue
        result.ready += 1
        if apply:
            try:
                with destination.open('x', encoding='utf-8', newline='\n') as output:
                    output.write(lyrics)
                result.written += 1
            except FileExistsError:
                result.existing += 1
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Import matching KuGou lyrics into Navidrome library')
    parser.add_argument('--music', type=Path, default=Path(__file__).resolve().parents[2] / 'Library')
    parser.add_argument('--cache', type=Path, default=Path('D:/KuGou/Lyric'))
    parser.add_argument('--apply', action='store_true', help='write LRC files; without this flag only report counts')
    args = parser.parse_args()
    result = import_lyrics(args.music, args.cache, args.apply)
    print(f'ready={result.ready} written={result.written} existing={result.existing} '
          f'unmatched={result.unmatched} invalid={result.invalid}')
