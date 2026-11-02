#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen original fastp evidence and whole FASTQ summary populations.

Launches no analysis. Use fastp-tools --verify for the complete native report,
transformation-envelope and disposition replay, including all retained indexes.
"""
import gzip,hashlib,io,json,math,stat,zipfile
from pathlib import Path
from catalog_source_integrity import verify_source

ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026'

def sha(raw):return hashlib.sha256(raw).hexdigest()
def stream_pin(stream):
    h=hashlib.sha256();size=0
    while True:
        block=stream.read(1048576)
        if not block:break
        size+=len(block);assert size<=2_147_483_648,'Frozen evidence member exceeds2GiB';h.update(block)
    return dict(bytes=size,sha256=h.hexdigest())
def pin(path):
    with Path(path).open('rb') as stream:return stream_pin(stream)
def object_(z,name):
    entry=z.getinfo(name);assert entry.file_size<=16_777_216
    return json.loads(z.read(entry))
def census(z,extras):
    names=z.namelist();assert len(names)==len(set(names)) and len(names)<=10000
    expected=object_(z,'MANIFEST.json');assert set(names)==set(expected)|set(extras)
    for name,wanted in expected.items():
        assert name and '\\' not in name and ':' not in name and not name.startswith('/') and all(p not in ['','.','..'] for p in name.split('/'))
        entry=z.getinfo(name);assert not stat.S_ISLNK(entry.external_attr>>16) and entry.file_size==wanted['bytes']
        with z.open(entry) as stream:assert stream_pin(stream)==wanted,name
    return expected
def fastq(stream,offset):
    result=dict(total_reads=0,total_bases=0,q20_bases=0,q30_bases=0,gc_bases=0)
    while True:
        header=stream.readline()
        if not header:break
        lines=[stream.readline() for _ in range(3)];assert header.startswith(b'@') and all(lines),'Truncated FASTQ'
        sequence,plus,quality=[line.rstrip(b'\r\n') for line in lines];assert plus.startswith(b'+') and len(sequence)==len(quality)
        assert all(33<=c<=126 for c in sequence) and all(0<=c-offset<=93 for c in quality)
        result['total_reads']+=1;result['total_bases']+=len(sequence);result['q20_bases']+=sum(c>=offset+20 for c in quality);result['q30_bases']+=sum(c>=offset+30 for c in quality);result['gc_bases']+=sum((c&7) in [3,7] for c in sequence)
    return result
def read_fastq(z,name,offset):
    with z.open(name) as raw:
        if name.endswith('.gz'):
            with gzip.GzipFile(fileobj=raw) as stream:return fastq(stream,offset)
        return fastq(raw,offset)
def rounded(actual,expected):
    assert isinstance(actual,(int,float)) and math.isfinite(actual)
    if not expected:assert actual==0;return
    tolerance=0.500001*10**(math.floor(math.log10(abs(expected)))-5)+8*math.ulp(expected);assert abs(actual-expected)<=tolerance,(actual,expected)
def verify_case(z,name):
    prefix=name+'/result/';manifest=object_(z,prefix+'manifest.json');marker=object_(z,prefix+'COMPLETED.json');assert marker['scoped_result_admitted'] and marker['original_tool_executed'] and marker['whole_paper_reproduced'] is False and marker['biological_accuracy_certified'] is False
    for record in [manifest,marker]:assert record['schema_version']==1 and record['tool']=='fastp-original' and record['scope']=='complete-population-report-and-conservation-envelope'
    with z.open(prefix+'manifest.json') as stream:assert stream_pin(stream)==marker['manifest']
    files=manifest['files'];assert {n[len(prefix):] for n in z.namelist() if n.startswith(prefix)}==set(files)|{'manifest.json','COMPLETED.json'}
    for member,wanted in files.items():
        with z.open(prefix+member) as stream:assert stream_pin(stream)==wanted,member
    replay=object_(z,prefix+'recipe-replay.json');request=object_(z,prefix+'processing/request.json');assert replay['profile']==request['profile'] and replay['options']==request['options'] and replay['limits']['stage']==request['limits']
    version=request['profile'].split('fastp-')[-1];assert version in ['0.22.0','0.23.2','0.25.0','1.0.1']
    process=object_(z,prefix+'processing/original/process.json');assert process['status']=='completed-process-only' and process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    launch=object_(z,prefix+'processing/original/launch.json');assert launch['status']=='running' and launch['command']==process['command'] and launch['owned_launcher_pid']==process['owned_launcher_pid']
    captures=object_(z,prefix+'processing/input-captures.json');assert len(captures) in [1,2];before=[];after=[]
    for i,capture in enumerate(captures,1):
        assert replay['inputs']['read'+str(i)]=='processing/'+capture['snapshot']
        with z.open(prefix+'processing/'+capture['snapshot']) as stream:assert stream_pin(stream)==capture['identity']['pin']
        before.append(read_fastq(z,prefix+'processing/'+capture['snapshot'],64 if request['encoding']=='PHRED64' else 33));after.append(read_fastq(z,prefix+'processing/original/read'+str(i)+'.fastq',33))
    report=object_(z,prefix+'processing/original/fastp.json');assert report['summary']['fastp_version']==version
    for stage,models in [('before_filtering',before),('after_filtering',after)]:
        summary=report['summary'][stage];totals={key:sum(m[key] for m in models) for key in before[0]}
        for key in ['total_reads','total_bases','q20_bases','q30_bases']:assert summary[key]==totals[key]
        for i,m in enumerate(models,1):
            assert summary['read'+str(i)+'_mean_length']==(m['total_bases']//m['total_reads'] if m['total_reads'] else 0)
            detail=report['read'+str(i)+'_'+stage]
            for key in ['total_reads','total_bases','q20_bases','q30_bases']:assert detail[key]==m[key]
        for field,base in [('q20_rate','q20_bases'),('q30_rate','q30_bases'),('gc_content','gc_bases')]:rounded(summary[field],totals[base]/totals['total_bases'] if totals['total_bases'] else 0)
    represented=sum(m['total_reads'] for m in after)+read_fastq(z,prefix+'processing/original/failed.fastq',33)['total_reads']
    if len(captures)==2 and replay['options']['storeUnpaired']:
        for i in [1,2]:represented+=read_fastq(z,prefix+f'processing/original/unpaired{i}.fastq',33)['total_reads']
    session=object_(z,prefix+'session.json');audit=session['audit_result'];counts=audit['conservation'];total=sum(m['total_reads'] for m in before);assert counts['inputReads']==total and counts['representedReads']==represented and counts['unrepresentedReads']==total-represented
    assert session['main_passing_reads']==sum(m['total_reads'] for m in after) and session['downstream_usable']==(session['main_passing_reads']>0)
    assert sum(report['filtering_result'].get(k,0) for k in ['passed_filter_reads','low_quality_reads','too_many_N_reads','too_short_reads','too_long_reads','low_complexity_reads'])==total
    warning=z.read(prefix+'WARNINGS.md').decode();assert 'Required action' in warning and 'SKIP3' in warning and 'FAIL2' in warning and 'not authenticity' in warning
    if name=='p054-reported-options':assert replay['options']['duplication']['accuracy']==6 and '--dup_calc_accuracy' in process['command'] and process['command'][process['command'].index('--dup_calc_accuracy')+1]=='6' and process['peak_sampled_rss_bytes']>=24<<30
    return dict(input_reads=total,represented_reads=represented,unrepresented_reads=total-represented,independently_recomputed_report_fields=audit['independentlyRecomputedReportFields'],additionally_constrained_report_fields=audit['additionallyConstrainedReportFields'])

def verify(root=ROOT,data=DATA,emit=True):
    root=Path(root);proof=json.loads((root/'docs/literature/FASTP_WORKFLOW_DEVELOPMENT_VALIDATION.json').read_bytes());assert proof['status']=='passed-scoped-fastp-public-workflow-and-portable-verification'
    for path,expected in proof['source_pins'].items():verify_source(root,data,path,expected['sha256'])
    archive=root/'docs/literature'/proof['artifact']['path'];assert pin(archive)=={k:v for k,v in proof['artifact'].items() if k!='path'}
    totals={};cases=0
    with zipfile.ZipFile(archive) as outer:
        census(outer,{'MANIFEST.json'})
        for member,expected in [('validation/public-workflow-controls-1.zip',57),('validation/large-workflow-control-1.zip',1)]:
            with zipfile.ZipFile(io.BytesIO(outer.read(member))) as z:
                census(z,{'MANIFEST.json','CASES.json'});case_list=object_(z,'CASES.json');assert len(case_list['cases'])==expected and case_list['all_owned_processes_terminal'] and case_list['jar']['sha256']==proof['packaged_candidate_sha256']
                for name in case_list['cases']:
                    observed=object_(z,name+'/observed.json');assert observed['exit_code']==0 and observed['jar']['sha256']==proof['packaged_candidate_sha256'];values=verify_case(z,name);cases+=1
                    for key,value in values.items():totals[key]=totals.get(key,0)+value
        matrix=json.loads(outer.read('validation/packaged-tests-1/summary.json'));assert matrix['tests_found_per_JVM']==415 and len(matrix['runs'])==3 and all(r['exit_code']==0 for r in matrix['runs']) and matrix['artifact_sha256']==proof['packaged_candidate_sha256']
        large=json.loads(outer.read('validation/large-portable-windows-1/observed.json'));assert large['exit_code']==0 and large['input_bytes']==84300000 and large['input_reads']==300000 and not large['original_processing_rerun']
        for member in ['export-install-analysis-chain-1.json','guide-example-1.json']:
            record=json.loads(outer.read('validation/'+member));assert all(r['exit_code']==0 for r in record['records'])
        with zipfile.ZipFile(io.BytesIO(outer.read('validation/failed-smoke-1.zip'))) as z:assert 'result/COMPLETED.json' not in z.namelist() and object_(z,'result/FAILURE.json')['exit_code']==2
    assert cases==58 and totals==proof['counts'];result=dict(status='passed-complete-fastp-frozen-public-workflow-evidence',public_original_workflows=cases,**totals)
    if emit:print(json.dumps(result,indent=2))
    return result

if __name__=='__main__':
    try:verify()
    except Exception as failure:print('fastp EVIDENCE FAIL2: '+str(failure)+'\nRequired action: preserve source/results,correct the named mismatch,and repeat validation. No partial or changed scientific result is promoted.',file=__import__('sys').stderr);raise
