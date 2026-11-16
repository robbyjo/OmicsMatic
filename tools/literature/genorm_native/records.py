#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze once or read back saved native geNorm development evidence; no tool credit."""
from pathlib import Path
import argparse,hashlib,json,xml.etree.ElementTree as ET,zipfile

MODULE=Path(__file__).resolve().parent;ROOT=MODULE.parents[2]
EVIDENCE=ROOT/'docs/literature/genorm-native-development'
OWN=['src/main/java/org/jlinalg/expression/GeNorm.java','src/main/java/org/jlinalg/cli/GeNormCli.java',
 'src/test/java/org/jlinalg/expression/GeNormTest.java','src/test/java/org/jlinalg/cli/GeNormCliTest.java',
 'src/test/resources/org/jlinalg/expression/genorm-normqpcr158-reference.zip',
 'tools/literature/genorm_native/.gitattributes','tools/literature/genorm_native/reference.R',
 'tools/literature/genorm_native/prepare_reference.py','tools/literature/genorm_native/records.py',
 'docs/literature/GENORM_NATIVE.md','examples/genorm-native/references.tsv','examples/genorm-native/targets.tsv']
RETAINED=['intake-1','reference-1','source-role-1','prerequisite-controls-1','java-validation-1',
 'java-validation-2','java-validation-3','java25-validation-1','packaged-1']
META=['docs/literature/omics-2023-2026/'+n for n in ['README.md','WARNINGS.md','papers.json','summary.json','tool-inventory.json']]+[
 'site/literature-review.html','site/literature-warnings.html','tools/literature/reviewed-uses.json']
NOTICE='P040 input/Cq wording and reported software identity remain unresolved; no paper-specific credit. Native complete log-space calculations only. Read GENORM_NATIVE.md. Saved readback is not a fresh computation, full package qualification or binary authenticity proof. Preserve failures and historical evidence; the full250-paper goal remains open.'
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(p):return rawpin(Path(p).read_bytes())
def load(p):return json.loads(Path(p).read_bytes())
def save(p,value):
    with Path(p).open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2,sort_keys=True);f.write('\n')
def inventory(base):
    result={}
    for name in RETAINED:
        root=base/name
        if not root.is_dir() or root.is_symlink():raise ValueError('Missing or aliased retained directory: '+name)
        for p in sorted(root.rglob('*')):
            if p.is_symlink():raise ValueError('Unqualified private alias: '+str(p))
            if p.is_file():result[p.relative_to(base).as_posix()]=pin(p)
    return result
def tests(path):
    roots=[ET.parse(p).getroot() for p in path.glob('TEST-*.xml')]
    total={k:sum(int(s.get(k,0)) for s in roots) for k in ['tests','failures','errors','skipped']}
    if len(roots)!=2 or total!=dict(tests=15,failures=0,errors=0,skipped=0):raise ValueError('Complete focused test result differs: '+str(total))
    return dict(suites=len(roots),**total)
def freeze(base):
    if EVIDENCE.exists():raise FileExistsError('Preserve the frozen checkpoint; use a separately versioned qualification')
    for name in ['java-validation-3','java25-validation-1']:
        if load(base/name/'process.json')['exit_code']!=0:raise ValueError('Actual test process failed: '+name)
    sources=load(base/'java-validation-3/SOURCE-PINS.json')
    if any(pin(ROOT/n)!=v for n,v in sources.items()):raise ValueError('Tested scientific/test sources differ')
    summaries=dict(windows_java21=tests(ROOT/'build/test-results/test'),windows_java25=tests(base/'java25-validation-1/test-results'))
    if 'GENORM_TEST_JAVA=C:\\Program Files\\Java\\jdk-25\\bin\\java.exe' not in (base/'java25-validation-1/gradle.stdout').read_text():
        raise ValueError('Actual Java25 test launcher was not recorded')
    prior=load(base/'java-validation-1/process.json');failed=load(base/'java-validation-1/FAILURE-REVIEW.json')
    if prior['exit_code']!=1 or failed['failures']!=1:raise ValueError('Initial test-reader failure was lost')
    packed=load(base/'packaged-1/COMPLETED.json')
    if any(c['exit_code'] for c in packed['checks']) or len(packed['classes'])!=7:raise ValueError('Packaged real executions failed')
    if packed['candidate']!=pin(base/'packaged-1/omicsmatic-genorm-development.jar'):raise ValueError('Actual packaged JAR differs')
    role=load(base/'source-role-1/COMPLETED.json');skips=load(base/'prerequisite-controls-1/COMPLETED.json')
    if role['paragraph_sha256']!='d238ac4b738fa1f191f17e07d2ca2e864ff33a7398309989e5f3b0684ca73516' or role['new_catalog_credit']!=0:raise ValueError('Primary role/uncertainty differs')
    if len(skips['checks'])!=2 or any(x['exit_code']!=3 for x in skips['checks']):raise ValueError('Actual missing-prerequisite skips differ')
    fixture=ROOT/OWN[4]
    if pin(fixture)['sha256']!=failed['original_fixture_sha256']:raise ValueError('The original failed fixture was replaced')
    with zipfile.ZipFile(fixture) as z:
        reference=json.loads(z.read('provenance.json'));cases=z.read('case-index.txt').decode().splitlines()
        if reference!=load(base/'reference-1/PROVENANCE.json') or pin(MODULE/'reference.R')!=reference['project_reference_code']:
            raise ValueError('Actual original reference provenance or executed project caller differs')
        for name,identity in reference['private_files'].items():
            if pin(base/'reference-1'/name)!=identity:raise ValueError('Original private source/return/condition evidence differs: '+name)
        numeric=sum(len(z.read(case+'/'+name).decode().splitlines())-1 for case in cases for name in ['stability.tsv','meanM.tsv','variation.tsv','normalization.tsv'])
        rankings=sum(len(z.read(case+'/ranking.tsv').decode().splitlines())-1 for case in cases)
        if len(cases)!=45 or reference['archive']['sha256']!='f556aab9ae67d7d02deda192df6d1adf65520b0c9125797582a5b74839864a35':raise ValueError('Exact original reference scope differs')
        for name,identity in reference['public_files'].items():
            if rawpin(z.read(name))!=identity:raise ValueError('Complete public reference fixture differs: '+name)
    # Preserve the actual final Java21 reports before any later Gradle test run.
    with zipfile.ZipFile(base/'java-validation-3/final-source-and-reports.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
        for n in sources:z.write(ROOT/n,n)
        for directory in ['build/test-results/test','build/reports/tests/test']:
            for p in sorted((ROOT/directory).rglob('*')):
                if p.is_file():z.write(p,p.relative_to(ROOT).as_posix())
    EVIDENCE.mkdir(parents=True,exist_ok=False)
    (EVIDENCE/'.gitattributes').write_text('* -text\n',encoding='utf-8')
    (EVIDENCE/'README.md').write_text('# Native geNorm development evidence\n\n'+NOTICE+'\n\n[Usage and full scope](../GENORM_NATIVE.md). Run `python -B tools/literature/genorm_native/records.py` for public readback, optionally with `--private-base build/genorm-native` for all retained local records. No original NormqPCR source/function objects or installed upstream software are redistributed here.\n',encoding='utf-8')
    (EVIDENCE/'WARNINGS.md').write_text('# Warnings and required actions\n\n'+NOTICE+'\n\nThe first 13-test attempt had one fixture-scale reader failure caused by CRLF handling; it was retained before correction. The original numeric fixture bytes remain unchanged. The literal original NormqPCR quantity-product overflow and all non-finite values remain retained separately.\n',encoding='utf-8')
    ownpins={n:pin(ROOT/n) for n in OWN};save(EVIDENCE/'SOURCE-PINS.json',ownpins)
    with zipfile.ZipFile(EVIDENCE/'own-source-snapshot.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
        for n in OWN:z.write(ROOT/n,n)
        z.write(ROOT/'LICENSE','LICENSE-project.txt')
    save(EVIDENCE/'PRIVATE-RECORDS.json',dict(roots=RETAINED,files=inventory(base)))
    save(EVIDENCE/'TESTS.json',dict(**summaries,original_matrix_controls=45,complete_numeric_values_per_reference_run=numeric,complete_ranking_positions_per_reference_run=rankings,tolerance='5e-12 * max(1, abs(expected)); rankings and column identities exact',java21_actual_test_task_executed=True,java25_actual_test_task_executed=True,java25_compile_tasks_up_to_date=True))
    save(EVIDENCE/'PRIMARY-ROLE.json',role);save(EVIDENCE/'INITIAL-FAILURE.json',failed);save(EVIDENCE/'PACKAGED.json',packed)
    save(EVIDENCE/'CATALOG-METADATA-PINS.json',{n:pin(ROOT/n) for n in META})
    save(EVIDENCE/'REFERENCE.json',dict(archive=reference['archive'],url=reference['archive_url'],original_source_files=reference['source_files'],fixture=pin(fixture),cases=cases,original_selectHKs_cases=44,original_stabMeasureM_two_reference_cases=1,two_reference_ranking_and_factor_are_equation_derived=True,original_function_bodies_modified=False,original_log_true_matrix_definition_only=True,full_package_installed=False,literal_quantity_branch_equivalence_claimed=False))
    public={p.name:pin(p) for p in EVIDENCE.iterdir() if p.is_file()}
    save(EVIDENCE/'QUALIFICATION.json',dict(status='PASS_NATIVE_GENORM_LOG2_DEVELOPMENT_PROFILE',public_files=public,private_files=len(load(EVIDENCE/'PRIVATE-RECORDS.json')['files']),license=pin(ROOT/'LICENSE'),original_controls=45,java_tests_each=15,new_catalog_credit=0,paper_input_representation_resolved=False,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False))
def verify(base=None):
    proof=load(EVIDENCE/'QUALIFICATION.json')
    if any(p.is_symlink() or not p.is_file() for p in EVIDENCE.iterdir()):raise ValueError('Public evidence must contain only regular files')
    if {p.name for p in EVIDENCE.iterdir() if p.is_file()}!=set(proof['public_files'])|{'QUALIFICATION.json'}:raise ValueError('Complete public evidence membership differs')
    for name,identity in proof['public_files'].items():
        if pin(EVIDENCE/name)!=identity:raise ValueError('Frozen public evidence differs: '+name)
    sources=load(EVIDENCE/'SOURCE-PINS.json')
    with zipfile.ZipFile(EVIDENCE/'own-source-snapshot.zip') as z:
        if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(sources)|{'LICENSE-project.txt'}:raise ValueError('Own-source archive membership differs')
        for name,identity in sources.items():
            if rawpin(z.read(name))!=identity:raise ValueError('Own-source archive differs: '+name)
        if rawpin(z.read('LICENSE-project.txt'))!=proof['license']:raise ValueError('Own-source license differs')
    current=all((ROOT/n).is_file() and pin(ROOT/n)==v for n,v in sources.items())
    count=0
    if base is not None:
        private=load(EVIDENCE/'PRIVATE-RECORDS.json')
        if private['roots']!=RETAINED or inventory(base)!=private['files']:raise ValueError('Complete saved private records differ')
        count=len(private['files'])
        if count!=proof['private_files']:raise ValueError('Private record count differs')
    return dict(status='PASS_COMPLETE_SAVED_GENORM_PRIVATE_RECORD_READBACK' if base else 'PASS_GENORM_PUBLIC_METADATA_AND_OWN_SOURCE_READBACK',current_project_sources_match_snapshot=current,source_snapshot_historical_if_current_differs=not current,private_files_read=count,scientific_fits_repeated=False,original_runtime_reexecuted=False,upstream_binary_authenticity_established=False,new_catalog_credit=0,whole_goal_complete=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__,epilog=NOTICE);p.add_argument('--freeze',action='store_true');p.add_argument('--private-base',type=Path);a=p.parse_args()
    try:
        if a.freeze:
            if a.private_base is None:raise ValueError('--freeze requires all actual saved private records')
            freeze(a.private_base.resolve(strict=True))
        print(json.dumps(verify(a.private_base.resolve(strict=True) if a.private_base is not None else None),indent=2),flush=True)
    except Exception as e:
        print('FAIL2_GENORM_SAVED_RECORDS_UNVERIFIED',repr(e),NOTICE,flush=True);raise SystemExit(2)
