# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently audit full original Monocle2 probabilities, BH adjustment and preserved rerun results."""
import argparse
import json
import math
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--probe',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';probe=a.probe.resolve(strict=True);out=a.out.resolve();assert probe.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    result=json.loads((probe/'RESULT.json').read_bytes());assert result['status']=='COMPLETED_ORIGINAL_MONOCLE2_DIFFERENTIAL_READINESS_PROBE' and result['original_package_loaded'] is True
    assert len(result['cases'])==3 and all(c['returned'] and c['rows']==120 and c['all_status_OK'] for c in result['cases'])
    provenance=json.loads((probe/'PROVENANCE.json').read_bytes());assert provenance['runtime_unchanged'] and provenance['Java_adapter_validated'] is False
    guards=json.loads((probe/'source-guards.json').read_bytes());assert guards['R']=='4.2.1' and guards['dplyr']=='1.1.4' and guards['original_version']=='2.26.0' and len(guards['guarded_functions'])==8
    setup=base/'monocle2-linux-reference-setup-3';prefix=setup/'.pixi/envs/default';script=out/'export-original-probabilities.R'
    script.write_text('''a<-commandArgs(TRUE);probe<-a[1];out<-a[2];reports<-list()
for(id in c("condition-adjusted-relative","condition-adjusted-unscaled","progress-spline")) {
 x<-readRDS(file.path(probe,id,"original-tests.rds"));previous<-readRDS(file.path(dirname(probe),"monocle2-legacy-readiness-probe-5",id,"original-tests.rds"))
 stopifnot(identical(x,previous))
 reports[[id]]<-list(gene_ids=as.list(rownames(x)),statuses=as.list(x$status),families=as.list(x$family),p=as.list(sprintf("%.17g",x$pval)),q=as.list(sprintf("%.17g",x$qval)))
}
jsonlite::write_json(reports,file.path(out,"probabilities.json"),pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n')
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','60','/usr/bin/env','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'LANG=C','LC_ALL=C','LANGUAGE=en','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1',linux(prefix/'bin/Rscript'),'--vanilla',linux(script),linux(probe),linux(out)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=90);(out/'export.stdout').write_bytes(r.stdout);(out/'export.stderr').write_bytes(r.stderr);save(out/'export.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    values=json.loads((out/'probabilities.json').read_bytes());reports=[]
    for id,case in values.items():
        assert case['gene_ids']==['gene-'+str(i).zfill(3) for i in range(1,121)] and case['statuses']==['OK']*120
        pvalues=list(map(float,case['p']));qvalues=list(map(float,case['q']));assert all(math.isfinite(v) and 0<=v<=1 for v in [*pvalues,*qvalues])
        order=sorted(range(120),key=lambda i:pvalues[i]);expected=[0.0]*120;minimum=1.0
        for index in reversed(range(120)):
            ordinal=order[index];minimum=min(minimum,120.0/(index+1)*pvalues[ordinal]);expected[ordinal]=minimum
        error=0.0
        for x,y in zip(expected,qvalues):
            assert abs(x-y)<=5e-13*max(abs(x),1e-300),(id,x,y);error=max(error,abs(x-y))
        reports.append(dict(id=id,genes=120,finite_probabilities=120,BH_max_absolute_error=error,complete_original_table_identical_to_prior_scientific_fit=True))
    save(out/'COMPLETED.json',dict(status='audited-original-Monocle2-readiness-not-Java-adapter-admission',source_pin=pin(Path(__file__)),probe_result=pin(probe/'RESULT.json'),probe_provenance=pin(probe/'PROVENANCE.json'),source_guards=guards,cases=reports,total_original_tests=360,
        selected_original_functions_guarded=8,full_runtime_files_and_links_unchanged=provenance['existing_runtime_files_and_links_checked'],Java_adapter_validated=False,implementation_credit=False,whole_paper_reproduced=False,whole_goal_complete=False))
    print('Audited 360 finite original p/q values, independent BH, unchanged complete rerun tables and exact source guards')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original probability/source audits')
    main()
