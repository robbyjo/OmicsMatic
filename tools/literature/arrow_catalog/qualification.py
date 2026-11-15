# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay the qualified complete saved Arrow objects; run no new Parquet analysis."""
from pathlib import Path
import json,os,subprocess,sys,uuid

def qualification(root,runtime,wsl_distro=None):
    if not runtime:raise OSError('Exact permitted recipe-unpatched R Arrow 12.0.1 provider required; dependent verification skipped')
    actual=Path(os.fspath(root)).resolve()
    parent=actual/'build/literature-review/monocle-runtime';parent.mkdir(parents=True,exist_ok=True)
    out=parent/('arrow-catalog-R-replay-'+uuid.uuid4().hex)
    script=actual/'tools/literature/arrow1201/verify_checkpoint.py'
    if sys.platform=='linux':
        command=[sys.executable,'-B',str(script),'--runtime',str(runtime),'--out',str(out)]
    elif sys.platform=='win32' and wsl_distro:
        def linux(path):
            p=Path(path)
            if p.drive.lower()!='d:':raise OSError('Use explicit D: WSL routing, or run inside Linux for another layout')
            return '/mnt/d/'+p.as_posix()[3:]
        command=['wsl','-d',wsl_distro,'-u','root','--','/usr/bin/python3','-B',linux(script),
                 '--runtime',str(runtime),'--out',linux(out)]
    else:raise OSError('Complete Arrow replay requires Linux or an explicitly permitted WSL provider')
    with out.with_suffix('.stdout').open('xb') as stdout,out.with_suffix('.stderr').open('xb') as stderr:
        child=subprocess.run(command,cwd=actual,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
    if child.returncode==3:raise OSError('Original Arrow prerequisite unavailable; inspect '+str(out))
    if child.returncode:raise ValueError('Complete saved original Arrow replay failed; inspect '+str(out))
    result=json.loads((out/'COMPLETED.json').read_bytes())
    if (result['original_controls'],result['complete_returns'],result['original_failures_without_result'],result['bundle_members'])!=(13,10,3,984):
        raise ValueError('Complete original Arrow comparison population differs')
    if result['whole_condition_identity_controls']!=12 or result['complete_missing_column_difference']!='trace only; actual caller context retained':
        raise ValueError('Disclosed complete Arrow error-context comparison differs')
    if not result['all_frozen_members_checked'] or not result['no_condition_fields_deleted_or_coerced']:
        raise ValueError('Complete original evidence and unmodified conditions required')
    if result['new_family_credit'] or result['live_execution_context_identity_claimed'] or result['original_io_or_model_fitting_executed']:
        raise ValueError('Unsupported duplicate credit, live-context or analysis claim')
    return dict(status='PASS_COMPLETE_RECIPE_UNPATCHED_ARROW1201_CHECKPOINT_REPLAY',workflow=result,
                new_upstream_family_credit=1,native_family_credit_added=0,whole_goal_complete=False)
