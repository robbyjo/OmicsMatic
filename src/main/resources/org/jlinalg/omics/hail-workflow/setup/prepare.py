# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Separate exact Hail runtime preparation. CAPTURE reads existing software; INSTALL creates a new private venv only with permission flags."""
import argparse,hashlib,importlib.util,json,os,re,shutil,signal,subprocess,sys,time,urllib.request
from pathlib import Path

def pin(path):
    sha=hashlib.sha256();size=0
    with path.open('rb') as stream:
        while block:=stream.read(1048576):size+=len(block);sha.update(block)
    return dict(bytes=size,sha256=sha.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            raw=(path/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
            if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],process_group=int(fields[2]),session=int(fields[3]),start_ticks=int(fields[19])))
        except (FileNotFoundError,ProcessLookupError,PermissionError):pass
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mode',choices=['CAPTURE','INSTALL'],required=True);p.add_argument('--edition',choices=['0.2.139','0.2.130'],required=True);p.add_argument('--prefix',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--base-python',type=Path);p.add_argument('--archive-cache',type=Path,action='append',default=[]);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.absolute();prefix=a.prefix.absolute();java=a.java_home.absolute();assert not out.exists() and out.parent.resolve()==out.parent and out==out.resolve() and not out.is_relative_to(prefix) and not prefix.is_relative_to(out) and not out.is_relative_to(java) and not java.is_relative_to(out);out.mkdir();state=dict(status='SKIP3_HAIL_RUNTIME_UNAVAILABLE',edition=a.edition,mode=a.mode,analysis_executed=False,cloud_batch_submitted=False,processes=[]);action='Keep dependent Hail analysis skipped until exact runtime binding passes. Preserve all logs, archives and failed prefixes. Where installation is permitted, provide the named compatible Python/Java11 and exact archive cache, or explicitly enable the needed INSTALL/download flag in NEW paths. Where forbidden, supply an already installed allowed runtime with CAPTURE or keep analysis skipped. Never silently repair a shared runtime or choose another release/hash.';before=None;shared=None;inventory=None;env=dict(os.environ);kit=Path(__file__).resolve().parent.parent
    for key in list(env):
        if key.startswith(('HAIL_','SPARK_','PYSPARK_','PYTHON','PIP_')) or key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','HADOOP_CONF_DIR','YARN_CONF_DIR']:env.pop(key,None)
    for name in ['private-temp','private-config','private-cache']:(out/name).mkdir()
    env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',JAVA_HOME=str(java),TMPDIR=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),PIP_CONFIG_FILE='/dev/null',PIP_CACHE_DIR=str(out/'private-cache/pip'),PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_NO_INPUT='1',PYTHONDONTWRITEBYTECODE='1',PYTHONPYCACHEPREFIX=str(out/'private-pycache'),SPARK_LOCAL_IP='127.0.0.1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1');cancelled=[]
    def cancel(sig,frame):cancelled.append(sig)
    if sys.platform=='linux':
        for sig in [signal.SIGINT,signal.SIGTERM,signal.SIGHUP]:signal.signal(sig,cancel)
    def execute(name,command,timeout):
        command=list(map(str,command));row=dict(command=command,started_at=time.time());child=None;error=None;start=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);row['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),row)
                while child.poll() is None:
                    if cancelled:raise InterruptedError('Preparation cancelled')
                    if time.monotonic()-start>timeout:raise TimeoutError('Owned preparation exceeded declared phase limit: '+name)
                    time.sleep(.1)
                row['exit_code']=child.returncode
                if child.returncode:raise RuntimeError(name+' failed: inspect saved stdout/stderr; no runtime/analysis admitted')
            row['status']='completed-process-only'
        except Exception as failure:error=failure;row.update(status='SKIP3_PREPARATION_PROCESS_UNAVAILABLE',error=repr(failure))
        finally:
            if child is not None:
                row['owned_before_cleanup']=members(child.pid)
                for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                    if not members(child.pid):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(v['state']!='Z' for v in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);row['owned_after_cleanup']=members(child.pid);row['owned_live_after_cleanup']=[v for v in row['owned_after_cleanup'] if v['state']!='Z']
                if row['owned_live_after_cleanup']:error=RuntimeError('Owned preparation workers remain alive');row.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error=repr(error))
            row['elapsed_seconds']=time.monotonic()-start;save(out/(name+'.process.json'),row);state['processes'].append(row)
        if error:raise error
    try:
        assert sys.platform=='linux' and os.uname().machine=='x86_64','This exact runtime profile requires Linux x86_64';assert java.resolve()==java and (java/'bin/java').is_file();manifest=json.loads((kit/'setup-kit.json').read_bytes())
        for name,want in manifest['files'].items():assert pin(kit/name)==want,'Exported setup kit changed: '+name
        spec=importlib.util.spec_from_file_location('omicsmatic_hail_runtime_inventory',kit/'setup/inventory.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);inventory=module.inventory;profile=json.loads((kit/'profiles.json').read_bytes())['editions'][a.edition];locks=json.loads((kit/'setup/dependency-locks.json').read_bytes());execute('java-version',[java/'bin/java','-version'],60);text=(out/'java-version.stdout').read_text()+(out/'java-version.stderr').read_text();assert re.search(r'version "11(?:\.|\")',text),'Provide the exact permitted Java11 profile';shared=dict(java=inventory(java,progress=out/'java-shared-before.progress.json'))
        if a.mode=='INSTALL':
            assert a.allow_install,'Installation flag absent: installation is not authorized for this invocation';assert a.base_python and a.base_python.is_file() and not prefix.exists(),'INSTALL requires a supplied compatible base Python and a NEW prefix';base=a.base_python.resolve();shared['base_python']=dict(path=str(base),**pin(base));execute('base-python-version',[base,'-I','-B','-c','import sys; print(".".join(map(str,sys.version_info[:2])))'],60);assert (out/'base-python-version.stdout').read_text().strip()==profile['python_minor'],'Base Python minor version differs from exact profile';archives=out/'archives';archives.mkdir();needed={item['filename']:item for item in [*locks['bootstrap'],*locks['editions'][a.edition]['packages']]};archive_paths={}
            for name,item in needed.items():
                assert Path(name).name==name and item['url'].startswith('https://files.pythonhosted.org/');want={k:item[k] for k in ['bytes','sha256']};target=archives/name;cached=[root/name for root in a.archive_cache if (root/name).is_file()]
                if cached:
                    assert all(pin(path)==want for path in cached),'Exact archive-cache hash/length mismatch: '+name;shutil.copyfile(cached[0],target)
                else:
                    assert a.allow_download,'Download required but not permitted for this invocation: '+name
                    with urllib.request.urlopen(item['url'],timeout=120) as response,target.open('xb') as output:
                        count=0
                        while block:=response.read(1048576):
                            count+=len(block);assert count<=item['bytes'],'Exact download length exceeded';output.write(block)
                assert pin(target)==want,'Downloaded/copied archive identity differs: '+name;archive_paths[name]=target
            save(out/'archive-pins.json',{name:dict(url=needed[name]['url'],**pin(path)) for name,path in archive_paths.items()});execute('venv',[base,'-I','-B','-m','venv','--copies','--without-pip',prefix],120);python=prefix/'bin/python';bootstrap=out/'bootstrap-pip.py';bootstrap.write_text('import runpy,sys\nwheel=sys.argv.pop(1)\nsys.path.insert(0,wheel)\nrunpy.run_module("pip",run_name="__main__")\n',encoding='utf8');pipwheel=archive_paths[locks['bootstrap'][0]['filename']];execute('pip-bootstrap',[python,'-I','-B',bootstrap,pipwheel,'install','--no-index','--no-compile',pipwheel],120);pip=[python,'-I','-B','-m','pip'];execute('build-dependencies',[*pip,'install','--no-index','--no-deps','--no-compile',*[archive_paths[item['filename']] for item in locks['bootstrap'][1:]]],120);execute('install',[*pip,'install','--no-index','--no-deps','--no-build-isolation','--no-compile','--find-links',archives,'--report',out/'pip-report.json',*[archive_paths[item['filename']] for item in locks['editions'][a.edition]['packages']]],1800);execute('pip-check',[*pip,'check'],120)
        else:assert prefix.resolve()==prefix and prefix.is_dir(),'CAPTURE requires an existing canonical permitted prefix'
        python=prefix/'bin/python';assert python.is_file();env['PATH']=str(prefix/'bin')+os.pathsep+env.get('PATH',os.defpath);site=prefix/('lib/python'+profile['python_minor']+'/site-packages')
        for name,want in profile['wheel_members'].items():assert pin(site/name)==want,'Original source/backend wheel member differs or is missing: '+name
        save(out/'original-installed-wheel-files.json',profile['wheel_members']);before=dict(prefix=inventory(prefix,progress=out/'prefix-before.progress.json'),java=inventory(java,progress=out/'java-before.progress.json'));save(out/'runtime-before.json',before);health=out/'health.py';health.write_text('''import importlib,importlib.metadata as metadata,importlib.resources,json,pathlib,sys
import hail as hl
home=pathlib.Path(sys.argv[1]);profile=json.loads(pathlib.Path(sys.argv[2]).read_bytes())['editions'][sys.argv[3]]
assert hl.version()==profile['hail_version']
revision=importlib.import_module('hail.version').__revision__ if sys.argv[3]=='0.2.139' else importlib.resources.files('hail').joinpath('hail_revision').read_text().strip()
assert revision==profile['revision'] and '.'.join(map(str,sys.version_info[:2]))==profile['python_minor']
assert pathlib.Path(hl.__file__).resolve().is_relative_to(pathlib.Path(sys.prefix))
try:
 hl.init(backend='spark',master='local[2]',log=str(home/'original-hail.log'),tmp_dir=str(home/'hail-temp'),local_tmpdir=str(home/'hail-local-temp'),spark_conf={'spark.driver.memory':'2g','spark.executor.memory':'2g','spark.driver.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.executor.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.ui.enabled':'false','spark.ui.showConsoleProgress':'false'})
 assert hl.spark_context().version==profile['spark'] and hl.eval(hl.int32(1)+hl.int32(1))==2
 report=dict(status='PASS_ORIGINAL_HAIL_JAVA11_LOCAL_SPARK_HEALTH_ONLY',hail=hl.version(),revision=revision,python=sys.version,spark=hl.spark_context().version,package_path=hl.__file__,packages={d.metadata['Name']:d.version for d in metadata.distributions()},genomic_workflow_executed=False,cloud_batch_submitted=False)
 (home/'HEALTH.json').write_text(json.dumps(report,indent=2)+'\\n')
finally:hl.stop()
''',encoding='utf8',newline='\n');execute('health',[python,'-I','-B','-X','pycache_prefix='+str(out/'private-pycache'),health,out,kit/'profiles.json',a.edition],300);report=json.loads((out/'HEALTH.json').read_bytes());state.update(status='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY',runtime_environment=str(prefix),runtime_python=str(python),java_home=str(java),hail=profile['hail_version'],health=report,original_source=profile['wheel'],original_installed_wheel_files=pin(out/'original-installed-wheel-files.json'),runtime_inventory=pin(out/'runtime-before.json'))
    except Exception as error:state['error']=repr(error);print('SKIP3 dependent Hail analysis skipped:',repr(error),flush=True)
    finally:
        if inventory is not None and before is not None:
            after=dict(prefix=inventory(prefix,progress=out/'prefix-after.progress.json'),java=inventory(java,progress=out/'java-after.progress.json'));save(out/'runtime-after.json',after);state['runtime_trees_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_RUNTIME_TREE_CHANGED',error='Runtime changed during health; preserve complete before/after inventories and keep analysis unadmitted')
        if shared is not None and inventory is not None:
            current=dict(java=inventory(java,progress=out/'java-shared-after.progress.json'))
            if 'base_python' in shared:current['base_python']=dict(path=shared['base_python']['path'],**pin(Path(shared['base_python']['path'])))
            save(out/'shared-before.json',shared);save(out/'shared-after.json',current);state['shared_runtime_unchanged']=shared==current
            if shared!=current:state.update(status='FAIL2_SHARED_RUNTIME_CHANGED',error='A supplied shared Java/base-Python identity changed; no runtime binding admitted')
        state['required_action']=action;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Framework health only; no genomic analysis or cloud submission.')+'\n\nRequired action: '+action+'\n',encoding='utf8',newline='\n')
    print(state['status'],flush=True);return 0 if state['status'].startswith('PASS_') else 2 if state['status'].startswith('FAIL2') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/runtime preparation checks')
    try:raise SystemExit(main())
    except Exception as failure:
        print('SKIP3 HAIL PREPARATION UNAVAILABLE: '+repr(failure)+'\nRequired action: no runtime or analysis is admitted. Preserve partial paths and logs. Provide writable NEW evidence paths, an allowed compatible runtime and any required explicit installation/download permission flags; otherwise keep dependent analysis skipped.',file=sys.stderr);raise SystemExit(3)
