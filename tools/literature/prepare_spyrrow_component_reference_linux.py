# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install two exact private component imports over an unchanged permitted Python runtime. No original databases/pickles or full package import."""
import argparse,hashlib,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--allow-install',action='store_true');args=parser.parse_args();out=args.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/spyrrow-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_SPYRROW_COMPONENT_REFERENCE_PREPARATION',implementation_credit=False,full_spyrrow_package_imported=False,whole_goal_complete=False,processes=[]);before=None;prefix=None
    def execute(name,command,timeout=180):
        child=None;record=dict(command=list(map(str,command)));start=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                child=subprocess.Popen(record['command'],cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);save(out/(name+'.STARTED.json'),dict(pid=child.pid,owned_process_group=child.pid,command=record['command']));code=child.wait(timeout=timeout)
            record['exit_code']=code;assert code==0,name+' failed; read full stdout/stderr'
        finally:
            if child is not None and child.poll() is None:
                os.killpg(child.pid,signal.SIGTERM)
                try:child.wait(timeout=10)
                except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=10)
            record.update(elapsed_seconds=time.monotonic()-start,owned_root_alive=child is not None and child.poll() is None);save(out/(name+'.process.json'),record);state['processes'].append(record)
    try:
        if not args.allow_install:state.update(status='SKIP3_SPYRROW_COMPONENT_INSTALL_NOT_PERMITTED',error='Installation flag absent; skip dependent component validation and use NEW permitted paths');return_code=3
        else:
            setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';binding=json.loads((setup/'COMPLETED.json').read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY';prefix=Path(binding['runtime_environment']);before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes());downloads=ROOT/'build/literature-review/spyrrow-runtime/component-overlay-download-1';locked=json.loads((downloads/'COMPLETED.json').read_bytes());assert locked['status']=='PASS_EXACT_TWO_COMPONENT_DEPENDENCY_WHEEL_DOWNLOADS_ONLY';archives=[]
            for item in locked['archives']:
                path=downloads/item['file'];assert pin(path)=={k:item[k] for k in ['bytes','sha256']};archives.append(path)
            env=dict(os.environ)
            for key in list(env):
                if key.startswith(('PYTHON','PIP_','CONDA_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
            for name in ['private-home','private-temp','private-cache','private-config']:(out/name).mkdir()
            env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),PIP_CONFIG_FILE='/dev/null',PIP_NO_CACHE_DIR='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',MPLBACKEND='Agg',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1');overlay=out/'site';execute('offline-two-wheel-install',[prefix/'bin/python','-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',overlay,*archives]);health=out/'health.py';health.write_text('import sys,json,importlib.util,importlib.metadata as m;from pathlib import Path\nsys.path.insert(0,'+repr(str(overlay))+')\nimport cv2,shapely,numpy,pandas,scipy,matplotlib,networkx\nassert m.version("opencv-python-headless")=="4.10.0.84" and shapely.__version__=="2.0.6" and numpy.__version__=="1.26.4" and pandas.__version__=="2.2.3"\nsource=Path('+repr(str(ROOT/'build/literature-review/spyrrow-runtime/source-discovery-1/source/spyrrow/commu/basic.py'))+')\nspec=importlib.util.spec_from_file_location("spyrrow.commu.basic",source);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)\nassert module.cal_cell_communication.__module__=="spyrrow.commu.basic"\nprint(json.dumps(dict(status="PASS_UNCHANGED_ORIGINAL_BASIC_MODULE_IMPORT_ONLY",module=str(source),versions={n:m.version(n) for n in ["opencv-python-headless","shapely","numpy","pandas","scipy","matplotlib","networkx"]},full_package_imported=False,pickle_file_loaded=False)))\n',encoding='utf8',newline='\n');execute('original-basic-import-health',[prefix/'bin/python','-I','-B',health]);health_report=json.loads((out/'original-basic-import-health.stdout').read_bytes());overlay_tree=inventory(overlay,progress=out/'overlay.progress.json');save(out/'overlay.json',overlay_tree);state.update(status='PASS_SPYRROW_ORIGINAL_BASIC_COMPONENT_REFERENCE_ONLY',python=str(prefix/'bin/python'),shared_prefix=str(prefix),overlay=str(overlay),overlay_inventory=pin(out/'overlay.json'),archives=locked['archives'],health=health_report,pickle_file_loaded=False,namespace_functions_patched=False);return_code=0
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');return_code=2
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['shared_runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_SPYRROW_SHARED_RUNTIME_CHANGED',error='Shared original runtime changed');return_code=2
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nComponent reference only. No full Spyrrow package/cn.call, database pickle or paper analysis is run/admitted. The shared runtime stays unchanged; the two imports are installed only in the new private overlay. Read original source and full install/import diagnostics. SKIP3 skips dependent validation when installation is forbidden/unavailable. FAIL2 admits no component reference; preserve all files, resolve the named dependency/path/source error and retry NEW paths.\n',encoding='utf8')
    print(state['status'],state.get('error',''));return return_code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact component/runtime checks')
    raise SystemExit(main())
