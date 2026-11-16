#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Call the unchanged selected original CLI with a project-owned event recorder.

The CLI parser/API and logger-plugin interface are upstream. A driver receipt is
process/event evidence only; Java must separately audit inputs, runtime, job
events, complete expected outputs and the final bundle before admission.
"""
from pathlib import Path
import argparse,hashlib,importlib.metadata,json,os,platform,sys,traceback
if not __debug__:raise RuntimeError('Snakemake driver assertions must remain enabled')
HERE=Path(__file__).resolve().parent
PINS={'cli.py':'8e48111da41539e82ebfa54a41e9da26e72c3604be8415e7bf87f7e122018236','benchmark.py':'2d584d8aa2ad44e4f9b776d787a9454a31704a91fdd6fac00d1430cd066468d7'}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def health():
    if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Selected original execution requires permitted Linux x86-64 Python')
    import snakemake
    root=Path(snakemake.__file__).resolve().parent
    versions={name:importlib.metadata.version(name) for name in ['snakemake','snakemake-interface-common','snakemake-interface-logger-plugins','snakemake-interface-executor-plugins','PyYAML','psutil']}
    if versions['snakemake']!='9.27.1':raise RuntimeError('Exact selected original Snakemake 9.27.1 required; no edition substitution')
    for name,expected in PINS.items():
        if sha(root/name)!=expected:raise RuntimeError('Installed selected source changed: '+name)
    return dict(profile='linux-x86_64-snakemake-9.27.1',versions=versions,python=sys.version,executable=sys.executable,source_files={name:sha(root/name) for name in PINS},platform=platform.platform())
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--health',action='store_true');parser.add_argument('--command',type=Path);args=parser.parse_args()
    if args.health:
        if args.command is not None:raise ValueError('Use --health alone')
        print(json.dumps(dict(status='PASS_SELECTED_SOURCE_IMPORT_HEALTH_NOT_WORKFLOW_ADMISSION',**health())));return 0
    if args.command is None:raise ValueError('Explicit captured --command JSON required')
    if args.command.stat().st_size>4194304:raise ValueError('Driver command exceeds 4 MiB')
    request=json.loads(args.command.read_bytes());assert set(request)=={'schema_version','run_id','events_directory','receipt','argv'} and request['schema_version']==1
    argv=request['argv'];assert isinstance(argv,list) and all(isinstance(item,str) and '\0' not in item for item in argv)
    forbidden={'--dry-run','-n','--touch','-t','--delete-all-output','--delete-temp-output','--cleanup-metadata','--unlock','--logger','--list-rules','--summary','--detailed-summary'}
    if any(item.split('=',1)[0] in forbidden for item in argv):raise ValueError('Analysis command contains a report-only, destructive or caller-selected logger operation')
    events=Path(request['events_directory']);receipt=Path(request['receipt']);assert events.is_absolute() and receipt.is_absolute() and events.is_dir() and not receipt.exists()
    run_id=request['run_id'];assert isinstance(run_id,str) and len(run_id)==36
    os.environ['OMICS_SNAKEMAKE_EVENTS']=str(events);os.environ['OMICS_SNAKEMAKE_RUN_ID']=run_id
    # Only the captured, fingerprinted helper directory is added; -I excludes user paths.
    sys.path.insert(0,str(HERE));os.environ['PYTHONPATH']=str(HERE);os.environ['PYTHONSAFEPATH']='1';os.environ['PYTHONDONTWRITEBYTECODE']='1'
    before={str(p.relative_to(HERE)):sha(p) for p in HERE.rglob('*.py')}
    selected=health();code=1;failure=None
    try:
        from snakemake.cli import parse_args,args_to_api
        upstream_parser,upstream_args=parse_args(['--logger','omicsmatic',*argv])
        # Profiles may supply defaults; inspect the effective original-parser mode.
        if upstream_args.executor!='local' or upstream_args.dryrun or upstream_args.touch:raise ValueError('This adapter requires real local execution; remote/dry-run/touch modes are not admitted')
        code=0 if args_to_api(upstream_args,upstream_parser) else 1
    except BaseException as error:
        failure=repr(error);traceback.print_exc();code=int(error.code) if isinstance(error,SystemExit) and isinstance(error.code,int) else 1
    finally:
        after={str(p.relative_to(HERE)):sha(p) for p in HERE.rglob('*.py')}
        errors=list(events.glob('*.FAILURE.json'))
        if before!=after or errors:code=2
        streams=sorted(events.glob('events-*.jsonl'))
        write(receipt,dict(status='ORIGINAL_CLI_RETURNED_NOT_ANALYSIS_ADMISSION',schema_version=1,exit_code=code,driver_pid=os.getpid(),run_id=run_id,argv=['--logger','omicsmatic',*argv],health=selected,helpers_before=before,helpers_after=after,collector_streams={p.name:dict(bytes=p.stat().st_size,sha256=sha(p)) for p in streams},collector_errors=[p.name for p in errors],failure=failure,whole_paper_reproduced=False))
    return code
if __name__=='__main__':raise SystemExit(main())
