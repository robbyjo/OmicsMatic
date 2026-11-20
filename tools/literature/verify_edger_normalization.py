# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read saved native edgeR qualification without R/Git/JVM execution or refitting."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,sys,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/edger-normalization-checks'
STATUS='PASS_SCOPED_NATIVE_EDGER_NORMALIZATION_FIVE_SOURCE_EDITIONS'
ACTION=('SKIP3: restore the named exact permitted source/evidence or keep dependent work skipped. '
        'FAIL2: no complete result; retain changed evidence and diagnostics, resolve the cause and use NEW output. '
        'Do not refresh older hashes. Saved readback does not refit models, authenticate the host or qualify whole packages/papers.')
def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def safe(name):
    p=PurePosixPath(name);require(name and not p.is_absolute() and '..' not in p.parts and '\\' not in name and ':' not in name,'Unsafe evidence path');return name
def verify(folder=FOLDER,jar=None):
    folder=Path(folder);q=json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(q['schema_version']==1 and q['status']==STATUS and not q['whole_goal_complete'],'Different native qualification')
    for name,wanted in q['sources'].items():require(pin((ROOT/safe(name)).read_bytes())==wanted,'Changed qualified source: '+name)
    packed=(folder/'evidence.zip').read_bytes();require(pin(packed)==q['archive'],'Changed evidence ZIP')
    with zipfile.ZipFile(io.BytesIO(packed)) as z:
        require(len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(q['payloads']),'Different evidence inventory')
        require(sum(i.file_size for i in z.infolist())<=128*1024*1024,'Evidence exceeds readback capacity');raw={safe(n):z.read(n) for n in z.namelist()}
    for name,wanted in q['payloads'].items():require(pin(raw[name])==wanted,'Changed evidence payload: '+name)
    suites_count={}
    for host in ['windows21','windows25','linux21']:
        suites=[ET.fromstring(v) for n,v in raw.items() if n.startswith('normal-tests-2/reports-'+host+'/') and n.endswith('.xml')]
        total={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','errors','failures','skipped']}
        require(total==dict(tests=5165,errors=0,failures=0,skipped=0),'Different normal-JAR JVM disposition');suites_count[host]=total
    models=json.loads(raw['normal-tests-2/COMPLETED.json']);require(models['tests_per_JVM']==5165 and not models['production_class_overlay'] and models['producer']==q['producer'],'Different actual producer qualification')
    payloads=json.loads(raw['normal-tests-2/JAR-PAYLOADS.json']);require(payloads['previous_count']==23460 and payloads['current_count']==23471 and payloads['all_previous_payloads_unchanged'],'Different previous producer member scope')
    require(len(payloads['previous_payloads'])==23460 and len(payloads['additions'])==11,'Different decoded JAR inventory')
    for name,wanted in payloads['additions'].items():require(pin(raw['producer/new-members/'+safe(name)])==wanted,'Changed new class/notice payload')
    build=json.loads(raw['workflow-gradle-2/PROCESS-EXIT.json']);require(build['exit_code']==0,'Normal Gradle build did not pass')
    reference=json.loads(raw['reference-2/original/COMPLETED.json']);require(reference['normalizations']==2350 and reference['transforms']==552 and reference['current_namespace_functions_equal_original_source'],'Different original references')
    untouched=json.loads(raw['reference-2/ORIGINALS-UNCHANGED.json']);require(untouched['status']=='PASS_ORIGINAL_INPUTS_RETURNS_CONDITIONS_AND_RNG_IDENTICAL' and untouched['normalizations']==2350 and untouched['transforms']==552,'Original model/input preservation not established')
    historical=json.loads(raw['historical-cpm-3/COMPLETED.json']);require(historical['original_returns']==2208 and not historical['inference_run'] and not historical['author_hosts_reproduced'],'Different historical scope')
    for version in ['3.32.1','3.36.0','3.40.2','3.42.4']:
        row=historical['results'][version];kind='original-kernel' if version in ['3.32.1','3.36.0'] else 'package'
        require(row['provider']==kind and row['result']['provider']==kind and row['result']['transforms']==552 and not row['result']['original_source_modified'],'Different actual historical provider')
        require(row['result']['stock_package_namespace_loaded']==(kind=='package'),'Kernel provider falsely represented as stock package')
        if kind=='original-kernel':
            skip=json.loads(raw['runtime-'+version+'-1/SKIP3.json']);require(skip['exit_code']!=0 and not skip['analysis_run'],'Whole-package skip was not retained')
    examples=json.loads(raw['examples-1/COMPLETED.json']);require(examples['pipelines']==2 and examples['actual_native_replays']==1 and examples['native_R_absent_from_PATH'] and examples['maximum_absolute_error']==0,'Different actual composed examples')
    require(all(p['exit_code']==p['expected'] for p in examples['processes']),'Example process disposition differs')
    aliases=json.loads(raw['alias-controls-1/COMPLETED.json']);require(len(aliases['controls'])==3 and all(c['exit_code']==2 and c['parent_before_exit']==c['parent_after_exit']==0 and not c['output_created'] for c in aliases['controls']),'Replay alias protection differs')
    cross=json.loads(raw['cross-host-verify-1/COMPLETED.json']);require(cross['captures']==25 and set(cross['results'])=={'windows25','linux21'} and all(p['exit_code']==0 for rows in cross['results'].values() for p in rows),'Different exact cross-host recomputation')
    prior=json.loads(raw['predecessor-1/COMPLETED.json']);require(prior['historical_commit']=='ae5c476b435edf72838dfa288b336c92975d7c41' and prior['manifest_entries']==820 and prior['canonical_files_unchanged']==15 and not prior['source_overlay'] and all(r['exit_code']==0 for r in prior['records'].values()),'Different unchanged predecessor audit')
    for attempt,count in [('preflight-1',65),('preflight-2',25)]:
        suites=[ET.fromstring(v) for n,v in raw.items() if n.startswith(attempt+'/reports/') and n.endswith('.xml')]
        require(sum(int(s.get('failures',0)) for s in suites)==count,'Earlier numerical/serialization failure disposition not retained')
    census=json.loads((folder/'CENSUS.json').read_bytes());require(pin((folder/'CENSUS.json').read_bytes())==q['census'],'Changed source census')
    require(census['papers_scanned']==250 and len(census['source_identities'])==250 and census['positive_papers']==17 and census['existing_observations']==16 and census['proposed_additions']==['P088'],'Different source population or role proposal')
    require(not census['canonical_metadata_modified'] and census['new_family_credit']==census['new_canonical_review_records']==0,'Source census assigned unadmitted credit')
    for name in ['reference-20261120.zip','reference-20261120-v2.zip','historical-cpm-20261120.zip']:
        fixture=ROOT/'src/test/resources/org/jlinalg/differential/edger-normalization'/name;require(pin(fixture.read_bytes())==q['fixtures'][name],'Changed original fixture')
    jar_result=None
    if jar is not None:
        expected={**payloads['previous_payloads'],**payloads['additions']}
        with zipfile.ZipFile(jar) as z:
            require(len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(expected),'Different optional producer JAR members')
            for name,wanted in expected.items():require(pin(z.read(name))==wanted,'Different optional producer member: '+name)
        jar_result=dict(status='PASS_EXACT_QUALIFIED_DECODED_JAR_MEMBERS',members=len(expected))
    numeric={host:dict(current=models['results'][host]['numerics'],historical=models['results'][host]['historical_numerics']) for host in suites_count}
    return dict(status='PASS_SAVED_NATIVE_EDGER_NORMALIZATION_SOURCE_NUMERICS_AND_FILE_WORKFLOW',tests_per_JVM=5165,JVMs=3,
        original_normalizations=2350,current_original_transforms=552,historical_original_transforms=2208,
        previous_JAR_members_unchanged=23460,new_JAR_members=11,cross_host_captures=25,composed_original_examples=2,example_maximum_numeric_difference=0,
        historical_full_package_SKIPs=['3.32.1','3.36.0'],census_positive_roles=17,proposed_observations=['P088'],new_canonical_credit=0,
        numeric_reports=numeric,optional_producer=jar_result,fresh_R_models_run=False,fresh_JVM_execution=False,whole_tool_qualified=False,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--packet',type=Path,default=FOLDER);p.add_argument('--jar',type=Path);a=p.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');a.out.mkdir(parents=True,exist_ok=False);owned=True
        result=verify(a.packet,a.jar);(a.out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n');print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2;value=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),complete_result=False,required_action=ACTION)
        if owned:(a.out/'FAILURE.json').write_text(json.dumps(value,indent=2)+'\n')
        print(value['status']+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
