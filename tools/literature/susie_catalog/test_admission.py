"""Conservation and source-attribution controls for actual SuSiE catalog inputs."""
import copy
import json
import re
import unittest
from susie_catalog import model
from susie_catalog.coverage import build,REUSE
from susie_catalog.evidence import ROOT,baseline,qualification


class SusieAdmissionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=baseline();cls.census=json.loads((ROOT/'docs/literature/susie-workflow-checks/CENSUS.json').read_bytes())
        cls.q=qualification();cls.after=model.transition(cls.before,cls.census,cls.q)
        cls.roles={r['paper_id']:r for r in build(cls.census)['roles']}

    def use(self,pid):
        return next(t for p in json.loads(self.after[model.META[0]]) if p['paper_id']==pid for t in p['tools'] if t['tool_id']=='susie')

    def test_real_counts_and_single_upstream_credit(self):
        r=model.check(self.before,self.after,self.census,self.q)
        self.assertEqual((r['observations'],r['reviewed_uses'],r['unreviewed_uses']),(2704,1503,1201))
        self.assertEqual((r['new_scoped_integrations'],r['native_integrations'],r['upstream_integrations']),(138,25,113))
        self.assertEqual((r['observations_added'],r['observations_removed'],r['new_review_records']),(2,1,20))
        self.assertEqual((r['missing'],r['method_overlap'],r['including_existing_adapters']),(259,37,141))

    def test_all_1483_previous_reviews_exact_and_in_order(self):
        before=json.loads(self.before[model.META[5]]);after=json.loads(self.after[model.META[5]])
        self.assertEqual(before['reviews'],after['reviews'][:1483])
        self.assertEqual(len(after['reviews']),1503)
        self.assertTrue(all(r['tool_id']=='susie' for r in after['reviews'][1483:]))

    def test_only_exact_two_additions_and_proper_name_removal(self):
        before=json.loads(self.before[model.META[0]]);after=json.loads(self.after[model.META[0]])
        added=[];removed=[]
        for old,new in zip(before,after,strict=True):
            a=[t['tool_id'] for t in old['tools']];b=[t['tool_id'] for t in new['tools']]
            added.extend((old['paper_id'],n) for n in b if n not in a)
            removed.extend((old['paper_id'],n) for n in a if n not in b)
            self.assertEqual([t for t in old['tools'] if t['tool_id']!='susie'],[t for t in new['tools'] if t['tool_id']!='susie'])
        self.assertEqual(added,[('P073','susie'),('P203','susie')]);self.assertEqual(removed,[('P235','susie')])

    def test_proper_name_original_object_is_retained_in_census(self):
        row=self.census['proper_name_exclusions'][0]
        before=next(t for p in json.loads(self.before[model.META[0]]) if p['paper_id']=='P235' for t in p['tools'] if t['tool_id']=='susie')
        self.assertEqual(before,row['existing_observation_object']);self.assertIn('orangutan',row['reason'])

    def test_p212_introduction_is_not_a_new_observation(self):
        self.assertEqual(self.census['nonexecution_contexts'][0]['paper_id'],'P212')
        self.assertNotIn('P212',self.roles)
        self.assertFalse(any(t['tool_id']=='susie' for p in json.loads(self.after[model.META[0]]) if p['paper_id']=='P212' for t in p['tools']))

    def test_missed_results_descriptions_are_not_relabelled_as_methods(self):
        for pid in ['P073','P203']:self.assertEqual(self.use(pid)['usage_context'],'analysis-result-description')

    def test_all_frozen_paper_metadata_is_unchanged(self):
        a=json.loads(self.before[model.META[0]]);b=json.loads(self.after[model.META[0]])
        self.assertEqual([{k:v for k,v in p.items() if k!='tools'} for p in a],[{k:v for k,v in p.items() if k!='tools'} for p in b])

    def test_reuse_roles_never_get_fit_or_resource_qualification(self):
        for pid in REUSE:
            r=self.roles[pid]
            self.assertFalse(r['paper_reports_fitting_or_hosted_analysis'])
            self.assertEqual(r['supplied_input_fit_counterparts'],[])
            self.assertFalse(r['supplied_results_resource_or_selection_qualified'])
            self.assertEqual(self.use(pid)['usage_context'],'external-fine-mapped-result-reuse')

    def test_unknown_interface_does_not_acquire_a_function(self):
        for pid in ['P073','P203','P246']:self.assertEqual(self.roles[pid]['supplied_input_fit_counterparts'],[])

    def test_predictive_and_phenotype_roles_have_individual_counterparts(self):
        for pid in ['P108','P181','P234']:
            self.assertEqual(self.roles[pid]['supplied_input_fit_counterparts'][0]['model'],'INDIVIDUAL')
            self.assertTrue(self.roles[pid]['counterparts_are_not_author_function_attribution'])

    def test_unestablished_01143_cannot_get_adjacent_profile(self):
        r=self.roles['P157'];self.assertEqual(r['unestablished_reported_editions'],['0.11.43'])
        self.assertEqual(r['reported_package_edition_source_profiles'],[])
        self.assertIn('SKIP3',' '.join(r['remaining_author_roles']))
        self.assertEqual(self.use('P157')['version_reported_near_name'],'0.11.43')

    def test_literal_edition_match_does_not_recover_author_dependencies(self):
        r=self.roles['P092'];self.assertEqual(r['reported_package_edition_source_profiles'],['SuSiE-0.12.35-R4.6.1'])
        self.assertFalse(r['author_R_and_dependency_profile_recovered']);self.assertFalse(r['historical_package_parity'])

    def test_no_author_runtime_full_workflow_or_whole_paper_claims(self):
        for r in self.roles.values():
            for k in ['standalone_susieR_function_named','author_runtime_executed','exact_author_settings_established',
                      'historical_package_parity','full_reported_role_qualified','complete_author_workflow_qualified','whole_paper_reproduced']:
                self.assertIs(r[k],False)

    def test_source_summaries_cautions_editions_and_anchors_exact(self):
        for r in self.census['roles']:
            actual=self.use(r['paper_id'])
            for k in ['role','declared_host','reported_editions','use_summary','required_caution']:
                self.assertEqual(actual['source_role_qualification'][k],r[k])
            self.assertEqual(actual['evidence_paragraph_sha256'],r['anchors'][0]['paragraph_sha256'])

    def test_global_dispatch_and_released_binary_not_invented(self):
        row=next(t for t in json.loads(self.after[model.META[1]]) if t['id']=='susie')
        self.assertFalse(row['global_CLI_dispatch_available']);self.assertFalse(row['released_binary_contains_workflow_adapter'])
        self.assertTrue(row['source_build_required']);self.assertIn('SusieWorkflowCli',row['qualified_component_entrypoints'][0])

    def test_all_other_inventory_rows_exact(self):
        a=json.loads(self.before[model.META[1]]);b=json.loads(self.after[model.META[1]])
        self.assertEqual([r for r in a if r['id']!='susie'],[r for r in b if r['id']!='susie'])

    def test_changed_source_summary_and_author_claim_refused(self):
        for key,value in [('use_summary','invented'),('author_runtime_executed',True),('standalone_susieR_function_named',True)]:
            census=copy.deepcopy(self.census);census['roles'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):build(census)

    def test_wrong_qualification_scope_or_counts_refused(self):
        for key,value in [('normal_tests_per_JVM',72),('original_model_cases',40),('converged_accepted',41),
            ('maximum_model_numeric_absolute_error',1e-8),('actual_replay_fields_exact_except_call',False),
            ('global_dispatch_added',True),('linux_R_scientific_runtime_qualified',True),('author_runtime_executed',True),
            ('new_family_credit',1),('whole_tool_qualified',True)]:
            q=copy.deepcopy(self.q);q[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):model.transition(self.before,self.census,q)

    def test_reviewed_proper_name_cannot_be_silently_removed(self):
        before=dict(self.before);r=json.loads(before[model.META[5]])
        r['reviews'][0]['tool_id']='susie';before[model.META[5]]=model.encoded(r)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_changed_proper_name_before_object_refused(self):
        before=dict(self.before);papers=json.loads(before[model.META[0]])
        next(t for p in papers if p['paper_id']=='P235' for t in p['tools'] if t['tool_id']=='susie')['matched_text']='changed'
        before[model.META[0]]=model.encoded(papers)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_duplicate_observation_refused(self):
        before=dict(self.before);papers=json.loads(before[model.META[0]])
        p=next(p for p in papers if p['paper_id']=='P090');p['tools'].append(copy.deepcopy(next(t for t in p['tools'] if t['tool_id']=='susie')))
        before[model.META[0]]=model.encoded(papers)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_wrong_source_identity_refused(self):
        before=dict(self.before);papers=json.loads(before[model.META[0]])
        next(p for p in papers if p['paper_id']=='P090')['doi']='changed';before[model.META[0]]=model.encoded(papers)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_unrelated_output_and_review_change_refused(self):
        for name in [model.META[1],model.META[5]]:
            after=dict(self.after);value=json.loads(after[name])
            if name==model.META[1]:value[0]['name']='changed'
            else:value['reviews'][0]['use_summary']='changed'
            after[name]=model.encoded(value)
            with self.subTest(name=name),self.assertRaises(ValueError):model.check(self.before,after,self.census,self.q)

    def test_embedded_report_equals_actual_new_metadata(self):
        m=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',self.after[model.META[6]].decode(),re.S)
        self.assertEqual(json.loads(m[1]),dict(papers=json.loads(self.after[model.META[0]]),tools=json.loads(self.after[model.META[1]]),summary=json.loads(self.after[model.META[2]])))

    def test_prominent_recovery_precedes_retained_previous_notice(self):
        for name in model.META[6:]:
            text=self.after[name].decode();self.assertLess(text.index('id="susie-catalog-notice"'),text.index('id="scanpy-catalog-notice"'))
            self.assertIn('Warnings and required actions:',text);self.assertIn('0.11.43',text)


if __name__=='__main__':unittest.main()
