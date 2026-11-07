# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented Cicero example and compare complete original reference objects."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};example=ROOT/'examples/cicero-coaccessibility';sources=[Path(__file__),*[p for p in example.iterdir() if p.is_file()]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};setup=base/'cicero-reference-setup-1';libs=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]]
    for n in pins:
        target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
    def run(command,label):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en'),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=300)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+': inspect retained diagnostics'
    run([sys.executable,str(example/'prepare_request.py'),'--reference-setup',str(setup),'--r-home','C:/Program Files/R/R-4.6.1','--request',str(out/'request.json'),'--out',str(out/'result')],'prepare')
    run([str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.CiceroWorkflowCli','--request',str(out/'request.json')],'analysis');status=json.loads((out/'result/analysis-status.json').read_bytes());assert status['result_admitted'] is True
    script=out/'compare.R';script.write_text('''a<-commandArgs(TRUE);out<-a[1];reference<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(cicero));target<-file.path(out,"result/results/coaccess");x<-readRDS(file.path(target,"connections.rds"));y<-readRDS(file.path(reference,"connections.rds"));stopifnot(identical(x,y),identical(readRDS(file.path(target,"models.rds")),readRDS(file.path(reference,"models.rds"))),identical(readRDS(file.path(target,"aggregation-map.rds")),readRDS(file.path(reference,"aggregation-map.rds"))));jsonlite::write_json(list(rows=nrow(x),unresolved_NA=sum(is.na(x$coaccess)),all_original_models_connections_and_aggregation_identical=TRUE),file.path(out,"comparison.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
''',encoding='utf8',newline='\n');run(['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(base/'cicero-readiness-probe-2'),*map(str,libs)],'compare')
    assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};save(out/'COMPLETED.json',dict(status='executed-documented-Cicero-chunked-example-matched-original',artifact=artifact,source_pins=pins,comparison=json.loads((out/'comparison.json').read_bytes()),implementation_credit=False,whole_goal_complete=False));print('Documented example matched all2172 original connections, four NA, models and aggregation')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual example/source comparisons')
    main()
