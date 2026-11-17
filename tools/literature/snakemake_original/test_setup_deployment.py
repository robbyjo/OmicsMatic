#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Policy, retention and package-cache edge controls for private manager setup; install nothing."""
from pathlib import Path
import hashlib,io,json,sys,unittest,uuid
from contextlib import redirect_stderr,redirect_stdout
from unittest.mock import patch
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
import setup_deployment as setup
import adopt_deployment as adopter
ROOT=HERE.parents[2]

class DeploymentSetupTest(unittest.TestCase):
    def setUp(self):
        self.root=ROOT/'build/snakemake-original'/('setup-deployment-unit-'+uuid.uuid4().hex);self.root.mkdir(parents=True)
        assert self.root.resolve().is_relative_to((ROOT/'build/snakemake-original').resolve())
    def invoke(self,*extra):
        args=['setup_deployment.py','--out',str(self.root/'new'),*extra]
        with patch.object(sys,'argv',args),redirect_stdout(io.StringIO()),redirect_stderr(io.StringIO()):return setup.main()
    def test_disabled_installation_launches_no_download_or_process_and_retains_clear_skip(self):
        with patch.object(setup,'download',side_effect=AssertionError('Download forbidden')),patch.object(setup,'run',side_effect=AssertionError('Process forbidden')):
            self.assertEqual(3,self.invoke())
        failure=json.loads((self.root/'new/FAILURE.json').read_bytes());self.assertEqual('SKIP3',failure['status']);self.assertIn('No download',failure['error']);self.assertIn('SKIP3: dependent analysis skipped',(self.root/'new/WARNINGS.md').read_text());self.assertFalse((self.root/'new/downloads').exists())
    def test_existing_output_is_preserved(self):
        (self.root/'new').mkdir();(self.root/'new/sentinel').write_bytes(b'preserve\n')
        self.assertEqual(2,self.invoke('--allow-install'));self.assertEqual({'sentinel'},set(p.name for p in (self.root/'new').iterdir()));self.assertEqual(b'preserve\n',(self.root/'new/sentinel').read_bytes())
    def test_unsupported_host_skips_before_any_download(self):
        with patch.object(setup.sys,'platform','darwin'),patch.object(setup,'download',side_effect=AssertionError('Download forbidden')):self.assertEqual(3,self.invoke('--allow-install'))
        self.assertIn('Linux x86-64',(self.root/'new/WARNINGS.md').read_text());self.assertFalse((self.root/'new/downloads').exists())
    def test_forbidden_symlink_filesystem_skips_before_downloading(self):
        with patch.object(setup.sys,'platform','linux'),patch.object(setup.platform,'machine',return_value='x86_64'),patch.object(Path,'symlink_to',side_effect=PermissionError('Links forbidden')),patch.object(setup,'download',side_effect=AssertionError('Download forbidden')):self.assertEqual(3,self.invoke('--allow-install'))
        self.assertIn('case-sensitive Linux filesystem',(self.root/'new/WARNINGS.md').read_text());self.assertFalse((self.root/'new/downloads').exists())
    def test_unresolved_package_cache_link_is_preserved_without_invented_target(self):
        link=self.root/'dangling'
        try:link.symlink_to('other-package/not-yet-linked')
        except OSError:self.skipTest('This host does not permit symlink creation')
        rows=setup.manager_tree(self.root);self.assertEqual('missing',rows['dangling']['target_kind']);self.assertEqual('other-package/not-yet-linked',rows['dangling']['target']);self.assertIsNone(rows['dangling']['pin']);self.assertTrue(link.is_symlink());self.assertFalse(link.exists())
    def test_wrong_cached_artifact_is_rejected_without_download_or_copy(self):
        cache=self.root/'cache';cache.mkdir();(cache/'selected').write_bytes(b'bad');(self.root/'downloads').mkdir();selected=dict(name='selected',url='https://example.invalid/never',bytes=4,sha256=hashlib.sha256(b'good').hexdigest())
        with patch.object(setup,'download',side_effect=AssertionError('Download forbidden')):self.assertRaises(ValueError,setup.downloaded,self.root,selected,cache)
        self.assertFalse((self.root/'downloads/selected').exists());self.assertEqual(b'bad',(cache/'selected').read_bytes())
    def test_adoption_preserves_existing_output_without_adding_failure_records(self):
        target=self.root/'existing';target.mkdir();(target/'sentinel').write_bytes(b'preserve\n');args=['adopt_deployment.py','--conda-setup',str(self.root/'unused-conda'),'--apptainer-setup',str(self.root/'unused-app'),'--source-controls',str(self.root/'unused-controls'),'--out',str(target)]
        with patch.object(adopter.sys,'platform','linux'),patch.object(adopter.platform,'machine',return_value='x86_64'),patch.object(sys,'argv',args),redirect_stdout(io.StringIO()),redirect_stderr(io.StringIO()):self.assertEqual(2,adopter.main())
        self.assertEqual({'sentinel'},set(p.name for p in target.iterdir()));self.assertEqual(b'preserve\n',(target/'sentinel').read_bytes())
    def test_complete_source_export_index_rejects_changed_or_unlisted_records(self):
        source=self.root/'source';source.mkdir();record=source/'record.json';raw=b'{"status":"complete"}\n';record.write_bytes(raw);index=self.root/'index.json';index.write_text(json.dumps(dict(schema_version=1,files={'record.json':dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())})))
        adopter.verify_source_records(source,index)
        record.write_bytes(raw.replace(b'complete',b'damaged!'));self.assertRaises(ValueError,adopter.verify_source_records,source,index);record.write_bytes(raw)
        (source/'unlisted').write_text('extra');self.assertRaises(ValueError,adopter.verify_source_records,source,index)
    @unittest.skipUnless(sys.platform=='linux','Native filesystem prerequisite is tested on Linux')
    def test_incompatible_apptainer_libc_skips_before_download(self):
        record_root=self.root;self.root=Path('/tmp')/('omicsmatic-deployment-abi-unit-'+uuid.uuid4().hex);self.root.mkdir();self.assertEqual(Path('/tmp'),self.root.parent)
        with patch.object(setup.platform,'libc_ver',return_value=('glibc','2.37')),patch.object(setup,'download',side_effect=AssertionError('Download forbidden')):self.assertEqual(3,self.invoke('--allow-install','--components','apptainer'))
        record=json.loads((self.root/'new/APPTAINER-SKIP.json').read_bytes());self.assertIn('glibc >= 2.38',record['error']);self.assertFalse(list((self.root/'new/downloads').iterdir()))
        (record_root/'ABI-CASE.json').write_text(json.dumps(dict(native_test_root=str(self.root),skip_record=record),indent=2)+'\n')
    def test_failed_source_family_cannot_be_promoted_with_a_fresh_file_index(self):
        source=self.root/'failed-source';source.mkdir();path=source/'FAILURE.json';raw=b'{"status":"FAIL2"}\n';path.write_bytes(raw);index=self.root/'index.json';index.write_text(json.dumps(dict(schema_version=1,files={'FAILURE.json':dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())})))
        with self.assertRaisesRegex(ValueError,'Failed source-control family'):adopter.verify_source_records(source,index)
if __name__=='__main__':unittest.main()
