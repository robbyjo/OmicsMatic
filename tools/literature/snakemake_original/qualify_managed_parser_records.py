#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable independent Java parser-policy controls on complete genuine and damaged source records."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,time
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['compiled','jar','source','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--java',required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py')
    compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());assert compiled['adapter_class_source']=='byte-pinned-jar' and pin(a.jar)=={k:compiled['baseline_jar'][k] for k in ['bytes','sha256']}
    for name,expected in compiled['classes'].items():assert pin(a.compiled/'classes'/name)==expected
    export=json.loads((a.source/'EXPORTED.json').read_bytes());assert set(export['files'])=={p.relative_to(a.source).as_posix() for p in a.source.rglob('*') if p.is_file() and p.name!='EXPORTED.json'}
    for name,expected in export['files'].items():assert pin(a.source/name)==expected,name
    original=copy.deepcopy(export);classpath=str(a.compiled.resolve()/'classes')+os.pathsep+str(a.jar.resolve());rows=[];genuine={}
    def envelope(case):
        command=json.loads((case/'COMMAND.json').read_bytes());controller=json.loads((case/'CONTROLLER.json').read_bytes());expected=command['execution_policy']['expected'];return dict(policy=command['execution_policy'],controller=controller,allow_install=False,working=expected['directory'],conda=expected['conda_prefix'],images=expected['apptainer_prefix'])
    def run(name,request,accepted):
        case=out/name;case.mkdir();file=case/'REQUEST.json';write(file,request);cmd=[a.java,'-cp',classpath,compiled['probe'],'managed-parser-policy',str(file)];start=time.monotonic();env=os.environ.copy();env['PATH']='';env['PYTHONPATH']='';env['PYTHONNOUSERSITE']='1'
        with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,env=env,timeout=60)
        row=dict(case=name,command=cmd,exit_code=r.returncode,expected_accept=accepted,seconds=round(time.monotonic()-start,3),request=pin(file));write(case/'PROCESS.json',row);rows.append(row);assert (r.returncode==0)==accepted,(name,'Inspect retained portable reader diagnostics')
    for row in json.loads((a.source/'COMPLETED.json').read_bytes())['cases']:
        if row['scenario'].startswith('legacy-'):continue
        request=envelope(a.source/'cases'/row['case']);genuine[row['case']]=request;run('source-'+row['case'],request,True)
    good=genuine['plain-conda'];skip=genuine['until-conda'];run('valid-independent-json-reencoding',copy.deepcopy(good),True)
    mutations={
        'partial-dag':lambda r:r['controller']['effective_policy'].__setitem__('until',['make']),
        'other-deployment':lambda r:r['controller']['effective_policy'].__setitem__('software_deployment_method',['APPTAINER']),
        'other-conda-prefix':lambda r:r['controller']['effective_policy'].__setitem__('conda_prefix','/other/prefix'),
        'other-image-prefix':lambda r:r['controller']['effective_policy'].__setitem__('apptainer_prefix','/other/images'),
        'extra-runtime-args':lambda r:r['controller']['effective_policy'].__setitem__('apptainer_args','--writable'),
        'other-frontend':lambda r:r['controller']['effective_policy'].__setitem__('conda_frontend','mamba'),
        'envmodules':lambda r:r['controller']['effective_policy'].__setitem__('use_envmodules',True),
        'dryrun':lambda r:r['controller']['effective_policy'].__setitem__('dryrun',True),
        'other-cwd':lambda r:r['controller']['effective_policy'].__setitem__('directory','/other/cwd'),
        'removed-config':lambda r:r['controller']['effective_policy'].__setitem__('configfile',[]),
        'permission-change':lambda r:r['policy'].__setitem__('allow_install',True),
        'removed-setting':lambda r:r['controller']['effective_policy'].pop('use_envmodules'),
        'removed-provenance':lambda r:r['controller'].__setitem__('option_sources',{}),
        'false-complete-dag-contract':lambda r:r['policy']['expected'].__setitem__('forceall',False),
        'missing-api-call':lambda r:r['controller'].__setitem__('upstream_api_called',False),
        'changed-captured-expected':lambda r:r['controller']['execution_policy']['expected'].__setitem__('targets',['partial']),
    }
    for name,mutate in mutations.items():request=copy.deepcopy(good);mutate(request);run(name,request,False)
    for name,mutate in {
        'skip-api-was-called':lambda r:r['controller'].__setitem__('upstream_api_called',True),
        'skip-without-contradiction':lambda r:r['controller']['effective_policy'].__setitem__('until',[]),
        'skip-without-recovery':lambda r:r['controller'].__setitem__('failure','Skipped'),
        'skip-false-success':lambda r:r['controller'].__setitem__('upstream_success',True),
        'skip-nonboolean-api':lambda r:r['controller'].__setitem__('upstream_api_called',0),
    }.items():request=copy.deepcopy(skip);mutate(request);run(name,request,False)
    assert json.loads((a.source/'EXPORTED.json').read_bytes())==original
    for name,expected in original['files'].items():assert pin(a.source/name)==expected,name
    write(out/'COMPLETED.json',dict(status='PASS_PORTABLE_GENUINE_REENCODED_AND_DAMAGED_MANAGED_PARSER_POLICY_RECORDS',cases=rows,genuine_source_cases=len(genuine),negative_cases=len(mutations)+5,engine_executed=False,path_empty=True,jar=pin(a.jar),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_PORTABLE_MANAGED_PARSER_RECORDS '+str(len(rows)),flush=True)
if __name__=='__main__':main()
