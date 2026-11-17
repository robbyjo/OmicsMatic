#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Two actual original-engine replay generations per managed mode/permission, without original caller paths."""
from pathlib import Path
import argparse,hashlib,json,shutil,sys,traceback

ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from setup_deployment import manager_tree
from qualify_deployment import execute
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['compiled','runtime','source-family','native-out','out']:parser.add_argument('--'+name,type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=None;moved=[];restored=[]
    try:
        assert sys.platform=='linux';source=args.source_family.resolve();source_marker=pin(source/'COMPLETED.json');qualified=json.loads((source/'COMPLETED.json').read_bytes());assert len(qualified['cases'])==18 and qualified['managed_bundles'] and qualified['original_software_unchanged']
        compiled=json.loads((args.compiled/'COMPILED.json').read_bytes());assert compiled['adapter_class_source']=='byte-pinned-jar'
        assert pin(Path(compiled['baseline_jar']['path']))=={key:compiled['baseline_jar'][key] for key in ['bytes','sha256']}
        for name,expected in compiled['classes'].items():assert pin(args.compiled/'classes'/name)==expected
        stage=args.native_out.absolute();assert stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();(stage/'cases').mkdir();(stage/'as-run').mkdir()
        for name in ['qualify_managed_replay.py','qualify_deployment.py','setup_runtime.py','setup_deployment.py']:shutil.copyfile(HERE/name,stage/'as-run'/name)
        immutable=json.loads((source/'IMMUTABLE-AFTER.json').read_bytes());before={name:dict(root=value['root'],nodes=manager_tree(Path(value['root']))) for name,value in immutable.items()};assert before==immutable;write(stage/'IMMUTABLE-BEFORE.json',before);rows=[]
        def run(case,name,operation,request,success=True):
            result=execute(case,name,['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],operation,str(request)],dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),case,2400)
            assert (result['exit_code']==0)==success,(str(case),name,'Inspect retained original process and replay diagnostics')
            return result
        for permission in ['reuse','install']:
            for mode in ['conda','apptainer','conda-apptainer']:
                name=permission+'-'+mode;case=stage/'cases'/name;case.mkdir();original_case=source/'cases'/name;request=json.loads((original_case/'recipe.json').read_bytes());original=original_case/'execution/bundle'
                if permission=='install':
                    # The table is path-independent. Original software provenance is
                    # still retained in working-after; prefix-bearing outputs get
                    # their own explicit mismatch controls below, never normalization.
                    request['outputs']=[value for value in request['outputs'] if value['root']=='published'];write(case/'parent-recipe.json',request);write(case/'PARENT-LAUNCH.json',dict(recipe=str(case/'parent-recipe.json'),runtime=str(args.runtime.resolve()),out=str(case/'parent-execution')))
                    run(case,'parent','managed-bundle',case/'PARENT-LAUNCH.json');original=case/'parent-execution/bundle'
                parent=original;parent_manifest=pin(parent/'manifest.json');captures=json.loads((parent/'capture.json').read_bytes());caller_before={}
                try:
                    for root in captures['roots']:
                        path=Path(root['source']);assert path.parent==original_case and path.name in ['input','data'] and path.is_dir() and not path.is_symlink();retained=original_case/('retained-for-managed-replay-'+path.name);assert not retained.exists();caller_before[str(path)]=manager_tree(path);path.rename(retained);moved.append((path,retained))
                    write(case/'ORIGINAL-CALLERS-UNAVAILABLE.json',dict(paths=[str(path) for path,_ in moved],retained=[str(path) for _,path in moved],caller_trees=caller_before))
                    assert all(not path.exists() for path,_ in moved)
                    for generation in [1,2]:
                        launch=case/('REPLAY-'+str(generation)+'.json');output=case/('generation-'+str(generation));write(launch,dict(bundle=str(parent),runtime=str(args.runtime.resolve()),out=str(output)));process=run(case,'generation-'+str(generation),'replay',launch)
                        bundle=output/'bundle';run(case,'verify-'+str(generation),'verify',bundle);assert not (output/'FAILURE.json').exists() and all(not path.exists() for path,_ in moved)
                        result=bundle/'result';ledger=json.loads((result/'process/process.json').read_bytes());assert ledger['exit_code']==0 and not ledger['owned_live_after_cleanup'];controller=json.loads((result/'CONTROLLER.json').read_bytes());assert controller['upstream_api_called'] and controller['upstream_success'];assert json.loads((bundle/'comparison.json').read_bytes())['result_jobs']>=1
                        outputs=json.loads((result/'outputs.json').read_bytes());table=next(value for value in outputs if value['root']=='published')['members']['']['pin'];assert (result/'objects'/table['sha256']).read_bytes()==b'total\thuge\ttiny\n5\t9007199254740993\t1e-300\n'
                        rows.append(dict(case=name,generation=generation,process=process,bundle=str(bundle),manifest=pin(bundle/'manifest.json')));parent=bundle;print(name+' generation '+str(generation)+' PASS',flush=True)
                    assert pin(original/'manifest.json')==parent_manifest
                finally:
                    for path,retained in reversed(moved):assert not path.exists() and retained.exists() and path.parent==original_case and retained.parent==original_case;retained.rename(path);assert manager_tree(path)==caller_before[str(path)];restored.append(str(path))
                    moved.clear();write(case/'CALLERS-RESTORED.json',dict(original_caller_trees_restored=True,paths=list(caller_before)))
        negative=[]
        for mode in ['conda','conda-apptainer']:
            name='prefix-output-mismatch-'+mode;case=stage/'cases'/name;case.mkdir();parent=source/'cases'/('install-'+mode)/'execution/bundle';launch=case/'REPLAY.json';target=case/'replay';write(launch,dict(bundle=str(parent),runtime=str(args.runtime.resolve()),out=str(target)));process=run(case,'replay','replay',launch,False)
            failure=json.loads((target/'FAILURE.json').read_bytes());assert failure['exit_code']==2 and 'workflow/software.json' in failure['error'] and not (target/'bundle/COMPLETED.json').exists();assert json.loads((target/'bundle/result/CONTROLLER.json').read_bytes())['upstream_success'];run(case,'verify-failed-root','verify',target,False);negative.append(dict(case=name,process=process,expected_exit=2));print(name+' PASS',flush=True)
        case=stage/'cases/missing-provider';case.mkdir();parent_recipe=json.loads((source/'cases/reuse-conda/recipe.json').read_bytes());adoption=case/'provider-adoption';shutil.copytree(Path(parent_recipe['managed_software']['providers']),adoption);parent_recipe['managed_software']['providers']=str(adoption);write(case/'parent-recipe.json',parent_recipe);write(case/'PARENT.json',dict(recipe=str(case/'parent-recipe.json'),runtime=str(args.runtime.resolve()),out=str(case/'parent-execution')));run(case,'parent','managed-bundle',case/'PARENT.json')
        retained=case/'retained-provider-adoption';adoption.rename(retained)
        try:
            launch=case/'REPLAY.json';target=case/'replay';write(launch,dict(bundle=str(case/'parent-execution/bundle'),runtime=str(args.runtime.resolve()),out=str(target)));process=run(case,'replay','replay',launch,False);failure=json.loads((target/'FAILURE.json').read_bytes());assert failure['exit_code']==3 and 'Provider adoption' in failure['error'].replace('provider adoption','Provider adoption') and not (target/'bundle').exists();assert (target/'FAILURE_WARNINGS.md').is_file();run(case,'verify-failed-root','verify',target,False);negative.append(dict(case='missing-provider',process=process,expected_exit=3));print('missing-provider PASS',flush=True)
        finally:assert not adoption.exists();retained.rename(adoption)
        after={name:dict(root=value['root'],nodes=manager_tree(Path(value['root']))) for name,value in immutable.items()};write(stage/'IMMUTABLE-AFTER.json',after);assert after==before and source_marker==pin(source/'COMPLETED.json')
        write(stage/'COMPLETED.json',dict(status='PASS_TWELVE_GENUINE_MANAGED_REPLAY_GENERATIONS_WITHOUT_ORIGINAL_CALLERS_AND_THREE_FAILURE_SKIP_CONTROLS',generations=rows,negative=negative,original_callers_restored=restored,source_family_marker=source_marker,original_software_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));shutil.copyfile(stage/'COMPLETED.json',out/'NATIVE-COMPLETED.json');print('PASS_TWELVE_MANAGED_REPLAY_GENERATIONS_THREE_NEGATIVES',flush=True)
    except BaseException as error:
        record=dict(error=repr(error),traceback=traceback.format_exc(),native=str(stage),required_action='Retain every original, replay, partial result and diagnostic. Resolve the named discrepancy and retry NEW paths. Never normalize differing scientific outputs, relocate installed Conda prefixes or replace an unmatched replay with parent success.')
        write(out/'FAILURE.json',record)
        if stage is not None:write(stage/'FAILURE.json',record)
        raise

if __name__=='__main__':main()
