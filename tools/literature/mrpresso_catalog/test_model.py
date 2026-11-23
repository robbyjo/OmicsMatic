"""Native-family, source-role, unknown-setting and preservation regressions."""
import copy,json,subprocess,unittest
from mrpresso_catalog import model as m

BASE='6dda7a239967c68266c786de5678a156e3196658'

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={name:subprocess.check_output(['git','show',BASE+':'+name],cwd=m.ROOT) for name in m.META}
        cls.primary=m.frozen_primary();cls.q=json.loads((m.ROOT/'docs/literature/mrpresso-workflow-qualification/STATUS.json').read_bytes())
    def after(self):return m.transition(self.before,self.primary,self.q)
    def use(self,paper):return next(x for p in json.loads(self.after()[m.META[0]]) if p['paper_id']==paper for x in p['tools'] if x['tool_id']==m.TARGET)

    def test_one_native_family_six_reviews_and_no_observation_or_upstream_credit(self):
        result=m.check(self.before,self.after(),self.primary,self.q)
        self.assertEqual((153,28,125,1691,1031,2722),tuple(result[k] for k in ('scoped_families','native','upstream','reviewed_uses','pending_uses','observations')))
        self.assertEqual((1685,2716,445),tuple(result[k] for k in ('unchanged_prior_reviews','unchanged_unrelated_observations','unchanged_unrelated_inventory_rows')))
        self.assertEqual((1,0,0,0),tuple(result[k] for k in ('new_native_family_credit','new_upstream_family_credit','added_positive_observations','removed_positive_observations')))

    def test_unknown_paper_settings_are_not_replaced_by_validated_source_version(self):
        for paper in m.p.POSITIVE:
            use=self.use(paper);role=use['source_role_qualification']
            self.assertIsNone(use['version_reported_near_name'])
            for key in ('reported_near_name_version','reported_seed','reported_simulations'):self.assertIsNone(role[key])
            self.assertFalse(role['author_environment_reproduced']);self.assertFalse(role['whole_paper_reproduced'])

    def test_multivariable_source_role_is_not_the_older_multiple_outcome_method(self):
        use=self.use('P135');self.assertIn('height/BMI',use['use_summary']);self.assertIn('one fracture outcome',use['implementation_scope_note'])
        self.assertIn('MR-LASSO',use['implementation_scope_note'])
        target=next(x for x in json.loads(self.after()[m.META[1]]) if x['id']==m.TARGET)
        self.assertTrue(target['canonical_native_workflow_available']);self.assertFalse(target['upstream_runtime_adapter_required'])
        self.assertTrue(target['previous_method_overlap_source'].endswith('/MultivariateMrCli.java'))
        self.assertTrue(target['native_helper_source'].endswith('/mrpresso/MrPressoOriginal.java'))

    def test_conditional_study_selection_and_source_table_remain_explicit(self):
        use=self.use('P150');self.assertIn('global test is nonsignificant',use['implementation_scope_note'])
        self.assertIn('too few instruments remain',use['implementation_scope_note']);self.assertIn('325-outcome Bonferroni',use['implementation_scope_note'])
        tables=use['source_role_qualification']['table_anchors'];self.assertEqual(['Tab3'],[x['table_id'] for x in tables])
        ledger=json.loads(self.after()[m.META[5]]);review=next(x for x in ledger['reviews'] if x['tool_id']==m.TARGET and x['pmcid']==use['source_role_qualification']['pmcid'])
        self.assertEqual(tables,review['source_table_evidence'])

    def test_comparator_negative_findings_and_discussion_recommendation_are_distinct(self):
        comparator=self.use('P224');self.assertEqual('comparator-method',comparator['source_role_qualification']['source_role']);self.assertIn('primary MR-RAPS',comparator['use_summary'])
        negative=self.use('P189');self.assertIn('nonsignificant',negative['use_summary']);self.assertIn('not proof',negative['implementation_scope_note'])
        p177=next(x for x in json.loads(self.after()[m.META[0]]) if x['paper_id']=='P177')
        self.assertFalse(any(x['tool_id']==m.TARGET for x in p177['tools']))
        self.assertEqual('RECOMMENDED_AVAILABLE_METHOD_EXECUTION_NOT_ESTABLISHED',self.primary['discussion_only'][0]['classification'])

    def test_renderer_keeps_squidpy_and_gprofiler_edition_behavior(self):
        before=self.before[m.META[6]].decode();after=self.after()[m.META[6]].decode()
        block=before[before.index('function squidpyVersion'):before.index('function renderContent(){')]
        self.assertIn(block,after);self.assertIn('${mrpressoVersion(t)}',after);self.assertIn('return squidpyVersion(t)',after)
        self.assertIn('edition, seed and simulation count not reported',after)

    def test_native_probability_guard_and_failure_remedies_are_prominent(self):
        page=self.after()[m.META[6]].decode();self.assertEqual(1,page.count('id="mrpresso-catalog-notice"'))
        self.assertIn('Warnings and required actions',m.NOTICE);self.assertIn('SKIP3',m.NOTICE);self.assertIn('FAIL2',m.NOTICE)
        self.assertIn('probabilities above1',m.ACTION);self.assertIn('one-outcome',m.SCOPE);self.assertIn('R is not',m.SCOPE)
        for key in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'):self.assertFalse(m.check(self.before,self.after(),self.primary,self.q)[key])

    def test_missing_or_substituted_qualification_is_refused(self):
        for key in ('original_public_api_calls','packaged_commands','independent_original_comparisons','saved_control_host_checks','protocol_commands'):
            q=copy.deepcopy(self.q);q[key]-=1
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
        q=copy.deepcopy(self.q);q['producer']['sha256']='0'*64
        with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_broader_completion_or_already_admitted_qualification_claim_is_refused(self):
        for key in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete','catalog_admitted'):
            q=copy.deepcopy(self.q);q[key]=True
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,self.primary,q)

    def test_changed_anchor_or_invented_edition_seed_is_refused(self):
        for key,value in (('evidence_paragraph_sha256','0'*64),('version_reported_near_name','1.0')):
            before=dict(self.before);papers=json.loads(before[m.META[0]])
            next(x for p in papers if p['paper_id']=='P105' for x in p['tools'] if x['tool_id']==m.TARGET)[key]=value;before[m.META[0]]=m.encoded(papers)
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(before,self.primary,self.q)
        primary=copy.deepcopy(self.primary);primary['roles'][0]['reported_seed']=19
        with self.assertRaises(ValueError):m.transition(self.before,primary,self.q)

    def test_target_cannot_be_credited_twice(self):
        before=dict(self.before);ledger=json.loads(before[m.META[5]]);ledger['reviews'][0].update(tool_id=m.TARGET,pmcid=self.primary['roles'][0]['pmcid']);before[m.META[5]]=m.encoded(ledger)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.q)

    def test_unrelated_metadata_and_old_review_changes_are_refused(self):
        for filename,mutate in [(m.META[0],lambda x:x[0].update(doi='changed')),(m.META[5],lambda x:x['reviews'][0].update(use_summary='changed'))]:
            after=self.after();value=json.loads(after[filename]);mutate(value);after[filename]=m.encoded(value)
            with self.subTest(filename=filename),self.assertRaises(ValueError):m.check(self.before,after,self.primary,self.q)

    def test_only_one_registry_definition_changes(self):
        before=m.previous.registry();after=m.registry();self.assertEqual(485,len(after))
        self.assertEqual([x for x in before if x['id']!=m.TARGET],[x for x in after if x['id']!=m.TARGET])
        self.assertEqual('native-subset-validated',next(x for x in after if x['id']==m.TARGET)['current_coverage'])

if __name__=='__main__':unittest.main()
