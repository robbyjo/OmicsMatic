# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact TFBSTools constructor coercions and failures before auditing effective scanner models."""
import argparse,json,os,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import pin

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    save(out/'INCOMPLETE.json',dict(status='original-PFM-constructor-capture-in-progress'))
    try:
        name='tools/literature/collect_signac_pfm_constructor.py';source=pin(ROOT/name);save(out/'SOURCE-PINS.json',{name:source});(out/'collector-source.py').write_bytes((ROOT/name).read_bytes())
        paths=[str(BASE/'motif-reference-setup-1/R-lib'),str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r')];(out/'libraries.txt').write_text('\n'.join(paths)+'\n',encoding='utf8',newline='\n')
        script=out/'probe.R';script.write_text('''args<-commandArgs(TRUE);.libPaths(c(readLines(args[[1]],encoding="UTF-8"),.Library),include.site=FALSE)
stopifnot(as.character(packageVersion("TFBSTools"))=="1.50.0");library(jsonlite)
f<-get("PFMatrix",asNamespace("TFBSTools"));writeLines(deparse(f,width.cutoff=500L),"PFMatrix-original-function.R",useBytes=TRUE)
m<-matrix(c(9,1,0,0,0,7,3,0),4,dimnames=list(c("A","C","G","T"),NULL));bg<-c(A=.1,C=.2,G=.3,T=.4)
cases<-list(integer=m,fractional=m+.25,fractional_under_one=m*0+.5,zero_column=m,maximum_integer=m,integer_overflow=m,large_count=m,negative=m,zero_matrix=m*0,reordered=m[c("T","G","C","A"),,drop=FALSE])
cases$zero_column[,2]<-0;cases$maximum_integer[1,1]<-.Machine$integer.max;cases$integer_overflow[1,1]<-2147483648;cases$large_count[1,1]<-1e12;cases$negative[1,1]<--.5
results<-lapply(names(cases),function(id){declared<-cases[[id]];w<-character();value<-tryCatch(withCallingHandlers({pf<-TFBSTools::PFMatrix(ID="M",name="factor",profileMatrix=declared,bg=bg);list(effective=unname(methods::slot(pf,"profileMatrix")),background=unname(methods::slot(pf,"bg")),storage=typeof(methods::slot(pf,"profileMatrix")),valid=methods::validObject(pf))},warning=function(x){w<<-c(w,conditionMessage(x));invokeRestart("muffleWarning")}),error=function(x)list(error=conditionMessage(x)));list(id=id,declared=unname(declared),warnings=w,value=value)})
write_json(list(TFBSTools="1.50.0",results=results),"original.json",digits=17,pretty=TRUE,auto_unbox=TRUE,na="null");writeLines(capture.output(sessionInfo()),"sessionInfo.txt",useBytes=TRUE)
''',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out/'libraries.txt')];r=subprocess.run(argv,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);save(out/'process.json',dict(command=argv,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')[-1500:]
        data=json.loads((out/'original.json').read_bytes());by={v['id']:v for v in data['results']};assert len(by)==10
        assert by['integer']['value']['effective']==[[9,0],[1,7],[0,3],[0,0]] and by['fractional']['value']['effective']==by['integer']['value']['effective']
        for id in ['integer_overflow','large_count']:assert 'error' in by[id]['value']
        assert source==pin(ROOT/name);save(out/'COMPLETED.json',dict(status='captured-original-TFBSTools-PFM-integer-construction',source_pins={name:source},controls=10,original=pin(out/'original.json'),constructor=pin(out/'PFMatrix-original-function.R'),analysis_admitted=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-original-TFBSTools-PFM-integer-construction',out=str(out))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_PFM_CONSTRUCTOR_CAPTURE',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable constructor checks')
    main()
