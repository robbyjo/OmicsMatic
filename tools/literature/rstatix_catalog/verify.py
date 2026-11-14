#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify cumulative rstatix admission, full saved scientific controls and exact paper roles."""
from pathlib import Path
import argparse,json,sys,traceback
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from rstatix_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,EVIDENCE,PROOF,COMPATIBILITY,CURATION,INTAKE,encoded,pin,rawpin,reviews,check_transition
from rstatix_catalog.bridge import load_before,metadata_view,prior_admission
from rstatix_catalog.source import scan,validate
from rstatix_catalog.qualification import qualification

ACTION=('Required action: retain exact primary XML, full original/native evidence, all prior source reviews and predecessor metadata. SKIP3 skips dependent analysis for missing/forbidden source, Java/R runtime, provider, input, download, installation, literal export/package or execution. Install only permitted prerequisites and retry NEW paths. Original rstatix0.7.2 needs the private Linux/WSL R4.5.3 provider and trusted caller R script; the entry point is Python source, not a standalone JAR. Native Java Levene is separate and supplies only prepared one-way mean/median-centered vectors. FAIL2 admits no complete result; preserve full original objects/errors/warnings, all partial results, logs, manifests and status, correct the named cause and retry NEW paths. Review every original warning; original warn=2 remains an error. Never repair wilcoxon_test to wilcox_test silently, infer author designs/families/pairing/direction, substitute generic native tests or claim historical runtime equivalence/paper reproduction. Read RSTATIX_CATALOG_INTEGRATION.md and RSTATIX072_WORKFLOW_DEVELOPMENT.md. This gate executes no new original analysis or installation. Full semantic review and implementation remain open.')

def load_catalog_registry(root=ROOT):
 from qupath_catalog.verify import load_catalog_registry as previous
 old=previous(metadata_view(root,load_before(root)))
 if len(old)!=470 or len({r['id'] for r in old})!=470:raise ValueError('Unexpected cumulative registry population')
 current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']=='rstatix');found=0
 for row in old:
  if row['id']=='rstatix':
   for k in ['current_coverage','existing_source','coverage_note']:row[k]=current[k]
   found+=1
 if found!=1:raise ValueError('Missing/duplicate previous rstatix definition')
 return old

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/PROOF).read_bytes())
 if proof['status']!='PASS_ORIGINAL_RSTATIX072_CATALOG_ADMISSION_AND_SIX_SOURCE_ROLES' or proof['new_scoped_integration_credit']!=1 or proof['native_family_credit_added']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported rstatix admission/scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Current source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact predecessor metadata/archive differs')
 result=check_transition(root,before,{n:(root/n).read_bytes() for n in META})
 census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());source=validate(census,json.loads(before[PAPERS]),reviews(root))
 if (root/EVIDENCE/'qualified-intake-before.json').read_bytes()!=(root/INTAKE).read_bytes() or (root/EVIDENCE/'curation-intake.json').read_bytes()!=(root/CURATION).read_bytes():raise ValueError('Individual source reading/intake changed')
 previous=prior_admission(root,before);components=qualification(root)
 from catalog_source_integrity import reverse_delta,verify_source
 compatibility=json.loads((root/COMPATIBILITY).read_bytes())
 if len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:raise ValueError('Exact five-review append inverse differs')
 verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
 if rescan:
  fresh=scan(root,json.loads(before[PAPERS]))
  if fresh!=census:raise ValueError('Fresh250-source census differs')
 return dict(status=proof['status'],**result,components=components,primary_source_scope=source,registry_definitions=len(load_catalog_registry(root)),
  primary_sources_rescanned=rescan,previous_admission=previous,scientific_sources_overridden=False,scientific_verifiers_changed=False,
  original_analysis_executed=False,paper_wide_semantic_review_open=True)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_CATALOG',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
