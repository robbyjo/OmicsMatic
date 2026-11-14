#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Update only existing rstatix runtime routing and recovery; preserve all scientific reviews and counts."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from rstatix_revision2_catalog.model import *
from rstatix_revision2_catalog.qualification import qualification
from rstatix_revision2_catalog.verify import ACTION

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if any((ROOT/n).exists() for n in [EVIDENCE,PROOF,COVERAGE]):raise ValueError('Routing revision already exists; preserve earlier records')
  before={n:(ROOT/n).read_bytes() for n in META};prior=json.loads((ROOT/DATA/'ggpubr-catalog-validation.json').read_bytes())
  if {n:rawpin(v) for n,v in before.items()}!=prior['after_files']:raise ValueError('Current predecessor is not the exact admitted ggpubr catalog')
  component=qualification(ROOT);papers=json.loads(before[PAPERS]);census=scan(ROOT,papers);source=validate(census,papers,reviews(ROOT),ROOT)
  after=transition(ROOT,before);result=check_transition(ROOT,before,after)
  for n in [PAPERS,REVIEWS,META[1]]:
   if before[n]!=after[n]:raise ValueError('Routing must preserve scientific observations/reviews/summary bytes')
  evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_text('* -text\n',encoding='ascii')
  with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for n,raw in before.items():
    info=zipfile.ZipInfo(n,(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
  for name,raw in [('CENSUS.json',encoded(census)),('unchanged-source-reviews.json',(ROOT/CURATION).read_bytes()),('unchanged-intake.json',(ROOT/INTAKE).read_bytes())]:(evidence/name).write_bytes(raw)
  audit=dict(status='PASS_ALL250_PRIMARY_SOURCES_UNCHANGED_SIX_RSTATIX_ROLES_AND_COMPLETE_REVISION2_EVIDENCE_BEFORE_ROUTING',source=source,component=component,all_observations_and_reviews_unchanged=True,new_tool_credit=0,scientific_sources_changed=False,scientific_verifiers_changed=False,whole_goal_complete=False)
  (evidence/'SOURCE_AUDIT.json').write_bytes(encoded(audit));current=next(r for r in json.loads(after[META[0]]) if r['id']=='rstatix')
  (ROOT/COVERAGE).write_bytes(encoded(dict(status='PASS_UNCHANGED_SIX_RSTATIX_SOURCE_ROLES_WITH_REVISION2_RUNTIME_ROUTING',source_reviews=reviews(ROOT),current_paper_role_coverage=current['paper_role_coverage'],uncovered_roles=current['uncovered_roles'],supervisor_revision=2,new_scoped_integration_credit=0,added_reviews=0,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False)))
  names=['tools/literature/rstatix_revision2_catalog/'+n for n in ['.gitattributes','model.py','bridge.py','qualification.py','verify.py','apply.py','test_catalog.py','check_ui.cjs']]+[GUIDE,CURATION,INTAKE,'tools/literature/rstatix_catalog/source.py','tools/literature/ggpubr_catalog/model.py','tools/literature/ggpubr_catalog/verify.py','tools/literature/run_mrmega_catalog_checks.py']
  source_pins={n:pin(ROOT/n) for n in names}
  evidence_names=[EVIDENCE+'/'+n for n in ['.gitattributes','CENSUS.json','SOURCE_AUDIT.json','unchanged-source-reviews.json','unchanged-intake.json']]+[COVERAGE,DATA+'/ggpubr-catalog-validation.json']
  evidence_names += [WORKFLOW+'/'+p.name for p in (ROOT/WORKFLOW).iterdir() if p.is_file()]
  evidence_pins={n:pin(ROOT/n) for n in evidence_names}
  for n,raw in before.items():
   if (ROOT/n).read_bytes()!=raw:raise ValueError('Concurrent catalog edit: '+n)
  (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},new_tool_credit=0)))
  for n,raw in after.items():(ROOT/n).write_bytes(raw)
  proof=dict(status='PASS_ZERO_CREDIT_RSTATIX_REVISION2_CURRENT_CATALOG_ROUTING',before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins=source_pins,evidence_pins=evidence_pins,**result,routed_tool_ids=['rstatix'],admitted_new_tool_ids=[],scientific_sources_changed=False,scientific_verifiers_changed=False,previous_goal_turn_classification='progress')
  (ROOT/PROOF).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_ROUTING_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_REVISION2_ROUTING_APPLY',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
