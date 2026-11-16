#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze once or read saved streaming-v2 evidence; saved readback never launches analysis."""
from pathlib import Path
import argparse,hashlib,json,xml.etree.ElementTree as ET,zipfile
from run_large import pin,save

ROOT=Path(__file__).resolve().parents[3]
EVIDENCE=ROOT/'docs/literature/sambamba-streaming-workflow-development'
RETAINED=['PREVIOUS.json','SOURCE-DERIVATION.json','TEMPFILE-RACE-REVIEW.json',
 'previous-qualified-candidate.jar','previous-integrated-java28-reports.zip',
 'java-validation-1','java-validation-2','java-validation-3','java-validation-4','java-validation-5',
 'large-originals-1','workflow-cases-1','large-workflow-1','workflow-cases-2','large-workflow-2',
 'postbuild-1','postbuild-2','platform-controls-1','windows25-large-1']
NOTICE=('Development streaming-v2 original-runtime profile only. Require genuine runtime replay; metadata/checksums alone are insufficient. '
 'Large raw BAMs, full decision ledgers, original executables and candidate JARs are not in the public metadata archive. '
 'Named complete raw records are retained locally and can be checked with --private-base. Negative controls deliberately contain incorrect '
 'bundles and invalidated receipts. Expected SKIP3/FAIL2 controls and historical failures are retained, not accepted analyses. '
 'Supply named permitted prerequisites or skip dependent analysis. No independent BAI, whole-tool, paper-environment or biological '
 'qualification or new catalog credit. Current-tree catalog qualification and the full250-paper objective remain open. '
 'Read SAMBAMBA_STREAMING_WORKFLOW.md for bounds, setup, all20 fields and recovery.')

def load(path):return json.loads(path.read_bytes())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def inventory(base):
    files={}
    for name in RETAINED:
        root=base/name
        if root.is_symlink() or not root.exists():raise ValueError('Missing/aliased retained root: '+name)
        for path in ([root] if root.is_file() else sorted(root.rglob('*'))):
            if path.is_symlink():raise ValueError('Retained alias is unqualified: '+str(path))
            if path.is_file():files[path.relative_to(base).as_posix()]=pin(path)
    return files
def classes(jar,prefixes):
    with zipfile.ZipFile(jar) as z:return {n:rawpin(z.read(n)) for n in z.namelist() if n.startswith(prefixes) and n.endswith('.class')}
def archive_check(path,identities,extra=None):
    expected={**identities,**(extra or {})}
    with zipfile.ZipFile(path) as z:
        if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(expected):raise ValueError('Complete archive membership differs: '+str(path))
        for name,identity in expected.items():
            if rawpin(z.read(name))!=identity:raise ValueError('Archived bytes differ: '+name)
def preserved(jar=None):
    folder=ROOT/'docs/literature/sambamba-execution-development';proof=load(folder/'VALIDATION.json')
    for name,identity in proof['tested_sources'].items():
        if pin(ROOT/name)!=identity:raise ValueError('Frozen v1 source/test changed: '+name)
    for name,identity in load(ROOT/'docs/literature/sambamba-workflow-development/CATALOG-METADATA-PINS.json').items():
        if pin(ROOT/name)!=identity:raise ValueError('Catalog metadata changed: '+name)
    if jar is not None:
        expected=load(folder/'IMPLEMENTATION-IDENTITIES.json')
        if classes(jar,('org/jlinalg/cli/SambambaWorkflow','org/jlinalg/alignment/SambambaBamAudit'))!=expected:raise ValueError('Actual preserved v1 implementation classes changed')
    return dict(v1_source_pins=proof['tested_sources'],catalog_metadata_unchanged=True,v1_actual_classes_unchanged=jar is not None)

def freeze(base,jar):
    if EVIDENCE.exists():raise FileExistsError('Preserve the frozen checkpoint; use a separately versioned qualification')
    historical=preserved(jar);java=load(base/'java-validation-5/PROCESS.json')
    if java['exit_code']!=0:raise ValueError('Final actual Java test/package command failed')
    for name,identity in load(base/'java-validation-5/SOURCE-PINS.json').items():
        if pin(ROOT/name)!=identity:raise ValueError('Final tested source differs: '+name)
    suites=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
    totals={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
    if len(suites)!=4 or totals!=dict(tests=27,failures=0,errors=0,skipped=0):raise ValueError('Actual complete focused test results differ: '+str(totals))
    matrix=load(base/'workflow-cases-2/COMPLETED.json');large=load(base/'large-workflow-2/COMPLETED.json')
    component=load(base/'large-originals-1/COMPLETED.json');post=load(base/'postbuild-2/COMPLETED.json');platform=load(base/'platform-controls-1/COMPLETED.json')
    windows25=load(base/'windows25-large-1/PROCESS.json');candidate=pin(jar)
    if matrix['case_count']!=32 or matrix['genuine_original_replay_count']!=32 or matrix['baseline_BAM_comparisons']!=96:raise ValueError('Complete final fixture matrix missing')
    if any(r[k]['exit_code'] for r in matrix['checks'] for k in ['analysis','verification']):raise ValueError('Original fixture execution failed')
    if large['large_analysis_bundles']!=2 or large['large_genuine_replays']!=2 or len(large['checks'])!=4:raise ValueError('Both complete large workflow/replay pairs are required')
    for row in large['checks']:
        if row['exit_code']!=0 or row['independently_checked_ledger_records']!=4000000 or row['requested_java_heap_mb']!=64:raise ValueError('A complete final large workflow/ledger failed')
    if component['input']['records']!=4000000 or component['input']['decoded_bytes']!=308000201 or any(r['exit_code'] for r in component['checks']):raise ValueError('Original large component evidence differs')
    if len(post['adversarial']['checks'])!=15 or len(post['process_guards']['checks'])!=5 or any(r['exit_code'] for r in post['checks']):raise ValueError('Complete postbuild controls missing')
    if any(r['exit_code']!=r['expected_exit_code'] for r in platform['checks']) or windows25['exit_code']!=0:raise ValueError('Platform/legacy/JDK25 component check failed')
    for p in [matrix,large,post]:
        if p['candidate']!=candidate:raise ValueError('Recorded final candidate differs')
    library_prefix=('org/jlinalg/alignment/SambambaStreamingBamAudit',)
    if classes(jar,library_prefix)!=classes(base/'large-originals-1/candidate.jar',library_prefix):raise ValueError('Large component implementation differs from final workflow')
    failed=load(base/'workflow-cases-1/CASE-RESULTS.json')
    if len(failed['checks'])!=31 or len(failed['failures'])!=1 or (base/'workflow-cases-1/COMPLETED.json').exists():raise ValueError('Original genuine transient race refusal was lost')
    if load(base/'java-validation-4/PROCESS.json')['exit_code']!=1:raise ValueError('Windows observation test failure was not retained')
    if load(base/'postbuild-1/MESSAGE-EXPECTATION-REVIEW.json')['production_implementation_changed'] is not False:raise ValueError('Specific validator expectation failure review unavailable')
    files=inventory(base);own={}
    for folder in ['src/main/java/org/jlinalg/alignment','src/main/java/org/jlinalg/cli','src/test/java/org/jlinalg/alignment','src/test/java/org/jlinalg/cli']:
        for p in (ROOT/folder).glob('SambambaStreaming*.java'):own[p.relative_to(ROOT).as_posix()]=pin(p)
    for p in Path(__file__).parent.iterdir():
        if p.is_file():own[p.relative_to(ROOT).as_posix()]=pin(p)
    for name in ['docs/literature/SAMBAMBA_STREAMING_WORKFLOW.md','examples/sambamba-streaming/request.properties',
                 'src/test/resources/org/jlinalg/alignment/sambamba-original-markdup-066-082.zip',
                 'tools/literature/sambamba_validation/make_cases.py','tools/literature/sambamba_execution_validation/CompareAll.java']:
        own[name]=pin(ROOT/name)
    EVIDENCE.mkdir();(EVIDENCE/'.gitattributes').write_bytes(b'* -text\n')
    (EVIDENCE/'WARNINGS.md').write_bytes(('# Warnings and required actions\n\n'+NOTICE+'\n').encode())
    save(EVIDENCE/'SOURCE-PINS.json',own);save(EVIDENCE/'PRIVATE-RECORDS.json',dict(roots=RETAINED,files=files))
    save(EVIDENCE/'IMPLEMENTATION-IDENTITIES.json',classes(jar,('org/jlinalg/cli/SambambaStreamingWorkflow','org/jlinalg/alignment/SambambaStreamingBamAudit')))
    with zipfile.ZipFile(EVIDENCE/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in own:z.write(ROOT/name,name)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    archive_check(EVIDENCE/'own-source-snapshot.zip',own,{'LICENSE-project.txt':pin(ROOT/'LICENSE')})
    # Public metadata is explicitly distinct from complete retained raw evidence.
    included={n:i for n,i in files.items() if Path(n).suffix.lower() not in ['.jar','.bam','.bai','.class'] and Path(n).name!='decisions.tsv'}
    for folder in ['test-results/test','reports/tests/test']:
        for p in (ROOT/'build'/folder).rglob('*'):
            if p.is_file():included['final-java-reports/'+p.relative_to(ROOT/'build').as_posix()]=pin(p)
    save(EVIDENCE/'METADATA-ARCHIVE-PINS.json',included)
    with zipfile.ZipFile(EVIDENCE/'validation-metadata.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in included:
            p=ROOT/'build'/name.removeprefix('final-java-reports/') if name.startswith('final-java-reports/') else base/name
            z.write(p,name)
    archive_check(EVIDENCE/'validation-metadata.zip',included)
    if inventory(base)!=files:raise ValueError('Complete private records changed during capture')
    save(EVIDENCE/'VALIDATION.json',dict(candidate=candidate,java_tests=dict(suites=len(suites),**totals),java_process=java,
        final_fixture_matrix=matrix,large_final_workflows=large,large_original_component=component,postbuild=post,platform=platform,
        windows25_component=windows25,preserved=historical,initial_temporary_race=load(base/'TEMPFILE-RACE-REVIEW.json'),
        windows_observation_failure_retained=True,validator_expectation_failure=load(base/'postbuild-1/MESSAGE-EXPECTATION-REVIEW.json'),
        public_metadata_is_complete_private_raw_data=False,BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False))
    public={p.name:pin(p) for p in EVIDENCE.iterdir() if p.is_file()}
    save(EVIDENCE/'STATUS.json',dict(status='PASS_SCOPED_ORIGINAL_SAMBAMBA_STREAMING_V2_WORKFLOW_CONTROLS',public_files=public,
        private_files=len(files),private_total_bytes=sum(v['bytes'] for v in files.values()),project_license=pin(ROOT/'LICENSE'),
        candidate=candidate,notice=NOTICE,public_metadata_archive_members=len(included),raw_large_data_publicly_packaged=False,
        workflow_large_record_capacity_implemented=True,largest_actual_records_per_BAM=4000000,
        catalog_source_review_pending=True,BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False))

def verify(base=None):
    proof=load(EVIDENCE/'STATUS.json');entries=list(EVIDENCE.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in entries) or {p.name for p in entries}!=set(proof['public_files'])|{'STATUS.json'}:raise ValueError('Complete public checkpoint membership differs')
    for name,identity in proof['public_files'].items():
        if pin(EVIDENCE/name)!=identity:raise ValueError('Public checkpoint changed: '+name)
    own=load(EVIDENCE/'SOURCE-PINS.json');archive_check(EVIDENCE/'own-source-snapshot.zip',own,{'LICENSE-project.txt':proof['project_license']})
    archive_check(EVIDENCE/'validation-metadata.zip',load(EVIDENCE/'METADATA-ARCHIVE-PINS.json'))
    current=all((ROOT/n).is_file() and pin(ROOT/n)==identity for n,identity in own.items());preserved();count=0
    if base is not None:
        actual=inventory(base)
        if actual!=load(EVIDENCE/'PRIVATE-RECORDS.json')['files'] or len(actual)!=proof['private_files']:raise ValueError('Complete named raw private records differ')
        count=len(actual)
    return dict(status='PASS_COMPLETE_PRIVATE_STREAMING_V2_SAVED_READBACK' if base else 'PASS_PUBLIC_STREAMING_V2_METADATA_AND_SOURCE_READBACK',
        current_project_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,
        complete_private_raw_files_checked=count,public_readback_checks_large_raw_data=False,
        fresh_scientific_execution_performed=False,largest_recorded_records_per_BAM=proof['largest_actual_records_per_BAM'],
        BAI_independently_qualified=False,new_catalog_credit=0,whole_goal_complete=False)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--freeze',action='store_true');p.add_argument('--private-base',type=Path);p.add_argument('--jar',type=Path);a=p.parse_args()
    try:
        base=a.private_base.resolve(strict=True) if a.private_base else None
        if a.freeze:
            if base is None or a.jar is None:raise ValueError('Freeze requires --private-base and --jar')
            freeze(base,a.jar.resolve(strict=True))
        elif a.jar is not None:raise ValueError('--jar is only for explicit one-time freezing')
        print(json.dumps(verify(base),indent=2),flush=True)
    except Exception as e:print('FAIL2_STREAMING_V2_EVIDENCE_UNVERIFIED',repr(e),NOTICE,flush=True);raise SystemExit(2)
