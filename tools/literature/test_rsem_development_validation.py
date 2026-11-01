import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from rsem_development_validation import verify_historical_snapshot


class HistoricalRuntimeEvidenceTest(unittest.TestCase):
    def check_archive(self, changed=None, extra=False, stale_hash=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'source.zip'
            rows = [{'path': 'source/' + str(i), 'sha256': hashlib.sha256(str(i).encode()).hexdigest()}
                    for i in range(57)]
            with zipfile.ZipFile(target, 'w') as archive:
                for i, row in enumerate(rows):
                    archive.writestr(row['path'], b'changed' if i == changed else str(i).encode())
                if extra:
                    archive.writestr('undeclared', b'extra')
            digest = hashlib.sha256(target.read_bytes()).hexdigest()
            record = dict(current_source_validation=False, source_evidence=rows,
                          historical_source_snapshot=dict(path='source.zip', sha256='0' * 64 if stale_hash else digest))
            verify_historical_snapshot(root, record)

    def test_exact_historical_population_is_accepted(self):
        self.check_archive()

    def test_altered_historical_source_is_refused_even_with_new_archive_hash(self):
        with self.assertRaises(AssertionError):
            self.check_archive(changed=7)

    def test_undeclared_archive_member_is_refused(self):
        with self.assertRaises(AssertionError):
            self.check_archive(extra=True)

    def test_changed_archive_is_refused_before_source_admission(self):
        with self.assertRaises(AssertionError):
            self.check_archive(stale_hash=True)


if __name__ == '__main__':
    unittest.main()
