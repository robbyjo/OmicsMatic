#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile a frozen current adapter/probe snapshot without rewriting any older classpath."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,time,zipfile
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline-jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    assert sys.platform=='linux','Use permitted Linux Java for original-engine qualification'
    jar=a.baseline_jar.resolve();assert jar.is_file();jar_pin=pin(jar)
    resources=ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1'
    with zipfile.ZipFile(jar) as z:
        for path in resources.rglob('*'):
            if path.is_file():assert z.read(path.relative_to(ROOT/'src/main/resources').as_posix())==path.read_bytes(),path
    selected=sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('SnakemakeOriginal*.java'))+[ROOT/'tools/literature/snakemake_original/SnakemakeOriginalProbe.java'];sources={};copied=[]
    for path in selected:
        name=path.relative_to(ROOT).as_posix();target=out/'as-run'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target);assert pin(path)==pin(target);sources[name]=pin(path);copied.append(str(target))
    classes=out/'classes';classes.mkdir();command=['/usr/bin/javac','--release','17','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),*copied];start=time.monotonic()
    with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:result=subprocess.run(command,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=120)
    write(out/'PROCESS.json',dict(command=command,exit_code=result.returncode,seconds=round(time.monotonic()-start,3)));assert result.returncode==0,'Inspect retained compiler diagnostics'
    assert all(pin(ROOT/name)==expected for name,expected in sources.items()) and pin(jar)==jar_pin
    class_files={p.relative_to(classes).as_posix():pin(p) for p in sorted(classes.rglob('*.class'))}
    write(out/'COMPILED.json',dict(status='PASS_FROZEN_CURRENT_ADAPTER_COMPILES_NOT_WORKFLOW_QUALIFICATION',sources=sources,classes=class_files,baseline_jar=dict(path=str(jar),**jar_pin),classpath=str(classes)+':'+str(jar),probe='org.jlinalg.cli.SnakemakeOriginalProbe',user_analysis_admitted=False))
    print('PASS_FROZEN_CURRENT_ADAPTER_COMPILES_NOT_WORKFLOW_QUALIFICATION',flush=True)
if __name__=='__main__':main()
