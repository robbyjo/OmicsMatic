# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute all27 declared supported original controls in bounded edition groups; no partial group or user assembly is admitted."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--workers',type=int,default=4);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime') and 1<=a.workers<=6;out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_HIFIASM_FULL_MODE_MATRIX',editions={},full_adapter_admitted=False,implementation_credit=False,analysis_admitted=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());assert producer['status']=='prepared-twenty-seven-explicit-supported-original-hifiasm-controls' and pin(fixtures/'request.json')==producer['request'];before={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.iterdir() if p.is_file()};original=json.loads((fixtures/'request.json').read_bytes());driver=ROOT/'tools/literature/java/org/jlinalg/cli/HifiasmAssemblyControl.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(driver)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');requests=out/'requests';requests.mkdir();editions=list(dict.fromkeys(r['edition'] for r in original['cases']))
        for edition in editions:request=dict(original,cases=[r for r in original['cases'] if r['edition']==edition]);save(requests/(edition+'.json'),request)
        def execute(edition):
            home=out/edition;home.mkdir();command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.HifiasmAssemblyControl',linux(requests/(edition+'.json')),linux(home/'original')];print('Starting original edition matrix',edition,flush=True)
            with (home/'original.stdout').open('xb') as first,(home/'original.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=7000)
            save(home/'original.process.json',dict(command=command,exit_code=run.returncode));report=json.loads((home/'original/COMPLETED.json').read_bytes());print(edition,report['status'],flush=True);return edition,dict(exit_code=run.returncode,report=report)
        with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as pool:
            for edition,record in pool.map(execute,editions):state['editions'][edition]=record
        assert all(r['exit_code']==0 and r['report']['status']=='PASS_COMPLETE_ORIGINAL_HIFIASM_CONTROL_BATCH_ONLY' and not r['report']['analysis_admitted'] for r in state['editions'].values()),'One edition failed; no partial mode matrix admitted';cases=[c for r in state['editions'].values() for c in r['report']['cases']];assert len(cases)==len({r['id'] for r in cases})==27 and {r['mode'] for r in cases}=={'hifi','trio','hic','hifi-ul','trio-ul','hic-ul','ont'};assert all(pin(fixtures/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-twenty-seven-complete-supported-original-hifiasm-mode-controls-only',artifact=built['artifact'],input_pins=before,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [driver,Path(__file__).resolve()]},source_namespaces_patched=False,yak_runtime_used=False,host_binding_and_native_graph_audit_unfinished=True, unsupported_legacy_hic_editions=['0.14.2-r315','0.15.1-r328'],workers=a.workers,case_count=27)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original mode checks')
    raise SystemExit(main())
