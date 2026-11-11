# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare original Hail0.2.139/Spark3.5.3 in a NEW private Linux environment. No cloud submission or implementation credit."""
import argparse,hashlib,json,os,signal,subprocess,sys,time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
ACTION='SKIP3: keep dependent Hail analysis skipped until exact permitted Linux x86_64 Python3.12, Java21, Hail0.2.139/Spark3.5.3 and named dependencies load and health passes. Where installation is permitted correct the named package/library/network/permission/resource error in NEW private paths; preserve all originals, archives and failure logs. Analysis installs nothing. This preparation grants no Hail integration, cloud-scale or paper-reproduction credit.'

def pin(p):
    h=hashlib.sha256();count=0
    with p.open('rb') as f:
        while block:=f.read(1048576):count+=len(block);h.update(block)
    return dict(bytes=count,sha256=h.hexdigest())
def save(p,value):p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def identities(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file() and not p.is_symlink()}
def group_members(group):
    result=[]
    for p in Path('/proc').iterdir():
        if not p.name.isdigit():continue
        try:
            raw=(p/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
            if int(fields[2])==group:result.append(dict(pid=int(p.name),state=fields[0],process_group=int(fields[2]),session=int(fields[3]),start_ticks=int(fields[19])))
        except (FileNotFoundError,ProcessLookupError,PermissionError):pass
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--environment',required=True,type=Path);p.add_argument('--pip-bootstrap',type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();prefix=a.environment.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and prefix.is_relative_to(ROOT/'build') and not out.exists() and not prefix.exists() and not out.is_relative_to(prefix) and not prefix.is_relative_to(out);out.mkdir(parents=True);sources={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))};(out/'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_HAIL_REFERENCE_UNAVAILABLE',source_pins=sources,required_action=ACTION,implementation_credit=False,whole_goal_complete=False,processes=[]);temporary=out/'private-temp';temporary.mkdir();config=out/'private-config';config.mkdir();cache=out/'private-cache';cache.mkdir();env=dict(os.environ)
    for key in list(env):
        if key.startswith(('HAIL_','SPARK_','PYSPARK_','PYTHON','PIP_')) or key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','HADOOP_CONF_DIR','YARN_CONF_DIR']:env.pop(key,None)
    java=Path('/usr/lib/jvm/java-21-openjdk-amd64');env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',TMPDIR=str(temporary),XDG_CONFIG_HOME=str(config),XDG_CACHE_HOME=str(cache),PIP_CONFIG_FILE='/dev/null',PIP_CACHE_DIR=str(cache/'pip'),PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_NO_INPUT='1',PYTHONDONTWRITEBYTECODE='1',JAVA_HOME=str(java),SPARK_LOCAL_IP='127.0.0.1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1');shared={};owned=[]
    def execute(name,command,timeout=1200):
        record=dict(name=name,command=list(map(str,command)),started_at=time.time(),status='RUNNING_PROCESS_ONLY');save(out/(name+'.STARTED.json'),record);child=None;error=None;code=None;started=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                child=subprocess.Popen(list(map(str,command)),cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);owned.append(child.pid);record['owned_process_group']=child.pid
                while child.poll() is None:
                    if time.monotonic()-started>timeout:raise TimeoutError('Owned Hail preparation process exceeded timeout: '+name)
                    time.sleep(.1)
                code=child.returncode
            if code:raise RuntimeError(name+' failed with exit'+str(code)+'; inspect '+name+'.stderr/stdout')
            record['status']='completed-process-only'
        except Exception as e:error=e;record.update(status='SKIP3_PROCESS_OR_HEALTH_UNAVAILABLE',error=repr(e))
        finally:
            if child is not None:
                record['owned_before_cleanup']=group_members(child.pid)
                for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                    if not group_members(child.pid):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(r['state']!='Z' for r in group_members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_after_cleanup']=group_members(child.pid);record['owned_live_after_cleanup']=[r for r in record['owned_after_cleanup'] if r['state']!='Z']
                if record['owned_live_after_cleanup']:error=RuntimeError('Owned preparation workers remain alive; inspect cleanup receipt');record.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error=repr(error))
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(name+'.process.json'),record);state['processes'].append(record)
        if error:raise error
    try:
        assert a.allow_install,'Installation not permitted for this invocation';assert sys.platform=='linux' and os.uname().machine=='x86_64' and sys.version_info[:2]==(3,12);assert java.is_dir();before_python=pin(Path(sys.executable).resolve());before_java=identities(java);shared=dict(python=before_python,java=before_java);save(out/'shared-before.json',shared);intake=json.loads((a.intake/'COMPLETED.json').read_bytes());assert intake['status']=='PASS_TWO_ORIGINAL_HAIL_EDITIONS_SOURCE_CAPTURE_ONLY';source=intake['versions']['0.2.139'];assert source['revision']=='5e5eedb87ff814916ef08037199fec7b158f999b';wheel=ROOT/source['wheel']['file'];assert pin(wheel)=={k:source['wheel'][k] for k in ['bytes','sha256']};assert b'SPARK_VERSION ?= 3.5.3' in (a.intake/'0.2.139/upstream-hail_Makefile').read_bytes();venv=[sys.executable,'-I','-B','-m','venv','--copies'];
        if a.pip_bootstrap:venv.append('--without-pip')
        execute('venv',[*venv,prefix],120);python=prefix/'bin/python';archives=out/'archives';archives.mkdir();base=[python,'-I','-B','-m','pip']
        if a.pip_bootstrap:
            receipt=json.loads((a.pip_bootstrap/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_EXACT_OFFICIAL_PIP_BOOTSTRAP_WHEEL_CAPTURE_ONLY';pip_wheel=a.pip_bootstrap/'pip-24.3.1-py3-none-any.whl';assert pin(pip_wheel)==receipt['archive'];bootstrap=out/'bootstrap-pip.py';bootstrap.write_text('import runpy,sys\nwheel=sys.argv.pop(1)\nsys.path.insert(0,wheel)\nrunpy.run_module("pip",run_name="__main__")\n',encoding='utf8',newline='\n');execute('private-pip-bootstrap',[python,'-I','-B',bootstrap,pip_wheel,'install','--no-index','--no-compile',pip_wheel],120)
        execute('bootstrap-download',[*base,'download','--index-url','https://pypi.org/simple','--dest',archives,'setuptools==80.9.0','wheel==0.45.1'],300);execute('bootstrap-install',[*base,'install','--no-index','--find-links',archives,'--no-compile','setuptools==80.9.0','wheel==0.45.1'],120);execute('download',[*base,'download','--no-build-isolation','--index-url','https://pypi.org/simple','--dest',archives,wheel,'pyspark==3.5.3'],1800);save(out/'downloaded-archives.json',{f.name:pin(f) for f in sorted(archives.iterdir()) if f.is_file()});execute('install',[*base,'install','--no-index','--find-links',archives,'--no-build-isolation','--no-compile','--report',out/'pip-report.json',wheel,'pyspark==3.5.3'],1200);execute('pip-check',[*base,'check'],120);execute('freeze',[*base,'freeze','--all','--local'],120)
        health=out/'health.py';health.write_text('''import importlib.metadata as m,json,sys,pathlib
import hail as hl
assert hl.__version__=="0.2.139-5e5eedb87ff8",hl.__version__
home=pathlib.Path(sys.argv[1]);prefix=pathlib.Path(sys.prefix)
assert pathlib.Path(hl.__file__).resolve().is_relative_to(prefix)
try:
 hl.init(backend="spark",master="local[2]",log=str(home/"original-hail.log"),tmp_dir=str(home/"hail-temp"),local_tmpdir=str(home/"hail-local-temp"),spark_conf={"spark.driver.memory":"2g","spark.executor.memory":"2g","spark.driver.extraJavaOptions":"-XX:ActiveProcessorCount=2","spark.executor.extraJavaOptions":"-XX:ActiveProcessorCount=2","spark.ui.enabled":"false","spark.ui.showConsoleProgress":"false"})
 assert hl.eval(hl.int32(1)+hl.int32(1))==2
 report=dict(status="PASS_ORIGINAL_HAIL_LOCAL_SPARK_HEALTH_ONLY",hail=hl.__version__,python=sys.version,spark=hl.current_backend()._spark.version,package_path=hl.__file__,packages={d.metadata["Name"]:d.version for d in m.distributions()},genotype_analysis_executed=False,cloud_batch_submitted=False)
 assert report["spark"]=="3.5.3"
 (home/"HEALTH.json").write_text(json.dumps(report,indent=2)+"\\n")
finally:hl.stop()
''',encoding='utf8',newline='\n');execute('health',[python,'-I','-B','-X','pycache_prefix='+str(out/'private-pycache'),health,out],240);report=json.loads((out/'HEALTH.json').read_bytes());assert report['status']=='PASS_ORIGINAL_HAIL_LOCAL_SPARK_HEALTH_ONLY';site=prefix/'lib/python3.12/site-packages';checked=0
        for name,value in source['members'].items():
            if not name.startswith(('hail/','hailtop/')):continue
            assert pin(site/name)==value,('Installed original Hail source differs',name);checked+=1
        state.update(status='PASS_PRIVATE_ORIGINAL_HAIL139_SPARK353_REFERENCE_ONLY',runtime_environment=str(prefix),runtime_python=str(python),java_home=str(java),health=report,original_source_members_checked=checked,original_source=source['wheel'],resolved_packages=pin(out/'pip-report.json'),runtime_file_inventory=pin(out/'downloaded-archives.json'),genotype_analysis_executed=False,cloud_batch_submitted=False)
    except Exception as error:state['error']=repr(error);print('SKIP3 DEPENDENT HAIL ANALYSIS SKIPPED:',repr(error),flush=True)
    finally:
        if shared:
            after=dict(python=pin(Path(sys.executable).resolve()),java=identities(java));save(out/'shared-after.json',after);state['preexisting_python_and_JDK_unchanged']=after==shared
            if after!=shared:state.update(status='FAIL2_PREEXISTING_RUNTIME_CHANGED',error='Inspect complete before/after identities; no result admitted')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original Hail/Spark local expression health passed. No genotype workflow, local Batch DAG or cloud job has been validated yet.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original runtime/source preparation checks')
    raise SystemExit(main())
