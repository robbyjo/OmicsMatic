#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the unchanged original CLI through the new captured logger driver."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,uuid
if not __debug__:raise RuntimeError('Driver qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def run(command,root,env):
    start=time.monotonic()
    with (root/'outer.stdout').open('xb') as so,(root/'outer.stderr').open('xb') as se:
        p=subprocess.Popen(command,cwd=root,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
        try:p.wait(timeout=120)
        finally:
            try:os.killpg(p.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            p.wait()
    record=dict(command=command,exit_code=p.returncode,seconds=round(time.monotonic()-start,3));(root/'PROCESS.json').write_bytes(encoded(record));return record
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original-controls',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    try:
        rows=[]
        for case in ['standard','repeated','extended','failed-rule']:
            root=out/case;root.mkdir();work=root/'working';work.mkdir();original=a.original_controls/case
            for name in ['rules','reads','scripts']:shutil.copytree(original/name,work/name)
            for name in ['Snakefile','config.yaml']:shutil.copyfile(original/name,work/name)
            helpers=root/'helpers';shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1',helpers);events=root/'events';events.mkdir()
            argv=['--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','2','--scheduler','greedy','--forceall']
            if case=='extended':argv+=['--benchmark-extended']
            argv+=['--','results/summary.tsv'];request=dict(schema_version=1,run_id=str(uuid.uuid4()),events_directory=str(events),receipt=str(root/'DRIVER.json'),argv=argv);(root/'COMMAND.json').write_bytes(encoded(request))
            env=dict(os.environ,PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',LC_ALL='C.UTF-8',LANG='C.UTF-8',TZ='UTC',XDG_CACHE_HOME=str(root/'cache'))
            command=['/usr/bin/prlimit','--as=8589934592','--cpu=120','--fsize=67108864','--nofile=4096','--',sys.executable,'-I','-B',str(helpers/'driver.py'),'--command',str(root/'COMMAND.json')]
            result=run(command,root,env);receipt=json.loads((root/'DRIVER.json').read_bytes());assert receipt['exit_code']==result['exit_code'] and not receipt['collector_errors']
            records=[]
            for path in events.glob('*.jsonl'):
                lines=path.read_text().splitlines();data=[json.loads(line) for line in lines];assert data and data[0]['record_type']=='collector_opened' and data[-1]['record_type']=='collector_closed' and not data[-1]['collector_failed']
                assert [row['sequence'] for row in data]==list(range(1,len(data)+1));assert all(row['run_id']==request['run_id'] for row in data);records+=data
            source=[row['record'] for row in records if row['record_type']=='original_log_record'];event_counts={}
            for row in source:event_counts[str(row.get('event'))]=event_counts.get(str(row.get('event')),0)+1
            if case=='failed-rule':assert result['exit_code']!=0 and 'EXPECTED_SYNTHETIC_UPSTREAM_RULE_FAILURE' in (root/'outer.stderr').read_text()
            else:assert result['exit_code']==0 and (work/'results/summary.tsv').read_bytes()==(original/'results/summary.tsv').read_bytes()
            assert source and event_counts.get('workflow_started',0)==1
            rows.append(dict(case=case,process=result,source_event_counts=event_counts,source_records=len(source),streams=len(list(events.glob('*.jsonl'))),project_java_adapter_qualified=False));print(json.dumps(rows[-1]),flush=True)
        result=dict(status='PASS_ORIGINAL_CLI_PROJECT_LOGGER_DRIVER_CONTROLS_NOT_JAVA_ADAPTER',cases=rows,producer=pin(Path(__file__)),new_catalog_credit=0,whole_goal_complete=False);(out/'COMPLETED.json').write_bytes(encoded(result));return 0
    except Exception as error:(out/'FAILURE.json').write_bytes(encoded(dict(reason=repr(error),traceback=traceback.format_exc(),required_action='Retain original stdout/stderr, all event streams and the driver receipt. Resolve the source/protocol discrepancy and retry NEW output; do not infer task completion from an engine exit or collector-close event.')));raise
if __name__=='__main__':raise SystemExit(main())
