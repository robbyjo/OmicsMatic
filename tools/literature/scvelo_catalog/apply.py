#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply one qualified workflow, five existing source roles and one separately missing wrapper."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from scvelo_catalog.model import *
from scvelo_catalog.source import scan,validate
from scvelo_catalog.qualification import qualification
from scvelo_catalog.verify import compatibility,ACTION

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if any((ROOT/n).exists() for n in [EVIDENCE,PROOF,COVERAGE,COMPATIBILITY]):raise ValueError('Original admission evidence already exists; preserve it')
  before={n:(ROOT/n).read_bytes() for n in META};previous=json.loads((ROOT/DATA/'cell2location-catalog-validation.json').read_bytes())
  if {n:rawpin(v) for n,v in before.items()}!=previous['after_files']:raise ValueError('Current predecessor is not the exact admitted cell2location catalog')
  component=qualification(ROOT);papers=json.loads(before[PAPERS]);census=scan(ROOT,papers);roles=validate(census,papers,reviews(ROOT),ROOT);after=transition(ROOT,before);result=check_transition(ROOT,before,after);inverse=compatibility(before,after)
  evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_bytes(b'* -text\n')
  with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in before.items():
    info=zipfile.ZipInfo(name,(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
  for name,raw in [('CENSUS.json',encoded(census)),('qualified-source-reviews.json',(ROOT/CURATION).read_bytes()),('qualified-intake.json',(ROOT/INTAKE).read_bytes()),('new-wrapper-definition.json',(ROOT/DEFINITION).read_bytes()),('new-wrapper-extraction.json',(ROOT/EXTRACTION).read_bytes())]:(evidence/name).write_bytes(raw)
  (evidence/'SOURCE_AUDIT.json').write_bytes(encoded(dict(status='PASS_ALL250_PRIMARY_IDENTITIES_FIVE_SCVELO_ROLES_ONE_SEPARATE_WRAPPER_AND_COMPLETE_ORIGINAL_COMPONENT_BEFORE_ADMISSION',source_roles=roles,component=component,new_upstream_family_credit=1,new_native_credit=0,new_R_wrapper_credit=0,whole_goal_complete=False)))
  row=next(t for t in json.loads(after[META[0]]) if t['id']=='scvelo');(ROOT/COVERAGE).write_bytes(encoded(dict(status='PASS_FIVE_SCVELO_ROLES_AND_ONE_SEPARATE_MISSING_WRAPPER',source_reviews=reviews(ROOT),paper_role_coverage=row['paper_role_coverage'],uncovered_roles=row['uncovered_roles'],reported_0_24_execution_qualified=False,R_wrapper_execution_qualified=False,whole_tool_qualified=False,paper_reproduced=False,new_scoped_integration_credit=1,whole_goal_complete=False)))
  (ROOT/COMPATIBILITY).write_bytes(encoded(inverse))
  names=['tools/literature/scvelo_catalog/'+n for n in ['.gitattributes','model.py','source.py','bridge.py','qualification.py','verify.py','apply.py','test_catalog.py','check_ui.cjs','new-wrapper-definition.json','new-wrapper-extraction.json']]+[GUIDE,CURATION,INTAKE,'tools/literature/cell2location_catalog/model.py','tools/literature/cell2location_catalog/verify.py','tools/literature/catalog_source_integrity.py','tools/literature/wire_signac_catalog.py','tools/literature/run_mrmega_catalog_checks.py']
  source_pins={n:pin(ROOT/n) for n in names};evidence_names=[p.relative_to(ROOT).as_posix() for p in evidence.iterdir() if p.is_file() and p.name!='catalog-before.zip']+[COVERAGE,COMPATIBILITY,DATA+'/cell2location-catalog-validation.json']
  evidence_names += [p.relative_to(ROOT).as_posix() for p in (ROOT/WORKFLOW).rglob('*') if p.is_file()];evidence_pins={n:pin(ROOT/n) for n in evidence_names}
  for name,raw in before.items():
   if (ROOT/name).read_bytes()!=raw:raise ValueError('Concurrent catalog edit; preserve and reconcile: '+name)
  (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},new_tool_credit=1,added_reviews=6,new_missing_wrapper_implementation_credit=0)))
  for name,raw in after.items():(ROOT/name).write_bytes(raw)
  proof=dict(status='PASS_ORIGINAL_SCVELO024025_FIVE_ROLES_AND_SEPARATE_MISSING_VELOCIRAPTOR',before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins=source_pins,evidence_pins=evidence_pins,**result,admitted_new_tool_ids=['scvelo'],newly_registered_missing_tool_ids=['velociraptor'],scientific_sources_overridden=False,scientific_verifiers_changed=False)
  (ROOT/PROOF).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_SCVELO_ADMISSION_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_bytes((result['status']+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))
  print(result['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
