# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the unchanged source-bound component verifier in a fresh process, without module collisions."""
from pathlib import Path
import json,os,subprocess,sys,uuid

def qualification(root):
 actual=Path(os.fspath(root));data=actual/'docs/literature/scvelo024025-development';proof=json.loads((data/'VALIDATION.json').read_bytes())
 import hashlib
 for name,wanted in proof['sources'].items():
  path=root/name
  with path.open('rb') as f:raw=f.read()
  if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Qualified original scVelo source/evidence differs: '+name)
 out=actual/'build/literature-review/monocle-runtime'/('scvelo-component-catalog-check-'+uuid.uuid4().hex)
 command=[sys.executable,'-B',str(actual/'tools/literature/scvelo024025/verify.py'),'--out',str(out)]
 env={k:v for k,v in os.environ.items() if k not in ['PYTHONPATH','PYTHONHOME']};env.update(PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1')
 with out.with_suffix('.stdout').open('xb') as so,out.with_suffix('.stderr').open('xb') as se:child=subprocess.run(command,cwd=actual,env=env,stdout=so,stderr=se)
 if child.returncode==3:raise OSError('Original scVelo component prerequisite unavailable; retain '+str(out)+' and complete child stdout/stderr')
 if child.returncode:raise ValueError('Original scVelo component qualification failed; retain '+str(out)+' and complete child stdout/stderr')
 result=json.loads((out/'COMPLETED.json').read_bytes());science=result['scientific']
 if (science['complete_stages'],science['arrays'],science['elements'],science['result_files'],result['workflow_controls']['linux_workflow_controls'])!=(54,2124,25165036,2272,100) or result['new_tool_credit'] or result['whole_goal_complete'] or result['paper_reproduced']:raise ValueError('Incomplete original scVelo component population/scope')
 return result
