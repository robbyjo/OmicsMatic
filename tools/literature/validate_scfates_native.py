# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original-observation or compiled-tree-scan audits on three actual JVMs."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','reference','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--kind',choices=['root','workflow'],required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_THREE_JVM_NATIVE_AUDIT',kind=a.kind,runs=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_SCFATES_COORDINATE_WORKFLOW_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');reference=a.reference.resolve();clazz='ScfatesRootScanMatrix' if a.kind=='root' else 'ScfatesWorkflowAuditMatrix'
        for label,java,is_linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir()
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if is_linux else str(p)
            def command(args):return ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java,*args] if is_linux else [java,*args]
            version=subprocess.run(command(['-version']),capture_output=True,timeout=60);(home/'version.stdout').write_bytes(version.stdout);(home/'version.stderr').write_bytes(version.stderr);v=(version.stdout+version.stderr).decode('utf8',errors='replace');assert version.returncode==0 and ('"'+major+'.' in v or '"'+major+'"' in v);cmd=command(['-Xmx3g','-cp',path(jar),'org.jlinalg.cli.'+clazz,path(reference),path(home/'native.json')]);run=subprocess.run(cmd,cwd=ROOT,capture_output=True,timeout=600);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);record=dict(label=label,command=cmd,exit_code=run.returncode);save(home/'process.json',record);assert run.returncode==0,run.stderr.decode('utf8',errors='replace')[-4000:];report=json.loads((home/'native.json').read_bytes())
            if a.kind=='root':assert report['status']=='PASS_NATIVE_SCFATES_TREE_SCAN_EXACT_COMPILED_ORIGINAL_ONLY' and report['cases']==31 and report['numeric_states']==21593 and not report['general_asymmetric_graph_support']
            else:assert report['status']=='PASS_COMPLETE_NATIVE_SCFATES_OBSERVED_WORKFLOW_AUDIT_ONLY' and len(report['cases'])==9 and not report['public_execution_adapter_batch_admitted'] and not report['independent_trajectory_refit']
            record['report']=report;state['runs'].append(record)
        assert all(pin(ROOT/n)==want for n,want in built['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_THREE_JVM_SCFATES_'+a.kind.upper()+'_AUDITS_ONLY',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
