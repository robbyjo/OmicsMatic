"""Permission, identity and retention gates for development-only fastp setup."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import argparse
import install_fastp as module


class FastpSetupTest(unittest.TestCase):
    def arguments(self,root,**values):
        return argparse.Namespace(profile='linux-x86_64-fastp-0.23.2',home=root/'home',report=root/'report',cache=root/'cache',allow_install=False,allow_download=False,**values)
    def call(self,args):
        with contextlib.redirect_stdout(io.StringIO()):return module.install(args)
    def test_four_exact_editions_and_no_analysis_admission(self):
        lock=module.lock();self.assertEqual(4,len(lock['profiles']));self.assertIs(lock['runtime_analysis_admitted'],False);self.assertIs(lock['source_to_binary_reproduction_verified'],False)
    def test_duplicate_json_keys_rejected(self):
        with self.assertRaisesRegex(ValueError,'Duplicate'):module.strict_json('{"x":1,"x":2}')
    def test_nonfinite_json_rejected(self):
        for value in ['NaN','Infinity','-Infinity']:
            with self.assertRaisesRegex(ValueError,'Nonfinite'):module.strict_json('{"x":'+value+'}')
    def test_changed_lock_rejected_before_execution(self):
        with patch.object(module,'LOCK_SHA256','0'*64),self.assertRaisesRegex(ValueError,'lock differs'):module.lock()
    def test_unsupported_profile_is_explicit_skip(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();args=self.arguments(root);args.profile='fastp-latest'
            self.assertEqual(3,self.call(args));record=json.loads((args.report/'FAILURE.json').read_text());self.assertIn('Unsupported',record['error']);self.assertFalse(args.home.exists());self.assertFalse((args.report/'COMPLETED.json').exists())
    def test_install_forbidden_preserves_skipped_state(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(module.sys,'platform','linux'),patch.object(module.platform,'machine',return_value='x86_64'),patch.object(module,'smoke') as smoke:
            root=Path(tmp).resolve();args=self.arguments(root);self.assertEqual(3,self.call(args));smoke.assert_not_called();self.assertFalse(args.home.exists());self.assertFalse(args.cache.exists());self.assertIn('--allow-install',(args.report/'FAILURE.json').read_text())
    def test_download_forbidden_requires_exact_offline_cache(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(module.sys,'platform','linux'),patch.object(module.platform,'machine',return_value='x86_64'),patch.object(module.urllib.request,'urlopen') as download:
            root=Path(tmp).resolve();args=self.arguments(root);args.allow_install=True;self.assertEqual(3,self.call(args));download.assert_not_called();self.assertFalse(args.home.exists());self.assertIn('Download not permitted',(args.report/'FAILURE.json').read_text())
    def test_corrupt_cache_is_retained_without_execution(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(module.sys,'platform','linux'),patch.object(module.platform,'machine',return_value='x86_64'),patch.object(module,'smoke') as smoke:
            root=Path(tmp).resolve();args=self.arguments(root);args.allow_install=True;args.cache.mkdir();payload=args.cache/'fastp.0.23.2';payload.write_bytes(b'changed-original-binary')
            self.assertEqual(2,self.call(args));self.assertEqual(b'changed-original-binary',payload.read_bytes());smoke.assert_not_called();self.assertFalse(args.home.exists());self.assertIn('Cached binary differs',(args.report/'FAILURE.json').read_text())
    def test_unmanaged_home_is_retained_without_execution(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(module.sys,'platform','linux'),patch.object(module.platform,'machine',return_value='x86_64'),patch.object(module,'smoke') as smoke:
            root=Path(tmp).resolve();args=self.arguments(root);args.home.mkdir();(args.home/'keep').write_bytes(b'user-file');self.assertEqual(2,self.call(args));self.assertEqual(b'user-file',(args.home/'keep').read_bytes());smoke.assert_not_called()
    def test_existing_report_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();args=self.arguments(root);args.report.mkdir();(args.report/'keep').write_bytes(b'previous-evidence')
            with self.assertRaises(FileExistsError):self.call(args)
            self.assertEqual(b'previous-evidence',(args.report/'keep').read_bytes())
    def test_overlapping_paths_are_rejected_before_creation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();args=self.arguments(root);args.report=args.home/'nested'
            with self.assertRaisesRegex(ValueError,'disjoint'):self.call(args)
            self.assertFalse(args.home.exists())
    def test_hardlinked_cache_payload_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();a=root/'a';b=root/'b';a.write_bytes(b'x');os.link(a,b)
            with self.assertRaisesRegex(ValueError,'unlinked regular'):module.fingerprint(b,100)


if __name__=='__main__':unittest.main()
