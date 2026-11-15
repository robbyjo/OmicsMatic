# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import copy,json,re,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from melodi_catalog.model import *
from melodi_catalog.catalog import qualification,load_before,load_catalog_registry,compatibility
from melodi_catalog.source import audit
from catalog_source_integrity import reverse_delta
from hicat109_catalog.bridge import metadata_view
class CatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT) if (ROOT/PROOF).exists() else {n:(ROOT/n).read_bytes() for n in META};cls.after=transition(ROOT,cls.before)
 def test_corrected_identity_seven_new_observations_and_one_upstream_credit(self):
  q=check_transition(ROOT,self.before,self.after);self.assertEqual(tuple(q[k] for k in ['papers','named_tools','observations','reviewed_uses','unreviewed_uses','new_scoped_integrations','missing']),(250,445,2689,1195,1494,121,265))
  self.assertEqual(tuple(q[k] for k in ['reclassified_existing_observations','added_observations','added_reviews','new_registry_definitions','native_family_credit_added']),(1,7,8,5,0))
  counts=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((counts['upstream-adapter-validated'],counts['native-subset-validated']),(101,20))
 def test_every_previous_review_and_unrelated_primary_fact_is_preserved(self):
  old,new=json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews'];self.assertEqual(old,new[:len(old)]);self.assertEqual(len(new)-len(old),8)
  changed=set()
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'})
   preserved=[t for t in a['tools'] if t['tool_id']!='presto'];self.assertEqual(preserved,b['tools'][:len(preserved)])
   if a!=b:changed.add(a['paper_id'])
  self.assertEqual(changed,{'P042','P073','P169','P192'})
  self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='presto'],[r for r in json.loads(self.after[META[0]]) if r['id'] not in {d['id'] for d in DEFINITIONS}])
 def test_source_specific_versions_thresholds_access_dates_and_data_reuse(self):
  rows={(r.get('paper_id'),r['tool_id']):r for r in json.loads(self.after[REVIEWS])['reviews'] if r.get('paper_id')}
  r=rows['P169','melodi-presto'];self.assertEqual(r['reported_settings']['access_date'],'2024-04-03');self.assertEqual(r['reported_settings']['object'],'colorectal cancer');self.assertIsNone(r['version_reported_near_name'])
  r=rows['P169','oncoscore'];self.assertEqual(r['version_reported_near_name'],'1.30.0');self.assertEqual(r['reported_settings']['novelty_threshold'],21.09);self.assertIsNone(r['reported_settings']['threshold_comparator'])
  self.assertEqual(rows['P169','oncoenrichr']['version_reported_near_name'],'1.4.2.1')
  self.assertEqual(rows['P042','intogen']['version_reported_near_name'],'2023');self.assertFalse(rows['P169','intogen']['reported_settings']['pipeline_execution_established'])
  self.assertEqual(rows['P169','intogen']['reported_settings']['resource_release_date'],'2020-02-01')
  self.assertEqual([rows[p,'haploreg']['version_reported_near_name'] for p in ['P073','P169','P192']],[None,'4.1','4.2'])
 def test_all250_primary_sources_disambiguate_plasmid_funding_and_R_package(self):
  value=audit(ROOT);self.assertEqual(value['papers'],250)
  found={p['paper_id']:[a['paragraph_index'] for a in p['anchors']['presto']] for p in value['census'] if p['anchors']['presto']};self.assertEqual(found,{'P038':[68],'P157':[67],'P169':[15,49]})
  tools=[t for p in json.loads(self.after[PAPERS]) for t in p['tools']];self.assertFalse(any(t['tool_id']=='presto' for t in tools));self.assertEqual(sum(t['tool_id']=='melodi-presto' for t in tools),1)
  registry=load_catalog_registry(ROOT);self.assertEqual(len(registry),484);r=next(r for r in registry if r['id']=='presto');self.assertIsNone(re.search(r['pattern'],'MELODI Presto',re.I));self.assertIsNotNone(re.search(r['pattern'],'presto',re.I))
 def test_complete_qualified_service_and_private_record_readback(self):
  q=qualification(ROOT);self.assertEqual((q['actual_fixture_tests'],q['actual_Windows_live_operations'],q['actual_Linux_live_operations'],q['Windows_records_rechecked_under_Linux']),(28,3,3,3));self.assertGreater(q['complete_retained_private_files'],500)
  self.assertEqual(q['native_analytic_credit'],0);self.assertFalse(q['author_deployed_commit_or_database_snapshot_established'])
 def test_exact_review_byte_inverse_and_no_scientific_source_overlay(self):
  self.assertEqual(reverse_delta(self.after[REVIEWS],compatibility(self.before,self.after)['paths'][0]),self.before[REVIEWS])
  with self.assertRaises(ValueError):metadata_view(ROOT,{**self.before,PROGRAM:b'override forbidden'})
 def test_invented_native_credit_historical_snapshot_or_comparator_rejected(self):
  for tool,key,value in [('melodi-presto','native_analytical_credit_added',1),('melodi-presto','author_database_snapshot_established',True),('oncoscore','implementation_status','upstream-adapter-validated')]:
   inv=json.loads(self.after[META[0]]);next(r for r in inv if r['id']==tool)[key]=value
   with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,META[0]:encoded(inv)})
  doc=json.loads(self.after[REVIEWS]);next(r for r in doc['reviews'] if r.get('paper_id')=='P169' and r['tool_id']=='oncoscore')['reported_settings']['threshold_comparator']='<'
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,{**self.after,REVIEWS:encoded(doc)})
if __name__=='__main__':unittest.main()
