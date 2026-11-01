#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import contextlib,hashlib,io,json,shutil,tempfile,unittest,zipfile
from pathlib import Path
import shapeit_common_validation as validation
class CommonValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.data=self.root/'docs/literature/omics-2023-2026';self.data.mkdir(parents=True)
        for name in validation.SCIENCE:
            dest=self.root/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(validation.ROOT/name,dest)
        for name in ['shapeit-common-validation.json','shapeit-common-native-controls.zip']:shutil.copyfile(validation.DATA/name,self.data/name)
    def verify(self):
        with contextlib.redirect_stdout(io.StringIO()):return validation.verify(self.root,self.data)
    def metadata(self,change):
        path=self.data/'shapeit-common-validation.json';record=json.loads(path.read_bytes());change(record);path.write_text(json.dumps(record),encoding='utf-8')
    def mutate(self,member,change):
        path=self.data/'shapeit-common-native-controls.zip'
        with zipfile.ZipFile(path) as z:members={n:z.read(n) for n in z.namelist()}
        members[member]=change(members[member]);members['SHA256SUMS']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(members.items()) if name!='SHA256SUMS').encode()
        with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
            for name,raw in members.items():z.writestr(name,raw)
        self.metadata(lambda r:r['artifact'].update(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size))
    def test_current_contract_evidence_passes(self):self.assertEqual(self.verify()['applied_scaffold_constraints'],573)
    def test_changed_output_audit_source_is_rejected(self):
        path=self.root/'src/main/java/org/jlinalg/cli/ShapeitCommonOutputs.java';path.write_bytes(path.read_bytes()+b'\n//changed\n')
        with self.assertRaises(AssertionError):self.verify()
    def test_false_tool_or_runtime_completion_claims_are_rejected(self):
        for key in ['public_phasing_adapter_available','managed_runtime_admission_certified','binary_genotype_payloads_validated','haploid_pedigree_contracts_validated','phase_accuracy_certified']:
            self.metadata(lambda r:r.update({key:True}))
            with self.assertRaises(AssertionError):self.verify()
            self.metadata(lambda r:r.update({key:False}))
    def test_expected_original_failures_are_not_success(self):
        self.metadata(lambda r:r.update(original_complete_fits=12,expected_original_failures=0))
        with self.assertRaises(AssertionError):self.verify()
    def test_reference_AN_departure_cannot_be_erased(self):
        self.metadata(lambda r:r.update(source_AN_includes_reference_rows=0))
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_incomplete_test_log_is_rejected(self):
        self.mutate('matrix/windows-java21.stdout',lambda raw:raw.replace(b'349 tests successful',b'348 tests successful'))
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_dependency_substitution_is_rejected(self):
        self.mutate('matrix/lib/htsjdk-5.0.0.jar',lambda raw:raw+b'changed')
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_wrong_JVM_profile_is_rejected(self):
        def change(raw):
            record=json.loads(raw);record['windows-java25']['major']=17;return json.dumps(record).encode()
        self.mutate('matrix/runtime-versions.json',change)
        with self.assertRaises(AssertionError):self.verify()
    def test_source_path_set_cannot_be_weakened(self):
        self.metadata(lambda r:r['source_sha256'].pop('src/main/java/org/jlinalg/cli/ShapeitCommonCalls.java'))
        with self.assertRaises(AssertionError):self.verify()
if __name__=='__main__':unittest.main()
