#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check every frozen evidence member and replay the complete original saved comparisons."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,subprocess,sys,traceback,zipfile
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,load,save,sources,check,environment
ROOT=HERE.parents[2]
DATA=ROOT/'docs/literature/arrow1201-pristine-workflow-validation'

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--runtime',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args();out=args.out.absolute();owned=False
    try:
        runtime=args.runtime.resolve()
        if out!=out.resolve() or not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):
            raise ValueError('Use a NEW canonical monocle-runtime verification directory')
        if out.is_relative_to(runtime) or runtime.is_relative_to(out):raise ValueError('Output overlaps protected provider')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if sys.platform!='linux':raise Unavailable('Complete original saved comparisons require permitted Linux/WSL')
        if (DATA/'INVALIDATION.json').exists():
            raise ValueError('This frozen candidate was invalidated for patched C++ packaging; retain it and qualify NEW pristine evidence before admission')
        proof=load(DATA/'QUALIFICATION.json')
        if (proof['original_controls'],proof['complete_returns'],proof['original_failures_without_result'])!=(13,10,3):
            raise ValueError('Qualified original control population differs')
        if proof['adapter_sources']!=sources():raise ValueError('Qualified adapter source bytes differ; qualify NEW evidence')
        for name,expected in proof['module_sources'].items():
            if pin(HERE/name)!=expected:raise ValueError('Qualified original helper/document differs: '+name)
        bundle=DATA/'original-workflow-and-installation-evidence.zip'
        if pin(bundle)!=proof['evidence_bundle']:raise ValueError('Complete qualified original evidence bundle differs')
        frozen=out/'frozen';frozen.mkdir()
        with zipfile.ZipFile(bundle) as archive:
            names=archive.namelist()
            if len(names)!=proof['bundle_members'] or len(names)!=len(set(n.casefold() for n in names)):
                raise ValueError('Incomplete or colliding evidence members')
            manifest=json.loads(archive.read('BUNDLE-MANIFEST.json'))['files']
            if set(names)!=set(manifest)|{'BUNDLE-MANIFEST.json','QUALIFICATION.json'}:
                raise ValueError('Complete original evidence inventory differs')
            inner=json.loads(archive.read('QUALIFICATION.json'))
            if inner!={k:v for k,v in proof.items() if k not in ['evidence_bundle','bundle_members']}:
                raise ValueError('Outer qualification differs from complete frozen record')
            for name in names:
                path=PurePosixPath(name)
                if path.is_absolute() or '..' in path.parts or '\\' in name:raise ValueError('Unsafe evidence member')
                raw=archive.read(name)
                if name in manifest and dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=manifest[name]:
                    raise ValueError('Original evidence member differs: '+name)
                target=frozen.joinpath(*path.parts);target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw)
        ready=load(runtime/'READY.json');check(ready)
        if ready['adapter_sources']!=proof['adapter_sources']:raise ValueError('Provider does not bind qualified adapter')
        for name in ['home','tmp']:(out/name).mkdir()
        rscript=Path(ready['r_prefix'])/'bin/Rscript';env=environment(ready,out);reports={}
        for name,expected in proof['comparisons'].items():
            case=frozen/'current-workflow'/name;directory=out/name;directory.mkdir()
            command=[str(rscript),'--vanilla',str(HERE/'CompareWorkflow.R'),str(case/'original-reference'),
                str(case/'adapter/original'),str(directory/'COMPARISON.json')]
            with (directory/'original.stdout').open('xb') as stdout,(directory/'original.stderr').open('xb') as stderr:
                child=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
            save(directory/'PROCESS.json',dict(command=command,exit_code=child.returncode))
            if child.returncode:raise ValueError('Complete saved original comparison differs: '+name)
            result=load(directory/'COMPARISON.json')
            if result!=expected:raise ValueError('Qualified complete comparison report differs: '+name)
            reports[name]=result
        command=[str(rscript),'--vanilla',str(HERE/'InspectConditionDifferences.R'),
            str(frozen/'current-workflow/query-missing-column/original-reference/complete-conditions.rds'),
            str(frozen/'current-workflow/query-missing-column/adapter/original/complete-conditions.rds'),str(out/'CONDITION-DIFFERENCES.json')]
        with (out/'conditions.stdout').open('xb') as stdout,(out/'conditions.stderr').open('xb') as stderr:
            child=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        if child.returncode or load(out/'CONDITION-DIFFERENCES.json')!=proof['complete_condition_difference']:
            raise ValueError('Disclosed complete missing-column condition context differs')
        check(ready)
        (out/'WARNINGS.md').write_text('# Complete saved original objects replayed; review original conditions\n\n'
            'Ten returns and three original failures were replayed without new I/O analysis. The missing-column '
            'error differs only in its complete trace field, which retains actual caller context. No condition '
            'field was deleted or coerced, and no live execution-context identity is claimed.\n\n'+ACTION+'\n',encoding='utf-8')
        save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_FROZEN_ARROW1201_WORKFLOW_AND_ORIGINAL_R_REPLAY',
            original_controls=13,complete_returns=10,original_failures_without_result=3,bundle_members=len(names),
            all_frozen_members_checked=True,complete_return_identity_and_numeric_payloads=True,
            complete_RNG_compared=True,whole_condition_identity_controls=12,
            complete_missing_column_difference='trace only; actual caller context retained',
            no_condition_fields_deleted_or_coerced=True,live_execution_context_identity_claimed=False,
            original_io_or_model_fitting_executed=False,comparisons=reports,new_family_credit=0,whole_goal_complete=False))
        print('PASS_COMPLETE_FROZEN_ARROW1201_WORKFLOW_AND_ORIGINAL_R_REPLAY',flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_ARROW1201_CHECKPOINT_UNAVAILABLE' if code==3 else 'FAIL2_ARROW1201_CHECKPOINT_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:
                code=2;print('FAIL2_CHECKPOINT_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
