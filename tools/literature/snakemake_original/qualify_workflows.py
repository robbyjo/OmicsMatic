#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run original source controls through captured Java execution, without final admission."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,tempfile,time,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def tree(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def recipe(work,case):
    basic=case in ['standard','repeated','extended','failed-rule']
    result=dict(schema_version=1,engine_profile='linux-x86_64-snakemake-9.27.1',workflow=dict(path=str(work),snakefile='Snakefile',configfiles=['config.yaml'] if basic else [],targets=['results/summary.tsv'] if basic else ['result.txt']),resources={},parameters={},environment={},required_tools=[dict(name='cat',path='/usr/bin/cat')] if case=='pipe' else [],outputs=[dict(root='workflow',path='results/summary.tsv' if basic else 'result.txt',kind='file',allow_empty=False,comparison='exact')],execution=dict(cores=2,scheduler='greedy',retries=1 if case=='retry' else 0,latency_wait_seconds=1,notemp=False,benchmark_extended=case=='extended',deployment='preinstalled',allow_install=False,require_jobs=True),limits=dict(capture_files=5000,capture_bytes=268435456,file_bytes=67108864,workspace_bytes=1073741824,wall_seconds=120,address_space_bytes=8589934592,cpu_seconds=120,output_file_bytes=67108864,open_files=4096,processes=16,threads=128,sampled_rss_bytes=2147483648,poll_millis=100,metadata_bytes=4194304,event_rows=100000,log_bytes=67108864))
    if basic:result['outputs'] += [dict(root='workflow',path='benchmarks/'+name,kind='file',allow_empty=False,comparison='benchmark-measurement') for name in ['count-alpha.tsv','count-beta.tsv','gather.tsv']]
    return result
def config_fixture(stage,work):
    (work/'rules').mkdir();(work/'scripts').mkdir();data=stage/'data';data.mkdir();(data/'values.txt').write_text('2\n3\n',encoding='utf-8',newline='\n')
    (work/'Snakefile').write_text('configfile: "base.yaml"\ninclude: "rules/make.smk"\nrule all:\n    input: config["destination"]\n',encoding='utf-8',newline='\n')
    (work/'base.yaml').write_text('base_flag: true\nnested:\n  retained: from-workflow\n  override: default\n',encoding='utf-8',newline='\n')
    (work/'extra.json').write_text('{"nested":{"extra":"from-extra","override":"extra"}}\n',encoding='utf-8',newline='\n')
    (work/'rules/make.smk').write_text('rule make:\n    input: config["input"]\n    output: config["destination"]\n    script: "../scripts/sum.py"\n',encoding='utf-8',newline='\n')
    (work/'scripts/sum.py').write_text('from pathlib import Path\nfrom decimal import Decimal\nc=snakemake.config\nassert c["base_flag"] is True and c["huge"]==9007199254740993 and c["tiny"]==1e-300\nn=c["nested"]\nassert n==dict(retained="from-workflow",extra="from-extra",override="request")\ntotal=sum(map(Decimal,Path(snakemake.input[0]).read_text().splitlines()))\nPath(snakemake.output[0]).write_text("total\\thuge\\ttiny\\tretained\\toverride\\textra\\n"+str(total)+"\\t"+str(c["huge"])+"\\t"+str(c["tiny"])+"\\t"+n["retained"]+"\\t"+n["override"]+"\\t"+n["extra"]+"\\n")\n',encoding='utf-8',newline='\n')
    result=recipe(work,'run');result['workflow'].update(configfiles=['extra.json'],targets=['all']);result['resources']={'data':dict(path=str(data))};result['parameters']=dict(input={'$input':'data','path':'values.txt'},destination={'$output':'total.tsv'},huge=9007199254740993,tiny=1e-300,nested=dict(override='request'));result['outputs']=[dict(root='published',path='total.tsv',kind='file',allow_empty=False,comparison='exact'),dict(root='published',path='',kind='directory',allow_empty=False,comparison='exact')];return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--case',choices=['standard','repeated','extended','failed-rule','run','script','checkpoint','retry','pipe','config-bindings'],required=True);p.add_argument('--operation',choices=['workflow','bundle'],default='workflow');p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);stage=None
    shutil.copyfile(Path(__file__),out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux';compiled=json.loads((a.compiled/'COMPILED.json').read_bytes())
        assert all(pin(a.compiled/'classes'/name)==expected for name,expected in compiled['classes'].items());stage=Path(tempfile.mkdtemp(prefix='omicsmatic-snakemake-captured-',dir='/tmp')).resolve();assert stage.parent==Path('/tmp') and stage.name.startswith('omicsmatic-snakemake-captured-')
        work=stage/'input';work.mkdir();basic=a.case in ['standard','repeated','extended','failed-rule'];source=ROOT/'build/snakemake-original'/('driver-controls-2' if basic else 'native-driver-controls-2/records')/a.case/'working'
        selected=['Snakefile','config.yaml','rules','reads','scripts'] if basic else ['Snakefile']+(['pipeline.py'] if a.case=='script' else [])
        for name in ([] if a.case=='config-bindings' else selected):
            old=source/name;new=work/name
            if old.is_dir():shutil.copytree(old,new)
            else:shutil.copyfile(old,new)
        request=config_fixture(stage,work) if a.case=='config-bindings' else recipe(work,a.case);before=tree(work);data_before=tree(stage/'data') if (stage/'data').exists() else None
        write(out/'DEPLOYMENT.json',dict(native_stage=str(stage),stage_retained=True,source_control='Synthetic configuration/binding fixture authored in this qualifier' if a.case=='config-bindings' else str(source),selected_source_files=before,external_data=data_before,producer=pin(Path(__file__)),compiled_record=pin(a.compiled/'COMPILED.json')))
        write(stage/'recipe.json',request);write(stage/'launch.json',dict(recipe=str(stage/'recipe.json'),runtime=str(a.runtime.resolve()),out=str(stage/'execution')))
        command=['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],a.operation,str(stage/'launch.json')];start=time.monotonic()
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:process=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
        write(out/'PROCESS.json',dict(command=command,exit_code=process.returncode,seconds=round(time.monotonic()-start,3)));assert tree(work)==before,'Original caller workflow inputs changed'
        if data_before is not None:assert tree(stage/'data')==data_before,'Original external caller data changed'
        if a.case=='failed-rule':
            assert process.returncode!=0 and (stage/'execution/FAILURE.json').is_file() and not (stage/'execution/EXECUTED.json').exists()
            failure=json.loads((stage/'execution/FAILURE.json').read_bytes());driver=json.loads((stage/'execution/DRIVER.json').read_bytes());ledger=json.loads((stage/'execution/process/process.json').read_bytes())
            assert failure['exit_code']==2 and driver['exit_code']==1 and not ledger['owned_live_after_cleanup']
            assert 'EXPECTED_SYNTHETIC_UPSTREAM_RULE_FAILURE' in (stage/'execution/process/process.stderr').read_text()
        else:
            assert process.returncode==0,'Inspect retained source/adapter diagnostics';marker=json.loads((stage/'execution/EXECUTED.json').read_bytes());assert not marker['user_analysis_admitted'] and marker['catalog_credit']==0
            capture=json.loads((stage/'execution/invocation.json').read_bytes());actual=Path(capture['bindings']['workflow'])/('results/summary.tsv' if basic else 'result.txt');expected=source/('results/summary.tsv' if basic else 'result.txt')
            if a.case=='config-bindings':
                actual=stage/'execution/workspace/published/total.tsv';assert actual.read_bytes()==b'total\thuge\ttiny\tretained\toverride\textra\n5\t9007199254740993\t1e-300\tfrom-workflow\trequest\tfrom-extra\n'
            elif a.case=='checkpoint':assert sorted(actual.read_text().splitlines())==['A','B']
            else:assert actual.read_bytes()==expected.read_bytes(),'Declared source output differs'
            if a.case=='retry':assert 'Original diagnostics contain' in (stage/'execution/WARNINGS.md').read_text()
            ledger=json.loads((stage/'execution/process/process.json').read_bytes());assert not ledger['owned_live_after_cleanup']
            assert (stage/'execution/working-before.json').is_file() and (stage/'execution/working-after.json').is_file() and not (stage/'execution/COMPLETED.json').exists()
            if a.operation=='bundle':assert (stage/'execution/bundle/COMPLETED.json').is_file()
        shutil.copytree(stage,out/'records',symlinks=True);write(out/'COMPLETED.json',dict(status='PASS_CAPTURED_ORIGINAL_WORKFLOW_DEVELOPMENT_CONTROL_NOT_FINAL_ADMISSION',case=a.case,expected_failure=a.case=='failed-rule',original_caller_inputs_unchanged=True,native_stage=str(stage),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_CAPTURED_ORIGINAL_WORKFLOW_CONTROL '+a.case,flush=True)
    except Exception as error:
        if stage is not None and not (out/'records').exists():shutil.copytree(stage,out/'records',symlinks=True)
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),native_stage=str(stage),required_action='Retain full source, request, captured objects, events, all diagnostics and failed attempts. Resolve the named cause and retry NEW output; no final user analysis or catalog admission is established.'));raise
if __name__=='__main__':main()
