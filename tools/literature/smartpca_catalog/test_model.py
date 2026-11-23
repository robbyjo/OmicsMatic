"""Scientific context/version distinctions, cumulative credit and preservation."""
import copy,json,subprocess,unittest
from smartpca_catalog import model as m

BASE='efe2e19dd1f4e5add6a6dd09b949e900368dea74'

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:subprocess.check_output(['git','show',BASE+':'+n],cwd=m.ROOT) for n in m.META}
        cls.primary=m.frozen_primary();cls.q=json.loads((m.ROOT/'docs/literature/smartpca-fst-qualification/STATUS.json').read_bytes())
    def after(self):return m.transition(self.before,self.primary,self.q)
    def use(self,paper):return next(u for p in json.loads(self.after()[m.META[0]]) if p['paper_id']==paper for u in p['tools'] if u['tool_id']==m.TARGET)

    def test_one_native_family_and_six_reviews_without_extra_observations(self):
        r=m.check(self.before,self.after(),self.primary,self.q)
        self.assertEqual((154,29,125,1697,1025,2722),tuple(r[k] for k in ('scoped_families','native','upstream','reviewed_uses','pending_uses','observations')))
        self.assertEqual((1691,2716,445),tuple(r[k] for k in ('unchanged_prior_reviews','unchanged_unrelated_observations','unchanged_unrelated_inventory_rows')))
        self.assertEqual((1,0,0,0),tuple(r[k] for k in ('new_native_family_credit','new_upstream_family_credit','added_positive_observations','removed_positive_observations')))

    def test_p019_mvp_edition_is_not_assigned_to_rosmap_projection(self):
        q=self.use('P019')['source_role_qualification'];self.assertEqual(['6',None],[r['reported_package_edition'] for r in q['roles']])
        self.assertEqual('6',self.use('P019')['version_reported_near_name']);self.assertIn('Do not assign',q['scope_limit'])

    def test_p180_pca_and_fst_versions_and_preprocessing_are_separate(self):
        q=self.use('P180')['source_role_qualification'];self.assertEqual(['7.2.0',None],[r['reported_package_edition'] for r in q['roles']])
        self.assertEqual({'fstonly':True},q['reported_settings']['FST']);self.assertEqual(100000,q['reported_settings']['PCA']['minimum_SNP_cutoff'])
        self.assertIsNone(self.use('P180')['version_reported_near_name']);self.assertIn('its application to FST is not established',q['scope_limit'])

    def test_p229_program_identifier_is_not_a_package_edition(self):
        q=self.use('P229')['source_role_qualification'];self.assertIsNone(q['roles'][0]['reported_package_edition']);self.assertEqual('13050',q['roles'][0]['reported_program_version'])
        self.assertIn('16000',q['scope_limit']);self.assertIsNone(self.use('P229')['version_reported_near_name'])

    def test_unreported_edition_and_ld_switch_are_not_inferred(self):
        for paper in ('P045','P071'):
            q=self.use(paper)['source_role_qualification'];self.assertTrue(all(r['reported_package_edition'] is None for r in q['roles']))
        q=self.use('P045')['source_role_qualification'];self.assertEqual(.7,q['reported_settings']['r2thresh']);self.assertNotIn('killr2',q['reported_settings'])
        self.assertIn('Do not infer',q['scope_limit']);self.assertIn('Eurasian and East Asian',self.use('P071')['implementation_scope_note'])
        self.assertEqual('7.2.1',self.use('P041')['source_role_qualification']['roles'][0]['reported_package_edition'])

    def test_draft_is_unchanged_but_admitted_qualifications_are_explicitly_applied(self):
        self.assertFalse(any(r['source_role_review_applied'] for r in self.primary['roles']))
        inventory=json.loads(self.after()[m.META[1]]);target=next(x for x in inventory if x['id']==m.TARGET)
        for paper in m.p.POSITIVE:
            q=self.use(paper)['source_role_qualification'];self.assertTrue(q['source_role_review_applied']);self.assertFalse(q['original_author_environment_reproduced']);self.assertFalse(q['whole_paper_reproduced'])
            self.assertEqual(q,target['paper_role_qualification'][paper])
        self.assertEqual(5,len(target['native_helper_sources']));self.assertTrue(target['global_CLI_dispatch_available']);self.assertFalse(target['released_binary_contains_workflow'])

    def test_ledger_retains_literal_contexts_and_no_invented_table_anchors(self):
        reviews=[r for r in json.loads(self.after()[m.META[5]])['reviews'] if r['tool_id']==m.TARGET]
        self.assertEqual(6,len(reviews))
        for row in reviews:
            use=next(self.use(paper) for paper in m.p.POSITIVE if self.use(paper)['source_role_qualification']['pmcid']==row['pmcid'])
            self.assertEqual(use['source_role_qualification']['roles'],row['reported_version_contexts']);self.assertEqual([],row['source_table_evidence'])

    def test_renderer_preserves_previous_edition_helpers_and_escapes_contexts(self):
        before=self.before[m.META[6]].decode();after=self.after()[m.META[6]].decode()
        block=before[before.index('function mrpressoVersion'):before.index('function renderContent(){')]
        self.assertIn(block,after);self.assertIn('${smartpcaVersion(t)}',after);self.assertIn('return mrpressoVersion(t)',after)
        for item in ('esc(r.context)','esc(r.reported_package_edition)','esc(r.reported_program_version)','package edition not reported','reported program identifier '):self.assertIn(item,after)

    def test_failure_remedies_and_native_limits_are_prominent_without_completion_claim(self):
        for filename in (m.META[6],m.META[7]):self.assertEqual(1,self.after()[filename].decode().count('id="smartpca-catalog-notice"'))
        for item in ('Warnings and required actions','SKIP3','FAIL2','per-field','Negative FST'):self.assertIn(item,m.NOTICE)
        self.assertIn('historical-version equivalence',m.SCOPE);self.assertIn('at most one block is refused',m.SCOPE)
        self.assertTrue(all(not m.check(self.before,self.after(),self.primary,self.q)[k] for k in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete')))

    def test_incomplete_qualification_or_substitute_producer_is_refused(self):
        for key in ('normal_tests','packaged_protocol_commands','source_program_calls','direct_source_jackknife_calls','source_finite_comparisons_across_profiles','methods_per_JVM','example_commands','source_audit_profiles'):
            q=copy.deepcopy(self.q);q[key]-=1
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
        q=copy.deepcopy(self.q);q['producer']['sha256']='0'*64
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_broader_completion_or_missing_dispatch_qualification_is_refused(self):
        for key in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete','catalog_admitted','native_fst_workflow_qualified','global_CLI_dispatch_available'):
            q=copy.deepcopy(self.q);q[key]=not q[key]
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_changed_anchor_or_original_literal_version_is_refused(self):
        for key,value in (('evidence_paragraph_sha256','0'*64),('version_reported_near_name','7.2.1')):
            before=dict(self.before);papers=json.loads(before[m.META[0]]);next(u for p in papers if p['paper_id']=='P019' for u in p['tools'] if u['tool_id']==m.TARGET)[key]=value;before[m.META[0]]=m.encoded(papers)
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(before,self.primary,self.q)
        primary=copy.deepcopy(self.primary);primary['roles'][-1]['roles'][0]['reported_package_edition']='13050'
        with self.assertRaises(ValueError):m.transition(self.before,primary,self.q)

    def test_duplicate_credit_is_refused(self):
        before=dict(self.before);ledger=json.loads(before[m.META[5]]);ledger['reviews'][0].update(tool_id=m.TARGET,pmcid=self.primary['roles'][0]['pmcid']);before[m.META[5]]=m.encoded(ledger)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)

    def test_unrelated_metadata_prior_review_and_applied_flag_changes_are_refused(self):
        for name,mutate in [(m.META[0],lambda x:x[0].update(doi='changed')),(m.META[5],lambda x:x['reviews'][0].update(use_summary='changed'))]:
            after=self.after();value=json.loads(after[name]);mutate(value);after[name]=m.encoded(value)
            with self.subTest(name=name),self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.q)
        after=self.after();papers=json.loads(after[m.META[0]]);next(u for p in papers if p['paper_id']=='P019' for u in p['tools'] if u['tool_id']==m.TARGET)['source_role_qualification']['source_role_review_applied']=False;after[m.META[0]]=m.encoded(papers)
        with self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.q)

    def test_only_one_registry_definition_changes(self):
        before=m.previous.registry();after=m.registry();self.assertEqual(485,len(after))
        self.assertEqual([x for x in before if x['id']!=m.TARGET],[x for x in after if x['id']!=m.TARGET])
        self.assertEqual('native-subset-validated',next(x for x in after if x['id']==m.TARGET)['current_coverage'])

if __name__=='__main__':unittest.main()
