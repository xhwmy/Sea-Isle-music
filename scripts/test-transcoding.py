"""Exercise real Navidrome + FFmpeg in an isolated, localhost-only library.

Temporary credentials never leave this process or its private test database.
Production accounts and music are not modified.
"""
import argparse
import hashlib
import json
import pathlib
import secrets
import shutil
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--navidrome', required=True)
    parser.add_argument('--ffmpeg', required=True)
    parser.add_argument('--song', required=True)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent.parent / '.qa' / ('transcoding-' + str(int(time.time())))
    library = root / 'music'
    library.mkdir(parents=True)
    source = pathlib.Path(args.song).resolve()
    shutil.copy2(source, library / source.name)
    results = []
    # Reproduce the missing-tool failure, then compare the configured encoder.
    for working in (False, True):
        run = root / ('working' if working else 'missing-encoder')
        run.mkdir()
        data = run / 'data'
        data.mkdir()
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        encoder = pathlib.Path(args.ffmpeg).resolve() if working else run / 'absent-ffmpeg.exe'
        config = run / 'navidrome.toml'
        config.write_text(f"MusicFolder = '{library}'\nDataFolder = '{data}'\nAddress = '127.0.0.1'\nPort = {port}\nFFmpegPath = '{encoder}'\nScanSchedule = '@every 1m'\n", encoding='utf-8')
        password = secrets.token_urlsafe(32)
        base = f'http://127.0.0.1:{port}'
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

        def api(method, **parameters):
            salt = secrets.token_hex(12)
            query = dict(u='qa', t=hashlib.md5((password + salt).encode()).hexdigest(),
                         s=salt, v='1.16.1', c='SeaShelf-QA', f='xml', **parameters)
            return base + '/rest/' + method + '.view?' + urllib.parse.urlencode(query)

        with (run / 'server.log').open('wb') as log:
            process = subprocess.Popen([str(pathlib.Path(args.navidrome).resolve()), '-c', str(config)],
                                       stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                for _ in range(60):
                    if process.poll() is not None:
                        raise RuntimeError('Test server exited; inspect local QA log')
                    try:
                        with opener.open(base + '/ping', timeout=1):
                            break
                    except OSError:
                        time.sleep(.25)
                request = urllib.request.Request(base + '/auth/createAdmin',
                    data=json.dumps(dict(username='qa', name='QA', password=password)).encode(),
                    headers={'Content-Type': 'application/json'})
                with opener.open(request, timeout=10) as response:
                    response.read()
                with opener.open(api('startScan'), timeout=10) as response:
                    response.read()
                song = None
                for _ in range(60):
                    with opener.open(api('getRandomSongs', size='1'), timeout=5) as response:
                        xml = ET.fromstring(response.read())
                    song = next((n for n in xml.iter() if n.tag.split('}')[-1] == 'song'), None)
                    if song is not None:
                        break
                    time.sleep(.25)
                if song is None:
                    raise RuntimeError('Test song was not scanned')
                begin = time.perf_counter()
                with opener.open(api('stream', id=song.attrib['id'], format='mp3', maxBitRate='192'), timeout=30) as response:
                    chunk = response.read(1024)
                    first_ms = round((time.perf_counter() - begin) * 1000)
                    body = chunk + response.read()
                    content_type = response.headers.get('Content-Type')
                    content_length = response.headers.get('Content-Length')
                item = dict(encoder_available=working, content_type=content_type,
                            first_audio_ms=first_ms, bytes=len(body), original_bytes=source.stat().st_size,
                            cold_content_length=content_length)
                if working:
                    assert content_type.startswith('audio/'), 'Configured encoder did not return audio'
                    output = run / 'stream.mp3'
                    output.write_bytes(body)
                    checked = subprocess.run([str(encoder), '-v', 'error', '-i', str(output), '-f', 'null', '-'],
                                             capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW, timeout=30)
                    assert checked.returncode == 0, 'Transcoded audio failed full decode'
                    assert len(body) < source.stat().st_size, 'No size reduction'
                    request = urllib.request.Request(api('stream', id=song.attrib['id'], format='raw'),
                                                     headers={'Range': 'bytes=0-1023'})
                    with opener.open(request, timeout=10) as response:
                        assert response.status == 206 and len(response.read()) == 1024
                    item['decode_ok'] = True
                    item['original_range_ok'] = True
                    request = urllib.request.Request(api('stream', id=song.attrib['id'], format='mp3', maxBitRate='192'),
                                                     headers={'Range': 'bytes=1024-2047'})
                    with opener.open(request, timeout=10) as response:
                        item['cached_range_status'] = response.status
                        partial = response.read()
                        item['cached_range_bytes'] = len(partial)
                        assert response.status == 206 and partial == body[1024:2048], 'Cached MP3 range seek failed'
                else:
                    assert 'xml' in content_type, 'Expected missing-encoder failure'
                    xml = ET.fromstring(body)
                    assert xml.attrib['status'] == 'failed'
                    item['reproduced_server_error'] = True
                results.append(item)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
    print(json.dumps(results, ensure_ascii=False, indent=2))
    (root / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
