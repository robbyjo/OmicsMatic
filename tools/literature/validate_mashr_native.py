#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate native mashr foundations from an actual development JAR on three JVMs.

Requires existing Java21/25, WSL Java21 and cached JUnit dependencies. Launches
no upstream analysis and never installs or overwrites a runtime or executable.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'
JAVA21=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
LIBS=ROOT/'build/literature-review/shapeit-runtime/owned-common-native-matrix-4/lib'
NAMES=['org.jlinalg.shrinkage.'+name for name in ('MashGaussianTest','MashGaussianOriginalTest','MashMixtureWeightsTest','MashMixtureWeightsOriginalTest')]


def pin(path):
    value=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1048576),b''):value.update(block);size+=len(block)
    return dict(bytes=size,sha256=value.hexdigest())


def linux(path):
    return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args();jar,out=args.artifact.resolve(),args.out.resolve()
    if not jar.is_relative_to(BASE.resolve()) or not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists():
        raise ValueError('Use an existing mashr development JAR and NEW output under its private build directory')
    out.mkdir(parents=True)
    try:
        libraries=sorted(LIBS.glob('*.jar'))
        if len(libraries)!=9 or not (JAVA21/'javac.exe').is_file() or not shutil.which('wsl.exe'):
            (out/'WARNINGS.md').write_text('# SKIP3: native validation dependencies unavailable\n\nRequired action: provide JDK21, Java25, WSL Java21 and the cached nine-library JUnit test closure; repeat into a NEW directory. No validation success is admitted.\n')
            return 3
        artifact_pin=pin(jar);canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        sources=[ROOT/'src/test/java'/Path(*name.split('.')).with_suffix('.java') for name in NAMES]
        sources+=[ROOT/'src/main/java/org/jlinalg/shrinkage/MashGaussian.java',ROOT/'src/main/java/org/jlinalg/shrinkage/MashMixtureWeights.java',Path(__file__)]
        fixtures=sorted((ROOT/'src/test/resources/org/jlinalg/shrinkage/mashr-original').glob('*.zip'));assert len(fixtures)==2
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in [*sources,*fixtures,*libraries]}
        with zipfile.ZipFile(jar) as archive:
            assert archive.read('org/jlinalg/omics/mashr-BSD-NOTICE.txt')==(ROOT/'src/main/resources/org/jlinalg/omics/mashr-BSD-NOTICE.txt').read_bytes()
            packaged={name:dict(bytes=len(archive.read(name)),sha256=hashlib.sha256(archive.read(name)).hexdigest()) for name in archive.namelist() if name.startswith('org/jlinalg/shrinkage/') and name.endswith('.class')}
            assert len(packaged)==16,packaged.keys()
        runner=out/'RunMashrNativeChecks.java'
        runner.write_text('''import java.io.PrintWriter;
import java.io.File;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMashrNativeChecks {
 private RunMashrNativeChecks() { }
 public static void main(String[] args) throws Exception {
  for(String name:new String[]{"org.jlinalg.shrinkage.MashGaussian","org.jlinalg.shrinkage.MashMixtureWeights","jdistlib.accelerator.CpuComputeBackend"})
   if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong JAR loaded: "+name);
  var request=LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build();
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);var summary=listener.getSummary();
  summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));
  if(summary.getTestsFoundCount()!=181||summary.getTestsSucceededCount()!=181||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native mashr validation");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+name+'")' for name in NAMES)),encoding='utf-8')
        classes=out/'classes';classes.mkdir()
        compile_command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(runner),*map(str,sources[:4])]
        with (out/'compile.stdout').open('xb') as stdout,(out/'compile.stderr').open('xb') as stderr:
            process=subprocess.run(compile_command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=120,check=False)
        (out/'compile-command.json').write_text(json.dumps(dict(argv=compile_command,exit_code=process.returncode),indent=2)+'\n')
        assert process.returncode==0,'Test compiler failed; inspect compile.stderr'
        entries=[classes,jar,ROOT/'src/test/resources',*libraries];windows_cp=os.pathsep.join(map(str,entries))
        runs=[('windows-java21',[str(JAVA21/'java.exe'),'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',windows_cp,'RunMashrNativeChecks',str(jar)]),
              ('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx128m','-XX:ActiveProcessorCount=2','-cp',windows_cp,'RunMashrNativeChecks',str(jar)]),
              ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','env','JLINALG_MASHR_GAUSSIAN_CONTROLS=','/usr/bin/java','-Xmx128m','-XX:ActiveProcessorCount=2','-cp',':'.join(map(linux,entries)),'RunMashrNativeChecks',linux(jar)])]
        def execute(row):
            name,command=row
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                result=subprocess.run(command,cwd=ROOT,env=dict(os.environ,JLINALG_MASHR_GAUSSIAN_CONTROLS=''),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=300,check=False)
            observed=dict(name=name,argv=command,exit_code=result.returncode)
            (out/(name+'.json')).write_text(json.dumps(observed,indent=2)+'\n')
            report=(out/(name+'.stdout')).read_text(encoding='utf-8',errors='replace')
            assert result.returncode==0,name+' failed; inspect retained stdout/stderr'
            for count,label in ((181,'tests found'),(181,'tests successful'),(0,'tests failed'),(0,'tests aborted'),(0,'tests skipped')):
                assert re.search(r'\[\s*'+str(count)+' '+label+r'\s*\]',report),(name,label)
            print(name+': 181 passed',flush=True);return observed
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:observed=list(pool.map(execute,runs))
        assert pin(jar)==artifact_pin and pin(canonical)==canonical_pin
        assert all(pin(ROOT/name)==expected for name,expected in bound.items()),'A bound test/source/fixture/dependency changed during validation'
        record=dict(schema_version=1,status='passed-native-mashr-foundation-actual-JAR-matrix',tests_per_JVM=181,heap_mib=128,
                    artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact_pin),canonical_artifact_unchanged=canonical_pin,
                    source_and_dependency_pins=bound,packaged_native_classes=packaged,runs=observed,
                    upstream_originals_rerun_in_matrix=False,public_user_analysis_admitted=False,whole_mashr_integrated=False,
                    files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()})
        (out/'COMPLETED.json').write_text(json.dumps(record,indent=2)+'\n')
        return 0
    except Exception as error:
        (out/'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_NATIVE_MASHR_VALIDATION',message=str(error)),indent=2)+'\n')
        (out/'WARNINGS.md').write_text('# FAIL2: native mashr validation incomplete\n\n'+str(error)+'\n\nRequired action: preserve this attempt; inspect compile and platform logs. Correct the failure and repeat into a NEW directory; do not admit this as successful validation.\n')
        raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:
        print('MASHR NATIVE VALIDATION FAIL2: '+str(failure),file=sys.stderr);sys.exit(2)
