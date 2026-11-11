# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck scientific-table refusals with final JARs; no upstream R rerun."""
import argparse,csv,io,json,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',action='append',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    previous=BASE/'strength-admission-controls-1';report=json.loads((previous/'COMPLETED.json').read_bytes());assert report['status']=='PASS_STRENGTH_SKIP_FAILURE_AND_REHASHED_REFUSALS' and (report['analysis_dispositions'],report['offline_tamper_refusals'])==(15,8);assert all(pin(Path(n))==v for n,v in report['source_pins'].items())
    targets={p.name:p for p in sorted((previous/'tampered').iterdir()) if p.is_dir()};assert len(targets)==8
    target=out/'selected-header';shutil.copytree(BASE/'strength-controls-3x4-javac21-2/analysis',target);manifest=json.loads((target/'COMPLETED.json').read_bytes());relative='results/comparison/selected-strengths.tsv';file=target/relative;rows=list(csv.reader(io.StringIO(file.read_text()),delimiter='\t'))
    for i,row in enumerate(rows):row.append('unrequested' if i==0 else 'extra')
    buffer=io.StringIO(newline='');writer=csv.writer(buffer,delimiter='\t',quoting=csv.QUOTE_ALL,lineterminator='\n');writer.writerows(rows);file.write_text(buffer.getvalue(),encoding='utf8',newline='\n');manifest['files'][relative]=pin(file);save(target/'COMPLETED.json',manifest);targets['selected-header']=target
    records=[];bindings={str(p.resolve()):pin(p) for p in a.jar};bindings[str(Path(__file__).resolve())]=pin(Path(__file__).resolve())
    for i,jar in enumerate(a.jar,1):
        for name,target in targets.items():
            command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx1g','-jar',str(jar.resolve()),'--verify',str(target)];r=subprocess.run(command,capture_output=True,timeout=180);(out/f'jar{i}-{name}.stdout').write_bytes(r.stdout);(out/f'jar{i}-{name}.stderr').write_bytes(r.stderr);records.append(dict(id=name,jar=i,command=command,exit_code=r.returncode,expected_exit=2));assert r.returncode==2,(name,r.stdout,r.stderr)
    assert all(pin(Path(n))==v for n,v in bindings.items());save(out/'COMPLETED.json',dict(status='PASS_FINAL_STRENGTH_REHASHED_REFUSALS',variants=9,refusals=len(records),R_executed=False,source_pins=bindings,records=records));print('PASS',len(records),'final-JAR scientific-table refusals; no R execution')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable refusal checks')
    main()
