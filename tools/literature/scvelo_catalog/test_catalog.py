# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Protect previous evidence, exact editions, the missing R interface and complete admission counts."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from scvelo_catalog.model import *
from scvelo_catalog.bridge import load_before,metadata_view
from scvelo_catalog.source import scan
from scvelo_catalog.verify import compatibility
from scvelo_catalog.qualification import qualification
from catalog_source_integrity import reverse_delta

class ScveloCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT) if (ROOT/EVIDENCE/'catalog-before.zip').exists() else {n:(ROOT/n).read_bytes() for n in META};cls.after=transition(ROOT,cls.before)
 def reject(self,name,change):
  after=dict(self.after);v=json.loads(after[name]);change(v);after[name]=encoded(v)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_complete_population_and_exact_credit(self):
  result=check_transition(ROOT,self.before,self.after);self.assertEqual((250,433,2672,1168,1504,111,263,1,0),tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing','new_scoped_integration_credit','new_missing_wrapper_implementation_credit']))
 def test_all_previous_reviews_unchanged(self):self.assertEqual(json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews'][:1162])
 def test_other431_families_unchanged(self):self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='scvelo'],[r for r in json.loads(self.after[META[0]]) if r['id'] not in ['scvelo','velociraptor']])
 def test_other2666_observations_and_all_paper_identities_unchanged(self):
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'});self.assertEqual([r for r in a['tools'] if r['tool_id']!='scvelo'],[r for r in b['tools'] if r['tool_id'] not in ['scvelo','velociraptor']])
 def test_five_original_extractions_and_new_named_wrapper_retained(self):
  rows=[r for p in json.loads(self.after[PAPERS]) for r in p['tools'] if r['tool_id'] in ['scvelo','velociraptor']];self.assertEqual(6,len(rows));self.assertTrue(all('source_extraction_before_review' in r for r in rows));self.assertEqual(1,sum(r['tool_id']=='velociraptor' for r in rows))
 def test_complete250_source_census_and_family_anchor_scope(self):
  census=scan(ROOT,json.loads(self.before[PAPERS]));self.assertEqual(6,sum(len(r['scvelo_anchors']) for r in census));self.assertEqual(1,sum(len(r['velociraptor_anchors']) for r in census))
 def test_printed_0_24_must_not_be_rewritten(self):self.reject(PAPERS,lambda ps:next(r for p in ps if p['paper_id']=='P070' for r in p['tools'] if r['tool_id']=='scvelo').update(version_reported_near_name='0.2.4'))
 def test_unknown_wrapper_edition_must_not_be_inferred(self):self.reject(PAPERS,lambda ps:next(r for p in ps if p['paper_id']=='P228' for r in p['tools'] if r['tool_id']=='velociraptor').update(version_reported_near_name='1.20.0'))
 def test_R_wrapper_must_not_receive_implementation_credit(self):self.reject(META[0],lambda ts:next(r for r in ts if r['id']=='velociraptor').update(implementation_status='upstream-adapter-validated',new_scoped_integration_credit=1))
 def test_no_native_or_standalone_credit(self):self.reject(META[0],lambda ts:next(r for r in ts if r['id']=='scvelo').update(implementation_status='native-subset-validated',canonical_adapter_available=True))
 def test_no_other_family_rewrite(self):self.reject(META[0],lambda ts:next(r for r in ts if r['id']=='cell2location').update(reported_0_7a0_execution_qualified=True))
 def test_no_previous_review_rewrite(self):self.reject(REVIEWS,lambda d:d['reviews'][0].update(use_summary='changed old review'))
 def test_no_dropped_or_duplicate_observation(self):self.reject(PAPERS,lambda ps:ps[0]['tools'].append(copy.deepcopy(ps[0]['tools'][0])))
 def test_exact_review_only_inverse(self):
  doc=compatibility(self.before,self.after);self.assertEqual([REVIEWS],[r['path'] for r in doc['paths']]);self.assertEqual(self.before[REVIEWS],reverse_delta(self.after[REVIEWS],doc['paths'][0]))
 def test_scientific_metadata_overlays_forbidden(self):
  for name in [PROGRAM,WORKFLOW+'/objects-0001.zip','tools/literature/scvelo024025/verify.py',CURATION]:
   before=dict(self.before);before[name]=b'forged'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
 def test_prominent_current_counts_and_recovery_notices(self):
  page=self.after[META[4]].decode();self.assertLess(page.index('id="scvelo-required-actions"'),page.index('id="overview"'));self.assertEqual(1,page.count(NOTICE));self.assertNotIn('**',NOTICE)
  for word in ['433','2,672','1,168','1,504','111 scoped','91 original upstream','263 named','0.24','0.3.3','268,435,479','cannot be recovered','SKIP3','FAIL2','unavailable, never zero','condition_counts_complete false','warning_review_required true','Cell Ranger ARC','retry NEW paths','not a standalone JAR']:self.assertIn(word,NOTICE)
 def test_changed_component_source_refused_before_execution(self):
  proof=json.loads((ROOT/WORKFLOW/'VALIDATION.json').read_bytes())
  with tempfile.TemporaryDirectory(dir=ROOT/'build/literature-review/monocle-runtime') as name:
   root=Path(name)
   for n in proof['sources']:
    path=root/n;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/n).read_bytes())
   (root/WORKFLOW/'VALIDATION.json').write_bytes((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());(root/PROGRAM).write_bytes(b'changed source; never execute\n')
   with self.assertRaisesRegex(ValueError,'Qualified original scVelo source/evidence differs'):qualification(root)
if __name__=='__main__':unittest.main()
