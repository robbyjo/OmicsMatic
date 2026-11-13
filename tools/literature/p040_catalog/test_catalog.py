# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial source/candidate limits and append-only primary-record checks."""
from pathlib import Path
import copy,json,re,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from p040_catalog.model import ROOT,DATA,EVIDENCE,META,PAPERS,REVIEWS,PHENO,encoded,transition,check_transition,assets
from p040_catalog.bridge import load_before,metadata_view
from p040_catalog.verify import load_catalog_registry,candidate_qualification
class SoftwareCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT) if (ROOT/EVIDENCE/'catalog-before.zip').exists() else {n:(ROOT/n).read_bytes() for n in META};cls.after=transition(ROOT,cls.before)
 def reject(self,path,mutate):
  after=dict(self.after);value=json.loads(after[path]);mutate(value);after[path]=encoded(value)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_all_previous_paper_identities_and_observations_unchanged(self):
  old=json.loads(self.before[PAPERS]);new=json.loads(self.after[PAPERS]);_,add,_=assets(ROOT)
  for a,b in zip(old,new,strict=True):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'});self.assertEqual(a['tools'],b['tools'][:len(a['tools'])]);self.assertEqual(add['paper_tool_additions'][a['paper_id']],b['tools'][len(a['tools']):])
 def test_previous_reviews_unchanged_and_only24_appended(self):
  a=json.loads(self.before[REVIEWS]);b=json.loads(self.after[REVIEWS]);self.assertEqual(a['reviews'],b['reviews'][:1055]);self.assertEqual(1079,len(b['reviews']));self.assertEqual({k:v for k,v in a.items() if k!='reviews'},{k:v for k,v in b.items() if k!='reviews'})
 def test_every_unrelated_inventory_row_unchanged(self):
  a=json.loads(self.before[META[0]]);b={r['id']:r for r in json.loads(self.after[META[0]])}
  for row in a:
   if row['id']!='rphenograph':self.assertEqual(row,b[row['id']])
 def test_old_observation_cannot_be_rewritten(self):self.reject(PAPERS,lambda ps:ps[0]['tools'][0].update(use_summary='rewritten previous use'))
 def test_old_review_cannot_be_rewritten(self):self.reject(REVIEWS,lambda x:x['reviews'][0].update(use_summary='rewritten previous review'))
 def test_new_source_anchor_cannot_be_changed(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='qupath').update(evidence_paragraph_sha256='0'*64))
 def test_imagej_literal_suffix_is_retained(self):
  row=next(t for p in json.loads(self.after[PAPERS]) if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='fiji_imagej');self.assertEqual('1.54t',row['version_reported_near_name']);self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='fiji_imagej').update(version_reported_near_name='1.54'))
 def test_no_badge_for_name_census_or_candidate_version(self):
  for id in ['cytomapper','rphenograph','fiji_imagej']:
   self.reject(META[0],lambda rs,id=id:next(r for r in rs if r['id']==id).update(implementation_status='upstream-adapter-validated'))
 def test_coverage_counts_and_goal_cannot_be_inflated(self):
  self.reject(META[1],lambda x:x['implementation_status_counts'].update(**{'native-subset-validated':20}));self.reject(META[1],lambda x:x.update(goal_status='complete'));self.reject(META[1],lambda x:x.update(window_extended=True))
 def test_0_99_candidate_cannot_replace_reported_0_1_1(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='rphenograph').update(version_reported_near_name='0.99.1'))
 def test_deepcell_company_is_not_a_software_use(self):
  c,a,defs=assets(ROOT);r=next(r for r in defs if r['id']=='deepcell_mesmer');self.assertFalse(re.search(r['pattern'],'DeepCell, Inc.',re.I));self.assertTrue(re.search(r['pattern'],'DeepCell’s Mesmer (v.0.4.1)',re.I));self.assertFalse(any(t['tool_id']=='deepcell_mesmer' for t in a['paper_tool_additions']['P250']))
 def test_author_inventory_does_not_infer_specific_functions(self):
  _,a,_=assets(ROOT)
  for id in ['facsdiva','graphpad_prism']:
   row=next(t for t in a['paper_tool_additions']['P082'] if t['tool_id']==id);self.assertEqual(64,row['evidence_paragraph_index']);self.assertIn('does not establish',row['use_summary']);self.assertEqual([],row['supporting_evidence'])
 def test_cytomapper_gating_and_plotpixels_both_retained(self):
  _,a,_=assets(ROOT);row=next(t for t in a['paper_tool_additions']['P040'] if t['tool_id']=='cytomapper');self.assertEqual(29,row['evidence_paragraph_index']);self.assertEqual([31],[s['paragraph_index'] for s in row['supporting_evidence']]);self.assertIn('gating',row['use_summary']);self.assertIn('plotPixels',row['use_summary'])
 def test_rendered_data_and_warning_bytes_cannot_drift(self):
  for n in META[2:6]:
   after=dict(self.after);after[n]+=b'unreviewed modification\n'
   with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_current_notice_precedes_overview_and_old_warnings(self):
  page=self.after[META[4]].decode();warnings=self.after[META[5]].decode();self.assertLess(page.index('id="p040-software-required-actions"'),page.index('id="overview"'));self.assertLess(warnings.index('id="p040-software-required-actions"'),warnings.index('id="scdrs-required-actions"'));self.assertIn('Current selected workflow',page);self.assertIn('Historical source-review implementation note',page)
 def test_no_scientific_or_additional_metadata_overlay(self):
  for n in ['src/main/java/org/jlinalg/cli/Rphenograph099WorkflowCli.java','unrelated-metadata.json']:
   d=dict(self.before);d[n]=b'forged\n'
   with self.assertRaises(ValueError):metadata_view(ROOT,d)
 def test_scientific_files_remain_live_through_metadata_view(self):
  base=ROOT/'build/literature-review/monocle-runtime'
  with tempfile.TemporaryDirectory(dir=base) as d:
   root=Path(d);p=root/'science.java';p.write_bytes(b'original\n');view=metadata_view(root,self.before);self.assertEqual(b'original\n',(view/'science.java').read_bytes());p.write_bytes(b'changed\n');self.assertEqual(b'changed\n',(view/'science.java').read_bytes())
 def test_candidate_source_corruption_is_rejected(self):
  v=json.loads((ROOT/PHENO/'VALIDATION.json').read_bytes());name=next(n for n in v['sources'] if n.startswith('src/main/java/'))
  with tempfile.TemporaryDirectory(dir=ROOT/'build/literature-review/monocle-runtime') as d:
   root=Path(d);p=root/PHENO/'VALIDATION.json';p.parent.mkdir(parents=True);p.write_bytes((ROOT/PHENO/'VALIDATION.json').read_bytes());p=root/PHENO/'QUALIFICATION.json';p.write_bytes((ROOT/PHENO/'QUALIFICATION.json').read_bytes())
   for n in v['sources']:
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
   (root/name).write_bytes(b'Deliberately changed scientific source\n')
   with self.assertRaisesRegex(ValueError,'Qualified candidate source differs'):candidate_qualification(root)
if __name__=='__main__':unittest.main()
