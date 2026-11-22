import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('deeptools_installer',Path(__file__).with_name('install_deeptools.py'))
installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)

class DeepToolsDependencyPolicyTest(unittest.TestCase):
    def args(self,root,allowed=False):
        return argparse.Namespace(environment=root/'runtime',report=root/'report',allow_download=allowed,allow_source_build=False,prepared_cygwin=None,base_python=None)
    def record(self,args):return json.loads((args.report/'dependency-status.json').read_text())
    def test_absent_runtime_without_permission_skips_and_does_not_create_venv(self):
        with tempfile.TemporaryDirectory() as text,patch.object(installer,'execute') as execute:
            args=self.args(Path(text));self.assertEqual(3,installer.install(args));self.assertFalse(args.environment.exists());execute.assert_not_called()
            self.assertEqual('skipped-install-not-permitted',self.record(args)['status']);self.assertIn('ANALYSIS SKIPPED',(args.report/'WARNINGS.md').read_text());self.assertIn('--allow-download',self.record(args)['required_action'])
    def test_unmanaged_home_is_preserved_even_when_download_allowed(self):
        with tempfile.TemporaryDirectory() as text,patch.object(installer,'execute') as execute:
            args=self.args(Path(text),True);args.environment.mkdir();original=args.environment/'original.txt';original.write_text('retained')
            self.assertEqual(3,installer.install(args));self.assertEqual('retained',original.read_text());execute.assert_not_called();self.assertIn('new --environment',self.record(args)['required_action'])
    def sealed(self,args):
        args.environment.mkdir();member=args.environment/'member';member.write_text('sealed')
        profile={'kind':'native','lock_sha256':installer.digest(installer.LOCK),'files':{str(member):installer.digest(member)}}
        (args.environment/installer.MARKER).write_text(json.dumps(profile));return member
    def test_changed_member_is_rejected_before_any_probe_or_download(self):
        with tempfile.TemporaryDirectory() as text,patch.object(installer,'execute') as execute,patch.object(installer,'probe') as probe:
            args=self.args(Path(text),True);member=self.sealed(args);member.write_text('modified')
            self.assertEqual(3,installer.install(args));probe.assert_not_called();execute.assert_not_called();self.assertEqual('modified',member.read_text());self.assertIn('changed runtime',self.record(args)['error'])
    def test_valid_existing_runtime_reuses_with_download_and_build_forbidden(self):
        with tempfile.TemporaryDirectory() as text,patch.object(installer,'execute') as execute,patch.object(installer,'probe',return_value={'status':'probe-completed','four_loaded_runtime_forks_and_eigenvalues':'passed'}):
            args=self.args(Path(text));self.sealed(args);self.assertEqual(0,installer.install(args));execute.assert_not_called();record=self.record(args)
            self.assertTrue(record['runtime_reused']);self.assertFalse(record['download_authorized']);self.assertFalse(record['source_build_authorized']);self.assertEqual('available-not-yet-adapter-validated',record['status'])
    def probe_with(self,root,data):
        home=root/'runtime';home.mkdir();attempt=root/'attempt';attempt.mkdir();executable=home/'python';executable.write_text('not executed')
        profile={'kind':'native','python_command':[str(executable)],'python_executable':str(executable)}
        def simulated_zero_exit(command,env,log):
            Path(command[-1]).write_text(json.dumps(data));log.write_text('simulated exit0; no real analysis')
        with patch.object(installer,'execute',side_effect=simulated_zero_exit):
            return installer.probe(profile,home,attempt,json.loads(installer.LOCK.read_text()))
    def test_exit_zero_without_completed_report_is_not_available(self):
        with tempfile.TemporaryDirectory() as text:
            with self.assertRaisesRegex(RuntimeError,'before dependency/fork completion'):
                self.probe_with(Path(text),{'status':'initializing-dependencies'})
    def test_wrong_actual_versions_fail_even_with_completed_marker(self):
        with tempfile.TemporaryDirectory() as text:
            with self.assertRaisesRegex(RuntimeError,'versions differ'):
                self.probe_with(Path(text),{'status':'probe-completed','four_loaded_runtime_forks_and_eigenvalues':'passed','versions':{}})
    def test_wrong_scientific_source_fails_even_with_correct_versions(self):
        lock=json.loads(installer.LOCK.read_text())
        with tempfile.TemporaryDirectory() as text:
            with self.assertRaisesRegex(RuntimeError,'scientific source differs'):
                self.probe_with(Path(text),{'status':'probe-completed','four_loaded_runtime_forks_and_eigenvalues':'passed','versions':lock['critical_versions'],'source_sha256':{}})
    def test_unknown_preparation_cannot_be_sealed(self):
        with tempfile.TemporaryDirectory() as text:
            with self.assertRaisesRegex(ValueError,'Unrecognized'):
                installer.prepare_cygwin({'schema':'unrecognized'},Path(text))
    def prepared(self,root,version=390):
        home=root/'runtime';home.mkdir();python=root/'python dir/python.exe';python.parent.mkdir();python.write_text('not executed');shell=root/'bash.exe';shell.write_text('not executed')
        data=root/'mpl-data';font=data/'fonts/ttf/mock.ttf';font.parent.mkdir(parents=True);font.write_text('test-only marker; not an analysis font')
        cache=root/'fontlist-v390.json';cache.write_text(json.dumps({'_version':version,'ttflist':[{'fname':'fonts/ttf/mock.ttf'}],'afmlist':[]}))
        spec={'schema':'jlinalg-deeptools-cygwin-prepared-v1','python_cyg':installer.cygpath(python),'python_executable':str(python),'shell':str(shell),'expected_prefix':'/prepared','path_entries':[installer.cygpath(python.parent),'/usr/bin'],'python_paths':['/prepared/packages'],'seal_roots':[str(data)],'font_cache':str(cache),'matplotlib_data':str(data)}
        return home,spec
    def test_prepared_launcher_pins_private_library_and_copies_versioned_font_template(self):
        with tempfile.TemporaryDirectory() as text:
            home,spec=self.prepared(Path(text));profile=installer.prepare_cygwin(spec,home);script=(home/'launch.sh').read_text()
            self.assertIn('LD_LIBRARY_PATH=',script);self.assertIn('PYTHONDONTWRITEBYTECODE=1',script);self.assertNotIn('\nexec ',script);self.assertIn("'"+spec['python_cyg']+"'",script)
            self.assertEqual(390,json.loads((Path(profile['font_cache_template'])/'fontlist-v390.json').read_text())['_version']);self.assertIn(str(home/'font-cache-template'),profile['seal_roots'])
    def test_wrong_font_cache_version_is_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as text:
            home,spec=self.prepared(Path(text),389)
            with self.assertRaisesRegex(RuntimeError,'v390'):installer.prepare_cygwin(spec,home)

if __name__=='__main__':unittest.main()
