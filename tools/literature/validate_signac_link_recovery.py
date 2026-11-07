# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check copied LinkPeaks audits, altered evidence, explicit skips and actual owned R process cleanup."""
import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacLinksRequestCli'
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def inventory(path):return {p.relative_to(path).as_posix():pin(p) for p in path.rglob('*') if p.is_file()}
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='LinkPeaks-corruption-and-owned-process-checks-in-progress'))
    try:
        build=read(BASE/'links-workflow-build-3/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};main=BASE/'links-workflow-2';original=main/'Unicode β analyses/00-1.17.1-package-pearson-6-0.05';request=read(main/'request-00.json');assert (original/'result-links.tsv').is_file() and read(original/'analysis-status.json')['status']=='completed-with-warnings'
        sources={'tools/literature/validate_signac_link_recovery.py':pin(Path(__file__))};save(out/'SOURCE-PINS.json',sources);snapshot=out/'source-snapshot/tools/literature';snapshot.mkdir(parents=True);shutil.copyfile(Path(__file__),snapshot/Path(__file__).name)
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0,timeout=120):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1200:]);return r
        def launch(name,value,code=0):
            path=out/(name+'-request.json');save(path,value);command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],code)
        def verify(name,path,code=0):
            for platform,java,convert in platforms:
                p=out/(name+'-'+platform+'.json');save(p,dict(schema_version=1,mode='VERIFY',analysis=convert(path)));command(name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(p)],code)
        baseline=out/'copied-baseline';shutil.copytree(original,baseline);verify('copied-baseline',baseline)
        mutations=[('published-score','result-links.tsv'),('original-score','worker/links.tsv'),('background-coefficient','worker/background.tsv'),('background-identity','worker/background.tsv'),('pool-order','worker/pools.tsv'),('retained-decision','worker/pairs.tsv'),('options','request.json'),('source-request','source-request.json'),('manifest','manifest.json'),('worker-parameters','worker/parameters.json'),('effective-method','worker/effective-settings.tsv'),('rng-replay','worker/rng-audit-after.tsv'),('literal-source','worker/source/signac-links/1.14.0/LinkPeaks.R'),('warnings','WARNINGS.md'),('diagnostics','diagnostics.json'),('captured-counts',read(original/'request.json')['counts']),('exchange-counts','exchange/peaks.mtx'),('runtime-protocol','worker/worker-request.txt')];rejections=[]
        def reseal(path):
            target=path/'artifact-checksums.tsv';target.write_text('sha256\tpath\n'+''.join(pin(p)['sha256']+'\t'+p.relative_to(path).as_posix()+'\n' for p in sorted(path.rglob('*')) if p.is_file() and p!=target),encoding='utf8',newline='\n')
        for name,file in mutations:
            target=out/('tamper-'+name);shutil.copytree(baseline,target);path=target/file
            if file.endswith('.json'):
                value=read(path)
                if name=='options':value['distance']+=1
                elif name=='source-request':value['seed']+=1
                elif name=='manifest':value['process_exit_code']=17
                elif name=='worker-parameters':value['seed']+=1
                else:value['tested_pairs']+=1
                save(path,value)
            elif name=='warnings':path.write_text('All results are certified. No action needed.\n',encoding='utf8',newline='\n')
            elif name=='literal-source':path.write_text(path.read_text(encoding='utf8')+'\n# altered source\n',encoding='utf8',newline='\n')
            elif name=='runtime-protocol':
                lines=path.read_text(encoding='utf8').splitlines();lines[2]='altered-parameters.json';path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            else:
                lines=path.read_text(encoding='utf8').splitlines();index=1;delimiter='\t'
                if name in ['captured-counts','exchange-counts']:
                    delimiter=' ';index=next(i+1 for i,v in enumerate(lines) if not v.startswith('%'));fields=lines[index].split();fields[2]=str(float(fields[2])+1)
                    if name=='captured-counts':fields[2]=str(int(float(fields[2])))
                else:
                    fields=lines[index].split('\t')
                    if name in ['published-score','original-score']:fields[5]=str(float(fields[5])+0.01)
                    elif name=='background-coefficient':fields[4]=str(float(fields[4])+0.01)
                    elif name=='background-identity':fields[3]=lines[2].split('\t')[3]
                    elif name=='pool-order':fields[2]=lines[2].split('\t')[2]
                    elif name=='retained-decision':fields[5]='false' if fields[5]=='true' else 'true'
                    elif name=='rng-replay':fields[1]=str(int(fields[1])+1)
                    elif name=='effective-method':index=next(i for i,line in enumerate(lines) if line.startswith('method\t'));fields=['method','spearman']
                lines[index]=delimiter.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            reseal(target);verify('reject-'+name,target,2);rejections.append(dict(mutation=name,rejected_platforms=3));print('rejected altered '+name,flush=True)
        nested=out/'unlisted-nested-file';shutil.copytree(baseline,nested);(nested/'input/artifact-checksums.tsv').write_text('extra\n',encoding='utf8',newline='\n');verify('reject-unlisted-nested-file',nested,2)
        before=inventory(baseline);existing=copy.deepcopy(request);existing['out']=str(baseline);launch('existing-destination',existing,2);assert inventory(baseline)==before
        skips=[]
        for name in ['missing-rscript','unavailable-package']:
            value=copy.deepcopy(request);target=out/name;value['out']=str(target)
            if name=='missing-rscript':value['rscript']=str(out/'Rscript-not-installed.exe')
            else:
                empty=out/'empty-private-library';empty.mkdir();value['r_library']=str(empty)
            launch(name,value,3);assert read(target/'analysis-status.json')['status']=='skipped-dependency-unavailable' and not (target/'result-links.tsv').exists() and (target/'WARNINGS.md').read_text(encoding='utf8').startswith('# SKIP3');verify('reject-skipped-'+name,target,2);skips.append(dict(name=name,exit_code=3,no_result_admitted=True))
        # A package-private Java harness interrupts the actual request thread, invoking owned-process cleanup.
        harness=out/'LinkInterrupt.java';harness.write_text('''package org.jlinalg.cli;import java.nio.file.*;import java.util.concurrent.atomic.AtomicInteger;public final class LinkInterrupt{public static void main(String[] a)throws Exception{AtomicInteger code=new AtomicInteger(-1);Thread worker=new Thread(()->code.set(SignacLinksRequestCli.run(new String[]{"--request",a[0]},System.out,System.err)),"owned-LinkPeaks-request");worker.start();while(worker.isAlive()&&!Files.exists(Path.of(a[1])))Thread.sleep(25);if(worker.isAlive())worker.interrupt();worker.join(20000);if(worker.isAlive()){ProcessHandle.current().descendants().forEach(ProcessHandle::destroyForcibly);throw new AssertionError("Request thread failed to stop");}if(code.get()!=2||!worker.isInterrupted())throw new AssertionError("Lost interruption or wrong exit");System.out.println("OWNED_LINKPEAKS_INTERRUPT_EXIT2_FLAG_PRESERVED");}}
''',encoding='utf8',newline='\n');classes=out/'interrupt-classes';classes.mkdir();command('compile-interrupt',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,harness])
        def tree(pid):
            script=f"$taskParentPid={int(pid)}; $taskAll=Get-CimInstance Win32_Process; $taskKnown=[Collections.Generic.HashSet[uint32]]::new(); [void]$taskKnown.Add([uint32]$taskParentPid); do {{$taskChanged=$false; foreach($taskProcess in $taskAll) {{if($taskKnown.Contains([uint32]$taskProcess.ParentProcessId) -and -not $taskKnown.Contains([uint32]$taskProcess.ProcessId)) {{[void]$taskKnown.Add([uint32]$taskProcess.ProcessId); $taskChanged=$true}}}}}} while($taskChanged); $taskAll | Where-Object {{$taskKnown.Contains([uint32]$_.ProcessId) -and $_.ProcessId -ne $taskParentPid}} | Select-Object ProcessId,ParentProcessId,Name,CreationDate | ConvertTo-Json -Compress"
            return process_query(script)
        def process_query(script):
            r=subprocess.run(['pwsh.exe','-NoLogo','-NoProfile','-Command',script],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=12,check=True);raw=r.stdout.decode('utf-8-sig').strip();data=[] if not raw else json.loads(raw);return data if isinstance(data,list) else [data]
        def surviving_observed(observed):
            # Inspect each captured PID directly after Java exits. A descendant ancestry walk
            # alone can miss an orphan after its Rscript parent has already terminated.
            ids=','.join(str(int(p['ProcessId'])) for p in observed)
            current=process_query(f"$taskObservedIds=@({ids}); Get-CimInstance Win32_Process | Where-Object {{$_.ProcessId -in $taskObservedIds}} | Select-Object ProcessId,ParentProcessId,Name,CreationDate | ConvertTo-Json -Compress")
            return [p for p in current if any(p['ProcessId']==v['ProcessId'] and p['CreationDate']==v['CreationDate'] for v in observed)]
        owned=[]
        for name in ['timeout','interrupt']:
            value=copy.deepcopy(request);target=out/('owned-'+name);value['out']=str(target);value['timeout']=4 if name=='timeout' else 60;path=out/(name+'-request.json');save(path,value);signal=out/'interrupt.signal'
            argv=[str(JAVA21/'java.exe'),'-Xmx768m','-cp',str(jar),CLI,'--request',str(path)] if name=='timeout' else [str(JAVA21/'java.exe'),'-Xmx768m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.LinkInterrupt',str(path),str(signal)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                process=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);observed=[];deadline=time.monotonic()+12
                try:
                    while process.poll() is None and time.monotonic()<deadline:
                        observed=[p for p in tree(process.pid) if p['Name'].lower() in ['rscript.exe','rterm.exe','r.exe']]
                        if observed:break
                        time.sleep(0.1)
                    if name=='interrupt':signal.write_text('interrupt only this owned request thread\n',encoding='utf8',newline='\n')
                    exit_code=process.wait(timeout=35)
                except BaseException:
                    if name=='interrupt' and not signal.exists():signal.write_text('interrupt this owned request after verifier failure\n',encoding='utf8',newline='\n')
                    # Do not abandon the actual launched worker when the observer fails.
                    process.wait(timeout=70)
                    raise
            save(out/(name+'.process.json'),dict(command=argv,exit_code=exit_code,observed_owned_R_processes=observed));assert observed and exit_code==(2 if name=='timeout' else 0),(name,exit_code,observed)
            live=surviving_observed(observed);save(out/(name+'-direct-PID-recheck.json'),dict(observed=observed,surviving_same_processes=live));assert not live;assert not (target/'result-links.tsv').exists()
            if target.exists():assert read(target/'analysis-status.json')['status']=='failed'
            if name=='timeout':
                cleanup=read(target/'manifest.json')['owned_process_cleanup'];assert cleanup and all(not p['alive_after_cleanup'] for p in cleanup)
            owned.append(dict(name=name,actual_live_R_process_observed=True,observed=observed,direct_PID_and_creation_time_recheck=True,owned_processes_stopped=True,no_result_admitted=True))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-LinkPeaks-captured-corruption-skips-and-owned-process-cleanup',source_pins=sources,artifact=build['artifact'],copied_verifications=3,rehashed_rejections=rejections,unlisted_nested_file_rejections=3,existing_destination_preserved=True,dependency_skips=skips,owned_process_checks=owned,complete_runtime_DLL_host_closure_certified=False,historical_packages_executed=False,full_Signac_workflow_available=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],rehashed_rejections=len(rejections)*3,dependency_skips=2,actual_owned_process_checks=2)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_LINKPEAKS_RECOVERY_AND_OWNED_PROCESS_CHECKS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original LinkPeaks recovery checks')
    main()
