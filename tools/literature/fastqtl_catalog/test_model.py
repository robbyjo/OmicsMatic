"""Check source meanings, literal captures and exactly one family/five reviews."""
import copy,json,subprocess,unittest
from fastqtl_catalog import model as m,primary as p
BASE='4547f09cb445b88dd7dd5bf608550bcc025ad0a6'
class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META};cls.roles=p.frozen();cls.q=m.qualification();cls.after=m.transition(cls.before,cls.roles,cls.q)
    def rows(self):return {r['paper_id']:r for r in self.roles['roles']}
    def test_counts_and_all_unrelated_records(self):
        r=m.check(self.before,self.after,self.roles,self.q);self.assertEqual((r['scoped_families'],r['reviewed_uses'],r['pending_uses'],r['new_reviews']),(159,1760,964,5));self.assertEqual((r['unchanged_unrelated_observations'],r['unchanged_prior_reviews']),(2719,1755));self.assertFalse(r['whole_goal_complete'])
    def test_complete_provider_snapshot_only_one_new_family(self):
        rows=m.registry();self.assertEqual(len(rows),len({r['id'] for r in rows}));self.assertEqual(len(rows),485);lookup={r['id']:r for r in rows};self.assertEqual(lookup['fastqtl']['current_coverage'],'upstream-adapter-validated');self.assertEqual(lookup['facsdiva']['current_coverage'],'missing');self.assertEqual(lookup['flowjo']['current_coverage'],'native-subset-validated')
    def test_peer_rule_is_closest_and_unresolved_unit_and_ties_remain(self):
        s=self.rows()['P019']['reported_settings'];self.assertEqual(s['yield_choice'],'closest-to-95-percent-of-maximum');self.assertEqual(s['candidate_PEER_range'],[5,50]);self.assertFalse(s['tie_policy_reported']);self.assertFalse(s['yield_unit_and_P_stage_fully_resolved'])
    def test_contextual_2165_preserves_raw_null_and_duplicate_declaration(self):
        r=self.rows()['P157'];self.assertIsNone(r['original_observation']['version_reported_near_name']);v=r['reported_version_contexts'];self.assertEqual([(x['paragraph_index'],x['tool_edition']) for x in v],[(71,'2.165'),(79,'2.165')]);self.assertEqual(v[1]['duplicate_of_paragraph_index'],71);self.assertFalse(v[1]['independent_execution_established']);self.assertEqual(r['related_prepared_operations'],[])
    def test_p170_distinct_thresholds_and_literal_nominal_Q(self):
        s=self.rows()['P170']['reported_settings'];self.assertEqual((s['mapping_Q_max'],s['profile_prioritization_Q_max'],s['permutations_per_locus']),(.05,.10,1000));self.assertEqual(s['colocalization_selection_literal'],'nominal Q value < 0.05')
    def test_p187_centers_TSS_covariates_and_BH_remain_separate(self):
        s=self.rows()['P187']['reported_settings'];self.assertEqual((s['caQTL_window_bp'],s['eQTL_window_bp'],s['caQTL_donors']),(1000,1000000,39));self.assertFalse(s['ATAC_PCs_used']);self.assertEqual(list(s['expression_PC_counts'].values()),[2,3,6,2,3,6]);self.assertTrue(s['beta_P_and_BH_are_separate']);self.assertFalse(s['bi_allelic_variant_types_fully_resolved'])
    def test_dataset_reuse_does_not_invent_local_or_conditional_execution(self):
        r=self.rows()['P212'];self.assertFalse(r['local_operation_established']);self.assertTrue(r['reported_settings']['public_results_reused']);self.assertEqual(r['related_prepared_operations'],[]);self.assertEqual(r['reported_settings']['assumed_signals_per_islet_gene'],1)
    def test_no_paper_or_unqualified_edition_fits_admitted(self):
        self.assertFalse(self.q['historical_2_165_qualified']);self.assertEqual(self.q['native_Windows_upstream_fits'],0)
        for r in self.roles['roles']:
            self.assertFalse(r['paper_edition_qualified']);self.assertFalse(r['required_original_inputs_available']);self.assertFalse(r['original_paper_analysis_reexecuted'])
    def test_fabricated_edition_or_execution_refused(self):
        q=copy.deepcopy(self.q);q['historical_2_165_qualified']=True
        with self.assertRaises(ValueError):m.transition(self.before,self.roles,q)
        roles=copy.deepcopy(self.roles);roles['roles'][-1]['local_operation_established']=True
        with self.assertRaises(ValueError):m.transition(self.before,roles,self.q)
    def test_changed_raw_capture_and_prior_review_refused(self):
        changed=dict(self.after);papers=json.loads(changed[m.META[0]]);next(u for u in next(r for r in papers if r['paper_id']=='P157')['tools'] if u['tool_id']=='fastqtl')['version_reported_near_name']='2.165';changed[m.META[0]]=p.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
        changed=dict(self.after);ledger=json.loads(changed[m.META[5]]);ledger['reviews'][0]['use_summary']='altered earlier review';changed[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
    def test_source_role_or_family_credit_forgery_refused(self):
        changed=dict(self.after);inventory=json.loads(changed[m.META[1]]);next(r for r in inventory if r['id']=='fastqtl')['whole_tool_qualified']=True;changed[m.META[1]]=p.encoded(inventory)
        with self.assertRaises(ValueError):m.check(self.before,changed,self.roles,self.q)
    def test_notice_history_and_older_renderers_preserved(self):
        for n in m.META[6:]:text=self.after[n].decode();self.assertEqual(text.count('id="fastqtl-catalog-notice"'),1);self.assertEqual(text.count('id="fastqtl-prior-catalog"'),1);self.assertIn('SKIP3',text);self.assertIn('FAIL2',text)
        text=self.after[m.META[6]].decode();self.assertEqual(text.count('${fastqtlVersion(t)}'),1);self.assertEqual(text.count('function cytometryVersion(t)'),1);self.assertEqual(text.count('function imputeVersion(t)'),1)
if __name__=='__main__':unittest.main()
