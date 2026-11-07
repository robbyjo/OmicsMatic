# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain a single real typed marker request, including every rejected attempt."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,save,pin,inventory
from validate_deseq2_inference import JAVA21
BASE=ROOT/'build/literature-review/signac-runtime'

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);p.add_argument('--case',default='pair_wilcox');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    source=BASE/'differential-requests-1';r=json.loads((source/(a.case+'.json')).read_bytes())
    for key in ['counts','features','cells','metadata','data']:r[key]=str((source/r[key]).resolve())
    r['out']=str(out/'analysis');save(out/'request.json',r);jar=BASE/'differential-tools-development/omicsmatic-0.3.7-seurat-differential-development.jar';save(out/'ARTIFACT.json',pin(jar))
    before=inventory();save(out/'BASES-BEFORE.json',before);command=[str(JAVA21),'-Xmx768m','-cp',str(jar),'org.jlinalg.cli.SeuratDifferentialCli','--request',str(out/'request.json')]
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
    save(out/'PROCESS.json',dict(command=command,exit_code=result.returncode));after=inventory();save(out/'BASES-AFTER.json',after);assert before==after
    print('Actual typed request exit',result.returncode)
    status=out/'analysis/analysis-status.json'
    if status.exists():print(status.read_text()[:1800])
    worker=out/'analysis/worker/stderr.log'
    if worker.exists():print('WORKER STDERR:',worker.read_text()[-1800:])
    assert result.returncode==0,'Retained failure; inspect source/status/logs and use a NEW path'
    print('Original marker fit and native capture verification passed')

if __name__=='__main__':main()
