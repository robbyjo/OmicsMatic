#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the additive HOPS badge and strict unchanged predecessor/primary evidence."""
from pathlib import Path
import argparse,json,sys
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from hops_mepo_catalog.model import ROOT,DATA,WORKFLOW,META,pin,encoded,metadata,qualification
from hops_mepo_catalog.bridge import predecessor,prior_admission,protected
def verify(root=ROOT):
 proof=json.loads((root/DATA/'hops-mepo-adapter-validation.json').read_bytes())
 if proof['status']!='PASS_SCOPED_HOPS_MEPO_UPSTREAM_ADAPTER_ADMISSION' or proof['new_scoped_integration_credit']!=1 or proof['whole_tool_credit']!=0 or proof['whole_goal_complete']:raise ValueError('Unsupported HOPS admission scope')
 qualification(root);protected(root,proof['protected']);protected(root,proof['source_pins']);protected(root,proof['after_files'])
 if pin(root/WORKFLOW/'VALIDATION.json')!=proof['workflow_validation']:raise ValueError('HOPS qualification identity differs')
 view,review=predecessor(root);before={n:(view/n).read_bytes() for n in META};old=prior_admission(root,before)
 inventory=json.loads((root/DATA/'tool-inventory.json').read_bytes());summary=json.loads((root/DATA/'summary.json').read_bytes());current=next(t for t in inventory if t['id']=='hops');expected=dict(proof['before_entry']);expected.update(metadata(root))
 if current!=expected or current!=proof['after_entry'] or proof['changed_tool_ids']!=['hops']:raise ValueError('Scoped HOPS metadata differs')
 if summary['implementation_status_counts']!=proof['new_counts']:raise ValueError('Current HOPS counts differ')
 papers=json.loads((root/DATA/'papers.json').read_bytes());reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];role=json.loads((root/DATA/'hops-mepo-adapter-role-coverage.json').read_bytes())
 if len(papers)!=250 or sum(len(p['tools']) for p in papers)!=2479 or len(reviews)!=1052:raise ValueError('Primary observation/review population changed')
 use=next(t for p in papers if p['paper_id']=='P002' for t in p['tools'] if t['tool_id']=='hops')
 if pin(root/DATA/'hops-mepo-adapter-role-coverage.json')!=proof['role_coverage'] or role['source_observation']!=use or role['status']!='PASS_ONE_PRIMARY_HOPS_CANDIDATE_ASSESSMENT_ROLE_SCOPED' or role['paper_version_reported'] or role['paper_settings_recovered'] or role['manual_authentication_automated'] or role['historical_database_reconstructed'] or role['MALT_index_alignment_included']:raise ValueError('Primary HOPS role/boundary changed')
 for edition,value in proof['candidates'].items():
  if pin(root/WORKFLOW/('hops-mepo-jdk'+edition+'.jar'))!=value:raise ValueError('HOPS candidate differs')
 return dict(status='PASS_ONE_SCOPED_HOPS_ADMISSION_WITH_PRESERVED_PRIMARY_AND_SCIENTIFIC_EVIDENCE',papers=250,observations=2479,reviewed_uses=1052,new_scoped_integrations=101,missing=245,previous_admission=old,predecessor_review=review,primary_files_overridden=False,scientific_sources_overridden=False,scientific_verifiers_changed=False,new_scoped_integration_credit=1,whole_tool_credit=0,paper_wide_semantic_review_open=True,whole_goal_complete=False)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 try:r=verify();(out/'COMPLETED.json').write_bytes(encoded(r));print(r['status'],flush=True);return 0
 except Exception as e:
  import traceback
  (out/'FAILURE.json').write_bytes(encoded(dict(status='FAIL2_HOPS_CATALOG_ADMISSION',cause=repr(e),traceback=traceback.format_exc(),required_action='Retain diagnostics; review the exact named source/evidence or metadata transition. Never waive source hashes, alter primary records or grant credit to a failed gate.')));print('FAIL2_HOPS_CATALOG_ADMISSION',repr(e),flush=True);return 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete admission checks')
 raise SystemExit(main())
