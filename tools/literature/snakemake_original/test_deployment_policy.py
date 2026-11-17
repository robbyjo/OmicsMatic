#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original-method edge controls, including preservation of incomplete prefixes; install nothing."""
from pathlib import Path
from types import SimpleNamespace
import sys,tempfile,unittest
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from deployment_policy import InstallationPolicy,InstallationForbidden,InstallationIntegrityError
from snakemake.deployment.conda import Env,get_env_setup_done_flag_file
from snakemake.deployment.singularity import Image

class DeploymentPolicyTest(unittest.TestCase):
    def setUp(self):
        # Keep native case/mode/link behavior. These are small retained fixtures,
        # not environments that could support a scientific analysis.
        base=Path.home()/'.local/share/omicsmatic/deployment-policy-units';base.mkdir(parents=True,exist_ok=True)
        self.root=Path(tempfile.mkdtemp(prefix=self._testMethodName+'-',dir=base))
        print('Retained fixture: '+str(self.root),file=sys.stderr)
        self.original_create=Env.create;self.original_pull=Image.pull
    def tearDown(self):
        self.assertIs(Env.create,self.original_create);self.assertIs(Image.pull,self.original_pull)
    def environment(self):
        yaml=self.root/'environment.yaml';yaml.write_text('dependencies:\n  - python=3.14.10\n');prefix=self.root/'envs';prefix.mkdir()
        workflow=SimpleNamespace(persistence=SimpleNamespace(conda_env_path=str(prefix)),deployment_settings=SimpleNamespace(apptainer_args=''),runtime_paths=[],sourcecache=SimpleNamespace(exists=lambda *_:False))
        env=Env(workflow,env_file=str(yaml));env._content=yaml.read_bytes();env._content_deploy=b'';env._content_pin=b''
        # No auxiliary pin/post-deploy file exists in this fixture; avoid a
        # manager platform query while preserving the original lookup method.
        env._conda=SimpleNamespace(platform='linux-64')
        return env
    def image(self):
        dag=SimpleNamespace(workflow=SimpleNamespace(persistence=SimpleNamespace(container_img_path=str(self.root/'images'))))
        return Image('docker://example.invalid/image@sha256:'+'a'*64,dag,False)
    def test_permission_must_be_an_explicit_boolean(self):
        for value in [None,0,1,'false',[],{}]:
            with self.assertRaises(ValueError):InstallationPolicy(value)
    def test_context_restores_original_bindings_after_an_unrelated_failure(self):
        policy=InstallationPolicy(False)
        with self.assertRaisesRegex(RuntimeError,'controlled exception'):
            with policy:raise RuntimeError('controlled exception')
        self.assertTrue(policy.closed);self.assertFalse(policy.blocked)
    def test_nested_scope_is_rejected_without_disturbing_the_original_guard(self):
        with InstallationPolicy(False) as outer:
            guarded=Env.create
            with self.assertRaises(ValueError):
                with InstallationPolicy(False):self.fail('Nested policy entered')
            self.assertIs(Env.create,guarded);self.assertFalse(outer.closed)
    def test_reusing_a_closed_context_is_rejected(self):
        policy=InstallationPolicy(False)
        with policy:pass
        with self.assertRaises(RuntimeError):
            with policy:self.fail('Closed policy reused')
    def test_changed_binding_is_an_integrity_failure_and_original_is_restored(self):
        policy=InstallationPolicy(False)
        with self.assertRaises(InstallationIntegrityError):
            with policy:Env.create=lambda *_:None
        self.assertTrue(policy.closed)
    def test_missing_conda_environment_is_blocked_before_the_original_create(self):
        env=self.environment();address=Path(env.address)
        with InstallationPolicy(False) as policy:
            with self.assertRaises(InstallationForbidden):env.create()
        self.assertFalse(address.exists());self.assertTrue(policy.blocked);self.assertFalse(policy.records[0]['upstream_method_called'])
    def test_incomplete_conda_environment_is_preserved_instead_of_being_removed(self):
        env=self.environment();address=Path(env.address);address.mkdir();sentinel=address/'keep-original-partial-prefix';sentinel.write_bytes(b'preserve incomplete evidence\n')
        with InstallationPolicy(False) as policy:
            with self.assertRaises(InstallationForbidden):env.create()
        self.assertEqual(b'preserve incomplete evidence\n',sentinel.read_bytes());self.assertFalse(get_env_setup_done_flag_file(address).exists());self.assertFalse(policy.records[0]['upstream_method_called'])
    def test_complete_fixture_calls_original_method_and_preserves_return_value(self):
        env=self.environment();address=Path(env.address);address.mkdir();marker=get_env_setup_done_flag_file(address);marker.touch()
        with InstallationPolicy(False) as policy:result=env.create()
        self.assertEqual(str(address),result);self.assertFalse(policy.blocked);self.assertTrue(policy.records[0]['upstream_method_called']);self.assertTrue(policy.records[0]['upstream_returned']);self.assertTrue(marker.is_file())
    def test_dryrun_calls_original_without_installing_a_missing_environment(self):
        env=self.environment();address=Path(env.address)
        with InstallationPolicy(False) as policy:result=env.create(dryrun=True)
        self.assertEqual(str(address),result);self.assertFalse(address.exists());self.assertFalse(policy.blocked);self.assertTrue(policy.records[0]['upstream_method_called'])
    def test_externally_managed_invalid_create_retains_original_assertion(self):
        workflow=SimpleNamespace(persistence=SimpleNamespace(conda_env_path=str(self.root/'envs')),deployment_settings=SimpleNamespace(apptainer_args=''),runtime_paths=[]);env=Env(workflow,env_name='external')
        with InstallationPolicy(False) as policy:
            with self.assertRaises(AssertionError):env.create()
        self.assertFalse(policy.blocked);self.assertTrue(policy.records[0]['upstream_method_called']);self.assertFalse(policy.records[0]['upstream_returned'])
    def test_missing_remote_image_is_blocked_before_any_original_runtime_check(self):
        image=self.image()
        with InstallationPolicy(False) as policy:
            with self.assertRaises(InstallationForbidden):image.pull()
        self.assertFalse(Path(image.path).exists());self.assertFalse(policy.records[0]['upstream_method_called']);self.assertTrue(policy.blocked)
    def test_bad_method_argument_does_not_create_or_remove_software(self):
        env=self.environment()
        with InstallationPolicy(False) as policy:
            with self.assertRaises(TypeError):env.create(dryrun='false')
        self.assertFalse(policy.records);self.assertFalse(Path(env.address).exists())
if __name__=='__main__':unittest.main()
