#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply the exact two-workflow admission and reviewed ImageJ roles, retaining predecessors."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from imagej_catalog.model import *
from imagej_catalog.source import scan
from imagej_catalog.verify import qualification,ACTION
from p040_catalog.verify import verify as previous
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;archive=ROOT/EVIDENCE/'catalog-before.zip';proofPath=ROOT/DATA/'imagej-catalog-validation.json';compatibility=DATA+'/imagej-reviews-shared-source-compatibility.json';coverage=DATA+'/imagej-role-coverage.json'
  if any(p.exists() for p in [archive,proofPath,ROOT/compatibility,ROOT/coverage]):raise ValueError('Amendment already exists; preserve all evidence')
  prior=previous(ROOT);before={n:(ROOT/n).read_bytes() for n in META};papers=json.loads(before[PAPERS]);census=scan(ROOT,papers);components=qualification(ROOT);evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=True)
  if json.loads((evidence/'curation-intake-before.json').read_bytes())['reviews']!=reviews(ROOT):raise ValueError('Individual review records changed from their qualified intake; only the explanatory scope was updated')
  (evidence/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n',encoding='ascii',newline='\n');censusPath=evidence/'CENSUS.json'
  if censusPath.exists() and censusPath.read_bytes()!=encoded(census):raise ValueError('Existing intake census differs; do not overwrite')
  if not censusPath.exists():censusPath.write_bytes(encoded(census))
  after=transition(ROOT,before);result=check_transition(ROOT,before,after)
  with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in before.items():
    info=zipfile.ZipInfo(name,(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
  (ROOT/REGISTRY).write_bytes(encoded([BLEACH]));(evidence/'SOURCE_AUDIT.json').write_bytes(encoded(dict(status='PASS_ALL250_SOURCES_AND_BOTH_COMPLETE_SAVED_COMPONENTS_BEFORE_MUTATION',source=dict(papers=250,imagej_reviewed_uses=51,imagej_paragraphs=84,bleach_reviewed_uses=1,bleach_paragraphs=1),components=components,previous={k:prior[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing']},scientific_sources_changed=False,original_analysis_executed=False,whole_goal_complete=False)))
  fiji,bleach=entries(ROOT,before);(ROOT/coverage).write_bytes(encoded(dict(status='PASS_SELECTED_IMAGEJ_CORE_AND_CORRECTBLEACH_SOURCE_ROLES',imagej_paper_roles=fiji['paper_role_coverage'],imagej_uncovered_roles=fiji['uncovered_roles'],bleach_paper_roles=bleach['paper_role_coverage'],source_reviews=reviews(ROOT),preexisting_P040_review_retained=True,additional_reviews=51,new_scoped_integration_credit=2,whole_paper_reproduced=False,whole_tool_qualified=False,whole_goal_complete=False)))
  delta=inverse(before[REVIEWS],after[REVIEWS])
  if reverse_delta(after[REVIEWS],delta)!=before[REVIEWS]:raise ValueError('Exact review append inverse differs')
  (ROOT/compatibility).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='imagej-and-bleach-reviewed-roles',scope='Exactly50 appended ImageJ reviews and one Bleach Corrector review; all1079 prior reviews unchanged. No scientific source overlay is allowed.',paths=[delta])))
  for name,raw in before.items():
   if (ROOT/name).read_bytes()!=raw:raise ValueError('Concurrent catalog edit: '+name)
  (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},before_archive=pin(archive))))
  for name,raw in after.items():(ROOT/name).write_bytes(raw)
  sources=['tools/literature/imagej_catalog/'+n for n in ['model.py','source.py','bridge.py','verify.py','apply.py','test_catalog.py','check_ui.cjs','.gitattributes']]+[REGISTRY,CURATION,GUIDE,'tools/literature/catalog_source_integrity.py','tools/literature/run_mrmega_catalog_checks.py','tools/literature/curate_shapeit_uses.py','tools/literature/collect_omics_review.py']
  evidenceNames=[EVIDENCE+'/'+n for n in ['CENSUS.json','SOURCE_AUDIT.json','curation-intake-before.json','.gitattributes']]+[coverage,compatibility,DATA+'/p040-catalog-validation.json']+[f'docs/literature/{folder}/{name}' for folder,names in [('imagej154t-workflow-development',['VALIDATION.json','ARTIFACTS.json','SHA256SUMS','OFFLINE_REPLAY.json']),('correctbleach210-workflow-development',['VALIDATION.json','ARTIFACTS.json','SHA256SUMS','OFFLINE_REPLAY.json','PUBLIC_POLICY.json'])] for name in names]
  proof=dict(status='PASS_TWO_ORIGINAL_JAVA_IMAGE_WORKFLOWS_AND_51_ADDITIONAL_REVIEWS',before_archive=pin(archive),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins={n:pin(ROOT/n) for n in sources},evidence_pins={n:pin(ROOT/n) for n in evidenceNames},**result,admitted_tool_ids=['fiji_imagej','bleach_correction'],added_software_names=1,scientific_sources_changed=False,scientific_verifiers_changed=False)
  proofPath.write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_IMAGEJ_CATALOG_AMENDMENT',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(v));(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
