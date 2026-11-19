"""Scientific scope, source identity and cumulative preservation controls."""
import copy
import json
from pathlib import Path
import unittest
from enrichr_catalog import model
from enrichr_catalog.coverage import build
from enrichr_catalog.evidence import ROOT, baseline, qualification


class EnrichrAdmissionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = baseline()
        cls.census = json.loads((ROOT/'docs/literature/enrichr-component-checks/CENSUS.json').read_bytes())
        cls.q = qualification(); cls.after = model.transition(cls.before, cls.census, cls.q)
        cls.roles = {r['paper_id']: r for r in build(cls.census)['roles']}

    def uses(self, paper):
        return next(p for p in json.loads(self.after[model.META[0]]) if p['paper_id']==paper)['tools']

    def test_counts_and_scope_credit(self):
        r = model.check(self.before, self.after, self.census, self.q)
        self.assertEqual((r['observations'], r['reviewed_uses'], r['unreviewed_uses']), (2703,1462,1241))
        self.assertEqual((r['new_scoped_integrations'], r['native_integrations'], r['upstream_integrations']), (136,25,111))

    def test_all_1439_earlier_reviews_exact(self):
        a=json.loads(self.before[model.META[5]]); b=json.loads(self.after[model.META[5]])
        self.assertEqual(a['reviews'], b['reviews'][:1439]); self.assertEqual(len(b['reviews']),1462)

    def test_old_enrichr_use_objects_preserved(self):
        for pid in ['P061','P163']:
            old=next(t for p in json.loads(self.before[model.META[0]]) if p['paper_id']==pid for t in p['tools'] if t['tool_id']=='enrichr')
            new=next(t for t in self.uses(pid) if t['tool_id']=='enrichr')
            self.assertEqual(old,{k:v for k,v in new.items() if k!='source_role_qualification'})

    def test_only_three_new_observations(self):
        before=json.loads(self.before[model.META[0]]); after=json.loads(self.after[model.META[0]])
        self.assertEqual([a['paper_id'] for b,a in zip(before,after) if len(a['tools'])!=len(b['tools'])],['P048','P051','P134'])

    def test_caption_anchors_not_relabeled_methods(self):
        for pid in ['P048','P051']:
            use=next(t for t in self.uses(pid) if t['tool_id']=='enrichr')
            self.assertEqual(use['usage_context'],'figure-legend-analysis-description')

    def test_library_only_is_not_enrichment_execution(self):
        r=self.roles['P055']; self.assertEqual(r['qualified_component_overlap'],['current-human-mouse-library-download'])
        self.assertFalse(r['reported_enrichment_analysis']); self.assertIn('FGSEA', ' '.join(r['remaining_author_roles']))

    def test_julia_local_recalculation_gap_retained(self):
        r=self.roles['P121']; self.assertFalse(r['complete_author_workflow_qualified'])
        self.assertTrue(any('Julia Fisher' in s for s in r['remaining_author_roles']))

    def test_ambiguous_host_gets_no_execution_credit(self):
        r=self.roles['P182']; self.assertEqual(r['qualified_component_overlap'],[])
        self.assertEqual(r['declared_host'],'declared-ClusterProfiler'); self.assertFalse(r['enrichment_service_execution_verified'])

    def test_client_versions_not_inferred_server_versions(self):
        for pid in ['P009','P161','P169','P182']:
            use=next(t for t in self.uses(pid) if t['tool_id']=='enrichr')
            self.assertIsNone(use['version_reported_near_name'])

    def test_literal_library_and_inequality_retained(self):
        self.assertEqual(self.roles['P161']['reported_library_labels'],['WikiPathway_2023_Human'])
        self.assertIn('log2[FC]<-0.5',self.roles['P039']['use_summary'])
        self.assertIn('two-sided Fisher',self.roles['P098']['required_caution'])

    def test_historical_paper_identity_change_refused(self):
        after=dict(self.after); papers=json.loads(after[model.META[0]]); papers[0]['doi']='changed'
        after[model.META[0]]=model.encoded(papers)
        with self.assertRaises(ValueError): model.check(self.before,after,self.census,self.q)

    def test_unrelated_inventory_change_refused(self):
        after=dict(self.after); rows=json.loads(after[model.META[1]]); rows[0]['name']='changed'
        after[model.META[1]]=model.encoded(rows)
        with self.assertRaises(ValueError): model.check(self.before,after,self.census,self.q)

    def test_wrong_current_qualification_refused(self):
        q=copy.deepcopy(self.q); q['normal_tests_per_JVM']=41
        with self.assertRaises(ValueError): model.transition(self.before,self.census,q)

    def test_false_family_credit_refused(self):
        q=copy.deepcopy(self.q); q['new_family_credit']=1
        with self.assertRaises(ValueError): model.transition(self.before,self.census,q)

    def test_false_author_execution_refused(self):
        c=copy.deepcopy(self.census); c['roles'][0]['author_runtime_executed']=True
        with self.assertRaises(ValueError): build(c)

    def test_changed_library_source_decision_refused(self):
        c=copy.deepcopy(self.census); next(r for r in c['roles'] if r['paper_id']=='P161')['reported_library_labels']=['WikiPathways_2023_Human']
        with self.assertRaises(ValueError): build(c)

    def test_old_review_object_mutation_refused(self):
        before=dict(self.before); reviews=json.loads(before[model.META[5]])
        next(r for r in reviews['reviews'] if r['tool_id']=='enrichr')['use_summary']='changed'
        before[model.META[5]]=model.encoded(reviews)
        with self.assertRaises(ValueError): model.transition(before,self.census,self.q)

    def test_new_notice_precedes_old_and_embedded_population_updated(self):
        for n in model.META[6:]:
            text=self.after[n].decode(); self.assertLess(text.index('id="enrichr-catalog-notice"'),text.index('id="coloc-catalog-notice"'))
            self.assertIn('Warnings and required actions:',text)


if __name__=='__main__': unittest.main()
