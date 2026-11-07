# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Serialize the completed original RDS controls after an S4 JSON-formatting failure; no algorithm rerun."""
import argparse,json,os,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import pin
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--previous',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();prior=a.previous.resolve();out=a.out.resolve();assert prior.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();old=json.loads((prior/'SOURCE-PINS.json').read_bytes())
    for n,v in old.items():
        raw=(prior/'source-snapshot'/n).read_bytes();assert pin(prior/'source-snapshot'/n)==v
        if n=='tools/literature/collect_chromvar_deviations.R':assert (ROOT/n).read_bytes().replace(b"  if(inherits(x,'Matrix'))return(encode(as.matrix(x)))\n",b'',1)==raw
        else:assert pin(ROOT/n)==v
    out.mkdir();save(out/'INCOMPLETE.json',dict(status='serialize-existing-original-controls-in-progress'));name='tools/literature/finalize_chromvar_deviation_controls.py';sources={n:pin(ROOT/n) for n in old};sources[name]=pin(ROOT/name);save(out/'SOURCE-PINS.json',sources)
    for n in sources:
        t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
    try:
        (out/'cases.json').write_bytes((prior/'cases.json').read_bytes());protocol=out/'paths.json';save(protocol,dict(libraries=[str(BASE/'motif-reference-setup-1/R-lib'),str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r')],partial=str(prior/'partial-results.rds'),profiles=str(ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations/SOURCE-PROFILES.json'),target=str(out/'original.json')))
        (out/'libraries.txt').write_text('\n'.join(json.loads(protocol.read_bytes())['libraries'])+'\n',encoding='utf8',newline='\n')
        script=out/'serialize.R';script.write_text('''args<-commandArgs(TRUE);.libPaths(c(readLines(args[[1]],encoding="UTF-8"),.Library),include.site=FALSE);library(Matrix);library(chromVAR);library(jsonlite);p<-fromJSON(args[[2]])
stopifnot(as.character(packageVersion("chromVAR"))=="1.34.1",as.character(packageVersion("Matrix"))=="1.7.5")
number<-function(v){if(is.na(v))return(if(is.nan(v))"NaN" else "NA");if(is.infinite(v))return(if(v>0)"Inf" else "-Inf");trimws(formatC(v,format="g",digits=17,decimal.mark="."))}
finish<-function(v){if(inherits(v,"Matrix")){m<-as.matrix(v);return(list(dim=dim(m),rows=lapply(seq_len(nrow(m)),function(i)vapply(m[i,,drop=FALSE],number,character(1)))))};if(is.list(v))return(lapply(v,finish));v}
results<-readRDS(p$partial);stopifnot(length(results)==52L);results<-finish(results)
write_json(list(profiles=read_json(p$profiles,simplifyVector=FALSE)$records,results=results,dependencies=list(chromVAR="1.34.1",Matrix="1.7.5",historical_whole_packages_executed=FALSE,background_sampling_performed=FALSE)),p$target,digits=17,pretty=TRUE,auto_unbox=FALSE,na="string")
writeLines(capture.output(sessionInfo()),file.path(dirname(p$target),"serialization-sessionInfo.txt"),useBytes=TRUE)
''',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out/'libraries.txt'),str(protocol)];r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60,check=False);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);save(out/'process.json',dict(command=argv,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')[-1500:]
        data=json.loads((out/'original.json').read_bytes());assert len(data['results'])==52 and all(pin(ROOT/n)==v for n,v in sources.items());save(out/'COMPLETED.json',dict(status='captured-original-chromVAR-expectation-deviation-controls',source_pins=sources,case_populations=26,profile_case_protocols=52,source_profiles=['1.16.0-source','1.34.1-package'],cases=pin(out/'cases.json'),original=pin(out/'original.json'),previous_attempt=prior.relative_to(ROOT).as_posix(),completed_controls_RDS=pin(prior/'partial-results.rds'),algorithm_reexecuted=False,only_existing_sparse_outputs_serialized_as_exact_dense_rows=True,historical_whole_packages_executed=False,background_sampling_performed=False,user_analysis_admitted=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-original-chromVAR-expectation-deviation-controls',protocols=52,algorithm_reexecuted=False)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_SERIALIZING_EXISTING_CHROMVAR_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable retained-control checks')
    main()
