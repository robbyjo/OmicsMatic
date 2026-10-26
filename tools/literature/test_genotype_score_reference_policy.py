"""Unavailable or changed validation references must skip before any Java call."""
from pathlib import Path
import json
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class GenotypeScoreReferencePolicyTest(unittest.TestCase):
    def check_reference(self, changed):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            jar = directory / "candidate.jar"
            jar.write_bytes(b"not launched: reference admission must happen first")
            reference = directory / "reference.exe"
            if changed:
                reference.write_bytes(b"changed untrusted reference, must never execute")
            work = directory / "validation"
            result = subprocess.run(
                [sys.executable, "-X", "utf8", str(ROOT / "tools/literature/verify_genotype_score.py"),
                 "--jar", str(jar), "--java", str(directory / "must-not-run-java.exe"),
                 "--plink1", str(reference), "--plink2", str(directory / "absent-plink2.exe"),
                 "--work", str(work)],
                capture_output=True, timeout=30, check=False)
            self.assertEqual(result.returncode, 3, result.stderr.decode(errors="replace"))
            proof = json.loads((work / "verification.json").read_text(encoding="utf-8"))
            self.assertEqual(proof["status"], "skipped-reference-dependency-unavailable")
            self.assertEqual(proof["java_calls"], [])
            self.assertEqual(proof["original_calls"], [])
            warning = (work / "WARNINGS.md").read_text(encoding="utf-8")
            self.assertTrue(warning.startswith("# ORIGINAL REFERENCE VALIDATION SKIPPED"))
            self.assertIn("Supply the exact permitted official reference binary", warning)
            self.assertIn("--plink1 PATH", warning)
            self.assertIn("Native genotype-score itself needs only Java", warning)
            self.assertFalse(any(work.glob("**/results.tsv")))

    def test_missing_original_skips_with_recovery(self):
        self.check_reference(False)

    def test_changed_original_is_never_launched(self):
        self.check_reference(True)


if __name__ == "__main__":
    unittest.main()
