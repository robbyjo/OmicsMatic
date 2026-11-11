# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import hashlib,io,json,shutil,tempfile,unittest,zipfile
from pathlib import Path
import iqtree_workflow_validation as validation

class IqtreeWorkflowValidationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.data=Path(self.temp.name)/'data';self.data.mkdir()
        for name in ['iqtree-workflow-validation.json','iqtree-workflow-controls.zip']:
            shutil.copyfile(validation.DATA/name,self.data/name)
        for path in validation.DATA.glob('*-shared-source-compatibility.json'):
            shutil.copyfile(path,self.data/path.name)
    def verify(self):return validation.verify(validation.ROOT,self.data,emit=False)
    def record(self,change):
        path=self.data/'iqtree-workflow-validation.json';record=json.loads(path.read_bytes());change(record);path.write_text(json.dumps(record),encoding='utf-8')
    def mutate(self,name,change):
        path=self.data/'iqtree-workflow-controls.zip'
        with zipfile.ZipFile(path) as bundle:members={name:bundle.read(name) for name in bundle.namelist()}
        members[name]=change(members[name]);members['SHA256SUMS']=''.join(validation.sha(raw)+'  '+name+'\n' for name,raw in sorted(members.items()) if name!='SHA256SUMS').encode()
        with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as bundle:
            for name,raw in members.items():bundle.writestr(name,raw)
        self.record(lambda r:r['artifact'].update(bytes=path.stat().st_size,sha256=validation.sha(path.read_bytes())))
    def test_complete_retained_public_workflows_and_current_source_pass(self):
        result=self.verify();self.assertEqual(result['completed_fits'],17);self.assertEqual(result['bootstrap_trees'],1100)
        self.assertEqual(result['support_edges'],36);self.assertEqual(result['model_candidates'],71)
    def test_scientific_scope_cannot_be_relabelled_complete(self):
        for field in ['whole_paper_reproduced','biological_accuracy_certified','partitions_available','dating_available','batch_gene_trees_available','custom_mixture_models_available','released_or_deployed']:
            self.record(lambda r:r.update({field:True}))
            with self.assertRaises(AssertionError):self.verify()
            self.record(lambda r:r.update({field:False}))
    def test_current_scientific_source_change_cannot_use_dispatcher_compatibility(self):
        root=Path(self.temp.name)/'changed-source'
        for name in validation.SCIENCE:
            target=root/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(validation.ROOT/name,target)
        path=root/'src/main/java/org/jlinalg/cli/IqtreeAnalysis.java';path.write_bytes(path.read_bytes()+b'\n// unverified change\n')
        with self.assertRaises(AssertionError):validation.verify(root,self.data,emit=False)
    def test_rehashed_bundle_byte_changes_are_still_rejected_by_original_manifest(self):
        self.mutate('cases/DNA-JC/bundle/tree.nwk',lambda raw:raw+b'\n')
        with self.assertRaises(AssertionError):self.verify()
    def test_missing_late_bootstrap_tree_is_not_a_complete_retained_bundle(self):
        self.mutate('cases/UFB-SHalrt/bundle/bootstrap-trees.nwk',lambda raw:raw[:raw.rfind(b';',0,raw.rfind(b';'))+1])
        with self.assertRaises(AssertionError):self.verify()
    def test_rehashed_publication_cannot_reduce_packaged_test_population(self):
        def change(raw):
            record=json.loads(raw);record['packaged']['focused_Linux_passed']=529;return json.dumps(record).encode()
        self.mutate('validation/publication.json',change)
        with self.assertRaises(AssertionError):self.verify()
    def test_count_metadata_must_match_every_complete_public_protocol(self):
        self.record(lambda r:r['observed'].update(completed_fits=16))
        with self.assertRaises(AssertionError):self.verify()
    def test_duplicate_and_unsafe_archive_members_are_rejected(self):
        for name in ['../escape','/absolute','bad'+chr(92)+'separator','C:/absolute','a/./b']:
            output=io.BytesIO()
            with zipfile.ZipFile(output,'w') as bundle:bundle.writestr(name,b'bad')
            with self.assertRaises(AssertionError):validation.archive(output.getvalue())

if __name__=='__main__':unittest.main()
