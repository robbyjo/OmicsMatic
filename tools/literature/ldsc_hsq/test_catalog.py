# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Cumulative catalog boundary tests; numerical methods retain their separate qualification."""
import collections,copy,gzip,json,re,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from ldsc_hsq import catalog as m

class LdscCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=m.baseline();cls.census=m.primary(Path('D:/projects/JLinAlg/build/literature-review/xml'),json.loads(cls.before[m.META[0]]));cls.qualification=copy.deepcopy(cls.census['native_qualification']);cls.after=m.transition(cls.before,cls.census)
 def setUp(self):
  self.binding=patch.object(m,'native_qualification',return_value=copy.deepcopy(self.qualification));self.binding.start();self.addCleanup(self.binding.stop)
 def reject(self,name,mutate):
  after=dict(self.after);value=json.loads(after[name]);mutate(value);after[name]=m.encoded(value)
  with self.assertRaises(ValueError):m.check(self.before,after,self.census)
 def test_exactly_two_added_one_removed_and_all_unrelated_observations_preserved(self):
  a,b=json.loads(self.before[m.META[0]]),json.loads(self.after[m.META[0]])
  for old,new in zip(a,b,strict=True):
   self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'});self.assertEqual([t for t in old['tools'] if t['tool_id']!='ldsc'],[t for t in new['tools'] if t['tool_id']!='ldsc'])
   self.assertEqual(int(old['paper_id'] in ['P019','P092'])-int(old['paper_id']=='P151'),len(new['tools'])-len(old['tools']))
  self.assertEqual(2695,sum(len(p['tools']) for p in b));self.assertEqual(42,sum(any(t['tool_id']=='ldsc' for t in p['tools']) for p in b))
 def test_42_reviews_append_without_rewriting_1219_prior_reviews(self):
  old=json.loads(self.before['tools/literature/reviewed-uses.json']);new=json.loads(self.after['tools/literature/reviewed-uses.json']);self.assertEqual(old['reviews'],new['reviews'][:1219]);self.assertEqual(1261,len(new['reviews']));self.assertEqual(42,sum(r['tool_id']=='ldsc' for r in new['reviews']));self.assertEqual({k:v for k,v in old.items() if k!='reviews'},{k:v for k,v in new.items() if k!='reviews'})
 def test_all_inventory_frequencies_and_one_native_family_credit_are_derived(self):
  papers=json.loads(self.after[m.META[0]]);inventory=json.loads(self.after[m.META[1]]);counts=collections.Counter(t['tool_id'] for p in papers for t in p['tools']);self.assertEqual(446,len(counts))
  for row in inventory:self.assertEqual(counts[row['id']],row['paper_count']);self.assertEqual([p['paper_id'] for p in papers if any(t['tool_id']==row['id'] for t in p['tools'])],row['papers'])
  states=collections.Counter(t['implementation_status'] for t in inventory);self.assertEqual(24,states['native-subset-validated']);self.assertEqual(104,states['upstream-adapter-validated']);self.assertEqual(46,states['method-overlap']);self.assertEqual(260,states['missing']);self.assertEqual(128,states['native-subset-validated']+states['upstream-adapter-validated'])
 def test_source_negative_and_resource_roles_cannot_be_promoted_to_execution(self):
  roles={r['paper_id']:r for r in self.census['roles']};self.assertNotIn('P151',roles);self.assertNotIn('P177',roles)
  self.assertEqual('reused-estimates',roles['P220']['source_attribution']);self.assertEqual('annotation-resource',roles['P245']['source_attribution'])
  for paper in ['P220','P245']:
   self.assertEqual([],roles[paper]['native_unpartitioned_profiles_applicable']);bad=copy.deepcopy(self.census);role=next(r for r in bad['roles'] if r['paper_id']==paper);role['new_observation']['native_unpartitioned_profiles_applicable']=['rg']
   with self.assertRaises(ValueError):m.transition(self.before,bad)
  self.assertEqual('wrapper',roles['P123']['source_attribution']);self.assertEqual([],roles['P123']['native_unpartitioned_profiles_applicable'])
 def test_literal_edition_and_annotation_wrapper_versions_remain_distinct(self):
  roles={r['paper_id']:r for r in self.census['roles']};self.assertEqual('1.0.11',roles['P115']['reported_ldsc_edition']);self.assertEqual('1.0.1',roles['P203']['reported_ldsc_edition']);self.assertIsNone(roles['P092']['reported_ldsc_edition']);self.assertIsNone(roles['P140']['reported_ldsc_edition']);self.assertEqual('1.0.1',roles['P023']['reported_ldsc_edition'])
 def test_registry_preserves_all_other_484_definitions_and_handles_new_spellings(self):
  from common_statistics.prism_catalog import registry as previous
  old,new=previous(),m.registry();self.assertEqual(485,len(new));self.assertEqual([r for r in old if r['id']!='ldsc'],[r for r in new if r['id']!='ldsc'])
  pattern=next(r['pattern'] for r in new if r['id']=='ldsc')
  for s in ['LDSC','sLDSC','S-LDSC','linkage disequilibrium score regression','LD-score regression','ldsc.py --rg']:self.assertIsNotNone(re.search(pattern,s,re.I))
  for s in ['myLDSC','LDSC_extra','ldscoreboard']:self.assertIsNone(re.search(pattern,s,re.I))
 def test_unrelated_families_reviews_window_and_goal_cannot_change(self):
  self.reject(m.META[1],lambda rows:next(r for r in rows if r['id']=='ldak').update(implementation_status='upstream-adapter-validated'));self.reject('tools/literature/reviewed-uses.json',lambda rows:rows['reviews'][0].update(use_summary='unrelated rewrite'));self.reject(m.META[2],lambda d:d.update(goal_status='complete'));self.reject(m.META[2],lambda d:d.update(window_extended=True));self.reject(m.META[2],lambda d:d['implementation_status_counts'].update(missing=259))
 def test_complete_embedded_payload_and_current_warning_precede_history(self):
  page=self.after['site/literature-review.html'].decode();payload=re.search(r'<script id="review-data" type="application/json">(.*?)</script>',page,re.S);self.assertIsNotNone(payload);self.assertEqual(dict(papers=json.loads(self.after[m.META[0]]),tools=json.loads(self.after[m.META[1]]),summary=json.loads(self.after[m.META[2]])),json.loads(payload[1]));self.assertLess(page.index('id="ldsc-catalog-notice"'),page.index('id="overview"'));self.assertLess(page.index('id="ldsc-catalog-notice"'),page.index('id="prism-source-catalog-notice"'))
 def test_changed_primary_xml_refuses_before_any_canonical_mutation(self):
  paper=json.loads(self.before[m.META[0]])[0]
  with tempfile.TemporaryDirectory(dir=m.ROOT/'build') as folder:
   (Path(folder)/(paper['pmcid']+'.xml.gz')).write_bytes(gzip.compress(b'<article>changed negative control</article>',mtime=0))
   with self.assertRaisesRegex(ValueError,'Frozen primary XML differs'):m.primary(Path(folder),json.loads(self.before[m.META[0]]))
 def test_changed_scientific_code_cannot_borrow_the_prior_qualification(self):
  self.binding.stop();real=m.pin;path=m.ROOT/'src/main/java/org/jlinalg/xwas/LdscGeneticCorrelation.java'
  def altered(p):return dict(bytes=1,sha256='0'*64) if Path(p)==path else real(p)
  with patch.object(m,'pin',side_effect=altered):
   with self.assertRaisesRegex(ValueError,'Qualified scientific source/fixture changed'):m.native_qualification()
if __name__=='__main__':unittest.main()
