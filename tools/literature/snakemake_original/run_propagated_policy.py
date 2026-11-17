#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Developer original API controller auditing per-process installation/propagation records."""
from pathlib import Path
import hashlib,json,os,sys,traceback
if not __debug__:raise RuntimeError('Propagation qualification assertions must remain enabled')

def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    if len(sys.argv)!=2:raise ValueError('Require one complete controller request JSON')
    request=json.loads(Path(sys.argv[1]).read_bytes());assert set(request)=={'schema_version','helpers','argv','installation_policy','receipt'} and type(request['schema_version']) is int and request['schema_version']==1
    helpers=Path(request['helpers']);sys.path.insert(0,str(helpers));os.environ['PYTHONPATH']=str(helpers);os.environ['PYTHONSAFEPATH']='1';os.environ['PYTHONDONTWRITEBYTECODE']='1';os.environ['OMICS_SNAKEMAKE_INSTALL_POLICY']=request['installation_policy']
    config_file=Path(request['installation_policy']);config=json.loads(config_file.read_bytes());config_pin=pin(config_file);directory=Path(config['records_directory']);events=Path(os.environ['OMICS_SNAKEMAKE_EVENTS']);receipt=Path(request['receipt']);assert config['logger']=='omicsmanaged' and config['run_id']==os.environ['OMICS_SNAKEMAKE_RUN_ID']
    before={p.relative_to(helpers).as_posix():pin(p) for p in helpers.rglob('*.py')};code=2;failure=None;api_called=False;success=False;records=[];integrity=[]
    try:
        from snakemake.cli import parse_args,args_to_api
        parser,args=parse_args(['--logger','omicsmanaged',*request['argv']]);api_called=True;success=args_to_api(args,parser);code=0 if success else 1
    except BaseException as error:failure=repr(error);traceback.print_exc();code=1
    try:
        from omicsmatic_policy_runtime import finalize_policy
        finalize_policy()
        stream_pids={int(p.name[7:-6]) for p in events.glob('events-*.jsonl')};closed_files=list(directory.glob('policy-*.json'));closed_files=[p for p in closed_files if p.name.count('.')==1];record_pids=set()
        for path in closed_files:
            value=json.loads(path.read_bytes());pid=value['pid'];assert path.name=='policy-'+str(pid)+'.json' and pid not in record_pids;record_pids.add(pid)
            assert value['schema_version']==1 and value['status']=='INSTALLATION_POLICY_CLOSED_AFTER_ORIGINAL_EXECUTION_NOT_ANALYSIS' and value['entered'] and value['closed'] and value['bindings_restored'] and value['run_id']==config['run_id'] and value['allow_install']==config['allow_install'] and value['config']==config_pin
            records.append(value)
        assert stream_pids<=record_pids and os.getpid() in stream_pids,'Every recorder process must close its installation policy'
        assert len(record_pids)-1==sum(len(r['spawn_arguments']) for r in records),'Every original local spawn must close a propagated child policy; original subprocess mode deliberately suppresses plugin log handlers'
        assert {p.name for p in directory.iterdir()}=={name for pid in record_pids for name in ['policy-'+str(pid)+'.json','policy-'+str(pid)+'.OPENED.json']},'Unexpected or incomplete installation-policy record census'
        for value in records:
            opened=json.loads((directory/('policy-'+str(value['pid'])+'.OPENED.json')).read_bytes())
            assert opened['pid']==value['pid'] and opened['parent_pid']==value['parent_pid'] and opened['run_id']==value['run_id'] and opened['config']==value['config'] and opened['allow_install']==value['allow_install'] and opened['local_executor_source_sha256']==value['local_executor_source_sha256']
            assert opened['arguments']==value['arguments'] and opened['executable']==value['executable'] and opened['working_directory']==value['working_directory']
        assert not list(directory.glob('*.FAILURE.json')) and not list(events.glob('*.FAILURE.json')) and pin(config_file)==config_pin
        assert before=={p.relative_to(helpers).as_posix():pin(p) for p in helpers.rglob('*.py')}
        for path in events.glob('events-*.jsonl'):
            rows=[json.loads(line) for line in path.read_text().splitlines()];assert rows[0]['record_type']=='collector_opened' and rows[-1]['record_type']=='collector_closed' and not rows[-1]['collector_failed']
        if any(r['blocked'] for r in records):code=3
    except BaseException as error:integrity.append(repr(error));traceback.print_exc();code=2
    write(receipt,dict(schema_version=1,status='ORIGINAL_API_WITH_PROPAGATED_INSTALLATION_POLICY_NOT_ANALYSIS',controller_pid=os.getpid(),exit_code=code,upstream_api_called=api_called,upstream_success=success,failure=failure,integrity_errors=integrity,policy_processes=records,configuration=config_pin,helpers_before=before,helpers_after={p.relative_to(helpers).as_posix():pin(p) for p in helpers.rglob('*.py')},user_analysis_admitted=False,new_catalog_credit=0))
    return code
if __name__=='__main__':raise SystemExit(main())
