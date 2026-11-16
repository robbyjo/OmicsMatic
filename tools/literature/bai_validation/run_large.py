#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit retained real large original sidecars and fresh native indexes in actual64-MiB heaps."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[3]
def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for raw in iter(lambda:stream.read(1<<20),b''):digest.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=digest.hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def properties(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#') and '=' in line)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','large_originals','out']:p.add_argument('--'+name.replace('_','-'),type=Path,required=True)
    p.add_argument('--wsl',required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);base=a.large_originals.resolve(strict=True)
    generator=json.loads((base/'input/INPUT.json').read_bytes());assert generator['records']==4_000_000 and generator['pairs']==2_000_000
    assert pin(base/'input/input.bam')==dict(bytes=generator['compressed_bytes'],sha256=generator['sha256'])
    source_pins={}
    for path in Path(__file__).parent.iterdir():
        if path.is_file():source_pins[path.relative_to(ROOT).as_posix()]=pin(path)
    for name in ['src/main/java/org/jlinalg/alignment/BamBaiAudit.java','src/main/java/org/jlinalg/alignment/BamIndexStream.java','src/main/java/org/jlinalg/alignment/SambambaStreamingBamAudit.java']:source_pins[name]=pin(ROOT/name)
    save(out/'SOURCE-PINS.json',source_pins)
    with zipfile.ZipFile(out/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
        for name in source_pins:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    jar=out/'candidate.jar';candidate=pin(a.jar);shutil.copy2(a.jar,jar);assert candidate==pin(jar)==pin(a.jar)
    checks=[]
    def linux(path):
        path=Path(path).resolve();return '/mnt/'+path.as_posix()[0].lower()+path.as_posix()[2:]
    def run(where,label,command):
        command=list(map(str,command));start=time.monotonic()
        with (where/(label+'.stdout')).open('xb') as so,(where/(label+'.stderr')).open('xb') as se:
            process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=660)
        row=dict(label=label,command=command,exit_code=process.returncode,seconds=round(time.monotonic()-start,2));save(where/(label+'.process.json'),row);checks.append(row)
        print(json.dumps(row),flush=True)
        if process.returncode:raise RuntimeError(label+' failed; inspect retained consoles')
    prefix=['wsl.exe','-d',a.wsl,'--','/usr/bin/timeout','--signal=TERM','--kill-after=5','630']
    classes=out/'probe-classes';classes.mkdir()
    run(out,'compile-probes',[*prefix,'/usr/bin/javac','--release','17','-cp',linux(jar),'-d',linux(classes),linux(Path(__file__).with_name('BaiQueryProbe.java')),linux(Path(__file__).with_name('LargeIndexProbe.java'))])
    java=[*prefix,'/usr/bin/java','-Xms16m','-Xmx64m','-cp',linux(jar)+':'+linux(classes)]
    inputs=[('input','input',base/'input/input.bam',None)]+[(edition+'-'+action,action,base/edition/(action+'.bam'),base/edition/(action+'.bam.bai')) for edition in ['0.6.6','0.8.2'] for action in ['mark','remove']]
    before={str(path):pin(path) for _,_,bam,bai in inputs for path in [bam,bai] if path is not None};save(out/'INPUT-PINS.json',before);results=[]
    for label,kind,bam,original in inputs:
        where=out/label;where.mkdir()
        if original is not None:
            run(where,'original-index-check',[*java,'LargeIndexProbe',linux(bam),linux(original),str(generator['pairs']),kind,linux(where/'ORIGINAL.properties')])
            results.append(dict(input=label,index='retained-original',report=properties(where/'ORIGINAL.properties')))
        index=where/'fresh-native.bai'
        run(where,'fresh-native-index',[*java,'BaiQueryProbe','index',linux(bam),linux(index),linux(where/'CREATED.properties')])
        run(where,'fresh-native-index-check',[*java,'LargeIndexProbe',linux(bam),linux(index),str(generator['pairs']),kind,linux(where/'NATIVE.properties')])
        results.append(dict(input=label,index='fresh-native-java',report=properties(where/'NATIVE.properties')))
    for name,identity in before.items():assert pin(Path(name))==identity,'Retained original changed'
    for name,identity in source_pins.items():assert pin(ROOT/name)==identity,'Source changed during controls'
    assert candidate==pin(jar)
    assert len(results)==9 and all(int(row['report']['maximum_java_heap_bytes'])<=64<<20 for row in results)
    save(out/'COMPLETED.json',dict(status='PASS_NINE_LARGE_NATIVE_BAI_AND_QUERY_CONTROLS_WITH_64_MIB_HEAPS',candidate=candidate,generator=generator,results=results,checks=checks,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_NINE_LARGE_NATIVE_BAI_AND_QUERY_CONTROLS_WITH_64_MIB_HEAPS',flush=True)
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_LARGE_INDEX_CONTROLS_INCOMPLETE',repr(failure),flush=True);raise SystemExit(2)
