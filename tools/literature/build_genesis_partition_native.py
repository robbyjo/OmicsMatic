# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile isolated Java17 native GENESIS matrix partition API and external consumer; no R or canonical rebuild."""
import argparse
import json
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'


def jar(path,classes,notice,main=None):
    payload={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    payload['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes();payload['META-INF/NOTICE']=notice.encode('utf8')
    payload['META-INF/MANIFEST.MF']=('Manifest-Version: 1.0\n'+('Main-Class: '+main+'\n' if main else '')+'\n').encode()
    with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,raw in sorted(payload.items()):
            info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_GENESIS_PARTITION_BUILD',new_catalog_credit=False,whole_goal_complete=False,original_R_analysis_executed=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        native=ROOT/'src/main/java/org/jlinalg/genetics/GenesisPcairPartition.java';probe=ROOT/'src/test/java/org/jlinalg/genetics/GenesisPartitionReferenceProbe.java'
        sources=[native,probe,ROOT/'src/test/java/org/jlinalg/genetics/GenesisPcairPartitionTest.java',Path(__file__)]
        source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources}
        for n in source_pins:
            target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/n,target)
        tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')
        api_classes=out/'api-classes';consumer_classes=out/'consumer-classes';api_classes.mkdir();consumer_classes.mkdir()
        def compile(source,classes,stem,cp=None):
            command=[str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes)]
            if cp:command+=['-cp',str(cp)]
            command.append(str(source));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
            (out/(stem+'.stdout')).write_bytes(r.stdout);(out/(stem+'.stderr')).write_bytes(r.stderr);save(out/(stem+'.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==0,r.stderr[-4000:]
        compile(native,api_classes,'compile-api');api=out/'genesis-partition-api.jar'
        jar(api,api_classes,'Project-owned native finite/unique/symmetric-matrix GENESIS2.42.0 pcairPartition profile. Original algorithm credited to GENESIS authors, GPL3. R Core4.6.1 long-double64 sum profile explicitly emulated and separately controlled. No upstream runtime bundled, PCA/PC-Relate/model fit, whole-package, producer-authentication or whole-goal claim.\n')
        compile(probe,consumer_classes,'compile-external-consumer',api);consumer=out/'genesis-partition-consumer.jar'
        jar(consumer,consumer_classes,'Project-owned external Java17 API consumer for complete source controls, not a public analysis adapter. API JAR is a separate explicit dependency.\n','org.jlinalg.genetics.GenesisPartitionReferenceProbe')
        assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and pin(canonical)==before
        state.update(status='PASS_ISOLATED_NATIVE_GENESIS_PARTITION_API_AND_EXTERNAL_CONSUMER_BUILD',compiler=a.compiler,source_pins=source_pins,
            artifacts={f.name:dict(path=f.relative_to(ROOT).as_posix(),**pin(f)) for f in [api,consumer]},canonical_executable_unchanged=before)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3000],flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable strict native source or canonical-artifact preservation checks')
    raise SystemExit(main())
