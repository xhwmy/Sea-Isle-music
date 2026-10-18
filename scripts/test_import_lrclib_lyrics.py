import json
import sqlite3
import tempfile
import threading
import unittest
import urllib.error
from unittest.mock import patch, MagicMock
from collections import Counter
from pathlib import Path
import import_lrclib_lyrics as lyrics


def track(root, **changes):
    values = dict(id='song-1', path=root / 'Artist - Song.flac', title='Song',
                  artist='Artist', album='Album', duration=180.0, library_root=root)
    values.update(changes)
    return lyrics.Track(**values)


def record(**changes):
    values = dict(id=1, trackName='Song', artistName='Artist', albumName='Album',
                  duration=180, instrumental=False,
                  syncedLyrics='[00:01.00]First synthetic line\n[00:02.00]Second synthetic line\n')
    values.update(changes)
    return values


class MatchingTests(unittest.TestCase):
    def test_normalization_accepts_typography_but_retains_version_identity(self):
        self.assertEqual('songlive', lyrics.normalized(' Ｓｏｎｇ（Live） '))
        self.assertNotEqual(lyrics.normalized('Song'), lyrics.normalized('Song (Live)'))

    def test_wrong_artist_edition_and_duration_are_rejected(self):
        item = track(Path('.').resolve())
        for candidate in [record(artistName='Cover Artist'), record(trackName='Song (Live)'),
                          record(duration=183), record(duration=None), record(instrumental=True)]:
            self.assertIsNone(lyrics.select_record(item, [candidate]))
        self.assertIsNotNone(lyrics.select_record(item, [record(duration=181.9)]))

    def test_exact_album_narrows_ambiguous_different_versions(self):
        item = track(Path('.').resolve())
        chosen = lyrics.select_record(item, [record(id=1, albumName='Other'), record(id=2)])
        self.assertEqual(2, chosen['id'])
        ambiguous = [record(id=1, albumName='Other'), record(id=2, albumName='Another',
                     syncedLyrics='[00:03.00]Different synthetic line\n[00:04.00]More words\n')]
        self.assertIsNone(lyrics.select_record(item, ambiguous))

    def test_duplicate_identical_lyrics_are_not_ambiguous(self):
        item = track(Path('.').resolve())
        self.assertIsNotNone(lyrics.select_record(item, [record(id=1), record(id=2)]))

    def test_duration_closeness_alone_cannot_resolve_conflicting_words(self):
        item = track(Path('.').resolve())
        candidates = [record(id=1), record(id=2, duration=181,
            syncedLyrics='[00:01.00]Conflicting synthetic words\n[00:02.00]Another line\n')]
        self.assertIsNone(lyrics.select_record(item, candidates))

    def test_only_real_timed_lyrics_and_plausible_timing_are_imported(self):
        self.assertFalse(lyrics.valid_lrc('Plain synthetic words', 180))
        self.assertFalse(lyrics.valid_lrc('[00:60.00]Invalid\n[00:62.00]Invalid', 180))
        self.assertFalse(lyrics.valid_lrc('[00:01.00]One\n[10:00.00]Wrong version', 180))
        self.assertTrue(lyrics.valid_lrc('[offset:100]\n[00:01.2][00:02.345]Synthetic\n', 180))
        self.assertFalse(lyrics.valid_lrc('[00:01.00]One\n[00:02.00]Two\x00', 180))


class StorageTests(unittest.TestCase):
    def test_dry_run_and_exclusive_write_never_replace_existing_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); item = track(root); item.path.write_bytes(b'placeholder')
            self.assertEqual('ready', lyrics.write_sidecar(item, record(), apply=False))
            self.assertFalse(item.path.with_suffix('.lrc').exists())
            self.assertEqual('written', lyrics.write_sidecar(item, record(), apply=True))
            first = item.path.with_suffix('.lrc').read_text(encoding='utf-8')
            self.assertEqual('existing', lyrics.write_sidecar(item, record(id=2), apply=True))
            self.assertEqual(first, item.path.with_suffix('.lrc').read_text(encoding='utf-8'))

    def test_path_escape_and_same_stem_collision_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); library = root / 'library'; library.mkdir()
            outside = root / 'outside.flac'; outside.write_bytes(b'audio')
            with self.assertRaises(ValueError):
                lyrics.write_sidecar(track(library, path=outside), record(), apply=True)
            db = root / 'test.db'; connection = sqlite3.connect(db)
            connection.executescript('CREATE TABLE library(id INTEGER,path TEXT);'
                'CREATE TABLE media_file(id TEXT,path TEXT,title TEXT,artist TEXT,album TEXT,'
                'duration REAL,missing INTEGER,library_id INTEGER);')
            connection.execute('INSERT INTO library VALUES(1,?)', (str(library),))
            (library / 'same.mp3').write_bytes(b'audio'); (library / 'same.flac').write_bytes(b'audio')
            for row in [('a','same.mp3','Song','Artist','Album',180,0,1),
                        ('b','same.flac','Other Song','Artist','Album',210,0,1),
                        ('c','../outside.flac','Song','Artist','Album',180,0,1)]:
                connection.execute('INSERT INTO media_file VALUES(?,?,?,?,?,?,?,?)', row)
            connection.commit(); connection.close()
            self.assertEqual([], lyrics.load_tracks(db))


class LookupTests(unittest.TestCase):
    def test_busy_catalogue_retries_once_without_treating_failure_as_no_lyrics(self):
        with tempfile.TemporaryDirectory() as directory:
            client = lyrics.LyricsClient(directory, interval=0)
            response = MagicMock(); response.__enter__.return_value = response
            response.read.return_value = b'[]'
            busy = urllib.error.HTTPError('https://example.invalid', 503, 'Busy', {}, None)
            with patch.object(lyrics.urllib.request, 'urlopen', side_effect=[busy, response]) as request, \
                    patch.object(lyrics.time, 'sleep'):
                result = None
                try:
                    result = client.query('search', {'track_name': 'Song'})
                except urllib.error.HTTPError:
                    pass
            self.assertEqual([], result, 'A temporary busy response should be retried once')
            self.assertEqual(2, request.call_count)

    def test_signature_service_failure_still_checks_public_catalogue(self):
        class Client(lyrics.LyricsClient):
            def __init__(self):
                self.signature_errors = Counter(); self.lock = threading.Lock()
            def query(self, endpoint, parameters):
                if endpoint == 'get':
                    raise urllib.error.HTTPError('https://example.invalid', 503, 'Unavailable', {}, None)
                return [record()]
        client = Client(); selected = None
        try:
            selected = client.find(track(Path('.').resolve()))
        except urllib.error.HTTPError:
            pass
        self.assertIsNotNone(selected, 'A failed optional signature lookup must not skip a working catalogue')
        self.assertEqual(1, client.signature_errors[503])


if __name__ == '__main__':
    unittest.main()
