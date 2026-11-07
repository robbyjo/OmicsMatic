# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile and execute the documented prepared-read counter example on three actual JVMs."""
import argparse
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_deseq2_inference import JAVA21,JAVA25,linux
from validate_htseq_read_count_native import BASE,JAR


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);source=ROOT/'examples/htseq-read-count/CountPreparedReads.java';classes=out/'classes';classes.mkdir()
    expected='''NONE A=1.0 B=0.0
FRACTION A=1.5 B=0.5
FRACTION summary no_feature=1 ambiguous=1 too_low_aQual=1 not_aligned=1 alignment_not_unique=1
PAIRED missing-first-NH FIRST_MATE_MISSING_TAG_ABORTED ASSIGNED
unit-capacity REFUSED
'''
    def command(label,argv):
        argv=list(map(str,argv));result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=90)
        (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr)
        save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,label
        if label!='compile':assert result.stdout.decode('utf8').replace('\r\n','\n')==expected,label
    command('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',JAR,'-d',classes,source])
    for label,java in [('windows-java21',JAVA21),('windows-java25',JAVA25)]:command(label,[java,'-cp',os.pathsep.join(map(str,[JAR,classes])),'CountPreparedReads'])
    command('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-cp',':'.join(map(linux,[JAR,classes])),'CountPreparedReads'])
    assert pin(JAR)==artifact
    save(out/'COMPLETED.json',dict(status='passed-native-HTSeq-prepared-read-example-three-JVMs',artifact=artifact,source=pin(source),JVMs=3,
        complete_count_modes_per_JVM=2,source_short_circuit_demonstrated=True,capacity_refusal_demonstrated=True,
        native_SAM_GTF_parser=False,native_pairing=False,analysis_installed_software=False))
    print('Checked native prepared-read example passed on3 JVMs with source NH behavior and explicit capacity refusal')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example checks')
    main()
