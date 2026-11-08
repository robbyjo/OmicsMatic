# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate an already installed original Hail wheel with Java11 in NEW evidence paths; no installation or runtime repair."""
import argparse,hashlib,json,os,re,signal,subprocess,sys,time,zipfile
from pathlib import Path
from setup_hail_reference_linux import ROOT,pin,save,group_members
from inventory_hail_runtime_linux import inventory

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--edition',choices=['0.2.139','0.2.130'],required=True);parser.add_argument('--prefix',type=Path,required=True);parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();prefix=a.prefix.resolve();java=a.java_home.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists() and prefix.is_relative_to(ROOT/'build') and java.is_relative_to(ROOT/'build') and not out.is_relative_to(prefix) and not out.is_relative_to(java);out.mkdir(parents=True);(out/'binder-at-attempt.py').write_bytes(Path(__file__).read_bytes());utility=ROOT/'tools/literature/inventory_hail_runtime_linux.py';(out/'inventory-at-attempt.py').write_bytes(utility.read_bytes());state=dict(status='SKIP3_HAIL_WORKFLOW_RUNTIME_UNAVAILABLE',edition=a.edition,implementation_credit=False,whole_goal_complete=False,cloud_batch_submitted=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__)),utility.relative_to(ROOT).as_posix():pin(utility),(ROOT/'tools/literature/setup_hail_reference_linux.py').relative_to(ROOT).as_posix():pin(ROOT/'tools/literature/setup_hail_reference_linux.py')});before=None;child=None;start=time.monotonic();process={};error=None
    try:
        assert sys.platform=='linux' and os.uname().machine=='x86_64';version='3.12' if a.edition=='0.2.139' else '3.10';spark='3.5.3' if a.edition=='0.2.139' else '3.3.2';revision='5e5eedb87ff814916ef08037199fec7b158f999b' if a.edition=='0.2.139' else 'bea04d9c79b5ca739364e8c121132845475f617a';expected=a.edition+'-'+revision[:12];python=prefix/'bin/python';assert python.is_file() and (java/'bin/java').is_file();before=dict(prefix=inventory(prefix,progress=out/'prefix-before.progress.json'),java=inventory(java,progress=out/'java-before.progress.json'));save(out/'runtime-before.json',before);intake=ROOT/'build/literature-review/hail-runtime/source-intake-2';source=json.loads((intake/'COMPLETED.json').read_bytes())['versions'][a.edition];wheel=ROOT/source['wheel']['file'];assert pin(wheel)=={k:source['wheel'][k] for k in ['bytes','sha256']};site=prefix/('lib/python'+version+'/site-packages');members={}
        with zipfile.ZipFile(wheel) as original:
            for item in original.infolist():
                if item.is_dir() or item.filename.endswith('.dist-info/RECORD'):continue
                h=hashlib.sha256();size=0
                with original.open(item) as stream:
                    while block:=stream.read(1048576):size+=len(block);h.update(block)
                want=dict(bytes=size,sha256=h.hexdigest());assert pin(site/item.filename)==want,'Original installed wheel member differs: '+item.filename;members[item.filename]=want
        save(out/'original-installed-wheel-files.json',members);health=out/'health.py';health.write_text('''import importlib,importlib.metadata as metadata,importlib.resources,json,pathlib,sys
import hail as hl
home=pathlib.Path(sys.argv[1]);expected=sys.argv[2];spark=sys.argv[3];python=sys.argv[4]
assert hl.version()==expected,(hl.version(),expected)
revision=importlib.import_module('hail.version').__revision__ if expected.startswith('0.2.139-') else importlib.resources.files('hail').joinpath('hail_revision').read_text().strip()
assert revision==sys.argv[5]
assert '.'.join(map(str,sys.version_info[:2]))==python
assert pathlib.Path(hl.__file__).resolve().is_relative_to(pathlib.Path(sys.prefix))
try:
 hl.init(backend='spark',master='local[2]',log=str(home/'original-hail.log'),tmp_dir=str(home/'hail-temp'),local_tmpdir=str(home/'hail-local-temp'),spark_conf={'spark.driver.memory':'2g','spark.executor.memory':'2g','spark.driver.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.executor.extraJavaOptions':'-XX:ActiveProcessorCount=2','spark.ui.enabled':'false','spark.ui.showConsoleProgress':'false'})
 assert hl.spark_context().version==spark
 assert hl.eval(hl.int32(1)+hl.int32(1))==2
 report=dict(status='PASS_ORIGINAL_HAIL_JAVA11_LOCAL_SPARK_HEALTH_ONLY',hail=hl.version(),revision=revision,python=sys.version,spark=spark,package_path=hl.__file__,packages={d.metadata['Name']:d.version for d in metadata.distributions()},genomic_workflow_executed=False,cloud_batch_submitted=False)
 (home/'HEALTH.json').write_text(json.dumps(report,indent=2)+'\\n')
finally:hl.stop()
''',encoding='utf8',newline='\n');env=dict(os.environ)
        for key in list(env):
            if key.startswith(('HAIL_','SPARK_','PYSPARK_','PYTHON','PIP_')) or key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','HADOOP_CONF_DIR','YARN_CONF_DIR']:env.pop(key,None)
        for name in ['private-temp','private-config','private-cache']:(out/name).mkdir()
        env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',JAVA_HOME=str(java),SPARK_LOCAL_IP='127.0.0.1',TMPDIR=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1');jvm=subprocess.run([str(java/'bin/java'),'-version'],env=env,capture_output=True,timeout=60);(out/'java.stdout').write_bytes(jvm.stdout);(out/'java.stderr').write_bytes(jvm.stderr);assert jvm.returncode==0 and re.search(r'version "11(?:\.|\")',(jvm.stdout+jvm.stderr).decode());command=[str(python),'-I','-B','-X','pycache_prefix='+str(out/'private-pycache'),str(health),str(out),expected,spark,version,revision];process=dict(command=command,started_at=time.time())
        with (out/'health.stdout').open('xb') as stdout,(out/'health.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);process['owned_process_group']=child.pid;save(out/'health.STARTED.json',process)
            health_started=time.monotonic()
            while child.poll() is None:
                if time.monotonic()-health_started>300:raise TimeoutError('Bounded original framework health exceeded timeout')
                time.sleep(.1)
            process['exit_code']=child.returncode;assert child.returncode==0,'Original framework health failed; inspect health.stderr/stdout'
        report=json.loads((out/'HEALTH.json').read_bytes());assert report['status']=='PASS_ORIGINAL_HAIL_JAVA11_LOCAL_SPARK_HEALTH_ONLY';state.update(status='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY',runtime_environment=str(prefix),runtime_python=str(python),java_home=str(java),hail=expected,health=report,original_source=source['wheel'],original_installed_wheel_files=pin(out/'original-installed-wheel-files.json'),runtime_inventory=pin(out/'runtime-before.json'))
    except Exception as failure:error=failure;state['error']=repr(failure);print('SKIP3 dependent original Hail analysis:',repr(failure),flush=True)
    finally:
        if child is not None:
            process['owned_before_cleanup']=group_members(child.pid)
            for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                if not group_members(child.pid):break
                try:os.killpg(child.pid,sig)
                except ProcessLookupError:pass
                deadline=time.monotonic()+seconds
                while any(v['state']!='Z' for v in group_members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
            child.wait(timeout=10);process['owned_after_cleanup']=group_members(child.pid);process['owned_live_after_cleanup']=[v for v in process['owned_after_cleanup'] if v['state']!='Z']
            if process['owned_live_after_cleanup']:state.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error='Owned workers remain alive')
            process['elapsed_seconds']=time.monotonic()-start;save(out/'health.process.json',process)
        if before is not None:
            after=dict(prefix=inventory(prefix,progress=out/'prefix-after.progress.json'),java=inventory(java,progress=out/'java-after.progress.json'));save(out/'runtime-after.json',after);state['runtime_trees_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_RUNTIME_TREE_CHANGED',error='No runtime mutation may be admitted; inspect complete before/after inventory')
        state['required_action']='If this binding fails, skip dependent analysis. Preserve installed environments and prior attempts. Diagnose the named version/source/JVM/library/capacity mismatch; provide the exact permitted runtime and repeat binding in NEW output paths. This helper installs nothing and grants no full Hail, cloud-scale or paper-reproduction credit.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Framework expression health only; no workflow admission.')+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8',newline='\n')
    print(state['status'],flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original wheel/runtime binding checks')
    raise SystemExit(main())
