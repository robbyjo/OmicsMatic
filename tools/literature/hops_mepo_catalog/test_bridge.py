# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial scope checks for the single-HOPS metadata predecessor."""
from pathlib import Path
import copy,json,sys,tempfile,unittest,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from hops_mepo_catalog.model import ROOT,META,INTEGRATION,metadata,transition,check_transition,encoded,rawpin
from hops_mepo_catalog.bridge import metadata_view,protected

class HopsCatalogBoundaryTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  archive=ROOT/INTEGRATION/'catalog-before.zip'
  if archive.is_file():
   with zipfile.ZipFile(archive) as z:cls.before={n:z.read(n) for n in META}
  else:cls.before={n:(ROOT/n).read_bytes() for n in META}
  cls.entry=metadata(ROOT);cls.after=transition(cls.before,cls.entry)
 def test_exact_transition_changes_only_hops_and_one_count(self):
  self.assertEqual(['hops'],check_transition(self.before,self.after,self.entry)['changed_tool_ids'])
  before=json.loads(self.before[META[0]]);after=json.loads(self.after[META[0]])
  self.assertEqual([r for r in before if r['id']!='hops'],[r for r in after if r['id']!='hops'])
  self.assertEqual(245,json.loads(self.after[META[1]])['implementation_status_counts']['missing'])
 def test_rehashed_unrelated_tool_change_is_rejected(self):
  after=dict(self.after);rows=json.loads(after[META[0]]);next(r for r in rows if r['id']=='maltextract')['name']='Changed unrelated method';after[META[0]]=encoded(rows)
  with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_extra_count_or_goal_completion_cannot_hide_in_summary(self):
  for field,value in [('goal_status','complete'),('papers',249)]:
   after=dict(self.after);summary=json.loads(after[META[1]]);summary[field]=value;after[META[1]]=encoded(summary)
   with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_paper_change_inside_rendered_page_is_rejected(self):
  after=dict(self.after);raw=after[META[4]];self.assertIn(b'Ancient genomes',raw);after[META[4]]=raw.replace(b'Ancient genomes',b'Changed genomes',1)
  with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_warning_or_document_changes_are_exact(self):
  for name in [META[2],META[3],META[5]]:
   after=dict(self.after);after[name]+=b'Unreviewed addition\n'
   with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_scientific_path_cannot_be_declared_a_predecessor(self):
  bad=dict(self.before);bad['src/main/java/org/jlinalg/cli/HopsMepoCli.java']=b'forged source\n'
  with self.assertRaises(ValueError):metadata_view(ROOT,bad)
 def test_scientific_paths_stay_live_and_primary_pins_stay_strict(self):
  parent=(ROOT/'build/literature-review/nfcore-runtime').resolve();directory=tempfile.TemporaryDirectory(dir=parent);root=Path(directory.name)
  try:
   source=root/'src/main/java/scientific.java';source.parent.mkdir(parents=True);source.write_bytes(b'original science\n');view=metadata_view(root,self.before)
   self.assertEqual(source.read_bytes(),(view/'src/main/java/scientific.java').read_bytes());source.write_bytes(b'changed science\n');self.assertEqual(source.read_bytes(),(view/'src/main/java/scientific.java').read_bytes())
   paper=root/'papers.json';paper.write_bytes(b'primary evidence\n');expected={'papers.json':rawpin(paper.read_bytes())};protected(root,expected);paper.write_bytes(b'changed primary\n')
   with self.assertRaises(ValueError):protected(root,expected)
  finally:
   assert root.resolve().is_relative_to(parent);directory.cleanup()
if __name__=='__main__':unittest.main()
