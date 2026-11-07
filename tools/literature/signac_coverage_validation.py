# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Shared standalone native coverage validation helpers; no analysis-time external runtime."""
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux
BASE=ROOT/'build/literature-review/signac-runtime'
JAR=BASE/'coverage-tools-development/omicsmatic-0.3.7-signac-coverage-development.jar'
CLI='org.jlinalg.cli.SignacCoverageCli'
def read(p):return json.loads(p.read_bytes())
def files(p):return {f.relative_to(p).as_posix():pin(f) for f in p.rglob('*') if f.is_file()}
def invoke(out,label,request,expected=0,platform='windows-java21'):
    if platform=='linux-java21':argv=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Djava.awt.headless=true','-Xmx768m','-cp',linux(JAR),CLI,'--request',linux(request)]
    else:argv=[JAVA21 if platform=='windows-java21' else JAVA25,'-Djava.awt.headless=true','-Xmx768m','-cp',JAR,CLI,'--request',request]
    argv=list(map(str,argv))
    with (out/(label+'.stdout')).open('xb') as a,(out/(label+'.stderr')).open('xb') as b:p=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=240)
    save(out/(label+'.process.json'),dict(command=argv,exit_code=p.returncode,expected_exit_code=expected));assert p.returncode==expected,label+' failed; inspect retained logs'
def verify(out,label,target,expected=0):
    for platform in ['windows-java21','windows-java25','linux-java21']:
        p=out/(label+'-'+platform+'-verify.json');save(p,dict(schema_version=1,mode='VERIFY',analysis=linux(target) if platform=='linux-java21' else str(target)))
        invoke(out,label+'-'+platform+'-verify',p,expected,platform)
