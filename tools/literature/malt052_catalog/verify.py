#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify exactly one MALT workflow badge and all preserved primary/scientific evidence."""
from pathlib import Path
import argparse,json,sys
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from malt052_catalog.model import ROOT,DATA,WORKFLOW,pin,encoded,metadata,qualification
from malt052_catalog.bridge import predecessor,prior_admission,protected
def verify(root=ROOT):
 proof=json.loads((root/DATA/'malt052-adapter-validation.json').read_bytes())
 if proof['status']!='PASS_SCOPED_MALT052_BUILD_BLASTN_UPSTREAM_ADAPTER_ADMISSION' or proof['new_scoped_integration_credit']!=1 or proof['whole_tool_credit']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported scoped MALT admission')
 qualification(root);protected(root,proof['protected']);protected(root,proof['source_pins']);protected(root,proof['after_files'])
 if pin(root/WORKFLOW/'VALIDATION.json')!=proof['workflow_validation']:raise ValueError('MALT workflow qualification identity differs')
 view,review=predecessor(root);before={n:(view/n).read_bytes() for n in proof['after_files']};old=prior_admission(root,before)
 inventory=json.loads((root/DATA/'tool-inventory.json').read_bytes());summary=json.loads((root/DATA/'summary.json').read_bytes());current=next(t for t in inventory if t['id']=='malt');expected=dict(proof['before_entry']);expected.update(metadata(root))
 if current!=expected or current!=proof['after_entry'] or proof['changed_tool_ids']!=['malt'] or summary['implementation_status_counts']!=proof['new_counts']:raise ValueError('Scoped MALT entry/count transition differs')
 papers=json.loads((root/DATA/'papers.json').read_bytes());reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];role=json.loads((root/DATA/'malt052-adapter-role-coverage.json').read_bytes())
 if len(papers)!=250 or sum(len(p['tools']) for p in papers)!=2479 or len(reviews)!=1052:raise ValueError('Primary observation/review population changed')
 use=next(t for p in papers if p['paper_id']=='P002' for t in p['tools'] if t['tool_id']=='malt')
 if pin(root/DATA/'malt052-adapter-role-coverage.json')!=proof['role_coverage'] or role['source_observation']!=use or role['status']!='PASS_TWO_PRIMARY_MALT_DNA_SCREENING_ROLES_SCOPED' or role['main_pipeline_version_recovered'] or role['supplement_reported_custom_version']!='0.5.2' or role['original_accession_mapping_qualified'] or role['historical_database_reconstructed'] or role['paper_resource_request_executed'] or role['whole_paper_reproduced']:raise ValueError('MALT primary role/method boundary differs')
 for edition,value in proof['candidates'].items():
  if pin(root/WORKFLOW/('malt052-workflow-jdk'+edition+'.jar'))!=value:raise ValueError('MALT candidate differs')
 return dict(status='PASS_ONE_SCOPED_MALT052_ADMISSION_WITH_PRESERVED_PRIMARY_AND_SCIENTIFIC_EVIDENCE',papers=250,observations=2479,reviewed_uses=1052,new_scoped_integrations=102,missing=244,previous_admission=old,predecessor_review=review,primary_files_overridden=False,scientific_sources_overridden=False,scientific_verifiers_changed=False,new_scoped_integration_credit=1,whole_tool_credit=0,paper_wide_semantic_review_open=True,whole_goal_complete=False)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 try:r=verify();(out/'COMPLETED.json').write_bytes(encoded(r));print(r['status'],flush=True);return 0
 except Exception as e:
  import traceback
  (out/'FAILURE.json').write_bytes(encoded(dict(status='FAIL2_MALT052_CATALOG_ADMISSION',cause=repr(e),traceback=traceback.format_exc(),required_action='Retain diagnostics; resolve the exact named source/evidence or derived-metadata transition. Never waive scientific hashes, alter primary records or grant a failed gate credit.')));print('FAIL2_MALT052_CATALOG_ADMISSION',repr(e),flush=True);return 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete admission checks')
 raise SystemExit(main())
