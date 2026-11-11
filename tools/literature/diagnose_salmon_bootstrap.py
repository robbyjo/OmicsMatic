# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain controlled thread/address-space probes of an original Salmon bootstrap crash."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    matrix=args.matrix.resolve();out=args.out.resolve();base=(ROOT/'build/literature-review/salmon-runtime').resolve()
    assert os.uname().sysname=='Linux' and matrix.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    receipt=json.loads((matrix/'1.8.0/paired-em-bias-50.json').read_bytes());assert receipt['exit_code']==-11
    original=receipt['command'];old_output=Path(original[original.index('-o')+1]);assert old_output.parent.is_relative_to(Path('/var/tmp'))
    runs=[]
    for name,threads,gib in [('same-8GiB-2threads',2,8),('32GiB-2threads',2,32),('32GiB-1thread',1,32)]:
        command=list(original);command[command.index('-p')+1]=str(threads);new_output=old_output.parent.parent/(out.name+'-'+name);assert not new_output.exists();command[command.index('-o')+1]=str(new_output)
        guarded=['/usr/bin/prlimit','--cpu=180:185','--as='+str(gib*1024**3)+':'+str(gib*1024**3),'--fsize=4294967296:4294967296','--nofile=1024:1024','--core=0:0','--',*command]
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
            run=subprocess.run(guarded,env=receipt['environment'],cwd=old_output.parent,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        item=dict(label=name,threads=threads,address_space_GiB=gib,command=guarded,exit_code=run.returncode,output=str(new_output));runs.append(item)
        (out/(name+'.json')).write_text(json.dumps(item,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(label=name,exit_code=run.returncode)),flush=True)
    (out/'COMPLETED.json').write_text(json.dumps(dict(status='completed-original-bootstrap-diagnostic-comparisons-no-admission',runs=runs),indent=2)+'\n',encoding='utf-8',newline='\n')


if __name__=='__main__':main()
