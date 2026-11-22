"""The install-forbidden and unmanaged-environment paths must remain inert."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import venv

INSTALLER = Path(__file__).with_name("install_cell_dependencies.py")


class DependencyPolicyTest(unittest.TestCase):
    def test_already_present_distribution_is_available_when_installation_is_forbidden(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            environment, report = root / "environment", root / "report"
            venv.EnvBuilder(with_pip=False).create(environment)
            python = environment / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
            site = Path(subprocess.check_output([str(python), "-I", "-c", "import sysconfig; print(sysconfig.get_path('purelib'))"], text=True).strip())
            # Metadata fixtures test availability policy only, not scientific runtime imports.
            for name, version in [("cutadapt", "5.2"), ("multiqc", "1.35")]:
                metadata = site / (name + "-" + version + ".dist-info")
                metadata.mkdir()
                (metadata / "METADATA").write_text("Metadata-Version: 2.1\nName: " + name + "\nVersion: " + version + "\n")
            process = subprocess.run([sys.executable, str(INSTALLER), "--profile", "sequence", "--environment", str(environment),
                                      "--report", str(report)], capture_output=True, text=True)
            self.assertEqual(0, process.returncode, process.stderr)
            result = json.loads((report / "dependency-status.json").read_text())
            self.assertTrue(all(r["status"] == "available-not-yet-analysis-validated" for r in result["records"]))
            self.assertFalse(list(report.glob("*-install.log")))
            self.assertFalse((environment / ".jlinalg-sequence-tools.json").exists())

    def test_forbidden_installation_reports_skips_without_creating_environment(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            environment, report = root / "environment", root / "report"
            process = subprocess.run([sys.executable, str(INSTALLER), "--environment", str(environment),
                                      "--report", str(report)], capture_output=True, text=True)
            self.assertEqual(3, process.returncode, process.stderr)
            self.assertFalse(environment.exists())
            result = json.loads((report / "dependency-status.json").read_text())
            self.assertEqual(4, len(result["records"]))
            self.assertTrue(all(r["status"] == "skipped-install-not-permitted" for r in result["records"]))
            self.assertIn("What to do", (report / "WARNINGS.md").read_text())

    def test_unmanaged_environment_is_preserved_and_requires_a_new_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            environment, report = root / "environment", root / "report"
            environment.mkdir()
            sentinel = environment / "existing-user-file"
            sentinel.write_text("retained")
            process = subprocess.run([sys.executable, str(INSTALLER), "--allow-install", "--environment",
                                      str(environment), "--report", str(report)], capture_output=True, text=True)
            self.assertEqual(3, process.returncode, process.stderr)
            self.assertEqual("retained", sentinel.read_text())
            self.assertEqual([sentinel], list(environment.iterdir()))
            result = json.loads((report / "dependency-status.json").read_text())
            self.assertTrue(all(r["status"] == "skipped-unmanaged-environment" for r in result["records"]))


if __name__ == "__main__":
    unittest.main()
