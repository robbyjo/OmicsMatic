# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install the original missing HSMMSingleCell data dependency into a NEW private library."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    action='Preserve installer logs and this attempt. Install the original declared data dependency only in NEW private libraries where permitted; otherwise skip dependent Monocle2 analysis. Constructor/namespace health is not scientific admission. Never update shared R libraries or replace a missing import with a stub.'
    if not a.allow_install:save(out/'RESULT.json',dict(status='SKIP3_HSMMSINGLECELL_UNAVAILABLE',analysis_admitted=False,required_action=action));return 3
    intake=base/'monocle2-hsmm-intake-1';proof=json.loads((intake/'SOURCE.json').read_bytes());archive=intake/('HSMMSingleCell_'+proof['version']+'.tar.gz');assert pin(archive)=={k:proof['archive'][k] for k in ['bytes','sha256']}
    setup=base/'monocle2-linux-reference-setup-3';assert json.loads((setup/'RESULT.json').read_bytes())['installer_exit_code']==0;prefix=setup/'.pixi/envs/default'
    for name in ['home','tmp','cache','R-lib']:(out/name).mkdir()
    script=out/'installer.R';script.write_text('''a<-commandArgs(TRUE);out<-a[1];lib<-a[2];archive<-a[3];expected<-a[4];.libPaths(c(lib,.Library),include.site=FALSE)
warnings_seen<-character();messages_seen<-character();status<-"SKIP3_HSMMSINGLECELL_UNAVAILABLE";detail<-""
tryCatch(withCallingHandlers({
 install.packages(archive,lib=lib,repos=NULL,type="source",dependencies=FALSE,INSTALL_opts="--no-multiarch")
 stopifnot(requireNamespace("HSMMSingleCell",quietly=TRUE),identical(as.character(packageVersion("HSMMSingleCell")),expected),requireNamespace("monocle",quietly=TRUE),identical(as.character(packageVersion("monocle")),"2.26.0"))
 status<-"INSTALLED_ORIGINAL_HSMMSINGLECELL_REFERENCE_ONLY"
},warning=function(w){warnings_seen<<-c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){detail<<-conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
jsonlite::write_json(list(status=status,detail=detail,warnings=as.list(warnings_seen),messages=as.list(messages_seen),R=as.character(getRversion()),HSMMSingleCell_requested=expected,analysis_admitted=FALSE),file.path(out,"RESULT.json"),auto_unbox=TRUE,pretty=TRUE)
quit(status=if(status=="INSTALLED_ORIGINAL_HSMMSINGLECELL_REFERENCE_ONLY")0L else 3L)
''',encoding='utf8',newline='\n')
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','300','/usr/bin/env','HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache'),'R_HOME='+linux(prefix/'lib/R'),'LD_LIBRARY_PATH='+linux(prefix/'lib'),'LANG=C','LC_ALL=C','LANGUAGE=en','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',linux(prefix/'bin/Rscript'),'--vanilla',linux(script),linux(out),linux(out/'R-lib'),linux(archive),proof['version']]
    save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),worker_pin=pin(script),archive=pin(archive),source_receipt=pin(intake/'SOURCE.json'),command=command,analysis_admitted=False))
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
        process=subprocess.Popen(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL);save(out/'PROCESS-STARTED.json',dict(pid=process.pid,command=command));code=process.wait(timeout=360)
    save(out/'process.json',dict(command=command,pid=process.pid,exit_code=code));assert pin(archive)=={k:proof['archive'][k] for k in ['bytes','sha256']}
    if not (out/'RESULT.json').exists():save(out/'RESULT.json',dict(status='SKIP3_HSMMSINGLECELL_UNAVAILABLE',cause='Installer produced no terminal report; inspect stdout/stderr',analysis_admitted=False))
    result=json.loads((out/'RESULT.json').read_bytes());(out/'WARNINGS.md').write_text('# '+result['status']+'\n\n'+result.get('detail',result.get('cause',''))+'\n\n'+'\n\n'.join(result.get('warnings',[]))+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n');print(json.dumps({k:v for k,v in result.items() if k!='messages'}),flush=True)
    return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original dependency checks')
    raise SystemExit(main())
