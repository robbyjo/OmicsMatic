# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare all 33 exact original images in a NEW private directory; no installation or analysis."""
import argparse,hashlib,json,os,platform,shutil,sys,traceback,urllib.error,urllib.parse,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
PROFILE=ROOT/'src/main/resources/org/jlinalg/omics/nfcore-chipseq200/images.json'
PROFILE_PIN=dict(bytes=17144,sha256='08c51df1b7b00636f4889d5607e155930b0fa34c5ebb8bf1a8baaad1ff536c58')
ACTION='Image setup only; no scientific analysis or runtime installation occurred. Use Linux x86_64, an allowed private directory and the complete exact original image set. Download only where permitted. If download/write access is forbidden or a required image is unavailable, leave dependent analysis skipped and use an allowed environment or an admitted local cache. FAIL2 integrity/input errors admit no image set: preserve the failed directory and originals, resolve the named cause and retry a NEW path. Never relabel another image or edit expected hashes. Separately prepare the exact managed Nextflow/private Apptainer runtime and complete its health checks before analysis. Hashes establish byte integrity, not author history or authenticity.'
class Unavailable(Exception):pass
def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as stream:
  for block in iter(lambda:stream.read(1048576),b''):h.update(block);n+=len(block)
 return dict(bytes=n,sha256=h.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf8',newline='\n')
def unique(pairs):
 result={}
 for key,value in pairs:
  if key in result:raise ValueError('Duplicate JSON key: '+key)
  result[key]=value
 return result
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--from-cache',type=Path,help='Explicit image-set JSON; all present entries must match and remain unchanged');p.add_argument('--allow-download',choices=['true','false'],default='false');a=p.parse_args();out=None;owned=False
 try:
  if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise Unavailable('Use Linux x86_64, including Linux Python within WSL; dependent analysis skipped')
  out=a.out.absolute()
  if out.parent.resolve()!=out.parent or out.exists() or out.is_symlink() or out==out.parent:raise ValueError('Use a NEW canonical directory with an existing canonical parent')
  if pin(PROFILE)!=PROFILE_PIN:raise ValueError('Compiled original source/image profile differs')
  original=json.loads(PROFILE.read_bytes(),object_pairs_hook=unique);images=original['images'];assert len(images)==33;cache={};guards={str(PROFILE):PROFILE_PIN,str(Path(__file__).resolve()):pin(Path(__file__).resolve())};cache_raw=None
  if a.from_cache:
   source=a.from_cache.absolute();cache_raw=source.read_bytes();declaration=json.loads(cache_raw,object_pairs_hook=unique)
   if set(declaration)!=set(['schema_version','images']) or type(declaration['schema_version']) is not int or declaration['schema_version']!=1 or not isinstance(declaration['images'],dict) or not set(declaration['images'])<=set(images):raise ValueError('Cache declaration has unknown keys/URIs or a different schema')
   cache=declaration['images'];guards[str(source)]=pin(source)
  out.mkdir();owned=True
  if cache_raw is not None:(out/'original-cache-declaration.json').write_bytes(cache_raw)
  records=[];bound={}
  for index,(uri,row) in enumerate(images.items(),1):
   parsed=urllib.parse.urlparse(uri)
   if parsed.scheme!='https' or parsed.netloc!='depot.galaxyproject.org' or not parsed.path.startswith('/singularity/') or parsed.query or parsed.fragment:raise ValueError('Unknown original source-image URL contract')
   expected=row['pin'];target=out/('image-%03d-'%index+expected['sha256']+'.sif');partial=target.with_suffix('.sif.partial');record=dict(source_uri=uri,expected=expected,downloaded=False)
   if uri in cache:
    source=Path(cache[uri])
    if not source.exists():raise Unavailable('Required cached image is missing: '+str(source)+'; dependent analysis skipped')
    assert source.is_absolute() and source.resolve()==source and source.is_file() and not source.is_symlink() and not source.is_relative_to(out),'Declare a complete canonical cache file outside the NEW setup directory'
    if pin(source)!=expected:raise ValueError('Original cached image integrity differs: '+uri)
    guards[str(source)]=expected;shutil.copyfile(source,partial);record['source_cache_path']=str(source)
   else:
    if a.allow_download!='true':raise Unavailable('Required original image is not in the explicit cache and downloading is not enabled: '+uri+'. Use --allow-download true only where permitted; dependent analysis skipped')
    request=urllib.request.Request(uri,headers={'User-Agent':'OmicsMatic-original-image-setup'})
    try:
     with urllib.request.urlopen(request,timeout=120) as response,partial.open('xb') as destination:
      if urllib.parse.urlparse(response.geturl()).scheme!='https':raise ValueError('Original image redirected outside HTTPS')
      total=0
      for block in iter(lambda:response.read(1048576),b''):
       total+=len(block)
       if total>expected['bytes']:raise ValueError('Original image download exceeds its exact expected size')
       destination.write(block)
      record['final_url']=response.geturl()
    except (urllib.error.URLError,TimeoutError) as error:raise Unavailable('Required original download is unavailable or forbidden: '+uri+'; '+str(error)) from error
    record['downloaded']=True
   if pin(partial)!=expected:raise ValueError('Original complete image bytes differ: '+uri+'; retain '+str(partial))
   partial.rename(target);bound[uri]=str(target);record.update(path=str(target),actual=pin(target));records.append(record);save(out/'progress.json',dict(status='IMAGE_SETUP_IN_PROGRESS_NO_ANALYSIS',images=records));print('Verified original image',index,'of 33',flush=True)
  if not all(pin(Path(name))==value for name,value in guards.items()) or any(pin(Path(bound[uri]))!=images[uri]['pin'] for uri in bound):raise ValueError('Original source/cache/setup image changed before completion')
  save(out/'image-set.json',dict(schema_version=1,images=bound));save(out/'IMAGE_SETUP_COMPLETED.json',dict(status='PASS_COMPLETE_ORIGINAL_CHIPSEQ200_IMAGES_ONLY',images=records,source_profile=PROFILE_PIN,original_files_unchanged=guards,scientific_analysis_executed=False,runtime_installed=False,biological_accuracy_certified=False));(out/'WARNINGS.md').write_text('# Original image setup only\n\n'+ACTION+'\n',encoding='utf8');print('Complete exact image declaration:',out/'image-set.json');print(ACTION);return 0
 except (Exception,KeyboardInterrupt) as error:
  code=3 if isinstance(error,(Unavailable,PermissionError)) else 2;status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_IMAGE_SET';message=status+': '+str(error);print(message+'\nRequired action: '+ACTION,file=sys.stderr)
  if owned:
   try:save(out/'FAILURE.json',dict(status=status,error=str(error),required_action=ACTION,traceback=traceback.format_exc()));(out/'WARNINGS.md').write_text('# '+status+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8')
   except OSError as diagnostic:print('Retain the setup directory; diagnostic write failed: '+str(diagnostic),file=sys.stderr)
  return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original-image setup integrity checks')
 raise SystemExit(main())
