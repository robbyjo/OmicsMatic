# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile
import shapeit_managed_validation as validation


class ManagedValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.data=self.root/'docs/literature/omics-2023-2026';self.data.mkdir(parents=True)
        for name in validation.SCIENCE:
            dest=self.root/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(validation.ROOT/name,dest)
        for path in validation.DATA.glob('*-shared-source-compatibility.json'):shutil.copyfile(path,self.data/path.name)
        for name in ['shapeit-managed-common-validation.json','shapeit-managed-common-controls.zip']:shutil.copyfile(validation.DATA/name,self.data/name)
    def verify(self):return validation.verify(self.root,self.data,emit=False)
    def record(self,change):
        path=self.data/'shapeit-managed-common-validation.json';record=json.loads(path.read_bytes());change(record);path.write_text(json.dumps(record),encoding='utf8')
    def mutate(self,name,change):
        path=self.data/'shapeit-managed-common-controls.zip'
        with zipfile.ZipFile(path) as z:members={n:z.read(n) for n in z.namelist()}
        members[name]=change(members[name]);members['SHA256SUMS']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(members.items()) if name!='SHA256SUMS').encode()
        with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
            for name,raw in members.items():z.writestr(name,raw)
        self.record(lambda r:r['artifact'].update(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size))
    def test_current_complete_evidence_passes(self):self.assertEqual(self.verify()['actual_successful_fits'],9)
    def test_changed_scientific_source_is_rejected(self):
        path=self.root/'src/main/java/org/jlinalg/cli/ShapeitCommonPhasing.java';path.write_bytes(path.read_bytes()+b'\n//changed\n')
        with self.assertRaises(AssertionError):self.verify()
    def test_unsupported_scopes_cannot_be_relabelled_complete(self):
        for field in ['download_source_build_installer_available','binary_genotype_payloads_validated','historical_editions_reproduced','pedigree_haploid_rare_ligation_validated','phase_accuracy_certified','released_or_deployed','whole_paper_reproduced']:
            self.record(lambda r:r.update({field:True}))
            with self.assertRaises(AssertionError):self.verify()
            self.record(lambda r:r.update({field:False}))
    def test_reference_AN_departure_cannot_be_erased(self):
        self.record(lambda r:r.update(source_AN_includes_reference_rows=0))
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_output_change_is_rejected(self):
        self.mutate('workflow/public-cli/cohort-default/analysis/phased.bcf',lambda raw:raw[:-1])
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_reduced_test_census_is_rejected(self):
        self.mutate('matrix/windows-java25.stdout',lambda raw:raw.replace(b'380 tests successful',b'379 tests successful'))
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_runtime_installation_cannot_claim_scientific_success(self):
        def change(raw):
            value=json.loads(raw);value['scientific_analysis_admitted']=True;return json.dumps(value).encode()
        self.mutate('workflow/installed-runtime-marker.json',change)
        with self.assertRaises(AssertionError):self.verify()
    def test_expected_skip_cannot_be_promoted_to_completed_analysis(self):
        def change(raw):
            value=json.loads(raw);value['exit_code']=0;value['scientific_result_admitted']=True;return json.dumps(value).encode()
        self.mutate('workflow/transport/plain-vcf/session.json',change)
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_supervision_class_substitution_is_rejected(self):
        self.mutate('capture/prior-process-candidate.jar',lambda raw:raw[:-20])
        with self.assertRaises((AssertionError,zipfile.BadZipFile)):self.verify()


if __name__=='__main__':unittest.main()
