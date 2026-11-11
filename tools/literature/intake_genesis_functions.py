# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record installed GENESIS scientific function/method signatures from its exact private runtime; no data analysis."""
import argparse
import json
import os
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'
PROGRAM=r'''
.libPaths(c(private_library,file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1",requireNamespace("GENESIS",quietly=TRUE),utils::packageDescription("GENESIS",fields="Version")=="2.42.0",
    dirname(find.package("GENESIS"))==normalizePath(private_library,winslash="/"))
packages <- c("GENESIS","GWASTools","SNPRelate","gdsfmt","SeqArray","SeqVarTools","Matrix")
versions <- data.frame(package=packages,version=vapply(packages,function(p) utils::packageDescription(p,fields="Version"),character(1L)),
    location=vapply(packages,find.package,character(1L)))
utils::write.table(versions,file.path(out,"actual-package-versions.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
ns <- asNamespace("GENESIS");names <- grep("^(pcair|pcairPartition|pcrelate|fitNullModel|assocTestSingle|assocTestAggregate|nullModel)",ls(ns,all.names=TRUE),value=TRUE)
result <- list()
for(name in names) {
    fn <- get(name,envir=ns)
    if(is.function(fn)) {
        dput(formals(fn),file=file.path(out,paste0(name,"-formals.R")))
        dput(body(fn),file=file.path(out,paste0(name,"-body.R")))
        result[[name]] <- data.frame(function_name=name,exported=name %in% getNamespaceExports("GENESIS"),arguments=paste(names(formals(fn)),collapse=","),check.names=FALSE)
    }
}
utils::write.table(do.call(rbind,result),file.path(out,"function-signatures.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
cat("PRIVATE_GENESIS_FUNCTION_INTAKE_ONLY_NO_ANALYSIS\n")
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='SKIP3_GENESIS_FUNCTION_INTAKE_UNAVAILABLE',analysis_performed=False,new_catalog_credit=False,whole_goal_complete=False);protected={};code=3
    try:
        setup=BASE/'private-original-setup-1';metadata=json.loads((setup/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_PRIVATE_ORIGINAL_GENESIS_RUNTIME_SETUP_ONLY'
        library=Path(metadata['private_library']);rscript=Path(metadata['rscript']['path']);assert pin(rscript)=={k:metadata['rscript'][k] for k in ['bytes','sha256']};protected[str(rscript)]=pin(rscript)
        for name,value in metadata['installed_files'].items():assert pin(library/name)==value;protected[str(library/name)]=value
        def literal(value):return json.dumps(str(value).replace('\\','/'))
        script=out/'intake.R';script.write_text('private_library <- '+literal(library)+'\nout <- '+literal(out)+'\n'+PROGRAM,encoding='utf8',newline='\n');temp=out/'tmp';temp.mkdir()
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_LIBS_USER=str(library),R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp))
        command=[str(rscript),'--vanilla',str(script)]
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:r=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.returncode
        assert b'PRIVATE_GENESIS_FUNCTION_INTAKE_ONLY_NO_ANALYSIS' in (out/'stdout').read_bytes()
        state.update(status='PASS_EXACT_PRIVATE_GENESIS242_FUNCTION_INTAKE_ONLY',setup=pin(setup/'COMPLETED.json'),producer=pin(Path(__file__)),
            installed_namespace_functions_recorded=len(list(out.glob('*-formals.R'))),source_files={f.name:pin(f) for f in sorted(out.iterdir()) if f.is_file()});code=0
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        changed=[]
        for name,value in protected.items():
            try:
                if pin(Path(name))!=value:changed.append(name)
            except OSError:changed.append(name)
        state['preservation_checks']=dict(files=len(protected),all_unchanged=not changed,changed_or_missing=changed)
        if changed:state.update(status='FAIL2_GENESIS_PROTECTED_RUNTIME_CHANGED',cause='Exact private runtime changed; admit no analysis');code=2
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nNo analysis is admitted. If an exact required runtime is unavailable or installation is forbidden, skip dependent analyses; supply permitted prerequisites and retry NEW paths. No ordinary-PCA/KING substitution.\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('installed_namespace_functions_recorded',''),state.get('cause','')[:2400],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact installed namespace/source preservation checks')
    raise SystemExit(main())
