# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Require both original profiles and fresh full frozen-object verification."""
from pathlib import Path
import json,subprocess,sys,uuid

def qualification(root,runtime2023,runtime2022,wsl_distro=None):
    if not runtime2023 or not runtime2022:raise OSError('Both exact permitted BIGCAT source-profile providers are required; dependent verification skipped')
    actual=Path(str(root)).resolve();parent=actual/'build/literature-review/monocle-runtime'
    parent.mkdir(parents=True,exist_ok=True)
    out=parent/('bigcat-catalog-replay-'+uuid.uuid4().hex)
    script=actual/'tools/literature/scrattchbigcat005_workflows/verify_checkpoint.py'
    if sys.platform=='linux':command=[sys.executable,'-B',str(script)]
    elif sys.platform=='win32' and wsl_distro:
        if actual.drive.lower()!='d:':raise OSError('Use explicit D: WSL routing or run inside permitted Linux')
        def linux(path):return '/mnt/d/'+Path(path).as_posix()[3:]
        command=['wsl','-d',wsl_distro,'-u','root','--','/usr/bin/python3','-B',linux(script)]
    else:raise OSError('Complete saved BIGCAT checks require Linux or an explicitly permitted WSL environment')
    routed=str(out) if sys.platform=='linux' else linux(out)
    command+=['--runtime2023',str(runtime2023),'--runtime2022',str(runtime2022),'--out',routed]
    with out.with_suffix('.stdout').open('xb') as stdout,out.with_suffix('.stderr').open('xb') as stderr:
        child=subprocess.run(command,cwd=actual,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
    if child.returncode==3:raise OSError('Original BIGCAT prerequisite unavailable; inspect '+str(out))
    if child.returncode:raise ValueError('Complete saved BIGCAT verification failed; inspect '+str(out))
    result=json.loads((out/'COMPLETED.json').read_bytes())
    if (result['original_controls'],result['complete_returns'],result['original_failures_without_result'])!=(25,19,6):raise ValueError('Complete original BIGCAT population differs')
    if (result['logical_evidence_files'],result['unique_frozen_payloads'],result['compared_fields'])!=(3335,1858,3198):
        raise ValueError('Complete frozen BIGCAT evidence or comparison population differs')
    if not result['all_frozen_members_checked'] or result['original_condition_fields_deleted_or_coerced']:
        raise ValueError('Full original evidence without deleted/coerced condition fields required')
    if result['live_R_object_identity_claimed'] or result['original_Bigcat_analysis_or_model_fitting_executed'] or result['new_family_credit']:
        raise ValueError('Unsupported live-object/model/duplicate-credit claim')
    return dict(status='PASS_COMPLETE_SAVED_BIGCAT_SOURCE_BOUND_WORKFLOW_REPLAY',workflow=result,
      new_upstream_family_credit=1,native_family_credit_added=0,whole_goal_complete=False)
