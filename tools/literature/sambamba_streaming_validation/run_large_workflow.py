#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute both streaming workflow editions and genuine replays on the complete large synthetic input."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,time
from run_large import pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ['jar','binary066','binary082','input','out']:p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args()
    if sys.platform!='linux':raise FileNotFoundError('Use the approved Linux/WSL Python/JVM and original runtimes')
    manifest=json.loads(a.input.with_name('INPUT.json').read_bytes());pairs=manifest['pairs']
    if manifest['records']!=2*pairs or pairs<2000000 or pin(a.input)['sha256']!=manifest['sha256']:raise ValueError('Require the complete independent at-least4million-record input')
    out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);jar=out/'candidate.jar';candidate=pin(a.jar);shutil.copy2(a.jar,jar)
    if candidate!=pin(a.jar) or candidate!=pin(jar):raise ValueError('Candidate changed during capture')
    common=['/usr/bin/java','-Xms16m','-Xmx64m','-cp',str(jar),'org.jlinalg.cli.SambambaStreamingWorkflowCli'];checks=[]
    for edition,binary in [('0.6.6',a.binary066),('0.8.2',a.binary082)]:
        where=out/edition;where.mkdir();req=where/'request.properties'
        values=dict(bam=str(a.input.resolve()),sambamba=str(binary.resolve()),edition=edition,action='remove',threads='2',compression='6',
            hash_table_size='262144',overflow_list_size='200000',sort_buffer_mb='16',io_buffer_mb='1',wall_ms='600000',address_space_mb='8192',work_mb='8192',max_files='1024',
            audit_compressed_bytes='1099511627776',audit_decoded_bytes='8796093022208',audit_records='1099511627776',audit_record_bytes='16777216',audit_header_bytes='4194304',audit_dictionary_bytes='4194304')
        req.write_text('\n'.join(k+'='+v for k,v in values.items())+'\n',encoding='utf-8',newline='\n')
        for kind,command in [('analysis',common+['--request',str(req),'--out',str(where/'analysis')]),
                            ('verification',common+['--verify',str(where/'analysis'),'--out',str(where/'verification')])]:
            start=time.monotonic()
            with (where/(kind+'.stdout')).open('xb') as so,(where/(kind+'.stderr')).open('xb') as se:
                child=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
            row=dict(edition=edition,kind=kind,command=command,exit_code=child.returncode,seconds=round(time.monotonic()-start,2),requested_java_heap_mb=64)
            save(where/(kind+'.process.json'),row);checks.append(row);print(json.dumps(row),flush=True)
            if child.returncode:raise RuntimeError('Streaming '+edition+' '+kind+' failed; inspect retained phase consoles and FAILURE.txt')
            root=where/kind;assert (root/'COMPLETED.properties').is_file();count=duplicates=0
            with (root/'decisions.tsv').open() as ledger:
                header=ledger.readline();assert header=='ordinal\tname\toriginal_flag\tmarked_flag\tscored_by_source\tquality_score\tfive_prime_source_coordinate\n'
                for line in ledger:
                    f=line.rstrip('\n').split('\t');first=count<pairs;index=count if first else count-pairs;best=index==0
                    expected=[str(count),'large_%09d'%index,str(99 if first else 147),str((99 if first else 147)|(0 if best else 1024)),
                              'true',str(400 if best else 200),str(999 if first else 2009)]
                    if f!=expected:raise ValueError('Independent complete recorded decision differs at'+str(count))
                    duplicates+=not best;count+=1
            assert count==manifest['records'] and duplicates==manifest['expected_duplicate_records']
            metrics=dict(line.split('\t') for line in (root/'audit.tsv').read_text().splitlines()[1:])
            assert int(metrics['input_records'])==count and metrics['retained_after_removal']=='2' and metrics['input_stream_sha256']==manifest['sha256']
            row['independently_checked_ledger_records']=count;row['ledger_bytes']=(root/'decisions.tsv').stat().st_size
            print('PASS_FULL_RECORDED_LEDGER',edition,kind,count,'records',row['ledger_bytes'],'bytes',flush=True)
    assert pin(jar)==candidate and pin(a.input)['sha256']==manifest['sha256']
    save(out/'COMPLETED.json',dict(status='PASS_BOTH_STREAMING_WORKFLOW_EDITIONS_FOUR_MILLION_RECORDS_AND_GENUINE_REPLAYS_WITH_64_MIB_HEAP',
        candidate=candidate,input=manifest,checks=checks,original_mark_remove_calls=8,large_analysis_bundles=2,large_genuine_replays=2,
        all_recorded_decisions_independently_checked=True,BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False))
    print('PASS_FULL_LARGE_STREAMING_WORKFLOW_AND_REPLAYS',flush=True)
if __name__=='__main__':
    try:main()
    except FileNotFoundError as e:print('SKIP3_STREAMING_WORKFLOW_VALIDATION_PREREQUISITE',repr(e),'Supply the named permitted prerequisite and skip dependent analysis.',flush=True);raise SystemExit(3)
    except Exception as e:print('FAIL2_STREAMING_WORKFLOW_VALIDATION_UNADMITTED',repr(e),'Retain errors/partials; no accepted validation. Resolve the cause and retry NEW output.',flush=True);raise
