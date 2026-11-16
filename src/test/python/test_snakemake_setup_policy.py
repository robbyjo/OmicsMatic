# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Setup permission and evidence-retention contracts; no software is downloaded or installed."""
from pathlib import Path
import contextlib,importlib.util,io,json,os,sys,tempfile,unittest,zipfile
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[3]
SPEC=importlib.util.spec_from_file_location('snakemake_private_setup',ROOT/'tools/literature/snakemake_original/setup_runtime.py')
SETUP=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(SETUP)

class SetupPolicyTest(unittest.TestCase):
    def setUp(self):
        self.scratch=tempfile.TemporaryDirectory(prefix='omicsmatic-snakemake-policy-');self.root=Path(self.scratch.name).resolve()
    def tearDown(self):
        self.assertEqual(Path(tempfile.gettempdir()).resolve(),self.root.parent);self.assertTrue(self.root.name.startswith('omicsmatic-snakemake-policy-'));self.scratch.cleanup()
    def call(self,*args):
        error=io.StringIO()
        with patch.object(sys,'argv',['setup_runtime.py',*map(str,args)]),patch.object(SETUP,'download',side_effect=AssertionError('Download must not run')),patch.object(SETUP.subprocess,'Popen',side_effect=AssertionError('Installation/control must not run')),contextlib.redirect_stderr(error):code=SETUP.main()
        return code,error.getvalue()
    def test_disabled_installation_retains_prominent_skip_and_launches_nothing(self):
        out=self.root/'disabled';code,error=self.call('--out',out)
        self.assertEqual(3,code);self.assertIn('SKIP3',error);failure=json.loads((out/'FAILURE.json').read_bytes());self.assertEqual(3,failure['exit_code']);self.assertFalse(failure['user_analysis_admitted']);self.assertIn('NEW',failure['required_action']);self.assertFalse((out/'environment').exists());self.assertFalse((out/'READY-SETUP.json').exists())
    def test_existing_prefix_and_its_files_are_preserved(self):
        out=self.root/'existing';out.mkdir();(out/'sentinel').write_bytes(b'preserve original\n');before={p.name:p.read_bytes() for p in out.iterdir()};code,error=self.call('--out',out,'--allow-install');self.assertEqual(2,code);self.assertIn('FAIL2',error);self.assertEqual(before,{p.name:p.read_bytes() for p in out.iterdir()})
    def test_skip_records_are_exported_with_their_complete_census(self):
        out=self.root/'disabled';records=self.root/'records';code,_=self.call('--out',out,'--records-out',records);self.assertEqual(3,code)
        record=json.loads((records/'EXPORTED.json').read_bytes());actual={p.relative_to(records).as_posix() for p in records.rglob('*') if p.is_file()};self.assertEqual(set(record['files'])|{'EXPORTED.json'},actual);self.assertFalse(record['user_analysis_admitted']);self.assertEqual((out/'FAILURE.json').read_bytes(),(records/'FAILURE.json').read_bytes());self.assertIn('as-run-setup_runtime.py',record['files'])
    def test_export_cannot_overlap_or_overwrite_an_existing_directory(self):
        records=self.root/'records';records.mkdir();(records/'sentinel').write_bytes(b'keep');out=self.root/'new';code,_=self.call('--out',out,'--records-out',records);self.assertEqual(2,code);self.assertFalse(out.exists());self.assertEqual(b'keep',(records/'sentinel').read_bytes())
    @unittest.skipUnless(os.name=='posix','Case-sensitive POSIX source filesystem required')
    def test_case_distinct_probe_evidence_keeps_both_names_and_link_spelling(self):
        out=self.root/'native-source';probe=out/'filesystem-probe';probe.mkdir(parents=True);(probe/'Case').write_bytes(b'upper\n');(probe/'case').write_bytes(b'lower\n');(probe/'Case').chmod(0o700);(probe/'alias').symlink_to('Case');records=self.root/'records';SETUP.export_records(out,records)
        with zipfile.ZipFile(records/'FILESYSTEM-PROBE.zip') as archive:
            self.assertEqual({'Case','case','alias'},set(archive.namelist()));self.assertEqual(b'upper\n',archive.read('Case'));self.assertEqual(b'lower\n',archive.read('case'));self.assertEqual(b'Case',archive.read('alias'));self.assertEqual(0o700,(archive.getinfo('Case').external_attr>>16)&0o777)

if __name__=='__main__':unittest.main()
