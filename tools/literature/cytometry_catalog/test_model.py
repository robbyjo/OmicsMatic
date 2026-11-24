"""Guard source identities, partial coverage and the cumulative review ledger."""
import copy,json,subprocess,unittest
from cytometry_catalog import model as m,primary as p
BASE='68c260fb2aede0b0ad4ee29a841179380cb162f7'
class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META};cls.roles=p.frozen();cls.q=m.qualification();cls.after=m.transition(cls.before,cls.roles,cls.q)
    def rows(self):return {(r['paper_id'],r['tool_id']):r for r in self.roles['roles']}
    def test_counts_and_all_unrelated_records(self):
        result=m.check(self.before,self.after,self.roles,self.q);self.assertEqual((result['scoped_families'],result['reviewed_uses'],result['pending_uses'],result['new_reviews'],result['existing_reviews_enriched']),(158,1755,969,41,3));self.assertEqual(result['unchanged_unrelated_observations'],2680);self.assertFalse(result['whole_goal_complete'])
    def test_complete_provider_snapshot_and_no_instrument_credit(self):
        rows=m.registry();self.assertEqual(len(rows),485);lookup={r['id']:r for r in rows};self.assertEqual(lookup['flowjo']['current_coverage'],'native-subset-validated');self.assertEqual(lookup['facsdiva']['current_coverage'],'missing')
    def test_facsdiva_declaration_is_not_an_inferred_call(self):
        r=self.rows()['P082','facsdiva'];self.assertEqual(r['role_kind'],'software-declaration');self.assertEqual(r['available_profiles'],[]);self.assertIn('no local FACSDiva',r['use_summary'])
    def test_unsupported_nine_and_two_cell_cycle_fits(self):
        rows=self.rows();self.assertIn('FLOWJO_9',rows['P121','flowjo']['historical_analysis_status'])
        for pid in ('P121','P122','P228'):self.assertEqual(rows[pid,'flowjo']['available_profiles'],[])
        self.assertIn('Dean-Jett-Fox',rows['P122','flowjo']['use_summary']);self.assertIn('Watson Pragmatic',rows['P228','flowjo']['use_summary'])
    def test_plugins_and_vendor_binning_are_unqualified(self):
        rows=self.rows();self.assertIn('PLUGINS',rows['P051','flowjo']['historical_analysis_status'])
        for pid in ('P032','P084','P163'):self.assertFalse(rows[pid,'flowjo']['vendor_statistics_qualified']);self.assertIn('UNQUALIFIED',rows[pid,'flowjo']['historical_analysis_status'])
        self.assertEqual(self.q['raw_R_underflow_differences'],2);self.assertEqual(self.q['compensation_zero_boundary_comparisons'],513)
    def test_literal_version_joint_roles_and_alternatives_remain(self):
        rows=self.rows();self.assertEqual(rows['P064','facsdiva']['reported_version'],'8.02');self.assertIn('or FCS Express 7',rows['P119','flowjo']['use_summary'])
        for pid in ('P031','P052'):self.assertEqual(rows[pid,'facsdiva']['role_kind'],'jointly-attributed-analysis-display')
    def test_every_paper_analysis_stays_skipped_without_original_inputs(self):
        for r in self.roles['roles']:self.assertTrue(r['historical_analysis_status'].startswith('SKIP3'));self.assertFalse(r['required_original_inputs_available']);self.assertFalse(r['original_paper_analysis_reexecuted'])
    def test_changed_qualification_or_fabricated_source_fit_refused(self):
        q=copy.deepcopy(self.q);q['vendor_statistics_qualified']=True
        with self.assertRaises(ValueError):m.transition(self.before,self.roles,q)
        roles=copy.deepcopy(self.roles);roles['roles'][0]['original_paper_analysis_reexecuted']=True
        with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)
    def test_changed_raw_capture_refused(self):
        changed=dict(self.after);papers=json.loads(changed[m.META[0]]);use=next(u for u in next(r for r in papers if r['paper_id']=='P064')['tools'] if u['tool_id']=='facsdiva');use['version_reported_near_name']='8.0.2';changed[m.META[0]]=p.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
    def test_instrument_coverage_or_unrelated_review_forgery_refused(self):
        changed=dict(self.after);inventory=json.loads(changed[m.META[1]]);next(r for r in inventory if r['id']=='facsdiva')['implementation_status']='native-subset-validated';changed[m.META[1]]=p.encoded(inventory)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
        changed=dict(self.after);ledger=json.loads(changed[m.META[5]]);ledger['reviews'][0]['use_summary']='changed unrelated review';changed[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
    def test_existing_review_identities_preserved(self):
        old=json.loads(self.before[m.META[5]])['reviews'];new=json.loads(self.after[m.META[5]])['reviews']
        for r in old:
            match=next(x for x in new if x['pmcid']==r['pmcid'] and x['tool_id']==r['tool_id'])
            for key in ('pmcid','doi','tool_id','evidence_paragraph_sha256'):self.assertEqual(r[key],match[key])
    def test_current_notice_and_prior_renderers(self):
        for name in m.META[6:]:text=self.after[name].decode();self.assertEqual(text.count('id="cytometry-catalog-notice"'),1);self.assertEqual(text.count('id="cytometry-prior-catalog"'),1);self.assertIn('SKIP3',text);self.assertIn('FAIL2',text)
        text=self.after[m.META[6]].decode();self.assertEqual(text.count('${cytometryVersion(t)}'),1);self.assertEqual(text.count('function imputeVersion(t)'),1)
if __name__=='__main__':unittest.main()
