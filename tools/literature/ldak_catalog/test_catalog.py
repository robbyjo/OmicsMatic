#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-role and unrelated-catalog retention controls for current LDAK admission."""
import argparse,copy,json,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];sys.dont_write_bytecode=True;sys.path.insert(0,str(ROOT/'tools/literature'))
from ldak_catalog.catalog import baseline,primary
from ldak_catalog.model import META,POSITIVE,registry,transition,check
CACHE=None

class CatalogTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=baseline();cls.census=primary(CACHE,cls.before);cls.after=transition(cls.before,cls.census)
 def test_current_results_are_distinct_from_context_and_unreported_commands(self):
  papers=json.loads(self.after[META[0]]);positive={p['paper_id'] for p in papers if any(t['tool_id']=='ldak' for t in p['tools'])};self.assertEqual({'P090','P109','P120','P171','P238'},positive)
  context=next(r for r in self.census['roles'] if r['paper_id']=='P245');self.assertFalse(context['positive_record_retained']);self.assertIsNone(context['selected_project_profile']);self.assertEqual('background-or-previous-work-not-current-ldak-execution',context['usage_context'])
  benchmark=next(t for p in papers if p['paper_id']=='P238' for t in p['tools'] if t['tool_id']=='ldak');self.assertIsNone(benchmark['reviewed_reported_engine_version']);self.assertEqual('current-study-comparative-benchmark',benchmark['usage_context']);self.assertIn('main Methods paragraph specifies GCTA',benchmark['implementation_scope_note']);self.assertFalse(benchmark['whole_paper_reproduced'])
 def test_only_five_positive_reviews_are_appended_and_other_paper_uses_survive(self):
  original=json.loads(self.before['tools/literature/reviewed-uses.json'])['reviews'];current=json.loads(self.after['tools/literature/reviewed-uses.json'])['reviews'];self.assertEqual(original,current[:len(original)]);self.assertEqual(5,len(current)-len(original));self.assertTrue(all(r['tool_id']=='ldak' for r in current[len(original):]));result=check(self.before,self.after,self.census);self.assertEqual(2695,result['observations']);self.assertEqual(1266,result['reviewed_uses']);self.assertEqual(129,result['new_scoped_integrations']);self.assertEqual(24,result['native_integrations']);self.assertEqual(105,result['upstream_integrations'])
 def test_an_unrelated_inventory_rewrite_is_refused(self):
  changed=dict(self.after);inventory=json.loads(changed[META[1]]);next(r for r in inventory if r['id']=='star')['implementation_scope']='Unrelated rewritten behavior';changed[META[1]]=json.dumps(inventory).encode()
  with self.assertRaises(ValueError):check(self.before,changed,self.census)
 def test_changed_primary_anchor_or_background_classification_is_refused(self):
  for kind in ['anchor','context']:
   census=copy.deepcopy(self.census);role=next(r for r in census['roles'] if r['paper_id']==('P245' if kind=='context' else 'P090'))
   if kind=='anchor':role['anchor_sha256']='0'*64
   else:role['usage_context']='current-study-analysis'
   with self.assertRaises(ValueError):transition(self.before,census)
 def test_registry_preserves_the_cumulative_ldsc_admission(self):
  from ldsc_hsq.catalog import registry as predecessor
  old,new=predecessor(),registry();self.assertEqual(485,len(new));self.assertEqual([r for r in old if r['id']!='ldak'],[r for r in new if r['id']!='ldak']);self.assertEqual('native-subset-validated',next(r for r in new if r['id']=='ldsc')['current_coverage']);self.assertEqual('upstream-adapter-validated',next(r for r in new if r['id']=='ldak')['current_coverage'])

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--primary-cache',required=True,type=Path);args,remaining=p.parse_known_args();CACHE=args.primary_cache;unittest.main(argv=[sys.argv[0],*remaining])
