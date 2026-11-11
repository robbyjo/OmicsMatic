import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

from rsem_development_validation import verify_historical_snapshot, verify_legacy_source_pins


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


class MetadataSourceEvolutionTest(unittest.TestCase):
    def check_metadata(self, changed_current=False, changed_old=False, extra=False, scientific=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); data = root / 'data'; data.mkdir()
            name = 'src/main/java/org/jlinalg/cli/RsemBuild.java' if scientific else 'tools/literature/quantification_catalog.py'
            source = root / name; source.parent.mkdir(parents=True); source.write_bytes(b'current metadata')
            old_pin = hashlib.sha256(b'old metadata').hexdigest(); current_pin = hashlib.sha256(source.read_bytes()).hexdigest()
            archive = root / 'metadata.zip'
            with zipfile.ZipFile(archive, 'w') as z:
                z.writestr(name, b'wrong historical metadata' if changed_old else b'old metadata')
                if extra: z.writestr('undeclared', b'extra')
            public = dict(source_evidence=[dict(path=name, sha256=current_pin)], historical_metadata_snapshot=dict(path='metadata.zip', sha256=hashlib.sha256(archive.read_bytes()).hexdigest(), source_evidence=[dict(path=name, sha256=old_pin)]))
            if changed_current: source.write_bytes(b'changed after current proof')
            verify_legacy_source_pins(root, data, [dict(path=name, sha256=old_pin)], public)

    def test_explicit_old_and_current_metadata_are_both_verified(self):
        self.check_metadata()

    def test_current_metadata_drift_is_refused(self):
        with self.assertRaises((AssertionError, ValueError)): self.check_metadata(changed_current=True)

    def test_historical_metadata_drift_is_refused_even_with_rebuilt_archive_hash(self):
        with self.assertRaises(AssertionError): self.check_metadata(changed_old=True)

    def test_extra_metadata_members_are_refused(self):
        with self.assertRaises(AssertionError): self.check_metadata(extra=True)

    def test_scientific_source_cannot_use_the_metadata_exception(self):
        with self.assertRaises(AssertionError): self.check_metadata(scientific=True)


if __name__ == '__main__':
    unittest.main()
