# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-correction guards: retain original populations and reject inflated coverage."""
import collections
import copy
import gzip
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from common_statistics.prism_catalog import ROOT,META,BASE,baseline,primary,transition,check,encoded,registry

class PrismSourceCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=baseline();cls.census=primary(Path('D:/projects/JLinAlg/build/literature-review/xml'),json.loads(cls.before[META[0]]));cls.after=transition(cls.before,cls.census)
 def reject(self,name,change):
  after=dict(self.after);value=json.loads(after[name]);change(value);after[name]=encoded(value)
  with self.assertRaises(ValueError):check(self.before,after,self.census)
 def test_exactly_one_observation_and_one_review_are_appended(self):
  a,b=json.loads(self.before[META[0]]),json.loads(self.after[META[0]])
  for old,new in zip(a,b,strict=True):
   self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'});self.assertEqual(old['tools'],new['tools'][:len(old['tools'])]);self.assertEqual(int(old['paper_id']=='P215'),len(new['tools'])-len(old['tools']))
  old=json.loads(self.before['tools/literature/reviewed-uses.json']);new=json.loads(self.after['tools/literature/reviewed-uses.json']);self.assertEqual(old['reviews'],new['reviews'][:-1]);self.assertEqual(1219,len(new['reviews']));self.assertEqual(2694,sum(len(p['tools']) for p in b))
 def test_all_five_pointers_have_one_anchor_and_four_supports_without_version_inference(self):
  p=next(p for p in json.loads(self.after[META[0]]) if p['paper_id']=='P215');t=p['tools'][-1];self.assertEqual('graphpad_prism',t['tool_id']);self.assertEqual(26,t['evidence_paragraph_index']);self.assertEqual([11,12,20,25],[s['paragraph_index'] for s in t['supporting_evidence']]);self.assertIsNone(t['version_reported_near_name']);self.assertIn('replicate-means',t['usage_context']);self.assertIn('Welch',t['implementation_scope_note'])
 def test_all_inventory_frequencies_match_actual_paper_tool_pairs(self):
  papers=json.loads(self.after[META[0]]);counts=collections.Counter(t['tool_id'] for p in papers for t in p['tools']);inventory=json.loads(self.after[META[1]])
  self.assertEqual(len(counts),len(inventory))
  for t in inventory:self.assertEqual(counts[t['id']],t['paper_count'],t['id']);self.assertEqual([p['paper_id'] for p in papers if any(u['tool_id']==t['id'] for u in p['tools'])],t['papers'],t['id'])
 def test_bare_name_alias_keeps_all_previous_definitions_and_rejects_substrings(self):
  from snpeff.model import registry as previous
  old,new=previous(),registry();self.assertEqual(485,len(new));self.assertEqual([r for r in old if r['id']!='graphpad_prism'],[r for r in new if r['id']!='graphpad_prism']);pattern=next(t['pattern'] for t in new if t['id']=='graphpad_prism')
  for text in ['Prism','using Prism.','GraphPad','GraphPad Prism 10','Prism 9']:self.assertIsNotNone(re.search(pattern,text,re.I),text)
  for text in ['PRISMA','Prismatic','myPrism','Prism_values']:self.assertIsNone(re.search(pattern,text,re.I),text)
 def test_previous_observations_reviews_and_unrelated_inventory_cannot_change(self):
  self.reject(META[0],lambda p:p[0]['tools'][0].update(use_summary='rewritten prior observation'))
  self.reject('tools/literature/reviewed-uses.json',lambda d:d['reviews'][0].update(use_summary='rewritten prior review'))
  self.reject(META[1],lambda ts:next(t for t in ts if t['id']=='ldak').update(implementation_status='upstream-adapter-validated'))
 def test_source_only_correction_cannot_award_a_badge_or_close_the_goal(self):
  self.reject(META[1],lambda ts:next(t for t in ts if t['id']=='graphpad_prism').update(implementation_status='native-subset-validated'))
  self.reject(META[2],lambda s:s.update(goal_status='complete'))
  self.reject(META[2],lambda s:s['implementation_status_counts'].update(missing=259))
 def test_changed_primary_xml_is_refused_before_any_canonical_write(self):
  paper=json.loads(self.before[META[0]])[0]
  with tempfile.TemporaryDirectory(dir=ROOT/'build') as folder:
   path=Path(folder)/(paper['pmcid']+'.xml.gz');path.write_bytes(gzip.compress(b'<article>changed synthetic negative control</article>',mtime=0))
   with self.assertRaisesRegex(ValueError,'Frozen primary XML differs'):primary(Path(folder),json.loads(self.before[META[0]]))
 def test_rendered_payload_matches_all_current_tables_and_correction_warning_is_first(self):
  page=self.after['site/literature-review.html'].decode();payload=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S);self.assertIsNotNone(payload);self.assertEqual(dict(papers=json.loads(self.after[META[0]]),tools=json.loads(self.after[META[1]]),summary=json.loads(self.after[META[2]])),json.loads(payload[1]));self.assertLess(page.index('id="prism-source-catalog-notice"'),page.index('id="overview"'));self.assertIn('one new paper-tool observation',page)

if __name__=='__main__':unittest.main()
