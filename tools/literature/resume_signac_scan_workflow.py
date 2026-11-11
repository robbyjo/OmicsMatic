# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Continue an interrupted matrix, preserving completed original fits and freshly verifying all captures."""
import argparse,json,os,subprocess
from pathlib import Path
from validate_signac_scan_workflow import ROOT,BASE,CLI,read,save,compare_original
from prepare_signac_scan_request import prepare
from validate_mashr_native import JAVA21,linux,pin

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--previous',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();prior=a.previous.resolve()
    assert out.is_relative_to(BASE.resolve()) and prior.is_relative_to(BASE.resolve()) and not out.exists() and prior.is_dir() and not (prior/'COMPLETED.json').exists()
    # An absent session handle alone does not prove the external worker ended.
    query="Get-CimInstance Win32_Process -Filter \"Name = 'python.exe' OR Name = 'java.exe' OR Name = 'Rscript.exe' OR Name = 'Rterm.exe'\" | Where-Object { $_.ProcessId -ne "+str(os.getpid())+" -and ($_.CommandLine -like '*scan-workflow-matrix-1*' -or $_.CommandLine -like '*validate_signac_scan_workflow.py*') } | Select-Object ProcessId,ParentProcessId,Name,CreationDate | ConvertTo-Json -Compress"
    check=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',query],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30,check=False)
    assert check.returncode==0 and not check.stdout.strip(),'An earlier matrix process is still live; do not start a second worker'
    out.mkdir();save(out/'INCOMPLETE.json',dict(status='resumed-scan-matrix-in-progress'));(out/'prior-owned-process-check.stdout').write_bytes(check.stdout);(out/'prior-owned-process-check.stderr').write_bytes(check.stderr)
    try:
        old_sources=read(prior/'SOURCE-PINS.json');assert all(pin(ROOT/n)==v and pin(prior/'source-snapshot'/n)==v for n,v in old_sources.items())
        sources=dict(old_sources);sources['tools/literature/resume_signac_scan_workflow.py']=pin(ROOT/'tools/literature/resume_signac_scan_workflow.py');save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        save(prior/'INTERRUPTED.json',dict(status='interrupted-by-user-pause-no-owned-worker-live-at-resume',continuation=out.relative_to(ROOT).as_posix(),prior_INCOMPLETE_preserved=True))
        build=read(BASE/'scan-workflow-build-2/BUILD.json');jar=ROOT/build['artifact']['path'];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        data=read(BASE/'scan-original-7/cases.json');original=read(BASE/'scan-original-7/original.json');inputs=out/'Unicode α inputs';inputs.mkdir();analyses=out/'Unicode β analyses';analyses.mkdir();runs=[];failures=[];reused=0
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1800:])
        for index,record in enumerate(original['results']):
            case=next(c for c in data['cases'] if c['id']==record['id']);name=f"{index:03d}-{record['profile']}-{record['id']}";expected=record['result']['value'];failed='error' in expected;old_receipt=prior/(name+'.process.json')
            target=analyses/name;request=prepare(data,case,record['profile'],inputs/name,target);input_pins={p.name:pin(p) for p in (inputs/name).iterdir()}
            if old_receipt.exists():
                old_path=prior/(f'request-{index:03d}.json');old_request=read(old_path);target=Path(old_request['out']);old_inputs=prior/'Unicode α inputs'/name
                assert {p.name:pin(p) for p in old_inputs.iterdir()}=={p.name:pin(p) for p in (inputs/name).iterdir()}
                reconstructed={k:str(v).replace(str(inputs/name),str(old_inputs)) if isinstance(v,str) else v for k,v in request.items()};reconstructed['out']=str(target);assert reconstructed==old_request
                receipt=read(old_receipt);assert receipt==dict(command=list(map(str,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',old_path])),exit_code=2 if failed else 0)
                save(out/(name+'.reused-process-receipt.json'),dict(previous=old_receipt.relative_to(ROOT).as_posix(),receipt=pin(old_receipt),source_request=pin(old_path),analysis_reexecuted=False));reused+=not failed
            else:
                path=out/(f'request-{index:03d}.json');save(path,request);command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],2 if failed else 0)
            if failed:
                assert not (target/'enrichment.tsv').exists() and not (target/'motif-presence.mtx').exists()
                if target.exists():assert read(target/'analysis-status.json')['status']=='failed' and 'FAIL2' in (target/'WARNINGS.md').read_text(encoding='utf8')
                failures.append(dict(case=record['id'],profile=record['profile'],original_error=expected['error'],analysis_directory_created=target.exists(),no_result_admitted=True))
            else:
                assert read(target/'analysis-status.json')['status']=='completed-with-warnings';compare_original(target,expected,record['profile']=='1.6.0-source')
                for platform,java,convert in platforms:
                    path=out/(f'verify-{index:03d}-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-verify-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)])
                runs.append(dict(case=record['id'],profile=record['profile'],analysis_path=target.relative_to(ROOT).as_posix(),actual_original_R_fit=True,original_enrichment_and_RNG_states_match=True,captured_verifications=3,reused_completed_fit=old_receipt.exists()))
            assert input_pins=={p.name:pin(p) for p in (inputs/name).iterdir()}
            if old_receipt.exists():assert input_pins=={p.name:pin(p) for p in old_inputs.iterdir()}
            print(('retained failure ' if failed else 'verified reused fit ' if old_receipt.exists() else 'passed fresh fit ')+name,flush=True)
        assert len(runs)==75 and len(failures)==21 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        result=dict(status='passed-full-original-scan-enrichment-user-workflow-matrix',source_pins=sources,artifact=build['artifact'],actual_original_fits=runs,retained_original_failure_gates=failures,captured_verifications=225,source_inputs_unchanged=True,previous_interrupted_attempt=prior.relative_to(ROOT).as_posix(),completed_fits_reused_without_reexecution=reused,new_original_fits=75-reused,historical_whole_packages_executed=False,motifmatchr_profile='1.34.0-package',independent_MOODS_KDE_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],reused=reused,new_fits=75-reused,captured_verifications=225)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_RESUMED_SCAN_MATRIX',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable matrix checks')
    main()
