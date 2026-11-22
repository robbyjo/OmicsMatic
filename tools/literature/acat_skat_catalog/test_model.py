"""Preservation, false-admission and scientific provider-boundary controls."""
import copy,json,re,subprocess,unittest
from acat_skat_catalog import primary as p,model as m,evidence as e
from catalog_source_integrity import reverse_delta
from wire_signac_catalog import delta

class ModelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before={n:e.git(p.ROOT,'show',e.BASE+':'+n) for n in m.META};cls.primary=p.frozen()
        cls.qualified=dict(status='PASS_CURRENT_NATIVE_ACAT091_AND_SOURCE_ROLES_FOR_ADMISSION',tests_each_JVM=500,JVMs=3,source_hosts=2,producer=dict(bytes=197362611,sha256='9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b'),source_roles=22,whole_goal_complete=False)
        cls.after=m.transition(cls.before,cls.primary,cls.qualified)
    def role(self,pid,tool):return next(r for r in self.primary['roles'] if r['paper_id']==pid and r['tool_id']==tool)
    def test_all1639_previous_reviews_preserved(self):
        old=json.loads(self.before[m.META[5]])['reviews'];new=json.loads(self.after[m.META[5]])['reviews'];self.assertEqual(old,new[:1639]);self.assertEqual(1661,len(new));self.assertEqual(1661,len({(r['pmcid'],r['tool_id']) for r in new}))
    def test_all250_identities_and2701_unrelated_observations_preserved(self):
        n=0
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):
            self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'});old=[r for r in a['tools'] if r['tool_id'] not in m.PATTERNS];self.assertEqual(old,[r for r in b['tools'] if r['tool_id'] not in m.PATTERNS]);n+=len(old)
        self.assertEqual(2701,n)
    def test_only_p127_skato_is_recovered(self):
        a=json.loads(self.before[m.META[0]]);b=json.loads(self.after[m.META[0]]);added=[(x['paper_id'],r) for x,y in zip(a,b,strict=True) for r in y['tools'] if r['tool_id'] not in {u['tool_id'] for u in x['tools']}];self.assertEqual(1,len(added));pid,row=added[0];self.assertEqual(('P127','skato','SKATO'),(pid,row['tool_id'],row['matched_text']));self.assertIsNone(row['extraction_score']);self.assertEqual(39,row['evidence_paragraph_index'])
    def test_existing_named_anchors_are_fixed(self):
        for a,b in zip(json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]]),strict=True):
            new={r['tool_id']:r for r in b['tools']}
            for r in a['tools']:
                for key in ['tool_id','tool','matched_text','extraction_score','evidence_paragraph_index','evidence_paragraph_sha256','evidence_section','evidence_locator']:self.assertEqual((key in r,r.get(key)),(key in new[r['tool_id']],new[r['tool_id']].get(key)))
    def test_other443_inventory_rows_are_preserved(self):
        self.assertEqual([r for r in json.loads(self.before[m.META[1]]) if r['id'] not in m.PATTERNS],[r for r in json.loads(self.after[m.META[1]]) if r['id'] not in m.PATTERNS])
    def test_other482_definitions_preserved(self):
        from qtltools_catalog.model import registry as old
        self.assertEqual([r for r in old() if r['id'] not in m.PATTERNS],[r for r in m.registry() if r['id'] not in m.PATTERNS]);self.assertEqual(485,len(m.registry()))
    def test_exact_one_native_family_and_no_skat_credit(self):
        result=m.check(self.before,self.after,self.primary,self.qualified);self.assertEqual((147,27,120,1,0),(result['integrations'],result['native_integrations'],result['upstream_integrations'],result['native_family_credit_added'],result['SKAT_family_credit_added']));self.assertEqual((2723,1661,1062),(result['observations'],result['reviewed_uses'],result['pending_uses']))
    def test_skat_statuses_and_alias_credit_remain(self):
        rows={r['id']:r for r in json.loads(self.after[m.META[1]])};self.assertEqual('native-subset-validated',rows['acat']['implementation_status']);self.assertEqual('method-overlap',rows['skat']['implementation_status']);self.assertEqual('method-overlap',rows['skato']['implementation_status']);self.assertEqual(9,rows['skato']['paper_count']);self.assertEqual(2,sum(r['implementation_status']=='validated-family-alias' for r in rows.values()))
    def test_glued_spellings_do_not_create_three_families(self):
        self.assertTrue(re.fullmatch(m.PATTERNS['skato'],'SKATO'));self.assertFalse(re.search(m.PATTERNS['skat'],'SKATO'));self.assertFalse(re.search(m.PATTERNS['skat'],'SKAT-O'));self.assertTrue(re.fullmatch(m.PATTERNS['acat'],'ACATV'));self.assertTrue(re.fullmatch(m.PATTERNS['acat'],'ACATO'))
    def test_source_editions_are_tool_specific(self):
        self.assertEqual('0.91',self.role('P131','acat')['reported_tool_edition']);self.assertEqual('2.2.5',self.role('P246','skat')['reported_tool_edition']);self.assertIsNone(self.role('P246','skato')['reported_tool_edition']);self.assertEqual('2.2.5',self.role('P246','skato')['provider']['edition'])
    def test_p090_does_not_turn_comparators_into_runs(self):
        for tool in ['skat','skato']:
            row=self.role('P090',tool);self.assertEqual('comparator-not-established-as-executed',row['usage_context']);self.assertIn('not-an-executed-SKAT',row['provider']['relationship']);self.assertIsNone(row['reported_tool_edition'])
    def test_p127_rho_grid_keeps_nonstandard_calibration(self):
        row=self.role('P127','skato');self.assertTrue(row['recovered_observation']);self.assertIn('minimum-p',row['implementation_scope_note']);self.assertEqual('REMETA',row['provider']['name']);self.assertIsNone(row['reported_tool_edition'])
    def test_p154_keeps_seak_and_regenie_separate(self):
        row=self.role('P154','skat');self.assertEqual(('SEAK','0.4.3'),(row['provider']['name'],row['provider']['edition']));self.assertIn('5000',row['implementation_scope_note']);self.assertIn('REGENIE',row['implementation_scope_note']);self.assertIsNone(row['reported_tool_edition'])
    def test_p207_is_not_canonical_acat_o(self):
        row=self.role('P207','acat');self.assertEqual('3.0',row['provider']['edition']);self.assertIn('six-component',row['implementation_scope_note']);self.assertIn('below 10',self.role('P207','skato')['implementation_scope_note']);self.assertIn('MAC<=10',self.role('P207','skato')['implementation_scope_note'])
    def test_p220_version_and_reused_results_are_separate(self):
        row=self.role('P220','skato');self.assertEqual('1.1.9',row['provider']['edition']);self.assertIn('bibliography citation45',row['implementation_scope_note']);self.assertIn('not fresh local fits',row['implementation_scope_note']);self.assertEqual('method-details',row['supporting_evidence'][0]['evidence_role'])
    def test_p221_missing_models_and_unknown_sign_are_retained(self):
        row=self.role('P221','acat');self.assertIn('at least two',row['usage_summary']);self.assertIn('single-model',row['usage_summary']);self.assertIn('do not determine a Z-score direction',row['implementation_scope_note']);self.assertIsNone(row['provider'])
    def test_cauchy_only_and_nested_names_do_not_invent_software(self):
        decisions={r['paper_id']:r for r in self.primary['negative_decisions']};self.assertEqual({'P123','P147','P210','P176','P106'},set(decisions));self.assertIn('CDF',decisions['P210']['reason']);self.assertFalse(any(r['paper_id'] in {'P123','P147','P210'} for r in self.primary['roles']));self.assertFalse(any(r['paper_id']=='P176' and r['tool_id']=='skat' for r in self.primary['roles']))
    def test_p190_supplement_uniform_weight_scope(self):
        row=self.role('P190','acat');self.assertEqual([31,32,36],row['auxiliary_evidence'][0]['pages']);self.assertEqual(p.PDF_PIN,row['auxiliary_evidence'][0]['pdf']);self.assertIn('uniform weights',row['usage_summary']);self.assertIsNone(row['reported_tool_edition'])
    def test_no_author_or_whole_paper_execution_claims(self):
        for r in self.primary['roles']:self.assertTrue(all(not r[k] for k in ['author_pipeline_executed','paper_executed_commit_confirmed','whole_tool_qualified','whole_paper_reproduced']))
    def test_wrong_scientific_qualification_refused(self):
        for key,value in [('tests_each_JVM',499),('source_hosts',1),('source_roles',21),('whole_goal_complete',True)]:
            q=copy.deepcopy(self.qualified);q[key]=value
            with self.assertRaises(ValueError):m.transition(self.before,self.primary,q)
    def test_duplicate_apply_refused(self):
        with self.assertRaises(ValueError):m.transition(self.after,self.primary,self.qualified)
    def test_changed_primary_interpretation_refused(self):
        primary=copy.deepcopy(self.primary);primary['roles'][0]['usage_summary']='Executed standalone SKAT'
        with self.assertRaises(ValueError):m.transition(self.before,primary,self.qualified)
    def test_duplicate_old_ledger_refused(self):
        before=dict(self.before);ledger=json.loads(before[m.META[5]]);ledger['reviews'][1]=copy.deepcopy(ledger['reviews'][0]);before[m.META[5]]=p.encoded(ledger)
        with self.assertRaises(ValueError):m.transition(before,self.primary,self.qualified)
    def test_old_ledger_serialized_prefix_is_exact(self):
        old=self.before[m.META[5]];newline=b'\r\n' if b'\r\n' in old else b'\n';closing=old.rfind(newline+b'  ]');self.assertGreater(closing,0);self.assertTrue(self.after[m.META[5]].startswith(old[:closing]))
    def test_review_inverse_reconstructs_all_prior_bytes(self):
        n=m.META[5];self.assertEqual(self.before[n],reverse_delta(self.after[n],delta(n,self.before[n],self.after[n])))
    def test_report_embeds_exact_after_population(self):
        text=self.after[m.META[6]].decode();match=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',text,re.S);value=json.loads(match[1]);self.assertEqual(json.loads(self.after[m.META[0]]),value['papers']);self.assertEqual(json.loads(self.after[m.META[1]]),value['tools']);self.assertEqual(json.loads(self.after[m.META[2]]),value['summary']);self.assertEqual(1,text.count('id="acat-skat-catalog-notice"'))

if __name__=='__main__':unittest.main()
