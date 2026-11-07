# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute documented native root ORDER graph under actual -jar entry points on three JVMs."""
import argparse
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((base/'root-build-1/COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    source=ROOT/'examples/monocle-tools';wanted={p.relative_to(ROOT).as_posix():pin(p) for p in source.rglob('*') if p.is_file()};runs=[]
    for label,java in [('windows-java21',[str(JAVA21/'java.exe')]),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'])]:
        target=out/label;target.mkdir();(target/'prepared-graph.json').write_bytes((source/'prepared-graph.json').read_bytes());(target/'order.json').write_bytes((source/'order.template.json').read_bytes())
        command=[*java,'-jar',linux(jar) if label.startswith('linux') else str(jar),'monocle-tools','ORDER','--request',linux(target/'order.json') if label.startswith('linux') else str(target/'order.json')]
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(target/'stdout.log').write_bytes(r.stdout);(target/'stderr.log').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
        result=target/'ordered-result';status=json.loads((result/'analysis-status.json').read_bytes());assert status['result_admitted']
        cells=json.loads((result/'results/cell-pseudotime.json').read_bytes());assert [v['id'] for v in cells]==['a','b','c'] and [float(v['pseudotime_token']) for v in cells]==[3,0,5]
        roots=json.loads((result/'results/selected-roots.json').read_bytes());assert len(roots)==1 and roots[0]['cell_id']=='b' and roots[0]['zero_based_cell_index']==1
        row=dict(JVM=label,command=command,exit_code=r.returncode,cell_values=[3,0,5],selected_cell='b');save(target/'process.json',row);runs.append(row);print(label,': native root example [3,0,5]',flush=True)
    assert all(pin(ROOT/n)==v for n,v in wanted.items()) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='executed-documented-root-ORDER-example-on-three-JVMs',artifact=artifact,source_pins={**wanted,Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},runs=runs,R_required=False,whole_paper_reproduced=False,implementation_credit=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example/output checks')
    main()
