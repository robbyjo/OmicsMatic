#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check pinned native Snakemake benchmark records, not current runtime health."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,sys,traceback,zipfile,xml.etree.ElementTree as ET
from decimal import Decimal

ROOT=Path(__file__).resolve().parents[3]
PROOF='docs/literature/snakemake-benchmark-development'
PROFILE='snakemake-benchmark-9.27.1-v1'
if not __debug__:raise RuntimeError('Scientific/evidence assertions must remain enabled')
ACTION='SKIP3: restore the exact permitted missing evidence or skip the dependent check. FAIL2: no result admitted; retain all inputs/diagnostics and correct the named discrepancy before retrying NEW output. Never refresh saved hashes or infer current runtime health, paper reproduction or full-tool equivalence from saved records.'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def filepin(path):
    h=hashlib.sha256();n=0
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):n+=len(raw);h.update(raw)
    return dict(bytes=n,sha256=h.hexdigest())
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def table(raw):
    text=raw.decode('utf-8');assert text.endswith('\n');rows=[line.removesuffix('\r').split('\t') for line in text.splitlines()];assert rows
    assert all(len(row)==len(rows[0]) for row in rows);return [dict(zip(rows[0],row)) for row in rows[1:]]
def properties(raw):
    result={}
    for line in raw.decode('utf-8').splitlines():
        assert '=' in line;key,value=line.split('=',1);assert key not in result;result[key]=value
    return result
def check_bundle(files,prefix):
    data={name[len(prefix):]:raw for name,raw in files.items() if name.startswith(prefix)};assert 'COMPLETED.properties' in data
    marker=properties(data['COMPLETED.properties']);assert set(marker)=={'profile','jobs','tables','groups','kind','manifest_sha256','parent_manifest_sha256'} and marker['profile']==PROFILE
    assert pin(data['FILES.tsv'])['sha256']==marker['manifest_sha256']
    actual={};directories=set()
    for name,raw in data.items():
        if name in ['COMPLETED.properties','FILES.tsv']:continue
        value=pin(raw);actual[name]=dict(kind='F',bytes=str(value['bytes']),sha256=value['sha256'])
        directories.update(str(p) for p in PurePosixPath(name).parents if str(p)!='.')
    for name in directories:actual[name]=dict(kind='D',bytes='0',sha256='-')
    declared=table(data['FILES.tsv']);expected={row['path']:{k:row[k] for k in ['kind','bytes','sha256']} for row in declared};assert len(expected)==len(declared) and actual==expected
    jobs=table(data['inputs/jobs.tsv']);benchmarks={name.removeprefix('inputs/benchmarks/'):table(raw) for name,raw in data.items() if name.startswith('inputs/benchmarks/')}
    assert {row['benchmark'] for row in jobs}==set(benchmarks)
    owners={};selected=set();seen_jobs=set();groups={};measurements=table(data['MEASUREMENTS.tsv']);assert len(measurements)==len(jobs)
    for job,measurement in zip(jobs,measurements):
        name=job['benchmark'];index=int(job['row'])-1;assert index>=0;source=benchmarks[name][index];group=(job['pipeline'],job['replicate']);identity=(name,index)
        assert identity not in selected;selected.add(identity);owner=(job['pipeline'],job['job']);assert owners.setdefault(name,owner)==owner
        logical=(*group,job['job']);assert logical not in seen_jobs;seen_jobs.add(logical)
        cpu,rss,wall=[Decimal(source[key]) for key in ['cpu_time','max_rss','s']];assert all(value.is_finite() and value>=0 for value in [cpu,rss,wall])
        value=groups.setdefault(group,dict(jobs=0,cpu_seconds=Decimal(0),max_job_rss_mib=Decimal(0),summed_job_wall_seconds=Decimal(0)));value['jobs']+=1;value['cpu_seconds']+=cpu;value['max_job_rss_mib']=max(value['max_job_rss_mib'],rss);value['summed_job_wall_seconds']+=wall
        assert all(measurement[key]==job[key] for key in ['pipeline','replicate','job','benchmark','row'])
        assert all(Decimal(measurement[key])==expected for key,expected in [('wall_seconds',wall),('cpu_seconds',cpu),('max_rss_mib',rss)])
    assert len(selected)==sum(len(rows) for rows in benchmarks.values())
    summary=table(data['SUMMARY.tsv']);assert len(summary)==len(groups)
    for row in summary:
        group=(row['pipeline'],row['replicate']);assert group in groups;expected=groups.pop(group);assert int(row['jobs'])==expected['jobs']
        assert all(Decimal(row[key])==expected[key] for key in ['cpu_seconds','max_job_rss_mib','summed_job_wall_seconds'])
    assert not groups and int(marker['jobs'])==len(jobs) and int(marker['tables'])==len(benchmarks) and int(marker['groups'])==len(summary)
    if marker['kind']=='native-benchmark-aggregate':assert marker['parent_manifest_sha256']=='none' and 'REPLAY_PARENT.tsv' not in data
    else:assert marker['kind']=='genuine-native-recomputation' and data['REPLAY_PARENT.tsv'].decode()=='manifest_sha256\n'+marker['parent_manifest_sha256']+'\n'
    return dict(jobs=len(jobs),groups=len(summary))
def verify(private):
    folder=ROOT/PROOF;status=json.loads((folder/'STATUS.json').read_bytes());assert status['status']=='PASS_NATIVE_SNAKEMAKE_BENCHMARK_COMPONENT_NOT_EXECUTION_ADAPTER'
    for name,key in [('SOURCE-PINS.json','source_pins'),('EVIDENCE-PINS.json','evidence_pins'),('PRIVATE-RECORDS.json','private_records'),('VALIDATION-RECORDS.zip','validation_records')]:assert filepin(folder/name)==status[key],name
    for name,expected in json.loads((folder/'SOURCE-PINS.json').read_bytes()).items():assert filepin(ROOT/name)==expected,name
    expected=json.loads((folder/'EVIDENCE-PINS.json').read_bytes());files={}
    with zipfile.ZipFile(folder/'VALIDATION-RECORDS.zip') as z:
        assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(expected) and z.testzip() is None
        for name in z.namelist():
            assert not name.startswith(('/','\\')) and '..' not in PurePosixPath(name).parts;raw=z.read(name);assert pin(raw)==expected[name],name;files[name]=raw
    census=json.loads(files['records/primary-2/CENSUS.json']);assert census['papers_scanned']==250 and len(census['roles'])==6 and census['new_observation_pending']==['P190'] and not census['existing_catalog_changed']
    integrated=json.loads(files['records/native-integration-1/COMPLETED.json']);assert len(integrated['cases'])==3 and all(row['all_expected_decimals_exact'] and row['linux_replay'] and row['windows_replay_of_linux_replay'] for row in integrated['cases'])
    prefixes=sorted({name.removesuffix('COMPLETED.properties') for name in files if name.startswith(('records/native-integration-1/','records/guide-example-1/','records/guide-example-replay-1/')) and name.endswith('COMPLETED.properties')})
    assert len(prefixes)==14
    summaries=[check_bundle(files,prefix) for prefix in prefixes]
    counts=dict(tests=0,failures=0,errors=0,skipped=0)
    for name,raw in files.items():
        if name.startswith('records/windows-regressions-1/TEST-') and name.endswith('.xml'):
            suite=ET.fromstring(raw)
            for key in counts:counts[key]+=int(suite.attrib[key])
    assert counts==status['windows_tests']==dict(tests=44,failures=0,errors=0,skipped=0)
    named=json.loads((folder/'PRIVATE-RECORDS.json').read_bytes());checked=0
    if private is not None:
        for name,expected in named.items():assert filepin(private/name)==expected,name;checked+=1
        with zipfile.ZipFile(private/'native-integration-1/candidate.jar') as jar:
            for prefix in prefixes:
                for row in table(files[prefix+'CODE.tsv']):assert pin(jar.read(row['path']))==dict(bytes=int(row['bytes']),sha256=row['sha256'])
    return dict(status='PASS_PINNED_NATIVE_SNAKEMAKE_BENCHMARK_RECORDS',public_bundles_independently_audited=len(summaries),assigned_job_rows_audited=sum(row['jobs'] for row in summaries),private_files_checked=checked,private_check_skipped=private is None,primary_papers_scanned=250,source_roles=6,fresh_java_or_engine_execution=False,installed_runtime_rechecked=False,new_catalog_credit=0,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);m=p.add_mutually_exclusive_group(required=True);m.add_argument('--public-only',action='store_true');m.add_argument('--private-root',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        target=a.out.resolve();assert target.is_relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target;(out/'WARNINGS.md').write_text('# Scope and required actions\n\n'+ACTION+'\n');result=verify(a.private_root);(out/'COMPLETED.json').write_bytes(encoded(result));print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None and out.is_dir():
            path=out/'FAILURE.json'
            if not path.exists():path.write_bytes(encoded(dict(error=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
        print(('SKIP3' if code==3 else 'FAIL2')+'_SNAKEMAKE_RECORDS',repr(error),ACTION);return code
if __name__=='__main__':raise SystemExit(main())
