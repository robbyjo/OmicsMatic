"""Installer/integrity policy fixtures never execute fake scientific programs."""
import argparse
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch
import install_trim_galore as installer

ROOT = Path(__file__).resolve().parents[2]


class TrimGalorePolicyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.root = Path(self.temporary.name)
        self.lock_path = self.root / 'lock.json'
        self.source = self.root / 'sources'; (self.source / '0.6.10').mkdir(parents=True)
        for name in ['trim_galore', 'README.md', 'LICENSE']:
            (self.source / '0.6.10' / name).write_text('policy fixture; never executed: ' + name)
        files = {p.name: installer.digest(p) for p in (self.source / '0.6.10').iterdir()}
        self.lock = dict(schema_version=1, profile='windows-cygwin-perl-native-python', cutadapt_packages={'cutadapt': 'policy'}, profiles={'0.6.10': dict(commit='0'*40, files=files)})
        self.lock_path.write_text(json.dumps(self.lock))
        self.reference = self.root / 'existing-read-only-runtime'; self.reference.mkdir(); (self.reference / 'module.py').write_text('policy module')
        self.dependencies = dict(external_roots=[dict(path=str(self.reference))], external_fixed_files=[], external_files=installer.inventory([dict(path=str(self.reference))]))
        self.args = argparse.Namespace(environment=self.root / 'private', report=self.root / 'report', cache=self.root / 'cache', source_cache=self.source,
            perl=self.root / 'perl.exe', python=self.root / 'python.exe', fastqc_home=None, java=None, allow_install=False, allow_download=False)
        self.dependencies['perl'] = str(self.args.perl)  # Never executed; execute() is mocked in these policy fixtures.

    def tearDown(self):
        self.temporary.cleanup()

    def install(self):
        with (contextlib.redirect_stdout(io.StringIO()), patch.object(installer, 'LOCK', self.lock_path), patch.object(installer, 'discover', return_value=self.dependencies) as discovery,
                patch.object(installer, 'execute', return_value='version 0.6.10'), patch.object(installer.sys, 'platform', 'win32'), patch.object(installer.platform, 'machine', return_value='AMD64'),
                patch.object(installer.urllib.request, 'urlopen', side_effect=AssertionError('No network permitted in policy tests'))):
            code = installer.install(self.args)
        record = json.loads((self.args.report / 'dependency-status.json').read_text())
        return code, record, discovery.call_count

    def test_new_profile_requires_install_authorization_before_dependency_discovery(self):
        code, record, calls = self.install()
        self.assertEqual((3, 'skipped-install-not-permitted', 0), (code, record['status'], calls))
        self.assertFalse(self.args.environment.exists())
        warning = (self.args.report / 'WARNINGS.md').read_text(); self.assertIn('ANALYSIS SKIPPED', warning); self.assertIn('Required action:', warning)

    def test_verified_offline_sources_install_without_network_and_reference_files_are_unchanged(self):
        self.args.allow_install = True; before = (self.reference / 'module.py').read_bytes()
        code, record, calls = self.install()
        self.assertEqual(0, code); self.assertEqual('installed-not-yet-analysis-validated', record['status']); self.assertEqual(1, calls)
        self.assertEqual(before, (self.reference / 'module.py').read_bytes())
        self.assertFalse(record['download_authorized'])

    def test_intact_profile_reuse_needs_no_install_download_or_discovery(self):
        self.args.allow_install = True; self.assertEqual(0, self.install()[0]); self.args.allow_install = False
        code, record, calls = self.install()
        self.assertEqual((0, True, 0), (code, record['runtime_reused'], calls))
        self.assertEqual(1, len(record['history']))

    def test_unmanaged_or_corrupt_private_source_is_preserved(self):
        self.args.environment.mkdir(); marker = self.args.environment / 'keep'; marker.write_text('user data')
        self.args.allow_install = True; self.assertEqual(3, self.install()[0]); self.assertEqual('user data', marker.read_text())

    def test_changed_external_dependency_skips_without_repair(self):
        self.args.allow_install = True; self.assertEqual(0, self.install()[0]); dependency = self.reference / 'module.py'; dependency.write_text('changed source')
        code, record, calls = self.install(); self.assertEqual((3, 0), (code, calls)); self.assertIn('changed runtime preserved', record['error'])
        self.assertEqual('changed source', dependency.read_text())

    def test_added_external_file_and_added_private_member_are_both_rejected(self):
        self.args.allow_install = True; self.assertEqual(0, self.install()[0]); extra = self.reference / 'injected.py'; extra.write_text('not sealed')
        self.assertEqual(3, self.install()[0]); extra.unlink()
        (self.args.environment / 'extra').write_text('preserve'); self.assertEqual(3, self.install()[0])
        self.assertEqual('preserve', (self.args.environment / 'extra').read_text())

    def test_missing_or_changed_original_cache_is_not_downloaded_without_authorization(self):
        self.args.allow_install = True; self.args.source_cache = None
        self.assertEqual(3, self.install()[0])
        self.assertIn('--allow-download required', json.loads((self.args.report / 'dependency-status.json').read_text())['error'])

    def test_changed_supplied_original_is_rejected_and_staging_retained(self):
        self.args.allow_install = True; (self.source / '0.6.10/trim_galore').write_text('changed original')
        code, record, _ = self.install(); self.assertEqual(3, code); self.assertIn('Supplied original source differs', record['error'])
        self.assertTrue(Path(record['retained_staging']).is_dir())

    def test_attempt_directories_remain_unique_with_a_frozen_clock(self):
        instant = installer.dt.datetime(2026, 10, 25, tzinfo=installer.dt.timezone.utc)
        with patch.object(installer.dt, 'datetime') as clock:
            clock.now.return_value = instant
            first = self.install()[1]; sentinel = Path(first['attempt']) / 'raw.log'; sentinel.write_text('retain')
            second = self.install()[1]
        self.assertNotEqual(first['attempt'], second['attempt']); self.assertEqual('retain', sentinel.read_text())

    def test_missing_fixed_dependency_is_rejected_by_reuse(self):
        self.args.allow_install = True; self.assertEqual(0, self.install()[0])
        marker = self.args.environment / installer.MARKER; data = json.loads(marker.read_text()); data['external_fixed_files'] = [str(self.root / 'absent.dll')]; marker.write_text(json.dumps(data))
        self.assertEqual(3, self.install()[0])

    def test_profile_lock_is_identical_to_packaged_java_resource(self):
        self.assertEqual((ROOT / 'tools/literature/trim-galore-runtime-lock.json').read_bytes(), (ROOT / 'src/main/resources/org/jlinalg/omics/trim_galore_runtime_lock.json').read_bytes())

    def test_archive_comparison_checks_original_code_and_preserves_changed_installed_files(self):
        archive = self.root / 'original.whl'
        with zipfile.ZipFile(archive, 'w') as output:
            output.writestr('module.py', 'policy module')
            output.writestr('package.dist-info/RECORD', 'pip rewrites this')
        verified = installer.compare_archive(archive, self.reference, wheel=True)
        self.assertEqual({(self.reference / 'module.py').as_posix(): installer.digest(self.reference / 'module.py')}, verified)
        (self.reference / 'module.py').write_text('changed')
        with self.assertRaisesRegex(ValueError, 'differs from the original archive'):
            installer.compare_archive(archive, self.reference, wheel=True)
        self.assertEqual('changed', (self.reference / 'module.py').read_text())

    def test_unsafe_original_archive_member_is_rejected_without_extraction(self):
        archive = self.root / 'unsafe.whl'
        with zipfile.ZipFile(archive, 'w') as output:
            output.writestr('../outside.py', 'policy fixture')
        with self.assertRaisesRegex(ValueError, 'Unsafe upstream archive member'):
            installer.compare_archive(archive, self.reference, wheel=True)
        self.assertFalse((self.root / 'outside.py').exists())

    def test_authentication_archive_download_is_separately_permission_gated(self):
        pin = dict(url='https://example.invalid/original.whl', sha256='0'*64)
        with patch.object(installer.urllib.request, 'urlopen', side_effect=AssertionError('No network')):
            with self.assertRaisesRegex(RuntimeError, '--allow-download required'):
                installer.archive(pin, self.args.cache, False)

    def test_private_python_preparation_does_not_install_when_forbidden(self):
        self.args.python_environment = self.root / 'new-python'; self.args.python_base = self.root / 'base.exe'
        with self.assertRaisesRegex(RuntimeError, '--allow-install'):
            installer.prepare_python(self.args, self.root, self.lock)
        self.assertFalse(self.args.python_environment.exists())

    def test_private_python_preparation_preserves_unmanaged_and_incomplete_environments(self):
        self.args.allow_install = True; self.args.python_environment = self.root / 'existing-python'; self.args.python_environment.mkdir()
        sentinel = self.args.python_environment / 'keep'; sentinel.write_text('preserve')
        with self.assertRaisesRegex(RuntimeError, 'unmanaged Python environment preserved'):
            installer.prepare_python(self.args, self.root, self.lock)
        (self.args.python_environment / '.jlinalg-trim-python.json').write_text(json.dumps(dict(lock_sha256=installer.digest(self.lock_path))))
        with patch.object(installer, 'LOCK', self.lock_path):
            with self.assertRaisesRegex(RuntimeError, 'private Python is incomplete'):
                installer.prepare_python(self.args, self.root, self.lock)
        self.assertEqual('preserve', sentinel.read_text())

    def test_new_private_python_is_created_at_its_final_path_and_console_is_verified(self):
        self.args.allow_install = True; self.args.python_environment = self.root / 'final-python'; self.args.python_base = self.root / 'base.exe'; self.args.python_base.write_text('never executed')
        archive = self.root / 'policy.whl'
        with zipfile.ZipFile(archive, 'w') as output:
            output.writestr('module.py', 'policy module')
        local_lock = dict(wheels={'policy': dict(url='https://example.invalid/policy.whl', sha256=installer.digest(archive))})
        commands = []
        def execute(command, attempt, name, env):
            commands.append((name, list(map(str, command))))
            if name == 'python-base-version': return '3.13'
            if name == 'private-python-create':
                self.assertEqual(str(self.args.python_environment), str(command[-1]))
                scripts = self.args.python_environment / 'Scripts'; scripts.mkdir(); (scripts / 'python.exe').write_text('never executed'); (scripts / 'cutadapt.exe').write_text('never executed')
            elif name == 'private-python-install':
                site = self.args.python_environment / 'Lib/site-packages'; site.mkdir(parents=True); (site / 'module.py').write_text('policy module')
            elif name == 'private-python-console': return '5.2\n'
            return ''
        with patch.object(installer, 'LOCK', self.lock_path), patch.object(installer, 'archive', return_value=archive), patch.object(installer, 'execute', side_effect=execute):
            result = installer.prepare_python(self.args, self.root, local_lock)
        self.assertEqual(self.args.python_environment / 'Scripts/python.exe', result)
        self.assertTrue((self.args.python_environment / '.jlinalg-trim-python.json').is_file())
        self.assertTrue(any(name == 'private-python-console' for name, command in commands))
        self.assertFalse(list(self.root.glob('.jlinalg-trim-python-*')))


class TrimGaloreOriginalArgumentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('original_trim_galore_driver_for_policy', ROOT / 'src/main/resources/org/jlinalg/omics/trim_galore_original.py')
        cls.driver = importlib.util.module_from_spec(spec); spec.loader.exec_module(cls.driver)

    def config(self, **settings):
        return dict(release='0.6.10', operation='trim', paired=False, fastqc=False, settings=dict(phred=33, adapter_kind='auto', **settings))

    def test_omitted_original_defaults_are_not_replaced_by_a_simplified_pipeline(self):
        command = self.driver.original_arguments(self.config())
        self.assertNotIn('--quality', command); self.assertNotIn('--length', command); self.assertNotIn('-a', command)

    def test_custom_adapter_cannot_inject_additional_shell_or_cutadapt_arguments(self):
        config = self.config(); config['settings'].update(adapter_kind='custom', adapter1='ACGT -o outside')
        with self.assertRaisesRegex(ValueError, 'literal DNA'):
            self.driver.original_arguments(config)

    def test_small_error_rates_keep_numeric_values_without_shell_strings(self):
        command = self.driver.original_arguments(self.config(error_rate=1e-8))
        self.assertEqual('1e-08', command[command.index('--error_rate')+1])
        for value in [float('nan'), float('inf'), -0.1, '0.1; command']:
            with self.assertRaises(ValueError):
                self.driver.original_arguments(self.config(error_rate=value))

    def test_hardtrim_rejects_filter_parameters_instead_of_ignoring_them(self):
        config = dict(release='0.6.10', operation='hardtrim5', paired=False, fastqc=False, settings=dict(bases=50, minimum_length=50))
        with self.assertRaisesRegex(ValueError, 'Hardtrim requires only'):
            self.driver.original_arguments(config)


if __name__ == '__main__':
    unittest.main()
