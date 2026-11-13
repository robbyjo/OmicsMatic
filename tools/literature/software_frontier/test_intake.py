#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Regression checks for observed name/role conflations against a fresh full-source census."""
from pathlib import Path
import argparse,hashlib,json,traceback
ROOT=Path(__file__).resolve().parents[3]
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--census',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW owned monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;c=json.loads(a.census.read_bytes())
  if c['status']!='PASS_PINNED250_CANDIDATE_CENSUS_AND_DRAFT_ANCHOR_IDENTITY' or c['papers']!=250 or c['new_tool_credit']!=0 or c['main_catalog_modified'] or c['whole_goal_complete']:raise ValueError('Unqualified or overstated source intake')
  for name,wanted in c['source_pins'].items():
   if pin(ROOT/name)!=wanted:raise ValueError('Stale census; rerun the full source audit: '+name)
  rows={(r['paper_id'],r['tool_id'],r['paragraph_index']):r for r in c['matches']};checks=[]
  def expect(label,condition):
   if not condition:raise ValueError('Observed-source regression failed: '+label)
   checks.append(label)
  expect('all250_unique_frozen_primary_identities',len(c['census'])==250 and len({p['pmcid'] for p in c['census']})==250)
  hardware={(r['paper_id'],r['paragraph_index']) for r in c['matches'] if r['tool_id']=='cell_counter' and r['classification']=='hardware-not-software'}
  expect('six_cell_counter_instruments_not_plugin_uses',hardware=={('P006',61),('P007',84),('P051',80),('P098',55),('P187',7),('P192',14)})
  positive=[r for r in c['matches'] if r['tool_id']=='cell_counter' and r['classification']=='software-analysis']
  expect('five_true_plugin_anchors_in_three_papers',len(positive)==5 and {r['paper_id'] for r in positive}=={'P005','P018','P165'})
  expect('ordinary_patchwork_P036',rows[('P036','patchwork',1)]['classification']=='ordinary-word-not-software')
  expect('ordinary_patchwork_P111',rows[('P111','patchwork',1)]['classification']=='ordinary-word-not-software')
  expect('actual_patchwork_package_P054',rows[('P054','patchwork',93)]['classification']=='software-presentation' and rows[('P054','patchwork',93)]['version_reported_near_name']=='1.2.0')
  expect('spaced_Dye_Assistant_is_named',rows[('P038','leica_dyeassistant',101)]['classification']=='software-acquisition' and any(' ' in name for name in rows[('P038','leica_dyeassistant',101)]['matched_names']))
  expect('Harmony_PyTorch_is_not_base_PyTorch',all(('P093','pytorch',i) not in rows and ('P093','harmony_pytorch',i) in rows for i in [31,49]))
  expect('Harmony_PyTorch_edition_kept_separate',rows[('P093','harmony_pytorch',31)]['version_reported_near_name']=='0.1.7')
  expect('PyTorch_Geometric_is_not_base_PyTorch',all((p,'pytorch',i) not in rows and rows[(p,'pytorch_geometric',i)]['classification']=='software-analysis' for p,i in [('P009',104),('P236',11)]))
  expect('PyTorch_and_Lightning_editions_separate',rows[('P154','pytorch',36)]['version_reported_near_name']=='1.13.1' and rows[('P154','pytorch_lightning',36)]['version_reported_near_name']=='1.5.10')
  expect('literal_NumPy_1_12_5_not_repaired',rows[('P121','numpy',41)]['version_reported_near_name']=='1.12.5')
  expect('Visiopharm_parent_and_AI_app_editions_distinct',rows[('P085','visiopharm_nuclei_detect_ai',48)]['version_reported_near_name']=='2023.01.2.13695' and rows[('P085','visiopharm',48)]['version_reported_near_name'].startswith('2022.03;'))
  expect('Theano_historical_reference_not_current_execution',rows[('P184','theano',23)]['classification']=='historical-implementation-reference' and rows[('P184','pytorch',23)]['classification']=='software-analysis')
  expect('Bin2cell_output_reference_not_new_execution',rows[('P051','bin2cell',124)]['classification']=='data-product-reference' and rows[('P051','schard',124)]['classification']=='software-data-io')
  expect('named_anchor_counts_and_whole_paper_scope_not_conflated',c['reviewed_primary_anchors']+c['unreviewed_primary_anchors']==c['named_primary_anchors'] and not c['whole_paper_semantic_review_complete'])
  result=dict(status='PASS_OBSERVED_PRIMARY_SOURCE_NAME_ROLE_AND_EDITION_REGRESSIONS',checks=checks,checks_passed=len(checks),census=pin(a.census),test_source=pin(Path(__file__)),all_human_semantics_automatically_verified=False,new_tool_credit=0,main_catalog_modified=False,whole_goal_complete=False);(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(result['status'],'checks='+str(len(checks)),flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_SOURCE_REGRESSION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_SOURCE_INTAKE_REGRESSIONS',cause=repr(e),traceback=traceback.format_exc(),required_action='Retain the failure and correct the named source identity, pattern or role. Rerun the full primary audit after any intake change, then retry NEW paths. Do not repair original version strings or credit software from hardware, ordinary words, package substrings or historical references.')
  if owned:(out/'FAILURE.json').write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
