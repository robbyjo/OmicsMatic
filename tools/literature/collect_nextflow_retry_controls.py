# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Observe original retry attempts; recovered failures must remain auditable."""
import argparse,csv,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from collect_nextflow_runtime_controls import VERSIONS,WORKFLOW,EXPECTED,execute,pin,write


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime-base',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    assert __debug__ and os.uname().sysname=='Linux' and not a.out.exists();a.out.mkdir(parents=True)
    base=a.runtime_base;inputs=a.out/'inputs';inputs.mkdir()
    for name,text in [('a','>a\nACGT\nacgt\n'),('b','>b\nAAATTT\n'),('c','>c\nGCGC\ngcgcNN\n')]: (inputs/(name+'.fa')).write_text(text)
    java=base/'java11-private-1'/json.loads((base/'java11-download-1/download.json').read_bytes())['archive_root']
    results=[]
    for version,(size,digest) in VERSIONS.items():
        asset=base/'download-cache-1'/('nextflow-'+version+'-all');assert pin(asset)==dict(bytes=size,sha256=digest)
        family=a.out/version;family.mkdir()
        for mode in ['retry-once','retry-exhausted']:
            stage=family/mode;stage.mkdir();workflow=stage/'control.nf'
            text=WORKFLOW.replace("errorStrategy { params.ignore ? 'ignore' : 'terminate' }","errorStrategy 'retry'\n    maxRetries 1")
            assert text!=WORKFLOW
            text=text.replace("if(params.fail) 'exit 7'","if(task.attempt == 1) 'exit 7'" if mode=='retry-once' else "if(true) 'exit 7'")
            workflow.write_text(text)
            config=stage/'control.config';config.write_text("process.executor='local'\nprocess.cpus=1\nprocess.memory='64 MB'\nprocess.maxForks=2\nexecutor.cpus=2\nexecutor.memory='1 GB'\ntrace.fields='task_id,hash,name,status,exit,attempt,process,tag,workdir'\n")
            env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',NXF_HOME=str(base/'preflight-java11-1'/version/'nxf-home'),NXF_JAVA_HOME=str(java),NXF_OFFLINE='true',NXF_DISABLE_CHECK_LATEST='true',NXF_ANSI_LOG='false',NXF_OPTS='-Xms32m -Xmx512m -XX:ReservedCodeCacheSize=128m',NXF_PLUGINS_DEFAULT='false',NXF_ENABLE_SECRETS='false')
            command=['/bin/bash',str(asset),'-C',str(config),'-log',str(stage/'nextflow.log'),'run',str(workflow),'-ansi-log','false','-with-trace',str(stage/'trace.tsv'),'-work-dir',str(stage/'work'),'--inputs',str(inputs),'--outdir',str(stage/'published')]
            result=execute(command,env,stage,stage)
            rows=list(csv.DictReader((stage/'trace.tsv').open(),delimiter='\t')) if (stage/'trace.tsv').exists() else []
            result.update(version=version,mode=mode,rows=rows);write(stage/'result.json',result)
            assert not result['timed_out'] and rows,(version,mode,result)
            if mode=='retry-once':assert result['exit_code']==0 and (stage/'published/counts.tsv').read_bytes()==EXPECTED
            else:assert result['exit_code']!=0 and not (stage/'published/counts.tsv').exists()
            attempts=[row for row in rows if row['process']=='GATHER'];assert {row['attempt'] for row in attempts}=={'1','2'} and len(attempts)==2
            assert attempts[0]['status']=='FAILED' and attempts[1]['status']==('COMPLETED' if mode=='retry-once' else 'FAILED')
            result['control_expectation_passed']=True;write(stage/'result.json',result);results.append(result)
            print(json.dumps(dict(version=version,mode=mode,exit_code=result['exit_code'],task_rows=len(rows),attempts=attempts)),flush=True)
    write(a.out/'COMPLETED.json',dict(status='passed-six-original-Nextflow-retry-controls',cases=len(results),runs=results,collector_pin=pin(Path(__file__)),public_adapter_available=False))


if __name__=='__main__':main()
