# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute33 unchanged/additive gates with explicit before/after source and metadata bindings."""
import argparse,ast,json,subprocess,sys,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_CISTARGET_SCIENTIFIC_GATE_EXECUTION',whole_goal_complete=False);worker=None
    try:
        source=ROOT/'tools/literature/run_cistarget_catalog_checks.py';tree=ast.parse(source.read_text(encoding='utf8'))
        assignments=[n for n in ast.walk(tree) if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='names' for t in n.targets)]
        assert len(assignments)==1;names=ast.literal_eval(assignments[0].value);assert len(names)==len(set(names))==33
        paths=[Path(__file__).resolve(),source,ROOT/'tools/literature/cistarget_catalog.py',ROOT/'tools/literature/cistarget-registry-spelling.json',*[ROOT/'tools/literature'/(n+'.py') for n in names]]
        before={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
        data=ROOT/'docs/literature/omics-2023-2026';metadata={p.relative_to(ROOT).as_posix():pin(p) for p in [data/'papers.json',data/'tool-inventory.json',data/'summary.json',data/'SHA256SUMS',ROOT/'tools/literature/reviewed-uses.json']}
        for name in before:target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        save(out/'SOURCE-BEFORE.json',before);save(out/'METADATA-BEFORE.json',metadata);checks=out/'checks';command=[sys.executable,str(source),'--out',str(checks)];save(out/'command.json',command)
        started=time.monotonic()
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:
            worker=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/'live-process.json',dict(pid=worker.pid,command=command))
            code=worker.wait(timeout=3600)
        save(out/'process.json',dict(pid=worker.pid,exit_code=code,elapsed_seconds=time.monotonic()-started));assert code==0,'Read retained stdout/stderr and per-gate records'
        assert before=={n:pin(ROOT/n) for n in before},'Source changed during gate execution; no current-source gate admission'
        assert metadata=={n:pin(ROOT/n) for n in metadata},'Catalog changed during gate execution; preserve and retry NEW paths'
        proof=json.loads((checks/'COMPLETED.json').read_bytes());assert proof['status']=='passed-thirty-three-frozen-and-additive-cisTarget-gates' and len(proof['checks'])==33 and proof['current_reviews']==1018
        state.update(status='PASS33_CISTARGET_SCIENCE_GATES_WITH_UNCHANGED_EXECUTED_SOURCES_AND_METADATA',source_pins=before,input_pins=metadata,checks=proof['checks'],checks_receipt=pin(checks/'COMPLETED.json'),scientific_verifiers_changed=False,source_allowlist_changed=False)
    except Exception as failure:
        if worker is not None and worker.poll() is None:worker.terminate();worker.wait(timeout=30)
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS33_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable executed-source and metadata binding checks')
    raise SystemExit(main())
