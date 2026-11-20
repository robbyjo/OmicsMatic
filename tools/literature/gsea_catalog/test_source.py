"""Prevent provider/version and unresolved-source promotion in the source audit."""
import copy,unittest
from gsea_catalog import primary as p

class GseaSourceControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.source=p.frozen()

    def test_reported_clusterprofiler_433_is_not_Broad_433(self):
        source=copy.deepcopy(self.source);role=next(r for r in source['roles'] if r['paper_id']=='P040')
        role['matching_Broad_source_profiles']=['4.3.3']
        with self.assertRaisesRegex(ValueError,'provider/executable-edition'):p.validate(source)

    def test_resource_year_cannot_select_a_Broad_runtime(self):
        source=copy.deepcopy(self.source);role=next(r for r in source['roles'] if r['paper_id']=='P096')
        role['matching_Broad_source_profiles']=['4.3.3'];role['author_executable_edition_unresolved']=False
        with self.assertRaises(ValueError):p.validate(source)

    def test_chEA_hypergeometric_anchor_cannot_replace_ranked_RNA_role(self):
        source=copy.deepcopy(self.source);role=next(r for r in source['roles'] if r['paper_id']=='P098')
        role['primary_evidence']=role['supporting_evidence'][1]
        with self.assertRaisesRegex(ValueError,'role anchors'):p.validate(source)

    def test_nonmatching_fgsea_sentence_is_required_provider_evidence(self):
        source=copy.deepcopy(self.source);role=next(r for r in source['roles'] if r['paper_id']=='P170')
        role['primary_evidence']=role['supporting_evidence'][0]
        with self.assertRaisesRegex(ValueError,'role anchors'):p.validate(source)

    def test_generic_package_capability_cannot_add_unresolved_role(self):
        source=copy.deepcopy(self.source);source['roles'].append(dict(paper_id='P032'))
        with self.assertRaisesRegex(ValueError,'role/candidate'):p.validate(source)

    def test_source_audit_cannot_award_catalog_credit(self):
        source=copy.deepcopy(self.source);source['new_family_credit']=1
        with self.assertRaisesRegex(ValueError,'cannot award credit'):p.validate(source)

    def test_other_provider_or_full_author_workflow_cannot_be_claimed(self):
        source=copy.deepcopy(self.source);source['roles'][0]['other_provider_backend_qualified_by_Broad']=True
        with self.assertRaisesRegex(ValueError,'completion claim'):p.validate(source)

    def test_author_scripts_are_original_read_only_source_evidence(self):
        aux=p.auxiliary();self.assertFalse(aux['p096']['code_executed']);self.assertEqual(29,aux['p096']['scripts']);self.assertEqual('timestamp',aux['p096']['script20_options']['-rnd_seed'])
        self.assertFalse(aux['p096']['ranking_file_export_identified']);self.assertFalse(aux['p077']['GSEA_execution_options_resolved'])

if __name__=='__main__':unittest.main()
