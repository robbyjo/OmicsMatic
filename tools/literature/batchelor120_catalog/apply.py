#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply the exact source-audited Batchelor addition once; preserve its predecessor."""
from pathlib import Path
import argparse,json,subprocess,sys,zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor120_catalog.model import ROOT,DATA,EVIDENCE,WORKFLOW,META,PAPERS,REVIEWS,REGISTRY,REGISTRY_ROW,pin,encoded,entry,additions,transition
from batchelor120_catalog.verify import census,validate_census,qualification
from malt052_catalog.verify import verify as previous
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve()
 if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime child')
 out.mkdir(parents=True,exist_ok=False)
 proof_path=ROOT/DATA/'batchelor-catalog-validation.json';archive=ROOT/EVIDENCE/'catalog-before.zip'
 if any(p.exists() for p in [proof_path,archive,ROOT/REGISTRY]):raise ValueError('Amendment already exists; never overwrite evidence')
 old=previous(ROOT);qualification(ROOT)
 if old['new_scoped_integrations']!=102 or old['missing']!=244:raise ValueError('Unexpected admitted predecessor')
 before={n:(ROOT/n).read_bytes() for n in META};papers=json.loads(before[PAPERS]);scan=census(ROOT,papers);validate_census(scan,papers)
 author_path=ROOT/'build/literature-review/monocle-runtime/batchelor120-catalog-intake-1/p040-author-source';author=json.loads((author_path/'SOURCE.json').read_bytes())
 if author['commit']!='c8074a59e2fd6497e4e613eb2b2f9ea4f2a85e3d' or author['license'] is not None or author['executed'] or author['redistributed']:raise ValueError('Unexpected author-source review')
 for row in author['files']:
  if pin(author_path/'files'/row['path'])!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Author intake bytes differ')
 processing=author_path/'files/scripts/DataProcessing/R/01_Processing_and_QC.Rmd';annotation=author_path/'files/scripts/DataProcessing/R/02_Cell_annotation.Rmd'
 if '"batchelor"' not in processing.read_text(encoding='utf8') or annotation.stat().st_size!=0:raise ValueError('Author package/empty-notebook findings differ')
 evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=True)
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,raw in before.items():
   info=zipfile.ZipInfo(name,date_time=(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
 (evidence/'CENSUS.json').write_bytes(encoded(scan))
 author_review=dict(status='REVIEWED_AUTHOR_DEPENDENCY_WITH_UNRECOVERED_FASTMNN_CALL',paper_id='P040',pmcid='PMC13645638',doi='10.1038/s41586-026-10954-1',repository='https://github.com/pulmed/Spatial-proteogenomics-of-tumour-draining-lymph-nodes-',commit=author['commit'],license=None,processing_notebook=dict(path='scripts/DataProcessing/R/01_Processing_and_QC.Rmd',**pin(processing),package_import_line=28,matched_token='batchelor'),annotation_notebook=dict(path='scripts/DataProcessing/R/02_Cell_annotation.Rmd',**pin(annotation)),reviewed_text_files=author['files'],actual_fastMNN_call_recovered=False,exact_package_version_recovered=False,reported_method_version_literal='1.29',code_executed=False,code_redistributed=False,scope='Read-only review of linked author text files at the pinned current commit, not a recovered publication commit or execution transcript.')
 (evidence/'P040_AUTHOR_REVIEW.json').write_bytes(encoded(author_review));(ROOT/REGISTRY).write_bytes(encoded([REGISTRY_ROW]))
 current_entry=entry(ROOT);after=transition(before,current_entry);uses,added=additions(before)
 roles=dict(status='PASS_TWO_PRIMARY_BATCHELOR_ROLES_WITH_EXPLICIT_FASTMNN_GAP',source_observations=uses,appended_reviews=added,covered_selected_roles=current_entry['covered_selected_roles'],uncovered_roles=current_entry['uncovered_roles'],paper_function_and_settings_recovered=False,whole_tool_qualified=False,new_scoped_integration_credit=0,whole_goal_complete=False)
 (ROOT/DATA/'batchelor-role-coverage.json').write_bytes(encoded(roles))
 reverse=inverse(before[REVIEWS],after[REVIEWS]);reverse['path']=REVIEWS
 if reverse_delta(after[REVIEWS],reverse)!=before[REVIEWS]:raise ValueError('Exact append-only review inverse differs')
 compatibility=DATA+'/batchelor-reviews-shared-source-compatibility.json'
 (ROOT/compatibility).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='batchelor',scope='Exactly two appended reviewed uses; all1052 prior reviews remain unchanged. No scientific source path is allowed.',paths=[reverse])))
 for n,raw in after.items():
  if (ROOT/n).read_bytes()!=before[n]:raise ValueError('Concurrent catalog edit: '+n)
 # No mutation starts until all sources, candidates and predecessor bytes have passed.
 for n,raw in after.items():(ROOT/n).write_bytes(raw)
 source_names=['tools/literature/batchelor120_catalog/'+n for n in ['model.py','bridge.py','verify.py','apply.py','test_catalog.py','.gitattributes']]+[REGISTRY,'docs/literature/BATCHELOR_INTEGRATION.md','tools/literature/collect_omics_review.py','tools/literature/tool-registry.json']
 evidence_names=[EVIDENCE+'/'+n for n in ['CENSUS.json','P040_AUTHOR_REVIEW.json','.gitattributes']]+[DATA+'/batchelor-role-coverage.json',compatibility,WORKFLOW+'/VALIDATION.json',WORKFLOW+'/BATCHELOR_DISCOVERY.json']
 proof=dict(status='PASS_TWO_PRIMARY_BATCHELOR_ROLES_WITH_EXPLICIT_FASTMNN_GAP',before_archive=pin(archive),before_files={n:dict(bytes=len(raw),sha256=__import__('hashlib').sha256(raw).hexdigest()) for n,raw in before.items()},after_files={n:pin(ROOT/n) for n in META},source_pins={n:pin(ROOT/n) for n in source_names},evidence_pins={n:pin(ROOT/n) for n in evidence_names},new_scoped_integration_credit=0,whole_tool_credit=0,whole_goal_complete=False,unchanged_observations=2479,unchanged_reviews=1052,added_observations=2,added_reviews=2,added_main_article_supporting_pointers=1,covered_selected_roles=current_entry['covered_selected_roles'],uncovered_roles=current_entry['uncovered_roles'],scientific_sources_changed=False,scientific_verifiers_changed=False)
 proof_path.write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print('APPLIED_TWO_BATCHELOR_ROLES; no new integration credit; FastMNN gap retained',flush=True)

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable predecessor/source guards')
 main()
