# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare an isolated original Hail0.2.130 reference; no cloud access, paper reproduction or integration credit."""
import argparse,json,os,shutil,signal,subprocess,sys,time,urllib.request
from pathlib import Path
from setup_hail_reference_linux import ROOT,group_members,identities,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--environment',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();prefix=a.environment.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and prefix.is_relative_to(ROOT/'build') and not out.exists() and not prefix.exists() and not out.is_relative_to(prefix) and not prefix.is_relative_to(out);out.mkdir(parents=True);(out/'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_HAIL130_REFERENCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,cloud_batch_submitted=False,processes=[],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__)),(ROOT/'tools/literature/setup_hail_reference_linux.py').relative_to(ROOT).as_posix():pin(ROOT/'tools/literature/setup_hail_reference_linux.py')});action='Keep dependent Hail0.2.130 analysis skipped unless the exact permitted Python3.10/Java11/Spark3.3.2 runtime loads and health passes. Preserve this attempt. If installation is permitted, correct the named dependency/network/library/permission problem and repeat in NEW private paths; otherwise supply an allowed compatible runtime or retain SKIP3. Never substitute the Hail139 environment.';env=dict(os.environ)
    for key in list(env):
        if key.startswith(('HAIL_','SPARK_','PYSPARK_','PYTHON','PIP_','MAMBA_','CONDA_')) or key in ['JAVA_HOME','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','HADOOP_CONF_DIR','YARN_CONF_DIR']:env.pop(key,None)
    for name in ['private-temp','private-config','private-cache']:(out/name).mkdir()
    env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',TMPDIR=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),MAMBA_ROOT_PREFIX=str(out/'mamba-root'),PIP_CONFIG_FILE='/dev/null',PIP_CACHE_DIR=str(out/'private-cache/pip'),PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_NO_INPUT='1',PYTHONDONTWRITEBYTECODE='1',SPARK_LOCAL_IP='127.0.0.1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1');before=None
    def execute(name,command,timeout):
        command=list(map(str,command));record=dict(name=name,command=command,started_at=time.time());child=None;failure=None;start=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);record['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),record)
                while child.poll() is None:
                    if time.monotonic()-start>timeout:raise TimeoutError(name+' exceeded declared preparation timeout')
                    time.sleep(.1)
                record['exit_code']=child.returncode
                if child.returncode:raise RuntimeError(name+' exit'+str(child.returncode)+'; inspect saved stdout/stderr')
            record['status']='completed-process-only'
        except Exception as error:failure=error;record.update(status='SKIP3_PROCESS_UNAVAILABLE',error=repr(error))
        finally:
            if child is not None:
                record['owned_before_cleanup']=group_members(child.pid)
                for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                    if not group_members(child.pid):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(v['state']!='Z' for v in group_members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_after_cleanup']=group_members(child.pid);record['owned_live_after_cleanup']=[v for v in record['owned_after_cleanup'] if v['state']!='Z']
                if record['owned_live_after_cleanup']:failure=RuntimeError('Owned preparation workers remain alive');record.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error=repr(failure))
            record['elapsed_seconds']=time.monotonic()-start;save(out/(name+'.process.json'),record);state['processes'].append(record)
        if failure:raise failure
    try:
        assert a.allow_install and sys.platform=='linux' and os.uname().machine=='x86_64';shared_java=Path('/usr/lib/jvm/java-21-openjdk-amd64');before=dict(python=pin(Path(sys.executable).resolve()),java=identities(shared_java));save(out/'shared-before.json',before);intake=ROOT/'build/literature-review/hail-runtime/source-intake-2';receipt=json.loads((intake/'COMPLETED.json').read_bytes());source=receipt['versions']['0.2.130'];wheel=ROOT/source['wheel']['file'];assert source['revision']=='bea04d9c79b5ca739364e8c121132845475f617a' and pin(wheel)=={k:source['wheel'][k] for k in ['bytes','sha256']};url='https://raw.githubusercontent.com/hail-is/hail/'+source['revision']+'/hail/Makefile'
        with urllib.request.urlopen(url,timeout=60) as response:raw=response.read(1048577)
        (out/'upstream-Makefile').write_bytes(raw);save(out/'upstream-Makefile.json',dict(url=url,**pin(out/'upstream-Makefile')));assert len(raw)<=1048576;url='https://raw.githubusercontent.com/hail-is/hail/'+source['revision']+'/hail/python/pinned-requirements.txt'
        with urllib.request.urlopen(url,timeout=60) as response:raw=response.read(1048577)
        (out/'upstream-pinned-requirements.txt').write_bytes(raw);save(out/'upstream-pinned-requirements.json',dict(url=url,**pin(out/'upstream-pinned-requirements.txt')));assert len(raw)<=1048576 and 'pyspark==3.3.2' in raw.decode().splitlines(),'Exact original pinned PySpark version differs';original=ROOT/'build/literature-review/loftee-runtime/reference-setup-1/micromamba';mamba=out/'micromamba';shutil.copyfile(original,mamba);mamba.chmod(0o700);assert pin(mamba)==pin(original);save(out/'micromamba-source.json',dict(reused_read_only_source=str(original),**pin(mamba)));base=[mamba,'create','--no-rc','--override-channels','-c','conda-forge','-p',prefix,'python=3.10','openjdk=11','pip=24.3.1','setuptools=80.9.0','wheel=0.45.1'];execute('environment-plan',[*base,'--dry-run','--json'],900);execute('environment-install',[*base,'-y','--json'],1800);python=prefix/'bin/python';java=prefix/'lib/jvm';assert python.is_file() and (prefix/'bin/java').is_file();java_home=Path(subprocess.check_output([str(prefix/'bin/java'),'-XshowSettings:properties','-version'],env=env,stderr=subprocess.STDOUT).decode().split('java.home = ',1)[1].splitlines()[0].strip());assert java_home.is_relative_to(prefix);env['JAVA_HOME']=str(java_home);archives=out/'archives';archives.mkdir();pip=[python,'-I','-B','-m','pip'];execute('download',[*pip,'download','--no-build-isolation','--index-url','https://pypi.org/simple','--dest',archives,wheel,'pyspark==3.3.2'],1800);save(out/'downloaded-archives.json',{f.name:pin(f) for f in archives.iterdir() if f.is_file()});execute('install',[*pip,'install','--no-index','--find-links',archives,'--no-build-isolation','--no-compile','--report',out/'pip-report.json',wheel,'pyspark==3.3.2'],1200);execute('pip-check',[*pip,'check'],120)
        health=out/'health.py';health.write_text('''import json,pathlib,sys
import hail as hl
home=pathlib.Path(sys.argv[1]);assert hl.version()=='0.2.130-bea04d9c79b5'
assert pathlib.Path(hl.__file__).resolve().is_relative_to(pathlib.Path(sys.prefix))
try:
 hl.init(backend='spark',master='local[2]',log=str(home/'original-hail.log'),tmp_dir=str(home/'hail-temp'),local_tmpdir=str(home/'hail-local-temp'),spark_conf={'spark.driver.memory':'2g','spark.executor.memory':'2g','spark.driver.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.executor.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.ui.enabled':'false','spark.ui.showConsoleProgress':'false'})
 assert hl.eval(hl.int32(1)+hl.int32(1))==2
 report=dict(status='PASS_ORIGINAL_HAIL130_LOCAL_SPARK_HEALTH_ONLY',hail=hl.version(),python=sys.version,spark=hl.spark_context().version,package_path=hl.__file__,genotype_or_liftover_analysis_executed=False,cloud_batch_submitted=False)
 assert report['spark']=='3.3.2'
 (home/'HEALTH.json').write_text(json.dumps(report,indent=2)+'\\n')
finally:hl.stop()
''',encoding='utf8',newline='\n');execute('health',[python,'-I','-B','-X','pycache_prefix='+str(out/'private-pycache'),health,out],300);site=prefix/'lib/python3.10/site-packages';checked=0
        for name,want in source['members'].items():
            if name.startswith(('hail/','hailtop/')):assert pin(site/name)==want,name;checked+=1
        state.update(status='PASS_PRIVATE_ORIGINAL_HAIL130_SPARK332_REFERENCE_ONLY',runtime_environment=str(prefix),runtime_python=str(python),java_home=str(java_home),health=json.loads((out/'HEALTH.json').read_bytes()),original_source_members_checked=checked,original_source=source['wheel'])
    except Exception as failure:state['error']=repr(failure);print('SKIP3 Hail130 dependent analysis skipped:',repr(failure),flush=True)
    finally:
        if before:
            after=dict(python=pin(Path(sys.executable).resolve()),java=identities(shared_java));save(out/'shared-after.json',after);state['preexisting_python_and_JDK_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_PREEXISTING_RUNTIME_CHANGED',error='Shared runtime identity changed; inspect complete before/after records')
        state['required_action']=action;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original local expression health only; no liftover analysis or cloud submission.')+'\n\nRequired action: '+action+'\n',encoding='utf8',newline='\n')
    print(state['status'],flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original legacy preparation checks')
    raise SystemExit(main())
