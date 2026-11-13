# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial checks for primary preservation, role coverage and live science."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor120_catalog.model import ROOT,META,PAPERS,REVIEWS,encoded,entry,transition,check_transition
from batchelor120_catalog.bridge import load_before,metadata_view

class BatchelorCatalogBoundaryTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT);cls.entry=entry(ROOT);cls.after=transition(cls.before,cls.entry)
 def test_all_previous_primary_observations_and_reviews_remain(self):
  old=json.loads(self.before[PAPERS]);new=json.loads(self.after[PAPERS])
  for a,b in zip(old,new,strict=True):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'})
   self.assertEqual(a['tools'],[t for t in b['tools'] if t['tool_id']!='batchelor'])
  self.assertEqual(json.loads(self.before[REVIEWS])['reviews'],json.loads(self.after[REVIEWS])['reviews'][:1052])
 def reject(self,path,mutate):
  after=dict(self.after);value=json.loads(after[path]);mutate(value);after[path]=encoded(value)
  with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_unrelated_prior_observation_cannot_be_rewritten(self):
  self.reject(PAPERS,lambda rows:rows[0]['tools'][0].update(use_summary='rewritten earlier analysis'))
 def test_prior_review_cannot_be_rewritten(self):
  self.reject(REVIEWS,lambda value:value['reviews'][0].update(use_summary='rewritten earlier review'))
 def test_paper_or_window_cannot_change(self):
  self.reject(PAPERS,lambda rows:rows[0].update(first_publication_date='2020-01-01'))
  self.reject(META[1],lambda value:value.update(window_extended=True))
 def test_uncovered_fastmnn_cannot_gain_badge(self):
  self.reject(META[0],lambda rows:next(t for t in rows if t['id']=='batchelor').update(implementation_status='upstream-adapter-validated'))
  self.reject(META[1],lambda value:value['implementation_status_counts'].update(missing=244))
 def test_unrelated_tool_cannot_change(self):
  self.reject(META[0],lambda rows:rows[0].update(name='changed unrelated tool'))
 def test_primary_anchor_or_support_cannot_change(self):
  self.reject(PAPERS,lambda rows:next(t for p in rows if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='batchelor').update(evidence_paragraph_sha256='0'*64))
  self.reject(REVIEWS,lambda value:value['reviews'][-2].update(supporting_evidence=[]))
 def test_rendered_records_and_warning_must_match(self):
  for name in META[2:6]:
   after=dict(self.after);after[name]+=b'unreviewed change\n'
   with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_scientific_or_extra_primary_overrides_are_forbidden(self):
  for name in ['src/main/java/org/jlinalg/cli/Monocle137WorkflowCli.java','other-papers.json']:
   before=dict(self.before);before[name]=b'forged\n'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
 def test_scientific_paths_remain_live(self):
  base=ROOT/'build/literature-review/monocle-runtime';base.mkdir(parents=True,exist_ok=True)
  with tempfile.TemporaryDirectory(dir=base) as directory:
   root=Path(directory);path=root/'scientific.java';path.write_bytes(b'original\n');view=metadata_view(root,self.before)
   self.assertEqual(b'original\n',(view/'scientific.java').read_bytes());path.write_bytes(b'changed\n');self.assertEqual(b'changed\n',(view/'scientific.java').read_bytes())
 def test_registry_preserves_observed_and_unobserved_definitions(self):
  from batchelor120_catalog.verify import load_catalog_registry
  rows=load_catalog_registry(ROOT);by={r['id']:r for r in rows};self.assertEqual(444,len(by));old=json.loads(self.before[META[0]])
  for r in old:self.assertEqual({k:r[k] for k in by[r['id']]},by[r['id']])
  observed={r['id'] for r in old};base=json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes());unobserved=[r for r in base if r['id'] not in observed];self.assertEqual(38,len(unobserved))
  for r in unobserved:self.assertEqual(r,by[r['id']])
 def test_census_digest_cannot_disagree_with_a_new_role(self):
  from batchelor120_catalog.model import EVIDENCE,additions
  from batchelor120_catalog.verify import validate_role_anchors
  value=json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes());uses,_=additions(self.before);validate_role_anchors(value,uses)
  for row in value['records']:
   if row['paper_id']=='P040':row['matches'][0]['paragraph_sha256']='0'*64
  with self.assertRaises(ValueError):validate_role_anchors(value,uses)

if __name__=='__main__':unittest.main()
