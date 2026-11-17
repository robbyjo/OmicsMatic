#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""New original-engine controls: permitted installs, forbidden missing software and complete reuse."""
from pathlib import Path
import argparse,json,os,shutil,sys,traceback,uuid
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_deployment import manager_tree
from setup_runtime import pin,write
from qualify_deployment import COMPUTE,execute

def export(stage,out):
    for path in stage.iterdir():
        if path.is_file():shutil.copyfile(path,out/path.name)
    for name in ['helpers','as-run']:
        if (stage/name).is_dir():shutil.copytree(stage/name,out/name)
    for source in (stage/'cases').iterdir() if (stage/'cases').is_dir() else []:
        target=out/'cases'/source.name;target.mkdir(parents=True)
        for path in source.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        shutil.copytree(source/'events',target/'events');work=target/'working';work.mkdir()
        for name in ['Snakefile','env.yaml','compute.py','values.txt','result.tsv','software.json']:
            if (source/'working'/name).is_file():shutil.copyfile(source/'working'/name,work/name)
    write(out/'EXPORTED.json',dict(schema_version=1,status='COMPLETE_INSTALLATION_PERMISSION_CONTROL_EXPORT_NOT_ANALYSIS',files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--engine-python',type=Path,required=True);p.add_argument('--source-native',type=Path,required=True);p.add_argument('--native-out',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);stage=None
    shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux';stage=a.native_out.absolute();assert stage==stage.resolve() and stage.parent.is_dir() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();old=a.source_native.resolve();source=json.loads((old/'SOURCE.json').read_bytes());python=a.engine_python.resolve();managers=Path(source['manager_installation']);app=Path(source['apptainer_prefix']);image=source['container']['uri']
        snap=stage/'as-run';snap.mkdir()
        for name in ['qualify_installation_policy.py','run_installation_policy.py','deployment_policy.py','qualify_deployment.py','setup_deployment.py','setup_runtime.py']:shutil.copyfile(HERE/name,snap/name)
        shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1',stage/'helpers')
        for name in ['cases','cache','home','images']:(stage/name).mkdir()
        (stage/'condarc').write_text('channels:\n  - conda-forge\nchannel_priority: strict\n')
        prefix_paths={mode:old/mode/'working/.snakemake/conda' for mode in ['conda','conda-apptainer']}
        preserved={mode:manager_tree(path) for mode,path in prefix_paths.items()};old_images=manager_tree(old/'images');providers={name:manager_tree(path) for name,path in [('conda',managers/'conda'),('apptainer',app)]}
        env=dict(PATH=str(app/'bin')+':'+str(managers/'conda/bin')+':'+str(python.parent)+':/usr/bin:/bin',HOME=str(stage/'home'),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',XDG_CACHE_HOME=str(stage/'cache'),CONDARC=str(stage/'condarc'),CONDA_SOLVER='libmamba',APPTAINER_CACHEDIR=str(stage/'cache/apptainer'),APPTAINER_TMPDIR=str(stage/'cache'),APPTAINER_BINDPATH=str(stage)+','+str(old))
        rows=[]
        for scenario in ['reuse-forbidden','missing-forbidden','install-permitted']:
            for mode in ['conda','apptainer','conda-apptainer']:
                name=scenario+'-'+mode;case=stage/'cases'/name;case.mkdir();work=case/'working';work.mkdir();events=case/'events';events.mkdir()
                for leaf in ['Snakefile','env.yaml','values.txt','compute.py']:shutil.copyfile(old/mode/'working'/leaf,work/leaf)
                conda=prefix_paths.get(mode,work/'.snakemake/conda') if scenario=='reuse-forbidden' else work/'.snakemake/conda'
                images=old/'images' if scenario=='reuse-forbidden' or mode=='conda-apptainer' else stage/'images' if scenario=='install-permitted' else case/'images'
                if scenario=='install-permitted' and mode!='apptainer':
                    archive=work/'.snakemake/conda-archive';archive.parent.mkdir();shutil.copytree(old/'conda/working/.snakemake/conda-archive',archive)
                argv=['--logger','omicsmatic','--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','1','--scheduler','greedy','--software-deployment-method',*mode.split('-'),'--conda-prefix',str(conda),'--apptainer-prefix',str(images),'--forceall','--keep-incomplete','--','result.tsv','software.json']
                request=case/'COMMAND.json';write(request,dict(schema_version=1,helpers=str(stage/'helpers'),argv=argv,allow_install=scenario=='install-permitted',receipt=str(case/'POLICY.json')))
                selected=dict(env,CONDA_PKGS_DIRS=str(case/'package-cache'),OMICS_SNAKEMAKE_EVENTS=str(events),OMICS_SNAKEMAKE_RUN_ID=str(uuid.uuid4()))
                write(case/'ENVIRONMENT.json',selected);before=manager_tree(work)
                process=execute(case,'execute',[str(python),'-I','-B',str(snap/'run_installation_policy.py'),str(request)],selected,work,900)
                policy=json.loads((case/'POLICY.json').read_bytes());assert policy['policy_entered'] and policy['policy_closed'] and policy['upstream_api_called'] and policy['project_helpers_before']==policy['project_helpers_after']
                expected=3 if scenario=='missing-forbidden' else 0;assert process['exit_code']==policy['exit_code']==expected,(name,'Inspect retained source and policy diagnostics')
                if expected==3:
                    assert policy['blocked'] and any(r['blocked'] and not r['upstream_method_called'] for r in policy['records'])
                    assert not (work/'result.tsv').exists() and not (work/'software.json').exists()
                    assert not any(conda.glob('*.env_setup_done'))
                    if mode=='apptainer':assert not list(images.glob('*.simg'))
                else:
                    assert not policy['blocked'] and policy['records'] and all(r['upstream_method_called'] and r['upstream_returned'] for r in policy['records']);assert (work/'result.tsv').read_bytes()==b'total\tpython\n5\t3.14.10\n'
                    if scenario=='reuse-forbidden':assert all(not r['installation_required'] for r in policy['records'])
                    else:assert any(r['installation_required'] for r in policy['records'])
                write(case/'WORKING-BEFORE.json',dict(root=str(work),nodes=before));write(case/'WORKING-AFTER.json',dict(root=str(work),nodes=manager_tree(work)))
                rows.append(dict(scenario=scenario,mode=mode,exit_code=process['exit_code'],guard_records=len(policy['records']),blocked=policy['blocked']));print(name+' PASS_EXPECTED_'+str(expected),flush=True)
        for mode,path in prefix_paths.items():assert manager_tree(path)==preserved[mode],'Original qualified environment changed: '+mode
        assert manager_tree(old/'images')==old_images,'Original qualified image changed'
        for name,path in [('conda',managers/'conda'),('apptainer',app)]:assert manager_tree(path)==providers[name],'Original provider changed: '+name
        write(stage/'COMPLETED.json',dict(schema_version=1,status='PASS_NINE_ORIGINAL_INSTALLATION_PERMISSION_CONTROLS_NOT_JAVA_ADAPTER',cases=rows,qualified_existing_environments_images_and_providers_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));export(stage,out)
    except BaseException as error:
        if stage is not None:
            write(stage/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain the full native stage, prior source controls, raw events, installation-policy decisions and partial outputs. Skip dependent analysis on missing/forbidden prerequisites, correct the named failure and retry NEW paths. This is development qualification only.'));export(stage,out)
        raise
if __name__=='__main__':main()
