# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Protect exact source roles, unrelated records, edition boundaries and immutable scientific evidence."""
from pathlib import Path
import copy,json,sys,tempfile,unittest
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from ggpubr_catalog.model import ROOT,META,PAPERS,REVIEWS,EVIDENCE,CURATION,NOTICE,encoded,transition,check_transition,reviews
from ggpubr_catalog.source import EXPECTED,VERSIONS,validate
from ggpubr_catalog.bridge import load_before,metadata_view
from ggpubr_catalog.qualification import WORKFLOW,qualification
class GgpubrCatalogTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.before=load_before(ROOT);cls.after=transition(ROOT,cls.before);cls.papers=json.loads(cls.before[PAPERS]);cls.census=json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes())
 @staticmethod
 def tool(rows):return next(t for t in rows if t['id']=='ggpubr')
 def reject(self,path,change):
  after=dict(self.after);value=json.loads(after[path]);change(value);after[path]=encoded(value)
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
 def test_431_other_family_records_preserved(self):
  self.assertEqual([t for t in json.loads(self.before[META[0]]) if t['id']!='ggpubr'],[t for t in json.loads(self.after[META[0]]) if t['id']!='ggpubr'])
 def test_all_paper_identities_2663_other_observations_and_eight_old_records_preserved(self):
  changed=[];unchanged=0
  for old,new in zip(self.papers,json.loads(self.after[PAPERS]),strict=True):
   self.assertEqual({k:v for k,v in old.items() if k!='tools'},{k:v for k,v in new.items() if k!='tools'})
   for a,b in zip(old['tools'],new['tools'],strict=True):
    if old['paper_id'] in EXPECTED and old['paper_id']!='P040' and a['tool_id']=='ggpubr':self.assertEqual(a,b['source_extraction_before_review']);changed.append(old['paper_id'])
    else:self.assertEqual(a,b);unchanged+=1
  self.assertEqual([p for p in EXPECTED if p!='P040'],changed);self.assertEqual(2663,unchanged)
 def test_1148_old_reviews_preserved_and_exactly_eight_appended(self):
  old=json.loads(self.before[REVIEWS]);new=json.loads(self.after[REVIEWS]);self.assertEqual(old['reviews'],new['reviews'][:1148]);self.assertEqual(1156,len(new['reviews']));self.assertEqual(1156,len({(r['pmcid'],r['tool_id']) for r in new['reviews']}))
 def test_existing_P040_observation_and_review_unchanged(self):
  for path,select in [(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P040' for t in p['tools'] if t['tool_id']=='ggpubr')),(REVIEWS,lambda d:next(r for r in d['reviews'] if r['pmcid']=='PMC13645638' and r['tool_id']=='ggpubr'))]:self.assertEqual(select(json.loads(self.before[path])),select(json.loads(self.after[path])))
 def test_exactly_one_upstream_family_credit_for_both_editions(self):
  row=self.tool(json.loads(self.after[META[0]]));self.assertEqual(('upstream-adapter-validated',1,0,9),(row['implementation_status'],row['new_scoped_integration_credit'],row['native_family_credit_added'],row['source_roles_reviewed']))
  counts=json.loads(self.after[META[1]])['implementation_status_counts'];self.assertEqual((20,89,264),(counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing']))
 def test_no_new_families_or_observations(self):
  self.assertEqual(432,len(json.loads(self.after[META[0]])));self.assertEqual(2671,sum(len(p['tools']) for p in json.loads(self.after[PAPERS])));self.assertEqual(8,check_transition(ROOT,self.before,self.after)['added_reviews'])
 def test_original_source_entrypoint_does_not_claim_native_or_canonical_JAR(self):
  row=self.tool(json.loads(self.after[META[0]]));self.assertTrue(row['development_candidate'].endswith('/run.py'));self.assertIn('requires-qualified-original-R-provider',row['development_candidate_kind']);self.assertIn('not a standalone JAR',row['implementation_scope']);self.assertEqual(['0.4.0','0.6.0'],row['selected_upstream_versions']);self.assertEqual('4.3.3',row['selected_original_R_version']);self.assertFalse(row['canonical_adapter_available']);self.assertFalse(row['root_dispatch_available'])
 def test_reported_and_unreported_editions_remain_literal(self):
  by={p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='ggpubr') for p in json.loads(self.after[PAPERS]) if p['paper_id'] in EXPECTED};self.assertEqual(VERSIONS,{p:by[p]['version_reported_near_name'] for p in EXPECTED})
 def test_current_visible_roles_include_all_required_gaps(self):
  roles=self.tool(json.loads(self.after[META[0]]))['paper_role_coverage'];required={
   'P040':['0.6.0','signed-rank','unpaired','Pearson','unchanged'],
   'P054':['0.6.0','No specific ggpubr function','per-figure','Illustrator'],
   'P058':['unreported','QuPath','RNA-ISH','CellPose','not three proven command runs'],
   'P087':['unreported','stat_compare_means','unpaired two-sample','Variance assumptions'],
   'P098':['0.4.0','violin','does not identify','no ggpubr test or R version'],
   'P101':['0.4.0','base R','agricolae','not reassigned'],
   'P195':['0.4.0','Mann-Whitney','does not name','NMF'],
   'P231':['unreported','No individual plot','R 4.4.0'],
   'P241':['unreported','ggplot2 or ggpubr','111','not another execution']}
  for p,words in required.items():
   for word in words+['private Linux/WSL R 4.3.3','not a standalone JAR','eligibility']:self.assertIn(word,roles[p],p)
 def test_no_whole_tool_paper_reproduction_or_double_version_credit(self):self.reject(META[0],lambda ts:self.tool(ts).update(whole_tool_qualified=True,whole_paper_reproduced=True,new_scoped_integration_credit=2))
 def test_cannot_relabel_original_R_workflow_as_native_Java(self):self.reject(META[0],lambda ts:self.tool(ts).update(implementation_status='native-subset-validated',implementation_scope='Native Java ggpubr with every function'))
 def test_P098_nearby_Wilcoxon_cannot_be_assigned_to_ggpubr(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P098' for t in p['tools'] if t['tool_id']=='ggpubr').update(use_summary='Ran Wilcoxon tests using ggpubr'))
 def test_generic_P054_listing_cannot_gain_a_specific_function(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P054' for t in p['tools'] if t['tool_id']=='ggpubr').update(use_summary='Ran stat_compare_means for every plotted comparison'))
 def test_P101_base_R_and_agricolae_tests_not_reassigned(self):self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P101' for t in p['tools'] if t['tool_id']=='ggpubr').update(implementation_scope_note='All reported statistical tests were performed in ggpubr'))
 def test_P195_Mann_Whitney_and_P231_operation_not_inferred(self):
  for paper,summary in [('P195','Computed Mann-Whitney tests in ggpubr'),('P231','Ran stat_cor for every manipulation')]:self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']==paper for t in p['tools'] if t['tool_id']=='ggpubr').update(use_summary=summary))
 def test_P241_resource_link_does_not_imply_an_edition_or_extra_execution(self):
  rows=copy.deepcopy(reviews(ROOT));rows[-1]['named_anchor_classification']['111']='named-analysis-or-visualization-role'
  with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
  self.reject(PAPERS,lambda ps:next(t for p in ps if p['paper_id']=='P241' for t in p['tools'] if t['tool_id']=='ggpubr').update(version_reported_near_name='0.6.0'))
 def test_all_twelve_anchors_cover_nine_roles_eleven_analysis_one_link(self):
  r=validate(self.census,self.papers,reviews(ROOT));self.assertEqual((9,12,11,1),tuple(r[k] for k in ['individual_paper_role_readings','named_primary_paragraphs','analysis_or_visualization_anchors','resource_link_only_anchors']))
 def test_omitted_duplicate_and_stale_supports_rejected(self):
  for mutation in ['omit','duplicate','stale']:
   rows=copy.deepcopy(reviews(ROOT));support=rows[2]['supporting_evidence']
   if mutation=='omit':support.pop()
   elif mutation=='duplicate':support[-1]=copy.deepcopy(support[0])
   else:support[0]['paragraph_sha256']='0'*64
   with self.assertRaises(ValueError):validate(self.census,self.papers,rows)
 def test_foreign_anchor_and_duplicate_census_rejected(self):
  for mutation in ['identity','duplicate']:
   c=copy.deepcopy(self.census)
   if mutation=='identity':c['matches'][0]['doi']='10.0000/wrong'
   else:c['matches'][-1]=copy.deepcopy(c['matches'][0])
   with self.assertRaises(ValueError):validate(c,self.papers,reviews(ROOT))
 def test_scientific_overlays_forbidden_and_science_remains_live(self):
  for name in ['tools/literature/ggpubr_workflow/run.py','tools/literature/ggpubr_workflow/verify.py','tools/literature/ggpubr/source-profiles.json',CURATION,'other.json']:
   before=dict(self.before);before[name]=b'forged'
   with self.assertRaises(ValueError):metadata_view(ROOT,before)
  base=ROOT/'build/literature-review/monocle-runtime'
  with tempfile.TemporaryDirectory(dir=base) as directory:
   root=Path(directory).resolve();file=root/'science.R';file.write_bytes(b'original');view=metadata_view(root,self.before);self.assertEqual(b'original',(view/'science.R').read_bytes());file.write_bytes(b'changed');self.assertEqual(b'changed',(view/'science.R').read_bytes())
 def test_changed_scientific_source_rejected_before_frozen_verifier_import(self):
  proof=json.loads((ROOT/WORKFLOW/'VALIDATION.json').read_bytes());cumulative=json.loads((ROOT/WORKFLOW/'CUMULATIVE_VERIFICATION.json').read_bytes());base=ROOT/'build/literature-review/monocle-runtime'
  with tempfile.TemporaryDirectory(dir=base) as directory:
   root=Path(directory).resolve()
   for name in ['VALIDATION.json','CUMULATIVE_VERIFICATION.json']:
    p=root/WORKFLOW/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/WORKFLOW/name).read_bytes())
   for name in cumulative['current_scientific_and_auxiliary_source_pins']:
    p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/name).read_bytes())
   (root/'tools/literature/ggpubr_workflow/run.py').write_bytes(b'Deliberately changed scientific workflow source\n')
   with self.assertRaisesRegex(ValueError,'Qualified original-workflow scientific source differs'):qualification(root)
 def test_registry_remains_470_with_only_ggpubr_coverage_updated(self):
  from ggpubr_catalog.verify import load_catalog_registry
  from rstatix_catalog.verify import load_catalog_registry as previous
  old=previous(metadata_view(ROOT,self.before));new=load_catalog_registry(ROOT);self.assertEqual(470,len(new));self.assertEqual([r for r in old if r['id']!='ggpubr'],[r for r in new if r['id']!='ggpubr']);self.assertEqual('upstream-adapter-validated',next(r for r in new if r['id']=='ggpubr')['current_coverage'])
 def test_current_notice_prominent_and_unsupported_warning_changes_rejected(self):
  page=self.after[META[4]].decode();self.assertEqual(1,page.count(NOTICE));self.assertLess(page.index('id="ggpubr-required-actions"'),page.index('id="overview"'))
  for word in ['1,156','1,515','264 named families remain missing','SKIP3','FAIL2','Computation failed in','unavailable, not zero','persistent','not a standalone JAR','Cell Ranger ARC','retry NEW paths']:self.assertIn(word,NOTICE)
  after=dict(self.after);after[META[5]]+=b'Unsupported changed warning\n'
  with self.assertRaises(ValueError):check_transition(ROOT,self.before,after)
if __name__=='__main__':unittest.main()
