# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind six exact compiled original hifiasm editions to their unchanged declared host dependencies."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','references','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_RUNTIME_BINDINGS',editions={},analysis_admitted=False,implementation_credit=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};references=a.references.resolve();reference=json.loads((references/'COMPLETED.json').read_bytes());assert reference['status']=='passed-requested-original-hifiasm-source-builds-only';editions=list(reference['editions'])
        def execute(edition):
            home=out/edition;home.mkdir();receipt=references/edition/'receipts/COMPLETED.json';assert json.loads(receipt.read_bytes())['status']=='PASS_ORIGINAL_HIFIASM_SOURCE_COMPILATION_ONLY';request=home/'request.json';save(request,dict(schema_version=1,mode='BIND_REFERENCE_ONLY',build_receipt=linux(receipt),out=linux(home/'runtime')));command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.HifiasmRuntimeCli','--request',linux(request)];print('Binding original',edition,flush=True)
            with (home/'binding.stdout').open('xb') as first,(home/'binding.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=2400)
            save(home/'process.json',dict(command=command,exit_code=run.returncode));report=json.loads((home/'runtime/COMPLETED.json').read_bytes());print(edition,report['status'],flush=True);return edition,dict(exit_code=run.returncode,receipt=dict(path=(home/'runtime/COMPLETED.json').relative_to(ROOT).as_posix(),**pin(home/'runtime/COMPLETED.json')),status=report['status'])
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for edition,record in pool.map(execute,editions):state['editions'][edition]=record
        assert all(r['exit_code']==0 and r['status']=='PASS_BOUND_ORIGINAL_HIFIASM_RUNTIME_ONLY' for r in state['editions'].values());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state.update(status='passed-six-original-hifiasm-host-runtime-bindings-only',artifact=build['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-runtime admission checks')
    raise SystemExit(main())
