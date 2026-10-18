"""Check real same-name LRC delivery in an isolated localhost Navidrome instance.

No production accounts are read. Copied music, temporary credentials and logs stay
in the ignored .qa directory. Only assertion results and line counts are printed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--navidrome', required=True, type=Path)
    parser.add_argument('--ffmpeg', required=True, type=Path)
    parser.add_argument('--song', required=True, type=Path)
    args = parser.parse_args()
    song = args.song.resolve(strict=True)
    lrc = song.with_suffix('.lrc')
    if not lrc.is_file():
        raise ValueError('A same-name LRC file is required')
    root = Path(__file__).resolve().parent.parent / '.qa' / ('lyrics-' + secrets.token_hex(6))
    library = root / 'music'; library.mkdir(parents=True)
    data = root / 'data'; data.mkdir()
    shutil.copy2(song, library / song.name)
    shutil.copy2(lrc, library / lrc.name)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
    config = root / 'navidrome.toml'
    config.write_text(f"MusicFolder = '{library}'\nDataFolder = '{data}'\n"
        f"Address = '127.0.0.1'\nPort = {port}\nFFmpegPath = '{args.ffmpeg.resolve()}'\n"
        "ScanSchedule = '@every 1m'\n", encoding='utf-8')
    password = secrets.token_urlsafe(32)
    base = f'http://127.0.0.1:{port}'
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def api(method, **parameters):
        salt = secrets.token_hex(12)
        query = dict(u='qa', t=hashlib.md5((password + salt).encode()).hexdigest(),
                     s=salt, v='1.16.1', c='SeaShelf-QA', f='xml', **parameters)
        with opener.open(base + '/rest/' + method + '.view?' + urllib.parse.urlencode(query), timeout=10) as response:
            xml = ET.fromstring(response.read())
        assert xml.attrib.get('status') == 'ok', 'Navidrome API rejected the test request'
        return xml

    with (root / 'server.log').open('wb') as log:
        process = subprocess.Popen([str(args.navidrome.resolve()), '-c', str(config)],
            stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            ready = False
            for _ in range(80):
                if process.poll() is not None:
                    raise RuntimeError('Isolated server exited; inspect private QA log')
                try:
                    with opener.open(base + '/ping', timeout=1):
                        ready = True; break
                except OSError:
                    time.sleep(.25)
            assert ready, 'Isolated server did not start'
            request = urllib.request.Request(base + '/auth/createAdmin',
                data=json.dumps(dict(username='qa', name='QA', password=password)).encode(),
                headers={'Content-Type': 'application/json'})
            with opener.open(request, timeout=10) as response:
                response.read()
            api('startScan')
            scanned = None
            for _ in range(80):
                scanned = next((n for n in api('getRandomSongs', size='1').iter()
                    if n.tag.split('}')[-1] == 'song'), None)
                if scanned is not None:
                    break
                time.sleep(.25)
            assert scanned is not None, 'Test audio was not scanned'
            response = api('getLyricsBySongId', id=scanned.attrib['id'])
            sections = [n for n in response.iter() if n.tag.split('}')[-1] == 'structuredLyrics'
                        and n.attrib.get('synced') == 'true']
            assert sections, 'Same-name LRC was not served as synchronized lyrics'
            lines = [n for n in sections[0] if n.tag.split('}')[-1] == 'line']
            timed = [n for n in lines if n.attrib.get('value', n.text or '').strip()]
            starts = [int(n.attrib['start']) for n in timed]
            assert len(starts) >= 2 and min(starts) >= 0, 'Invalid synchronized lyric timing'
            assert starts == sorted(starts), 'Lyrics were not delivered in time order'
            print(json.dumps(dict(same_name_lrc_served=True, synced_lines=len(starts),
                                  timing_sorted=True, production_accounts_used=False)))
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=5)


if __name__ == '__main__':
    main()
