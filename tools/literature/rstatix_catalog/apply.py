#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit the complete original rstatix workflow and append five individual source-role reviews."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from rstatix_catalog.model import *
from rstatix_catalog.source import scan,validate
from rstatix_catalog.qualification import NATIVE,WORKFLOW,qualification
from rstatix_catalog.verify import ACTION
from qupath_catalog.verify import verify as previous
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;archive=ROOT/EVIDENCE/'catalog-before.zip'
  if any(path.exists() for path in [ROOT/EVIDENCE,ROOT/PROOF,ROOT/COMPATIBILITY,ROOT/COVERAGE]):raise ValueError('Admission already exists; preserve all evidence')
  prior=previous(ROOT);before={n:(ROOT/n).read_bytes() for n in META};papers=json.loads(before[PAPERS]);census=scan(ROOT,papers);components=qualification(ROOT)
  evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n',encoding='ascii',newline='\n')
  (evidence/'CENSUS.json').write_bytes(encoded(census));(evidence/'qualified-intake-before.json').write_bytes((ROOT/INTAKE).read_bytes());(evidence/'curation-intake.json').write_bytes((ROOT/CURATION).read_bytes())
  after=transition(ROOT,before);result=check_transition(ROOT,before,after)
  with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as saved:
   for n,raw in before.items():
    info=zipfile.ZipInfo(n,(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;saved.writestr(info,raw)
  audit=dict(status='PASS_ALL250_PRIMARY_SOURCES_AND_COMPLETE_ORIGINAL_RSTATIX_CONTROLS_BEFORE_MUTATION',source=validate(census,papers,reviews(ROOT)),components=components,
   previous={k:prior[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing']},scientific_sources_changed=False,scientific_verifiers_changed=False,original_analysis_executed=False,whole_goal_complete=False)
  (evidence/'SOURCE_AUDIT.json').write_bytes(encoded(audit));current=next(t for t in json.loads(after[META[0]]) if t['id']=='rstatix')
  (ROOT/COVERAGE).write_bytes(encoded(dict(status='PASS_SIX_RSTATIX_ROLES_WITH_EXPLICIT_DESIGN_FUNCTION_AND_RUNTIME_GAPS',source_reviews=reviews(ROOT),
   current_paper_role_coverage=current['paper_role_coverage'],uncovered_roles=current['uncovered_roles'],selected_original_version='0.7.2',selected_R_version='4.5.3',
   new_scoped_integration_credit=1,native_family_credit_added=0,existing_P040_review_unchanged=True,whole_paper_reproduced=False,whole_tool_qualified=False,whole_goal_complete=False)))
  delta=inverse(before[REVIEWS],after[REVIEWS])
  if reverse_delta(after[REVIEWS],delta)!=before[REVIEWS]:raise ValueError('Exact five-review append inverse differs')
  (ROOT/COMPATIBILITY).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='rstatix-individual-source-roles',
   scope='Exactly five appended individual reviews; all1143 previous reviews, including P040, unchanged. No scientific-source overlay.',paths=[delta])))
  for n,raw in before.items():
   if (ROOT/n).read_bytes()!=raw:raise ValueError('Concurrent catalog edit: '+n)
  (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},before_archive=pin(archive))))
  sources=['tools/literature/rstatix_catalog/'+n for n in ['model.py','source.py','bridge.py','qualification.py','verify.py','apply.py','test_catalog.py','check_ui.cjs','.gitattributes']]
  sources += [CURATION,GUIDE,'tools/literature/catalog_source_integrity.py','tools/literature/run_mrmega_catalog_checks.py','tools/literature/curate_shapeit_uses.py','tools/literature/collect_omics_review.py']
  names=[EVIDENCE+'/'+n for n in ['CENSUS.json','SOURCE_AUDIT.json','qualified-intake-before.json','curation-intake.json','.gitattributes']]+[COVERAGE,COMPATIBILITY,DATA+'/qupath-catalog-validation.json']
  names += [folder+'/'+n for folder in [WORKFLOW,NATIVE] for n in ['VALIDATION.json','CONTROLS_LAYOUT.json','SHA256SUMS','ARTIFACTS.json','CENSUS.json','VERIFIER_BOUNDARIES.json','verifier-boundary-controls.zip']]
  source_pins={n:pin(ROOT/n) for n in sources};evidence_pins={n:pin(ROOT/n) for n in names}
  for n,raw in after.items():(ROOT/n).write_bytes(raw)
  proof=dict(status='PASS_ORIGINAL_RSTATIX072_CATALOG_ADMISSION_AND_SIX_SOURCE_ROLES',before_archive=pin(archive),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},
   source_pins=source_pins,evidence_pins=evidence_pins,**result,admitted_tool_ids=['rstatix'],added_software_names=0,scientific_sources_changed=False,scientific_verifiers_changed=False,previous_goal_turn_classification='progress')
  (ROOT/PROOF).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_CATALOG_ADMISSION',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
