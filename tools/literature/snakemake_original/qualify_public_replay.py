#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Two genuine public-CLI replay generations with the captured caller inputs absent."""
from pathlib import Path
import argparse,json,os,shutil,sys,traceback

if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from setup_deployment import manager_tree
from qualify_deployment import execute

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','runtime','native-out','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--managed-source',type=Path);p.add_argument('--preinstalled-source',type=Path)
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=None;moved=[];caller_before={};rows=[]
    try:
        assert sys.platform=='linux' and bool(args.managed_source)!=bool(args.preinstalled_source)
        stage=args.native_out.absolute();assert stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir()
        jar=args.jar.resolve();jar_pin=pin(jar);runtime=args.runtime.resolve()
        def run(case,name,operation,options,expected=0):
            process=execute(case,name,['/usr/bin/java','-jar',str(jar),'snakemake-tools',operation,*options],dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),case,2400)
            assert process['exit_code']==expected,(str(case),name,'Inspect retained actual public-command diagnostics')
            return process
        def archive(case,name,source):
            folder=out/name
            process=execute(case,'archive-'+name,[sys.executable,'-I','-B',str(HERE/'archive_workflow_bundle.py'),'--source',str(source),'--out',str(folder)],dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),case,900)
            assert process['exit_code']==0
            return folder/'bundle.zip'
        if args.managed_source:
            source=args.managed_source.resolve();complete=json.loads((source/'COMPLETED.json').read_bytes());assert complete['public_cli'] and complete['managed_bundles'] and complete['original_software_unchanged'] and complete['jar']==jar_pin
            selected=[(permission+'-'+mode,source/'cases'/(permission+'-'+mode)/'execution/bundle') for permission in ['reuse','install'] for mode in ['conda','apptainer','conda-apptainer']]
            immutable=json.loads((source/'IMMUTABLE-AFTER.json').read_bytes());assert {key:dict(root=value['root'],nodes=manager_tree(Path(value['root']))) for key,value in immutable.items()}==immutable
        else:
            source=args.preinstalled_source.resolve();complete=json.loads((source/'COMPLETED.json').read_bytes());assert complete['public_cli'] and len(complete['cases'])==12
            original=Path(json.loads((source/'config-bindings/DEPLOYMENT.json').read_bytes())['native_stage']);assert original.parent==Path('/tmp') and original.name.startswith('omicsmatic-snakemake-captured-')
            selected=[('preinstalled',original/'execution/bundle')];immutable=None
        for name,original_bundle in selected:
            case=stage/name;case.mkdir();original_marker=pin(original_bundle/'manifest.json');parent=original_bundle
            if name.startswith('install-'):
                request=json.loads((original_bundle/'REQUEST.json').read_bytes());request['outputs']=[value for value in request['outputs'] if value['root']=='published'];write(case/'parent-recipe.json',request)
                run(case,'parent','run',['--request',str(case/'parent-recipe.json'),'--runtime',str(runtime),'--out',str(case/'parent')]);parent=case/'parent/bundle'
            capture=json.loads((parent/'capture.json').read_bytes());caller_before={}
            try:
                for entry in capture['roots']:
                    path=Path(entry['source']);assert path.is_dir() and not path.is_symlink() and path.name in ['input','data']
                    if args.managed_source:assert path.parent.parent==source/'cases'
                    else:assert path.parent==original
                    retained=path.parent/('retained-for-public-replay-'+path.name);assert not retained.exists();caller_before[str(path)]=manager_tree(path);path.rename(retained);moved.append((path,retained))
                write(case/'CALLERS-UNAVAILABLE.json',dict(paths=list(caller_before),retained=[str(q) for _,q in moved],trees=caller_before));assert all(not path.exists() for path,_ in moved)
                for generation in [1,2]:
                    supplied=parent if generation==1 else archive(case,name+'-generation-1',parent)
                    output=case/('generation-'+str(generation));process=run(case,'generation-'+str(generation),'replay',['--bundle',str(supplied),'--runtime',str(runtime),'--out',str(output)])
                    bundle=output/'bundle';run(case,'verify-'+str(generation),'verify',['--bundle',str(bundle),'--out',str(case/('verification-'+str(generation)))]);assert not (output/'FAILURE.json').exists() and all(not path.exists() for path,_ in moved)
                    result=bundle/'result';controller=json.loads((result/('CONTROLLER.json' if args.managed_source else 'DRIVER.json')).read_bytes());assert controller['upstream_api_called'] and (controller.get('upstream_success',True))
                    ledger=json.loads((result/'process/process.json').read_bytes());assert ledger['exit_code']==0 and not ledger['owned_live_after_cleanup']
                    output_record=json.loads((result/'outputs.json').read_bytes());value=next(item for item in output_record if item['root']=='published' and item['path'] in ['summary.tsv','total.tsv'])['members']['']['pin'];raw=(result/'objects'/value['sha256']).read_bytes()
                    assert b'\n5\t9007199254740993\t1e-300' in raw
                    rows.append(dict(case=name,generation=generation,supplied_as_zip=generation==2,process=process,bundle=str(bundle),manifest=pin(bundle/'manifest.json')));parent=bundle;print(name+' generation '+str(generation)+' PASS',flush=True)
                assert pin(original_bundle/'manifest.json')==original_marker
            finally:
                for path,retained in reversed(moved):
                    assert not path.exists() and retained.exists() and path.parent==retained.parent;retained.rename(path);assert manager_tree(path)==caller_before[str(path)]
                moved.clear();write(case/'CALLERS-RESTORED.json',dict(original_caller_trees_restored=True,paths=list(caller_before)))
        negative=[]
        if args.managed_source:
            for mode in ['conda','conda-apptainer']:
                name='prefix-output-mismatch-'+mode;case=stage/name;case.mkdir();parent=source/'cases'/('install-'+mode)/'execution/bundle';target=case/'replay'
                process=run(case,'replay','replay',['--bundle',str(parent),'--runtime',str(runtime),'--out',str(target)],2);failure=json.loads((target/'FAILURE.json').read_bytes());assert failure['exit_code']==2 and 'workflow/software.json' in failure['error'] and not (target/'bundle/COMPLETED.json').exists();assert json.loads((target/'bundle/result/CONTROLLER.json').read_bytes())['upstream_success'];run(case,'reject-failed-outer','verify',['--bundle',str(target),'--out',str(case/'rejected')],2);negative.append(dict(case=name,process=process,expected_exit=2));print(name+' PASS',flush=True)
            case=stage/'missing-provider';case.mkdir();request=json.loads((source/'cases/reuse-conda/recipe.json').read_bytes());providers=case/'providers';shutil.copytree(Path(request['managed_software']['providers']),providers);request['managed_software']['providers']=str(providers);write(case/'recipe.json',request)
            run(case,'parent','run',['--request',str(case/'recipe.json'),'--runtime',str(runtime),'--out',str(case/'parent')]);(providers/'ADOPTED.json').rename(providers/'RETAINED-ADOPTED.json');target=case/'replay';process=run(case,'replay','replay',['--bundle',str(case/'parent/bundle'),'--runtime',str(runtime),'--out',str(target)],3);assert json.loads((target/'FAILURE.json').read_bytes())['exit_code']==3 and not (target/'execution').exists();run(case,'reject-skipped-outer','verify',['--bundle',str(target),'--out',str(case/'rejected')],2);negative.append(dict(case='missing-provider',process=process,expected_exit=3));print('missing-provider PASS',flush=True)
        if immutable:assert {key:dict(root=value['root'],nodes=manager_tree(Path(value['root']))) for key,value in immutable.items()}==immutable
        assert pin(jar)==jar_pin
        result=dict(status='PASS_PUBLIC_CLI_GENUINE_DIRECTORY_AND_ZIP_REPLAY_WITH_CALLERS_UNAVAILABLE',cases=rows,negative_controls=negative,jar=jar_pin,original_callers_restored=True,original_software_unchanged=True,complete_qualification=False,new_catalog_credit=0,whole_goal_complete=False)
        write(stage/'COMPLETED.json',result);write(out/'COMPLETED.json',result);print('PASS_PUBLIC_REPLAY_GENERATIONS '+str(len(rows)),flush=True)
    except BaseException as failure:
        if moved:
            for path,retained in reversed(moved):
                if not path.exists() and retained.exists():retained.rename(path)
        write(out/'FAILURE.json',dict(error=repr(failure),traceback=traceback.format_exc(),native_stage=str(stage),required_action='Retain all parents, new results, original logs and this failed qualification. Restore any named caller directories after checking both paths. Resolve the cause and retry NEW paths; no older success replaces the failed command.'));raise
if __name__=='__main__':main()
