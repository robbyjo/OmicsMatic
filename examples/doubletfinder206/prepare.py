#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extract exact synthetic requests and complete original expectations; no analysis/install."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,zipfile
HERE=Path(__file__).resolve().parent
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve()
 if out.is_relative_to(HERE) or HERE.is_relative_to(out):raise ValueError('Use a NEW separate extraction output')
 metadata=json.loads((HERE/'FILES.json').read_bytes());archive=HERE/'synthetic-requests.zip'
 if pin(archive.read_bytes())!=metadata['archive']:raise ValueError('Frozen original example archive differs')
 with zipfile.ZipFile(archive) as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(metadata['files'])|{'FILES.json'} or len(names)!=metadata['regular_members']:raise ValueError('Complete example member census differs')
  data={name:z.read(name) for name in names}
 for name,raw in data.items():
  path=PurePosixPath(name)
  if path.is_absolute() or any(p in ['.','..'] for p in path.parts) or '\\' in name:raise ValueError('Unsafe example path')
  if name!='FILES.json' and pin(raw)!=metadata['files'][name]:raise ValueError('Original example payload differs: '+name)
 out.mkdir(parents=True,exist_ok=False)
 for name,raw in data.items():
  target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
 (out/'PREPARED.json').write_text(json.dumps(dict(status='PREPARED_EXACT_ORIGINAL_SYNTHETIC_REQUESTS_NOT_ANALYSIS',cases=len(metadata['cases']),original_error_examples=2,archive=metadata['archive'],analysis_executed=False,installation_executed=False),indent=2)+'\n')
 print('PREPARED_EXACT_ORIGINAL_SYNTHETIC_REQUESTS_NOT_ANALYSIS',len(metadata['cases']))
if __name__=='__main__':main()
