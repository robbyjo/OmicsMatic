"""Retained map evidence must reject changed science, corruption and false scope."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from shapeit_map_validation import ROOT, DATA, verify


class ShapeitMapValidationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='omicsmatic-shapeit-map-')
        self.root = Path(self.temporary.name).resolve()
        assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve())
        self.data = self.root/'docs/literature/omics-2023-2026'; self.data.mkdir(parents=True)
        self.record = json.loads((DATA/'shapeit-map-validation.json').read_bytes())
        for item in self.record['source_evidence']:
            destination = self.root/item['path']; assert destination.resolve().is_relative_to(self.root)
            destination.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(ROOT/item['path'],destination)
        self.proof = self.data/'shapeit-map-validation.json'
        shutil.copyfile(DATA/self.proof.name,self.proof)
        shutil.copyfile(DATA/self.record['native_controls']['path'],self.data/self.record['native_controls']['path'])
        for compatibility in DATA.glob('*-shared-source-compatibility.json'):
            shutil.copyfile(compatibility,self.data/compatibility.name)

    def tearDown(self):
        assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve()); self.temporary.cleanup()

    def check(self):
        with contextlib.redirect_stdout(io.StringIO()): return verify(self.root,self.data)

    def test_complete_original_and_native_map_populations_verify(self):
        result = self.check()
        self.assertEqual((result['original_map_observations'],result['complete_original_results'],result['expected_original_failures']),(127,105,22))
        self.assertFalse(result['public_phasing_adapter_available'])

    def test_changed_numerical_source_cannot_reuse_prior_evidence(self):
        path = self.root/'src/main/java/org/jlinalg/genetics/ShapeitGeneticMap.java'
        path.write_bytes(path.read_bytes()+b'\n// changed scientific source\n')
        with self.assertRaisesRegex(ValueError,'Changed scientific/unreviewed source'): self.check()

    def test_missing_or_corrupt_native_evidence_is_rejected(self):
        path = self.data/self.record['native_controls']['path']
        raw = bytearray(path.read_bytes()); raw[len(raw)//2] ^= 1; path.write_bytes(raw)
        with self.assertRaises(AssertionError): self.check()

    def test_map_foundation_cannot_be_relabelled_as_a_public_phaser(self):
        self.record['public_phasing_adapter_available'] = True
        self.proof.write_text(json.dumps(self.record),encoding='utf-8')
        with self.assertRaises(AssertionError): self.check()

    def test_map_foundation_cannot_increment_completed_tool_integrations(self):
        self.record['new_tool_integrations'] = 1
        self.proof.write_text(json.dumps(self.record),encoding='utf-8')
        with self.assertRaises(AssertionError): self.check()

    def test_contradictory_version_or_population_metadata_is_rejected(self):
        self.record['native_tests_per_JVM'] = 999
        self.proof.write_text(json.dumps(self.record),encoding='utf-8')
        with self.assertRaises(AssertionError): self.check()
        self.record['native_tests_per_JVM'] = 133
        self.record['source_profiles']['4.2.2'] = self.record['source_profiles']['4.1.3']
        self.proof.write_text(json.dumps(self.record),encoding='utf-8')
        with self.assertRaises(AssertionError): self.check()

    def test_optimized_python_cannot_disable_verification(self):
        result = subprocess.run([sys.executable,'-O',str(ROOT/'tools/literature/shapeit_map_validation.py')],cwd=ROOT,capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0); self.assertIn('Verification requires normal Python',result.stderr)


if __name__=='__main__': unittest.main()
