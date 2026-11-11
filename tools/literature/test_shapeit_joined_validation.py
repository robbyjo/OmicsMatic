#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import contextlib,hashlib,importlib.util,io,json,shutil,tempfile,unittest,zipfile
from pathlib import Path
import shapeit_joined_validation as validation

class JoinedValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.data=self.root/'docs/literature/omics-2023-2026';self.data.mkdir(parents=True)
        for path in validation.SCIENCE:
            target=self.root/path;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(validation.ROOT/path,target)
        for name in ['shapeit-joined-validation.json','shapeit-joined-native-controls.zip']:shutil.copyfile(validation.DATA/name,self.data/name)
    def verify(self):
        with contextlib.redirect_stdout(io.StringIO()):return validation.verify(self.root,self.data)
    def metadata(self,change):
        path=self.data/'shapeit-joined-validation.json';record=json.loads(path.read_bytes());change(record);path.write_text(json.dumps(record),encoding='utf-8')
    def mutate_control(self,member,change):
        path=self.data/'shapeit-joined-native-controls.zip'
        with zipfile.ZipFile(path) as z:members={name:z.read(name) for name in z.namelist()}
        members[member]=change(members[member]);members['SHA256SUMS']=''.join(hashlib.sha256(raw).hexdigest()+'  '+name+'\n' for name,raw in sorted(members.items()) if name!='SHA256SUMS').encode()
        with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as z:
            for name,raw in members.items():z.writestr(name,raw)
        self.metadata(lambda r:r['artifact'].update(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size))
    def test_current_evidence_is_valid(self):self.assertEqual(self.verify()['original_joined_events'],181)
    def test_scientific_source_change_is_rejected(self):
        path=self.root/'src/main/java/org/jlinalg/cli/ShapeitJoinedRecords.java';path.write_bytes(path.read_bytes()+b'\n// changed\n')
        with self.assertRaises(AssertionError):self.verify()
    def test_public_phasing_claim_is_rejected(self):
        self.metadata(lambda r:r.update(public_phasing_adapter_available=True))
        with self.assertRaises(AssertionError):self.verify()
    def test_runtime_or_binary_admission_claims_are_rejected(self):
        for key in ['runtime_admission_certified','binary_genotype_payloads_validated','phase_accuracy_certified']:
            self.metadata(lambda r:r.update({key:True}))
            with self.assertRaises(AssertionError):self.verify()
            self.metadata(lambda r:r.update({key:False}))
    def test_changed_archive_is_rejected(self):
        path=self.data/'shapeit-joined-native-controls.zip';path.write_bytes(path.read_bytes()+b'changed')
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_incomplete_test_transcript_is_rejected(self):
        self.mutate_control('matrix/windows-java21.stdout',lambda raw:raw.replace(b'171 tests successful',b'170 tests successful'))
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_dependency_substitution_is_rejected(self):
        self.mutate_control('matrix/lib/htsjdk-5.0.0.jar',lambda raw:raw+b'changed')
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_wrong_runtime_major_is_rejected(self):
        def alter(raw):
            record=json.loads(raw);record['windows-java25']['major']=17;return json.dumps(record).encode()
        self.mutate_control('matrix/runtime-versions.json',alter)
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_dependency_source_substitution_is_rejected(self):
        self.mutate_control('matrix/dependency-sources/htsjdk-5.0.0-source.zip',lambda raw:raw+b'changed')
        with self.assertRaises(AssertionError):self.verify()
    def test_expected_failures_cannot_be_relabelled_complete(self):
        self.metadata(lambda r:r.update(original_complete_streams=29,original_expected_failures=0))
        with self.assertRaises(AssertionError):self.verify()

if __name__=='__main__':unittest.main()
