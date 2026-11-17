#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the actual beginner example in every selected software deployment mode."""
from pathlib import Path
import argparse,json,shutil,sys,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from setup_deployment import manager_tree
from qualify_deployment import execute

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','runtime','providers','source-native','native-out','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--resume-from',type=Path,help='Retain and independently verify an already completed preinstalled run after a post-run checker failure')
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=None
    try:
        assert sys.platform=='linux';stage=args.native_out.absolute();assert stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();producer=ROOT/'examples/snakemake-workflow/prepare.py';producer_pin=pin(producer);shutil.copyfile(producer,out/'as-run-example.py');jar=args.jar.resolve();jar_pin=pin(jar);runtime=args.runtime.resolve();source=args.source_native.resolve();rows=[];env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC')
        for mode in ['preinstalled','conda','apptainer','conda-apptainer']:
            case=stage/mode;case.mkdir();example=case/'example';cmd=[sys.executable,'-I','-B',str(producer),'--runtime',str(runtime),'--out',str(example),'--deployment',mode]
            if mode!='preinstalled':cmd+=['--providers',str(args.providers.resolve())]
            if mode in ['conda','conda-apptainer']:cmd+=['--conda-environments',str(source/mode/'working/.snakemake/conda')]
            if mode in ['apptainer','conda-apptainer']:cmd+=['--container-images',str(source/'images')]
            adopted=mode=='preinstalled' and args.resume_from is not None
            if adopted:
                previous=args.resume_from.resolve();assert previous.is_relative_to(ROOT/'build') and (previous/'FAILURE.json').is_file() and not (previous/'COMPLETED.json').exists();failure=json.loads((previous/'FAILURE.json').read_bytes());old=Path(failure['native_stage']);assert old.parent==stage.parent and old.name.startswith('snakemake-public-example-');assert pin(previous/'as-run-example.py')==producer_pin
                example=old/mode/'example';prepared=json.loads((old/mode/'prepare.PROCESS.json').read_bytes());run=json.loads((old/mode/'run.PROCESS.json').read_bytes());assert prepared['exit_code']==run['exit_code']==0 and str(jar) in run['command'];before=json.loads((old/mode/'INPUTS-BEFORE.json').read_bytes());assert before=={key:manager_tree(example/key) for key in ['workflow','data']};write(case/'ADOPTED-RUN.json',dict(previous_failure=pin(previous/'FAILURE.json'),previous_case=str(old/mode),prepare=prepared,run=run,scientific_execution_repeated=False))
            else:
                prepared=execute(case,'prepare',cmd,env,case,60);assert prepared['exit_code']==0
                before={key:manager_tree(example/key) for key in ['workflow','data']};write(case/'INPUTS-BEFORE.json',before)
                cmd=['/usr/bin/java','-jar',str(jar),'snakemake-tools','run','--request',str(example/'recipe.json'),'--runtime',str(runtime),'--out',str(example/'analysis')];run=execute(case,'run',cmd,env,case,1500);assert run['exit_code']==0,(mode,'Inspect retained actual example command diagnostics')
            assert (example/'analysis/workspace/published/summary.tsv').read_bytes()==b'total\thuge\ttiny\n5\t9007199254740993\t1e-300\n'
            invocation=json.loads((example/'analysis/invocation.json').read_bytes());benchmark=Path(invocation['bindings']['workflow'])/'benchmarks/count.tsv';assert benchmark.is_file() and 'cpu_time' in benchmark.read_text().splitlines()[0]
            verified=execute(case,'verify',['/usr/bin/java','-jar',str(jar),'snakemake-tools','verify','--bundle',str(example/'analysis/bundle'),'--out',str(case/'verification')],env,case,900);assert verified['exit_code']==0
            assert before=={key:manager_tree(example/key) for key in ['workflow','data']};rows.append(dict(mode=mode,prepare=prepared,run=run,verify=verified,native_example=str(example),adopted_completed_run=adopted));print(mode+' EXAMPLE PASS',flush=True)
        assert pin(jar)==jar_pin and pin(producer)==producer_pin
        result=dict(status='PASS_ACTUAL_BEGINNER_EXAMPLE_FOUR_ORIGINAL_DEPLOYMENT_MODES',cases=rows,jar=jar_pin,example_source=producer_pin,source_inputs_unchanged=True,synthetic_only=True,complete_qualification=False,new_catalog_credit=0,whole_goal_complete=False);write(stage/'COMPLETED.json',result);write(out/'COMPLETED.json',result)
    except BaseException as error:write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),native_stage=str(stage),required_action='Retain the generated example, request, source reports and every failed attempt. Resolve the named prerequisite or discrepancy and retry NEW paths; do not claim the example was tested in an unpassed deployment mode.'));raise
if __name__=='__main__':main()
