# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual original-background requests across every retained source population and both profiles."""
import argparse,concurrent.futures,json,os,subprocess
from pathlib import Path
from prepare_chromvar_background_request import prepare,save,ROOT,BASE
from probe_chromvar_background import comparison
from validate_mashr_native import JAVA21,linux,pin
CLI='org.jlinalg.cli.ChromVarBackgroundRequestCli'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-original-background-request-matrix-in-progress'))
    try:
        build=json.loads((BASE/'background-workflow-build-2/BUILD.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        names=['tools/literature/validate_chromvar_background_workflow.py','tools/literature/prepare_chromvar_background_request.py','tools/literature/probe_chromvar_background.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        data=json.loads((BASE/'background-original-controls-1/cases.json').read_bytes());original=json.loads((BASE/'background-original-controls-1/original.json').read_bytes())['results'];specs=[]
        for case in data['cases']:
            for profile in ['1.16.0-source','1.34.1-package']:specs.append((case,profile))
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(folder,name,argv,expected):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(folder/(name+'.stdout')).write_bytes(r.stdout);(folder/(name+'.stderr')).write_bytes(r.stderr);save(folder/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==expected,(folder.name,name,r.returncode,r.stderr.decode('utf8',errors='replace')[-700:])
        runs=out/'runs';runs.mkdir()
        def test(spec):
            case,profile=spec;run=runs/(case['id']+'--'+profile);run.mkdir();analysis=run/'Unicode β analysis';request,peaks,cells=prepare(run/'inputs',analysis,data['datasets'][case['dataset']],case,profile);request['timeout']=90;save(run/'inputs/request.json',request);input_pins={p.relative_to(run).as_posix():pin(p) for p in (run/'inputs').rglob('*') if p.is_file()};reference=next(v for v in original if v['id']==case['id'] and v['profile']==profile);expected=0 if reference['ok'] else 2;command(run,'BACKGROUND',[JAVA21/'java.exe','-Xmx768m','-XX:ActiveProcessorCount=2','-cp',jar,CLI,'--request',run/'inputs/request.json'],expected);manifest=json.loads((analysis/'manifest.json').read_bytes());status=json.loads((analysis/'analysis-status.json').read_bytes());assert manifest['results_admitted']==reference['ok']==status['results_admitted']
            if reference['ok']:
                comparison(analysis,reference);assert manifest['process_exit_code']==0 and (analysis/'background.tsv').read_text(encoding='utf8').splitlines()[1].split('\t')[0]==peaks[0]
            else:
                assert not any((analysis/n).exists() for n in ['background.tsv','bias.tsv','diagnostics.json','prepared-inputs.json','verify-request.json']);assert reference['error'] in manifest['error'],(case['id'],reference['error'],manifest['error'])
            save(run/'verify.json',dict(schema_version=1,mode='VERIFY',analysis=analysis.name))
            for name,java,convert in platforms:command(run,name,[*java,'-Xmx768m','-XX:ActiveProcessorCount=2','-cp',convert(jar),CLI,'--request',convert(run/'verify.json')],0 if reference['ok'] else 2)
            assert all(pin(run/n)==v for n,v in input_pins.items());return dict(case=case['id'],profile=profile,analysis_path=analysis.relative_to(ROOT).as_posix(),original_background_indices_and_RNG_match=reference['ok'],no_result_admitted=not reference['ok'],captured_verifications=3 if reference['ok'] else 0,failed_capture_rejections=0 if reference['ok'] else 3,inputs_and_request_unchanged=True)
        results=[]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures=[pool.submit(test,spec) for spec in specs]
            try:
                for future in concurrent.futures.as_completed(futures):
                    results.append(future.result());save(out/'PROGRESS.json',dict(completed=len(results),total=len(specs),runs=results))
                    if len(results)%10==0:print(json.dumps(dict(completed=len(results),total=len(specs))),flush=True)
            except BaseException:
                for future in futures:future.cancel()
                raise
        successful=[v for v in results if v['original_background_indices_and_RNG_match']];failed=[v for v in results if v['no_result_admitted']];assert (len(successful),len(failed))==(18,12) and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        save(out/'COMPLETED.json',dict(status='passed-actual-original-background-request-matrix',source_pins=sources,artifact=build['artifact'],original_background_and_RNG_comparisons=sorted(successful,key=lambda v:(v['case'],v['profile'])),retained_source_failures=sorted(failed,key=lambda v:(v['case'],v['profile'])),captured_verifications=54,failed_capture_rejections=36,inputs_and_requests_unchanged=True,Unicode_paths_and_IDs=True,independent_native_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-actual-original-background-request-matrix',original_comparisons=18,retained_source_failures=12,captured_verifications=54,failed_capture_rejections=36)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ACTUAL_ORIGINAL_BACKGROUND_REQUEST_MATRIX',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original background matrix checks')
    main()
