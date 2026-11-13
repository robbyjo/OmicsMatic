# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial admission checks for primary preservation and live scientific evidence."""
from pathlib import Path
import copy,json,shutil,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor129_catalog.model import ROOT,DATA,EVIDENCE,META,PAPERS,REVIEWS,WORKFLOW,encoded,rawpin,entries,transition,check_transition
from batchelor129_catalog.bridge import load_before,metadata_view
from batchelor129_catalog.verify import fastmnn_qualification,validate_phenograph
class Batchelor129CatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  if (ROOT/EVIDENCE/'catalog-before.zip').exists():cls.before=load_before(ROOT)
  else:
   cls.before={n:(ROOT/n).read_bytes() for n in META};expected=json.loads((ROOT/DATA/'batchelor-catalog-validation.json').read_bytes())['after_files']
   if any(rawpin(cls.before[n])!=expected[n] for n in META):raise ValueError('Not the exact admitted predecessor')
  cls.entries=entries(ROOT,cls.before);cls.after=transition(cls.before,cls.entries)
 def reject(self,path,mutate):
  after=dict(self.after);v=json.loads(after[path]);mutate(v);after[path]=encoded(v)
  with self.assertRaises(ValueError):check_transition(self.before,after,self.entries)
 def test_every_previous_observation_review_and_paper_identity_remains(self):
  a=json.loads(self.before[PAPERS]);b=json.loads(self.after[PAPERS])
  for old,new in zip(a,b,strict=True):
   self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'})
   self.assertEqual(old['tools'],[t for t in new['tools'] if t['tool_id']!='rphenograph'])
  old=json.loads(self.before[REVIEWS]);new=json.loads(self.after[REVIEWS]);self.assertEqual(old['reviews'],new['reviews'][:1054]);self.assertEqual({k:v for k,v in old.items() if k!='reviews'},{k:v for k,v in new.items() if k!='reviews'})
 def test_every_unrelated_tool_is_unchanged(self):
  a=json.loads(self.before[META[0]]);b={t['id']:t for t in json.loads(self.after[META[0]])}
  for row in a:
   if row['id']!='batchelor':self.assertEqual(row,b[row['id']])
 def test_prior_observation_cannot_be_rewritten(self):self.reject(PAPERS,lambda p:p[0]['tools'][0].update(use_summary='rewritten old use'))
 def test_prior_review_cannot_be_rewritten(self):self.reject(REVIEWS,lambda d:d['reviews'][-2].update(implementation_scope_note='rewritten source history'))
 def test_reported_rphenograph_version_cannot_be_replaced(self):self.reject(PAPERS,lambda p:next(t for x in p if x['paper_id']=='P040' for t in x['tools'] if t['tool_id']=='rphenograph').update(version_reported_near_name='0.99.1'))
 def test_new_primary_anchor_cannot_change(self):self.reject(REVIEWS,lambda d:d['reviews'][-1].update(evidence_paragraph_sha256='0'*64))
 def test_missing_rphenograph_cannot_gain_badge(self):self.reject(META[0],lambda rows:next(t for t in rows if t['id']=='rphenograph').update(implementation_status='upstream-adapter-validated'))
 def test_no_native_or_second_badge_can_be_added(self):
  self.reject(META[1],lambda d:d['implementation_status_counts'].update(**{'native-subset-validated':20}))
  self.reject(META[1],lambda d:d['implementation_status_counts'].update(missing=244))
 def test_paper_window_and_goal_cannot_change(self):
  self.reject(META[1],lambda d:d.update(window_extended=True))
  self.reject(META[1],lambda d:d.update(goal_status='complete'))
 def test_unrelated_tool_cannot_change(self):self.reject(META[0],lambda rows:rows[0].update(name='changed unrelated tool'))
 def test_paper_patch_or_whole_tool_credit_cannot_be_claimed(self):
  self.reject(META[0],lambda rows:next(t for t in rows if t['id']=='batchelor').update(whole_tool_qualified=True))
  self.reject(META[0],lambda rows:next(t for t in rows if t['id']=='batchelor').update(paper_exact_unknown_settings_available=True))
 def test_historical_note_is_labeled_and_current_warning_is_prominent(self):
  page=self.after[META[4]].decode();self.assertIn('Historical source-review implementation note (retained)',page);self.assertIn('Current selected workflow:',page);self.assertLess(page.index('id="batchelor129-required-actions"'),page.index('id="overview"'))
  warnings=self.after[META[5]].decode();self.assertLess(warnings.index('id="batchelor129-required-actions"'),warnings.index('id="scdrs-required-actions"'))
 def test_rendered_or_warning_bytes_cannot_drift(self):
  for name in META[2:6]:
   d=dict(self.after);d[name]+=b'unreviewed modification\n'
   with self.assertRaises(ValueError):check_transition(self.before,d,self.entries)
 def test_scientific_or_extra_primary_overlays_are_forbidden(self):
  for name in ['src/main/java/org/jlinalg/cli/FastMnn129WorkflowCli.java','other-papers.json']:
   before=dict(self.before);before[name]=b'forged\n'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
 def test_scientific_paths_remain_live(self):
  base=ROOT/'build/literature-review/monocle-runtime';base.mkdir(parents=True,exist_ok=True)
  with tempfile.TemporaryDirectory(dir=base) as d:
   root=Path(d);p=root/'scientific.java';p.write_bytes(b'original\n');view=metadata_view(root,self.before);self.assertEqual(b'original\n',(view/'scientific.java').read_bytes());p.write_bytes(b'changed\n');self.assertEqual(b'changed\n',(view/'scientific.java').read_bytes())
 def test_changed_scientific_source_is_rejected_by_qualification(self):
  v=json.loads((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());name=next(n for n in v['sources'] if n.startswith('src/main/java/'))
  with tempfile.TemporaryDirectory(dir=ROOT/'build/literature-review/monocle-runtime') as d:
   root=Path(d);target=root/WORKFLOW/'VALIDATION.json';target.parent.mkdir(parents=True);shutil.copyfile(ROOT/WORKFLOW/'VALIDATION.json',target)
   for n in v['sources']:
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/n,p)
   p=root/name;p.write_bytes(p.read_bytes()+b'\n// Deliberately changed scientific source\n')
   with self.assertRaisesRegex(ValueError,'Qualified FastMNN source differs'):fastmnn_qualification(root)
 def test_rphenograph_source_version_scope_rejects_bad_anchor(self):
  from batchelor129_catalog.model import additions
  use,_=additions(self.before);use['evidence_paragraph_sha256']='0'*64
  with self.assertRaises(ValueError):validate_phenograph(ROOT,json.loads(self.before[PAPERS]),use)
if __name__=='__main__':unittest.main()
