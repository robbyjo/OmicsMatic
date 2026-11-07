# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Use a diagnostic-only signal tracer without editing the original quantifier."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--matrix',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();matrix=a.matrix.resolve();out=a.out.resolve();base=(ROOT/'build/literature-review/salmon-runtime').resolve()
    assert os.uname().sysname=='Linux' and matrix.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    record=json.loads((matrix/'1.8.0/paired-em-bias-50.json').read_bytes());command=list(record['command']);old=Path(command[command.index('-o')+1]);assert old.parent.is_relative_to(Path('/var/tmp'))
    area=old.parent.parent/out.name;assert not area.exists();area.mkdir();source=area/'trace.c'
    source.write_text('''#define _GNU_SOURCE
#include <execinfo.h>
#include <signal.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <ucontext.h>
static char alt[65536];
static void fatal(int sig,siginfo_t *info,void *raw) {
 void *frames[64]; int n=backtrace(frames,64);ucontext_t *context=(ucontext_t*)raw;
 dprintf(2,"DIAGNOSTIC_ONLY signal=%d address=%p rip=%llx\\n",sig,info->si_addr,(unsigned long long)context->uc_mcontext.gregs[REG_RIP]);
 backtrace_symbols_fd(frames,n,2);_exit(128+sig);
}
__attribute__((constructor)) static void setup(void) {
 void *frames[1];backtrace(frames,1);stack_t stack={.ss_sp=alt,.ss_size=sizeof(alt)};sigaltstack(&stack,0);
 struct sigaction action={0};action.sa_sigaction=fatal;action.sa_flags=SA_SIGINFO|SA_ONSTACK;sigemptyset(&action.sa_mask);
 sigaction(SIGSEGV,&action,0);sigaction(SIGABRT,&action,0);
}
''',encoding='ascii',newline='\n')
    library=area/'trace.so';compiled=subprocess.run(['/usr/bin/gcc','-shared','-fPIC','-g','-O0','-Wall','-Wextra','-Werror',str(source),'-o',str(library)],stdin=subprocess.DEVNULL,capture_output=True,timeout=60)
    (out/'compile.stdout').write_bytes(compiled.stdout);(out/'compile.stderr').write_bytes(compiled.stderr);assert compiled.returncode==0
    destination=area/'quant';command[command.index('-o')+1]=str(destination);env=dict(record['environment'],LD_PRELOAD=str(library))
    guarded=['/usr/bin/prlimit','--as=34359738368:34359738368','--cpu=180:185','--core=0:0','--',*command]
    with (out/'trace.stdout').open('xb') as stdout,(out/'trace.stderr').open('xb') as stderr:run=subprocess.run(guarded,env=env,cwd=area,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
    result=dict(status='diagnostic-only-original-bootstrap-signal-trace-no-admission',command=guarded,environment=env,exit_code=run.returncode,
        source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),library_sha256=hashlib.sha256(library.read_bytes()).hexdigest(),original_binary_modified=False,scientific_result_admitted=False)
    (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(result));print((out/'trace.stderr').read_text(errors='replace')[-4500:])


if __name__=='__main__':main()
