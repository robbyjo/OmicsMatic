# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an isolated native GENESIS rank API and external consumer with the project's pinned JDistlib dependency."""
import argparse
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'
DEPENDENCY=ROOT/'build/dependencies/jdistlib-all-0.10.2.jar'
DEPENDENCY_SHA='8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'
ACTION='SKIP native ranking/build when pinned JDistlib0.10.2 or the selected Java compiler is unavailable or forbidden. Supply permitted prerequisites; use the existing Gradle fetchJdistlib task for the dependency. No quantile/version substitution. FAIL2: preserve the attempt, resolve the reported integrity/compilation cause and retry NEW paths. A build admits no model or user-data analysis.'


class Unavailable(RuntimeError):pass


def jar(path,classes,main=None):
    payload={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    payload['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes()
    payload['META-INF/NOTICE']=b'Native finite-input GENESIS2.42.0 .rankNorm component, algorithm attributed to GENESIS authors GPL3. Explicit JDistlib0.10.2 normal-quantile dependency is required separately and is not bundled. No R runtime, model fit, variance rescaling, whole-package or whole-goal claim.\n'
    payload['META-INF/MANIFEST.MF']=('Manifest-Version: 1.0\n'+('Main-Class: '+main+'\n' if main else '')+'\n').encode()
    with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(payload.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_GENESIS_RANK_BUILD',new_catalog_credit=False,whole_goal_complete=False)
    try:
        if not DEPENDENCY.is_file():raise Unavailable('The project-pinned JDistlib0.10.2 dependency is missing')
        assert pin(DEPENDENCY)['sha256']==DEPENDENCY_SHA,'Pinned normal-quantile dependency bytes differ'
        api=ROOT/'src/main/java/org/jlinalg/genetics/GenesisRankNormalisation.java';consumer=ROOT/'src/test/java/org/jlinalg/genetics/GenesisRankReferenceProbe.java';canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_before=pin(canonical)
        sources=[api,consumer,ROOT/'src/test/java/org/jlinalg/genetics/GenesisRankNormalisationTest.java',Path(__file__).resolve()];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources}
        for n in pins:
            target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/n,target)
        tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')
        if not (tools/'javac.exe').is_file():raise Unavailable('The selected Java compiler is missing: '+str(tools/'javac.exe'))
        artifacts={}
        for kind,source,cp in [('api',api,[DEPENDENCY]),('consumer',consumer,[out/'genesis-rank-api.jar',DEPENDENCY])]:
            classes=out/(kind+'-classes');classes.mkdir();command=[str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',';'.join(map(str,cp)),'-d',str(classes),str(source)]
            r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(out/(kind+'-compile.stdout')).write_bytes(r.stdout);(out/(kind+'-compile.stderr')).write_bytes(r.stderr);save(out/(kind+'-compile.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr[-5000:]
            artifact=out/('genesis-rank-'+kind+'.jar');jar(artifact,classes,'org.jlinalg.genetics.GenesisRankReferenceProbe' if kind=='consumer' else None);artifacts[artifact.name]=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact))
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(canonical)==canonical_before and pin(DEPENDENCY)['sha256']==DEPENDENCY_SHA
        state.update(status='PASS_NATIVE_GENESIS_RANK_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY',source_pins=pins,compiler=a.compiler,artifacts=artifacts,dependency=dict(path=DEPENDENCY.relative_to(ROOT).as_posix(),**pin(DEPENDENCY)),canonical_executable_unchanged=canonical_before)
    except Unavailable as error:state.update(status='SKIP3_NATIVE_GENESIS_RANK_BUILD_UNAVAILABLE',cause=repr(error))
    except Exception as error:state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    state['required_action']=ACTION
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','An isolated API and consumer were built; no analysis was performed.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('cause','')[:5000],flush=True);return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native source/dependency/canonical preservation checks')
    raise SystemExit(main())
