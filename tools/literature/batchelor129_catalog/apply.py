#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply one exact Batchelor admission and append one reviewed Rphenograph use."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor129_catalog.model import *
from batchelor129_catalog.verify import fastmnn_qualification,validate_phenograph,ACTION
from batchelor120_catalog.verify import verify as previous,census,validate_census,qualification
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;proof_path=ROOT/DATA/'batchelor129-catalog-validation.json';archive=ROOT/EVIDENCE/'catalog-before.zip';compatibility=DATA+'/batchelor129-reviews-shared-source-compatibility.json';coverage=DATA+'/batchelor129-role-coverage.json'
  if any(p.exists() for p in [proof_path,archive,ROOT/REGISTRY,ROOT/compatibility,ROOT/coverage]):raise ValueError('Amendment already exists; never overwrite retained evidence')
  old=previous(ROOT)
  if (old['new_scoped_integrations'],old['missing'],old['observations'],old['reviewed_uses'])!=(102,245,2481,1054):raise ValueError('Unexpected admitted predecessor')
  fast=fastmnn_qualification(ROOT);retained=qualification(ROOT);before={n:(ROOT/n).read_bytes() for n in META};papers=json.loads(before[PAPERS]);use,review=additions(before);pheno=validate_phenograph(ROOT,papers,use,True);batch_scan=census(ROOT,papers);validate_census(batch_scan,papers)
  if batch_scan!=json.loads((ROOT/'docs/literature/batchelor-catalog-integration/CENSUS.json').read_bytes()):raise ValueError('Fresh Batchelor source census differs')
  current_entries=entries(ROOT,before);after=transition(before,current_entries);evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n',encoding='ascii')
  with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in before.items():
    info=zipfile.ZipInfo(name,(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
  source_audit=dict(status='PASS_ALL250_PRIMARY_SOURCES_AND_BOTH_DISTINCT_ORIGINAL_WORKFLOWS_BEFORE_MUTATION',primary_sources=250,batchelor_census_matched_papers=['P040','P051'],rphenograph_census_matched_papers=['P040'],fastmnn=fast,reducedMNN=retained,rphenograph=pheno,old_admission=dict(papers=old['papers'],named_tools=old['named_tools'],observations=old['observations'],reviewed_uses=old['reviewed_uses'],new_scoped_integrations=old['new_scoped_integrations'],missing=old['missing']),original_fitting_repeated=False,whole_goal_complete=False)
  (evidence/'SOURCE_AUDIT.json').write_bytes(encoded(source_audit));(ROOT/REGISTRY).write_bytes(encoded([REGISTRY_ROW]))
  observations={p['paper_id']:next(t for t in p['tools'] if t['tool_id']=='batchelor') for p in papers if any(t['tool_id']=='batchelor' for t in p['tools'])};batch=current_entries[0]
  roles=dict(status='PASS_TWO_SELECTED_BATCHELOR_SOURCE_ROLES',source_observations=observations,covered_selected_roles=batch['covered_selected_roles'],selected_source_candidates={'P040':'Original Batchelor1.29.0 fastMNN at165c212df5312de6da18dc0a2b78929beaffc812; literal paper method1.29 retained','P051':'Original Monocle1.3.7 align_cds/Batchelor1.20 reducedMNN; explicit function interpretation'},uncovered_reported_Batchelor_operations=[],paper_settings_recovered=False,paper_exact_patch_or_call_recovered=False,whole_tool_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1,whole_goal_complete=False)
  (ROOT/coverage).write_bytes(encoded(roles));reverse=inverse(before[REVIEWS],after[REVIEWS])
  if reverse_delta(after[REVIEWS],reverse)!=before[REVIEWS]:raise ValueError('Review append inverse differs')
  (ROOT/compatibility).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='rphenograph',scope='Exactly one appended source-reviewed Rphenograph use; all 1054 prior reviews unchanged. No scientific source path is permitted.',paths=[reverse])))
  for name,raw in before.items():
   if (ROOT/name).read_bytes()!=raw:raise ValueError('Concurrent catalog edit: '+name)
  for name,raw in after.items():(ROOT/name).write_bytes(raw)
  source_names=['tools/literature/batchelor129_catalog/'+n for n in ['model.py','bridge.py','verify.py','apply.py','test_catalog.py','.gitattributes']]+[REGISTRY,GUIDE,'tools/literature/catalog_source_integrity.py','tools/literature/run_mrmega_catalog_checks.py','tools/literature/curate_shapeit_uses.py']
  evidence_names=[EVIDENCE+'/'+n for n in ['SOURCE_AUDIT.json','.gitattributes']]+[coverage,compatibility,WORKFLOW+'/VALIDATION.json',WORKFLOW+'/QUALIFICATION.json',WORKFLOW+'/REPLAYS.json','docs/literature/phenograph-source-review/CENSUS.json','docs/literature/phenograph-source-review/SOURCE.json','docs/literature/omics-2023-2026/batchelor-catalog-validation.json']
  proof=dict(status='PASS_TWO_SELECTED_BATCHELOR_WORKFLOWS_AND_REVIEWED_RPHENOGRAPH_GAP',before_archive=pin(archive),before_files={n:rawpin(raw) for n,raw in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins={n:pin(ROOT/n) for n in source_names},evidence_pins={n:pin(ROOT/n) for n in evidence_names},new_scoped_integration_credit=1,whole_tool_credit=0,whole_goal_complete=False,unchanged_observations=2481,unchanged_reviews=1054,added_observations=1,added_reviews=1,admitted_tool_ids=['batchelor'],added_missing_tool_ids=['rphenograph'],scientific_sources_changed=False,scientific_verifiers_changed=False)
  proof_path.write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;value=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_CATALOG_AMENDMENT',cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_bytes(encoded(value));(out/'WARNINGS_FAILURE.md').write_text(value['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8')
  print(value['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/transition checks')
 raise SystemExit(main())
