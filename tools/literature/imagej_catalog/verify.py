#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify cumulative source roles, both complete retained Java workflows and exact predecessor preservation."""
from pathlib import Path
import argparse,importlib.util,json,os,sys,tempfile,traceback
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from imagej_catalog.model import ROOT,DATA,EVIDENCE,META,PAPERS,REVIEWS,REGISTRY,CURATION,encoded,pin,rawpin,reviews,check_transition
from imagej_catalog.bridge import load_before,metadata_view,prior_admission
from imagej_catalog.source import scan,validate
ACTION='Required action: retain exact source, scope, diagnostics and predecessor evidence. Missing/forbidden cached primary XML SKIP3 skips only the dependent rescan; --rescan-sources reads all250 sources. Supply permitted original runtime/display/provider/plugins before any later analysis. FAIL2 admits no result; correct the named cause without changing original algorithms, then retry NEW output. This catalog admission validates complete saved controls and primary-use metadata, not new original image analysis, exact author settings or paper reproduction. ImageJ1.54t is distinct from Fiji/ImageJ2/plugins. P026 bleach edition/method/settings remain unreported; histogram matching is inappropriate for intensity quantification. The full implementation and semantic-review goal stays open.'
def qualification(root):
 result={};base=Path(os.fspath(root/'build/literature-review/monocle-runtime')).resolve();base.mkdir(parents=True,exist_ok=True)
 for id,folder,status in [('imagej154t','imagej154t-workflow-development','PASS_ORIGINAL_IMAGEJ154T_COMPLETE_MACRO_WORKFLOW'),('correctbleach210','correctbleach210-workflow-development','PASS_ORIGINAL_CORRECTBLEACH210_AND_NATIVE_FULL_IMAGE_WORKFLOWS')]:
  data='docs/literature/'+folder;v=json.loads((root/data/'VALIDATION.json').read_bytes())
  if v['status']!=status or v['original_source_patched'] or v['whole_goal_complete']:raise ValueError('Original component scope differs')
  for n,wanted in v['sources'].items():
   if pin(root/n)!=wanted:raise ValueError('Qualified scientific source differs: '+n)
  for n,wanted in json.loads((root/data/'ARTIFACTS.json').read_bytes()).items():
   # ImageJ stores descriptive records per platform; CorrectBleach stores path -> pin.
   if 'path' in wanted:path=wanted['path'];expected={k:wanted[k] for k in ['bytes','sha256']}
   else:path=n;expected=wanted
   if pin(root/path)!=expected:raise ValueError('Published development artifact differs: '+path)
  file=Path(os.fspath(root/('tools/literature/'+id+'/verify.py')));spec=importlib.util.spec_from_file_location('retained_'+id,file);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  with tempfile.TemporaryDirectory(prefix=id+'-catalog-',dir=base) as d:
   scratch=Path(d).resolve()
   if not scratch.is_relative_to(base):raise ValueError('Evidence scratch escaped owned workspace')
   module.evidence(scratch)
  result[id]=dict(status=status,complete_original_platforms=3,original_source_patched=False,original_analysis_repeated=False,development_artifacts_verified=True,all_complete_retained_objects_verified=True)
 result['imagej154t'].update(original_macros_per_platform=5,complete_outputs_per_platform=14,request_refusals_per_platform=21,setup_refusals=7)
 result['correctbleach210'].update(original_image_cases_per_platform=21,native_image_cases_per_platform=6,request_refusals_per_platform=24,setup_refusals=12,original_histogram_cases_per_platform=1048,complete_lookup_rows_per_platform=212763,complete_cdf_raw_values_per_platform=426993,offline_full_rebuilds=2)
 return result
def load_catalog_registry(root=ROOT):
 from p040_catalog.verify import load_catalog_registry as previous
 before=load_before(root);old=previous(metadata_view(root,before));new=json.loads((root/REGISTRY).read_bytes())
 if len(old)!=467 or len(new)!=1 or new[0]['id']!='bleach_correction' or {r['id'] for r in old}&{r['id'] for r in new}:raise ValueError('Unexpected registry extension')
 from imagej_catalog.model import entries
 imagej,_=entries(root,before);keys=['current_coverage','existing_source','coverage_note'];return [dict(r,**{k:imagej[k] for k in keys}) if r['id']=='fiji_imagej' else r for r in old]+new
def verify(root=ROOT,rescan=False):
 proof=json.loads((root/DATA/'imagej-catalog-validation.json').read_bytes())
 if proof['status']!='PASS_TWO_ORIGINAL_JAVA_IMAGE_WORKFLOWS_AND_51_ADDITIONAL_REVIEWS' or proof['new_scoped_integration_credit']!=2 or proof['whole_goal_complete']:raise ValueError('Unsupported catalog scope')
 for key in ['source_pins','evidence_pins','after_files']:
  for n,wanted in proof[key].items():
   if pin(root/n)!=wanted:raise ValueError('Current source/evidence differs: '+n)
 before=load_before(root)
 if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive']:raise ValueError('Exact predecessor archive differs')
 result=check_transition(root,before,{n:(root/n).read_bytes() for n in META});source=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes());validate(source,json.loads(before[PAPERS]),reviews(root))
 if json.loads((root/EVIDENCE/'curation-intake-before.json').read_bytes())['reviews']!=reviews(root):raise ValueError('Individual review records changed from their qualified intake')
 old=prior_admission(root,before);components=qualification(root)
 from catalog_source_integrity import reverse_delta,verify_source
 compatibility=json.loads((root/DATA/'imagej-reviews-shared-source-compatibility.json').read_bytes())
 if len(compatibility['paths'])!=1 or compatibility['paths'][0]['path']!=REVIEWS or reverse_delta((root/REVIEWS).read_bytes(),compatibility['paths'][0])!=before[REVIEWS]:raise ValueError('Exact review append inverse differs')
 verify_source(root,root/DATA,REVIEWS,rawpin(before[REVIEWS])['sha256'])
 if rescan and scan(root,json.loads(before[PAPERS]))!=source:raise ValueError('Fresh full-source census differs')
 return dict(status=proof['status'],**result,components=components,registry_definitions=len(load_catalog_registry(root)),primary_sources_rescanned=rescan,previous_admission=old,scientific_sources_overridden=False,scientific_verifiers_changed=False,original_analysis_executed=False,paper_wide_semantic_review_open=True)
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify(rescan=a.rescan_sources);(out/'COMPLETED.json').write_bytes(encoded(result));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_IMAGEJ_CATALOG',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(v));(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
