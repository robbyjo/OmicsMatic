# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the full original hifiasm mode corpus; raw outputs remain source controls, not admitted user assemblies."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_HIFIASM_MODE_CONTROLS',full_adapter_admitted=False,implementation_credit=False,analysis_admitted=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());assert producer['status']=='prepared-twenty-nine-explicit-original-hifiasm-assembly-controls' and pin(fixtures/'request.json')==producer['request'];before={p.relative_to(fixtures).as_posix():pin(p) for p in fixtures.iterdir() if p.is_file()};driver=ROOT/'tools/literature/java/org/jlinalg/cli/HifiasmAssemblyControl.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(driver)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.HifiasmAssemblyControl',linux(fixtures/'request.json'),linux(out/'original')]
        with (out/'original.stdout').open('xb') as first,(out/'original.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=20000)
        save(out/'original.process.json',dict(command=command,exit_code=run.returncode));report=json.loads((out/'original/COMPLETED.json').read_bytes());assert run.returncode==0 and report['status']=='PASS_COMPLETE_ORIGINAL_HIFIASM_CONTROL_BATCH_ONLY' and len(report['cases'])==29;assert all(pin(fixtures/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-twenty-nine-complete-original-hifiasm-assembly-mode-controls-only',artifact=built['artifact'],original_report=pin(out/'original/COMPLETED.json'),input_pins=before,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [driver,Path(__file__).resolve()]},source_namespaces_patched=False,yak_runtime_used=False,host_binding_and_native_graph_audit_unfinished=True)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original mode checks')
    raise SystemExit(main())
