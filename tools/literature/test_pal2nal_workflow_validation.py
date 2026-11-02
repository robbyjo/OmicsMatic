# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import copy,io,json,stat,unittest,zipfile
from pathlib import Path
from pal2nal_workflow_validation import (GUIDE,OLD_GUIDE,NEW_GUIDE,object_,safe_path,sha,unpack,verify,verify_case,verify_guide)


class EvidenceBoundaries(unittest.TestCase):
    def test_noncanonical_paths_and_foreign_roots_are_rejected(self):
        for name in ['','.', '../x','/x','C:/x','a\\b','a//b','a/./b','a/../b']:
            with self.subTest(name=name),self.assertRaises(AssertionError):safe_path(name)
    def test_duplicate_json_keys_fail(self):
        with self.assertRaises(AssertionError):object_(b'{"x":1,"x":2}')
    def archive(self,name='item',link=False):
        out=io.BytesIO()
        with zipfile.ZipFile(out,'w') as z:
            info=zipfile.ZipInfo(name)
            if link:info.create_system=3;info.external_attr=(stat.S_IFLNK|0o777)<<16
            z.writestr(info,b'payload')
        return out.getvalue()
    def test_link_members_are_rejected(self):
        with self.assertRaises(AssertionError):unpack(self.archive(link=True))
    def test_no_extraction_of_parent_traversal(self):
        with self.assertRaises(AssertionError):unpack(self.archive('../escape'))
    def test_archive_file_and_expanded_byte_bounds_are_enforced(self):
        for count,size in [(0,100),(10,1)]:
            with self.assertRaises(AssertionError):unpack(self.archive(),count,size)
    def guide(self):
        before=b'prefix\n'+OLD_GUIDE+b'suffix\n';after=before.replace(OLD_GUIDE,NEW_GUIDE)
        return before,after,dict(path=GUIDE,before_sha256=sha(before),after_sha256=sha(after))
    def test_exact_status_paragraph_preserves_every_other_guide_byte(self):verify_guide(*self.guide())
    def test_rehashed_extra_document_claim_is_not_accepted(self):
        before,after,proof=self.guide();after+=b'All biological alignments are certified.\n';proof['after_sha256']=sha(after)
        with self.assertRaises(AssertionError):verify_guide(before,after,proof)
    def test_document_exception_cannot_be_relabelled_as_scientific_source(self):
        before,after,proof=self.guide();proof['path']='src/main/java/org/jlinalg/sequence/Pal2Nal.java'
        with self.assertRaises(AssertionError):verify_guide(before,after,proof)


class NativeCaseMutation(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root=Path(__file__).resolve().parents[2];raw=(root/'docs/literature/omics-2023-2026/pal2nal-native-controls.zip').read_bytes();cls.matrix=unpack(raw)
        prefix='v14/frameshift-4/result/';cls.baseline={name[len(prefix):]:raw for name,raw in cls.matrix.items() if name.startswith(prefix)}
        cls.tables={}
        for line in (root/'src/main/resources/org/jlinalg/omics/pal2nal-v14-codes.tsv').read_text().splitlines():
            table,aa,regex=line.split('\t');cls.tables.setdefault(int(table),{})[aa]=regex
    def mutate(self,name,change):
        files=copy.deepcopy(self.baseline);files[name]=change(files[name]);manifest=object_(files['manifest.json'])
        for key in manifest['files']:manifest['files'][key]=dict(bytes=len(files[key]),sha256=sha(files[key]))
        files['manifest.json']=(json.dumps(manifest)+'\n').encode();marker=object_(files['COMPLETED.json']);marker['manifest_sha256']=sha(files['manifest.json']);files['COMPLETED.json']=(json.dumps(marker)+'\n').encode();return files
    def test_real_frame_shift_case_has_complete_cell_and_triplet_census(self):
        result=verify_case(self.baseline,self.tables);self.assertEqual(result['cells'],8);self.assertEqual(result['triplets'],5)
    def test_rehashed_coordinate_change_fails(self):
        files=self.mutate('cells.tsv',lambda raw:raw.replace(b'1\t3\t7\tCCCC',b'1\t3\t8\tCCCC'))
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_rehashed_missing_cell_is_not_a_completed_prefix(self):
        files=self.mutate('cells.tsv',lambda raw:b'\n'.join(raw.splitlines()[:-1])+b'\n')
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_rehashed_filter_coordinate_change_fails(self):
        files=self.mutate('triplets.tsv',lambda raw:raw.replace(b'1\t1\t1\t1\t',b'1\t1\t1\t4\t'))
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_rehashed_row_verification_counts_fail(self):
        files=self.mutate('rows.ndjson',lambda raw:raw.replace(b'"verified_residues":3',b'"verified_residues":0'))
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_rehashed_missing_alignment_row_fails(self):
        files=self.mutate('alignment.fasta',lambda raw:raw.split(b'>p2')[0])
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_rehashed_replay_cannot_change_original_scientific_option(self):
        def change(raw):
            recipe=object_(raw);recipe['options']['codon_table']=6;return json.dumps(recipe).encode()
        with self.assertRaises(AssertionError):verify_case(self.mutate('recipe-replay.json',change),self.tables)
    def test_rehashed_heap_receipt_cannot_expand_effective_limits(self):
        def change(raw):
            session=object_(raw);session['runtime_heap_bytes']=33554432;return json.dumps(session).encode()
        with self.assertRaises(AssertionError):verify_case(self.mutate('session.json',change),self.tables)
    def test_rehashed_manifest_cannot_change_declared_resource_ceilings(self):
        files=copy.deepcopy(self.baseline);manifest=object_(files['manifest.json']);manifest['limits']['bundleBytes']*=2;files['manifest.json']=json.dumps(manifest).encode();marker=object_(files['COMPLETED.json']);marker['manifest_sha256']=sha(files['manifest.json']);files['COMPLETED.json']=json.dumps(marker).encode()
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_rehashed_zero_verification_warning_cannot_be_hidden(self):
        original=self.baseline
        try:
            prefix='v14/weak-relaxed-success/result/';self.baseline={name[len(prefix):]:raw for name,raw in self.matrix.items() if name.startswith(prefix)}
            files=self.mutate('WARNINGS.md',lambda raw:raw.replace(b'NO_VERIFIED_RESIDUES',b'HIDDEN_ZERO_VERIFICATION'))
            with self.assertRaises(AssertionError):verify_case(files,self.tables)
        finally:self.baseline=original
    def test_rehashed_phylip_name_only_rows_are_rejected(self):
        files=self.mutate('alignment.phy',lambda raw:raw.replace(b'PN00000001 ',b'PN00000001\n'))
        with self.assertRaises(AssertionError):verify_case(files,self.tables)
    def test_empty_archive_is_never_a_native_completed_case(self):
        with self.assertRaises(AssertionError):verify_case({},self.tables)
    def test_extra_member_is_not_ignored(self):
        files=dict(self.baseline);files['extra.txt']=b'cannot hide'
        with self.assertRaises(AssertionError):verify_case(files,self.tables)


class FullCorpus(unittest.TestCase):
    def test_current_full_native_matrix_and_original_controls(self):
        result=verify(emit=False);self.assertEqual((result['protocols'],result['completed'],result['expected_failures']),(170,156,14));self.assertGreater(result['cells'],8000)


if __name__=='__main__':unittest.main()
