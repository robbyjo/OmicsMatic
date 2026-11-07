# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only Cicero-Monocle3 dependency/namespace intake; installs nothing and admits no analysis."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    source=base/'related-source-intake-1/cicero-m3';receipt=json.loads((source/'SOURCE.json').read_bytes());assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items())
    libs=[base/'reference-setup-3/R-lib',*[Path(p) for p in json.loads((base/'reference-setup-3/STARTED.json').read_bytes())['libraries']]]
    script=out/'probe.R';script.write_text('''a<-commandArgs(TRUE);out<-a[1];description<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
warnings<-character();report<-withCallingHandlers({
d<-read.dcf(description);fields<-paste(d[1,c("Depends","Imports")],collapse=",");names<-unique(trimws(strsplit(gsub("\\\\([^)]*\\\\)","",fields),",",fixed=TRUE)[[1]]));names<-setdiff(names,"R");rows<-setNames(lapply(names,function(n){error<-NULL;ready<-tryCatch(requireNamespace(n,quietly=TRUE),error=function(e){error<<-conditionMessage(e);FALSE});list(namespace_available=ready,version=if(ready)as.character(packageVersion(n)) else NULL,error=error)}),names)
list(status=if(all(vapply(rows,function(x)isTRUE(x$namespace_available),logical(1))))"DEPENDENCIES_LOADABLE_NOT_ANALYSIS_ADMISSION" else "SKIP3_CICERO_DEPENDENCY_UNAVAILABLE",R_version=as.character(getRversion()),platform=R.version$platform,requested_Cicero_version=d[1,"Version"],dependencies=rows,installed_software=FALSE,analysis_admitted=FALSE)
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")});report$warnings<-as.list(warnings);jsonlite::write_json(report,file.path(out,"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,null="null");writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
''',encoding='utf8',newline='\n')
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source/'source/DESCRIPTION'),*map(str,libs)]
    save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),upstream_source=pin(source/'SOURCE.json'),libraries=list(map(str,libs)),command=command,analysis_admitted=False))
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',TMPDIR=str(out),R_USER_CACHE_DIR=str(out)),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=180)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Read preserved dependency probe diagnostics';report=json.loads((out/'RESULT.json').read_bytes());save(out/'COMPLETED.json',dict(status='read-only-Cicero-dependency-probe-completed',source_pin=pin(Path(__file__)),report=pin(out/'RESULT.json'),software_installed=False,analysis_admitted=False));print(json.dumps(dict(status=report['status'],missing=[n for n,r in report['dependencies'].items() if not r['namespace_available']])))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable explicit source/dependency bindings')
    main()
