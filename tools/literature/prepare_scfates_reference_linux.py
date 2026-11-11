# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a new full original scFates/SimplePPT namespace over the unchanged project Python base; no analysis."""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,urllib.error,urllib.request,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

class Unavailable(Exception):pass
def members(group):
 result=[]
 for p in Path('/proc').iterdir():
  if not p.name.isdigit():continue
  try:
   raw=(p/'stat').read_text();f=raw[raw.rindex(')')+2:].split()
   if int(f[2])==group and f[0]!='Z':result.append(int(p.name))
  except (OSError,ValueError,IndexError):pass
 return result

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--archive-cache',action='append',type=Path,default=[]);a=p.parse_args();out=a.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_REFERENCE_SETUP',implementation_credit=False,whole_goal_complete=False,processes=[]);exit_code=2;before=None;base=None;env=dict(os.environ)
 for key in list(env):
  if key.startswith(('PYTHON','PIP_','CONDA_','R_','JAX_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
 for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
 env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),MPLCONFIGDIR=str(out/'private-config/matplotlib'),MPLBACKEND='Agg',PIP_CONFIG_FILE='/dev/null',PIP_NO_CACHE_DIR='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',LANG='C.UTF-8',LC_ALL='C.UTF-8',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',NUMBA_NUM_THREADS='1',JAX_PLATFORMS='cpu')
 def execute(name,command,timeout=600,dependency=False):
  command=list(map(str,command));record=dict(name=name,command=command);child=None;started=time.monotonic()
  try:
   with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),record);code=child.wait(timeout=timeout)
   record['exit_code']=code
   if code and dependency:raise Unavailable(name+' could not supply the exact compatible namespace. Read retained full logs, resolve the named software/network/permission cause and retry NEW paths; no analysis admitted')
   assert code==0,name+' failed; inspect complete retained logs'
  finally:
   if child:
    for sig in [signal.SIGTERM,signal.SIGKILL]:
     if not members(child.pid):break
     try:os.killpg(child.pid,sig)
     except ProcessLookupError:pass
     deadline=time.monotonic()+3
     while members(child.pid) and time.monotonic()<deadline:time.sleep(.05)
    child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
   record['elapsed_seconds']=time.monotonic()-started;save(out/(name+'.process.json'),record);state['processes'].append(record)
 try:
  if not a.allow_install:raise Unavailable('Installation forbidden or --allow-install absent. Skip dependent analysis until exact permitted software is available')
  if not a.allow_download:raise Unavailable('First full-namespace resolution requires explicit --allow-download. This setup does not execute a solver or analysis without permission')
  intake=ROOT/'build/literature-review/scfates-runtime/source-intake-1';source=json.loads((intake/'SOURCE.json').read_bytes());review_path=ROOT/'build/literature-review/scfates-runtime/source-review-1/COMPLETED.json';review=json.loads(review_path.read_bytes());assert source['status']=='PASS_OFFICIAL_SCFATES_SIMPLEPPT_SOURCE_ONLY' and review['status']=='PASS_SCFATES_SIMPLEPPT_SCIENTIFIC_SOURCES_AND_GENERATED_VERSION_REVIEW_ONLY' and review['source']==pin(intake/'SOURCE.json');setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';binding=json.loads((setup/'COMPLETED.json').read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY';base=Path(binding['runtime_environment']);python=Path(binding['runtime_python']);assert python==base/'bin/python';before=inventory(base,progress=out/'shared-before.progress.json');save(out/'shared-before.json',before);assert pin(setup/'runtime-before.json')==binding['runtime_inventory'] and before==json.loads((setup/'runtime-before.json').read_bytes()),'Read-only Python base changed; never refresh its old binding';pins={str(f):pin(f) for f in [Path(__file__).resolve(),intake/'SOURCE.json',review_path,setup/'COMPLETED.json']};constraints='numpy==1.26.4\npandas==2.2.3\nscipy==1.11.4\nscikit-learn==1.3.2\nanndata==0.11.4\nscanpy==1.11.5\n';(out/'constraints.txt').write_text(constraints,encoding='utf8');requirements=[name+'=='+v['version'] for name,v in source['packages'].items()];execute('exact-dependency-resolution',[python,'-I','-B','-m','pip','install','--dry-run','--ignore-installed','--only-binary=:all:','--index-url','https://pypi.org/simple','--report',out/'pip-plan.json','--constraint',out/'constraints.txt',*requirements],600,True);plan=json.loads((out/'pip-plan.json').read_bytes());assert 1<=len(plan['install'])<=100;wheels=out/'wheels';wheels.mkdir();locks=[];total=0
  for row in plan['install']:
   name=row['metadata']['name'];version=row['metadata']['version'];info=row['download_info'];url=info['url'];filename=url.rsplit('/',1)[-1];sha=info['archive_info']['hashes']['sha256'];assert url.startswith('https://files.pythonhosted.org/') and filename.endswith('.whl') and Path(filename).name==filename
   with urllib.request.urlopen('https://pypi.org/pypi/'+name+'/'+version+'/json',timeout=60) as response:raw=response.read(8388609);assert len(raw)<=8388608
   (out/('pypi-'+name+'-'+version+'.json')).write_bytes(raw);metadata=json.loads(raw);matches=[v for v in metadata['urls'] if v['url']==url and v['filename']==filename and v['digests']['sha256']==sha and not v['yanked']];assert len(matches)==1;published=matches[0];assert published['size']<=536870912 and total+published['size']<=2147483648;total+=published['size'];target=wheels/filename;cached=None
   for directory in a.archive_cache:
    candidate=directory/filename
    if candidate.is_file() and not candidate.is_symlink() and pin(candidate)==dict(bytes=published['size'],sha256=sha):cached=candidate;break
   if cached:shutil.copyfile(cached,target)
   else:
    with urllib.request.urlopen(url,timeout=120) as response,target.open('xb') as stream:
     count=0
     while block:=response.read(1048576):count+=len(block);assert count<=published['size'];stream.write(block)
   assert pin(target)==dict(bytes=published['size'],sha256=sha);locks.append(dict(name=name,version=version,filename=filename,url=url,**pin(target),reused_verified_cache=cached is not None))
  for name,v in source['packages'].items():match=[r for r in locks if r['name'].lower()==name];assert len(match)==1 and match[0]['version']==v['version'] and {k:match[0][k] for k in ['bytes','sha256']}=={k:v['wheel'][k] for k in ['bytes','sha256']}
  save(out/'dependency-locks.json',dict(schema_version=1,archives=locks,total_archive_bytes=total,original_source=pin(intake/'SOURCE.json'),shared_base_receipt=pin(setup/'COMPLETED.json'),profile='Linux x86_64 Python3.11.17 NumPy1.26.4 coordinate reference; no historical paper-edition inference'));dependencies=out/'dependencies';execute('install-locked-dependencies',[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',dependencies,*[wheels/r['filename'] for r in locks]],600,True);original_members={}
  for name,v in source['packages'].items():
   original_members[name]={}
   for member,want in v['wheel_members'].items():
    actual=pin(dependencies/member)
    if member.endswith('.dist-info/RECORD'):original_members[name][member]=dict(original=want,installer_generated=actual)
    else:assert actual==want,'Original scientific/license/metadata member changed: '+member
  save(out/'installer-records.json',original_members);initial=inventory(dependencies,progress=out/'dependencies-before.progress.json');save(out/'dependencies-before.json',initial);health=out/'health.py';health.write_text('import sys,os,json,importlib.metadata as m\nfrom pathlib import Path\nsys.path.insert(0,'+repr(str(dependencies))+')\nassert sys.flags.dont_write_bytecode and sys.flags.safe_path and sys.flags.no_user_site and not sys.flags.ignore_environment and os.environ["PYTHONHASHSEED"]=="0"\nimport scFates,simpleppt,numpy,pandas,scipy,scanpy,anndata,sklearn,igraph,numba,skmisc,elpigraph,leidenalg,adjustText,plotly\nfor mod in [scFates,simpleppt,numpy,pandas,scipy,scanpy,anndata,sklearn,igraph,numba,skmisc,elpigraph,leidenalg,adjustText,plotly]:assert Path(mod.__file__).resolve().is_relative_to(Path('+repr(str(dependencies))+'))\nprint(json.dumps(dict(status="PASS_COMPLETE_ORIGINAL_SCFATES_SIMPLEPPT_IMPORTS_ONLY",versions={n:m.version(n) for n in '+repr([r['name'] for r in locks])+'},python=sys.version,namespace_patched=False,analysis_admitted=False)))\n',encoding='utf8');execute('full-namespace-import-health',[python,'-B','-P','-s',health],600,True);report=json.loads((out/'full-namespace-import-health.stdout').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_SIMPLEPPT_IMPORTS_ONLY';after=inventory(dependencies,progress=out/'dependencies-after.progress.json');save(out/'dependencies-after.json',after);assert after==initial;assert all(pin(Path(n))==v for n,v in pins.items());state.update(status='PASS_COMPLETE_PRIVATE_ORIGINAL_SCFATES_SIMPLEPPT_IMPORTS_ONLY',source_pins=pins,python=str(python),shared_prefix=str(base),dependencies=str(dependencies),dependency_locks=pin(out/'dependency-locks.json'),dependency_inventory=pin(out/'dependencies-before.json'),health=report,original_scientific_sources_unchanged=True,namespace_patched=False,analysis_admitted=False);exit_code=0
 except (Unavailable,urllib.error.URLError,PermissionError) as error:state.update(status='SKIP3_SCFATES_RUNTIME_UNAVAILABLE',error=repr(error));exit_code=3;(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 finally:
  if before is not None:
   after=inventory(base,progress=out/'shared-after.progress.json');save(out/'shared-after.json',after);state['shared_runtime_unchanged']=before==after
   if before!=after:state.update(status='FAIL2_SCFATES_SHARED_RUNTIME_CHANGED',error='Read-only Python base changed; no reference or analysis admitted');exit_code=2
  save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nSKIP3: skip dependent scFates analysis if software, downloads or installation are forbidden or unavailable. Read the exact cause and full logs; obtain permitted compatible setup and retry NEW paths. FAIL2 admits no reference/analysis; preserve all originals/failed prefixes and fix the named source/hash/import cause before retrying NEW paths. Never refresh old runtime binding hashes. RUN must not install. This is full original import preparation only, not validated fitting or paper reproduction. The shared Python base stays read-only. Original scientific code and diagnostic suppression remain unchanged. CPU coordinate reference only; optional R/GAM, GPU and unrelated analysis are not admitted.\n',encoding='utf8')
 print(state['status'],state.get('error',''));return exit_code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable full namespace/runtime checks')
 raise SystemExit(main())
