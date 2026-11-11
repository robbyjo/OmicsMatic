# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rehashed corruption/traversal controls for lossless content-addressed evidence packaging."""
import copy,json,tempfile,unittest,zipfile
from pathlib import Path
from signac_scan_evidence import pack,unpack,byte_pin

class EvidenceContainerTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.path=self.root/'controls.zip';self.files={'source/file.R':b'unchanged source\n','capture/Unicode α/file.R':b'unchanged source\n','capture/result.tsv':b'x\tvalue\n1\t0.5\n'};self.record=pack(self.path,self.files)
    def rewrite(self,changes,additions=None):
        target=self.root/'altered.zip'
        with zipfile.ZipFile(self.path) as before,zipfile.ZipFile(target,'x') as after:
            for name in before.namelist():after.writestr(name,changes.get(name,before.read(name)))
            for name,raw in (additions or {}).items():after.writestr(name,raw)
        return target
    def test_all_logical_bytes_round_trip_with_shared_contents(self):
        self.assertEqual(unpack(self.path,self.record),self.files);self.assertEqual(self.record['logical_files'],3);self.assertEqual(self.record['archive_members'],3)
    def test_rehashed_object_cannot_keep_an_unrelated_content_name(self):
        record=copy.deepcopy(self.record);name=next(n for n in record['archive_file_pins'] if n.startswith('objects/'));raw=b'altered scientific output';record['archive_file_pins'][name]=byte_pin(raw)
        with self.assertRaisesRegex(ValueError,'SHA-256 name'):unpack(self.rewrite({name:raw}),record)
    def test_rehashed_logical_mapping_cannot_escape_extraction_root(self):
        record=copy.deepcopy(self.record);record['logical_file_pins']['../escape']=record['logical_file_pins'].pop('source/file.R');raw=(json.dumps(dict(schema_version=1,files=record['logical_file_pins']))+'\n').encode();record['archive_file_pins']['logical-files.json']=byte_pin(raw)
        with self.assertRaisesRegex(ValueError,'Unsafe logical'):unpack(self.rewrite({'logical-files.json':raw}),record)
    def test_rehashed_extra_object_is_not_silently_ignored(self):
        record=copy.deepcopy(self.record);raw=b'undocumented payload';value=byte_pin(raw);name='objects/'+value['sha256'];record['archive_file_pins'][name]=value;record['archive_members']+=1
        with self.assertRaisesRegex(ValueError,'Unreferenced'):unpack(self.rewrite({}, {name:raw}),record)

if __name__=='__main__':unittest.main()
