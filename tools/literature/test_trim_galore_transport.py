"""Exercise the real transport process with binary subprocess I/O, not tool mocks."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

RESOURCE = Path(__file__).resolve().parents[2] / "src/main/resources/org/jlinalg/omics/trim_galore_cutadapt.py"


class TrimGaloreTransportTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="trim-galore-transport-")
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def run_transport(self, body, digest=None):
        executable = Path(sys.executable)
        command = [sys.executable, str(RESOURCE), "--cutadapt", str(executable), "--expected-sha256",
                   digest or hashlib.sha256(executable.read_bytes()).hexdigest(), "--audit-dir", str(self.root / "audit"), "--", "-c", body]
        return subprocess.run(command, capture_output=True, timeout=20)

    def record(self, ordinal=0):
        path = self.root / "audit" / ("invocation-%03d.json" % ordinal)
        record = json.loads(path.read_text())
        for name, digest in record["files"].items():
            self.assertEqual(digest, hashlib.sha256((path.parent / name).read_bytes()).hexdigest())
        return record

    def test_version_crlf_becomes_lf_and_original_bytes_remain_available(self):
        result = self.run_transport("import sys;sys.stdout.buffer.write(b'5.2\\r\\n')")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"5.2\n", result.stdout)
        self.assertEqual(b"5.2\r\n", (self.root / "audit/invocation-000.stdout-raw").read_bytes())
        self.assertEqual(b"5.2\n", (self.root / "audit/invocation-000.stdout-lf").read_bytes())
        self.assertEqual(0, self.record()["exit_code"])

    def test_chunk_boundary_crlf_and_final_standalone_cr_are_preserved_correctly(self):
        original = b"A" * 65535 + b"\r\n" + b"C" * 65535 + b"\r\n" + b"\r"
        result = self.run_transport("import sys;sys.stdout.buffer.write(b'A'*65535+b'\\r\\n'+b'C'*65535+b'\\r\\n'+b'\\r')")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(original.replace(b"\r\n", b"\n"), result.stdout)
        self.assertEqual(original, (self.root / "audit/invocation-000.stdout-raw").read_bytes())
        self.record()

    def test_large_stderr_cannot_deadlock_the_legacy_stdout_first_drain(self):
        result = self.run_transport("import sys;sys.stderr.buffer.write(b'E'*262144);sys.stderr.buffer.flush();sys.stdout.buffer.write(b'@A\\r\\nAC\\r\\n+\\r\\nII\\r\\n')")
        self.assertEqual(0, result.returncode, result.stderr[:100])
        self.assertEqual(b"@A\nAC\n+\nII\n", result.stdout)
        self.assertEqual(b"E" * 262144, result.stderr)
        self.record()

    def test_child_failure_code_and_raw_error_are_never_repaired(self):
        result = self.run_transport("import sys;sys.stdout.buffer.write(b'partial\\r\\n');sys.stderr.buffer.write(b'error\\r\\n');sys.exit(7)")
        self.assertEqual(7, result.returncode)
        self.assertEqual(b"partial\n", result.stdout)
        self.assertEqual(b"error\r\n", result.stderr)
        self.assertEqual(7, self.record()["exit_code"])

    def test_changed_dependency_skips_before_launch_with_a_recovery_message(self):
        result = self.run_transport("raise RuntimeError('MUST NOT RUN')", "0" * 64)
        self.assertEqual(3, result.returncode)
        self.assertEqual(b"", result.stdout)
        self.assertIn(b"dependency is missing or changed", result.stderr)
        self.assertIn(b"Analysis skipped", result.stderr)
        self.assertFalse((self.root / "audit").exists())

    def test_separate_calls_retain_separate_complete_audit_records(self):
        for number in range(2):
            result = self.run_transport("print('call%d')" % number)
            self.assertEqual(0, result.returncode)
            self.assertEqual(("call%d\n" % number).encode(), result.stdout)
            self.record(number)
        self.assertEqual(2, len(list((self.root / "audit").glob("*.json"))))


if __name__ == "__main__":
    unittest.main()
