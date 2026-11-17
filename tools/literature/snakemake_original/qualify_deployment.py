#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise original Conda/container execution and original Conda archives; never admit user analysis."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,uuid
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_deployment import manager_tree
from setup_runtime import pin,write
COMPUTE='''from pathlib import Path
import json,os,sys
mode=sys.argv[1]
assert sys.version.split()[0]=='3.14.10',sys.version
if mode in ['apptainer','conda-apptainer']:assert Path('/.singularity.d').is_dir(),'Container filesystem marker missing'
if mode in ['conda','conda-apptainer']:assert '/working/.snakemake/conda/' in os.environ.get('CONDA_PREFIX',''),'Rule environment was not activated'
total=sum(map(int,Path('values.txt').read_text().splitlines()))
Path('result.tsv').write_text('total\\tpython\\n'+str(total)+'\\t'+sys.version.split()[0]+'\\n')
Path('software.json').write_text(json.dumps(dict(executable=sys.executable,conda_prefix=os.environ.get('CONDA_PREFIX'),container_fs=Path('/.singularity.d').is_dir(),mode=mode),indent=2)+'\\n')
'''
BOOTSTRAP='''from pathlib import Path
import json,sys
request=json.loads(Path(sys.argv[1]).read_bytes())
sys.path.insert(0,request['helpers'])
from snakemake.cli import main
main(request['argv'])
'''

def execute(root,name,command,env,cwd,timeout=600):
    start=time.monotonic();timed_out=False
    with (root/(name+'.stdout')).open('xb') as so,(root/(name+'.stderr')).open('xb') as se:
        p=subprocess.Popen(command,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
        try:
            try:p.wait(timeout=timeout)
            except subprocess.TimeoutExpired:timed_out=True;os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=15)
        finally:
            if p.poll() is None:os.killpg(p.pid,signal.SIGKILL);p.wait()
    value=dict(command=command,working_directory=str(cwd),exit_code=p.returncode,timed_out=timed_out,seconds=round(time.monotonic()-start,3));write(root/(name+'.PROCESS.json'),value);return value

def export(stage,out):
    # Installed environments, OCI caches and case-sensitive package trees stay on
    # native Linux. Preserve their full metadata rather than collapsing names.
    for path in stage.iterdir():
        if path.is_file():shutil.copyfile(path,out/path.name)
    for name in ['helpers','as-run']:
        if (stage/name).is_dir():shutil.copytree(stage/name,out/name)
    for name in ['conda','apptainer','conda-apptainer']:
        source=stage/name
        if not source.is_dir():continue
        target=out/name;target.mkdir()
        for path in source.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        for path in source.glob('events-*'):
            if path.is_dir():shutil.copytree(path,target/path.name)
        if (source/'working').is_dir():
            work=target/'working';work.mkdir()
            for leaf in ['Snakefile','env.yaml','values.txt','compute.py','result.tsv','software.json']:
                if (source/'working'/leaf).is_file():shutil.copyfile(source/'working'/leaf,work/leaf)
    write(out/'EXPORTED.json',dict(schema_version=1,status='COMPLETE_SOURCE_CONTROL_RECORD_EXPORT',files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--engine-python',type=Path,required=True);p.add_argument('--managers',type=Path,required=True);p.add_argument('--apptainer-prefix',type=Path,help='Optional separately installed private Apptainer prefix');p.add_argument('--container-source',type=Path,required=True);p.add_argument('--native-out',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);stage=None
    shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux';stage=a.native_out.absolute();assert stage==stage.resolve() and stage.parent.is_dir() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();python=a.engine_python.resolve();managers=a.managers.resolve();apptainer=a.apptainer_prefix.resolve() if a.apptainer_prefix is not None else managers/'apptainer';image=json.loads(a.container_source.read_bytes());assert image['manifest_digest'] in image['uri']
        helpers=stage/'helpers';shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1',helpers);snap=stage/'as-run';snap.mkdir()
        for name in ['qualify_deployment.py','setup_deployment.py','setup_runtime.py']:shutil.copyfile(HERE/name,snap/name)
        (stage/'original-cli.py').write_text(BOOTSTRAP,encoding='utf-8');write(stage/'SOURCE.json',dict(engine_python=str(python),manager_installation=str(managers),apptainer_prefix=str(apptainer),container=image,project_sources={p.relative_to(stage).as_posix():pin(p) for p in stage.rglob('*') if p.is_file()},user_analysis_admitted=False))
        prefixes=dict(conda=managers/'conda',apptainer=apptainer);before={name:manager_tree(prefix) for name,prefix in prefixes.items()};(stage/'images').mkdir();(stage/'home').mkdir();(stage/'cache').mkdir();condarc=stage/'condarc';condarc.write_text('channels:\n  - conda-forge\nchannel_priority: strict\n',encoding='utf-8')
        shared=dict(PATH=str(apptainer/'bin')+':'+str(managers/'conda/bin')+':'+str(python.parent)+':/usr/bin:/bin',HOME=str(stage/'home'),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',XDG_CACHE_HOME=str(stage/'cache'),CONDARC=str(condarc),CONDA_SOLVER='libmamba',APPTAINER_CACHEDIR=str(stage/'cache/apptainer'),APPTAINER_TMPDIR=str(stage/'cache'),APPTAINER_DISABLE_CACHE='false',APPTAINER_BINDPATH=str(stage))
        # Prepare the same digest-pinned image through the unchanged runtime,
        # then pin it before source jobs and verify its bytes after every case.
        # Earlier source controls separately exercised the cold upstream pull.
        image_name=hashlib.md5(image['uri'].encode(),usedforsecurity=False).hexdigest()+'.simg';image_path=stage/'images'/image_name
        result=execute(stage,'image-pull',[str(apptainer/'bin/apptainer'),'pull','--name',image_name,image['uri']],shared,stage/'images');assert result['exit_code']==0,'Inspect retained image-pull diagnostics';image_pin=pin(image_path);write(stage/'CONTAINER-IMAGE.json',dict(uri=image['uri'],path=str(image_path),before_source_jobs=image_pin,prepared_by='Unchanged Apptainer pull of the selected immutable OCI manifest',source_cases_use_existing_image=True))
        rows=[]
        for mode in ['conda','apptainer','conda-apptainer']:
            case=stage/mode;case.mkdir();work=case/'working';work.mkdir();(work/'compute.py').write_text(COMPUTE,encoding='utf-8');(work/'values.txt').write_text('2\n3\n');(work/'env.yaml').write_text('channels:\n  - conda-forge\ndependencies:\n  - python=3.14.10\n')
            snake='rule make:\n    input: "values.txt"\n    output: "result.tsv", "software.json"\n'
            if mode in ['conda','conda-apptainer']:snake+='    conda: "env.yaml"\n'
            if mode in ['apptainer','conda-apptainer']:snake+='    container: '+json.dumps(image['uri'])+'\n'
            snake+='    shell: "python compute.py '+mode+'"\n';(work/'Snakefile').write_text(snake,encoding='utf-8')
            env=dict(shared,CONDA_PKGS_DIRS=str(case/'package-cache'));shutil.copytree(managers/'conda/pkgs',case/'package-cache',symlinks=True)
            if mode=='conda-apptainer':
                archives=stage/'conda/working/.snakemake/conda-archive';assert archives.is_dir();destination=work/'.snakemake/conda-archive';destination.parent.mkdir();shutil.copytree(archives,destination)
                archive_input=manager_tree(destination);write(case/'ARCHIVE-INPUT.json',dict(source=str(archives),destination=str(destination),nodes=archive_input))
            for name,command in [('git-init',['/usr/bin/git','init']),('git-add',['/usr/bin/git','add','--','Snakefile','compute.py','values.txt','env.yaml']),('git-commit',['/usr/bin/git','-c','user.name=OmicsMatic synthetic controls','-c','user.email=controls@example.invalid','commit','-m','Synthetic source deployment fixture'])]:
                result=execute(case,name,command,env,work,30);assert result['exit_code']==0
            def cli(name,archive=False):
                events=case/('events-'+name);events.mkdir();run_id=str(uuid.uuid4());selected=dict(env,OMICS_SNAKEMAKE_EVENTS=str(events),OMICS_SNAKEMAKE_RUN_ID=run_id)
                argv=['--logger','omicsmatic','--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','1','--scheduler','greedy','--software-deployment-method',*mode.split('-'),'--conda-prefix',str(work/'.snakemake/conda'),'--apptainer-prefix',str(stage/'images'),'--forceall','--keep-incomplete']
                if archive:argv+=['--archive',str(case/'workflow-archive.tar')]
                argv+=['--','result.tsv','software.json'];request=case/(name+'.COMMAND.json');write(request,dict(helpers=str(helpers),argv=argv,run_id=run_id,events_directory=str(events),environment=selected));result=execute(case,name,[str(python),'-I','-B',str(stage/'original-cli.py'),str(request)],selected,work)
                assert result['exit_code']==0,(mode,name,'Inspect retained original diagnostics')
                streams=list(events.glob('*.jsonl'));assert streams
                for path in streams:
                    records=[json.loads(line) for line in path.read_text().splitlines()];assert records[0]['record_type']=='collector_opened' and records[-1]['record_type']=='collector_closed' and not records[-1]['collector_failed']
                    assert all(r['run_id']==run_id for r in records) and [r['sequence'] for r in records]==list(range(1,len(records)+1))
                return result
            result=cli('execute');assert (work/'result.tsv').read_bytes()==b'total\tpython\n5\t3.14.10\n';software=json.loads((work/'software.json').read_bytes());assert software['container_fs']==(mode!='conda')
            assert pin(image_path)==image_pin,'Container bytes changed during source jobs'
            if mode=='conda-apptainer':assert manager_tree(destination)==archive_input and 'Installing archived conda packages.' in (case/'execute.stderr').read_text()
            if mode=='conda':
                cli('archive',True);environments=[x for x in (work/'.snakemake/conda').iterdir() if x.is_dir()];assert len(environments)==1;query=execute(case,'conda-list',[str(managers/'conda/bin/conda'),'list','--explicit','--prefix',str(environments[0])],env,work,60);assert query['exit_code']==0
            write(case/'WORKING-TREE.json',dict(native_working=str(work),nodes=manager_tree(work),package_cache_retained=str(case/'package-cache')))
            row=dict(mode=mode,process=result,software=software,native_working=str(work),source_outputs=dict(result=pin(work/'result.tsv'),software=pin(work/'software.json')),whole_paper_reproduced=False);rows.append(row);print(mode+' PASS_ORIGINAL_SOURCE_DEPLOYMENT_CONTROL',flush=True)
        for name,expected in before.items():assert manager_tree(prefixes[name])==expected,'Installed deployment manager changed: '+name
        write(stage/'CONTAINER-IMAGE-AFTER.json',dict(path=str(image_path),after_source_jobs=pin(image_path),unchanged=pin(image_path)==image_pin))
        write(stage/'COMPLETED.json',dict(status='PASS_THREE_ORIGINAL_SOURCE_DEPLOYMENT_MODES_NOT_JAVA_ADAPTER',cases=rows,native_stage=str(stage),manager_trees_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));export(stage,out);print('PASS_THREE_ORIGINAL_SOURCE_DEPLOYMENT_MODES_NOT_JAVA_ADAPTER',flush=True)
    except BaseException as error:
        if stage is not None:
            write(stage/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain the complete native stage, package/cache/image bytes, raw original events, source commands and partial outputs. Missing/forbidden prerequisites skip dependent analysis. Resolve the named source/deployment problem and retry NEW paths; never infer adapter qualification or catalog completion from a manager version probe.'));export(stage,out)
        raise
if __name__=='__main__':main()
