"""Meaningful scope, raw-evidence and unrelated-record controls for the catalog transition."""
import copy,json,subprocess,unittest
from impute_catalog import model as m,primary as p

BASE='d8165e5d8301a1eec011413d3d316d4bc2c5f6d0'
class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META};cls.primary=p.frozen();cls.q=m.qualification()
        cls.after=m.transition(cls.before,cls.primary,cls.q)
    def test_exact_scoped_counts_and_every_prior_review_remain(self):
        r=m.check(self.before,self.after,self.primary,self.q)
        self.assertEqual((r['observations'],r['reviewed_uses'],r['pending_uses'],r['scoped_families']),(2724,1714,1010,157));self.assertEqual(r['unchanged_prior_reviews'],1707);self.assertFalse(r['whole_goal_complete'])
    def test_missed_p116_is_a_declaration_with_no_automatic_method_score(self):
        papers=json.loads(self.after[m.META[0]]);u=next(x for x in next(p for p in papers if p['paper_id']=='P116')['tools'] if x['tool_id']=='impute')
        self.assertIsNone(u['extraction_score']);self.assertEqual(u['evidence_paragraph_index'],45);self.assertEqual(u['evidence_section'],'Code availability');self.assertEqual(u['version_reported_near_name'],'2.3.1');self.assertEqual(u['source_role_qualification']['available_operation_types'],[])
    def test_moba_context_version_does_not_replace_raw_null(self):
        papers=json.loads(self.after[m.META[0]]);u=next(x for x in next(p for p in papers if p['paper_id']=='P140')['tools'] if x['tool_id']=='impute')
        self.assertIsNone(u['version_reported_near_name']);self.assertEqual(u['source_role_qualification']['reported_version_contexts'][0]['tool_edition'],'4.1.2_r300.3');self.assertEqual(u['source_role_qualification']['available_operation_types'],[])
    def test_ukbb_quirks_and_distinct_info_boundary_are_not_repaired(self):
        roles={r['paper_id']:r for r in self.primary['roles']};settings=roles['P084']['reported_settings']
        self.assertEqual(settings['source_remove_mac'],'>100');self.assertEqual(settings['source_quality_label'],'Minimac r2');self.assertEqual(roles['P076']['reported_settings']['retain_info'],'>0.3');self.assertEqual(roles['P136']['reported_settings']['ToMMo_remove_info'],'<0.3')
    def test_analogy_and_code_list_do_not_become_an_execution(self):
        role=next(r for r in self.primary['roles'] if r['paper_id']=='P149');self.assertFalse(role['reported_settings']['local_operation_established']);self.assertEqual(role['available_operation_types'],[]);self.assertFalse(role['original_paper_analysis_reexecuted'])
    def test_versions_and_bgen_and_x_boundaries_stay_explicit(self):
        roles={r['paper_id']:r for r in self.primary['roles']};self.assertIn('IMPUTE4',roles['P140']['historical_edition_analysis_status']);self.assertIn('IMPUTE5',roles['P180']['historical_edition_analysis_status']);self.assertIn('BGEN conversion',roles['P136']['boundary']);self.assertIn('BEAGLE4.1',roles['P136']['boundary']);self.assertEqual(self.q['only_qualified_tool_edition'],'2.3.2')
    def test_forged_native_family_credit_or_changed_qualification_is_refused(self):
        q=copy.deepcopy(self.q);q['only_qualified_tool_edition']='5'
        with self.assertRaisesRegex(ValueError,'qualification'):m.transition(self.before,self.primary,q)
        modified=dict(self.after);inventory=json.loads(modified[m.META[1]]);next(r for r in inventory if r['id']=='impute')['new_native_family_credit']=1;modified[m.META[1]]=p.encoded(inventory)
        with self.assertRaises(ValueError):m.check(self.before,modified,self.primary,self.q)
    def test_changed_unrelated_use_and_prior_review_are_refused(self):
        modified=dict(self.after);papers=json.loads(modified[m.META[0]]);papers[0]['tools'][0]['use_summary']='unrelated edit';modified[m.META[0]]=p.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,modified,self.primary,self.q)
        modified=dict(self.after);ledger=json.loads(modified[m.META[5]]);ledger['reviews'][0]['use_summary']='unrelated edit';modified[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,modified,self.primary,self.q)
    def test_fabricated_p116_score_and_replaced_moba_raw_version_are_refused(self):
        for paper_id,key,value in [('P116','extraction_score',15),('P140','version_reported_near_name','4.1.2_r300.3')]:
            modified=dict(self.after);papers=json.loads(modified[m.META[0]]);next(u for u in next(r for r in papers if r['paper_id']==paper_id)['tools'] if u['tool_id']=='impute')[key]=value;modified[m.META[0]]=p.encoded(papers)
            with self.assertRaises(ValueError):m.check(self.before,modified,self.primary,self.q)
    def test_repaired_qc_or_fabricated_source_fit_are_refused(self):
        for paper_id,change in [('P084',lambda r:r['reported_settings'].update(source_remove_mac='<100')),('P149',lambda r:r.update(original_paper_analysis_reexecuted=True))]:
            roles=copy.deepcopy(self.primary);change(next(r for r in roles['roles'] if r['paper_id']==paper_id))
            with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)
    def test_both_pages_have_one_current_notice_and_explicit_history(self):
        for name in m.META[6:]:
            text=self.after[name].decode();self.assertEqual(text.count('id="impute-catalog-notice"'),1);self.assertEqual(text.count('id="impute-prior-catalog"'),1);self.assertIn('Warnings and required actions',text)
        text=self.after[m.META[6]].decode();self.assertEqual(text.count('function imputeVersion(t)'),1);self.assertEqual(text.count('${imputeVersion(t)}'),1)

if __name__=='__main__':unittest.main()
