# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile and execute the documented binary read-presence example on three JVMs."""
import argparse
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from build_htseq_tools_diagnostics import BASE,JAR
from validate_deseq2_inference import JAVA21,JAVA25,linux


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();artifact=pin(JAR)
    source=ROOT/'examples/htseq-binary-accessibility/BinaryReadPresence.java';classes=out/'classes';classes.mkdir()
    expected='B\tc2\t1\nB\tc1\t1\nA\tc1\t1\nreads=5 overlapping=4 nonzero=3; zero peak/cell retained\nnonzero-capacity REFUSED\n'
    def run(label,argv):
        argv=list(map(str,argv));result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
        (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr);save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,label
        if label!='compile':assert result.stdout.decode('utf8').replace('\r\n','\n')==expected,label
    run('compile',[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',JAR,'-d',classes,source])
    for name,java in [('windows-java21',JAVA21),('windows-java25',JAVA25)]:run(name,[java,'-cp',os.pathsep.join(map(str,[JAR,classes])),'BinaryReadPresence'])
    run('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-cp',':'.join(map(linux,[JAR,classes])),'BinaryReadPresence'])
    assert pin(JAR)==artifact
    save(out/'COMPLETED.json',dict(status='passed-native-binary-accessibility-example-three-JVMs',artifact=artifact,source=pin(source),JVMs=3,
        sparse_coordinates_per_JVM=3,explicit_zero_peak_and_cell=True,capacity_refusals_per_JVM=1,analysis_installed_software=False))
    print('Checked binary example on three JVMs:3 sparse coordinates, explicit zero axes and1 capacity refusal each')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact example checks')
    main()
