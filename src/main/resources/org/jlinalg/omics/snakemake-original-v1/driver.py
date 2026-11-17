#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Call the unchanged selected original CLI with a project-owned event recorder.

The CLI parser/API and logger-plugin interface are upstream. A driver receipt is
process/event evidence only; Java must separately audit inputs, runtime, job
events, complete expected outputs and the final bundle before admission.
"""
from pathlib import Path
from enum import Enum
import argparse,hashlib,importlib.metadata,json,os,platform,sys,traceback
if not __debug__:raise RuntimeError('Snakemake driver assertions must remain enabled')
HERE=Path(__file__).resolve().parent
PINS={'cli.py':'8e48111da41539e82ebfa54a41e9da26e72c3604be8415e7bf87f7e122018236','benchmark.py':'2d584d8aa2ad44e4f9b776d787a9454a31704a91fdd6fac00d1430cd066468d7'}
CONTROLLED=('snakefile','directory','targets','configfile','executor','cores','scheduler','retries','latency_wait','forceall','keep_incomplete','notemp','benchmark_extended','logger')
DISABLED={**dict.fromkeys(('dryrun','touch','list_target_rules','list_rules','print_compilation','filegraph','d3dag','unlock','conda_cleanup_envs','conda_create_envs_only','list_conda_envs','cleanup_shadow','container_cleanup_images','list_input_changes','list_params_changes','list_untracked','summary','detailed_summary','delete_all_output','delete_temp_output','use_conda','use_apptainer','use_envmodules','immediate_submit','all_temp'),False),**dict.fromkeys(('lint','dag','rulegraph','deploy_sources','containerize','report','reporter','generate_unit_tests','cleanup_metadata','list_changes','archive','batch','allowed_rules','cache','default_storage_provider','draft_notebook','edit_notebook','runtime_profile','precommand'),None),**{key:[] for key in ('until','omit_from','target_jobs','omit_flags','software_deployment_method')},'mode':'DEFAULT','default_storage_prefix':''}

class PolicyUnavailable(Exception):
    """The effective source invocation cannot satisfy the declared analysis mode."""

def recorded(value):
    if isinstance(value,Enum):return value.name
    if isinstance(value,Path):return str(value)
    if value is None or type(value) in (str,int,float,bool):return value
    if isinstance(value,(list,tuple)):return [recorded(item) for item in value]
    if isinstance(value,(set,frozenset)):return sorted((recorded(item) for item in value),key=lambda x:json.dumps(x,sort_keys=True))
    if isinstance(value,dict):return {str(key):recorded(item) for key,item in value.items()}
    # A nonstandard typed value in a disabled field (for example Batch) must
    # reject before the API. The parser's raw option provenance retains its value.
    return {'unqualified_type':type(value).__module__+'.'+type(value).__qualname__}

def effective_policy(args):
    # These are read from the selected parser *after* its workflow-profile reparse.
    return {key:recorded(getattr(args,key)) for key in (*CONTROLLED,*DISABLED)}

def validate_policy(policy,effective):
    if not isinstance(policy,dict) or set(policy)!={'schema_version','deployment','allow_install','expected'} or policy['schema_version']!=1 or type(policy['allow_install']) is not bool:raise ValueError('Malformed explicit driver execution policy')
    if policy['deployment']!='preinstalled':raise PolicyUnavailable('Managed deployment is not yet qualified; retain this skip and use a qualified permitted deployment. No preinstalled fallback.')
    expected=policy['expected']
    if not isinstance(expected,dict) or set(expected)!=set(CONTROLLED):raise ValueError('Incomplete explicit recipe/parser policy')
    for key,value in {**expected,**DISABLED}.items():
        if json.dumps(effective[key],sort_keys=True,allow_nan=False)!=json.dumps(value,sort_keys=True,allow_nan=False):
            raise PolicyUnavailable('Effective original parser setting '+key+'='+repr(effective[key])+' conflicts with captured analysis policy '+repr(value)+'. No original API/workflow was called. Remove the conflicting profile setting or request a separately qualified mode; retain this skip and retry NEW output.')

def option_sources(parser):
    # Preserve raw option provenance without serializing argparse Action objects
    # or unstable repr() addresses. Defaults remain defined by the pinned source.
    result={}
    for source,options in parser.get_source_to_settings_dict().items():
        if source=='defaults':continue
        result[source]={key:dict(destination=None if action is None else action.dest,value=recorded(value)) for key,(action,value) in options.items()}
    return result
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
    request=json.loads(args.command.read_bytes());assert set(request)=={'schema_version','run_id','events_directory','receipt','argv','policy'} and request['schema_version']==2
    argv=request['argv'];assert isinstance(argv,list) and all(isinstance(item,str) and '\0' not in item for item in argv)
    forbidden={'--dry-run','-n','--touch','-t','--delete-all-output','--delete-temp-output','--cleanup-metadata','--unlock','--logger','--list-rules','--summary','--detailed-summary'}
    if any(item.split('=',1)[0] in forbidden for item in argv):raise ValueError('Analysis command contains a report-only, destructive or caller-selected logger operation')
    events=Path(request['events_directory']);receipt=Path(request['receipt']);assert events.is_absolute() and receipt.is_absolute() and events.is_dir() and not receipt.exists()
    run_id=request['run_id'];assert isinstance(run_id,str) and len(run_id)==36
    os.environ['OMICS_SNAKEMAKE_EVENTS']=str(events);os.environ['OMICS_SNAKEMAKE_RUN_ID']=run_id
    # Only the captured, fingerprinted helper directory is added; -I excludes user paths.
    sys.path.insert(0,str(HERE));os.environ['PYTHONPATH']=str(HERE);os.environ['PYTHONSAFEPATH']='1';os.environ['PYTHONDONTWRITEBYTECODE']='1'
    before={str(p.relative_to(HERE)):sha(p) for p in HERE.rglob('*.py')}
    selected=health();code=1;failure=None;effective=None;sources=None;policy_status='NOT_CHECKED';api_called=False
    try:
        from snakemake.cli import parse_args,args_to_api
        upstream_parser,upstream_args=parse_args(['--logger','omicsmatic',*argv])
        effective=effective_policy(upstream_args);sources=option_sources(upstream_parser)
        validate_policy(request['policy'],effective);policy_status='PASS_EFFECTIVE_ORIGINAL_PARSER_POLICY'
        api_called=True
        code=0 if args_to_api(upstream_args,upstream_parser) else 1
    except PolicyUnavailable as error:
        failure=str(error);policy_status='SKIP3_EFFECTIVE_ORIGINAL_PARSER_POLICY';print('SKIP3: '+failure,file=sys.stderr);code=3
    except BaseException as error:
        failure=repr(error);traceback.print_exc();code=int(error.code) if isinstance(error,SystemExit) and isinstance(error.code,int) else 1
    finally:
        after={str(p.relative_to(HERE)):sha(p) for p in HERE.rglob('*.py')}
        errors=list(events.glob('*.FAILURE.json'))
        if before!=after or errors:code=2
        streams=sorted(events.glob('events-*.jsonl'))
        write(receipt,dict(status='ORIGINAL_CLI_RETURNED_NOT_ANALYSIS_ADMISSION',schema_version=2,exit_code=code,driver_pid=os.getpid(),run_id=run_id,argv=['--logger','omicsmatic',*argv],policy=request['policy'],effective_policy=effective,option_sources=sources,policy_status=policy_status,upstream_api_called=api_called,health=selected,helpers_before=before,helpers_after=after,collector_streams={p.name:dict(bytes=p.stat().st_size,sha256=sha(p)) for p in streams},collector_errors=[p.name for p in errors],failure=failure,whole_paper_reproduced=False))
    return code
if __name__=='__main__':raise SystemExit(main())
