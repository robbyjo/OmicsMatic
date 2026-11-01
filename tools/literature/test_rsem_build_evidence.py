import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from rsem_build_evidence import open_evidence


class SourceBuildEvidenceObjectsTest(unittest.TestCase):
    def make_bundle(self, root, data=b'alpha', object_data=None, extra=False, name='results/value', size=None, inconsistent=False):
        sha = hashlib.sha256(data).hexdigest()
        logical = {name: dict(sha256=sha, bytes=len(data) if size is None else size)}
        if inconsistent:
            logical['results/alias'] = dict(sha256=sha, bytes=len(data) + 1)
        path = root / 'evidence.zip'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('index.json', json.dumps(dict(schema='omicsmatic-rsem-source-build-controls-v1', logical_files=logical)))
            archive.writestr('objects/' + sha, data if object_data is None else object_data)
            if extra:
                archive.writestr('objects/' + '0' * 64, b'undeclared')
        return path, hashlib.sha256(path.read_bytes()).hexdigest()

    def test_exact_logical_object_is_read_without_extraction(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, sha = self.make_bundle(root)
            with open_evidence(path, sha) as (index, read):
                self.assertEqual(read('results/value'), b'alpha')
                self.assertEqual(len(index['logical_files']), 1)
            self.assertEqual(sorted(p.name for p in root.iterdir()), ['evidence.zip'])

    def test_same_length_changed_object_fails_even_with_fresh_outer_archive_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = self.make_bundle(Path(directory), object_data=b'omega')
            with self.assertRaisesRegex(ValueError, 'content differs'):
                with open_evidence(path, sha):
                    self.fail('Changed science object was accepted')

    def test_unreferenced_archive_objects_are_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = self.make_bundle(Path(directory), extra=True)
            with self.assertRaisesRegex(ValueError, 'extra/missing'):
                with open_evidence(path, sha):
                    self.fail('Undeclared object was accepted')

    def test_wrong_logical_length_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = self.make_bundle(Path(directory), size=4)
            with self.assertRaisesRegex(ValueError, 'length differs'):
                with open_evidence(path, sha):
                    self.fail('Incorrect length was accepted')

    def test_parent_path_components_are_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = self.make_bundle(Path(directory), name='../outside')
            with self.assertRaisesRegex(ValueError, 'logical RSEM evidence path'):
                with open_evidence(path, sha):
                    self.fail('Parent path was accepted')

    def test_shared_objects_must_have_consistent_lengths(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = self.make_bundle(Path(directory), inconsistent=True)
            with self.assertRaisesRegex(ValueError, 'shared RSEM evidence object size'):
                with open_evidence(path, sha):
                    self.fail('Inconsistent aliases were accepted')


if __name__ == '__main__':
    unittest.main()
