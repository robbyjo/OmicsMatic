"""Meaningful scope/preservation controls on actual frozen pre-admission metadata."""
import copy
import json
from pathlib import Path
import re
import unittest
from coloc_catalog import model
from coloc_catalog.coverage import build,encoded
from coloc_catalog.evidence import ROOT,baseline,qualification
from coloc_catalog.catalog import failure_code
from coloc_source_roles import MissingSource

class Admission(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before=baseline()
        cls.census=json.loads((ROOT/'docs/literature/coloc-abf-handover/source-roles.json').read_bytes())
        cls.q=qualification();cls.after=model.transition(cls.before,cls.census,cls.q)

    def test_exact_counts_and_no_whole_tool_or_goal_claim(self):
        r=model.check(self.before,self.after,self.census,self.q)
        self.assertEqual((135,25,110,1439,1261),(r['new_scoped_integrations'],r['native_integrations'],r['upstream_integrations'],r['reviewed_uses'],r['unreviewed_uses']))
        self.assertFalse(r['whole_tool_qualified']);self.assertFalse(r['whole_goal_complete'])

    def test_old_reviews_and_P190_observation_are_preserved(self):
        old=json.loads(self.before[model.META[5]])['reviews'];new=json.loads(self.after[model.META[5]])['reviews'];self.assertEqual(old,new[:1413])
        a=next(p for p in json.loads(self.before[model.META[0]]) if p['paper_id']=='P190');b=next(p for p in json.loads(self.after[model.META[0]]) if p['paper_id']=='P190')
        old_use=next(t for t in a['tools'] if t['tool_id']=='coloc');new_use=next(t for t in b['tools'] if t['tool_id']=='coloc')
        self.assertEqual(old_use,{k:v for k,v in new_use.items() if k!='source_role_qualification'})

    def test_method_and_host_scope_is_not_inferred(self):
        roles=build(self.census)['roles'];self.assertEqual(8,sum(r['abf_method_branch_explicit'] for r in roles))
        self.assertEqual(5,sum(bool(r['native_alternative_overlap']) for r in roles));self.assertEqual(16,sum(r['source_entrypoint_unreported'] for r in roles))
        self.assertEqual(3,sum(r['hosted_execution_scope_unvalidated'] for r in roles))
        for r in roles:
            self.assertFalse(r['native_BF_literal_source_parity']);self.assertFalse(r['author_runtime_executed']);self.assertFalse(r['whole_paper_reproduced'])

    def test_source_edition_and_prior_literals_remain_distinct(self):
        papers={p['paper_id']:p for p in json.loads(self.after[model.META[0]])}
        use=lambda pid:next(t for t in papers[pid]['tools'] if t['tool_id']=='coloc')
        self.assertIsNone(use('P074')['version_reported_near_name']);self.assertEqual(['3.2.1','5.1.0'],[r['literal'] for r in use('P074')['source_role_qualification']['reported_editions']])
        self.assertEqual('5.1.0.1',use('P212')['version_reported_near_name']);self.assertEqual('v.5',use('P234')['version_reported_near_name'])
        self.assertIn('1e-6',use('P201')['use_summary']);self.assertIn('p1+p12=0.0026',use('P219')['use_summary'])

    def test_forged_specific_entrypoint_is_refused(self):
        c=copy.deepcopy(self.census);next(r for r in c['roles'] if r['paper_id']=='P090')['method_family']='susie'
        with self.assertRaises(ValueError):model.transition(self.before,c,self.q)

    def test_changed_manual_source_summary_is_refused(self):
        c=copy.deepcopy(self.census);c['roles'][0]['use_summary']='all methods and editions fully reproduced'
        with self.assertRaises(ValueError):build(c)

    def test_missing_or_duplicate_role_is_refused(self):
        for action in ['remove','duplicate']:
            c=copy.deepcopy(self.census)
            if action=='remove':c['roles'].pop()
            else:c['roles'][-1]=copy.deepcopy(c['roles'][0])
            with self.assertRaises(ValueError):build(c)

    def test_false_literal_BF_parity_is_refused(self):
        q=copy.deepcopy(self.q);q['native_BF_literal_source_parity']=True
        with self.assertRaises(ValueError):model.transition(self.before,self.census,q)

    def test_incomplete_component_tests_are_refused(self):
        q=copy.deepcopy(self.q);q['ABF_workflow_tests_per_JVM']=0
        with self.assertRaises(ValueError):model.transition(self.before,self.census,q)

    def test_P190_cannot_be_reappended_as_new_credit(self):
        c=copy.deepcopy(self.census);next(r for r in c['roles'] if r['paper_id']=='P190')['existing_review_preserved']=False
        with self.assertRaises(ValueError):model.transition(self.before,c,self.q)

    def test_already_admitted_family_cannot_gain_credit_twice(self):
        before=dict(self.before);rows=json.loads(before[model.META[1]]);next(t for t in rows if t['id']=='coloc')['implementation_status']='native-subset-validated';before[model.META[1]]=encoded(rows)
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)

    def test_unrelated_observation_or_review_tampering_is_refused(self):
        after=dict(self.after);papers=json.loads(after[model.META[0]]);papers[0]['doi']='changed';after[model.META[0]]=encoded(papers)
        with self.assertRaises(ValueError):model.check(self.before,after,self.census,self.q)
        after=dict(self.after);reviews=json.loads(after[model.META[5]]);reviews['reviews'][0]['use_summary']='changed';after[model.META[5]]=encoded(reviews)
        with self.assertRaises(ValueError):model.check(self.before,after,self.census,self.q)

    def test_missing_metadata_or_source_identity_is_refused(self):
        before=dict(self.before);before.pop(model.META[0])
        with self.assertRaises(ValueError):model.transition(before,self.census,self.q)
        c=copy.deepcopy(self.census);c['roles'][0]['source_xml_sha256']='0'*64
        with self.assertRaises(ValueError):model.transition(self.before,c,self.q)

    def test_inverse_restores_exact_old_review_bytes_including_CRLF(self):
        from wire_signac_catalog import delta
        from catalog_source_integrity import reverse_delta
        for crlf in [False,True]:
            before=dict(self.before)
            if crlf:before[model.META[5]]=before[model.META[5]].replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')
            after=model.transition(before,self.census,self.q);change=delta(model.META[5],before[model.META[5]],after[model.META[5]])
            self.assertEqual(before[model.META[5]],reverse_delta(after[model.META[5]],change))

    def test_embedded_data_and_history_notice_match(self):
        page=self.after[model.META[6]].decode();match=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S)
        self.assertIsNotNone(match);self.assertEqual(dict(papers=json.loads(self.after[model.META[0]]),tools=json.loads(self.after[model.META[1]]),summary=json.loads(self.after[model.META[2]])),json.loads(match[1]))
        for name in model.META[6:]:
            text=self.after[name].decode();self.assertEqual(1,text.count(model.NOTICE));self.assertEqual(1,text.count(model.PREVIOUS_NOTICE));self.assertIn('Previous PLINK-family catalog checkpoint (historical)',text)

    def test_registry_and_family_are_scoped_not_whole_package(self):
        rows=model.registry();self.assertEqual(485,len(rows));row=next(t for t in rows if t['id']=='coloc');self.assertEqual('native-subset-validated',row['current_coverage'])
        row=next(t for t in json.loads(self.after[model.META[1]]) if t['id']=='coloc');self.assertFalse(row['whole_tool_qualified']);self.assertTrue(row['source_build_required']);self.assertFalse(row['released_binary_contains_workflow_adapter'])

    def test_missing_prerequisite_and_changed_evidence_have_distinct_exit_codes(self):
        self.assertEqual(3,failure_code(MissingSource('missing XML')));self.assertEqual(3,failure_code(FileNotFoundError('missing exact archive')));self.assertEqual(2,failure_code(ValueError('changed source')))

if __name__=='__main__':unittest.main()
