#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original managed profile controls through the actual Java supervisor; no workflow/catalog admission."""
from pathlib import Path
import argparse,json,shutil,sys,traceback,uuid
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from setup_deployment import manager_tree
from qualify_deployment import execute

def export(stage,out):
    for path in stage.iterdir():
        if path.is_file():shutil.copyfile(path,out/path.name)
    for name in ['helpers','as-run']:
        if (stage/name).exists():shutil.copytree(stage/name,out/name)
    for case in (stage/'cases').iterdir():
        target=out/'cases'/case.name;target.mkdir(parents=True)
        for path in case.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        for name in ['events','policy-records','process']:
            if (case/name).exists():shutil.copytree(case/name,target/name)
        for path in (case/'working').rglob('*'):
            if path.is_file() and '.snakemake' not in path.relative_to(case/'working').parts:
                dest=target/'working'/path.relative_to(case/'working');dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
    write(out/'EXPORTED.json',dict(files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['compiled','runtime','providers','source-native','native-out','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--scenarios',nargs='+',choices=['plain','resources','precedence','until','envmodules','dryrun','missing-software','legacy-plain','legacy-missing'],default=['plain','resources','precedence','until','envmodules','dryrun','missing-software','legacy-plain','legacy-missing'])
    p.add_argument('--modes',nargs='+',choices=['conda','apptainer','conda-apptainer'],default=['conda','apptainer','conda-apptainer']);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);stage=None
    shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='linux';stage=a.native_out.absolute();assert stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();(stage/'cases').mkdir();(stage/'helpers').mkdir();(stage/'as-run').mkdir()
        compiled=json.loads((a.compiled/'COMPILED.json').read_bytes())
        for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
        for name in ['qualify_managed_parser.py','setup_runtime.py','setup_deployment.py','qualify_deployment.py']:shutil.copyfile(HERE/name,stage/'as-run'/name)
        old=a.source_native.resolve();source=json.loads((old/'SOURCE.json').read_bytes());conda_provider=Path(source['manager_installation'])/'conda';app=Path(source['apptainer_prefix']);python=Path(json.loads((a.runtime/'READY.json').read_bytes())['environment'])/'bin/python'
        roots={'conda-environments':old/'conda/working/.snakemake/conda','combined-environments':old/'conda-apptainer/working/.snakemake/conda','images':old/'images','conda-provider':conda_provider,'apptainer-provider':app}
        before={key:dict(root=str(path),nodes=manager_tree(path)) for key,path in roots.items()};write(stage/'IMMUTABLE-BEFORE.json',before)
        (stage/'condarc').write_text('channels:\n  - conda-forge\nchannel_priority: strict\n');rows=[]
        for scenario in a.scenarios:
            for mode in a.modes:
                name=scenario+'-'+mode;case=stage/'cases'/name;case.mkdir();work=case/'working';work.mkdir();events=case/'events';events.mkdir();policies=case/'policy-records';policies.mkdir()
                for leaf in ['Snakefile','env.yaml','values.txt','compute.py']:shutil.copyfile(old/mode/'working'/leaf,work/leaf)
                snake=work/'Snakefile';snake.write_text(snake.read_text().replace('input: "values.txt"','input: "values.txt", "compute.py"',1).replace('    input:','    shadow: "minimal"\n    input:',1))
                with (work/'Snakefile').open('a') as f:f.write('\nonstart:\n    from pathlib import Path\n    Path("api-called.txt").write_text("Original API/workflow onstart was called\\n")\n')
                profile={'resources':'set-threads:\n  make: 1\nprintshellcmds: true\n','precedence':'cores: 99\nscheduler: ilp\nconda-prefix: /unqualified-profile-prefix\n','until':'until: [make]\n','envmodules':'use-envmodules: true\n','dryrun':'dry-run: true\n'}.get(scenario)
                if profile is not None:folder=work/'profiles/default';folder.mkdir(parents=True);(folder/'config.yaml').write_text(profile)
                missing=scenario in ['missing-software','legacy-missing'];conda=case/'environments' if missing else roots['combined-environments' if mode=='conda-apptainer' else 'conda-environments'];images=case/'images' if missing else roots['images']
                for path in [case/'environments',case/'images',case/'package-cache']:path.mkdir()
                run=str(uuid.uuid4());configuration=case/'INSTALLATION-POLICY.json';write(configuration,dict(schema_version=1,run_id=run,allow_install=False,records_directory=str(policies),logger='omicsmanaged'));parameters=case/'parameters.json';write(parameters,{})
                argv=['--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','1','--scheduler','greedy','--retries','0','--latency-wait','1','--software-deployment-method',*mode.split('-'),'--conda-prefix',str(conda),'--apptainer-prefix',str(images),'--conda-frontend','conda','--forceall','--keep-incomplete','--configfile',str(parameters),'--','result.tsv','software.json']
                expected=dict(snakefile=str(work/'Snakefile'),directory=str(work),targets=['result.tsv','software.json'],configfile=[str(parameters)],executor='local',cores=1,scheduler='greedy',retries=0,latency_wait=1,forceall=True,keep_incomplete=True,notemp=False,benchmark_extended=False,logger=['omicsmanaged'],software_deployment_method=sorted(x.upper() for x in mode.split('-')),conda_prefix=str(conda),apptainer_prefix=str(images),apptainer_args='',conda_frontend='conda')
                legacy=scenario.startswith('legacy-');request=case/'COMMAND.json';command=dict(schema_version=1 if legacy else 2,helpers=str(stage/'helpers'),argv=argv,installation_policy=str(configuration),receipt=str(case/'CONTROLLER.json'))
                if not legacy:command['execution_policy']=dict(schema_version=1,deployment=mode,allow_install=False,expected=expected)
                write(request,command);environment=dict(PATH=str(app/'bin')+':'+str(conda_provider/'bin')+':'+str(python.parent)+':/usr/bin:/bin',HOME=str(stage),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',XDG_CACHE_HOME=str(stage/'cache'),CONDARC=str(stage/'condarc'),CONDA_SOLVER='libmamba',CONDA_PKGS_DIRS=str(case/'package-cache'),APPTAINER_CACHEDIR=str(stage/'cache/apptainer'),APPTAINER_TMPDIR=str(stage/'cache'),APPTAINER_BINDPATH=str(stage)+','+str(old),OMICS_SNAKEMAKE_EVENTS=str(events),OMICS_SNAKEMAKE_RUN_ID=run)
                envelope=case/'SUPERVISOR-REQUEST.json';write(envelope,dict(root=str(stage),working=str(work),runtime=str(a.runtime.resolve()),providers=str(a.providers.resolve()),controller_request=str(request),environment=environment,limits=dict(wall_seconds=240,address_space_bytes=17179869184,cpu_seconds=240,file_bytes=1073741824,open_files=4096,workspace_bytes=34359738368,files=200000,processes=96,threads=512,sampled_rss_bytes=8589934592,poll_millis=25)))
                process=execute(case,'execute',['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],'supervised-policy',str(envelope)],environment,work,900);rejected=scenario in ['until','envmodules','dryrun'];exit_code=3 if rejected or missing else 0
                assert process['exit_code']==exit_code,(name,'Inspect retained source/controller/supervisor diagnostics');controller=json.loads((case/'CONTROLLER.json').read_bytes());bound=json.loads((case/'SUPERVISED-POLICY.json').read_bytes())['bound'];assert bound['exitCode']==exit_code and not controller['integrity_errors']
                if rejected:
                    assert not controller['upstream_api_called'] and not (work/'api-called.txt').exists() and not (work/'result.tsv').exists() and not list(events.iterdir())
                    assert not bound['parserPolicy']['accepted'] and not bound['policy']['deploymentCalls'] and not bound['policy']['spawnCalls']
                else:
                    assert controller['upstream_api_called'] and (legacy or bound['parserPolicy']['accepted'])
                    if missing:assert bound['policy']['blockedCalls']>0 and not (work/'result.tsv').exists()
                    else:assert (work/'api-called.txt').is_file() and (work/'result.tsv').read_bytes()==b'total\tpython\n5\t3.14.10\n'
                write(case/'WORKING-AFTER.json',dict(root=str(work),nodes=manager_tree(work)));rows.append(dict(case=name,mode=mode,scenario=scenario,exit_code=exit_code,api_called=controller['upstream_api_called']));print(name+' PASS_EXPECTED_'+str(exit_code),flush=True)
        after={key:dict(root=str(path),nodes=manager_tree(path)) for key,path in roots.items()};write(stage/'IMMUTABLE-AFTER.json',after);assert before==after
        for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
        write(stage/'COMPLETED.json',dict(status='PASS_ACTUAL_ORIGINAL_MANAGED_PROFILE_POLICY_AND_SUPERVISOR_CONTROLS_NOT_WORKFLOW_ADMISSION',cases=rows,compiled=pin(a.compiled/'COMPILED.json'),original_software_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));export(stage,out)
    except BaseException as error:
        if stage is not None:write(stage/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain all native/exported evidence and skip dependent analysis. Resolve the named discrepancy where permitted and retry NEW paths; do not refresh earlier hashes.'));export(stage,out)
        raise
if __name__=='__main__':main()
