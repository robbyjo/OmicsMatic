# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented native normal example and compare its tails to original R controls."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21
from validate_normal_fit import audit,table,close


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/fitdistrplus-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];example=ROOT/'examples/normal-fit';paths=[Path(__file__).resolve(),ROOT/'tools/literature/validate_normal_fit.py',*[p for p in example.glob('*') if p.is_file()]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    for name in pins:
        p=out/'source-snapshot'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/name).read_bytes())
    request=json.loads((example/'request.json').read_bytes());request['samples']=str(example/request['samples']);request['tests']=str(example/request['tests']);request['out']=str(out/'result');save(out/'request.json',request)
    command=[str(JAVA21/'java.exe'),'-Xmx256m','-cp',str(jar),'org.jlinalg.cli.NormalFitToolsCli','--request',str(out/'request.json')]
    with (out/'example.stdout').open('xb') as stdout,(out/'example.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=60)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0
    files={p.relative_to(out/'result').as_posix():p.read_bytes() for p in (out/'result').rglob('*') if p.is_file()};report=audit(files,None);reference=json.loads((base/'original-controls-1/controls.json').read_bytes());prob=next(row for row in reference['probabilities'] if float(row['mean'])==0 and float(row['sd'])==1 and row['tail']=='upper')
    for row in table(files['results/tests.tsv'])[1:]:
        at=[float(v) for v in prob['values']].index(float(row[1]));close(prob['p'][at],row[3]);close(prob['logp'][at],row[4])
    assert report['observations']==2 and report['tests']==5 and [row[0] for row in table(files['results/tests.tsv'])[1:] if row[-1]=='true']==['far-upper']
    assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='passed-executed-normal-fit-example',artifact=artifact,source_pins=pins,comparison=report,original_tail_values_checked=5,implementation_credit=False,whole_goal_complete=False));print('Executed normal example; every fit/test/BH row checked')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/example checks')
    main()
