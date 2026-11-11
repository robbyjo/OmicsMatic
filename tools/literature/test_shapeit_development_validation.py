"""Real retained evidence must reject changed source, corruption and false scope."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from shapeit_development_validation import ROOT, DATA, verify


class ShapeitDevelopmentValidationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='omicsmatic-shapeit-proof-')
        self.root = Path(self.temporary.name).resolve()
        assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve())
        self.data = self.root / 'docs/literature/omics-2023-2026'
        self.data.mkdir(parents=True)
        proof = json.loads((DATA / 'shapeit-development-validation.json').read_bytes())
        for item in proof['source_evidence']:
            destination = self.root / item['path']
            assert destination.resolve().is_relative_to(self.root)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / item['path'], destination)
        shutil.copyfile(DATA / proof['native_controls']['path'], self.data / proof['native_controls']['path'])
        self.proof = self.data / 'shapeit-development-validation.json'
        shutil.copyfile(DATA / self.proof.name, self.proof)
        self.record = proof

    def tearDown(self):
        assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve())
        self.temporary.cleanup()

    def check(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return verify(self.root, self.data)

    def test_complete_retained_native_and_original_populations_verify(self):
        report = self.check()
        self.assertEqual((report['original_reader_cells'], report['original_cohort_output_cells']), (800, 800))
        self.assertFalse(report['public_upstream_adapter_available'])

    def test_changed_scientific_source_cannot_reuse_the_frozen_validation(self):
        path = self.root / 'src/main/java/org/jlinalg/genetics/ShapeitGenotypes.java'
        path.write_bytes(path.read_bytes() + b'\n// changed source\n')
        with self.assertRaisesRegex(ValueError, 'Changed scientific/unreviewed source'):
            self.check()

    def test_corrupted_control_archive_is_rejected(self):
        path = self.data / self.record['native_controls']['path']
        raw = bytearray(path.read_bytes()); raw[len(raw) // 2] ^= 1; path.write_bytes(raw)
        with self.assertRaises(AssertionError):
            self.check()

    def test_foundation_cannot_be_relabelled_as_a_public_phasing_adapter(self):
        self.record['public_upstream_adapter_available'] = True
        self.proof.write_text(json.dumps(self.record), encoding='utf-8')
        with self.assertRaises(AssertionError):
            self.check()

    def test_optimized_python_cannot_disable_verification(self):
        result = subprocess.run([sys.executable, '-O', str(ROOT / 'tools/literature/shapeit_development_validation.py')],
                                cwd=ROOT, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Verification requires normal Python', result.stderr)


if __name__ == '__main__':
    unittest.main()
