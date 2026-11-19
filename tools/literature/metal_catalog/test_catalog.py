#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Meaningful source-semantic, credit and unrelated-record refusal controls."""
import argparse,copy,json,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];sys.dont_write_bytecode=True;sys.path.insert(0,str(ROOT/'tools/literature'))
from metal_catalog.catalog import baseline,primary
from metal_catalog.model import META,registry,transition,check
CACHE=None


class CatalogTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=baseline();cls.census=primary(CACHE,cls.before);cls.after=transition(cls.before,cls.census)

 def uses(self):return {p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='metal') for p in json.loads(self.after[META[0]]) if any(t['tool_id']=='metal' for t in p['tools'])}

 def test_exact_counts_and_old_review_prefix_are_retained(self):
  report=check(self.before,self.after,self.census);self.assertEqual((130,24,106,1290,1405,2695),(report['new_scoped_integrations'],report['native_integrations'],report['upstream_integrations'],report['reviewed_uses'],report['unreviewed_uses'],report['observations']))
  old=json.loads(self.before['tools/literature/reviewed-uses.json'])['reviews'];new=json.loads(self.after['tools/literature/reviewed-uses.json'])['reviews'];self.assertEqual(old,new[:1266]);self.assertEqual(24,len(new[1266:]));self.assertTrue(all(r['tool_id']=='metal' for r in new[1266:]))

 def test_frequency_gene_eqtl_and_benchmark_roles_stay_distinct(self):
  uses=self.uses();self.assertEqual('current-study-allele-frequency-support',uses['P212']['usage_context']);self.assertIn('frequency',uses['P212']['analysis_role']);self.assertIn('CNV',uses['P114']['analysis_role']);self.assertEqual('eQTL meta-analysis',uses['P203']['analysis_role']);self.assertEqual('current-study-comparative-benchmark-data-preparation',uses['P076']['usage_context']);self.assertIn('RE2C',uses['P123']['use_summary']+' '+uses['P123']['implementation_scope_note'])

 def test_literal_historical_editions_and_unknown_author_settings_survive(self):
  uses=self.uses();self.assertEqual({'P076','P088','P105','P114','P123','P126','P243'},{id for id,u in uses.items() if u['reported_historical_edition_not_reproduced']});self.assertIn('Windows',uses['P243']['version_reported_near_name']);self.assertIsNone(uses['P203']['version_reported_near_name']);self.assertTrue(all(not u['exact_author_settings_established'] and not u['whole_paper_reproduced'] for u in uses.values()))

 def test_p077_reanchor_updates_all_pointers_and_keeps_support(self):
  old=next(t for p in json.loads(self.before[META[0]]) if p['paper_id']=='P077' for t in p['tools'] if t['tool_id']=='metal');new=self.uses()['P077'];role=next(r for r in self.census['roles'] if r['paper_id']=='P077')
  self.assertEqual(42,old['evidence_paragraph_index']);self.assertEqual(52,new['evidence_paragraph_index']);self.assertNotEqual(old['evidence_paragraph_sha256'],new['evidence_paragraph_sha256']);self.assertEqual(role['anchor_section'],new['evidence_section']);self.assertEqual(role['anchor_sha256'],new['evidence_paragraph_sha256']);self.assertEqual(role['anchors'][1:],new['supporting_evidence']);self.assertIn(role['anchor_section'],new['evidence_locator'])

 def test_changed_source_role_summary_edition_and_anchor_are_refused(self):
  for field,value in [('role','inverse-variance'),('use_summary','Invented paper result'),('reported_edition_literal','2020-05-05'),('anchor_sha256','0'*64),('positive_use',False),('exact_author_settings_established',True)]:
   with self.subTest(field=field):
    census=copy.deepcopy(self.census);next(r for r in census['roles'] if r['paper_id']=='P243')[field]=value
    with self.assertRaises(ValueError):transition(self.before,census)

 def test_changed_support_and_primary_identity_are_refused(self):
  for kind in ['support','identity']:
   with self.subTest(kind=kind):
    census=copy.deepcopy(self.census)
    if kind=='support':next(r for r in census['roles'] if r['paper_id']=='P077')['anchors'][1]['paragraph_sha256']='0'*64
    else:census['primary_files'][0]['xml_sha256']='0'*64
    with self.assertRaises(ValueError):transition(self.before,census)

 def test_missing_duplicate_and_extra_positive_roles_are_refused(self):
  for kind in ['missing','duplicate','extra']:
   census=copy.deepcopy(self.census)
   if kind=='missing':census['roles'].pop()
   elif kind=='duplicate':census['roles'][-1]=copy.deepcopy(census['roles'][0])
   else:census['roles'].append(copy.deepcopy(census['roles'][0]))
   with self.assertRaises(ValueError):transition(self.before,census)

 def test_unrelated_inventory_and_paper_rewrites_are_refused(self):
  for name,kind in [(META[1],'inventory'),(META[0],'paper')]:
   changed=dict(self.after);data=json.loads(changed[name])
   if kind=='inventory':next(r for r in data if r['id']=='star')['implementation_scope']='Unrelated rewrite'
   else:data[0]['tools'][0]['use_summary']='Unrelated rewrite'
   changed[name]=json.dumps(data).encode()
   with self.assertRaises(ValueError):check(self.before,changed,self.census)

 def test_old_review_rewrite_or_inflated_credit_is_refused(self):
  for name in ['tools/literature/reviewed-uses.json',META[2]]:
   changed=dict(self.after);data=json.loads(changed[name])
   if name.endswith('reviewed-uses.json'):data['reviews'][0]['use_summary']='Historical rewrite'
   else:data['implementation_status_counts']['native-subset-validated']+=1
   changed[name]=json.dumps(data).encode()
   with self.assertRaises(ValueError):check(self.before,changed,self.census)

 def test_registry_preserves_every_nonmetal_definition(self):
  from ldak_catalog.model import registry as parent
  old,new=parent(),registry();self.assertEqual(485,len(new));self.assertEqual([t for t in old if t['id']!='metal'],[t for t in new if t['id']!='metal']);self.assertEqual('upstream-adapter-validated',next(t for t in new if t['id']=='metal')['current_coverage'])

 def test_exact_inverse_restores_historical_review_bytes(self):
  from wire_signac_catalog import delta
  from catalog_source_integrity import reverse_delta
  name='tools/literature/reviewed-uses.json';row=delta(name,self.before[name],self.after[name]);self.assertEqual(self.before[name],reverse_delta(self.after[name],row));row['edits'][0]['after_chunk_sha256']='0'*64
  with self.assertRaises(ValueError):reverse_delta(self.after[name],row)

 def test_readmitting_the_after_catalog_is_refused(self):
  with self.assertRaises(ValueError):transition(self.after,self.census)


if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();CACHE=args.primary_cache
 with args.out.open('x',encoding='utf-8') as output:
  suite=unittest.defaultTestLoader.loadTestsFromTestCase(CatalogTests);result=unittest.TextTestRunner(verbosity=2).run(suite);json.dump(dict(status='PASS_METAL_CATALOG_CONTROLS' if result.wasSuccessful() else 'FAIL2_NO_ADMISSION',tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped)),output,indent=2);output.write('\n')
 raise SystemExit(0 if result.wasSuccessful() else 2)
