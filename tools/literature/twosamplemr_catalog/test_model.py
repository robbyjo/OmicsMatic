import copy,json,os,re,unittest
from pathlib import Path
from twosamplemr_catalog import primary as p,model as m,evidence as e

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fixture=os.environ.get('TWOSAMPLEMR_EXACT_BEFORE')
        if fixture:
            from twosamplemr_catalog.catalog import read_before
            cls.before=read_before(Path(fixture));expected=json.loads((e.ROOT/e.OLD_PROOF).read_bytes())['after']
            if {n:p.pin(raw) for n,raw in cls.before.items()}!=expected:raise ValueError('Different portable exact-before fixture')
        else:cls.before={n:e.git(e.ROOT,'show',e.BASE+':'+n) for n in m.META}
        cls.primary=p.frozen();cls.qualification=e.qualification()['result'];cls.after=m.transition(cls.before,cls.primary,cls.qualification);cls.papers={r['paper_id']:r for r in json.loads(cls.after[m.META[0]])}
    def use(self,pid):return next(r for r in self.papers[pid]['tools'] if r['tool_id']=='twosamplemr')
    def reject_after(self,name,edit):
        after=dict(self.after);value=json.loads(after[name]);edit(value);after[name]=p.encoded(value)
        with self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.qualification)
    def test_01_counts_one_upstream_family_no_native_credit(self):
        r=m.check(self.before,self.after,self.primary,self.qualification);self.assertEqual((144,26,118,1630,1092,2,0),(r['integrations'],r['native_integrations'],r['upstream_integrations'],r['reviewed_uses'],r['pending_uses'],r['validated_family_alias_labels'],r['native_family_credit_added']))
    def test_02_all1616_earlier_reviews_exact_and14_unique_appended(self):
        old=json.loads(self.before[m.META[5]]);new=json.loads(self.after[m.META[5]]);self.assertEqual(old['reviews'],new['reviews'][:1616]);self.assertEqual(1630,len(new['reviews']));self.assertEqual(1630,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
    def test_03_unrelated_observations_and445_inventory_rows_exact(self):
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):self.assertEqual([r for r in a['tools'] if r['tool_id']!='twosamplemr'],[r for r in b['tools'] if r['tool_id']!='twosamplemr'])
        self.assertEqual([r for r in json.loads(self.before[m.META[1]]) if r['id']!='twosamplemr'],[r for r in json.loads(self.after[m.META[1]]) if r['id']!='twosamplemr'])
    def test_04_only_two_missed_observations_are_added(self):
        for old in json.loads(self.before[m.META[0]]):self.assertEqual([r['tool_id'] for r in old['tools']]+(['twosamplemr'] if old['paper_id'] in ['P157','P177'] else []),[r['tool_id'] for r in self.papers[old['paper_id']]['tools']])
    def test_05_only_seven_reported_editions_are_assigned(self):
        versions={'P077':'0.4.9','P088':'0.5.6','P150':'0.5.6','P157':'0.5.7','P182':'0.5.7','P211':'0.5.7','P224':'0.6.8'}
        for role in self.primary['roles']:self.assertEqual(versions.get(role['paper_id']),self.use(role['paper_id'])['version_reported_near_name'])
    def test_06_p157_archived_bidirectional_source_extension_is_explicit(self):
        use=self.use('P157');self.assertIn('harmonise_data',use['use_summary']);self.assertIn('both',use['use_summary']);self.assertTrue(use['static_author_code_attribution']['paper_linked_deposit']['current_GitHub_MR_file_identical']);self.assertFalse(use['source_role_qualification']['author_runtime_executed'])
    def test_07_p177_ivw_provider_and_median_mode_inference_are_distinct(self):
        use=self.use('P177');self.assertIn('MendelianRandomization::mr_input/mr_ivw',use['use_summary']);self.assertIn('inferred',use['use_summary']);self.assertIsNone(use['version_reported_near_name']);self.assertTrue(use['static_author_code_attribution']['TEMR_is_separate_provider']);self.assertIn('Egger',use['implementation_scope_note'])
    def test_08_p224_selection_preparation_not_mvmr_estimation(self):
        use=self.use('P224');self.assertIn('mv_harmonise_data',use['use_summary']);self.assertIn('MVMR v0.4.1',use['implementation_scope_note']);self.assertIn('conditional F',use['implementation_scope_note'])
    def test_09_p150_maf_and_api_scope_not_repaired(self):
        use=self.use('P150');self.assertIn('0.5-percent MAF',use['implementation_scope_note']);self.assertIn('API',use['implementation_scope_note']);self.assertNotIn('inverse variance weighted',use['use_summary'].lower())
    def test_10_p211_citation_version_and_steiger_direction_retained(self):
        use=self.use('P211');self.assertEqual('0.5.7',use['version_reported_near_name']);self.assertIn('-1.96',use['implementation_scope_note']);self.assertIn('R4.2.1',use['implementation_scope_note']);self.assertIn('R4.3.1',use['implementation_scope_note'])
    def test_11_p112_count_discrepancy_and_p105_estimator_gap_retained(self):
        self.assertIn('three remain',self.use('P112')['implementation_scope_note']);self.assertIn('precise estimator',self.use('P105')['implementation_scope_note']);self.assertIsNone(self.use('P105')['version_reported_near_name'])
    def test_12_registry_only_one_definition_changes(self):
        from peer_catalog.model import registry as preceding
        old,new=preceding(),m.registry();self.assertEqual(485,len(new));self.assertEqual([r for r in old if r['id']!='twosamplemr'],[r for r in new if r['id']!='twosamplemr'])
        pattern=re.compile(m.PATTERN,re.I)
        for value in ['TwoSampleMR','twosamplemr','TWOSAMPLEMR v0.5.7']:self.assertIsNotNone(pattern.search(value))
        for value in ['Two-sample MR','XTwoSampleMR','TwoSampleMR_more']:self.assertIsNone(pattern.search(value))
    def test_13_all_literal_package_witnesses_match(self):
        for role in self.primary['roles']:self.assertIsNotNone(re.fullmatch(m.PATTERN,role['source_name_witness']['literal'],re.I))
    def test_14_rewritten_earlier_review_is_refused(self):self.reject_after(m.META[5],lambda rows:rows['reviews'][0].update(use_summary='rewritten'))
    def test_15_unrelated_observation_rewrite_is_refused(self):self.reject_after(m.META[0],lambda rows:rows[0]['tools'][0].update(use_summary='rewritten'))
    def test_16_native_second_family_credit_is_refused(self):self.reject_after(m.META[1],lambda rows:next(r for r in rows if r['id']=='twosamplemr').update(native_family_credit_added=1))
    def test_17_incomplete_global_qualification_is_refused(self):
        for key,value in [('packaged_tests_per_JVM',0),('global_profiles',6),('source_hosts',1),('global_command_registered',False)]:
            qualification=copy.deepcopy(self.qualification);qualification[key]=value
            with self.assertRaises(ValueError):m.transition(self.before,self.primary,qualification)
    def test_18_double_admission_is_refused(self):
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualification)
    def test_19_extra_or_dropped_observation_is_refused(self):
        self.reject_after(m.META[0],lambda rows:rows[0]['tools'].append(dict(tool_id='twosamplemr')))
        self.reject_after(m.META[0],lambda rows:next(r for r in rows if r['paper_id']=='P157')['tools'].pop())
    def test_20_changed_before_primary_identity_is_refused(self):
        before=dict(self.before);rows=json.loads(before[m.META[0]]);rows[0]['doi']='wrong';before[m.META[0]]=p.encoded(rows)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.qualification)
    def test_21_changed_before_inventory_membership_is_refused(self):
        before=dict(self.before);rows=json.loads(before[m.META[1]]);next(r for r in rows if r['id']=='twosamplemr')['paper_count']=13;before[m.META[1]]=p.encoded(rows)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.qualification)
    def test_22_changed_role_or_author_execution_claim_is_refused(self):
        for key,value in [('kind','whole-paper-reproduction'),('author_runtime_executed',True)]:
            primary=copy.deepcopy(self.primary);primary['roles'][0][key]=value
            with self.assertRaises(ValueError):m.transition(self.before,primary,self.qualification)
    def test_23_changed_provider_archive_or_invented_edition_is_refused(self):
        for pid,key,value in [('P177','reported_TwoSampleMR_version','0.6.8'),('P177','TEMR_is_separate_provider',False),('P157','reported_TwoSampleMR_version','0.7.14')]:
            primary=copy.deepcopy(self.primary);primary['author_attribution'][pid][key]=value
            with self.assertRaises(ValueError):m.transition(self.before,primary,self.qualification)
    def test_24_reports_and_current_notices_agree(self):
        for name in m.META[6:]:
            text=self.after[name].decode();self.assertEqual(1,text.count('id="twosamplemr-catalog-notice"'));self.assertLess(text.index('id="twosamplemr-catalog-notice"'),text.index('id="peer-catalog-notice"'));self.assertIn('Warnings and required actions:',text);self.assertIn('1,092 pending',text)
        data=json.loads(re.search(r'<script id="review-data" type="application/json">(.*?)</script>',self.after[m.META[6]].decode(),re.S)[1]);self.assertEqual(json.loads(self.after[m.META[0]]),data['papers']);self.assertEqual(2722,data['summary']['paper_tool_observations'])

if __name__=='__main__':unittest.main()
