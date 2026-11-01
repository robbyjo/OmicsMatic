"""Keep source-mask evidence distinct from completed phasing or binary-data validation."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from shapeit_selection_validation import ROOT,DATA,verify


class ShapeitSelectionValidationTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='omicsmatic-shapeit-selection-')
        self.root=Path(self.temporary.name).resolve();assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve())
        self.data=self.root/'docs/literature/omics-2023-2026';self.data.mkdir(parents=True)
        self.record=json.loads((DATA/'shapeit-selection-validation.json').read_bytes())
        for item in self.record['source_evidence']:
            target=self.root/item['path'];assert target.resolve().is_relative_to(self.root)
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/item['path'],target)
        self.proof=self.data/'shapeit-selection-validation.json';shutil.copyfile(DATA/self.proof.name,self.proof)
        shutil.copyfile(DATA/self.record['native_controls']['path'],self.data/self.record['native_controls']['path'])
        for compatibility in DATA.glob('*-shared-source-compatibility.json'):shutil.copyfile(compatibility,self.data/compatibility.name)

    def tearDown(self):
        assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve());self.temporary.cleanup()

    def check(self):
        with contextlib.redirect_stdout(io.StringIO()):return verify(self.root,self.data)

    def write(self):self.proof.write_text(json.dumps(self.record),encoding='utf-8')

    def test_complete_original_and_native_selection_populations_verify(self):
        result=self.check();self.assertEqual((result['original_scanner_calls'],result['complete_original_masks'],result['expected_original_failures']),(50,36,14));self.assertFalse(result['public_phasing_adapter_available'])

    def test_changed_scientific_source_cannot_reuse_the_frozen_result(self):
        path=self.root/'src/main/java/org/jlinalg/genetics/ShapeitCommonVariants.java';path.write_bytes(path.read_bytes()+b'\n// changed science\n')
        with self.assertRaisesRegex(ValueError,'Changed scientific/unreviewed source'):self.check()

    def test_corrupted_native_controls_are_rejected(self):
        path=self.data/self.record['native_controls']['path'];raw=bytearray(path.read_bytes());raw[len(raw)//2]^=1;path.write_bytes(raw)
        with self.assertRaises(AssertionError):self.check()

    def test_scanner_mask_cannot_be_relabelled_as_a_public_phaser(self):
        self.record['public_phasing_adapter_available']=True;self.write()
        with self.assertRaises(AssertionError):self.check()

    def test_original_joins_cannot_be_relabelled_as_native_join_reconstruction(self):
        self.record['native_join_reconstruction_available']=True;self.write()
        with self.assertRaises(AssertionError):self.check()

    def test_failed_source_controls_cannot_be_relabelled_as_successful(self):
        self.record['complete_scanner_results']=50;self.record['expected_original_failures']=0;self.write()
        with self.assertRaises(AssertionError):self.check()

    def test_empty_binary_controls_cannot_be_relabelled_as_genotype_validation(self):
        self.record['XCF_binary_payloads_validated']=True;self.write()
        with self.assertRaises(AssertionError):self.check()

    def test_optimized_python_cannot_disable_source_evidence_verification(self):
        result=subprocess.run([sys.executable,'-O',str(ROOT/'tools/literature/shapeit_selection_validation.py')],cwd=ROOT,capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0);self.assertIn('Verification requires normal Python',result.stderr)


if __name__=='__main__':unittest.main()
