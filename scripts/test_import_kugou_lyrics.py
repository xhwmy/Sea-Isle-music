import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from import_kugou_lyrics import import_lyrics, parse_krc


KEY = bytes([0x40, 0x47, 0x61, 0x77, 0x5E, 0x32, 0x74, 0x47,
             0x51, 0x36, 0x31, 0x2D, 0xCE, 0xD2, 0x6E, 0x69])


def fixture(text):
    compressed = zlib.compress(text.encode('utf-8'))
    return b'krc1' + bytes(value ^ KEY[index % len(KEY)]
                           for index, value in enumerate(compressed))


class ImportKugouLyricsTest(unittest.TestCase):
    def test_decodes_line_timing_and_word_tags(self):
        data = fixture('[offset:100]\n[12540,1000]<0,500,0>第一<500,500,0>句\n'
                       '[14000,1000]<0,1000,0>第二句\n')
        self.assertEqual('[00:12.64]第一句\n[00:14.10]第二句\n', parse_krc(data))

    def test_imports_exact_song_only_and_preserves_existing_lyrics(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            music, cache = root / 'Library', root / 'Lyric'
            music.mkdir()
            cache.mkdir()
            (music / '歌手 - 好歌(69).flac').write_bytes(b'')
            (music / '甲 - 同名歌.mp3').write_bytes(b'')
            existing = music / '乙 - 已有.lrc'
            existing.write_text('原有歌词', encoding='utf-8')
            (music / '乙 - 已有.flac').write_bytes(b'')
            payload = fixture('[0,1000]<0,1000,0>测试\n[1000,1000]<0,1000,0>歌词\n')
            (cache / ('歌手 - 好歌-' + 'a' * 32 + '-1-00000000.krc')).write_bytes(payload)
            (cache / ('别的歌手 - 同名歌-' + 'b' * 32 + '-2-00000000.krc')).write_bytes(payload)
            (cache / ('乙 - 已有-' + 'c' * 32 + '-3-00000000.krc')).write_bytes(payload)
            result = import_lyrics(music, cache, apply=False)
            self.assertEqual(1, result.ready)
            self.assertFalse((music / '歌手 - 好歌(69).lrc').exists())
            result = import_lyrics(music, cache, apply=True)
            self.assertEqual(1, result.written)
            self.assertIn('[00:01.00]歌词', (music / '歌手 - 好歌(69).lrc').read_text(encoding='utf-8'))
            self.assertFalse((music / '甲 - 同名歌.lrc').exists())
            self.assertEqual('原有歌词', existing.read_text(encoding='utf-8'))

    def test_matches_short_artist_as_full_collaborator_name(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            music, cache = root / 'Library', root / 'Lyric'
            music.mkdir()
            cache.mkdir()
            (music / '周深 - Rubia.flac').write_bytes(b'')
            payload = fixture('[0,1000]<0,1000,0>一句\n')
            (cache / ('周深、HOYO-MiX - Rubia-' + 'a' * 32 + '-1-00000000.krc')).write_bytes(payload)
            self.assertEqual(1, import_lyrics(music, cache, apply=False).ready)


if __name__ == '__main__':
    unittest.main()
