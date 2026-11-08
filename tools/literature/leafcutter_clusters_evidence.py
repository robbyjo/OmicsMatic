# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck every retained native/source cluster/count/state/order and current source; execute no LeafCutter runtime."""
import gzip,hashlib,json,re,sys,zipfile
from pathlib import Path
from signac_scan_evidence import byte_pin,strict_json,unpack
from validate_leafcutter_clusters import source_result,compare,audit_counts

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/leafcutter-clusters-development'

class RetainedPath:
    def __init__(self,files,name):self.files=files;self.name=name
    def __truediv__(self,name):return RetainedPath(self.files,self.name+'/'+str(name))
    def read_bytes(self):return self.files[self.name]
    def read_text(self,encoding='utf8'):return self.read_bytes().decode(encoding)

def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable complete retained evidence checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert proof['status']=='passed-native-LeafCutter-RegTools-clustering-counts-component-only' and proof['implementation_credit'] is False and proof['full_LeafCutter_workflow_admitted'] is False and proof['whole_goal_complete'] is False and byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as z:assert len(z.namelist())<30000 and sum(i.file_size for i in z.infolist())<134217728
    files=unpack(data/'controls.zip',proof['packing']);obj=lambda n:strict_json(files[n])
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    build=obj('build/COMPLETED.json');validation=obj('validation/COMPLETED.json');intake=obj('source-intake/COMPLETED.json');assert build['status']=='compiled-native-LeafCutter-clusters-not-original-validation' and validation['status']=='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_THREE_JVM_ONLY' and intake['status']=='PASS_THREE_EXACT_ORIGINAL_LEAFCUTTER_SOURCE_EDITIONS_ONLY';assert proof['artifact']==build['artifact']==validation['artifact'] and proof['canonical_executable_unchanged']==build['canonical_executable_unchanged']==validation['canonical_executable_unchanged'];assert build['unchanged_parent_entries']>20000 and build['parent_artifact']==json.loads((root/'docs/literature/hail-workflow-development/VALIDATION.json').read_bytes())['artifact']
    for name,want in [*build['source_pins'].items(),*validation['source_pins'].items()]:assert proof['source_pins'][name]==want
    for name,want in build['source_pins'].items():assert byte_pin(files['build/source-snapshot/'+name])==want
    assert set(build['added_entries'])==set(proof['packaged_entries']) and len(build['added_entries'])==19
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want
    versions={}
    for edition,profile in intake['editions'].items():
        home='source-intake/'+edition;commit=obj(home+'/official-git-commit.json');tree=obj(home+'/official-source-tree.json');assert commit['sha']==profile['revision'] and commit['tree']['sha']==tree['sha'] and tree['truncated'] is False;members={r['path']:r for r in tree['tree'] if r['type']=='blob'}
        for name,want in profile['members'].items():
            raw=files[home+'/source/'+name];assert byte_pin(raw)=={k:want[k] for k in ['bytes','sha256']} and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==members[name]['sha']==want['git_blob_sha1'],name
        if edition.startswith('v'):
            ref=obj(home+'/official-git-ref.json');assert ref['ref']=='refs/tags/'+edition;item=ref['object'];item=obj(home+'/official-annotated-tag.json')['object'] if item['type']=='tag' else item;assert item['type']=='commit' and item['sha']==profile['revision']
        description=files[home+'/source/leafcutter/DESCRIPTION'].decode('utf8');assert re.search(r'^Version:\s*(.+)$',description,re.M)[1].strip()==profile['R_package_version'];versions[edition]=profile['R_package_version'];assert profile['R_package_license']=='GPL (>= 3)' and b'Apache License' in files[home+'/source/LICENSE']
    assert versions=={'v0.2.6':'0.2.6','v0.2.7':'0.2.6','source-2c9907ef66ad':'0.2.9'};assert not any(t['name'] in ['v0.2.9','0.2.9'] for t in obj('source-intake/official-tags.json'));source=intake['editions']['source-2c9907ef66ad']['members']['clustering/leafcutter_cluster_regtools.py'];profile=obj('current-source/src/main/resources/org/jlinalg/omics/leafcutter-clusters/SOURCE-PROFILE.json');assert profile['source']==source and profile['revision']=='2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb' and profile['full_LeafCutter_workflow_admitted'] is False
    fixture=obj('reference/windows/cases.json');assert files['reference/windows/cases.json']==files['reference/linux/cases.json'] and len(fixture['cases'])==88
    for platform in ['windows','linux']:
        report=obj('reference/'+platform+'/COMPLETED.json');assert report['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_CLUSTER_CONTROLS_ONLY' and report['namespace_patched'] is False and report['analysis_installed_software'] is False and len(report['cases'])==88 and report['original']['sha256']==source['sha256'];assert byte_pin(files['reference/'+platform+'/cases.json'])==report['fixtures']
        for row in report['cases']:
            home='reference/'+platform+'/'+row['id'];process=obj(home+'/original.process.json');assert process['exit_code']==0 and process['owned_live_after_cleanup']==[] and '-I' in process['command'] and 'utf8' in process['command']
            for name,pins in row['output_families'].items():
                raw=files[home+'/original/'+name];decoded=files[home+'/'+name+'.decoded'];assert byte_pin(raw)==pins['raw'] and byte_pin(decoded)==pins['decoded'] and (gzip.decompress(raw) if name.endswith('.gz') else raw)==decoded
    errors=[];checks={};expected=dict(cases=88,original_source_rows=5456,independent_count_conservation_values=3718,expected_native_rejections=17,retained_output_rows=602)
    for jvm in ['windows21','windows25','linux21']:
        version=(files['validation/'+jvm+'.version.stdout']+files['validation/'+jvm+'.version.stderr']).decode('utf8');assert version==validation['jvm_versions'][jvm] and re.search(r'version "'+('25' if jvm=='windows25' else '21')+r'(?:\.|\")',version);assert obj('validation/'+jvm+'.process.json')['exit_code']==0;native_report=obj('validation/'+jvm+'/COMPLETED.json');assert native_report['status']=='PASS_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_CASES_ONLY' and len(native_report['cases'])==88 and len(set(native_report['expected_native_rejections']))==17;counts=dict(cases=88,original_source_rows=0,independent_count_conservation_values=0,expected_native_rejections=17,retained_output_rows=0)
        for case in fixture['cases']:
            original=source_result(RetainedPath(files,'reference/windows/'+case['id']));other=source_result(RetainedPath(files,'reference/linux/'+case['id']));native=obj('validation/'+jvm+'/'+case['id']+'.json');compare(original,other,'platforms/'+case['id'],errors);compare(original,{k:native[k] for k in ['samples','pooled','refined','rows']},jvm+'/'+case['id'],errors)
            for key,value in audit_counts(case,native).items():counts[key]+=value
            counts['retained_output_rows']+=len(native['rows'])
        assert counts==validation['checks'][jvm]==proof['checks'][jvm]==expected;checks[jvm]=counts
    assert errors==obj('validation/complete-mismatches.json')==[];assert obj('history/source-intake-1/COMPLETED.json')['status']=='SKIP3_LEAFCUTTER_ORIGINAL_SOURCE_UNAVAILABLE' and obj('history/build-1/compile.json')['exit_code']!=0 and obj('history/validation-1/COMPLETED.json')['status']=='FAIL2_NATIVE_LEAFCUTTER_CLUSTER_COMPONENT_VALIDATION_INCOMPLETE';assert obj('comparison-tests/process.json')['exit_code']==0 and b'Ran 10 tests' in files['comparison-tests/stderr'] and b'OK' in files['comparison-tests/stderr'];assert obj('comparison-tests/process.json')['source_pin']==proof['source_pins']['tools/literature/test_leafcutter_cluster_comparison.py']
    result=dict(status='passed-retained-native-LeafCutter-cluster-component-evidence',artifact=proof['artifact'],checks=checks,logical_files=len(files),implementation_credit=False,full_LeafCutter_workflow_admitted=False,whole_goal_complete=False,scope='Complete original/native synthetic clustering/count comparison with exact types, integer values and order; no original runtime execution by this reader, R/Stan model, phenotype/PCA, junction extraction, cohort calibration or historical/whole-paper parity.')
    if emit:print(json.dumps(result,indent=2))
    return result

if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('LEAFCUTTER CLUSTER EVIDENCE FAIL2: '+repr(failure)+'\nRequired action: keep all original/native counts, source, failures and partials. Correct the named mismatch and repeat into NEW paths. Do not omit a case/field, repair counts or refresh a saved scientific hash. Full LeafCutter remains missing.',file=sys.stderr);raise SystemExit(2)
