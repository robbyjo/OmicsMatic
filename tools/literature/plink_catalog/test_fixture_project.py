# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Scientific fixture-integrity and output-preservation controls; attempts retained."""
import json
from pathlib import Path
import subprocess
import sys
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fixture_project as subject


class FixtureProjectTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parent = subject.ROOT / 'build/plink-catalog-admission-1'
        index = 1
        while (parent / ('fixture-tests-' + str(index))).exists():
            index += 1
        cls.home = parent / ('fixture-tests-' + str(index))
        cls.home.mkdir(parents=True)

    def setUp(self):
        self.out = self.home / self._testMethodName
        self.out.mkdir()
        self.name = 'examples/known.tsv'
        self.raw = b'id\tvalue\r\na\t1\r\n'
        original = self.raw.replace(b'\r\n', b'\n')
        self.manifest = dict(schema_version=1, status=subject.STATUS,
            source_files={self.name: subject.fingerprint(self.raw)},
            original_working_files={self.name: subject.fingerprint(original)},
            restored=[dict(path=self.name, reason='captured-current-fixture-with-original-CRLF',
                working_copy_unchanged=subject.fingerprint(original), restored=subject.fingerprint(self.raw))],
            current_working_tree_modified=False, scientific_data_fields_changed=False,
            old_verifier_sources_or_expected_hashes_changed=False,
            runtimes_or_third_party_programs_bundled=False, whole_goal_complete=False)

    def archive(self, files=None):
        path = self.out / 'fixtures.zip'
        with zipfile.ZipFile(path, 'w') as z:
            for n, raw in (files or {self.name: self.raw}).items():
                z.writestr(n, raw)
        self.manifest['archive'] = subject.fingerprint(path.read_bytes())
        return path

    def test_restores_original_bytes_without_changing_source(self):
        path = self.archive()
        before = path.read_bytes()
        files = subject.read_archive(self.manifest, path)
        project = subject.restore(files, self.out / 'restored')
        self.assertEqual((project / self.name).read_bytes(), self.raw)
        self.assertEqual(path.read_bytes(), before)

    def test_changed_archive_is_rejected_before_output(self):
        path = self.archive()
        path.write_bytes(path.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'Changed fixture archive'):
            subject.read_archive(self.manifest, path)

    def test_resealed_changed_scientific_value_is_rejected(self):
        path = self.archive({self.name: self.raw.replace(b'1', b'2')})
        with self.assertRaisesRegex(ValueError, 'Changed fixture member'):
            subject.read_archive(self.manifest, path)

    def test_resealed_line_ending_claim_cannot_change_data(self):
        changed = self.raw.replace(b'1', b'2')
        path = self.archive({self.name: changed})
        self.manifest['source_files'][self.name] = subject.fingerprint(changed)
        self.manifest['restored'][0]['restored'] = subject.fingerprint(changed)
        with self.assertRaisesRegex(ValueError, 'more than recorded line endings'):
            subject.read_archive(self.manifest, path)

    def test_missing_lineage_is_rejected(self):
        path = self.archive()
        self.manifest['restored'] = []
        with self.assertRaisesRegex(ValueError, 'exact changes'):
            subject.read_archive(self.manifest, path)

    def test_arbitrary_historical_file_substitution_is_rejected(self):
        path = self.archive()
        self.manifest['restored'][0]['reason'] = 'exact-retained-validated-README'
        with self.assertRaisesRegex(ValueError, 'Unqualified fixture transformation'):
            subject.read_archive(self.manifest, path)

    def test_existing_output_is_unchanged(self):
        dest = self.out / 'existing'
        dest.mkdir()
        sentinel = dest / 'keep.bin'
        sentinel.write_bytes(b'preserve')
        with self.assertRaisesRegex(ValueError, 'Output exists'):
            subject.restore({self.name: self.raw}, dest)
        result = subprocess.run([sys.executable, '-B', str(Path(subject.__file__)),
            '--packet', str(self.out / 'missing'), '--out', str(dest)], capture_output=True)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(list(dest.iterdir()), [sentinel])
        self.assertEqual(sentinel.read_bytes(), b'preserve')

    def test_missing_packet_is_a_skip_with_recovery(self):
        dest = self.out / 'missing-packet-attempt'
        result = subprocess.run([sys.executable, '-B', str(Path(subject.__file__)),
            '--packet', str(self.out / 'missing'), '--out', str(dest)], capture_output=True)
        self.assertEqual(result.returncode, 3)
        report = json.loads((dest / 'COMPLETED.json').read_bytes())
        self.assertTrue(report['status'].startswith('SKIP3_'))
        self.assertIn('Restore the exact', report['required_action'])
        self.assertFalse(report['scientific_software_executed'])

    def test_path_and_device_aliases_are_refused(self):
        for name in ['../outside', '/absolute', 'examples/../outside', 'examples/a\\b',
                     'examples/a:b', 'examples/./a', 'examples/a.', 'examples/CON.txt']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                subject.safe_name(name)

    def test_symlink_fixture_is_refused(self):
        path = self.out / 'fixtures.zip'
        with zipfile.ZipFile(path, 'w') as z:
            item = zipfile.ZipInfo(self.name)
            item.create_system = 3
            item.external_attr = (0o120777 << 16)
            z.writestr(item, self.raw)
        self.manifest['archive'] = subject.fingerprint(path.read_bytes())
        with self.assertRaisesRegex(ValueError, 'symlink'):
            subject.read_archive(self.manifest, path)


if __name__ == '__main__':
    unittest.main(verbosity=2)
