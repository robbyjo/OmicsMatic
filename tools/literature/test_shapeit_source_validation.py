# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import struct
import tempfile
import unittest
import zipfile

import shapeit_source_validation as validation


class SourceValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.data=Path(self.temp.name)/'data';self.data.mkdir()
        for name in ['shapeit-source-build-validation.json','shapeit-source-build-controls.zip','shapeit-managed-common-validation.json','shapeit-managed-common-controls.zip']:
            shutil.copyfile(validation.DATA/name,self.data/name)

    def verify(self):return validation.verify(validation.ROOT,self.data,emit=False)

    def record(self,change):
        path=self.data/'shapeit-source-build-validation.json';record=json.loads(path.read_bytes());change(record);path.write_text(json.dumps(record))

    def mutate(self,name,change):
        path=self.data/'shapeit-source-build-controls.zip'
        with zipfile.ZipFile(path) as source:members={n:source.read(n) for n in source.namelist()}
        members[name]=change(members[name])
        members['SHA256SUMS']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(members.items()) if name!='SHA256SUMS').encode()
        with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as target:
            for name,raw in members.items():target.writestr(name,raw)
        self.record(lambda record:record['artifact'].update(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))

    def test_complete_current_source_and_forty_public_protocols_pass(self):
        result=self.verify();self.assertEqual(result['actual_successful_fits'],18)
        self.assertEqual(result['unique_control_datasets'],9);self.assertEqual(result['installer_linux_tests'],24)

    def test_changed_installer_source_is_rejected_before_any_old_source_fallback(self):
        root=Path(self.temp.name)/'changed-root'
        for name in validation.SCIENCE:
            target=root/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(validation.ROOT/name,target)
        path=root/'tools/literature/build_shapeit_runtime.py';path.write_bytes(path.read_bytes()+b'\n# unverified change\n')
        with self.assertRaisesRegex(AssertionError,'build_shapeit_runtime'):validation.verify(root,self.data,emit=False)

    def test_unvalidated_editions_and_phase_accuracy_cannot_be_relabelled_complete(self):
        for field in ['whole_paper_reproduced','phase_accuracy_certified','historical_editions_reproduced','binary_genotype_payloads_validated','released_or_deployed']:
            self.record(lambda record:record.update({field:True}))
            with self.assertRaises(AssertionError):self.verify()
            self.record(lambda record:record.update({field:False}))

    def test_rehashed_installer_report_cannot_claim_scientific_admission(self):
        def change(raw):
            record=json.loads(raw);record['scientific_analysis_admitted']=True;return json.dumps(record).encode()
        self.mutate('setup/3/report.json',change)
        with self.assertRaises(AssertionError):self.verify()

    def test_rehashed_runtime_census_cannot_substitute_a_library(self):
        def change(raw):
            record=json.loads(raw);record[1]['files']['lib/libhts.so.3']['sha256']='0'*64;return json.dumps(record).encode()
        self.mutate('evidence/runtime-census.json',change)
        with self.assertRaises(AssertionError):self.verify()

    def test_loader_fallback_is_rejected_even_if_every_basename_is_expected(self):
        with zipfile.ZipFile(self.data/'shapeit-source-build-controls.zip') as bundle:
            text=bundle.read('evidence/workflows/2/common/cohort-default/analysis/fit-process.loader-check/process.stdout').decode()
        lock=json.loads((validation.ROOT/'tools/literature/shapeit-runtime-lock.json').read_bytes())
        home='/opt/omicsmatic-shapeit-source-runtime-attempt-3';validation.private_bindings(text,home,lock['bindings']['phase_common'])
        altered=text.replace(home+'/lib/libhts.so.3','/unverified/lib/libhts.so.3')
        self.assertNotEqual(text,altered)
        with self.assertRaises(AssertionError):validation.private_bindings(altered,home,lock['bindings']['phase_common'])

    def test_full_record_payload_and_nondate_header_mutations_are_rejected(self):
        with zipfile.ZipFile(self.data/'shapeit-source-build-controls.zip') as bundle:
            current=bundle.read('evidence/workflows/2/common/cohort-default/analysis/phased.bcf')
        with zipfile.ZipFile(self.data/'shapeit-managed-common-controls.zip') as bundle:
            prior=bundle.read('workflow/public-cli/cohort-default/analysis/phased.bcf')
        self.assertEqual(validation.original_record_parity(current,prior),126)
        raw=gzip.decompress(current);altered=raw[:-1]+bytes([raw[-1]^1])
        with self.assertRaisesRegex(AssertionError,'record payload'):validation.original_record_parity(gzip.compress(altered,mtime=0),prior)
        self.assertIn(b'##source=SHAPEIT5',raw)
        changed=raw.replace(b'##source=SHAPEIT5',b'##source=UNTRUST5')
        with self.assertRaisesRegex(AssertionError,'other than fileDate'):validation.original_record_parity(gzip.compress(changed,mtime=0),prior)

    def test_expected_transport_skip_cannot_be_promoted_to_scientific_success(self):
        def change(raw):
            record=json.loads(raw);record['exit_code']=0;record['scientific_result_admitted']=True;return json.dumps(record).encode()
        self.mutate('evidence/workflows/2/transport/plain-vcf/session.json',change)
        with self.assertRaises(AssertionError):self.verify()

    def test_rehashed_reduced_linux_control_census_is_rejected(self):
        self.mutate('setup/3/unit-linux-final.log',lambda raw:raw.replace(b'Ran 24 tests ',b'Ran 23 tests '))
        with self.assertRaises(AssertionError):self.verify()


if __name__=='__main__':unittest.main()
