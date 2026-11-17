#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Developer controller for original parser/API under scoped installation permission; no analysis admission."""
from pathlib import Path
import hashlib,json,sys,traceback
if not __debug__:raise RuntimeError('Permission qualification assertions must remain enabled')
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from deployment_policy import InstallationPolicy,InstallationForbidden,InstallationIntegrityError

def main():
    if len(sys.argv)!=2:raise ValueError('Require one captured controller request JSON')
    request=json.loads(Path(sys.argv[1]).read_bytes());assert set(request)=={'schema_version','helpers','argv','allow_install','receipt'} and type(request['schema_version']) is int and request['schema_version']==1
    assert isinstance(request['argv'],list) and all(type(s) is str for s in request['argv'])
    helpers=Path(request['helpers']);assert helpers.is_dir();sys.path.insert(0,str(helpers));receipt=Path(request['receipt']);assert not receipt.exists()
    policy=InstallationPolicy(request['allow_install']);api_called=False;code=2;failure=None
    before={name:hashlib.sha256((HERE/name).read_bytes()).hexdigest() for name in ['run_installation_policy.py','deployment_policy.py']}
    try:
        from snakemake.cli import parse_args,args_to_api
        parser,args=parse_args(request['argv'])
        with policy:
            api_called=True;success=args_to_api(args,parser)
        code=3 if policy.blocked else 0 if success else 1
    except InstallationForbidden as error:failure=str(error);traceback.print_exc();code=3
    except BaseException as error:
        failure=repr(error);traceback.print_exc();code=2 if isinstance(error,InstallationIntegrityError) or not api_called else 3 if policy.blocked else 1
    after={name:hashlib.sha256((HERE/name).read_bytes()).hexdigest() for name in before}
    if before!=after or policy.entered and not policy.closed:code=2
    result=dict(schema_version=1,status='ORIGINAL_API_WITH_SCOPED_DEPLOYMENT_PERMISSION_NOT_ANALYSIS_ADMISSION',exit_code=code,allow_install=policy.allow_install,upstream_api_called=api_called,policy_entered=policy.entered,policy_closed=policy.closed,blocked=policy.blocked,records=policy.records,failure=failure,project_helpers_before=before,project_helpers_after=after,user_analysis_admitted=False,new_catalog_credit=0,policy_scope='Checks the pinned original deployer entrypoints. Trusted workflow code, external filesystem races and independent subprocesses are not an installation, network or security sandbox.')
    with receipt.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    return code
if __name__=='__main__':raise SystemExit(main())
