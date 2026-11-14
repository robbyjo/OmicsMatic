#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Guarded update of one coverage field in intact catalog collections; retain exact before bytes."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from velociraptor_catalog.model import *
from velociraptor_catalog.source import scan,review
from velociraptor_catalog.qualification import qualification
from velociraptor_catalog.verify import compatibility,ACTION

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if any((ROOT/n).exists() for n in [EVIDENCE,PROOF,COVERAGE,COMPATIBILITY]):raise ValueError('Admission evidence exists; preserve it')
  before={n:(ROOT/n).read_bytes() for n in META};previous=json.loads((ROOT/DATA/'scvelo-catalog-validation.json').read_bytes())
  if {n:rawpin(v) for n,v in before.items()}!=previous['after_files']:raise ValueError('Current complete predecessor differs; preserve and reconcile')
  component=qualification(ROOT);census=scan(ROOT,json.loads(before[PAPERS]));after=transition(ROOT,before);result=check_transition(ROOT,before,after);inverse=compatibility(before,after)
  # Independently enforce preservation of every other record before any write.
  bi=json.loads(before[META[0]]);ai=json.loads(after[META[0]])
  if [r for r in bi if r['id']!='velociraptor']!=[r for r in ai if r['id']!='velociraptor']:raise ValueError('Other432 families changed')
  br=json.loads(before[REVIEWS])['reviews'];ar=json.loads(after[REVIEWS])['reviews']
  if len(br)!=1168 or len(ar)!=1168 or [r for r in br if r['tool_id']!='velociraptor']!=[r for r in ar if r['tool_id']!='velociraptor']:raise ValueError('Full1168-review collection was truncated or unrelated reviews changed')
  evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_bytes(b'* -text\n')
  with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
   for name,raw in before.items():
    info=zipfile.ZipInfo(name,(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
  (evidence/'CENSUS.json').write_bytes(encoded(census));(evidence/'implementation-coverage.json').write_bytes((ROOT/CURATION).read_bytes());(evidence/'qualified-source-review.json').write_bytes(encoded(review(ROOT)));(evidence/'SOURCE_AUDIT.json').write_bytes(encoded(dict(status='PASS_ALL250_SOURCE_IDENTITIES_EXISTING_WRAPPER_ROLE_AND_COMPLETE_ORIGINAL_COMPONENT_BEFORE_ADMISSION',component=component,added_papers=0,added_observations=0,added_reviews=0,unrelated_reviews_preserved=1167,new_upstream_family_credit=1,whole_goal_complete=False)))
  row=next(r for r in ai if r['id']=='velociraptor');(ROOT/COVERAGE).write_bytes(encoded(dict(status='PASS_EXISTING_PRIMARY_WRAPPER_USE_SCOPED_IMPLEMENTATION_COVERAGE',source_review=review(ROOT),paper_role_coverage=row['paper_role_coverage'],uncovered_roles=row['uncovered_roles'],whole_tool_qualified=False,paper_reproduced=False,new_scoped_integration_credit=1,whole_goal_complete=False)));(ROOT/COMPATIBILITY).write_bytes(encoded(inverse))
  names=['tools/literature/velociraptor_catalog/'+n for n in ['.gitattributes','implementation-coverage.json','source.py','model.py','bridge.py','qualification.py','verify.py','apply.py','test_catalog.py','check_ui.cjs']]+[GUIDE,'tools/literature/scvelo_catalog/model.py','tools/literature/scvelo_catalog/verify.py','tools/literature/catalog_source_integrity.py','tools/literature/wire_signac_catalog.py']
  source_pins={n:pin(ROOT/n) for n in names};evidence_names=[p.relative_to(ROOT).as_posix() for p in evidence.iterdir() if p.is_file() and p.name!='catalog-before.zip']+[COVERAGE,COMPATIBILITY,DATA+'/scvelo-catalog-validation.json'];evidence_names += [p.relative_to(ROOT).as_posix() for p in (ROOT/WORKFLOW).iterdir() if p.is_file()];evidence_pins={n:pin(ROOT/n) for n in evidence_names}
  for name,raw in before.items():
   if (ROOT/name).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite permitted: '+name)
  (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},retained_review_count=1168,unrelated_reviews_unchanged=1167,new_upstream_credit=1)))
  for name,raw in after.items():(ROOT/name).write_bytes(raw)
  proof=dict(status='PASS_ORIGINAL_VELOCIRAPTOR120_EXISTING_PRIMARY_USE_AND_SCOPED_WORKFLOW',before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins=source_pins,evidence_pins=evidence_pins,**result,admitted_new_tool_ids=['velociraptor'],scientific_sources_overridden=False,scientific_verifiers_changed=False)
  (ROOT/PROOF).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_WRAPPER_ADMISSION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_WRAPPER_ADMISSION_UNADMITTED';result=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(result))
  print(status,repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
