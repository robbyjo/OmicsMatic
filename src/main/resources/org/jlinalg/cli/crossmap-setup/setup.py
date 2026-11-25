# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install or recheck a new private pinned CrossMap runtime; never run user analysis."""
from pathlib import Path
import argparse,hashlib,json,os,platform,shutil,stat,subprocess,sys,time,urllib.error,urllib.request,zipfile
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parent
class Unavailable(Exception):pass
def put(p,v):p.write_bytes((json.dumps(v,indent=2)+'\n').encode())
def read(p):return json.loads(p.read_bytes())
def pin(p):
 with p.open('rb')as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def tree(root):
 rows={}
 for parent,dirs,names in os.walk(root,followlinks=False):
  for n in sorted(dirs+names):
   p=Path(parent)/n;name=p.relative_to(root).as_posix()
   if name=='runtime.json':continue
   if p.is_symlink():
    target=os.readlink(p);require(not Path(target).is_absolute()and '..'not in Path(target).parts and p.resolve().is_relative_to(root),'Runtime link escapes private home');rows[name]=dict(kind='symlink',target=target)
   elif p.is_file():rows[name]=dict(kind='file',**pin(p))
   else:require(p.is_dir(),'Unknown runtime node');rows[name]=dict(kind='directory')
 return rows
def external_files():
 import sysconfig
 root=Path(sysconfig.get_path('stdlib'));files={};links={}
 for p in root.rglob('*'):
  if p.is_symlink():links[str(p)]=os.readlink(p)
  if p.is_file():files[str(p.resolve())]=pin(p.resolve())
 for p in[Path(sys.executable).resolve(),Path('/usr/bin/sort')]:require(p.is_file(),'Required exact native helper is absent');files[str(p)]=pin(p)
 # Loaded system libraries remain an explicit current-host prerequisite.
 for line in Path('/proc/self/maps').read_text().splitlines():
  fields=line.split()
  if len(fields)>=6 and fields[-1].startswith('/'):
   p=Path(fields[-1])
   if p.is_file():files[str(p.resolve())]=pin(p.resolve())
 return dict(files=files,symlinks=links,interpreter=str(Path(sys.executable).resolve()),stdlib=str(root),scope='Current-host interpreter, complete standard-library files and loaded installer system libraries; not a portable OS or author-Python reconstruction')
def external_check(data):
 for name,identity in data['files'].items():require(pin(Path(name))==identity,'Current-host interpreter/library/helper changed: '+name)
 for name,target in data['symlinks'].items():require(Path(name).is_symlink()and os.readlink(name)==target,'Current-host library link changed: '+name)
def invoke(command,label,report,env=None):
 folder=report/label;folder.mkdir();command=list(map(str,command));put(folder/'COMMAND.json',command);start=time.time()
 with(folder/'stdout').open('xb')as out,(folder/'stderr').open('xb')as err:child=subprocess.run(command,cwd=report,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
 put(folder/'RESULT.json',dict(exit_code=child.returncode,elapsed_seconds=time.time()-start));require(child.returncode==0,'Setup '+label+' failed; inspect retained stdout/stderr');return folder
def asset(row,cache,download):
 p=cache/row['filename']
 if not p.exists():
  if not download:raise Unavailable('Missing pinned '+row['filename']+'; provide the exact offline wheel or permit download separately')
  cache.mkdir(parents=True,exist_ok=True);temporary=cache/(row['filename']+'.partial');require(not temporary.exists(),'Retain incomplete download; use a new cache path')
  try:
   request=urllib.request.Request(row['url'],headers={'User-Agent':'OmicsMatic-CrossMap-setup'})
   with urllib.request.urlopen(request,timeout=60)as response,temporary.open('xb')as out:
    total=0
    for block in iter(lambda:response.read(1048576),b''):
     total+=len(block);require(total<=row['bytes'],'Downloaded asset exceeds pinned size');out.write(block)
  except(urllib.error.URLError,PermissionError)as e:raise Unavailable('Required permitted download failed: '+str(e))from e
  require(pin(temporary)=={k:row[k]for k in('bytes','sha256')},'Downloaded pinned asset mismatch');temporary.rename(p)
 require(p.is_file()and not p.is_symlink()and pin(p)=={k:row[k]for k in('bytes','sha256')},'Changed/invalid pinned wheel: '+str(p));return p
def extract_original(archive,home,record):
 folder=home/'sources'/record['edition'];folder.mkdir(parents=True)
 with zipfile.ZipFile(archive)as z:
  require(len(z.namelist())==len(set(z.namelist()))and set(z.namelist())==set(record['members']),'Original wheel namespace changed')
  for name,identity in record['members'].items():
   n=Path(name);require(not n.is_absolute()and '..'not in n.parts and '\\'not in name and ':'not in name,'Unsafe original member');raw=z.read(name);require(len(raw)==identity['bytes']and hashlib.sha256(raw).hexdigest()==identity['sha256'],'Original source member differs');p=folder/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
def check_original(home,lock):
 for version,row in lock['sources'].items():
  base=home/'sources'/version;actual={p.relative_to(base).as_posix():pin(p)for p in base.rglob('*')if p.is_file()};require(actual==row['members'],'Changed/extra original source files: '+version)
def main(a):
 report=a.report.absolute();require(report.resolve()==report and report.parent.is_dir(),'Require a canonical existing report parent');report.mkdir();a._report_created=True;lock=read(ROOT/'source-lock.json');home=a.home.absolute();require(home.resolve()==home and home.parent.is_dir(),'Require a canonical existing private-home parent');require(not(home.is_relative_to(report)or report.is_relative_to(home)),'Report must be outside private home and its ancestors');cache=a.cache.absolute();require(not(home.is_relative_to(cache)or cache.is_relative_to(home)),'Wheel cache must be outside private home and its ancestors');put(report/'request.json',dict(home=str(home),profile=lock['profile'],allow_install=a.allow_install,allow_download=a.allow_download,user_analysis_admitted=False))
 if sys.platform!='linux'or platform.machine()!='x86_64'or sys.version_info[:3]!=(3,12,3):raise Unavailable('This qualified provider requires Linux x86_64 CPython3.12.3; supply that permitted interpreter before setup')
 for helper in('/usr/bin/sort','/usr/bin/setsid','/usr/bin/prlimit'):
  if not Path(helper).is_file()or not os.access(helper,os.R_OK|os.X_OK):raise Unavailable('Required permitted Linux helper is unavailable: '+helper+'; complete separate allowed setup or retain SKIP3')
 if not os.access('/proc/self/maps',os.R_OK):raise Unavailable('Readable Linux /proc metadata is required; dependent setup/conversion skipped')
 from health import environment
 if home.exists():
  marker=read(home/'runtime.json');require(marker['schema_version']==1 and marker['profile']==lock['profile']and marker['source_lock']==pin(ROOT/'source-lock.json'),'Existing runtime profile/source lock differs; never refresh old hashes');require(pin(home/'support/health.py')==pin(ROOT/'health.py'),'Existing runtime helper differs from this executable; use a new runtime home');check_original(home,lock);require(tree(home)==marker['tree'],'Existing private runtime changed');external_check(marker['external']);mode='RECHECK_EXISTING_RUNTIME_NO_RESEAL'
 else:
  if not a.allow_install:raise Unavailable('Installation is not permitted; provide the exact completed private runtime or explicitly permit separate setup')
  rows=[lock['bootstrap'],*lock['dependencies'],*lock['sources'].values()];archives={row['filename']:asset(row,a.cache.absolute(),a.allow_download)for row in rows}
  invoke([sys.executable,'-I','-B','-m','venv','--copies','--without-pip',home],'create-private-venv',report)
  support=home/'support';support.mkdir()
  for name in('source-lock.json','health.py'):shutil.copyfile(ROOT/name,support/name)
  python=home/'bin/python';bootstrap="import sys,runpy;w=sys.argv.pop(1);sys.path.insert(0,w);sys.argv[0]='pip';runpy.run_module('pip',run_name='__main__')";env=environment(report);env.update(PIP_CONFIG_FILE='/dev/null',PIP_NO_INPUT='1')
  command=[python,'-I','-B','-c',bootstrap,archives[lock['bootstrap']['filename']],'install','--no-index','--no-deps','--no-cache-dir','--no-compile','--disable-pip-version-check',*[archives[row['filename']]for row in lock['dependencies']]];invoke(command,'install-pinned-private-dependencies',report,env)
  for row in lock['sources'].values():extract_original(archives[row['filename']],home,row)
  check_original(home,lock);mode='NEW_PRIVATE_RUNTIME_INSTALLED'
 # Each source probe uses a separate interpreter to prevent cross-edition imports.
 for version in('0.5.2','0.6.3'):invoke([home/'bin/python','-I','-B',ROOT/'health.py','--home',home,'--probe',version],'source-probe-'+version,report,environment(report))
 invoke([home/'bin/python','-I','-B',ROOT/'health.py','--home',home,'--health',report/'health'],'functional-health',report,environment(report))
 if mode=='NEW_PRIVATE_RUNTIME_INSTALLED':
  marker=dict(schema_version=1,profile=lock['profile'],source_lock=pin(ROOT/'source-lock.json'),tree=tree(home),external=external_files(),user_analysis_admitted=False,author_Python37_reproduced=False);put(home/'runtime.json',marker)
 else:require(tree(home)==marker['tree'],'Existing runtime changed during recheck');external_check(marker['external'])
 diagnostics=[p.relative_to(report).as_posix()for p in report.rglob('stderr')if p.stat().st_size]
 text='# CROSSMAP SETUP WARNINGS AND REQUIRED ACTION\n\nThis is an artificial runtime health check, not a user analysis or paper reproduction. The selected interpreter is CPython3.12.3; the Python3.7 environment reported by P072 is not reproduced.\n\nOriginal source diagnostics are retained, including Python SyntaxWarning messages about legacy escape sequences. Read each named stderr file; no diagnostic was suppressed or source program rewritten.\n\n'+''.join('- '+p+'\n'for p in diagnostics)+'\nFor a missing/forbidden prerequisite, retain SKIP3 and skip dependent analysis. For changed/incomplete files or process failures, retain FAIL2 and use new home/report paths after resolving the cause. Preserve every failed attempt; never refresh a changed runtime seal. Require successful conversion and complete saved-result verification before downstream use.\n'
 (report/'WARNINGS.md').write_bytes(text.encode());put(report/'COMPLETED.json',dict(status='COMPLETE_PRIVATE_CROSSMAP_RUNTIME_HEALTH',mode=mode,profile=lock['profile'],runtime_marker=pin(home/'runtime.json'),original_source_editions=['0.5.2','0.6.3'],original_formats_checked=['bed','vcf','bigwig'],warnings=pin(report/'WARNINGS.md'),user_analysis_admitted=False,author_Python37_reproduced=False));print('Private CrossMap setup completed. Read WARNINGS.md and retained source diagnostics. Require this completed health report and unchanged runtime before dependent analysis. No user analysis was admitted.');return 0
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args()
 try:sys.exit(main(args))
 except Exception as e:
  code=3 if isinstance(e,(Unavailable,PermissionError,FileNotFoundError))else 2;label='SKIP3: DEPENDENT ANALYSIS SKIPPED'if code==3 else'FAIL2: NO QUALIFIED RUNTIME';action='Required action: preserve this attempt and all original files. Supply the named exact permitted prerequisite or complete separate authorized setup; use new home/report paths for changed or incomplete installs. Never re-seal a changed runtime. No user analysis is admitted by setup.'
  if getattr(args,'_report_created',False):
   try:put(args.report/'FAILURE.json',dict(status=label,error=repr(e),required_action=action));(args.report/'WARNINGS.md').write_bytes(('# '+label+'\n\n'+repr(e)+'\n\n'+action+'\n').encode())
   except OSError:pass
  print(label+': '+repr(e)+'\n'+action,file=sys.stderr);sys.exit(code)
