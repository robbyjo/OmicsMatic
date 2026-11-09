# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete original graph fields and native FASTA conversion on three JVMs without admitting a parent batch."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_NATIVE_GFA_COMPONENT',runs=[],full_adapter_admitted=False,implementation_credit=False,original_batch_relabelled=False,analysis_admitted=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());assert producer['status']=='prepared-complete-retained-hifiasm-graph-components-only' and pin(fixtures/'cases.json')==producer['cases'];driver=ROOT/'tools/literature/java/org/jlinalg/cli/HifiasmGfaAudit.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(driver)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
        for label,java,is_linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir();path=lambda p:linux(p) if is_linux else str(p);prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if is_linux else [java];version=subprocess.run(prefix+['-version'],capture_output=True,timeout=60);(home/'version.stderr').write_bytes(version.stderr);s=(version.stdout+version.stderr).decode();assert version.returncode==0 and ('"'+major+'.' in s or '"'+major+'"' in s);command=prefix+['-Xmx3g','-cp',path(classes)+(':' if is_linux else ';')+path(jar),'org.jlinalg.cli.HifiasmGfaAudit',path(fixtures/'cases.json'),path(ROOT),path(home/'native')]
            with (home/'native.stdout').open('xb') as first,(home/'native.stderr').open('xb') as second:run=subprocess.run(command,stdout=first,stderr=second,cwd=ROOT,timeout=1200)
            save(home/'native.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read '+str(home/'native.stderr');report=json.loads((home/'native/COMPLETED.json').read_bytes());assert report['status']=='passed-complete-retained-original-GFA-and-native-FASTA-component-only' and report['graph_views']==producer['graph_views'] and report['segments']==producer['segment_rows'] and report['links']==producer['link_rows'] and report['placements']==producer['placement_rows'] and report['invalid_families_rejected']==19;state['runs'].append(dict(jvm=label,report=report));print(label,report['graph_views'],'full original graph views and native FASTA exports passed',flush=True)
        assert pin(fixtures/'cases.json')==producer['cases'] and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-three-JVM-original-GFA-and-native-FASTA-component-only',artifact=built['artifact'],cases=producer['cases'],source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [driver,Path(__file__).resolve()]})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete graph/source checks')
    raise SystemExit(main())
