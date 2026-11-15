# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import copy,json,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from celldex114_catalog.model import *
from celldex114_catalog.catalog import load_before,load_catalog_registry,compatibility,qualification
from singler_catalog.source import census
from hicat109_catalog.bridge import metadata_view
from catalog_source_integrity import reverse_delta
class CatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META}
  cls.after=transition(ROOT,cls.before)
 def test_one_existing_family_no_new_native_or_observation_credit(self):
  result=check_transition(ROOT,self.before,self.after)
  self.assertEqual(tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing']),(250,441,2682,1187,1495,119,263))
  self.assertEqual((result['added_observations'],result['added_reviews'],result['new_registry_definitions'],result['native_family_credit_added']),(0,0,0,0))
  counts=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((counts['upstream-adapter-validated'],counts['native-subset-validated']),(99,20))
 def test_primary_facts_and_every_unrelated_review_unchanged(self):
  old,new=json.loads(self.before[REVIEWS]),json.loads(self.after[REVIEWS]);self.assertEqual(len(old['reviews']),len(new['reviews']))
  for a,b in zip(old['reviews'],new['reviews']):
   expected=copy.deepcopy(a)
   if a['tool_id']=='celldex':expected['implementation_scope_note']=NOTE
   self.assertEqual(expected,b)
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
   expected=copy.deepcopy(a)
   for row in expected['tools']:
    if row['tool_id']=='celldex':row['implementation_scope_note']=NOTE
   self.assertEqual(expected,b)
  self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='celldex'],[r for r in json.loads(self.after[META[0]]) if r['id']!='celldex'])
 def test_literal_reference_and_unknown_author_choices_retained(self):
  review=next(r for r in json.loads(self.after[REVIEWS])['reviews'] if r['tool_id']=='celldex')
  self.assertEqual((review['paper_id'],review['version_reported_near_name'],review['evidence_paragraph_index']),('P051','1.14.0',119))
  self.assertEqual(review['reported_settings']['reference_description_literal'],'MouseRNAseq ImmGen databases')
  self.assertIsNone(review['reported_settings']['exact_reference_function']);self.assertIsNone(review['reported_settings']['reference_snapshot'])
  row=next(r for r in json.loads(self.after[META[0]]) if r['id']=='celldex')
  for key in ['canonical_adapter_available','root_dispatch_available','selected_environment_is_author_environment','author_reference_functions_established','author_reference_snapshots_established','author_caller_options_established','reference_matrices_redistributed','whole_tool_qualified','whole_paper_reproduced']:self.assertFalse(row[key],key)
 def test_complete_qualification_requires_successful_returns_not_matching_errors(self):
  result=qualification(ROOT);self.assertEqual((result['original_controls'],result['complete_returns'],result['original_errors']),(16,12,4))
  self.assertEqual((result['public_Linux_guards'],result['Windows_prerequisite_guards'],result['complete_retained_private_file_entries']),(12,2,4469))
  self.assertFalse(result['reference_matrices_redistributed'])
 def test_exact_inverse_and_full479_registry(self):
  self.assertEqual(reverse_delta(self.after[REVIEWS],compatibility(self.before,self.after)['paths'][0]),self.before[REVIEWS])
  self.assertEqual(len(load_catalog_registry(ROOT)),479)
  with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,'tools/literature/celldex114/Worker.R':b'override forbidden'})
 def test_source_invention_native_double_credit_or_other_missing_family_rejected(self):
  for tool,key,value in [('celldex','whole_tool_qualified',True),('celldex','native_family_credit_added',1),('spotlight','implementation_status','upstream-adapter-validated')]:
   inv=json.loads(self.after[META[0]]);next(r for r in inv if r['id']==tool)[key]=value
   with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:encoded(inv)})
  papers=json.loads(self.after[PAPERS]);next(t for p in papers if p['paper_id']=='P051' for t in p['tools'] if t['tool_id']=='celldex')['version_reported_near_name']='1.16.0'
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,PAPERS:encoded(papers)})
 def test_all250_primary_sources_preserve_company_false_positive(self):
  result=census(ROOT);self.assertEqual(result['papers'],250)
  found={r['paper_id']:[a['paragraph_index'] for a in r['anchors']['celldex']] for r in result['census'] if r['anchors']['celldex']}
  self.assertEqual(found,{'P051':[119],'P069':[58]})
  observations=[(p['paper_id'],t['tool_id']) for p in json.loads(self.after[PAPERS]) for t in p['tools'] if t['tool_id']=='celldex'];self.assertEqual(observations,[('P051','celldex')])
if __name__=='__main__':unittest.main()
