# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check rehashed scan evidence, explicit runtime skips and actual owned-worker timeout/interruption."""
import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
import time
from validate_mashr_native import JAVA21,linux,pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacScanRequestCli'

def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def inventory(path):return {p.relative_to(path).as_posix():pin(p) for p in path.rglob('*') if p.is_file()}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='scan-workflow-build-3');p.add_argument('--baseline',type=Path,default=BASE/'scan-request-probe-3/Unicode β analysis');p.add_argument('--source-request',type=Path,default=BASE/'scan-request-probe-3/request.json');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='scan-captured-corruption-skips-owned-process-checks-in-progress'))
    try:
        build=read(BASE/args.build/'BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};original=args.baseline.resolve();assert original.is_relative_to(BASE.resolve());assert read(original/'analysis-status.json')['status']=='completed-with-warnings';original_request=read(args.source_request.resolve());sources={'tools/literature/validate_signac_scan_pfm_recovery.py':pin(Path(__file__))};save(out/'SOURCE-PINS.json',sources);snapshot=out/'source-snapshot/tools/literature';snapshot.mkdir(parents=True);shutil.copyfile(Path(__file__),snapshot/Path(__file__).name)
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1800:])
        def launch(name,value,code):
            path=out/(name+'-request.json');save(path,value);command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],code)
        def verify(name,target,code):
            for platform,java,convert in platforms:
                path=out/(name+'-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)],code)
        def reseal(path):
            target=path/'artifact-checksums.tsv';target.write_text('sha256\tpath\n'+''.join(pin(p)['sha256']+'\t'+p.relative_to(path).as_posix()+'\n' for p in sorted(path.rglob('*')) if p.is_file() and p!=target),encoding='utf8',newline='\n')
        baseline=out/'copied-baseline';shutil.copytree(original,baseline);verify('copied-baseline',baseline,0);captured=read(baseline/'request.json');mutations=['published-count','original-pvalue','position-width','presence-matrix','GC-statistic','dinucleotide','count-metadata','background-identity','effective-query','normalized-counts','raw-counts','raw-reference','raw-PFM-width','raw-PFM-label','worker-seed','effective-sampler','source-request','canonical-profile','RNG-replay','literal-source','warnings','diagnostics','process-exit','runtime-protocol','worker-command','raw-input-hash-population','native-followup-request','published-background','effective-PFM-count','effective-PFM-background','effective-PFM-id','effective-PFM-position','effective-PFM-missing','raw-PFM-count'];rejections=[]
        for name in mutations:
            target=out/('tamper-'+name);shutil.copytree(baseline,target)
            def change_tsv(file,column,delta=1):
                path=target/file;lines=path.read_text(encoding='utf8').splitlines();index=lines[0].split('\t').index(column);fields=lines[1].split('\t');fields[index]=str(float(fields[index])+delta);lines[1]='\t'.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            if name.startswith('effective-PFM-'):
                for file in ['worker/effective-pfms.tsv','effective-pfms.tsv']:
                    if name in ['effective-PFM-count','effective-PFM-background']:change_tsv(file,'A' if name=='effective-PFM-count' else 'bg_A')
                    else:
                        path=target/file;lines=path.read_text(encoding='utf8').splitlines()
                        if name=='effective-PFM-missing':lines.pop(1)
                        else:
                            fields=lines[1].split('\t');fields[0 if name=='effective-PFM-id' else 2]='OTHER' if name=='effective-PFM-id' else '2';lines[1]='\t'.join(fields)
                        path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name=='raw-PFM-count':change_tsv(captured['pfm'],'A')
            elif name=='published-count':change_tsv('enrichment.tsv','observed')
            elif name=='original-pvalue':
                for file in ['worker/enrichment-original.tsv','enrichment.tsv']:change_tsv(file,'pvalue',0.01)
            elif name=='position-width':
                for file in ['worker/motif-positions.tsv','motif-positions.tsv']:change_tsv(file,'end')
            elif name=='presence-matrix':
                for file in ['worker/motif-presence.mtx','motif-presence.mtx']:
                    path=target/file;lines=path.read_text(encoding='utf8').splitlines();i=next(i+1 for i,line in enumerate(lines) if not line.startswith('%'));fields=lines[i].split();fields[0]='1' if fields[0]!='1' else '2';lines[i]=' '.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name in ['GC-statistic','dinucleotide','count-metadata']:
                for file in ['worker/sequence-stats.tsv','sequence-stats.tsv']:change_tsv(file,{'GC-statistic':'GC.percent','dinucleotide':'AA','count-metadata':'count'}[name])
            elif name in ['background-identity','effective-query','published-background']:
                if name=='published-background':files=['background-peaks.tsv']
                else:files=['worker/effective-background.tsv','background-peaks.tsv'] if name=='background-identity' else ['worker/effective-query.tsv','query-peaks.tsv']
                for file in files:
                    path=target/file;lines=path.read_text(encoding='utf8').splitlines();i=1 if file.startswith('worker/') else 0;lines[i]=lines[i+1];path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name in ['normalized-counts','raw-counts']:
                file='exchange/counts.mtx' if name=='normalized-counts' else captured['counts'];path=target/file;lines=path.read_text(encoding='utf8').splitlines();i=next(i+1 for i,line in enumerate(lines) if not line.startswith('%'));fields=lines[i].split();fields[2]=str(int(float(fields[2]))+1) if name=='raw-counts' else str(float(fields[2])+1);lines[i]=' '.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name=='raw-reference':
                path=target/captured['fasta'];lines=path.read_text(encoding='utf8').splitlines();sequence=list(lines[1]);sequence[99]='C' if sequence[99]!='C' else 'A';lines[1]=''.join(sequence);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name in ['raw-PFM-width','raw-PFM-label']:
                path=target/captured['pfm'];lines=path.read_text(encoding='utf8').splitlines()
                if name=='raw-PFM-width':fields=lines[4].split('\t');fields[2]='5';lines.insert(5,'\t'.join(fields))
                else:
                    for i in range(1,5):fields=lines[i].split('\t');fields[1]='changed TF label';lines[i]='\t'.join(fields)
                path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name in ['worker-seed','source-request','canonical-profile','diagnostics','process-exit','worker-command','raw-input-hash-population','native-followup-request']:
                file={'worker-seed':'worker/parameters.json','source-request':'source-request.json','canonical-profile':'request.json','diagnostics':'diagnostics.json','native-followup-request':'native-enrichment-request.json'}.get(name,'manifest.json');path=target/file;value=read(path)
                if name in ['worker-seed','source-request']:value['seed']+=1
                elif name=='canonical-profile':value['profile']='1.14.0-source'
                elif name=='diagnostics':value['maximum_GC_error']+=1
                elif name=='process-exit':value['process_exit_code']=17
                elif name=='worker-command':value['command'][0]='another-Rscript.exe'
                elif name=='raw-input-hash-population':value['captured_input_sha256']={}
                else:value['p_adjust_method']='none'
                save(path,value)
            elif name=='effective-sampler':
                path=target/'worker/effective-settings.tsv';text=path.read_text(encoding='utf8');assert 'sampling\tbase-R-sample.int' in text;path.write_text(text.replace('sampling\tbase-R-sample.int','sampling\tdifferent-sampler'),encoding='utf8',newline='\n')
            elif name=='RNG-replay':change_tsv('worker/rng-replay-after.tsv','value')
            elif name=='literal-source':
                path=target/'worker/source/signac-scan/1.17.1/FindMotifs.R';path.write_text(path.read_text(encoding='utf8')+'\n# altered source\n',encoding='utf8',newline='\n')
            elif name=='warnings':(target/'WARNINGS.md').write_text('All motifs are causal; no review is needed.\n',encoding='utf8',newline='\n')
            elif name=='runtime-protocol':
                path=target/'worker/worker-request.txt';lines=path.read_text(encoding='utf8').splitlines();lines[3]='other-parameters.json';path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            else:raise AssertionError(name)
            if name in ['raw-counts','raw-reference','raw-PFM-width','raw-PFM-label','raw-PFM-count']:
                # Update both inventories: rejection must come from the native semantic audit.
                value=read(target/'manifest.json');field={'raw-counts':'counts','raw-reference':'fasta','raw-PFM-width':'pfm','raw-PFM-label':'pfm','raw-PFM-count':'pfm'}[name];value['captured_input_sha256'][captured[field]]=pin(target/captured[field])['sha256'];save(target/'manifest.json',value)
            reseal(target);verify('reject-'+name,target,2);rejections.append(dict(mutation=name,rejected_platforms=3));print('rejected altered scan evidence '+name,flush=True)
        nested=out/'unlisted-nested-file';shutil.copytree(baseline,nested);(nested/'input/artifact-checksums.tsv').write_text('extra\n',encoding='utf8',newline='\n');verify('reject-nested-file',nested,2)
        before=inventory(baseline);value=copy.deepcopy(original_request);value['out']=str(baseline);launch('existing-output',value,2);assert inventory(baseline)==before
        skips=[]
        for name in ['missing-rscript','missing-motif-package']:
            value=copy.deepcopy(original_request);target=out/name;value['out']=str(target)
            if name=='missing-rscript':value['rscript']=str(out/'Rscript-not-installed.exe')
            else:empty=out/'empty-motif-library';empty.mkdir();value['r_motif_library']=str(empty)
            launch(name,value,3);assert read(target/'analysis-status.json')['status']=='skipped-dependency-unavailable' and not (target/'enrichment.tsv').exists() and (target/'WARNINGS.md').read_text(encoding='utf8').startswith('# SKIP3');verify('reject-skipped-'+name,target,2);skips.append(dict(name=name,exit_code=3,no_result_admitted=True))
        harness=out/'ScanInterrupt.java';harness.write_text('''package org.jlinalg.cli;import java.nio.file.*;import java.util.concurrent.atomic.AtomicInteger;public final class ScanInterrupt{public static void main(String[] a)throws Exception{AtomicInteger code=new AtomicInteger(-1);Thread worker=new Thread(()->code.set(SignacScanRequestCli.run(new String[]{"--request",a[0]},System.out,System.err)),"owned-motif-scan-request");worker.start();while(worker.isAlive()&&!Files.exists(Path.of(a[1])))Thread.sleep(25);if(worker.isAlive())worker.interrupt();worker.join(20000);if(worker.isAlive()){ProcessHandle.current().descendants().forEach(ProcessHandle::destroyForcibly);throw new AssertionError("Owned scan request failed to stop");}if(code.get()!=2||!worker.isInterrupted())throw new AssertionError("Lost interruption or wrong exit");System.out.println("OWNED_SCAN_INTERRUPT_EXIT2_FLAG_PRESERVED");}}
''',encoding='utf8',newline='\n');classes=out/'interrupt-classes';classes.mkdir();command('compile-interrupt',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,harness])
        def query(script):
            r=subprocess.run(['pwsh.exe','-NoLogo','-NoProfile','-Command',script],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=12,check=True);raw=r.stdout.decode('utf-8-sig').strip();value=[] if not raw else json.loads(raw);return value if isinstance(value,list) else [value]
        def descendants(pid):
            return query(f"$taskParentPid={int(pid)};$taskAll=Get-CimInstance Win32_Process;$taskKnown=[Collections.Generic.HashSet[uint32]]::new();[void]$taskKnown.Add([uint32]$taskParentPid);do{{$taskChanged=$false;foreach($taskProcess in $taskAll){{if($taskKnown.Contains([uint32]$taskProcess.ParentProcessId)-and -not $taskKnown.Contains([uint32]$taskProcess.ProcessId)){{[void]$taskKnown.Add([uint32]$taskProcess.ProcessId);$taskChanged=$true}}}}}}while($taskChanged);$taskAll|Where-Object{{$taskKnown.Contains([uint32]$_.ProcessId)-and $_.ProcessId -ne $taskParentPid}}|Select-Object ProcessId,ParentProcessId,Name,CreationDate|ConvertTo-Json -Compress")
        owned=[]
        for name in ['timeout','interrupt']:
            value=copy.deepcopy(original_request);target=out/('owned-'+name);value['out']=str(target);value['timeout']=4 if name=='timeout' else 60;path=out/(name+'-request.json');save(path,value);signal=out/'interrupt.signal';argv=[str(JAVA21/'java.exe'),'-Xmx768m','-cp',str(jar),CLI,'--request',str(path)] if name=='timeout' else [str(JAVA21/'java.exe'),'-Xmx768m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.ScanInterrupt',str(path),str(signal)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                process=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);observed=[];deadline=time.monotonic()+12
                try:
                    while process.poll() is None and time.monotonic()<deadline:
                        observed=[p for p in descendants(process.pid) if p['Name'].lower() in ['rscript.exe','rterm.exe','r.exe']]
                        if observed:break
                        time.sleep(.1)
                    if name=='interrupt':signal.write_text('interrupt only this owned request thread\n',encoding='utf8',newline='\n')
                    code=process.wait(timeout=35)
                except BaseException:
                    if name=='interrupt' and not signal.exists():signal.write_text('interrupt after observer failure\n',encoding='utf8',newline='\n')
                    process.wait(timeout=70);raise
            save(out/(name+'.process.json'),dict(command=argv,exit_code=code,observed_owned_R_processes=observed));assert observed and code==(2 if name=='timeout' else 0),(name,code,observed)
            ids=','.join(str(int(p['ProcessId'])) for p in observed);current=query(f"$taskPids=@({ids});Get-CimInstance Win32_Process|Where-Object{{$_.ProcessId -in $taskPids}}|Select-Object ProcessId,ParentProcessId,Name,CreationDate|ConvertTo-Json -Compress");live=[p for p in current if any(p['ProcessId']==v['ProcessId'] and p['CreationDate']==v['CreationDate'] for v in observed)];save(out/(name+'-direct-PID-recheck.json'),dict(observed=observed,surviving_same_processes=live));assert not live and not (target/'enrichment.tsv').exists() and read(target/'analysis-status.json')['status']=='failed';cleanup=read(target/'manifest.json')['owned_process_cleanup'];assert cleanup and all(not p['alive_after_cleanup'] for p in cleanup);owned.append(dict(name=name,actual_live_R_observed=True,direct_PID_and_creation_time_recheck=True,owned_processes_stopped=True,no_result_admitted=True))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-scan-evidence-corruption-skips-and-owned-process-recovery',source_pins=sources,artifact=build['artifact'],copied_verifications=3,rehashed_rejections=rejections,unlisted_nested_file_rejections=3,existing_output_preserved=True,actual_dependency_skips=skips,actual_owned_process_checks=owned,full_Signac_workflow_available=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],rehashed_rejections=len(rejections)*3,actual_dependency_skips=2,actual_owned_process_checks=2)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_SCAN_EVIDENCE_RECOVERY_CHECKS',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scan recovery checks')
    main()
