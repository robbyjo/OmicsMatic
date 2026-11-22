"""Admission invariants against actual frozen predecessor and qualification evidence."""
from pathlib import Path
import copy,json,os,subprocess,unittest
from skat225_catalog import model as m

class CatalogModelTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base='abee077e478906a1a2d3f15005806f9767c2c880'
        cls.before={n:subprocess.check_output(['git','--no-replace-objects','show',base+':'+n],cwd=m.ROOT) for n in m.META}
        cls.q=json.loads(Path(os.environ.get('SKAT225_QUALIFICATION',str(m.ROOT/'docs/literature/skat225-qualification/QUALIFICATION.json'))).read_bytes())
    def test_one_upstream_family_and_one_alias_preserve_all_source_uses(self):
        after=m.transition(self.before,self.q);result=m.check(self.before,after,self.q)
        self.assertEqual((148,27,121,3,25),(result['scoped_families'],result['native'],result['upstream'],result['validated_family_alias_labels'],result['method_overlap']))
        self.assertEqual(self.before[m.META[0]],after[m.META[0]]);self.assertEqual(self.before[m.META[5]],after[m.META[5]])
    def test_registry_keeps_other483_provider_definitions_exact(self):
        before=m.previous.registry();after=m.registry()
        self.assertEqual([r for r in before if r['id'] not in m.TARGETS],[r for r in after if r['id'] not in m.TARGETS])
        self.assertEqual('skat',next(r for r in after if r['id']=='skato')['alias_of'])
    def test_incomplete_control_evidence_cannot_receive_credit(self):
        for key in ['normal_tests','matrix_cases','additional_zero_resampling_cases','public_resealed_controls','public_runtime_controls','fresh_source_audit_papers','source_roles_preserved']:
            q=copy.deepcopy(self.q);q[key]-=1
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,q)
    def test_whole_tool_paper_or_goal_claim_is_rejected(self):
        for key in ['whole_tool_qualified','whole_paper_reproduced','whole_goal_complete']:
            q=copy.deepcopy(self.q);q[key]=True
            with self.subTest(key=key),self.assertRaises(ValueError):m.transition(self.before,q)
    def test_changed_actual_producer_is_rejected(self):
        q=copy.deepcopy(self.q);q['producer']['sha256']='0'*64
        with self.assertRaises(ValueError):m.transition(self.before,q)
    def test_unestablished_paper_command_cannot_be_claimed(self):
        q=copy.deepcopy(self.q);q['P246_exact_command_reconstructed']=True
        with self.assertRaises(ValueError):m.transition(self.before,q)
    def test_already_admitted_provider_is_refused(self):
        after=m.transition(self.before,self.q)
        with self.assertRaises(ValueError):m.transition(after,self.q)
    def test_source_role_edit_is_refused(self):
        before=dict(self.before);papers=json.loads(before[m.META[0]])
        use=next(u for p in papers if p['paper_id']=='P246' for u in p['tools'] if u['tool_id']=='skat');use['source_role_qualification']['usage_context']='assumed-default-command';before[m.META[0]]=m.primary.encoded(papers)
        with self.assertRaises(ValueError):m.transition(before,self.q)
    def test_unrelated_inventory_edit_is_refused(self):
        after=m.transition(self.before,self.q);inventory=json.loads(after[m.META[1]]);next(r for r in inventory if r['id'] not in m.TARGETS)['paper_count']+=1;after[m.META[1]]=m.primary.encoded(inventory)
        with self.assertRaises(ValueError):m.check(self.before,after,self.q)
    def test_embedded_report_uses_current_counts_and_unchanged_papers(self):
        import re
        after=m.transition(self.before,self.q);page=after[m.META[6]].decode();payload=json.loads(re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S)[1])
        self.assertEqual(json.loads(self.before[m.META[0]]),payload['papers']);self.assertEqual(148,payload['summary']['integration_family_counts']['scoped']);self.assertEqual(1,page.count('id="skat225-catalog-notice"'))
        self.assertIn('SKIP3',page);self.assertIn('FAIL2',page);self.assertIn('full250-paper implementation goal remains unfinished',page)

if __name__=='__main__':unittest.main()
