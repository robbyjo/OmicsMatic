# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute one complete public scFates CLI control and retain its actual process/status evidence."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.resolve().relative_to(ROOT).as_posix()
def local(path):assert path.startswith('/mnt/d/projects/JLinAlg/');return ROOT/path.removeprefix('/mnt/d/projects/JLinAlg/')
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['build','fixtures','out']:parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--control',required=True,choices=['positive-batch','quoted-identifiers','late-source-error','late-sampling-error','nonconvergence-policy','invalid-zero-iterations']);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_PUBLIC_CLI_CONTROL',control=a.control,implementation_credit=False,whole_goal_complete=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_SCFATES_COORDINATE_WORKFLOW_ONLY';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};fixture=json.loads((a.fixtures/'COMPLETED.json').read_bytes());assert fixture['status']=='PASS_EXACT_ORIGINAL_SCFATES_PUBLIC_INPUT_CONTROLS_PREPARED_ONLY';assert all(pin(a.fixtures/n)==want for n,want in fixture['files'].items());request_path=a.fixtures/'requests'/(a.control+'.json');request=json.loads(request_path.read_bytes());run_home=local(request['out']);assert not run_home.exists();expected=0 if a.control in ['positive-batch','quoted-identifiers'] else 2;command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(jar),'org.jlinalg.cli.ScfatesWorkflowCli','--request',linux(request_path)];save(out/'STARTED.json',dict(command=command,expected_exit=expected,request=pin(request_path),artifact=build['artifact']));run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=3000);(out/'stdout').write_bytes(run.stdout);(out/'stderr').write_bytes(run.stderr);state.update(command=command,exit_code=run.returncode,expected_exit=expected,run_directory=run_home.relative_to(ROOT).as_posix(),artifact=build['artifact']);assert run.returncode==expected,run.stderr.decode('utf8',errors='replace')[-4000:]
        if a.control=='invalid-zero-iterations':assert not run_home.exists() and b'FAIL2' in run.stderr
        else:
            admission=json.loads((run_home/'analysis-status.json').read_bytes());assert admission['result_admitted']==(expected==0) and (run_home/'WARNINGS.md').is_file();state['analysis_status']=admission
            if expected==0:assert admission['status']=='PASS_ORIGINAL_SCFATES_WORKFLOWS' and len(admission['cases'])==len(request['analyses'])
            elif a.control.startswith('late-'):
                worker=json.loads((run_home/'worker/COMPLETED.json').read_bytes());assert worker['status']=='FAIL2_ORIGINAL_SCFATES_BATCH_UNADMITTED' and len(worker['earlier_cases_provisional'])==(2 if a.control=='late-source-error' else 1);assert ('IndexError' if a.control=='late-source-error' else 'ValueError') in worker['error'];state['earlier_cases_provisional']=len(worker['earlier_cases_provisional'])
            elif a.control=='nonconvergence-policy':assert 'NOT CONVERGED' in admission['error'] and (run_home/'worker/COMPLETED.json').is_file()
        assert all(pin(ROOT/n)==want for n,want in build['source_pins'].items());state.update(status='PASS_COMPLETE_SCFATES_PUBLIC_CLI_CONTROL_ONLY',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},analysis_admitted=expected==0)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
