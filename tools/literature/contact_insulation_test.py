"""Catalog contract regressions: scope, source editions, existing reviews and unrelated data."""
from pathlib import Path
import copy,json,unittest,tempfile
import contact_insulation_catalog as catalog
import contact_insulation_model as model

ROOT=Path(__file__).resolve().parents[2]

class ContactCatalogTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=catalog.before(ROOT);cls.roles=catalog.source_roles(ROOT);cls.q=json.loads((ROOT/model.Q).read_bytes())
    def after(self):return model.transition(self.before,self.roles,self.q)
    def test_one_family_seven_new_reviews_and_one_expansion(self):
        result=model.check(self.before,self.after(),self.roles,self.q)
        self.assertEqual((result['scoped_families'],result['native'],result['upstream']),(167,33,134))
        self.assertEqual((result['reviewed_uses'],result['new_source_role_reviews'],result['existing_source_review_expanded']),(1809,7,1))
        self.assertEqual(result['observations'],2731);self.assertFalse(result['ALLCools_implementation_qualified'])
    def test_incomplete_execution_or_expanded_scope_cannot_get_credit(self):
        for field,value in [('source_component_cases_each_OS',458),('corrupted_capture_refusals_each_OS',6),('Linux_focused_JUnit',0),('whole_package_qualified',True),('ALLCools_implementation_qualified',True),('original_selected_functions_executed',False)]:
            q=copy.deepcopy(self.q);q[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):model.transition(self.before,self.roles,q)
    def test_a_dots_edition_does_not_identify_the_insulation_edition(self):
        roles=copy.deepcopy(self.roles);role=next(r for r in roles['roles']if(r['paper_id'],r['tool_id'])==('P003','cooltools'));role['literal_package_version_reported_elsewhere']='0.5.1'
        with self.assertRaises(ValueError):model.transition(self.before,roles,self.q)
    def test_eigenvectors_and_strict_boundary_rule_remain_distinct(self):
        for pair,key,value in [(('P228','cooltools'),'distinct_uses',['TAD calling']),(('P053','cooltools'),'distinct_uses',['boundary strength >=0.1'])]:
            roles=copy.deepcopy(self.roles);role=next(r for r in roles['roles']if(r['paper_id'],r['tool_id'])==pair);role[key]=value
            with self.subTest(pair=pair),self.assertRaises(ValueError):model.transition(self.before,roles,self.q)
    def test_existing_partial_review_is_updated_in_place_without_duplication(self):
        reviews=json.loads(self.after()[model.META[5]])['reviews'];old=json.loads(self.before[model.META[5]])['reviews']
        position=next(i for i,r in enumerate(old)if(r['pmcid'],r['tool_id'])==model.PRIOR_REVIEW)
        self.assertEqual((reviews[position]['pmcid'],reviews[position]['tool_id']),model.PRIOR_REVIEW)
        self.assertEqual(sum((r['pmcid'],r['tool_id'])==model.PRIOR_REVIEW for r in reviews),1)
        duplicate=copy.deepcopy(self.roles);duplicate['roles'].append(copy.deepcopy(duplicate['roles'][0]))
        with self.assertRaises(ValueError):model.transition(self.before,duplicate,self.q)
    def test_raw_version_and_unrelated_scientific_role_changes_are_refused(self):
        roles=copy.deepcopy(self.roles);roles['roles'][0]['raw_catalog_observation_preserved']['version_reported_near_name']='guessed'
        with self.assertRaises(ValueError):model.transition(self.before,roles,self.q)
        after=self.after();papers=json.loads(after[model.META[0]]);use=next(u for p in papers if p['paper_id']=='P029'for u in p['tools']if u['tool_id']=='diann');use['version_reported_near_name']='guessed';after[model.META[0]]=json.dumps(papers).encode()
        with self.assertRaises(ValueError):model.check(self.before,after,self.roles,self.q)
    def test_native_insulation_never_implements_the_allcools_or_eigs_role(self):
        inventory=json.loads(self.after()[model.META[1]]);allcools=next(r for r in inventory if r['id']=='allcools');cooltools=next(r for r in inventory if r['id']=='cooltools')
        self.assertEqual(allcools['implementation_status'],'missing');self.assertTrue(all(not r['implementation_available']for r in allcools['paper_role_qualification'].values()))
        self.assertFalse(cooltools['paper_role_qualification']['P228']['implementation_available'])
        self.assertTrue(all(not r['full_source_role_implemented']for r in cooltools['paper_role_qualification'].values()))
    def test_dynamic_evidence_members_cannot_escape_their_declared_root(self):
        for name in ('','/absolute','../parent','a/../parent','a//b','a/./b','C:drive-relative','a\\b'):
            with self.subTest(name=name):self.assertFalse(catalog.safe_relative(name))
        self.assertTrue(catalog.safe_relative('valid/path.json'))
    def test_extra_empty_capture_directories_cannot_be_admitted(self):
        with tempfile.TemporaryDirectory()as temporary:
            root=Path(temporary).resolve();(root/'inputs').mkdir();(root/'outputs').mkdir()
            declared={'inputs/bins.tsv','outputs/insulation.tsv','request.json'}
            for name in declared|{'manifest.json','COMPLETED.json'}:(root/name).write_bytes(b'x')
            catalog.capture_namespace(root,declared);(root/'unlisted-empty').mkdir()
            with self.assertRaises(ValueError):catalog.capture_namespace(root,declared)

if __name__=='__main__':unittest.main()
