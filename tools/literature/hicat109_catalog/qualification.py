# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Require complete frozen HICAT evidence and all 26 fresh original scoring calls."""
from pathlib import Path
import json,subprocess,sys,uuid

def qualification(root,runtime,wsl_distro=None):
    if not runtime:raise OSError('Exact permitted private HICAT 1.0.9 provider required; dependent verification skipped')
    actual=Path(str(root)).resolve();parent=actual/'build/literature-review/monocle-runtime';parent.mkdir(parents=True,exist_ok=True)
    out=parent/('hicat109-catalog-replay-'+uuid.uuid4().hex)
    script=actual/'tools/literature/hicat109_validation/replay.py'
    if sys.platform=='linux':command=[sys.executable,'-B',str(script)];routed=str(out)
    elif sys.platform=='win32' and wsl_distro:
        if actual.drive.lower()!='d:':raise OSError('Use explicit D: WSL routing or run in permitted Linux')
        def linux(path):return '/mnt/d/'+Path(path).as_posix()[3:]
        command=['wsl','-d',wsl_distro,'-u','root','--','/usr/bin/python3','-B',linux(script)];routed=linux(out)
    else:raise OSError('Fresh original HICAT verification requires permitted Linux or explicit WSL')
    command+=['--runtime',str(runtime),'--out',routed]
    with out.with_suffix('.stdout').open('xb') as so,out.with_suffix('.stderr').open('xb') as se:
        child=subprocess.run(command,cwd=actual,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    if child.returncode==3:raise OSError('Original HICAT prerequisite unavailable; inspect '+str(out))
    if child.returncode:raise ValueError('Complete HICAT source/evidence/fresh original verification failed; inspect '+str(out))
    result=json.loads((out/'COMPLETED.json').read_bytes())
    if result['status']!='PASS_FULL_FROZEN_HICAT109_RECORDS_AND_26_FRESH_ORIGINAL_REPLAYS_WITH_NATIVE_PREDICATE':
        raise ValueError('Unsupported scoped HICAT qualification')
    if (result['logical_files'],result['original_controls'],result['native_literal_R_comparisons'],result['actual_original_scores'])!=(1337,26,17697,13824):
        raise ValueError('Complete source/evidence/control population required')
    if not all(result[k] for k in ['complete_fresh_original_objects_compared','complete_original_conditions_and_rng_compared']):
        raise ValueError('Whole original scientific objects, conditions and RNG must be compared')
    if any(result[k] for k in ['source_or_dependency_algorithms_patched','whole_tool_qualified','whole_paper_reproduced','new_family_credit','whole_goal_complete']):
        raise ValueError('Unsupported source/whole-tool/duplicate-credit claim')
    return dict(status='PASS_COMPLETE_HICAT109_FROZEN_RECORDS_AND_FRESH_ORIGINAL_CONTROL_REPLAY',workflow=result,
      new_upstream_family_credit=1,native_family_credit_added=0,whole_goal_complete=False)
