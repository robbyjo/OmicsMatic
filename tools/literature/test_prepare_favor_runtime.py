#!/usr/bin/env python3
"""Portable FAVOR preparation policy/capacity/error tests;real Linux intake is separate."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

SPEC=importlib.util.spec_from_file_location('prepare_favor_runtime',Path(__file__).with_name('prepare_favor_runtime.py'))
MODULE=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(MODULE)

class FavorPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.root=Path(self.temporary.name).resolve()
    def tearDown(self):
        MODULE._HASH_BUDGET=None;self.temporary.cleanup()
    def invoke(self,args,platform='linux',machine='x86_64'):
        output=io.StringIO();error=io.StringIO()
        with contextlib.redirect_stdout(output),contextlib.redirect_stderr(error),mock.patch.object(MODULE.sys,'platform',platform),mock.patch.object(MODULE.platform,'machine',return_value=machine):
            code=MODULE.main(args)
        return code,output.getvalue(),error.getvalue()
    def arguments(self,out=None):return ['--runtime-root',str(self.root/'missing-runtime'),'--out',str(out or self.root/'output')]
    def test_pinned_policy_and_lock_match_java_resource(self):
        policy=MODULE.read_policy();resource=Path(__file__).resolve().parents[2]/'src/main/resources/org/jlinalg/omics/favor-runtime-policy.json'
        self.assertEqual((MODULE.ASSETS/'runtime-policy.json').read_bytes(),resource.read_bytes());self.assertEqual(len(policy['conda_packages']),154);self.assertEqual(MODULE.sha(MODULE.ASSETS/'pixi.lock'),policy['build_lock_sha256'])
    def test_original_bundled_sources_retain_pinned_bytes_and_license(self):
        policy=MODULE.read_policy()
        for name,expected in policy['original_source_sha256'].items():self.assertEqual(MODULE.sha(MODULE.ASSETS/'upstream'/name),expected)
        self.assertIn('licensed under GPLv3',(MODULE.ASSETS/'upstream/README.md').read_text());self.assertIn('Version 3, 29 June 2007',(MODULE.ASSETS/'upstream/COPYING').read_text())
    def test_hash_stream_preserves_raw_bytes(self):
        path=self.root/'payload';raw=b'\x00\xff\n'+b'a'*70000;path.write_bytes(raw);MODULE._HASH_BUDGET=MODULE.HashBudget(100000,100000)
        self.assertEqual(MODULE.sha(path),hashlib.sha256(raw).hexdigest());self.assertEqual(MODULE._HASH_BUDGET.used,len(raw));MODULE._HASH_BUDGET.verify()
    def test_per_file_capacity_cannot_be_bypassed(self):
        path=self.root/'large';path.write_bytes(b'123');MODULE._HASH_BUDGET=MODULE.HashBudget(2,100)
        with self.assertRaises(MODULE.CapacityError):MODULE.sha(path)
    def test_total_hash_capacity_cannot_be_bypassed(self):
        path=self.root/'payload';path.write_bytes(b'123');MODULE._HASH_BUDGET=MODULE.HashBudget(100,2)
        with self.assertRaises(MODULE.CapacityError):MODULE.sha(path)
    def test_file_identity_mutation_after_hash_is_detected(self):
        path=self.root/'payload';path.write_bytes(b'123');MODULE._HASH_BUDGET=MODULE.HashBudget(100,100);MODULE.sha(path);path.write_bytes(b'changed')
        with self.assertRaises(MODULE.Unavailable):MODULE._HASH_BUDGET.verify()
    def test_optimized_python_cannot_bypass_byte_capacity(self):
        path=self.root/'payload';path.write_bytes(b'123')
        script="import importlib.util,sys;from pathlib import Path;s=importlib.util.spec_from_file_location('favor',sys.argv[1]);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);m._HASH_BUDGET=m.HashBudget(2,100)\ntry:m.sha(Path(sys.argv[2]))\nexcept m.CapacityError:sys.exit(0)\nelse:sys.exit(2)"
        result=subprocess.run([sys.executable,'-O','-c',script,str(Path(MODULE.__file__)),str(path)],capture_output=True,timeout=30)
        self.assertEqual(result.returncode,0,result.stderr.decode('utf-8',errors='replace'))
    def test_optional_java_documentation_does_not_control_tree_initialization(self):
        prefix=Path('/opt/private-r');java=Path('/opt/java21/bin/java');roots=MODULE.runtime_roots(prefix,java,None);self.assertEqual(set(roots),{'r','java','host-loader'});self.assertEqual(roots['java'],java.parent.parent)
        self.assertEqual(MODULE.runtime_roots(prefix,java,Path('/docs/java'))['java-doc'],Path('/docs/java'))
    def test_new_output_never_overwrites_existing_evidence(self):
        root=self.root/'output';root.mkdir();marker=root/'marker';marker.write_text('retained')
        with self.assertRaises(ValueError):MODULE.new_output(root)
        self.assertEqual(marker.read_text(),'retained')
    def test_missing_parent_and_whitespace_output_are_invalid(self):
        for path in [self.root/'missing'/'output',self.root/'has whitespace']:
            with self.assertRaises(ValueError):MODULE.new_output(path)
    def test_canonical_runtime_root_must_exist(self):
        with self.assertRaises(MODULE.Unavailable):MODULE.canonical_directory(self.root/'missing')
        self.assertEqual(MODULE.canonical_directory(self.root),self.root)
    def test_metadata_duplicate_keys_and_nonfinite_values_are_rejected(self):
        path=self.root/'policy.json'
        for text in ['{"name":"a","name":"b"}','{"value":NaN}','{"value":Infinity}']:
            path.write_text(text)
            with self.assertRaises(MODULE.Unavailable):MODULE.strict_json(path)
    def test_metadata_utf8_and_byte_capacity_are_enforced(self):
        path=self.root/'policy.json';path.write_bytes(b'\xff')
        with self.assertRaises(UnicodeError):MODULE.strict_json(path)
        with path.open('wb') as stream:stream.truncate(16777217)
        with self.assertRaises(MODULE.CapacityError):MODULE.strict_json(path)
    def test_package_policy_requires_complete_unique_sha256_artifacts(self):
        policy=MODULE.read_policy();row=dict(policy['conda_packages'][0]);self.assertEqual(len(MODULE.package_set([row])),1)
        with self.assertRaises(MODULE.Unavailable):MODULE.package_set([row,row])
        for key in ['name','version','build','subdir','sha256','url']:
            changed=dict(row);changed.pop(key)
            with self.assertRaises(MODULE.Unavailable):MODULE.package_set([changed])
        row['sha256']='A'*64
        with self.assertRaises(MODULE.Unavailable):MODULE.package_set([row])
    def test_numeric_capacity_arguments_are_exact_positive_bounded_integers(self):
        self.assertEqual(MODULE.positive('123'),123)
        for value in ['0','-1','01','1.5',' 1','1e3','4503599627370497']:
            with self.assertRaises(Exception):MODULE.positive(value)
    def test_windows_and_arm_hosts_skip_without_writing_output(self):
        for platform,machine in [('win32','AMD64'),('linux','aarch64')]:
            code,_,error=self.invoke(self.arguments(),platform,machine);self.assertEqual(code,3);self.assertIn('Required action',error);self.assertIn('SKIPPED',error);self.assertFalse((self.root/'output').exists())
    def test_missing_dependency_retains_prominent_skip_diagnostics(self):
        code,_,error=self.invoke(self.arguments());self.assertEqual(code,3);root=self.root/'output';report=json.loads((root/'failure.json').read_text());self.assertEqual(report['exit_code'],3);self.assertFalse(report['scientific_runtime_admitted']);self.assertIn('Required action',(root/'WARNINGS.md').read_text());self.assertIn('SKIPPED',error);self.assertFalse((root/'inventory.json').exists())
    def test_invalid_entry_capacity_fails2_before_creating_output(self):
        code,_,error=self.invoke(self.arguments()+['--max-entries','1000001']);self.assertEqual(code,2);self.assertIn('Required action',error);self.assertFalse((self.root/'output').exists())
    def test_existing_output_fails2_and_preserves_previous_files(self):
        root=self.root/'output';root.mkdir();(root/'previous.json').write_text('retained');code,_,error=self.invoke(self.arguments());self.assertEqual(code,2);self.assertEqual((root/'previous.json').read_text(),'retained');self.assertIn('Required action',error)
    def test_os_forbidden_dependency_is_skip3_and_never_an_inventory(self):
        with mock.patch.object(MODULE,'preflight',side_effect=PermissionError('operating environment forbids dependency access')):
            code,_,error=self.invoke(self.arguments())
        self.assertEqual(code,3);self.assertIn('forbids dependency access',error);self.assertFalse((self.root/'output/inventory.json').exists());self.assertTrue((self.root/'output/failure.json').exists())
    def test_exceeded_preflight_hash_capacity_is_fail2(self):
        with mock.patch.object(MODULE,'preflight',side_effect=MODULE.CapacityError('Complete runtime byte capacity exceeded')):
            code,_,error=self.invoke(self.arguments())
        self.assertEqual(code,2);self.assertIn('byte capacity',error);self.assertEqual(json.loads((self.root/'output/failure.json').read_text())['exit_code'],2)
    def test_malformed_options_explain_required_action(self):
        error=io.StringIO()
        with contextlib.redirect_stderr(error),self.assertRaises(SystemExit) as result:MODULE.main(['--unexpected'])
        self.assertEqual(result.exception.code,2);self.assertIn('Required action',error.getvalue())

if __name__=='__main__':unittest.main()
