#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append source-anchored software records and expose the qualified distinct candidate."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from p040_catalog.model import *
from p040_catalog.verify import rescan_sources,candidate_qualification,ACTION
from batchelor129_catalog.verify import verify as previous
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;archive=ROOT/EVIDENCE/'catalog-before.zip';proof_path=ROOT/DATA/'p040-catalog-validation.json';compatibility=DATA+'/p040-reviews-shared-source-compatibility.json'
  if archive.exists() or proof_path.exists() or (ROOT/compatibility).exists():raise ValueError('Amendment already exists; retain evidence')
  prior=previous(ROOT)
  if (prior['papers'],prior['named_tools'],prior['observations'],prior['reviewed_uses'],prior['new_scoped_integrations'],prior['missing'])!=(250,407,2482,1055,103,245):raise ValueError('Unexpected predecessor')
  before={n:(ROOT/n).read_bytes() for n in META};papers=json.loads(before[PAPERS]);source=rescan_sources(ROOT,papers);candidate=candidate_qualification(ROOT);after=transition(ROOT,before);check_transition(ROOT,before,after)
  with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for n,raw in before.items():
    info=zipfile.ZipInfo(n,(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
  audit=dict(status='PASS_ALL250_PRIMARY_SOURCES_AND_LIVE_ORIGINAL_CANDIDATE_BEFORE_METADATA_MUTATION',source=source,candidate=candidate,old_admission={k:prior[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing']},scientific_sources_changed=False,original_analysis_executed=False,whole_goal_complete=False);(ROOT/EVIDENCE/'SOURCE_AUDIT.json').write_bytes(encoded(audit))
  reverse=inverse(before[REVIEWS],after[REVIEWS])
  if reverse_delta(after[REVIEWS],reverse)!=before[REVIEWS]:raise ValueError('Exact review append inverse differs')
  (ROOT/compatibility).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='p040-software-census',scope='Exactly24 appended individual software reviews; all1055 earlier reviews unchanged. No scientific source path is permitted.',paths=[reverse])))
  for n,raw in before.items():
   if (ROOT/n).read_bytes()!=raw:raise ValueError('Concurrent catalog edit: '+n)
  for n,raw in after.items():(ROOT/n).write_bytes(raw)
  source_names=['tools/literature/p040_catalog/'+n for n in ['model.py','bridge.py','verify.py','apply.py','test_catalog.py','.gitattributes']]+[REGISTRY,CURATION,GUIDE,'tools/literature/catalog_source_integrity.py','tools/literature/run_mrmega_catalog_checks.py','tools/literature/curate_shapeit_uses.py','tools/literature/collect_omics_review.py']
  evidence_names=[EVIDENCE+'/'+n for n in ['CENSUS.json','ADDITIONS.json','CYTOMAPPER_SOURCE.json','SOURCE_AUDIT.json','.gitattributes']]+[compatibility,PHENO+'/VALIDATION.json',PHENO+'/QUALIFICATION.json',PHENO+'/REPLAYS.json',PHENO+'/ARTIFACTS.json',PHENO+'/SHA256SUMS',DATA+'/batchelor129-catalog-validation.json']
  proof=dict(status='PASS_P040_SOFTWARE_CENSUS_AND_EXPLICIT_RPHENOGRAPH_CANDIDATE',before_archive=pin(archive),before_files={n:rawpin(raw) for n,raw in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins={n:pin(ROOT/n) for n in source_names},evidence_pins={n:pin(ROOT/n) for n in evidence_names},added_observations=186,added_reviews=24,added_names=22,unchanged_previous_observations=2482,unchanged_previous_reviews=1055,new_scoped_integration_credit=0,scientific_sources_changed=False,scientific_verifiers_changed=False,whole_goal_complete=False)
  proof_path.write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_PRIMARY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_P040_CATALOG_AMENDMENT',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(value));(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
