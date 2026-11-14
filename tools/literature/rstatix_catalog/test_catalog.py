# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Protect literal paper roles, prior observations, single-family credit and live scientific sources."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from rstatix_catalog.model import ROOT,META,PAPERS,REVIEWS,EVIDENCE,CURATION,NOTICE,encoded,transition,check_transition,reviews
from rstatix_catalog.source import EXPECTED,validate
from rstatix_catalog.bridge import load_before,metadata_view
from rstatix_catalog.qualification import WORKFLOW,qualification

class RstatixCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT);cls.after=transition(ROOT,cls.before);cls.papers=json.loads(cls.before[PAPERS]);cls.census=json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes())
 @staticmethod
 def rstatix(rows):return next(t for t in rows if t['id']=='rstatix')
 def reject(self,path,change):
  after=dict(self.after);value=json.loads(after[path]);change(value);after[path]=encoded(value)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_431_other_family_records_preserved(self):
  self.assertEqual([r for r in json.loads(self.before[META[0]]) if r['id']!='rstatix'],[r for r in json.loads(self.after[META[0]]) if r['id']!='rstatix'])
 def test_all_paper_identities_2666_other_observations_and_five_exact_old_records_retained(self):
  changed=[];unchanged=0
  for old,new in zip(self.papers,json.loads(self.after[PAPERS]),strict=True):
   self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'})
   for a,b in zip(old['tools'],new['tools'],strict=True):
    if old['paper_id'] in EXPECTED and old['paper_id']!='P040' and a['tool_id']=='rstatix':self.assertEqual(a,b['source_extraction_before_review']);changed.append(old['paper_id'])
    else:self.assertEqual(a,b);unchanged+=1
  self.assertEqual([p for p in EXPECTED if p!='P040'],changed);self.assertEqual(2666,unchanged)
 def test_1143_old_reviews_preserved_and_exactly_five_appended(self):
  old=json.loads(self.before[REVIEWS]);new=json.loads(self.after[REVIEWS]);self.assertEqual(old['reviews'],new['reviews'][:1143]);self.assertEqual(1148,len(new['reviews']));self.assertEqual(1148,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
 def test_existing_P040_observation_and_review_unchanged(self):
  for path,select in [(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='rstatix')),(REVIEWS,lambda d:next(r for r in d['reviews'] if r['pmcid']=='PMC13645638' and r['tool_id']=='rstatix'))]:
   self.assertEqual(select(json.loads(self.before[path])),select(json.loads(self.after[path])))
 def test_exactly_one_upstream_credit_without_second_native_family_credit(self):
  row=self.rstatix(json.loads(self.after[META[0]]));self.assertEqual(('upstream-adapter-validated',1,0,6),(row['implementation_status'],row['new_scoped_integration_credit'],row['native_family_credit_added'],row['source_roles_reviewed']))
  c=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((20,88,265),(c['native-subset-validated'],c['upstream-adapter-validated'],c['missing']))
 def test_no_new_families_or_observations(self):
  self.assertEqual(432,len(json.loads(self.after[META[0]])));self.assertEqual(2671,sum(len(p['tools']) for p in json.loads(self.after[PAPERS])));self.assertEqual(5,check_transition(ROOT,self.before,self.after)['added_reviews'])
 def test_source_entrypoint_requires_original_R_and_native_Levene_is_separate(self):
  row=self.rstatix(json.loads(self.after[META[0]]));self.assertTrue(row['development_candidate'].endswith('/run.py'));self.assertIn('requires-qualified-original-R-provider',row['development_candidate_kind']);self.assertIn('not a standalone JAR',row['implementation_scope']);self.assertTrue(row['native_candidate_component'].endswith('/LeveneTest.java'));self.assertFalse(row['root_dispatch_available'])
 def test_reported_editions_and_unreported_P241_remain_literal(self):
  by={p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='rstatix') for p in json.loads(self.after[PAPERS]) if p['paper_id'] in EXPECTED}
  self.assertEqual(['0.7.2']*5+['unreported'],[by[p]['version_reported_near_name'] for p in EXPECTED]);self.assertIsNone(by['P098']['source_extraction_before_review']['version_reported_near_name'])
 def test_current_visible_roles_include_each_required_gap(self):
  roles=self.rstatix(json.loads(self.after[META[0]]))['paper_role_coverage']
  required={'P032':['unknown','Holm/BH','GSVA'],'P040':['two-sided','variance assumption','pairing','unchanged'],
   'P054':['wilcoxon_test','wilcox_test','SKIP3','R 4.2.3 to 4.4.0','R 4.5.3/car 3.1-5','group-versus-all','native Java Levene'],
   'P087':['wilcox_test/BH','exact/tie/correction','stats Spearman','OLR1'],
   'P098':['earlier extractor','t_test/Holm','VST','variance assumption','DESeq2'],
   'P241':['unreported','not a recovered author runtime','113','not another execution']}
  for pid,words in required.items():
   for word in words:self.assertIn(word,roles[pid],pid)
   self.assertIn('private Linux/WSL R 4.5.3 provider',roles[pid])
 def test_no_whole_tool_or_six_paper_reproduction_claim(self):
  self.reject(META[0],lambda ts:self.rstatix(ts).update(whole_tool_qualified=True,whole_paper_reproduced=True,new_scoped_integration_credit=6))
 def test_native_Levene_cannot_supply_general_R_formulas_and_all_tests(self):
  self.reject(META[0],lambda ts:self.rstatix(ts).update(implementation_status='native-subset-validated',implementation_scope='All R formulas, grouped custom centers, t/Wilcoxon/ANOVA are native Java'))
 def test_literal_missing_export_cannot_be_silently_repaired(self):
  self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P054' for t in p['tools'] if t['tool_id']=='rstatix').update(implementation_scope_note='The authors actually called wilcox_test; typo repaired automatically'))
 def test_unknown_P032_adjustment_family_cannot_be_inferred(self):
  self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P032' for t in p['tools'] if t['tool_id']=='rstatix').update(use_summary='Used Welch t-tests and Holm across all activation genes'))
 def test_P241_resource_link_cannot_gain_edition_or_another_execution(self):
  self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P241' for t in p['tools'] if t['tool_id']=='rstatix').update(version_reported_near_name='0.7.2'))
  rows=copy.deepcopy(reviews(ROOT));rows[-1]['named_anchor_classification']['113']='analysis-role'
  with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
 def test_all_twelve_anchors_cover_six_uses_with_eleven_analysis_anchors(self):
  result=validate(self.census,self.papers,reviews(ROOT));self.assertEqual((6,12,11,1),tuple(result[k] for k in ['reviewed_paper_family_uses','named_primary_paragraphs','analysis_anchors','resource_link_anchors']))
 def test_omitted_duplicate_and_stale_supports_rejected(self):
  for mutation in ['omit','duplicate','stale']:
   rows=copy.deepcopy(reviews(ROOT));support=rows[2]['supporting_evidence']
   if mutation=='omit':support.pop()
   elif mutation=='duplicate':support[-1]=copy.deepcopy(support[0])
   else:support[0]['paragraph_sha256']='0'*64
   with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
 def test_foreign_anchor_and_duplicate_census_rejected(self):
  for mutation in ['identity','duplicate']:
   census=copy.deepcopy(self.census)
   if mutation=='identity':census['matches'][0]['doi']='10.0000/wrong'
   else:census['matches'][-1]=copy.deepcopy(census['matches'][0])
   with self.assertRaises(ValueError):validate(census,self.papers,reviews(ROOT))
 def test_scientific_and_extra_metadata_overlays_forbidden_and_science_live(self):
  for n in ['src/main/java/org/jlinalg/stats/LeveneTest.java','tools/literature/rstatix072_workflow/run.py','tools/literature/rstatix072/verify.py',CURATION,'other.json']:
   before=dict(self.before);before[n]=b'forged'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
  base=ROOT/'build/literature-review/monocle-runtime'
  with tempfile.TemporaryDirectory(dir=base) as directory:
   root=Path(directory).resolve();self.assertTrue(root.is_relative_to(base.resolve()));source=root/'science.R';source.write_bytes(b'original');view=metadata_view(root,self.before);self.assertEqual(b'original',(view/'science.R').read_bytes());source.write_bytes(b'changed');self.assertEqual(b'changed',(view/'science.R').read_bytes())
 def test_changed_original_workflow_source_rejected_before_verifier_import(self):
  proof=json.loads((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());base=ROOT/'build/literature-review/monocle-runtime'
  with tempfile.TemporaryDirectory(dir=base) as directory:
   root=Path(directory).resolve();p=root/WORKFLOW/'VALIDATION.json';p.parent.mkdir(parents=True);p.write_bytes(encoded(proof))
   for n in proof['sources']:
    p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
   (root/'tools/literature/rstatix072_workflow/run.py').write_bytes(b'Deliberately changed workflow source\n')
   with self.assertRaisesRegex(ValueError,'Qualified original-workflow scientific source differs'):qualification(root)
 def test_registry_stays_470_with_only_rstatix_coverage_fields_updated(self):
  from rstatix_catalog.verify import load_catalog_registry
  from qupath_catalog.verify import load_catalog_registry as previous
  old=previous(metadata_view(ROOT,self.before));new=load_catalog_registry(ROOT);self.assertEqual(470,len(new))
  self.assertEqual([r for r in old if r['id']!='rstatix'],[r for r in new if r['id']!='rstatix']);self.assertEqual('upstream-adapter-validated',next(r for r in new if r['id']=='rstatix')['current_coverage'])
 def test_prominent_current_notice_and_changed_warning_rejected(self):
  page=self.after[META[4]].decode('utf8');self.assertEqual(1,page.count(NOTICE));self.assertLess(page.index('id="rstatix-required-actions"'),page.index('id="overview"'))
  for word in ['1,148','1,523','265 named families remain missing','SKIP3','FAIL2','wilcoxon_test','not a standalone JAR','Cell Ranger ARC','retry NEW paths']:self.assertIn(word,NOTICE)
  after=dict(self.after);after[META[5]]+=b'Unsupported changed warning\n'
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)

if __name__=='__main__':unittest.main()
