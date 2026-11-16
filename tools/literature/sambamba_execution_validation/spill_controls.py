#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Observe original disk spills and compare complete BAMs across explicit thread/compression settings."""
from pathlib import Path
import argparse,gzip,hashlib,json,os,re,struct,subprocess,sys,time
from adversarial import pin,request,save

def process_identity(path,binary,parent):
    try:
        rows=dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#') and '=' in line)
        pid=int(rows['pid']);text=Path('/proc',str(pid),'stat').read_text();values=text[text.rfind(')')+2:].split()
        if int(values[1])==parent and values[0]!='Z' and Path('/proc',str(pid),'exe').resolve()==binary.resolve():
            return dict(pid=pid,parent_pid=parent,start_ticks=values[19],executable=str(binary.resolve()))
    except (OSError,ValueError,KeyError):pass
    return None

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ['jar','binary066','binary082','out']:p.add_argument('--'+key,type=Path,required=True)
    p.add_argument('--pairs',type=int,default=30000);a=p.parse_args()
    if sys.platform!='linux':raise FileNotFoundError('Run inside the permitted Linux/WSL Python and JDK')
    if not 30000<=a.pairs<=100000:raise ValueError('Require30000..100000 pairs for an observable sort spill within this control')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);candidate=pin(a.jar);checks=[]
    def run(where,label,command,analysis=None,binary=None):
        start=time.monotonic();observed={};identities={};samples=0
        with (where/(label+'.stdout')).open('xb') as so,(where/(label+'.stderr')).open('xb') as se:
            child=subprocess.Popen(list(map(str,command)),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
            try:
                while child.poll() is None:
                    if time.monotonic()-start>240:raise TimeoutError('Owned validation JVM exceeded240seconds')
                    if analysis is not None:
                        for phase in ['mark','remove']:
                            identity=process_identity(analysis/(phase+'.pid.properties'),binary,child.pid)
                            if identity is not None:
                                identities[phase]=identity;samples+=1
                                scratch=analysis/('tmp-'+phase)
                                for parent,dirs,files in os.walk(scratch):
                                    for name in files:
                                        path=Path(parent,name);key=path.relative_to(analysis).as_posix()
                                        try:size=path.stat().st_size
                                        except FileNotFoundError:continue
                                        prior=observed.setdefault(key,dict(phase=phase,first_seconds=round(time.monotonic()-start,4),maximum_observed_bytes=0))
                                        prior['maximum_observed_bytes']=max(prior['maximum_observed_bytes'],size)
                    time.sleep(.002)
                result=child.wait()
            finally:
                if child.poll() is None:child.kill();child.wait(timeout=15)
        row=dict(case=label,command=list(map(str,command)),exit_code=result,seconds=round(time.monotonic()-start,2),
                 original_process_identities=identities,observation_samples=samples,observed_temporary_files=observed)
        save(where/(label+'.process.json'),row);checks.append(row)
        if result:raise RuntimeError(label+' failed: '+(where/(label+'.stderr')).read_text(errors='replace')[-3000:])
        print('PASS',label,'observed temporary files',len(observed),flush=True);return row
    run(out,'generate-stress',[sys.executable,'-B',Path(__file__).with_name('make_stress.py'),'--out',out/'input','--pairs',a.pairs])
    classes=out/'probe-classes';classes.mkdir()
    run(out,'compile-probe',['/usr/bin/javac','--release','17','-cp',a.jar,'-d',classes,Path(__file__).with_name('QualificationProbe.java')])
    java=['/usr/bin/java','-Xmx2g','-cp',str(a.jar.resolve())+':'+str(classes)]
    common=java+['org.jlinalg.cli.SambambaWorkflowCli'];probe=java+['org.jlinalg.cli.QualificationProbe']
    variants=[('large-table',dict(threads='1',compression='6',hash_table_size='262144',overflow_list_size='200000',sort_buffer_mb='16')),
              ('spill-one-thread',dict(threads='1',compression='0',hash_table_size='1',overflow_list_size='1024',sort_buffer_mb='1')),
              ('spill-two-threads',dict(threads='2',compression='9',hash_table_size='1',overflow_list_size='1024',sort_buffer_mb='1'))]
    case_results=[]
    for edition,binary in [('0.6.6',a.binary066),('0.8.2',a.binary082)]:
        original=None
        for name,changes in variants:
            where=out/edition/name;where.mkdir(parents=True);req=where/'request.properties'
            request(req,out/'input/stress.bam',binary,edition=edition,action='remove',wall_ms='180000',work_mb='512',max_files='512',**changes)
            normal=run(where,'analysis',common+['--request',req,'--out',where/'analysis'],where/'analysis',binary)
            replay=run(where,'verification',common+['--verify',where/'analysis','--out',where/'verification'],where/'verification',binary)
            for folder in ['analysis','verification']:
                root=where/folder;assert (root/'COMPLETED.properties').is_file()
                with (root/'decisions.tsv').open() as f:
                    assert f.readline().startswith('ordinal\tname\t');count=duplicates=0
                    for line in f:
                        fields=line.rstrip('\n').split('\t');flag=int(fields[3]);expected=(99 if count<a.pairs else 147)
                        if fields[1]!='stress_00000000':expected|=1024
                        assert flag==expected,(edition,name,count,fields[1],flag,expected)
                        duplicates+=bool(flag&1024);count+=1
                assert count==2*a.pairs and duplicates==2*(a.pairs-1)
                metrics=dict(line.rstrip('\n').split('\t') for line in (root/'audit.tsv').read_text().splitlines()[1:])
                assert metrics['retained_after_removal']=='2'
            witnesses={}
            if name.startswith('spill-'):
                for label,execution in [('analysis',normal),('verification',replay)]:
                    assert set(execution['original_process_identities'])=={'mark','remove'}
                    for phase in ['mark','remove']:
                        files={n:r for n,r in execution['observed_temporary_files'].items() if r['phase']==phase and r['maximum_observed_bytes']>0}
                        sorted_bams=[n for n in files if re.fullmatch(r'sorted\.\d+\.bam',Path(n).name)]
                        pair_chunks=[n for n in files if Path(n).name.startswith('PairedEndsInfo')]
                        assert len(sorted_bams)>2,(edition,name,label,phase,'pairing spill not observed',sorted_bams)
                        assert len(pair_chunks)>1,(edition,name,label,phase,'sort spill not observed',pair_chunks)
                        witnesses[label+'/'+phase]=dict(pairing_spill_bams=len(sorted_bams),pair_sort_chunks=len(pair_chunks))
            if original is None:original=where/'analysis'
            else:run(where,'complete-records-vs-large-table',probe+['compare',original,where/'analysis'])
            case_results.append(dict(edition=edition,variant=name,records=2*a.pairs,expected_duplicates=2*(a.pairs-1),retained_records=2,
                                     spill_witnesses=witnesses,analysis_exit=normal['exit_code'],verification_exit=replay['exit_code']))
    assert candidate==pin(a.jar)
    save(out/'COMPLETED.json',dict(status='PASS_BOTH_EDITIONS_ACTUAL_SPILL_THREAD_COMPRESSION_AND_COMPLETE_RECORD_CONTROLS',
        candidate=candidate,checks=checks,cases=case_results,original_mark_remove_processes=24,
        complete_BAM_comparisons_across_settings=8,independent_expected_flags_checked=True,
        bai_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_BOTH_EDITIONS_SPILL_THREAD_COMPRESSION_CONTROLS',flush=True)
if __name__=='__main__':
    try:main()
    except FileNotFoundError as e:print('SKIP3_SPILL_VALIDATION_PREREQUISITE',repr(e),flush=True);raise SystemExit(3)
    except Exception as e:print('FAIL2_SPILL_VALIDATION_UNADMITTED',repr(e),flush=True);raise
