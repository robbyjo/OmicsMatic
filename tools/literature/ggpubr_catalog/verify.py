#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify cumulative ggpubr admission, exact source roles and unchanged complete scientific evidence."""
from pathlib import Path
import argparse,json,sys,traceback
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from ggpubr_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,EVIDENCE,PROOF,COMPATIBILITY,CURATION,INTAKE,encoded,pin,rawpin,reviews,check_transition
from ggpubr_catalog.bridge import load_before,metadata_view,prior_admission
from ggpubr_catalog.source import scan,validate
from ggpubr_catalog.qualification import qualification
ACTION='Required action: retain exact primary XML identities, all original plot/table/layer/condition evidence, every failed attempt, the lossless negative-ZIP deltas and the exact predecessor metadata. SKIP3 skips dependent analysis/verification when source, evidence, matching private Linux/WSL R provider, input, literal export/package, permitted install/download or execution is unavailable/forbidden. Supply only permitted exact prerequisites and retry NEW paths. FAIL2 admits no complete result; preserve raw objects, errors/warnings, logs, partial outputs, manifest and status, correct the named cause and retry NEW paths. Review all original warnings, require explicit grouping/eligibility, and do not treat unknown condition totals as zero. Never infer tests/figures from generic mentions, substitute editions, modify original algorithms or claim author-runtime or paper reproduction. Read GGPUBR_CATALOG_INTEGRATION.md and GGPUBR_WORKFLOW_DEVELOPMENT.md. This catalog gate executes no new original analysis or installation. Full semantic review and implementation remain open.'
def load_catalog_registry(root=ROOT):
 from rstatix_catalog.verify import load_catalog_registry as previous
 old=previous(metadata_view(root,load_before(root)))
 if len(old)!=470 or len({r['id'] for r in old})!=470:raise ValueError('Unexpected cumulative registry population')
 current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']=='ggpubr');found=0
 for row in old:
  if row['id']=='ggpubr':
   for k in ['current_coverage','existing_source','coverage_note']:row[k]=current[k]
   found+=1
 if found!=1:raise ValueError('Missing/duplicate previous ggpubr definition')
 return old
def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_ORIGINAL_GGPUBR_CATALOG_ADMISSION_AND_NINE_SOURCE_ROLES' or proof['new_scoped_integration_credit']!=1 or proof['native_family_credit_added']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported ggpubr admission/scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Current source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata differs')
 result=check_transition(root,before,{n:(root/n).read_bytes() for n in META})
 census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());source=validate(census,json.loads(before[PAPERS]),reviews(root),root)
 if (root/EVIDENCE/'qualified-intake-before.json').read_bytes()!=(root/INTAKE).read_bytes() or (root/EVIDENCE/'curation-intake.json').read_bytes()!=(root/CURATION).read_bytes():raise ValueError('Qualified individual source reading changed')
 previous=prior_admission(root,before);components=qualification(root)
 from catalog_source_integrity import reverse_delta,verify_source
 compatibility=json.loads((root/COMPATIBILITY).read_bytes())
 if len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:raise ValueError('Exact eight-review append inverse differs')
 verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
 if rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Fresh250-source census differs')
 return dict(status=proof['status'],**result,components=components,primary_source_scope=source,registry_definitions=len(load_catalog_registry(root)),primary_sources_rescanned=rescan,
  previous_admission=previous,scientific_sources_overridden=False,scientific_verifiers_changed=False,original_analysis_executed=False,paper_wide_semantic_review_open=True)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_GGPUBR_CATALOG',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
