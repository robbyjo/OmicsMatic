# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile and run the checked native interval example against the actual development JAR."""
import argparse
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux
from validate_htseq_interval_native import BASE,JAR


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    artifact=pin(JAR)
    source=ROOT/'examples/htseq-intervals/AssignFeatures.java'
    classes=out/'classes'
    classes.mkdir()
    expected='''overlap UNION AMBIGUOUS [gene-A, gene-B]
overlap INTERSECTION_STRICT UNIQUE [gene-A]
overlap INTERSECTION_NONEMPTY UNIQUE [gene-A]
gap UNION UNIQUE [gene-A]
gap INTERSECTION_STRICT NO_FEATURE []
gap INTERSECTION_NONEMPTY UNIQUE [gene-A]
zero-length query REFUSED
whole-assignment capacity REFUSED
'''
    def command(label,argv):
        argv=list(map(str,argv))
        result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=90)
        (out/(label+'.stdout')).write_bytes(result.stdout)
        (out/(label+'.stderr')).write_bytes(result.stderr)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode))
        assert result.returncode==0,label
        if label!='compile':assert result.stdout.decode('utf8').replace('\r\n','\n')==expected,label
    command('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',JAR,'-d',classes,source])
    for label,java in [('windows-java21',JAVA21),('windows-java25',JAVA25)]:
        command(label,[java,'-cp',os.pathsep.join(map(str,[JAR,classes])),'AssignFeatures'])
    command('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-cp',':'.join(map(linux,[JAR,classes])),'AssignFeatures'])
    assert pin(JAR)==artifact
    save(out/'COMPLETED.json',dict(status='passed-checked-native-HTSeq-interval-example-three-JVMs',artifact=artifact,
        source=pin(source),JVMs=3,assignment_outcomes_per_JVM=6,refusal_controls_per_JVM=2,
        original_htseq_count_SAM_GTF_run=False,analysis_installed_software=False))
    print('Checked native interval example passed on3 JVMs with six assignments and two explicit refusals each')


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable example output verification')
    main()
