# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented Linux example and compare its complete original test table."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    prefix=base/'monocle2-linux-reference-setup-3/.pixi/envs/default';extra=base/'monocle2-hsmm-reference-setup-1/R-lib';example=ROOT/'examples/monocle2-differential';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),*example.glob('*')] if p.is_file()}
    for n in pins:
        target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
    def run(command,label):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=180)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+': inspect retained diagnostics'
    wsl=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec']
    run([*wsl,'/usr/bin/python3',linux(example/'prepare_request.py'),'--rscript',linux(prefix/'bin/Rscript'),'--r-home',linux(prefix/'lib/R'),'--r-library',linux(extra),'--r-library',linux(prefix/'lib/R/library'),'--request',linux(out/'request.json'),'--out',linux(out/'result')],'prepare')
    run([*wsl,'/usr/bin/java','-Xmx1g','-cp',linux(jar),'org.jlinalg.cli.Monocle2DifferentialCli','--request',linux(out/'request.json')],'analysis')
    state=json.loads((out/'result/analysis-status.json').read_bytes());assert state['result_admitted'] is True
    script=out/'compare.R';script.write_text('''a<-commandArgs(TRUE);.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(monocle));x<-readRDS(file.path(a[1],"result/results/nb-fixed-relative/tests.rds"));y<-readRDS(file.path(a[2],"nb-fixed-relative-original.rds"));stopifnot(identical(rownames(x),rownames(y)),isTRUE(all.equal(x,y,tolerance=1e-13)));jsonlite::write_json(list(rows=nrow(x),all_original_columns_equal=TRUE),file.path(a[1],"comparison.json"),auto_unbox=TRUE,pretty=TRUE)
''',encoding='utf8',newline='\n')
    run([*wsl,'/usr/bin/env','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'LANG=C','LC_ALL=C',linux(prefix/'bin/Rscript'),'--vanilla',linux(script),linux(out),linux(base/'differential-variants-3'),linux(extra),linux(prefix/'lib/R/library')],'compare')
    assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='documented-example-executed-and-matched-original',artifact=artifact,source_pins=pins,comparison=json.loads((out/'comparison.json').read_bytes()),implementation_credit=False,whole_goal_complete=False))
    print('Documented synthetic example executed; all 12 original rows and columns matched')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual example comparisons')
    main()
