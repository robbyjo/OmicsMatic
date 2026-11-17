#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Qualify permission propagation into original spawned shadow jobs; no user analysis admission."""
from pathlib import Path
import argparse,json,os,shutil,sys,traceback,uuid
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_deployment import manager_tree
from setup_runtime import pin,write
from qualify_deployment import execute

def export(stage,out):
    for path in stage.iterdir():
        if path.is_file():shutil.copyfile(path,out/path.name)
    for name in ['helpers','as-run']:
        if (stage/name).is_dir():shutil.copytree(stage/name,out/name)
    for case in (stage/'cases').iterdir() if (stage/'cases').is_dir() else []:
        target=out/'cases'/case.name;target.mkdir(parents=True)
        for path in case.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        for name in ['events','policy-records']:
            if (case/name).is_dir():shutil.copytree(case/name,target/name)
        work=target/'working';work.mkdir()
        for name in ['Snakefile','env.yaml','values.txt','compute.py','result.tsv','software.json','fault-moved.json']:
            if (case/'working'/name).is_file():shutil.copyfile(case/'working'/name,work/name)
    write(out/'EXPORTED.json',dict(schema_version=1,status='COMPLETE_PROPAGATED_POLICY_DEVELOPMENT_EXPORT_NOT_ANALYSIS',files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--engine-python',type=Path,required=True);p.add_argument('--source-native',type=Path,required=True);p.add_argument('--native-out',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);stage=None
    shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux';stage=a.native_out.absolute();assert stage==stage.resolve() and stage.parent.is_dir() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();old=a.source_native.resolve();source=json.loads((old/'SOURCE.json').read_bytes());python=a.engine_python.resolve();managers=Path(source['manager_installation']);app=Path(source['apptainer_prefix']);image=source['container']['uri']
        helpers=stage/'helpers';shutil.copytree(HERE/'managed_helpers',helpers);shutil.copyfile(HERE/'deployment_policy.py',helpers/'deployment_policy.py');shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1/snakemake_logger_plugin_omicsmatic/__init__.py',helpers/'omicsmatic_logger_base.py')
        snap=stage/'as-run';snap.mkdir()
        for name in ['qualify_propagated_policy.py','run_propagated_policy.py','qualify_deployment.py','setup_deployment.py','setup_runtime.py']:shutil.copyfile(HERE/name,snap/name)
        for name in ['cases','cache','home','images']:(stage/name).mkdir()
        (stage/'condarc').write_text('channels:\n  - conda-forge\nchannel_priority: strict\n');pinned_sources={p.relative_to(stage).as_posix():pin(p) for d in [helpers,snap] for p in d.rglob('*') if p.is_file()}
        original_roots={'conda-environments':old/'conda/working/.snakemake/conda','combined-environments':old/'conda-apptainer/working/.snakemake/conda','images':old/'images','conda-provider':managers/'conda','apptainer-provider':app};immutable={name:dict(root=str(path),nodes=manager_tree(path)) for name,path in original_roots.items()};write(stage/'IMMUTABLE-BEFORE.json',immutable)
        shared=dict(PATH=str(app/'bin')+':'+str(managers/'conda/bin')+':'+str(python.parent)+':/usr/bin:/bin',HOME=str(stage/'home'),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',XDG_CACHE_HOME=str(stage/'cache'),CONDARC=str(stage/'condarc'),CONDA_SOLVER='libmamba',APPTAINER_CACHEDIR=str(stage/'cache/apptainer'),APPTAINER_TMPDIR=str(stage/'cache'),APPTAINER_BINDPATH=str(stage)+','+str(old));rows=[];prepared={}
        for scenario in ['reuse-forbidden','missing-forbidden','install-permitted','child-missing-forbidden']:
            for mode in ['conda','apptainer','conda-apptainer']:
                name=scenario+'-'+mode;case=stage/'cases'/name;case.mkdir();work=case/'working';work.mkdir();events=case/'events';events.mkdir();records=case/'policy-records';records.mkdir();moved=None;backup=None;cache_before=None;cache_root=None
                for leaf in ['env.yaml','values.txt','compute.py']:shutil.copyfile(old/mode/'working'/leaf,work/leaf)
                conda=old/mode/'working/.snakemake/conda' if scenario=='reuse-forbidden' and mode!='apptainer' else work/'.snakemake/conda'
                images=old/'images' if scenario=='reuse-forbidden' or scenario=='missing-forbidden' and mode=='conda-apptainer' else case/'images' if scenario=='missing-forbidden' else stage/'images'
                snake='rule make:\n    input: "values.txt", "compute.py"\n    output: "result.tsv", "software.json"\n    shadow: "minimal"\n'
                if mode!='apptainer':snake+='    conda: "env.yaml"\n'
                if mode!='conda':snake+='    container: '+json.dumps(image)+'\n'
                snake+='    shell: "python compute.py '+mode+'"\n'
                if scenario=='install-permitted' and mode!='apptainer':
                    archive=work/'.snakemake/conda-archive';archive.parent.mkdir();shutil.copytree(old/'conda/working/.snakemake/conda-archive',archive)
                if scenario=='child-missing-forbidden':
                    prior=prepared[mode];conda=Path(prior['conda']);images=Path(prior['images']);kind='container-image' if mode=='apptainer' else 'conda-environment';targets={r['address'] for proc in prior['policy']['policy_processes'] for r in proc['records'] if r['kind']==kind};assert len(targets)==1;target=Path(next(iter(targets)));assert target.is_relative_to(stage) and target.exists();backup=target.with_name(target.name+'.held-by-policy-control');assert backup.is_relative_to(stage) and not backup.exists();moved=target;cache_root=images if mode=='apptainer' else conda;cache_before=manager_tree(cache_root)
                    snake+='\nonstart:\n    from pathlib import Path\n    import json\n    original=Path('+repr(str(target))+')\n    held=Path('+repr(str(backup))+')\n    assert original.exists() and not held.exists()\n    original.rename(held)\n    Path("fault-moved.json").write_text(json.dumps({"original":str(original),"backup":str(held)}))\n'
                (work/'Snakefile').write_text(snake);run_id=str(uuid.uuid4());policy_file=case/'INSTALLATION-POLICY.json';write(policy_file,dict(schema_version=1,run_id=run_id,allow_install=scenario=='install-permitted',records_directory=str(records),logger='omicsmanaged'))
                argv=['--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','1','--scheduler','greedy','--software-deployment-method',*mode.split('-'),'--conda-prefix',str(conda),'--apptainer-prefix',str(images),'--forceall','--keep-incomplete','--','result.tsv','software.json'];request=case/'COMMAND.json';write(request,dict(schema_version=1,helpers=str(helpers),argv=argv,installation_policy=str(policy_file),receipt=str(case/'CONTROLLER.json')));environment=dict(shared,CONDA_PKGS_DIRS=str(case/'package-cache'),OMICS_SNAKEMAKE_EVENTS=str(events),OMICS_SNAKEMAKE_RUN_ID=run_id);write(case/'ENVIRONMENT.json',environment)
                try:process=execute(case,'execute',[str(python),'-I','-B',str(snap/'run_propagated_policy.py'),str(request)],environment,work,900)
                finally:
                    if moved is not None and backup.exists():
                        assert moved.is_relative_to(stage) and backup.is_relative_to(stage) and not moved.exists(),'Preserve both unexpected prefixes; do not overwrite a recreated target'
                        backup.rename(moved);write(case/'RESTORED.json',dict(original=str(moved),backup=str(backup),software_state_unchanged=manager_tree(cache_root)==cache_before));assert manager_tree(cache_root)==cache_before
                controller=json.loads((case/'CONTROLLER.json').read_bytes());expected=3 if scenario in ['missing-forbidden','child-missing-forbidden'] else 0;assert process['exit_code']==controller['exit_code']==expected and not controller['integrity_errors'],(name,'Inspect retained propagated policy and source diagnostics');processes=controller['policy_processes'];parent=next(p for p in processes if p['pid']==controller['controller_pid']);children=[p for p in processes if p['pid']!=controller['controller_pid']]
                if scenario=='missing-forbidden':assert parent['blocked'] and not children and not (work/'result.tsv').exists()
                else:
                    assert not parent['blocked'] and children and parent['spawn_arguments']
                    for proc in processes:
                        for spawn in proc['spawn_arguments']:assert spawn['propagated_arguments']==spawn['original_arguments']+' --logger omicsmanaged'
                    if expected==3:assert any(p['blocked'] for p in children) and (work/'fault-moved.json').is_file() and not (work/'result.tsv').exists()
                    else:
                        assert all(not p['blocked'] for p in processes) and (work/'result.tsv').read_bytes()==b'total\tpython\n5\t3.14.10\n'
                        if scenario=='reuse-forbidden':assert all(not r['installation_required'] for p in processes for r in p['records'])
                        else:assert any(r['installation_required'] for p in processes for r in p['records']);prepared[mode]=dict(conda=str(conda),images=str(images),policy=controller)
                write(case/'WORKING-AFTER.json',dict(root=str(work),nodes=manager_tree(work)));rows.append(dict(case=name,scenario=scenario,mode=mode,exit_code=process['exit_code'],policy_processes=len(processes),spawned_policy_processes=len(children)));print(name+' PASS_EXPECTED_'+str(expected),flush=True)
        after={name:dict(root=str(path),nodes=manager_tree(path)) for name,path in original_roots.items()};write(stage/'IMMUTABLE-AFTER.json',after);assert immutable==after,'Original qualified software changed';assert all(pin(stage/name)==value for name,value in pinned_sources.items());write(stage/'SOURCE.json',dict(schema_version=1,engine_python=str(python),source_native=str(old),helpers=pinned_sources,project_instrumentation='Original local-executor additional_general_args is called, and only the project recorder CLI flag is appended. The original CLI, executor job execution and deployer methods remain selected; permitted deployer calls execute the original method.',user_analysis_admitted=False));write(stage/'OWNED-IMAGES.json',dict(root=str(stage/'images'),nodes=manager_tree(stage/'images')))
        write(stage/'COMPLETED.json',dict(schema_version=1,status='PASS_TWELVE_ORIGINAL_SPAWNED_PROCESS_INSTALLATION_POLICY_CONTROLS_NOT_JAVA_ADAPTER',cases=rows,original_qualified_software_unchanged=True,temporary_faults_restored=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));export(stage,out)
    except BaseException as error:
        if stage is not None:
            write(stage/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain the complete native fixture, every policy/collector/source record and any fault backup. Skip dependent analysis; resolve the named discrepancy and retry NEW paths. No Java managed workflow, catalog or paper-reproduction admission is claimed.'));export(stage,out)
        raise
if __name__=='__main__':main()
