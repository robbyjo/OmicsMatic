import base64
import copy
import gzip
import json
from pathlib import Path
import tempfile
import unittest
from catalog_source_integrity import digest, reverse_delta, verify_source


class CatalogSourceIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.before = b'original\r\n'
        self.after = b'original\r\nadded\n'
        self.row = dict(path='tools/literature/render_review.py', before_sha256=digest(self.before), after_sha256=digest(self.after), before_bytes=len(self.before), edits=[dict(after_start_line=1, after_end_line=2, after_chunk_sha256=digest(b'added\n'), before_bytes=0, before_chunk_sha256=digest(b''), before_gzip_base64=base64.b64encode(gzip.compress(b'', mtime=0)).decode('ascii'))])

    def test_restores_exact_mixed_newline_bytes(self):
        self.assertEqual(reverse_delta(self.after, self.row), self.before)

    def test_current_byte_tampering_is_rejected(self):
        with self.assertRaises(ValueError):
            reverse_delta(self.after + b'x', self.row)

    def test_historical_digest_tampering_is_rejected(self):
        row = copy.deepcopy(self.row); row['before_sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            reverse_delta(self.after, row)

    def test_incorrect_decoded_length_is_rejected(self):
        row = copy.deepcopy(self.row); row['edits'][0]['before_bytes'] = 1
        with self.assertRaises(ValueError):
            reverse_delta(self.after, row)

    def test_scientific_source_cannot_use_even_an_exact_inverse(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); name = 'ScientificMethod.java'; (root/name).write_bytes(self.after)
            with self.assertRaisesRegex(ValueError, 'scientific/unreviewed'):
                verify_source(root, root, name, digest(self.before))

    def test_allowed_source_is_verified_without_rewriting_it(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); path = root/self.row['path']; path.parent.mkdir(parents=True); path.write_bytes(self.after)
            (root/'test-shared-source-compatibility.json').write_text(json.dumps(dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', paths=[self.row])), encoding='utf-8')
            verify_source(root, root, self.row['path'], digest(self.before))
            self.assertEqual(path.read_bytes(), self.after)


if __name__ == '__main__':
    unittest.main()
