"""R private-library installation policy checks; require an explicit test Rscript."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RSCRIPT = os.environ.get("JLINALG_HARMONY_RSCRIPT")
SCRIPT = Path(__file__).with_name("install_harmony_dependencies.R")


@unittest.skipUnless(RSCRIPT, "Set JLINALG_HARMONY_RSCRIPT for real R installation-policy checks")
class HarmonyRInstallationPolicyTest(unittest.TestCase):
    def invoke(self, root, permission):
        result = subprocess.run([RSCRIPT, "--vanilla", str(SCRIPT), str(root / "library"), str(root / "report"), permission],
                                capture_output=True, text=True, timeout=60)
        self.assertIn(result.returncode, (0, 3), result.stdout + result.stderr)
        text = (root / "report/dependency-status.yaml").read_text(encoding="utf-8")
        if result.returncode == 3:
            self.assertIn("Required action", (root / "report/WARNINGS.md").read_text(encoding="utf-8"))
        else:
            self.assertIn("available-not-yet-analysis-validated", text)
        return text

    def test_forbidden_install_skips_without_creating_library(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            text = self.invoke(root, "forbid")
            self.assertTrue("skipped-install-not-permitted" in text or "available-not-yet-analysis-validated" in text)
            self.assertFalse((root / "library").exists())

    def test_unmanaged_library_is_preserved_even_when_installs_are_authorized(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / "library").mkdir()
            sentinel = root / "library/user-file.txt"; sentinel.write_text("keep")
            text = self.invoke(root, "allow")
            self.assertTrue("skipped-unmanaged-library" in text or "available-not-yet-analysis-validated" in text)
            self.assertEqual("keep", sentinel.read_text())


if __name__ == "__main__":
    unittest.main()
