# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact original Monocle2 sources and the audited Linux differential runtime profile."""
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    base=ROOT/'build/literature-review/monocle-runtime';out=base/'differential-profile-1';assert not out.exists();out.mkdir()
    audit=json.loads((base/'monocle2-readiness-audit-1/COMPLETED.json').read_bytes());assert audit['total_original_tests']==360
    prefix=base/'monocle2-linux-reference-setup-3/.pixi/envs/default';extra=base/'monocle2-hsmm-reference-setup-1/R-lib';source=base/'monocle2-source-intake-2/source'
    receipt=json.loads((base/'monocle2-source-intake-2/SOURCE.json').read_bytes());assert receipt['version']=='2.26.0' and all(pin(source/n)==v for n,v in receipt['source_members'].items())
    resource=ROOT/'src/main/resources/org/jlinalg/omics/monocle2-differential';assert not resource.exists();resource.mkdir(parents=True)
    files={}
    for path in [source/'DESCRIPTION',*sorted((source/'R').glob('*.R'))]:
        name='2.26.0/'+path.relative_to(source).as_posix();target=resource/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes());files[name]=pin(target)
    (resource/'LICENSE-Artistic-2.0.txt').write_bytes(Path('C:/Program Files/R/R-4.6.1/share/licenses/Artistic-2.0').read_bytes())
    worker=out/'profile.R';worker.write_text('''a<-commandArgs(TRUE);.libPaths(c(a[-1],.Library),include.site=FALSE)
suppressPackageStartupMessages(library(monocle))
names<-c("monocle","VGAM","Biobase","BiocGenerics","Matrix","dplyr","plyr","Rcpp","tibble","jsonlite","RhpcBLASctl","HSMMSingleCell","DDRTree","igraph","leidenbase","MASS","irlba","ggplot2","limma","reshape2","matrixStats")
versions<-setNames(lapply(names,function(n)as.character(packageVersion(n))),names)
jsonlite::write_json(list(r_version=as.character(getRversion()),platform=R.version$platform,required_packages=versions,VGAM_family_formals=lapply(c("negbinomial.size","negbinomial","uninormal","binomialff"),function(n)paste(deparse(formals(get(n,asNamespace("VGAM")))),collapse="\\n"))),a[1],pretty=TRUE,auto_unbox=TRUE)
''',encoding='utf8',newline='\n')
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'LANG=C','LC_ALL=C','LANGUAGE=en',linux(prefix/'bin/Rscript'),'--vanilla',linux(worker),linux(out/'profile.json'),linux(extra),linux(prefix/'lib/R/library')]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    profile=json.loads((out/'profile.json').read_bytes());profile.update(schema_version=1,version='2.26.0',source_url=receipt['archive']['url'],files=files,
        guarded_functions=[*audit['source_guards']['guarded_functions'],'sparseApply','dispersionTable','parametricDispersionFit'],
        source_authentication_scope='Exact package versions and selected original R bodies/formals; not complete dependency/native-DLL source authentication',
        reference_audit=pin(base/'monocle2-readiness-audit-1/COMPLETED.json'))
    save(resource/'SOURCE-PROFILE.json',profile)
    (resource/'NOTICE.txt').write_text('Exact original Monocle 2.26.0 R sources and DESCRIPTION are retained byte for byte from Bioconductor 3.16.\nOriginal author: Cole Trapnell; license Artistic-2.0, included.\nThe separate worker and Java integration are project GPL-3.0-or-later code.\nNo historical paper environment, Monocle3/custom-test equivalence or biological certification is claimed.\n',encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',dict(status='captured-original-Monocle2-differential-source-profile',source_pin=pin(Path(__file__)),resource_pins={p.relative_to(resource).as_posix():pin(p) for p in resource.rglob('*') if p.is_file()},installed_nothing=True,analysis_admitted=False))
    print('Captured',len(files),'original source members and exact audited runtime dependencies')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/profile checks')
    main()
