# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import install_shapeit as setup


class ShapeitSetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_install_permission_is_required_before_any_host_or_file_mutation(self):
        with self.assertRaisesRegex(setup.Unavailable, 'allow-install'):
            setup.install(self.root / 'home', self.root / 'missing.json', False)
        self.assertFalse((self.root / 'home').exists())

    def test_duplicate_locator_json_is_rejected(self):
        path = self.root / 'duplicate.json'
        path.write_text('{"bin/phase_common":"a","bin/phase_common":"b"}')
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            setup.read_json(path)

    def test_oversized_metadata_is_not_read_as_a_partial_manifest(self):
        path = self.root / 'large.json'
        path.write_bytes(b' ' * 1048577)
        with self.assertRaisesRegex(ValueError, 'byte capacity'):
            setup.read_json(path)

    def test_invalid_locator_values_have_actionable_errors_before_creating_a_home(self):
        lock = setup.read_json(setup.LOCK_PATH)
        actual_read = Path.read_text
        def read(path, *args, **kwargs):
            if str(path).replace('\\', '/') == '/proc/cpuinfo':
                return 'flags : avx2 fma\n'
            return actual_read(path, *args, **kwargs)
        for invalid in [None, 123, '', '\0bad']:
            with self.subTest(value=invalid):
                path = self.root / 'locators.json'
                values = {name: 'unused' for name in lock['files']}
                values['bin/phase_common'] = invalid
                path.write_text(json.dumps(values))
                with patch.object(setup.platform, 'system', return_value='Linux'), patch.object(setup.platform, 'machine', return_value='x86_64'), patch.object(Path, 'read_text', read):
                    with self.assertRaisesRegex(ValueError, 'path string'):
                        setup.install(self.root / 'home', path, True)
                self.assertFalse((self.root / 'home').exists())

    def test_report_write_failure_is_prominent_and_not_success(self):
        stdout, stderr = io.StringIO(), io.StringIO()
        args = ['install_shapeit.py', '--home', str(self.root / 'home'), '--source-manifest', str(self.root / 'missing.json'), '--report', str(self.root / 'absent/report.json')]
        with patch.object(setup.sys, 'argv', args), contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            self.assertEqual(setup.main(), 2)
        self.assertIn('FAIL2-report-write-failed', stderr.getvalue())
        self.assertIn('fresh writable report path', stderr.getvalue())
        self.assertFalse((self.root / 'home').exists())

    def test_permission_skip_is_recorded_with_exact_recovery_and_no_analysis_claim(self):
        report = self.root / 'report.json'
        args = ['install_shapeit.py', '--home', str(self.root / 'home'), '--source-manifest', str(self.root / 'missing.json'), '--report', str(report)]
        with patch.object(setup.sys, 'argv', args), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(setup.main(), 3)
        value = json.loads(report.read_text())
        self.assertEqual(value['status'], 'SKIP3-runtime-unavailable')
        self.assertFalse(value['scientific_analysis_admitted'])
        self.assertIn('does not download', value['required_action'])
        self.assertIn('skip the analysis', value['required_action'])

    def test_existing_report_is_preserved_and_install_is_not_called(self):
        report = self.root / 'report.json'
        report.write_text('keep')
        args = ['install_shapeit.py', '--home', str(self.root / 'home'), '--source-manifest', str(self.root / 'missing.json'), '--report', str(report), '--allow-install']
        with patch.object(setup.sys, 'argv', args), patch.object(setup, 'install') as install, contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                setup.main()
        install.assert_not_called()
        self.assertEqual(report.read_text(), 'keep')


if __name__ == '__main__':
    unittest.main()
