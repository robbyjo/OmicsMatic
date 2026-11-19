#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the saved synthetic protocol to an exact Python, inputs and NEW output."""
from pathlib import Path
import argparse
import hashlib
import json

parser=argparse.ArgumentParser(description=__doc__)
for name in ['python','inputs','out','request']:parser.add_argument('--'+name,required=True)
args=parser.parse_args();here=Path(__file__).resolve().parent
data=Path(args.inputs).resolve();target=Path(args.out).resolve();protocol=Path(args.request).resolve()
if target.exists() or protocol.exists():raise SystemExit('FAIL2: output/request exists; preserve it and choose NEW paths')
expected=json.loads((here/'INPUTS.json').read_bytes())['files']
for name,wanted in expected.items():
 path=data/name
 if not path.is_file() or path.is_symlink():raise SystemExit('SKIP3: restore exact synthetic input '+str(path))
 with path.open('rb') as stream:actual=dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
 if actual!=wanted:raise SystemExit('FAIL2: synthetic input changed; preserve it and restore the original ZIP into NEW input paths: '+name)
request=json.loads((here/'protocol.json').read_bytes());request['out']=str(target);request['python']=str(Path(args.python).resolve())
for key in ['matrix','cells','features']:request['input'][key]=str(data/request['input'][key])
protocol.parent.mkdir(parents=True,exist_ok=True)
with protocol.open('x',encoding='utf-8',newline='\n') as stream:json.dump(request,stream,indent=2);stream.write('\n')
print('Prepared explicit synthetic RUN request:',protocol)
