#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read back saved BAI controls. This does not rerun original software or scientific analysis."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/bai-query-qualification'
RETAINED=['wide-fixtures-1','wide-probe-1','wide-originals-1','wide-originals-2','adversarial-1','large-indexes-1','java-validation-2','source-review-1']
NOTICE='Scoped native BAM/BAI audit and recorded query controls only. Retain the three inconsistent original0.6.6 indexes and two score-overflow failures; they are not accepted analyses. Use a separately selected audited native index or a separately qualified edition/tool, or skip the affected analysis. Do not alter reads/qualities or refresh historical verifier identities. Large BAMs and JARs are retained locally, not in public metadata. Current-tree catalog admission and the full250-paper objective remain open; no new catalog credit.'
def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for raw in iter(lambda:stream.read(1<<20),b''):digest.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=digest.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def load(path):return json.loads(path.read_bytes())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def inventory(base):
    files={}
    for name in RETAINED:
        parent=base/name
        if not parent.is_dir() or parent.is_symlink():raise ValueError('Missing/aliased retained root: '+name)
        for path in sorted(parent.rglob('*')):
            if path.is_symlink():raise ValueError('Retained alias refused: '+str(path))
            if path.is_file():files[path.relative_to(base).as_posix()]=pin(path)
    return files
def archive_check(path,identities):
    with zipfile.ZipFile(path) as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or set(names)!=set(identities) or z.testzip() is not None:raise ValueError('Archive membership/CRC differs: '+str(path))
        for name,identity in identities.items():
            if name.startswith(('/','\\')) or '..' in Path(name).parts or rawpin(z.read(name))!=identity:raise ValueError('Unsafe/different archive member: '+name)
def properties(path):return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#') and '=' in line)
def inspect_queries(expected,observed,count):
    with expected.open(encoding='utf-8') as source,observed.open(encoding='utf-8') as actual:
        assert actual.readline().rstrip('\n')=='id\treference\tstart\tend\texpected_mapped_names\tobserved_mapped_names\tmatch'
        checked=0
        for line in source:
            original=line.rstrip('\n').split('\t');row=actual.readline().rstrip('\n').split('\t')
            assert len(original)==5 and len(row)==7 and row[:5]==original and row[5]==original[4] and row[6]=='true'
            checked+=1
        assert not actual.read() and checked==count
def assess(base):
    first=load(base/'wide-originals-1/RESULTS.json');second=load(base/'wide-originals-2/RESULTS.json');second_complete=load(base/'wide-originals-2/COMPLETED.json')
    assert len(first['results'])==4 and len(first['failures'])==1 and first['failures'][0]['case']=='cross_bgzf_members' and not (base/'wide-originals-1/COMPLETED.json').exists()
    assert not second['failures'] and len(second['results'])==1 and second_complete['cases']==['cross_bgzf_members']
    assert len(second_complete['original_score_overflow_refusals'])==2
    assert 'Conversion positive overflow' in (base/'wide-originals-1/cross_bgzf_members/0.6.6/mark/original-mark.stderr').read_text()
    observations=[];queries=0;metadata_failures=0;source_failures=0
    for folder,rows in [('wide-originals-1',first['results']),('wide-originals-2',second['results'])]:
        for case in rows:
            for row in case['results']:
                producer=row['producer'];name=case['case'];directory=base/folder/name
                if 'result' not in row:
                    edition,action=producer.split('-');where=directory/edition/action
                    process=load(where/('original-'+action+'.process.json'))
                    assert process['exit_code']==1 and 'Conversion positive overflow' in (where/('original-'+action+'.stderr')).read_text()
                    assert row['status']=='FAIL2_ORIGINAL_066_SCORE_OVERFLOW_NO_ACCEPTED_ANALYSIS' and not (where/'AUDIT.properties').exists()
                    source_failures+=1;continue
                if producer=='native-java-input':where=directory/'native-input';bam=base/'wide-fixtures-1'/name/'input.bam';bai=where/'cli-output/alignment.bai'
                elif producer.endswith('-direct-index'):
                    where=directory/producer.split('-')[0]/'direct-index';bam=base/'wide-fixtures-1'/name/'input.bam';bai=where/'original.bai'
                elif producer.startswith('native-java-after-'):
                    edition,action=producer.removeprefix('native-java-after-').split('-');where=directory/edition/action/'native-index';bam=where.parent/'alignment.bam';bai=where/'fresh.bai'
                else:
                    edition,action=producer.split('-');where=directory/edition/action;bam=where/'alignment.bam';bai=Path(str(bam)+'.bai')
                result=row['result'];assert pin(bam)==result['bam'] and pin(bai)==result['bai']
                assert properties(where/'AUDIT.properties')==result['audit'] and properties(where/'QUERIES.properties')==result['queries']
                audit=result['audit']
                if audit['status']=='FAIL2_NO_ACCEPTED_BAI_AUDIT':
                    assert name=='wide_unplaced_tail' and producer.startswith('0.6.6-') and 'metadata reference extent' in audit['error'];metadata_failures+=1
                else:
                    assert audit['status']=='PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT' and audit['bam_sha256']==result['bam']['sha256'] and audit['bai_sha256']==result['bai']['sha256']
                count=int(result['queries']['queries']);assert count==case['manifest']['queries']
                inspect_queries(base/'wide-fixtures-1'/name/'queries.tsv',where/'queries-observed.tsv',count);queries+=count
                observations.append(dict(case=name,producer=producer,audit_status=audit['status'],queries=count))
    assert len(observations)==51 and metadata_failures==3 and source_failures==2 and queries==30070
    adversarial=load(base/'adversarial-1/COMPLETED.json');assert len(adversarial['results'])==13
    assert sum(row['audit']['status']=='PASS_NATIVE_COMPLETE_BAM_BAI_AUDIT' for row in adversarial['results'])==4
    linear=next(row for row in adversarial['results'] if row['name']=='linear_empty_gap_skips_long_alignment')
    assert 'linear index can skip' in linear['audit']['error'] and linear['indexed_queries']['status']=='FAIL2_INDEXED_QUERY_EXPECTATIONS_DIFFER'
    assert (base/'adversarial-1/linear_empty_gap_skips_long_alignment/queries-observed.tsv').read_text().splitlines()[1].endswith('\tlong_gap\t\tfalse')
    large=load(base/'large-indexes-1/COMPLETED.json');assert len(large['results'])==9
    for row in large['results']:
        report=row['report'];assert report['status']=='PASS_LARGE_BAI_AUDIT_SEQUENTIAL_AND_INDEXED_EXACT_EXPECTATIONS'
        assert int(report['maximum_java_heap_bytes'])<=64<<20 and report['audited_records']==report['sequential_records_checked']==report['indexed_records_checked']
    assert sum(row['report']['audited_records']=='4000000' for row in large['results'])==5
    assert sum(int(row['report']['audited_records']) for row in large['results'])==20_000_008
    java=load(base/'java-validation-2/VALIDATION.json');assert java['totals']==dict(tests=14,failures=0,errors=0,skipped=0)
    assert load(base/'java-validation-2/TERMINAL-OBSERVATION.json')['exit_code']==0
    expected=large['candidate'];assert second_complete['candidate']==expected==adversarial['candidate']
    for folder in ['wide-originals-1','wide-originals-2','large-indexes-1','adversarial-1']:assert pin(base/folder/'candidate.jar')==expected
    return dict(status='PASS_ALL_RECORDED_COORDINATE_QUERY_SCALE_AND_FAILURE_CONTROLS',candidate=expected,wide_pairs=51,wide_audits_accepted=48,original_metadata_refusals=3,original_score_overflow_refusals=2,
        independent_wide_mapped_queries=queries,adversarial_controls=13,legal_variants_accepted=4,corrupt_variants_refused=9,large_checks=9,four_million_record_checks=5,
        records_checked_in_each_large_pass=20_000_008,actual_large_heap_bytes=64<<20,focused_java_tests=java['totals'],wide_observations=observations,
        complete_original_marking_on_066_high_score_input=False,arbitrary_downstream_consumer_qualified=False,existing_workflow_BAI_receipts_promoted=False,new_catalog_credit=0,whole_goal_complete=False)
def freeze(base,large_originals):
    if EVIDENCE.exists():raise FileExistsError('Refuse existing evidence; retain original snapshots')
    assessment=assess(base);files=inventory(base);own={}
    for path in Path(__file__).parent.iterdir():
        if path.is_file():own[path.relative_to(ROOT).as_posix()]=pin(path)
    for name in ['src/main/java/org/jlinalg/alignment/BamBaiAudit.java','src/main/java/org/jlinalg/alignment/BamIndexStream.java','src/main/java/org/jlinalg/alignment/SambambaStreamingBamAudit.java',
                 'src/test/java/org/jlinalg/alignment/BamBaiWideAuditTest.java','src/test/resources/org/jlinalg/alignment/bai-wide-original-native.zip','docs/literature/BAM_BAI_QUERY_QUALIFICATION.md']:
        own[name]=pin(ROOT/name)
    fixture=ROOT/'src/test/resources/org/jlinalg/alignment/bai-wide-original-native.zip'
    assert pin(fixture)==dict(bytes=135830,sha256='39c2a275c9366faf45fb70b22ec33a7d4b7c20fb4bb5c86eade04cdfdd27ec0c')
    with zipfile.ZipFile(fixture) as z:manifest=json.loads(z.read('MANIFEST.json'))
    archive_check(fixture,dict(manifest['members'],**{'MANIFEST.json':rawpin(json.dumps(manifest,indent=2).encode()+b'\n')}))
    external={}
    for name,identity in load(base/'large-indexes-1/INPUT-PINS.json').items():
        name=name.replace('\\','/').split('/large-originals-1/',1)[1]
        assert pin(large_originals/name)==identity;external[name]=identity
    EVIDENCE.mkdir();(EVIDENCE/'.gitattributes').write_bytes(b'* -text\n');(EVIDENCE/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+NOTICE+'\n').encode())
    save(EVIDENCE/'SOURCE-PINS.json',own);save(EVIDENCE/'PRIVATE-RECORDS.json',dict(roots=RETAINED,files=files));save(EVIDENCE/'RETAINED-LARGE-INPUTS.json',dict(files=external,these_original_inputs_are_outside_private_base=True))
    save(EVIDENCE/'QUALIFICATION.json',assessment)
    with zipfile.ZipFile(EVIDENCE/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in own:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    archive_check(EVIDENCE/'own-source-snapshot.zip',dict(own,**{'LICENSE-project.txt':pin(ROOT/'LICENSE')}))
    metadata={name:identity for name,identity in files.items() if Path(name).suffix.lower() not in ['.jar','.bam','.bai','.class']}
    save(EVIDENCE/'METADATA-PINS.json',metadata)
    with zipfile.ZipFile(EVIDENCE/'validation-metadata.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in metadata:z.write(base/name,name)
    archive_check(EVIDENCE/'validation-metadata.zip',metadata)
    if files!=inventory(base):raise ValueError('Private records changed during capture')
    save(EVIDENCE/'STATUS.json',dict(status='RECORDED_SCOPED_NATIVE_BAI_COORDINATE_QUERY_AND_SCALE_QUALIFICATION',notice=NOTICE,public_files={p.name:pin(p) for p in EVIDENCE.iterdir() if p.is_file()},
        project_license=pin(ROOT/'LICENSE'),private_files=len(files),private_bytes=sum(row['bytes'] for row in files.values()),public_metadata_members=len(metadata),candidate=assessment['candidate'],
        wide_pairs=51,wide_mapped_queries=30070,large_checks=9,original_metadata_refusals=3,original_score_overflow_refusals=2,whole_current_tree_catalog_qualified=False,new_catalog_credit=0,whole_goal_complete=False))
def verify(base=None,large_originals=None):
    proof=load(EVIDENCE/'STATUS.json');entries=list(EVIDENCE.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in entries) or {p.name for p in entries}!=set(proof['public_files'])|{'STATUS.json'}:raise ValueError('Public evidence membership differs')
    for name,identity in proof['public_files'].items():
        if pin(EVIDENCE/name)!=identity:raise ValueError('Public evidence identity differs: '+name)
    own=load(EVIDENCE/'SOURCE-PINS.json');private=load(EVIDENCE/'PRIVATE-RECORDS.json');metadata=load(EVIDENCE/'METADATA-PINS.json')
    archive_check(EVIDENCE/'own-source-snapshot.zip',dict(own,**{'LICENSE-project.txt':proof['project_license']}));archive_check(EVIDENCE/'validation-metadata.zip',metadata)
    current=all((ROOT/name).is_file() and pin(ROOT/name)==identity for name,identity in own.items());count=0
    if base is not None:
        if private['roots']!=RETAINED or inventory(base)!=private['files']:raise ValueError('Complete named retained records differ')
        count=len(private['files'])
    external=load(EVIDENCE/'RETAINED-LARGE-INPUTS.json')['files'];external_count=0
    if large_originals is not None:
        for name,identity in external.items():
            if pin(large_originals/name)!=identity:raise ValueError('Retained original large input differs: '+name)
            external_count+=1
    return dict(status='PASS_BAI_QUERY_SAVED_RECORD_READBACK',current_project_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,
        complete_named_new_private_records_checked=count,external_original_large_inputs_checked=external_count,fresh_original_analysis_or_scientific_reexecution=False,
        wide_pairs=proof['wide_pairs'],wide_mapped_queries=proof['wide_mapped_queries'],large_checks=proof['large_checks'],original_metadata_refusals=3,original_score_overflow_refusals=2,
        existing_workflow_BAI_receipts_promoted=False,whole_current_tree_catalog_qualified=False,new_catalog_credit=0,whole_goal_complete=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__,epilog=NOTICE);p.add_argument('--private-base',type=Path);p.add_argument('--large-originals',type=Path);p.add_argument('--freeze',action='store_true');a=p.parse_args()
    try:
        base=a.private_base.resolve(strict=True) if a.private_base else None;large=a.large_originals.resolve(strict=True) if a.large_originals else None
        if a.freeze:
            if base is None or large is None:raise ValueError('Freeze requires all actual private and external original large inputs')
            freeze(base,large)
        print(json.dumps(verify(base,large),indent=2),flush=True)
    except Exception as failure:print('FAIL2_BAI_QUERY_EVIDENCE_UNVERIFIED',repr(failure),NOTICE,flush=True);raise SystemExit(2)
