# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check scientific scope boundaries, review provenance and exact historical metadata isolation."""
from pathlib import Path
import json,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from imagej_catalog.model import ROOT,META,PAPERS,REVIEWS,EVIDENCE,encoded,transition,check_transition,reviews
from imagej_catalog.bridge import load_before,metadata_view
from imagej_catalog.source import validate
from imagej_catalog.verify import qualification
class ImageJCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.before=load_before(ROOT);cls.after=transition(ROOT,cls.before)
 def reject(self,path,change):
  after=dict(self.after);value=json.loads(after[path]);change(value);after[path]=encoded(value)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_all_paper_identities_and_original_extraction_histories(self):
  changed=0
  for a,b in zip(json.loads(self.before[PAPERS]),json.loads(self.after[PAPERS]),strict=True):
   self.assertEqual({k:v for k,v in a.items() if k!='tools'},{k:v for k,v in b.items() if k!='tools'})
   for old,new in zip(a['tools'],b['tools'][:len(a['tools'])],strict=True):
    if old['tool_id']=='fiji_imagej' and a['paper_id']!='P040':self.assertEqual(old,new['source_extraction_before_review']);changed+=1
    else:self.assertEqual(old,new)
  self.assertEqual(50,changed)
 def test_previous_reviews_and_P040_not_duplicated(self):
  a=json.loads(self.before[REVIEWS]);b=json.loads(self.after[REVIEWS]);self.assertEqual(a['reviews'],b['reviews'][:1079]);self.assertEqual(1130,len(b['reviews']));self.assertEqual(1,sum(r['tool_id']=='fiji_imagej' and r['pmcid']=='PMC13645638' for r in b['reviews']))
 def test_unrelated_inventory_entries_preserved(self):
  b={r['id']:r for r in json.loads(self.after[META[0]])}
  for r in json.loads(self.before[META[0]]):
   if r['id']!='fiji_imagej':self.assertEqual(r,b[r['id']])
  self.assertEqual(430,len(b))
 def test_every_named_primary_paragraph_has_review_support(self):
  self.assertEqual(84,validate(json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes()),json.loads(self.before[PAPERS]),reviews(ROOT))['imagej_named_primary_paragraphs'])
 def test_reported_1_8_0_cannot_be_reinterpreted_as_Java(self):
  self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P006' for t in p['tools'] if t['tool_id']=='fiji_imagej').update(version_reported_near_name='Java1.8'))
 def test_no_claim_that_core_supplies_all_plugins(self):self.reject(META[0],lambda rs:next(r for r in rs if r['id']=='fiji_imagej').update(uncovered_roles=[],whole_tool_qualified=True))
 def test_native_histogram_not_double_credited(self):self.reject(META[1],lambda s:s['implementation_status_counts'].update(**{'native-subset-validated':20}))
 def test_no_inferred_bleach_paper_method(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P026' for t in p['tools'] if t['tool_id']=='bleach_correction').update(use_summary='The paper used histogram matching with baseline7'))
 def test_scientific_and_additional_metadata_overlays_forbidden(self):
  for name in ['src/main/java/org/jlinalg/cli/ImageJ154tWorkflowCli.java','tools/literature/imagej_catalog/source.py','unrelated.json']:
   before=dict(self.before);before[name]=b'forged\n'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
 def test_scientific_files_stay_live_through_metadata_view(self):
  base=ROOT/'build/literature-review/monocle-runtime'
  with tempfile.TemporaryDirectory(dir=base) as d:
   root=Path(d).resolve();self.assertTrue(root.is_relative_to(base.resolve()));p=root/'science.java';p.write_bytes(b'original\n');view=metadata_view(root,self.before);self.assertEqual(b'original\n',(view/'science.java').read_bytes());p.write_bytes(b'changed\n');self.assertEqual(b'changed\n',(view/'science.java').read_bytes())
 def test_qualified_original_source_corruption_is_rejected(self):
  base=ROOT/'build/literature-review/monocle-runtime';v=json.loads((ROOT/'docs/literature/imagej154t-workflow-development/VALIDATION.json').read_bytes())
  with tempfile.TemporaryDirectory(dir=base) as d:
   root=Path(d).resolve();self.assertTrue(root.is_relative_to(base.resolve()));p=root/'docs/literature/imagej154t-workflow-development/VALIDATION.json';p.parent.mkdir(parents=True);p.write_bytes(encoded(v))
   for n in v['sources']:
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
   name=next(n for n in v['sources'] if n.startswith('src/main/java/'));(root/name).write_bytes(b'Deliberately corrupted scientific source\n')
   with self.assertRaisesRegex(ValueError,'Qualified scientific source differs'):qualification(root)
 def test_current_notice_and_rendered_records_are_consistent(self):
  page=self.after[META[4]].decode('utf8');self.assertLess(page.index('id="imagej-catalog-required-actions"'),page.index('id="overview"'));self.assertIn('Histogram matching must not be used',page);after=dict(self.after);after[META[5]]+=b'Unsupported rendered change\n'
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
if __name__=='__main__':unittest.main()
