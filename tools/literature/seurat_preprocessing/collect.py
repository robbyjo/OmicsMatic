#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recapture original Seurat preprocessing controls in NEW workspace output; never install."""
import argparse,hashlib,json,os,shutil,subprocess,sys,traceback,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
RESOURCE=ROOT/'src/test/resources/org/jlinalg/sequence/seurat-preprocessing'
ACTION=('SKIP3: original-reference capture was skipped. Supply permitted R4.6.1 and the named exact private libraries, '
 'then use --execution allow and NEW output, or leave it skipped. Native Java use needs neither R nor these packages. '
 'FAIL2: no complete reference capture. Retain inputs, partials and both consoles, correct the stated cause and retry NEW output. '
 'No package install, version substitution, identifier repair, NaN/Inf repair or native qualification is performed by this collector.')

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rscript',type=Path,required=True);p.add_argument('--r-library',type=Path,action='append',required=True);p.add_argument('--execution',choices=['allow','forbid'],default='forbid');p.add_argument('--timeout',type=int,default=600);args=p.parse_args();out=None;processes=[]
 try:
  target=args.out.absolute();target.resolve().relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target
  if args.execution!='allow':raise PermissionError('Original R execution is forbidden by this request')
  if not args.rscript.is_file():raise FileNotFoundError('Missing permitted Rscript: '+str(args.rscript))
  if not 1<=args.timeout<=3600:raise ValueError('Reference timeout must be 1..3600 seconds')
  for library in args.r_library:
   if not library.is_dir():raise FileNotFoundError('Missing declared R library: '+str(library))
   if '\n' in str(library) or '\r' in str(library):raise ValueError('Literal library paths cannot contain line breaks')
  capture=json.loads((RESOURCE/'CAPTURE.json').read_bytes());archive=(RESOURCE/'original-source.zip').read_bytes()
  if pin(archive)!=capture['source_archive']:raise ValueError('Pinned original source archive differs')
  source=out/'original-source';source.mkdir()
  with zipfile.ZipFile(RESOURCE/'original-source.zip') as z:
   names=z.namelist();entries=json.loads(z.read('ENTRY-MANIFEST.json'))['payloads']
   if len(names)!=len(set(names)) or set(names)!=set(entries)|{'ENTRY-MANIFEST.json'}:raise ValueError('Original source archive inventory differs')
   for name,value in entries.items():
    raw=z.read(name)
    if pin(raw)!=value:raise ValueError('Pinned source payload differs: '+name)
    path=source/name;path.resolve().relative_to(source.resolve());path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
  libraries=out/'libraries.txt';libraries.write_text(''.join(str(p.resolve())+'\n' for p in args.r_library),encoding='utf-8')
  for name in ['reference.R','preflight.R']:shutil.copyfile(Path(__file__).with_name(name),out/name)
  env=dict(os.environ,LC_ALL='C',LANG='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  for name,command in [('preflight',[str(args.rscript),'--vanilla',str(out/'preflight.R'),str(libraries)]),('reference',[str(args.rscript),'--vanilla',str(out/'reference.R'),str(out),str(source),str(libraries)])]:
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:
    child=subprocess.run(command,cwd=ROOT,stdout=so,stderr=se,stdin=subprocess.DEVNULL,env=env,timeout=args.timeout)
   row=dict(name=name,command=command,exit_code=child.returncode);processes.append(row);(out/(name+'.PROCESS.json')).write_text(json.dumps(row,indent=2)+'\n',encoding='utf-8')
   if child.returncode==3:raise FileNotFoundError((out/(name+'.stderr')).read_text(errors='replace'))
   if child.returncode!=0:raise ValueError('Original '+name+' failed; read retained consoles')
  result=json.loads((out/'reference.json').read_bytes())
  if result['profile']!='Seurat-5.6.0-matrix-preprocessing' or len(result['datasets'])!=13 or len(result['normalizations'])!=90 or len(result['scalings'])!=208:raise ValueError('Incomplete original reference population')
  record=dict(status='PASS_ORIGINAL_SEURAT_REFERENCE_RECAPTURE_ONLY',processes=processes,reference=pin((out/'reference.json').read_bytes()),original_results=pin((out/'original-results.rds').read_bytes()),datasets=13,normalization_requests=90,scaling_requests=208,original_shape_errors=sum('error' in r for r in result['normalizations']),native_qualification_performed=False,whole_paper_reproduced=False,new_family_credit=0,system_installation=False)
  (out/'COMPLETED.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');print(json.dumps(record),flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
  record=dict(status='SKIP3_REFERENCE_CAPTURE_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_REFERENCE_CAPTURE',error=str(error),processes=processes,required_action=ACTION,detail=traceback.format_exc())
  if out is not None:(out/'FAILURE.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
  print(record['status']+': '+str(error)+'\nRequired action: '+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
