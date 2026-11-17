#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual public-command overlap, terminal-delivery and late archive-integrity controls."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,sys,time,traceback,zipfile
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from setup_deployment import manager_tree
from qualify_deployment import execute

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','runtime','preinstalled-source','managed-source','native-out','out']:p.add_argument('--'+name,type=Path,required=True)
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=None
    try:
        assert sys.platform=='linux';stage=args.native_out.absolute();assert stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();jar=args.jar.resolve();jar_pin=pin(jar);runtime=args.runtime.resolve();env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC');rows=[]
        source=args.preinstalled_source.resolve();assert json.loads((source/'COMPLETED.json').read_bytes())['public_cli'];caller=Path(json.loads((source/'config-bindings/DEPLOYMENT.json').read_bytes())['native_stage']);assert caller.parent==Path('/tmp') and caller.name.startswith('omicsmatic-snakemake-captured-');recipe=caller/'recipe.json';bundle=caller/'execution/bundle';original={name:manager_tree(caller/name) for name in ['input','data']}
        def command(operation,options):return ['/usr/bin/java','-jar',str(jar),'snakemake-tools',operation,*options]
        def run(name,operation,options,expected=2,full=False):
            case=stage/name;case.mkdir();cmd=command(operation,options)
            if full:
                start=time.monotonic()
                with open('/dev/full','wb',buffering=0) as so,(case/'stderr').open('xb') as se:result=subprocess.run(cmd,env=env,cwd=case,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
                process=dict(command=cmd,exit_code=result.returncode,seconds=round(time.monotonic()-start,3),stdout='/dev/full');write(case/'PROCESS.json',process)
            else:process=execute(case,'execute',cmd,env,case,600)
            assert process['exit_code']==expected,(name,'Inspect retained command diagnostics');rows.append(dict(case=name,**process));return case
        managed=json.loads((args.managed_source.resolve()/'cases/reuse-conda/recipe.json').read_bytes());adopted=json.loads((Path(managed['managed_software']['providers'])/'ADOPTED.json').read_bytes());ready=json.loads((runtime/'READY.json').read_bytes());request=json.loads(recipe.read_bytes())
        protected=[('workflow',Path(request['workflow']['path']),recipe),('data',Path(request['resources']['data']['path']),recipe),('runtime-adoption',runtime,recipe),('runtime-environment',Path(ready['environment']),recipe),('provider-adoption',Path(managed['managed_software']['providers']),args.managed_source/'cases/reuse-conda/recipe.json'),('provider-installation',Path(adopted['components']['conda']['prefix']),args.managed_source/'cases/reuse-conda/recipe.json'),('borrowed-conda',Path(managed['managed_software']['conda_environments']),args.managed_source/'cases/reuse-conda/recipe.json')]
        for label,path,request_path in protected:
            destination=path/'omicsmatic-overlap-public-failure-20261117-1';assert path.is_dir() and not destination.exists();run('overlap-'+label,'run',['--request',str(request_path),'--runtime',str(runtime),'--out',str(destination)]);assert not destination.exists()
        for operation in ['run','verify','replay']:
            target=stage/('stdout-failed-'+operation)
            options=['--request',str(recipe),'--runtime',str(runtime)] if operation=='run' else ['--bundle',str(bundle)]+(['--runtime',str(runtime)] if operation=='replay' else [])
            run('full-device-'+operation,operation,[*options,'--out',str(target)],full=True)
            marker=target if operation=='verify' else target/'bundle';assert (target/'FAILURE.json').is_file() and (marker/'INVALIDATED-COMPLETED.json').is_file() and not (marker/'COMPLETED.json').exists();assert 'FAIL2' in (target/'FAILURE_WARNINGS.md').read_text()
            run('verify-failed-'+operation,'verify',['--bundle',str(target),'--out',str(stage/('rejected-'+operation))])
        case=stage/'late-archive-mutation';case.mkdir();archive=case/'controlled-parent.zip'
        with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as target:
            for path in sorted(bundle.rglob('*')):
                if path.is_file():assert not path.is_symlink();target.write(path,path.relative_to(bundle).as_posix())
        before=pin(archive);target=case/'replay';cmd=command('replay',['--bundle',str(archive),'--runtime',str(runtime),'--out',str(target)]);start=time.monotonic()
        with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:
            process=subprocess.Popen(cmd,cwd=case,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
            try:
                deadline=time.monotonic()+300
                while process.poll() is None and not (target/'execution/COMMAND.json').is_file() and time.monotonic()<deadline:time.sleep(.01)
                assert process.poll() is None and (target/'execution/COMMAND.json').is_file(),'A live actual replay must reach execution before the controlled archive changes'
                write(case/'LIVE-MUTATION.json',dict(pid=process.pid,execution_command=pin(target/'execution/COMMAND.json'),archive_before=before,source_bundle_untouched=True))
                with archive.open('ab') as changed:changed.write(b'X')
                process.wait(timeout=600)
            finally:
                if process.poll() is None:os.killpg(process.pid,15);process.wait(timeout=15)
        row=dict(case='late-archive-mutation',command=cmd,exit_code=process.returncode,seconds=round(time.monotonic()-start,3),archive_before=before,archive_after=pin(archive));write(case/'PROCESS.json',row);assert process.returncode==2 and before!=row['archive_after'];assert (target/'bundle/INVALIDATED-COMPLETED.json').is_file() and (target/'FAILURE.json').is_file() and not (target/'bundle/COMPLETED.json').exists();assert (target/'execution/EXECUTED.json').is_file();rows.append(row)
        run('verify-late-mutated-outer','verify',['--bundle',str(target),'--out',str(stage/'rejected-late-mutation')])
        assert {name:manager_tree(caller/name) for name in ['input','data']}==original and pin(jar)==jar_pin
        result=dict(status='PASS_PUBLIC_OUTPUT_PROTECTION_TERMINAL_DELIVERY_AND_LATE_ARCHIVE_REJECTION',cases=rows,jar=jar_pin,native_stage=str(stage),source_inputs_unchanged=True,failed_scientific_outputs_retained=True,complete_qualification=False,new_catalog_credit=0,whole_goal_complete=False);write(stage/'COMPLETED.json',result);write(out/'COMPLETED.json',result);print('PASS_PUBLIC_FAILURE_CONTROLS '+str(len(rows)),flush=True)
    except BaseException as error:write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),native_stage=str(stage),required_action='Retain all failed and successful controls, original inputs, outputs and diagnostics. Resolve the actual discrepancy and retry NEW paths; no untested terminal-result or archive-integrity claim is admitted.'));raise
if __name__=='__main__':main()
