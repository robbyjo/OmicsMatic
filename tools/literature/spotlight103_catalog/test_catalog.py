# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import copy,json,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from spotlight103_catalog.model import *
from spotlight103_catalog.catalog import load_before,load_catalog_registry,compatibility,qualification
from singler_catalog.source import census
from hicat109_catalog.bridge import metadata_view
from catalog_source_integrity import reverse_delta

class CatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META}
  cls.after=transition(ROOT,cls.before)
 def test_only_one_existing_upstream_family_credit(self):
  result=check_transition(ROOT,self.before,self.after)
  self.assertEqual(tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing']),(250,441,2682,1187,1495,120,262))
  self.assertEqual(tuple(result[k] for k in ['added_observations','added_reviews','new_registry_definitions','native_family_credit_added']),(0,0,0,0))
  counts=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((counts['upstream-adapter-validated'],counts['native-subset-validated']),(100,20))
 def test_every_primary_fact_and_unrelated_review_preserved(self):
  for a,b in zip(json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews']):
   expected=copy.deepcopy(a)
   if a['tool_id']=='spotlight':expected['implementation_scope_note']=NOTE
   self.assertEqual(expected,b)
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
   expected=copy.deepcopy(a)
   for row in expected['tools']:
    if row['tool_id']=='spotlight':row['implementation_scope_note']=NOTE
   self.assertEqual(expected,b)
  self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='spotlight'],[r for r in json.loads(self.after[META[0]]) if r['id']!='spotlight'])
 def test_literal_source_contexts_and_downstream_threshold_unchanged(self):
  r=next(r for r in json.loads(self.after[REVIEWS])['reviews'] if r['tool_id']=='spotlight')
  self.assertEqual((r['paper_id'],r['version_reported_near_name'],[e['paragraph_index'] for e in r['supporting_evidence']]),('P051','1.0.3',[114,119]))
  self.assertEqual((r['reported_settings']['predicted_neutrophil_fraction_threshold'],r['reported_settings']['comparator']),(0.1,'>='))
  row=next(r for r in json.loads(self.after[META[0]]) if r['id']=='spotlight')
  for key in ['canonical_adapter_available','root_dispatch_available','selected_environment_is_author_environment','author_exact_source_commit_established','original_NMF_convergence_certified','biological_accuracy_established','whole_tool_qualified','whole_paper_reproduced']:self.assertFalse(row[key],key)
 def test_real_positive_original_calls_and_complete_saved_record_readback(self):
  q=qualification(ROOT);self.assertEqual((q['original_controls'],q['complete_returns'],q['original_errors']),(29,20,9))
  self.assertEqual((q['public_Linux_guards'],q['Windows_prerequisite_guards']),(11,2));self.assertGreater(q['complete_synthetic_record_file_entries'],500)
  self.assertTrue(q['full_cpp_backtraces_retained_but_not_required_byte_identical'])
 def test_complete_review_inverse_and479_registry(self):
  self.assertEqual(reverse_delta(self.after[REVIEWS],compatibility(self.before,self.after)['paths'][0]),self.before[REVIEWS])
  self.assertEqual(len(load_catalog_registry(ROOT)),479)
  with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,'tools/literature/spotlight103/Worker.R':b'override forbidden'})
 def test_invented_scope_version_native_credit_or_other_family_rejected(self):
  for tool,key,value in [('spotlight','whole_tool_qualified',True),('spotlight','native_family_credit_added',1),('spotlight','original_NMF_convergence_certified',True),('bd-rhapsody','implementation_status','upstream-adapter-validated')]:
   inv=json.loads(self.after[META[0]]);next(r for r in inv if r['id']==tool)[key]=value
   with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:encoded(inv)})
  papers=json.loads(self.after[PAPERS]);next(t for p in papers if p['paper_id']=='P051' for t in p['tools'] if t['tool_id']=='spotlight')['version_reported_near_name']='1.0.0'
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,PAPERS:encoded(papers)})
 def test_unapplied_options_failure_recovery_and_full_goal_are_prominent(self):
  for name in [META[2],META[3],META[4],META[5]]:
   text=self.after[name].decode()
   for word in ['min_prop','NaN','convergence','SKIP3','FAIL2','COMPLETED.json','Cell Ranger/ARC','262','ASLR']:self.assertIn(word,text)
  observations=[p['paper_id'] for p in json.loads(self.after[PAPERS]) for t in p['tools'] if t['tool_id']=='spotlight'];self.assertEqual(observations,['P051'])
if __name__=='__main__':unittest.main()
