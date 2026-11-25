#!/usr/bin/env python3
"""Separate, explicit private installation. Analysis never invokes this script."""
import argparse,gzip,hashlib,json,os,platform,shutil,stat,sys,urllib.request
from pathlib import Path
from health import pin,put,run
class Unavailable(Exception):pass
def directory(p):
 if not p.is_absolute()or p!=p.resolve()or not p.is_dir():raise ValueError('Require canonical existing directory: '+str(p))
def inventory(home):
 result={}
 for path in sorted(home.rglob('*')):
  if path.name=='runtime.json'and path.parent==home:continue
  if path.is_symlink():raise ValueError('Runtime links are not permitted: '+str(path))
  name=path.relative_to(home).as_posix()
  if path.is_dir():result[name]=dict(kind='directory')
  elif path.is_file():result[name]=dict(kind='file',**pin(path))
  else:raise ValueError('Nonregular runtime member: '+str(path))
  if len(result)>100:raise ValueError('Unexpected private runtime size')
 return result
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--report',required=True,type=Path);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');a=parser.parse_args();home=a.home.absolute();report=a.report.absolute();cache=a.cache.absolute();created=False;report_ready=False
 try:
  directory(report.parent);report.mkdir();report_ready=True
  if sys.version_info<(3,11)or sys.platform!='linux'or platform.machine()not in('x86_64','amd64'):raise Unavailable('Use Linux x86_64 Python >=3.11, including Python inside WSL; dependent analysis skipped')
  for name in('/usr/bin/setsid','/usr/bin/prlimit','/proc/self/stat','/proc/self/status'):
   if not Path(name).is_file()or not os.access(name,os.R_OK)or(name.startswith('/usr/bin/')and not os.access(name,os.X_OK)):raise Unavailable('Required permitted Linux execution prerequisite unavailable: '+name)
  directory(home.parent);directory(cache)
  if home==report or home in report.parents or report in home.parents:raise ValueError('Private home and report must be disjoint')
  support=Path(__file__).resolve().parent;lock=json.loads((support/'source-lock.json').read_bytes());names=('source-lock.json','health.py','health-fixture.json','LICENSE');resources={name:pin(support/name)for name in names};lockpin=pin(support/'source-lock.json')
  if home.exists():
   directory(home)
   if not(home/'runtime.json').is_file():raise ValueError('Incomplete existing runtime; preserve it and use a NEW home, never reseal')
   marker=json.loads((home/'runtime.json').read_bytes());oldpin=pin(home/'runtime.json')
   if marker.get('profile')!=lock['profile']or marker.get('source_lock')!=lockpin or marker.get('tree')!=inventory(home):raise ValueError('Changed runtime; restore exact original bytes or use a NEW home, never refresh seals')
   if marker.get('resources')!=resources:raise ValueError('Runtime support differs from this setup export; use matching export or a NEW home')
  else:
   if not a.allow_install:raise Unavailable('Installation was not authorized: provide an existing qualified private home or rerun separate setup with --allow-install where permitted')
   home.mkdir();created=True;(home/'bin').mkdir();(home/'support').mkdir()
   for name in names:shutil.copyfile(support/name,home/'support'/name)
   for edition,definition in lock['editions'].items():
    archive=cache/('gemma-'+edition+'.gz')
    if not archive.exists():
     if not a.allow_download:raise Unavailable('Exact permitted archive absent: '+str(archive)+'. Supply locked bytes offline or allow separate download with --allow-download')
     try:
      with urllib.request.urlopen(definition['binary_url'],timeout=60)as src,archive.open('xb')as dst:
       count=0
       while True:
        block=src.read(65536)
        if not block:break
        count+=len(block)
        if count>definition['archive']['bytes']:raise ValueError('Original archive exceeded locked byte count')
        dst.write(block)
     except (PermissionError,urllib.error.URLError)as e:raise Unavailable('Download/access is unavailable; retain dependent-analysis skip and supply the exact permitted archive offline: '+str(e))from e
    if archive.is_symlink()or pin(archive)!=definition['archive']:raise ValueError('Wrong/changed original archive; do not overwrite it: '+str(archive))
    binary=home/'bin'/('gemma-'+edition)
    with gzip.open(archive,'rb')as src,binary.open('xb')as dst:
     count=0
     while True:
      block=src.read(65536)
      if not block:break
      count+=len(block)
      if count>definition['program']['bytes']:raise ValueError('Uncompressed original exceeds locked byte count')
      dst.write(block)
    if pin(binary)!=definition['program']:raise ValueError('Uncompressed original binary differs')
    binary.chmod(0o755)
   marker=dict(schema_version=1,profile=lock['profile'],source_lock=lockpin,resources=resources,tree=inventory(home),user_analysis_admitted=False,original_binaries_modified=False);put(home/'runtime.json',marker);oldpin=pin(home/'runtime.json')
  records=run(home,report/'health')
  if pin(home/'runtime.json')!=oldpin or inventory(home)!=marker['tree']:raise ValueError('Runtime changed during health; no setup admission')
  put(report/'COMPLETED.json',dict(schema_version=1,status='COMPLETE_PRIVATE_GEMMA_RUNTIME_HEALTH',profile=lock['profile'],runtime_marker=oldpin,health=pin(report/'health/COMPLETED.json'),source_lock=lockpin,original_controls=len(records),user_analysis_admitted=False));(report/'WARNINGS.md').write_text('RUNTIME HEALTH ONLY. No user association analysis admitted. Keep private home, this report and original sources/licenses. Hashes establish integrity, not authenticity or biological correctness. Analysis never installs or repairs software. Read the GEMMA workflow guide before use.\n',encoding='utf-8');print('PASS_PRIVATE_RUNTIME_HEALTH_ONLY',report);return 0
 except (Exception,KeyboardInterrupt)as e:
  code=3 if isinstance(e,(Unavailable,PermissionError))else 2;message=('SKIP3 DEPENDENT ANALYSIS SKIPPED'if code==3 else'FAIL2 NO QUALIFIED RUNTIME')+': '+str(e)+'\nRequired action: preserve this report and all partial files. Supply the named exact permitted prerequisite, or correct the cause using NEW home/report paths. Never reseal a changed existing runtime. No user analysis was run.\n'
  if report_ready:
   try:put(report/'FAILURE.json',dict(status='SKIP3'if code==3 else'FAIL2',error=str(e),required_action=message));(report/'WARNINGS.md').write_text(message,encoding='utf-8')
   except Exception as diagnostic:message+='Diagnostic files could not be completed: '+str(diagnostic)+'. Preserve the directory and inspect access/storage before retrying.\n'
  print(message,file=sys.stderr);return code
if __name__=='__main__':sys.exit(main())
