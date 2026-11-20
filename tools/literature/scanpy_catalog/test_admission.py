"""Conservation, attribution and numerical-scope controls for Scanpy catalog admission."""
import copy
import json
import re
import unittest
from scanpy_catalog import model
from scanpy_catalog.coverage import build
from scanpy_catalog.evidence import ROOT, baseline, qualification


class ScanpyAdmissionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=baseline()
        cls.census=json.loads((ROOT/'docs/literature/scanpy-preprocessing-checks/CENSUS.json').read_bytes())
        cls.q=qualification();cls.after=model.transition(cls.before,cls.census,cls.q)
        cls.roles={r['paper_id']:r for r in build(cls.census)['roles']}

    def use(self,pid):
        return next(t for p in json.loads(self.after[model.META[0]]) if p['paper_id']==pid
                    for t in p['tools'] if t['tool_id']=='scanpy')

    def test_actual_counts_and_one_upstream_family_credit(self):
        r=model.check(self.before,self.after,self.census,self.q)
        self.assertEqual((r['observations'],r['reviewed_uses'],r['unreviewed_uses']),(2703,1483,1220))
        self.assertEqual((r['new_scoped_integrations'],r['native_integrations'],r['upstream_integrations']),(137,25,112))
        self.assertEqual((r['missing'],r['method_overlap'],r['including_existing_adapters']),(259,38,140))
        self.assertEqual((r['native_family_credit_added'],r['upstream_family_credit_added']),(0,1))

    def test_all_1462_previous_reviews_exact(self):
        a=json.loads(self.before[model.META[5]]);b=json.loads(self.after[model.META[5]])
        self.assertEqual(a['reviews'],b['reviews'][:1462]);self.assertEqual(len(b['reviews']),1483)
        self.assertTrue(all(r['tool_id']=='scanpy' for r in b['reviews'][1462:]))

    def test_p061_old_observation_and_review_preserved(self):
        old=next(t for p in json.loads(self.before[model.META[0]]) if p['paper_id']=='P061'
                 for t in p['tools'] if t['tool_id']=='scanpy')
        self.assertEqual(old,{k:v for k,v in self.use('P061').items() if k!='source_role_qualification'})
        self.assertFalse(self.roles['P061']['catalog_review_appended'])

    def test_no_new_removed_or_reordered_observations(self):
        a=json.loads(self.before[model.META[0]]);b=json.loads(self.after[model.META[0]])
        for old,new in zip(a,b,strict=True):
            self.assertEqual([t['tool_id'] for t in old['tools']],[t['tool_id'] for t in new['tools']])

    def test_all_frozen_paper_identities_retained(self):
        a=json.loads(self.before[model.META[0]]);b=json.loads(self.after[model.META[0]])
        self.assertEqual([{k:v for k,v in p.items() if k!='tools'} for p in a],
                         [{k:v for k,v in p.items() if k!='tools'} for p in b])

    def test_exact_source_summaries_cautions_and_operations(self):
        for role in self.census['roles']:
            actual=self.use(role['paper_id'])['source_role_qualification']
            for key in ['role','declared_host','reported_editions','reported_operations','use_summary','required_caution']:
                self.assertEqual(actual[key],role[key])

    def test_function_unspecified_sources_get_no_invented_functions(self):
        for pid in ['P024','P083','P104']:
            self.assertEqual(self.roles[pid]['current_1130_tested_function_counterparts'],[])
            self.assertEqual(self.roles[pid]['qualified_native_component_overlap'],[])

    def test_supplied_object_reuse_is_not_a_de_novo_fit(self):
        r=self.roles['P182'];self.assertTrue(r['reusable_supplied_object_intake'])
        self.assertEqual(r['current_1130_tested_function_counterparts'],[])
        self.assertFalse(r['full_reported_role_qualified'])
        self.assertEqual(self.use('P182')['usage_context'],'provided-integrated-object-reuse')

    def test_dispersion_flavor_not_assigned_to_v3_or_pearson_hvgs(self):
        matches=[pid for pid,r in self.roles.items() if any(s['component']=='dispersion-hvg' for s in r['qualified_native_component_overlap'])]
        self.assertEqual(matches,['P021'])
        self.assertEqual(next(s for s in self.roles['P021']['qualified_native_component_overlap'] if s['component']=='dispersion-hvg')['operations'],['cell_ranger'])
        for pid in ['P062','P093']:
            self.assertIn('do not implement',' '.join(self.roles[pid]['remaining_author_roles']))

    def test_pearson_matrix_is_not_hvg_or_sct_fit(self):
        r=next(s for s in self.roles['P062']['qualified_native_component_overlap'] if s['component']=='pearson-matrix')
        self.assertIn('only;',r['relationship']);self.assertIn('HVG',r['relationship'])

    def test_native_components_are_explicit_not_author_parity(self):
        for r in self.roles.values():
            self.assertTrue(r['native_components_are_explicit_not_automatic_fallback'])
            for overlap in r['qualified_native_component_overlap']:
                self.assertFalse(overlap['historical_author_profile_qualified'])

    def test_no_complete_historical_author_or_whole_paper_claims(self):
        for r in self.roles.values():
            for key in ['author_runtime_executed','exact_author_settings_established','full_reported_role_qualified',
                        'complete_author_workflow_qualified','historical_package_parity','whole_paper_reproduced']:
                self.assertIs(r[key],False)

    def test_multiple_or_unreported_versions_not_flattened_to_one(self):
        for pid,r in self.roles.items():
            if pid=='P061':continue
            editions=list(dict.fromkeys(e['literal'] for e in r['reported_editions']))
            self.assertEqual(self.use(pid)['version_reported_near_name'],editions[0] if len(editions)==1 else None)

    def test_review_records_point_to_exact_source_anchors(self):
        reviews=json.loads(self.after[model.META[5]])['reviews'][1462:]
        for r in reviews:
            role=next(s for s in self.roles.values() if s['pmcid']==r['pmcid'])
            self.assertEqual(r['evidence_paragraph_sha256'],role['anchors'][0]['paragraph_sha256'])
            self.assertEqual(r['use_summary'],role['use_summary'])

    def test_all_non_scanpy_inventory_objects_unchanged(self):
        a=json.loads(self.before[model.META[1]]);b=json.loads(self.after[model.META[1]])
        self.assertEqual([r for r in a if r['id']!='scanpy'],[r for r in b if r['id']!='scanpy'])

    def test_fifteen_qualified_operations_not_twenty_nine(self):
        coverage=build(self.census)
        self.assertEqual((coverage['qualified_upstream_operations'],coverage['development_allowlisted_operations']),(15,29))
        self.assertIn('not 29 qualified',model.NOTE)

    def test_wrong_qualification_counts_or_author_claim_refused(self):
        for key,value in [('normal_global_tests_per_JVM',61),('qualified_upstream_operations',29),
            ('native_hvg_tests_per_JVM_per_producer',2573),('new_family_credit',1),
            ('historical_author_package_parity',True),('author_runtime_executed',True),('whole_tool_qualified',True)]:
            with self.subTest(key=key):
                q=copy.deepcopy(self.q);q[key]=value
                with self.assertRaises(ValueError):model.transition(self.before,self.census,q)

    def test_changed_literal_source_summary_refused(self):
        c=copy.deepcopy(self.census);c['roles'][0]['use_summary']='invented source attribution'
        with self.assertRaises(ValueError):build(c)

    def test_false_author_runtime_in_source_refused(self):
        c=copy.deepcopy(self.census);c['roles'][0]['author_runtime_executed']=True
        with self.assertRaises(ValueError):build(c)

    def test_old_review_mutation_refused(self):
        before=dict(self.before);r=json.loads(before[model.META[5]])
        next(x for x in r['reviews'] if x['tool_id']=='scanpy')['use_summary']='changed'
        before[model.META[5]]=model.encoded(r)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_wrong_paper_identity_refused(self):
        before=dict(self.before);p=json.loads(before[model.META[0]])
        next(x for x in p if x['paper_id']=='P021')['doi']='wrong'
        before[model.META[0]]=model.encoded(p)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_duplicate_observation_refused(self):
        before=dict(self.before);p=json.loads(before[model.META[0]]);row=next(x for x in p if x['paper_id']=='P021')
        row['tools'].append(copy.deepcopy(next(t for t in row['tools'] if t['tool_id']=='scanpy')))
        before[model.META[0]]=model.encoded(p)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_unrelated_output_change_refused(self):
        after=dict(self.after);rows=json.loads(after[model.META[1]]);rows[0]['name']='changed'
        after[model.META[1]]=model.encoded(rows)
        with self.assertRaises(ValueError):model.check(self.before,after,self.census,self.q)

    def test_embedded_report_matches_actual_metadata(self):
        match=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',self.after[model.META[6]].decode(),re.S)
        self.assertEqual(json.loads(match[1]),dict(papers=json.loads(self.after[model.META[0]]),
            tools=json.loads(self.after[model.META[1]]),summary=json.loads(self.after[model.META[2]])))

    def test_current_warnings_precede_retained_older_notice(self):
        for name in model.META[6:]:
            page=self.after[name].decode()
            self.assertLess(page.index('id="scanpy-catalog-notice"'),page.index('id="enrichr-catalog-notice"'))
            self.assertIn('Warnings and required actions:',page)


if __name__=='__main__':unittest.main()
