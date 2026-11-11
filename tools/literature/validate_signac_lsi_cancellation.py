# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Observe and terminate actual owned R processes through typed LSI interruption/timeout paths."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-owned-process-interruption-checks-in-progress'))
    try:
        build=json.loads((BASE/'lsi-build-5/BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        helper=out/'SignacLsiOwnedChecks.java';helper.write_text('''package org.jlinalg.cli;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public final class SignacLsiOwnedChecks{public static void main(String[] args)throws Exception{
String mode=args[0];Path report=Path.of(args[1]);String[] request=Arrays.copyOfRange(args,2,args.length);var result=new AtomicInteger(-999);
Thread worker=new Thread(()->result.set(SignacLsiToolsCli.run(request,System.out,System.err)),"owned-signac-fit");worker.start();
long deadline=System.nanoTime()+60_000_000_000L;List<ProcessHandle> children=List.of();
while(System.nanoTime()<deadline&&worker.isAlive()){children=ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).toList();if(!children.isEmpty())break;Thread.sleep(5);}
if(children.isEmpty())throw new AssertionError("No actually live owned process observed");
Files.writeString(report.resolve("observed-processes.tsv"),"pid\\talive_when_observed\\n"+children.stream().map(p->p.pid()+"\\ttrue\\n").collect(java.util.stream.Collectors.joining()));
if(mode.equals("interrupt"))worker.interrupt();else if(!mode.equals("timeout"))throw new AssertionError("Unknown ownership check");
worker.join(60000);if(worker.isAlive()||result.get()!=2)throw new AssertionError("Failed interrupted/timed-out return code");
for(ProcessHandle child:children){long until=System.nanoTime()+10_000_000_000L;while(child.isAlive()&&System.nanoTime()<until)Thread.sleep(10);if(child.isAlive())throw new AssertionError("Observed owned process still alive: "+child.pid());}
Files.writeString(report.resolve("OWNED-STOPPED.txt"),"ACTUAL_OWNED_R_PROCESS_OBSERVED_AND_STOPPED "+mode+" "+children.size()+"\\n");System.out.println("ACTUAL_OWNED_SIGNAC_PROCESS_STOPPED "+mode);}}
''',encoding='utf8',newline='\n')
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        classes=out/'classes';classes.mkdir();command('compile-owned-check',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,helper])
        data=BASE/'lsi-probe-1';inputs={n:pin(data/n) for n in ['counts.mtx','features.tsv','cells.tsv']};runs=[]
        for mode in ['interrupt','timeout']:
            report=out/mode;report.mkdir();destination=report/'fit'
            command(mode,[JAVA21/'java.exe','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.SignacLsiOwnedChecks',mode,report,'--counts',data/'counts.mtx','--features',data/'features.tsv','--cells',data/'cells.tsv','--out',destination,
                          '--rscript','C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--r-library',BASE/'reference-setup-1/R-lib','--r-base-library',ROOT/'build/omics-sctransform-r','--profile','1.17.1-package-rspectra','--components','5',*(['--timeout','1'] if mode=='timeout' else [])])
            assert (report/'OWNED-STOPPED.txt').exists() and not list(destination.glob('result-*'))
            manifest=json.loads((destination/'manifest.json').read_bytes());assert manifest['analysis_status']=='failed'
            assert 'Required action:' in (destination/'WARNINGS.md').read_text(encoding='utf8');runs.append(dict(mode=mode,actual_live_R_process_observed=True,observed_processes_stopped=True,no_results_admitted=True))
        assert all(pin(data/n)==v for n,v in inputs.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        save(out/'COMPLETED.json',dict(status='passed-actual-owned-Signac-R-interruption-and-timeout-cleanup',source_pins={'tools/literature/validate_signac_lsi_cancellation.py':pin(Path(__file__))},artifact=build['artifact'],runs=runs,inputs_unchanged=True))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(runs))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_ACTUAL_OWNED_SIGNAC_PROCESS_CHECK',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable process ownership checks')
    main()
