# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject ambiguous or lossy scientific inputs through the actual CLI before starting any original worker."""
import argparse,copy,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_INPUT_REJECTION_CONTROLS',checks=[],whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};base=json.loads((a.fixtures/'1.1.8.json').read_bytes());cases={c['id']:c for c in base['analyses']}
        tests=[('float32','prerank'),('schema','prerank'),('ragged','prerank'),('missing-row','prerank'),('remote-library','prerank'),('nonfinite','prerank'),('underflow','prerank'),('unpaired-surrogate','prerank'),('duplicate-samples','gsea-gene_set'),('class-order-length','gsea-gene_set'),('three-classes','gsea-gene_set'),('two-rankings','prerank')]
        for label,java,is_linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:
            path=lambda p:linux(p) if is_linux else str(p);prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if is_linux else [java]
            for name,kind in tests:
                home=out/label/name;home.mkdir(parents=True);case=copy.deepcopy(cases[kind]);source=ROOT/Path(case['input']['path']).relative_to('/mnt/d/projects/JLinAlg');data=json.loads(source.read_bytes())
                if name=='float32':data['numeric_dtype']='float32'
                elif name=='schema':data['schema_version']=2
                elif name=='ragged':data['values'][0].append(0)
                elif name=='missing-row':data['values'].pop()
                elif name=='remote-library':data['gene_sets']='GO_Biological_Process_2023'
                elif name=='nonfinite':data['values'][0][0]=float('nan')
                elif name=='underflow':data['values'][0][0]='LEXICAL_UNDERFLOW'
                elif name=='unpaired-surrogate':data['ids'][0]='\ud800'
                elif name=='duplicate-samples':data['columns'][1]=data['columns'][0]
                elif name=='class-order-length':data['classes'].pop()
                elif name=='three-classes':data['classes'][0]='third-class'
                elif name=='two-rankings':data['columns'].append('second-ranking');[row.append(0) for row in data['values']]
                encoded=json.dumps(data,ensure_ascii=True).replace('"LEXICAL_UNDERFLOW"','1e-999');(home/'prepared.json').write_text(encoded,encoding='utf8');case['input']['path']=path(home/'prepared.json');request=copy.deepcopy(base);request.update(out=path(home/'result'),analyses=[case]);save(home/'request.json',request);command=prefix+['-cp',path(jar),'org.jlinalg.cli.GseapyWorkflowCli','--request',path(home/'request.json')];run=subprocess.run(command,capture_output=True,timeout=120);(home/'analysis.stdout').write_bytes(run.stdout);(home/'analysis.stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=command,exit_code=run.returncode));report=json.loads((home/'result/analysis-status.json').read_bytes());assert run.returncode==2 and report['status']=='FAIL2_NO_RESULT_ADMITTED' and not report['result_admitted'] and not(home/'result/worker').exists(),(label,name,report);state['checks'].append(dict(jvm=label,input=name,exit_code=2,status=pin(home/'result/analysis-status.json')))
            print(label,'12 malformed/ambiguous/lossy input families rejected before worker startup',flush=True)
        assert len(state['checks'])==36 and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-three-JVM-twelve-scientific-input-rejections-before-worker',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},analysis_installed_software=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable input rejection checks')
    raise SystemExit(main())
