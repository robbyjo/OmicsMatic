#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit exactly one qualified original workflow and six source readings; preserve all other work."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from cell2location_catalog.model import *
from cell2location_catalog.source import scan,validate
from cell2location_catalog.qualification import qualification
from cell2location_catalog.verify import ACTION

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if any((ROOT/n).exists() for n in [EVIDENCE,PROOF,COVERAGE]):raise ValueError('Admission evidence already exists; preserve its original files')
  before={n:(ROOT/n).read_bytes() for n in META};previous=json.loads((ROOT/DATA/'rstatix-revision2-catalog-validation.json').read_bytes())
  if {n:rawpin(v) for n,v in before.items()}!=previous['after_files']:raise ValueError('Current exact predecessor is not the admitted rstatix revision2 catalog')
  component=qualification(ROOT);papers=json.loads(before[PAPERS]);census=scan(ROOT,papers);roles=validate(census,papers,reviews(ROOT),ROOT);after=transition(ROOT,before);result=check_transition(ROOT,before,after)
  evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_bytes(b'* -text\n')
  with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for n,raw in before.items():
    info=zipfile.ZipInfo(n,(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
  for n,raw in [('CENSUS.json',encoded(census)),('qualified-source-reviews.json',(ROOT/CURATION).read_bytes()),('qualified-intake.json',(ROOT/INTAKE).read_bytes())]:(evidence/n).write_bytes(raw)
  audit=dict(status='PASS_ALL250_PRIMARY_SOURCES_SIX_INDIVIDUAL_ROLES_AND_COMPLETE_ORIGINAL_WORKFLOW_BEFORE_ADMISSION',source_roles=roles,component=component,new_upstream_family_credit=1,new_native_credit=0,all_other_sources_and_roles_unchanged=True,whole_goal_complete=False);(evidence/'SOURCE_AUDIT.json').write_bytes(encoded(audit))
  row=next(t for t in json.loads(after[META[0]]) if t['id']=='cell2location');(ROOT/COVERAGE).write_bytes(encoded(dict(status='PASS_SIX_PRIMARY_CELL2LOCATION_ROLES_AND_ORIGINAL013_CPU_WORKFLOW',source_reviews=reviews(ROOT),paper_role_coverage=row['paper_role_coverage'],uncovered_roles=row['uncovered_roles'],reported_0_7a0_execution_qualified=False,whole_tool_qualified=False,paper_reproduced=False,new_scoped_integration_credit=1,whole_goal_complete=False)))
  names=['tools/literature/cell2location_catalog/'+n for n in ['.gitattributes','model.py','source.py','bridge.py','qualification.py','verify.py','apply.py','test_catalog.py','check_ui.cjs']]+[GUIDE,CURATION,INTAKE,'tools/literature/rstatix_revision2_catalog/model.py','tools/literature/rstatix_revision2_catalog/verify.py','tools/literature/run_mrmega_catalog_checks.py']
  source_pins={n:pin(ROOT/n) for n in names};evidence_names=[EVIDENCE+'/'+n for n in ['.gitattributes','CENSUS.json','SOURCE_AUDIT.json','qualified-source-reviews.json','qualified-intake.json']]+[COVERAGE,DATA+'/rstatix-revision2-catalog-validation.json']
  evidence_names += [p.relative_to(ROOT).as_posix() for p in (ROOT/WORKFLOW).rglob('*') if p.is_file()];evidence_pins={n:pin(ROOT/n) for n in evidence_names}
  for n,raw in before.items():
   if (ROOT/n).read_bytes()!=raw:raise ValueError('Concurrent catalog change; preserve/reconcile: '+n)
  (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},new_tool_credit=1,added_reviews=6)))
  for n,raw in after.items():(ROOT/n).write_bytes(raw)
  proof=dict(status='PASS_ORIGINAL_CELL2LOCATION013_WORKFLOW_AND_SIX_PRIMARY_SOURCE_ROLES',before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins=source_pins,evidence_pins=evidence_pins,**result,admitted_new_tool_ids=['cell2location'],scientific_sources_overridden=False,scientific_verifiers_changed=False)
  (ROOT/PROOF).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_CELL2LOCATION_ADMISSION_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
