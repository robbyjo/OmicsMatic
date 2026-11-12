# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial checks for the exact six-file, one-MALT metadata transition."""
from pathlib import Path
import json,sys,tempfile,unittest,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from malt052_catalog.model import ROOT,META,INTEGRATION,metadata,transition,check_transition,encoded,rawpin
from malt052_catalog.bridge import metadata_view,protected
class MaltCatalogBoundaryTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  archive=ROOT/INTEGRATION/'catalog-before.zip'
  if archive.is_file():
   with zipfile.ZipFile(archive) as z:cls.before={n:z.read(n) for n in META}
  else:cls.before={n:(ROOT/n).read_bytes() for n in META}
  cls.entry=metadata(ROOT);cls.after=transition(cls.before,cls.entry)
 def test_exact_transition_preserves_every_other_tool(self):
  self.assertEqual(['malt'],check_transition(self.before,self.after,self.entry)['changed_tool_ids']);old=json.loads(self.before[META[0]]);new=json.loads(self.after[META[0]])
  self.assertEqual([r for r in old if r['id']!='malt'],[r for r in new if r['id']!='malt']);self.assertEqual(244,json.loads(self.after[META[1]])['implementation_status_counts']['missing'])
 def test_unrelated_hops_change_cannot_hide_in_rehashed_metadata(self):
  after=dict(self.after);rows=json.loads(after[META[0]]);next(r for r in rows if r['id']=='hops')['name']='Different unrelated tool';after[META[0]]=encoded(rows)
  with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_goal_or_population_claim_cannot_change_with_badge(self):
  for key,value in [('goal_status','complete'),('papers',249)]:
   after=dict(self.after);summary=json.loads(after[META[1]]);summary[key]=value;after[META[1]]=encoded(summary)
   with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_rendered_primary_evidence_cannot_be_rewritten(self):
  after=dict(self.after);raw=after[META[4]];self.assertIn(b'Ancient genomes',raw);after[META[4]]=raw.replace(b'Ancient genomes',b'Altered genomes',1)
  with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_warning_or_checkpoint_changes_must_be_exact(self):
  for name in [META[2],META[3],META[5]]:
   after=dict(self.after);after[name]+=b'Unreviewed addition\n'
   with self.assertRaises(ValueError):check_transition(self.before,after,self.entry)
 def test_scientific_paths_cannot_be_predecessors(self):
  before=dict(self.before);before['src/main/java/org/jlinalg/cli/Malt052WorkflowCli.java']=b'forged source\n'
  with self.assertRaises(ValueError):metadata_view(ROOT,before)
 def test_primary_and_scientific_paths_stay_live_and_strict(self):
  base=(ROOT/'build/literature-review/nfcore-runtime').resolve();directory=tempfile.TemporaryDirectory(dir=base);root=Path(directory.name)
  try:
   path=root/'src/main/java/scientific.java';path.parent.mkdir(parents=True);path.write_bytes(b'original\n');view=metadata_view(root,self.before);self.assertEqual(path.read_bytes(),(view/'src/main/java/scientific.java').read_bytes());path.write_bytes(b'changed\n');self.assertEqual(path.read_bytes(),(view/'src/main/java/scientific.java').read_bytes())
   primary=root/'papers.json';primary.write_bytes(b'primary\n');pins={'papers.json':rawpin(primary.read_bytes())};protected(root,pins);primary.write_bytes(b'changed primary\n')
   with self.assertRaises(ValueError):protected(root,pins)
  finally:
   assert root.resolve().is_relative_to(base);directory.cleanup()
if __name__=='__main__':unittest.main()
