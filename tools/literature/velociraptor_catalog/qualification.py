# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Invoke the live source-bound complete original workflow verifier in a fresh process."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,uuid

def qualification(root):
 actual=Path(os.fspath(root));out=actual/'build/literature-review/monocle-runtime'/('velociraptor-component-catalog-check-'+uuid.uuid4().hex)
 proof=json.loads((root/'docs/literature/velociraptor120-workflow-development/VALIDATION.json').read_bytes())
 for name,wanted in proof['sources'].items():
  with (root/name).open('rb') as stream:raw=stream.read()
  if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Qualified velociraptor source/example differs before execution: '+name)
 command=[sys.executable,'-B',str(actual/'tools/literature/verify_velociraptor120.py'),'--out',str(out)]
 env={k:v for k,v in os.environ.items() if k not in ['PYTHONPATH','PYTHONHOME']};env.update(PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1')
 with out.with_suffix('.stdout').open('xb') as so,out.with_suffix('.stderr').open('xb') as se:child=subprocess.run(command,cwd=actual,env=env,stdout=so,stderr=se)
 if child.returncode==3:raise OSError('Original workflow prerequisite unavailable; retain '+str(out)+' and complete console')
 if child.returncode:raise ValueError('Complete original workflow evidence failed; retain '+str(out)+' and complete console')
 result=json.loads((out/'COMPLETED.json').read_bytes())
 if tuple(result[k] for k in ['complete_numeric_cases','nodes','atomic_payloads','atomic_payload_bytes','complete_figure_cases','complete_graph_RDS_replays','matching_original_figure_error_cases','executed_workflow_controls','complete_public_examples'])!=(13,3732,2617,2457351,4,8,2,36,6) or result['new_family_credit'] or result['whole_tool_qualified'] or result['whole_goal_complete']:raise ValueError('Incomplete original component scope')
 return result
