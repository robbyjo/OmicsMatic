#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Qualify real captured-profile parsing and re-adopt an existing unchanged engine; install nothing."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,tempfile,time,traceback,uuid,zipfile
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]

def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def process(command,where,env):
    start=time.monotonic()
    with (where/'stdout').open('xb') as so,(where/'stderr').open('xb') as se:
        p=subprocess.Popen(command,cwd=where,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
        try:p.wait(timeout=180)
        except BaseException:
            import signal
            try:os.killpg(p.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            p.wait();raise
    row=dict(command=command,exit_code=p.returncode,seconds=round(time.monotonic()-start,3));write(where/'PROCESS.json',row);return row

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--environment',type=Path,required=True);p.add_argument('--wheel',type=Path,required=True);p.add_argument('--lock',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualify_policy.py');stage=None
    try:
        assert sys.platform=='linux';python=a.environment.resolve()/'bin/python';assert python.is_file();stage=Path(tempfile.mkdtemp(prefix='omicsmatic-snakemake-policy-',dir='/tmp'))
        source=stage/'src/main/resources/org/jlinalg/omics/snakemake-original-v1';source.parent.mkdir(parents=True);shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1',source)
        scripts=stage/'tools/literature/snakemake_original';scripts.mkdir(parents=True)
        for name in ['prototype_driver.py','adopt_runtime.py']:shutil.copyfile(Path(__file__).with_name(name),scripts/name)
        (stage/'build').mkdir();sys.path.insert(0,str(scripts));from prototype_driver import execution_policy
        sources={path.relative_to(stage).as_posix():pin(path) for path in stage.rglob('*') if path.is_file()};write(out/'SOURCE.json',dict(native_stage=str(stage),sources=sources,engine_environment=str(a.environment.resolve()),installs_software=False))
        env=dict(PATH=str(python.parent)+':/usr/bin:/bin',HOME=str(stage),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',XDG_CACHE_HOME=str(stage/'cache'))
        cases={'plain':None,'resource-profile':'set-threads:\n  make: 1\nprintshellcmds: true\n','explicit-cli-precedence':'cores: 99\nscheduler: ilp\n',
               'conda':'use-conda: true\n','deployment-method':'software-deployment-method: [conda]\n','apptainer':'use-apptainer: true\n','envmodules':'use-envmodules: true\n','dry-run':'dry-run: true\n','touch':'touch: true\n','delete-output':'delete-all-output: true\n','cleanup-shadow':'cleanup-shadow: true\n','summary':'summary: true\n','report':'report: report.html\n','until':'until: [make]\n','omit-from':'omit-from: [make]\n','allowed-rules':'allowed-rules: [make]\n','batch':'batch: make=1/2\n','cache':'cache: [make]\n','subprocess-mode':'mode: subprocess\n'}
        rows=[]
        for name,profile in cases.items():
            case=stage/'build'/name;case.mkdir();work=case/'working';work.mkdir();events=case/'events';events.mkdir()
            (work/'Snakefile').write_text('from pathlib import Path\nPath("source-loaded").write_text("ORIGINAL_SOURCE_LOADED\\n")\nrule make:\n    output: "result.txt"\n    threads: 2\n    conda: "not-installed.yaml"\n    run:\n        Path(output[0]).write_text("threads="+str(threads)+"\\n")\n',encoding='utf-8')
            (work/'not-installed.yaml').write_text('channels: [conda-forge]\ndependencies: [python]\n',encoding='utf-8')
            if profile is not None:
                folder=work/'profiles/default';folder.mkdir(parents=True);(folder/'config.yaml').write_text(profile,encoding='utf-8')
            helpers=case/'helpers';shutil.copytree(source,helpers)
            argv=['--snakefile',str(work/'Snakefile'),'--directory',str(work),'--executor','local','--cores','2','--scheduler','greedy','--forceall','--','result.txt']
            request=dict(schema_version=2,run_id=str(uuid.uuid4()),events_directory=str(events),receipt=str(case/'DRIVER.json'),argv=argv,policy=execution_policy(work,['result.txt']));write(case/'COMMAND.json',request)
            result=process([str(python),'-I','-B',str(helpers/'driver.py'),'--command',str(case/'COMMAND.json')],case,env);receipt=json.loads((case/'DRIVER.json').read_bytes());expected=name in ['plain','resource-profile','explicit-cli-precedence']
            assert receipt['exit_code']==result['exit_code'] and receipt['helpers_before']==receipt['helpers_after'] and not receipt['collector_errors']
            assert receipt['upstream_api_called']==expected
            if expected:
                assert result['exit_code']==0 and receipt['policy_status']=='PASS_EFFECTIVE_ORIGINAL_PARSER_POLICY',(name,(case/'stderr').read_text())
                assert (work/'result.txt').read_text()==('threads=1\n' if name=='resource-profile' else 'threads=2\n') and (work/'source-loaded').is_file()
                if name=='resource-profile':assert any(key.startswith('config_file') for key in receipt['option_sources'])
                if name=='explicit-cli-precedence':assert receipt['effective_policy']['cores']==2 and receipt['effective_policy']['scheduler']=='greedy'
            else:
                assert result['exit_code']==3 and receipt['policy_status']=='SKIP3_EFFECTIVE_ORIGINAL_PARSER_POLICY',(name,(case/'stderr').read_text())
                assert not (work/'source-loaded').exists() and not (work/'result.txt').exists() and not list(events.iterdir())
            rows.append(dict(case=name,expected_original_execution=expected,process=result,policy_status=receipt['policy_status']));print(name+' '+receipt['policy_status'],flush=True)
        fixtures=stage/'control-source';fixtures.mkdir();assets=Path(__file__).with_name('runtime');declared=json.loads((assets/'SOURCE.json').read_bytes())
        with zipfile.ZipFile(assets/'setup-controls.zip') as z:
            assert set(z.namelist())==set(declared['fixture_files'])
            for name in z.namelist():
                raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==declared['fixture_files'][name];target=fixtures/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        control=stage/'driver-controls-process';control.mkdir();controls=stage/'build/driver-controls'
        result=process([str(python),'-I','-B',str(scripts/'prototype_driver.py'),'--original-controls',str(fixtures),'--out',str(controls)],control,env);assert result['exit_code']==0,'Inspect new driver controls'
        adoption_process=stage/'adoption-process';adoption_process.mkdir();adoption=out/'adoption'
        result=process([str(python),'-I','-B',str(scripts/'adopt_runtime.py'),'--environment',str(a.environment.resolve()),'--pixi-lock',str(a.lock.resolve()),'--wheel',str(a.wheel.resolve()),'--driver-controls',str(controls),'--out',str(adoption)],adoption_process,env);assert result['exit_code']==0,'Inspect fresh adoption diagnostics'
        assert all(pin(stage/name)==expected for name,expected in sources.items()),'Do not mutate as-run sources'
        shutil.copytree(stage,out/'records',symlinks=True)
        write(out/'COMPLETED.json',dict(status='PASS_REAL_PROFILE_POLICY_AND_FRESH_ADOPTION_WITHOUT_INSTALL',cases=rows,source_driver_controls=pin(controls/'COMPLETED.json'),adoption=pin(adoption/'READY.json'),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_REAL_PROFILE_POLICY_AND_FRESH_ADOPTION_WITHOUT_INSTALL',flush=True)
    except BaseException as error:
        if stage is not None and not (out/'records').exists():shutil.copytree(stage,out/'records',symlinks=True)
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain failed controls, source snapshots and old runtime receipts. Resolve the named cause and retry NEW output; never refresh historical helper hashes.'));raise
if __name__=='__main__':main()
