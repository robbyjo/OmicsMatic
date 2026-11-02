# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original Nextflow DAG/failure/resume controls; this is not a user adapter."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time

VERSIONS={
    '21.04.1':(56589130,'566deaf7533a2484d09f6520c677b31eff983d59b82c0816f9fe36b2886c768d'),
    '22.10.6':(89968128,'cde62c2b15919f3af45ba083fb28e8017c0237f01b3793f81e11f5a1fb67ae36'),
    '24.04.4':(108969144,'9077cfb151d4bc8682f09a65a77f45346bf34dac5931e371dba0d51bf13a5076'),
}
WORKFLOW=r'''
nextflow.enable.dsl=2
params.inputs=null
params.outdir=null
params.fail=false
params.ignore=false
params.empty=false

process COUNTS {
    tag { sample }
    input:
    tuple val(sample),path(sequence)
    output:
    tuple val(sample),path("${sample}.tsv")
    script:
    """
    awk 'BEGIN{n=0;gc=0} !/^>/{s=toupper(\$0);n+=length(s);gsub(/[^GC]/,"",s);gc+=length(s)} END{printf "%s\\t%d\\t%d\\n","$sample",n,gc}' "$sequence" > "${sample}.tsv"
    """
}

process GATHER {
    publishDir params.outdir,mode:'copy'
    errorStrategy { params.ignore ? 'ignore' : 'terminate' }
    input:
    path(tables)
    output:
    path 'counts.tsv'
    script:
    if(params.fail) 'exit 7'
    else 'cat *.tsv | LC_ALL=C sort > counts.tsv'
}

workflow {
    if(!params.empty){
        samples=Channel.of(tuple('a',file(params.inputs+'/a.fa',checkIfExists:true)),tuple('b',file(params.inputs+'/b.fa',checkIfExists:true)),tuple('c',file(params.inputs+'/c.fa',checkIfExists:true)))
        COUNTS(samples)
        GATHER(COUNTS.out.map{sample,table -> table}.collect())
    }
}
'''
EXPECTED=b'a\t8\t4\nb\t6\t0\nc\t10\t8\n'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def execute(command,env,cwd,stage):
    started=time.monotonic();timed_out=False
    with (stage/'stdout.log').open('xb') as out,(stage/'stderr.log').open('xb') as err:
        process=subprocess.Popen(['/usr/bin/prlimit','--as=8589934592','--cpu=120','--fsize=536870912','--nofile=4096','--',*command],env=env,cwd=cwd,stdout=out,stderr=err,start_new_session=True)
        write(stage/'process.json',dict(pid=process.pid,group=process.pid,command=command))
        try:code=process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            timed_out=True;os.killpg(process.pid,signal.SIGTERM)
            try:process.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
            code=process.returncode
    return dict(exit_code=code,timed_out=timed_out,elapsed_seconds=time.monotonic()-started)


def main():
    if not __debug__:raise RuntimeError('Never disable original-control assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--preflight',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    assert os.uname().sysname=='Linux' and not args.out.exists();args.out.mkdir(parents=True)
    controls=args.out/'inputs';controls.mkdir()
    for name,content in [('a','>a\nACGT\nacgt\n'),('b','>b\nAAATTT\n'),('c','>c\nGCGC\ngcgcNN\n')]:
        (controls/(name+'.fa')).write_text(content,encoding='ascii',newline='\n')
    workflow=args.out/'control.nf';workflow.write_text(WORKFLOW,encoding='utf-8',newline='\n')
    config=args.out/'control.config';config.write_text("process.executor='local'\nprocess.cpus=1\nprocess.memory='64 MB'\nprocess.maxForks=2\nexecutor.cpus=2\nexecutor.memory='1 GB'\n",encoding='utf-8',newline='\n')
    results=[]
    for version,(size,digest) in VERSIONS.items():
        asset=args.cache/('nextflow-'+version+'-all');assert pin(asset)==dict(bytes=size,sha256=digest)
        original=args.preflight/version;assert json.loads((original/'result.json').read_text())['exit_code']==0
        env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',NXF_HOME=str(original/'nxf-home'),NXF_JAVA_HOME=str(args.java_home),NXF_OFFLINE='true',NXF_DISABLE_CHECK_LATEST='true',NXF_ANSI_LOG='false',NXF_OPTS='-Xms32m -Xmx512m -XX:ReservedCodeCacheSize=128m',NXF_PLUGINS_DEFAULT='false',NXF_ENABLE_SECRETS='false')
        family=args.out/version;family.mkdir();shared=family/'success';shared.mkdir()
        for mode in ['success','resume','task-failure','ignored-task-failure','empty-workflow']:
            stage=shared if mode=='success' else family/mode
            if mode!='success':stage.mkdir()
            launch=shared if mode=='resume' else stage
            trace=stage/'trace.tsv';published=stage/'published'
            command=['/bin/bash',str(asset),'-C',str(config),'-log',str(stage/'nextflow.log'),'run',str(workflow),'-ansi-log','false','-with-trace',str(trace),'-work-dir',str(launch/'work'),'--inputs',str(controls),'--outdir',str(published)]
            if mode=='resume':
                # Preserve the exact first command's parameter values and cache;
                # otherwise Nextflow correctly reruns tasks instead of caching.
                command[command.index('--outdir')+1]=str(shared/'published');command+=['-resume']
            if mode in {'task-failure','ignored-task-failure'}:command+=['--fail','true']
            if mode=='ignored-task-failure':command+=['--ignore','true']
            if mode=='empty-workflow':command+=['--empty','true']
            result=execute(command,env,launch,stage);assert not result['timed_out'],(version,mode,result)
            rows=list(csv.DictReader(trace.open(encoding='utf-8'),delimiter='\t')) if trace.exists() else []
            statuses=[r['status'] for r in rows]
            result.update(version=version,mode=mode,task_count=len(rows),statuses=statuses,command=command)
            write(stage/'result.json',dict(result,control_expectation_passed=False))
            if mode=='success':assert result['exit_code']==0 and len(rows)==4 and statuses.count('COMPLETED')==4 and (published/'counts.tsv').read_bytes()==EXPECTED
            elif mode=='resume':assert result['exit_code']==0 and len(rows)==4 and statuses.count('CACHED')==4 and (shared/'published/counts.tsv').read_bytes()==EXPECTED
            elif mode=='task-failure':assert result['exit_code']!=0 and 'FAILED' in statuses and not published.exists()
            elif mode=='ignored-task-failure':assert result['exit_code']==0 and 'FAILED' in statuses and not published.exists()
            else:assert result['exit_code']==0 and not rows and not published.exists()
            result['control_expectation_passed']=True;write(stage/'result.json',result);results.append(result);print(json.dumps({k:result[k] for k in ['version','mode','exit_code','task_count','statuses','control_expectation_passed']}),flush=True)
        assert pin(asset)==dict(bytes=size,sha256=digest)
    result=dict(status='passed-original-Nextflow-DAG-failure-resume-controls',engines=list(VERSIONS),cases=len(results),runs=results,expected_GC_count_output=EXPECTED.decode(),ignored_task_failure_does_not_establish_success=True,empty_workflow_does_not_establish_a_biological_result=True,scientific_paper_reproduced=False,public_adapter_available=False,collector_pin=pin(Path(__file__)))
    write(args.out/'COMPLETED.json',result)


if __name__=='__main__':main()
