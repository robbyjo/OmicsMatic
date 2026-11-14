# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Protect exact source roles, complete populations, edition limits and one-family admission credit."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from cell2location_catalog.model import *
from cell2location_catalog.bridge import load_before,metadata_view
from cell2location_catalog.qualification import qualification

class Cell2locationCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT);cls.after=transition(ROOT,cls.before);cls.row=next(r for r in json.loads(cls.after[META[0]]) if r['id']=='cell2location')
 def reject(self,path,change):
  after=dict(self.after);value=json.loads(after[path]);change(value);after[path]=encoded(value)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_complete250_papers2671_observations_six_reviews_one_upstream_credit(self):
  self.assertEqual((250,2671,1162),(len(json.loads(self.after[PAPERS])),sum(len(p['tools']) for p in json.loads(self.after[PAPERS])),len(json.loads(self.after[REVIEWS])['reviews'])));r=check_transition(ROOT,self.before,self.after);self.assertEqual((110,263,6,1,0),tuple(r[k] for k in ['new_scoped_integrations','missing','added_reviews','new_scoped_integration_credit','native_family_credit_added']))
 def test_every_prior_review_unchanged(self):self.assertEqual(json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews'][:1156])
 def test_other431_families_unchanged(self):self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='cell2location'],[r for r in json.loads(self.after[META[0]]) if r['id']!='cell2location'])
 def test_other2665_observations_and_paper_identities_unchanged(self):
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS])):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'});self.assertEqual([r for r in a['tools'] if r['tool_id']!='cell2location'],[r for r in b['tools'] if r['tool_id']!='cell2location'])
 def test_all_six_original_extractions_retained(self):
  for paper in json.loads(self.after[PAPERS]):
   for r in paper['tools']:
    if r['tool_id']=='cell2location':self.assertIn('source_extraction_before_review',r)
 def test_methods_anchors_not_replaced_by_results_context(self):
  from cell2location_catalog.source import PRIMARY
  for r in reviews(ROOT):self.assertEqual(PRIMARY[r['paper_id']],r['evidence_paragraph_index']);self.assertEqual(len(EXPECTED[r['paper_id']]),len(r['supporting_evidence']))
 def test_reported_and_unreported_editions_preserved(self):
  rows={r['paper_id']:r for r in reviews(ROOT)};self.assertEqual('0.7a0',rows['P096']['version_reported_near_name']);self.assertEqual('0.1.3',rows['P100']['version_reported_near_name'])
  for n in ['P005','P008','P070','P099']:self.assertIsNone(rows[n]['version_reported_near_name'])
 def test_historical_edition_uncertainties_not_silently_repaired(self):
  text=self.row['paper_role_coverage']['P096']
  for word in ['0.7a0','log_normalised','np.arange(5,30)','5through29','250epochs','not establish']:self.assertIn(word,text)
  self.assertFalse(self.row['reported_0_7a0_execution_qualified']);self.assertEqual(['0.1.3'],self.row['selected_upstream_versions'])
 def test_source_workflow_not_relabelled_native_canonical_or_root_dispatch(self):
  self.assertEqual(PROGRAM,self.row['implementation_source']);self.assertEqual(pin(ROOT/PROGRAM)['sha256'],self.row['development_candidate_sha256']);self.assertIn('not a standalone JAR',self.row['implementation_scope'])
  for n in ['canonical_adapter_available','root_dispatch_available','whole_tool_qualified','whole_paper_reproduced']:self.assertFalse(self.row[n])
 def test_hyperparameters_not_reassigned_between_papers(self):
  ps=json.loads(self.after[PAPERS]);p100=next(t for p in ps if p['paper_id']=='P100' for t in p['tools'] if t['tool_id']=='cell2location');self.assertEqual((8,20,30000),tuple(p100['reported_settings'][k] for k in ['N_cells_per_location','detection_alpha','mapping_iterations']))
  self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P005' for t in p['tools'] if t['tool_id']=='cell2location')['reported_settings'].update(N_cells_per_location=8))
 def test_fake_native_and_extra_family_credit_refused(self):
  self.reject(META[0],lambda rs:next(r for r in rs if r['id']=='cell2location').update(implementation_status='native-subset-validated',canonical_adapter_available=True));self.reject(META[1],lambda d:d['implementation_status_counts'].update(missing=262))
 def test_new_or_rewritten_observation_refused(self):
  self.reject(PAPERS,lambda ps:ps[0]['tools'].append(copy.deepcopy(ps[0]['tools'][0])));self.reject(REVIEWS,lambda d:d['reviews'][0].update(use_summary='rewritten old review'))
 def test_legacy_edition_substitution_refused(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P096' for t in p['tools'] if t['tool_id']=='cell2location').update(version_reported_near_name='0.1.3'))
 def test_stale_source_and_unrelated_family_changes_refused(self):
  self.reject(META[0],lambda rs:next(r for r in rs if r['id']=='cell2location').update(development_candidate_sha256='0'*64));self.reject(META[0],lambda rs:next(r for r in rs if r['id']=='rstatix').update(root_dispatch_available=True))
 def test_prominent_current_recovery_and_full_goal_limits(self):
  page=self.after[META[4]].decode();self.assertLess(page.index('id="cell2location-required-actions"'),page.index('id="overview"'));self.assertEqual(1,page.count(NOTICE))
  for word in ['1,162','1,509','263 named families','110 scoped','113 including','0.7a0','np.arange(5,30)','SKIP3','FAIL2','unavailable, not zero','Cell Ranger ARC','not a standalone JAR','retry NEW paths']:self.assertIn(word,NOTICE)
 def test_scientific_overlays_forbidden_and_live_sources_preserved(self):
  for n in [PROGRAM,WORKFLOW+'/objects.zip','tools/literature/cell2location013/verify.py',CURATION]:
   bad=dict(self.before);bad[n]=b'forged'
   with self.assertRaises(ValueError):metadata_view(ROOT,bad)
  with tempfile.TemporaryDirectory(dir=ROOT/'build/literature-review/monocle-runtime') as name:
   root=Path(name);p=root/'science.py';p.write_bytes(b'original');view=metadata_view(root,self.before);self.assertEqual(b'original',(view/'science.py').read_bytes());p.write_bytes(b'changed');self.assertEqual(b'changed',(view/'science.py').read_bytes())
 def test_qualified_source_changed_before_import_refused(self):
  proof=json.loads((ROOT/WORKFLOW/'VALIDATION.json').read_bytes())
  with tempfile.TemporaryDirectory(dir=ROOT/'build/literature-review/monocle-runtime') as name:
   root=Path(name)
   for n,w in proof['sources'].items():
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
   p=root/WORKFLOW/'VALIDATION.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());(root/PROGRAM).write_bytes(b'changed isolated source; never execute\n')
   with self.assertRaisesRegex(ValueError,'Qualified original cell2location source/evidence differs'):qualification(root)
if __name__=='__main__':unittest.main()
