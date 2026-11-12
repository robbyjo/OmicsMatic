# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial checks: reviewed routing cannot hide other source changes."""
from pathlib import Path
import json,tempfile,unittest
from dispatcher import reviewed_root,PATH,DESCRIPTOR,BEFORE,pin
from setup_deseq2_reference import ROOT

class DispatcherReviewTest(unittest.TestCase):
 def setUp(self):
  self.directory=tempfile.TemporaryDirectory(dir=ROOT/'build/literature-review/nfcore-runtime');self.root=Path(self.directory.name)
  self.path=self.root/PATH;self.path.parent.mkdir(parents=True);self.path.write_bytes((ROOT/PATH).read_bytes())
  self.descriptor=self.root/DESCRIPTOR;self.descriptor.parent.mkdir(parents=True);self.descriptor.write_bytes((ROOT/DESCRIPTOR).read_bytes())
 def tearDown(self):self.directory.cleanup()
 def test_exact_predecessor_reconstructs_and_scientific_paths_remain_live(self):
  scientific=self.root/'src/main/java/scientific.java';scientific.write_bytes(b'current scientific source\n')
  view,report=reviewed_root(self.root);self.assertEqual(BEFORE,pin((view/PATH).read_bytes())['sha256']);self.assertFalse(report['scientific_sources_overridden']);self.assertEqual(scientific.read_bytes(),(view/'src/main/java/scientific.java').read_bytes())
  scientific.write_bytes(b'changed scientific source\n');self.assertEqual(scientific.read_bytes(),(view/'src/main/java/scientific.java').read_bytes())
 def test_unrelated_command_change_cannot_be_hidden(self):
  self.path.write_bytes(self.path.read_bytes().replace(b'StudyCli.run(',b'UnknownCli.run(',1))
  with self.assertRaises(ValueError):reviewed_root(self.root)
 def test_rehashed_metadata_cannot_admit_a_different_route(self):
  changed=self.path.read_bytes().replace(b'"gseapy-native"',b'"other-native"',1);self.path.write_bytes(changed);d=json.loads(self.descriptor.read_bytes());d['paths'][0]['after_sha256']=pin(changed)['sha256'];self.descriptor.write_text(json.dumps(d))
  with self.assertRaises(ValueError):reviewed_root(self.root)
 def test_corrupt_inverse_and_scientific_path_spoof_refuse(self):
  original=self.descriptor.read_bytes();d=json.loads(original);d['paths'][0]['edits'][0]['after_chunk_sha256']='0'*64;self.descriptor.write_text(json.dumps(d))
  with self.assertRaises(ValueError):reviewed_root(self.root)
  d=json.loads(original);d['paths'][0]['path']='src/main/java/scientific.java';self.descriptor.write_text(json.dumps(d))
  with self.assertRaises(ValueError):reviewed_root(self.root)
if __name__=='__main__':unittest.main()
