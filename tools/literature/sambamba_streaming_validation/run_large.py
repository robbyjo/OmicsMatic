#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run both original editions, then audit complete large BAMs in a measured small-heap JVM."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time

ROOT=Path(__file__).resolve().parents[3]
BINARIES={'0.6.6':'8b06691f80bc89c733267c523dbbb3a4c36b51cae42ef7b2a171e17c68516dc2','0.8.2':'bbccbd914ebf8c2b09a5bb6a922dba99ac24c09d25ddd4925f5b2cc8dc359543'}
def pin(path):
    h=hashlib.sha256();count=0
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1<<20),b''):h.update(block);count+=len(block)
    return dict(bytes=count,sha256=h.hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['jar','binary066','binary082','out']:parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--pairs',type=int,default=2000000);parser.add_argument('--heap-mb',type=int,default=64);a=parser.parse_args()
    if sys.platform!='linux':raise FileNotFoundError('Use the approved Linux/WSL Python, JDK and exact original runtimes')
    if a.heap_mb!=64 or a.pairs<2000000:raise ValueError('This qualification requires at least2million pairs and an explicit64-MiB Java heap')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);candidate=pin(a.jar);jar=out/'candidate.jar';shutil.copy2(a.jar,jar)
    if candidate!=pin(jar) or candidate!=pin(a.jar):raise ValueError('Actual candidate changed during capture')
    own=['src/main/java/org/jlinalg/alignment/SambambaStreamingBamAudit.java','src/test/java/org/jlinalg/alignment/SambambaStreamingBamAuditTest.java']
    own += [p.relative_to(ROOT).as_posix() for p in Path(__file__).parent.iterdir() if p.is_file()]
    save(out/'SOURCE-PINS.json',{n:pin(ROOT/n) for n in own});checks=[]
    def run(where,label,command,native=False):
        command=list(map(str,command));start=time.monotonic();peak=0;samples=0
        with (where/(label+'.stdout')).open('xb') as so,(where/(label+'.stderr')).open('xb') as se:
            p=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
            try:
                while p.poll() is None:
                    if time.monotonic()-start>600:raise TimeoutError('Owned validation process exceeded600seconds')
                    try:
                        for line in Path('/proc',str(p.pid),'status').read_text().splitlines():
                            if line.startswith('VmRSS:'):peak=max(peak,int(line.split()[1])*1024);samples+=1
                    except FileNotFoundError:pass
                    if native:
                        entries=0;size=0
                        for parent,dirs,files in os.walk(where):
                            entries+=len(dirs)+len(files)
                            for name in files:
                                try:size+=Path(parent,name).stat().st_size
                                except FileNotFoundError:pass
                        if entries>10000 or size>16<<30:raise RuntimeError('Original validation exceeded sampled10000-entry/16-GiB workspace bound')
                    time.sleep(.025)
                code=p.wait()
            finally:
                if p.poll() is None:p.kill();p.wait(timeout=15)
        row=dict(case=label,command=command,pid=p.pid,exit_code=code,seconds=round(time.monotonic()-start,2),peak_observed_root_rss_bytes=peak,rss_samples=samples,
                 rss_is_sampled_not_an_OS_limit=True,maximum_wall_seconds=600,native_address_space_bytes=(8<<30) if native else None)
        save(where/(label+'.process.json'),row);checks.append(row);print(json.dumps(row),flush=True)
        if code:raise RuntimeError(label+' failed; retain '+str(where/(label+'.stderr')))
    run(out,'independent-input',[sys.executable,'-B',Path(__file__).with_name('make_large.py'),'--pairs',a.pairs,'--out',out/'input'])
    data=json.loads((out/'input/INPUT.json').read_bytes());assert data['decoded_bytes']>256<<20 and data['records']>1000000
    classes=out/'probe-classes';classes.mkdir();run(out,'compile-probe',['/usr/bin/javac','--release','17','-cp',jar,'-d',classes,Path(__file__).with_name('LargeAudit.java')])
    results=[]
    for edition,binary in [('0.6.6',a.binary066),('0.8.2',a.binary082)]:
        if pin(binary)['sha256']!=BINARIES[edition]:raise ValueError('Exact original executable differs for'+edition)
        where=out/edition;where.mkdir()
        for action in ['mark','remove']:
            tmp=where/('tmp-'+action);tmp.mkdir();target=where/(action+'.bam')
            command=[str(binary.resolve()),'markdup','-t','2','-l','6','--hash-table-size=262144','--overflow-list-size=200000','--sort-buffer-size=16','--io-buffer-size=1','--tmpdir='+str(tmp)]
            if action=='remove':command.append('--remove-duplicates')
            command += [str(out/'input/input.bam'),str(target)]
            (where/(action+'.cl.txt')).write_text(' '.join(command[1:]),encoding='utf-8',newline='\n')
            run(where,'original-'+action,['/usr/bin/setpriv','--pdeathsig','KILL','/usr/bin/prlimit','--as='+str(8<<30),'--',*command],True)
        run(where,'java-small-heap',['/usr/bin/java','-Xms16m','-Xmx64m','-cp',str(jar)+':'+str(classes),'LargeAudit',
            out/'input/input.bam',where/'mark.bam',where/'remove.bam',edition,where/'mark.cl.txt',where/'remove.cl.txt',a.pairs,a.heap_mb,where/'AUDIT.properties'])
        rows=dict(line.split('=',1) for line in (where/'AUDIT.properties').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
        assert rows['status']=='PASS_LARGE_ORIGINAL_RECORDS_WITH_EXPLICIT_SMALL_HEAP' and int(rows['maximum_java_heap_bytes'])<=64<<20
        assert int(rows['decisions_independently_checked'])==data['records'] and rows['retained_records']=='2'
        if pin(binary)['sha256']!=BINARIES[edition]:raise ValueError('Original runtime changed while validating')
        results.append(dict(edition=edition,report=rows,mark=pin(where/'mark.bam'),remove=pin(where/'remove.bam')))
    if pin(out/'input/input.bam')['sha256']!=data['sha256'] or candidate!=pin(jar):raise ValueError('Captured candidate or independent input changed')
    save(out/'COMPLETED.json',dict(status='PASS_BOTH_ORIGINAL_EDITIONS_LARGE_STREAMING_COMPONENT_WITH_64_MIB_HEAP',candidate=candidate,input=data,
        checks=checks,results=results,whole_workflow_large_capacity_implemented=False,BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_BOTH_ORIGINAL_EDITIONS_LARGE_STREAMING_COMPONENT',flush=True)
if __name__=='__main__':
    try:main()
    except FileNotFoundError as e:print('SKIP3_LARGE_STREAMING_VALIDATION_PREREQUISITE',repr(e),'Supply the named permitted prerequisite; skip dependent analysis.',flush=True);raise SystemExit(3)
    except Exception as e:print('FAIL2_LARGE_STREAMING_VALIDATION_UNADMITTED',repr(e),'Retain raw outputs/errors; no accepted validation. Correct the cause and use NEW output.',flush=True);raise
