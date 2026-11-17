#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Captured original managed workflows with real inputs/software/events/outputs; no final bundle admission."""
from pathlib import Path
import argparse,json,os,shutil,sys,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from setup_deployment import manager_tree
from qualify_deployment import execute
from qualify_workflows import recipe

COMPUTE='''from pathlib import Path
import json,os,sys
mode=sys.argv[3]
assert sys.version.split()[0]=='3.14.10',sys.version
if mode!='conda':assert Path('/.singularity.d').is_dir()
if mode!='apptainer':assert os.environ.get('CONDA_PREFIX')
assert int(sys.argv[4])==9007199254740993 and float(sys.argv[5])==1e-300
total=sum(map(int,Path(sys.argv[1]).read_text().splitlines()))
Path(sys.argv[2]).write_text('total\\thuge\\ttiny\\n'+str(total)+'\\t'+sys.argv[4]+'\\t'+sys.argv[5]+'\\n')
Path('software.json').write_text(json.dumps(dict(executable=sys.executable,conda_prefix=os.environ.get('CONDA_PREFIX'),container_fs=Path('/.singularity.d').is_dir(),mode=mode)))
'''
def export(stage,out):
    pool=out/'shared-objects';pool.mkdir();blob_pins={};hardlinked=set();copied=0
    if (stage/'as-run').exists():shutil.copytree(stage/'as-run',out/'as-run')
    for path in stage.iterdir():
        if path.is_file():shutil.copyfile(path,out/path.name)
    for case in (stage/'cases').iterdir():
        target=out/'cases'/case.name;target.mkdir(parents=True)
        for path in case.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        execution=case/'execution'
        if not execution.exists():continue
        for path in execution.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        if (execution/'objects').exists():
            destination=target/'objects';destination.mkdir()
            for path in (execution/'objects').iterdir():
                expected=pin(path);assert expected['sha256']==path.name and not path.is_symlink();shared=pool/path.name;file=destination/path.name
                if not shared.exists():shutil.copyfile(path,shared);blob_pins[path.name]=expected
                else:assert blob_pins[path.name]==expected
                try:os.link(shared,file);hardlinked.add(file)
                except OSError:shutil.copyfile(shared,file);copied+=1
        for name in ['helpers','events','process','runtime','deployment','policy-records']:
            if (execution/name).exists():shutil.copytree(execution/name,target/name)
    for name,expected in blob_pins.items():assert pin(pool/name)==expected
    files={}
    for path in out.rglob('*'):
        if not path.is_file():continue
        if path in hardlinked:assert os.path.samefile(path,pool/path.name);value=blob_pins[path.name]
        elif path.parent==pool:value=blob_pins[path.name]
        else:value=pin(path)
        files[path.relative_to(out).as_posix()]=value
    write(out/'EXPORTED.json',dict(files=files,storage=dict(unique_blob_objects=len(blob_pins),hardlinked_case_members=len(hardlinked),copied_case_members=copied)))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['compiled','runtime','providers','source-native','native-out','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--scenarios',nargs='+',choices=['reuse','install','missing','profile-skip','upstream-fail','missing-output'],default=['reuse','install','missing','profile-skip','upstream-fail','missing-output']);p.add_argument('--modes',nargs='+',choices=['conda','apptainer','conda-apptainer'],default=['conda','apptainer','conda-apptainer']);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=None
    try:
        assert sys.platform=='linux';stage=a.native_out.absolute();assert stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();(stage/'cases').mkdir();(stage/'as-run').mkdir();compiled=json.loads((a.compiled/'COMPILED.json').read_bytes())
        for name in ['qualify_managed_capture.py','qualify_workflows.py','qualify_deployment.py','setup_runtime.py','setup_deployment.py']:shutil.copyfile(HERE/name,stage/'as-run'/name)
        for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
        old=a.source_native.resolve();source=json.loads((old/'SOURCE.json').read_bytes());roots={'conda':old/'conda/working/.snakemake/conda','conda-apptainer':old/'conda-apptainer/working/.snakemake/conda','images':old/'images','conda-provider':Path(source['manager_installation'])/'conda','apptainer-provider':Path(source['apptainer_prefix'])};before={key:dict(root=str(path),nodes=manager_tree(path)) for key,path in roots.items()};write(stage/'IMMUTABLE-BEFORE.json',before);rows=[]
        for scenario in a.scenarios:
            for mode in a.modes:
                name=scenario+'-'+mode;case=stage/'cases'/name;case.mkdir();work=case/'input';work.mkdir();data=case/'data';data.mkdir();(data/'values.txt').write_text('2\n3\n');shutil.copyfile(old/mode/'working/env.yaml',work/'env.yaml');(work/'compute.py').write_text(COMPUTE)
                snake='rule make:\n    input: data=config["data_path"], script="compute.py"\n    output: table=config["destination"], software="software.json"\n    params: mode=config["mode"], huge=config["huge"], tiny=config["tiny"]\n    shadow: "minimal"\n'
                if mode!='apptainer':snake+='    conda: "env.yaml"\n'
                if mode!='conda':snake+='    container: '+json.dumps(source['container']['uri'])+'\n'
                snake+='    shell: "python compute.py {input.data:q} {output.table:q} {params.mode:q} {params.huge:q} {params.tiny:q}"\n'
                if scenario=='upstream-fail':snake=snake.replace('python compute.py','exit 17; python compute.py')
                (work/'Snakefile').write_text(snake)
                if scenario=='profile-skip':profile=work/'profiles/default';profile.mkdir(parents=True);(profile/'config.yaml').write_text('until: [make]\n')
                if scenario=='install' and mode!='apptainer':archive=work/'.snakemake/conda-archive';archive.parent.mkdir();shutil.copytree(old/'conda/working/.snakemake/conda-archive',archive)
                request=recipe(work,'run');request['schema_version']=2;request['workflow']['targets']=['make'];request['resources']={'data':dict(path=str(data))};request['parameters']=dict(mode=mode,data_path={'$input':'data','path':'values.txt'},destination={'$output':'summary.tsv'},huge=9007199254740993,tiny=1e-300);request['execution'].update(deployment=mode,allow_install=scenario=='install',cores=1)
                borrowed=scenario not in ['install','missing'];request['managed_software']=dict(providers=str(a.providers.resolve()),conda_environments=str(roots['conda-apptainer' if mode=='conda-apptainer' else 'conda']) if borrowed and mode!='apptainer' else None,container_images=str(roots['images']) if borrowed and mode!='conda' else None)
                request['outputs']=[dict(root='published',path='summary.tsv',kind='file',allow_empty=False,comparison='exact'),dict(root='workflow',path='software.json',kind='file',allow_empty=False,comparison='exact')]
                if scenario=='missing-output':request['outputs'].append(dict(root='published',path='not-created.tsv',kind='file',allow_empty=False,comparison='exact'))
                request['limits'].update(capture_files=200000,capture_bytes=8589934592,file_bytes=1073741824,output_file_bytes=1073741824,workspace_bytes=34359738368,wall_seconds=900,cpu_seconds=900,address_space_bytes=17179869184,processes=96,threads=512,sampled_rss_bytes=8589934592,metadata_bytes=67108864,log_bytes=67108864,poll_millis=25)
                write(case/'recipe.json',request);write(case/'INPUTS-BEFORE.json',dict(workflow=manager_tree(work),data=manager_tree(data)));launch=case/'LAUNCH.json';write(launch,dict(recipe=str(case/'recipe.json'),runtime=str(a.runtime.resolve()),out=str(case/'execution')))
                process=execute(case,'execute',['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],'workflow',str(launch)],dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),case,1500);positive=scenario in ['reuse','install'];assert (process['exit_code']==0)==positive,(name,'Inspect retained original source, process and capture diagnostics')
                execution=case/'execution';assert not (execution/'COMPLETED.json').exists() and not (execution/'bundle').exists();assert json.loads((case/'INPUTS-BEFORE.json').read_bytes())==dict(workflow=manager_tree(work),data=manager_tree(data))
                if positive:
                    assert (execution/'EXECUTED.json').is_file();outputs=json.loads((execution/'outputs.json').read_bytes());table=next(row for row in outputs if row['root']=='published')['members']['']['pin'];assert (execution/'objects'/table['sha256']).read_bytes()==b'total\thuge\ttiny\n5\t9007199254740993\t1e-300\n';assert json.loads((execution/'completion-events.json').read_bytes())['final_done']>=1
                    provenance=next(row for row in outputs if row['root']=='workflow')['members']['']['pin'];software=json.loads((execution/'objects'/provenance['sha256']).read_bytes());assert software['mode']==mode and software['container_fs']==(mode!='conda')
                    selected=json.loads((execution/'invocation.json').read_bytes())['software_roots']
                    if mode!='apptainer':assert software['conda_prefix'].startswith(selected['environments']+'/')
                    controller=json.loads((execution/'CONTROLLER.json').read_bytes());calls=[item for process in controller['policy_processes'] for item in process['records']]
                    assert (any(item['installation_required'] for item in calls))==(scenario=='install')
                else:
                    failure=json.loads((execution/'FAILURE.json').read_bytes());assert failure['exit_code']==(3 if scenario in ['missing','profile-skip'] else 2) and not (execution/'EXECUTED.json').exists()
                assert (execution/'software-after.json').is_file();rows.append(dict(case=name,mode=mode,scenario=scenario,source_process=process,expected_success=positive));print(name+' PASS',flush=True)
        after={key:dict(root=str(path),nodes=manager_tree(path)) for key,path in roots.items()};write(stage/'IMMUTABLE-AFTER.json',after);assert before==after
        for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
        write(stage/'COMPLETED.json',dict(status='PASS_CAPTURED_MANAGED_ORIGINAL_WORKFLOW_INPUT_SOFTWARE_EVENTS_OUTPUTS_NOT_FINAL_ADMISSION',cases=rows,original_software_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));export(stage,out)
    except BaseException as error:
        if stage is not None:write(stage/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain every original input, source/process diagnostic, partial object and native prefix. Skip dependent analysis; resolve the cause where permitted and retry NEW paths. Never relocate installed Conda prefixes or refresh earlier hashes.'));export(stage,out)
        raise
if __name__=='__main__':main()
