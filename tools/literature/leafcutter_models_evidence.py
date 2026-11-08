# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read all retained original/native model families, lossless bindings and source/runtime records without executing R/Stan."""
import hashlib,io,json,math,re,sys,tarfile,zipfile
from pathlib import Path
from signac_scan_evidence import byte_pin,strict_json,unpack
from validate_leafcutter_models import source_cases,independent_posterior,compare

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/leafcutter-models-development'


class RetainedPath:
    def __init__(self,files,name):self.files=files;self.name=name
    def __truediv__(self,name):return RetainedPath(self.files,self.name+'/'+str(name))
    def read_bytes(self):return self.files[self.name]


def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable complete model evidence checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert proof['status']=='passed-native-LeafCutter-original-model-evaluation-component-only' and all(proof[k] is False for k in ['implementation_credit','full_LeafCutter_workflow_admitted','whole_goal_complete','released_or_deployed']);assert byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as z:assert len(z.namelist())<30000 and sum(i.file_size for i in z.infolist())<134217728
    files=unpack(data/'controls.zip',proof['packing']);obj=lambda n:strict_json(files[n])
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    build=obj('build/COMPLETED.json');validation=obj('validation/COMPLETED.json');original=obj('original/COMPLETED.json');precision=obj('precision/COMPLETED.json');library=obj('library/COMPLETED.json');intake=obj('R-source/COMPLETED.json')
    assert build['status']=='compiled-native-LeafCutter-model-evaluator-not-original-validation' and validation['status']=='PASS_COMPLETE_ORIGINAL_NATIVE_LEAFCUTTER_MODEL_AUDIT_THREE_JVM_ONLY';assert proof['artifact']==build['artifact']==validation['artifact'];assert proof['canonical_executable_unchanged']==build['canonical_executable_unchanged']==validation['canonical_executable_unchanged']
    parent_dir=root/'docs/literature/leafcutter-phenotypes-development';parent=strict_json((parent_dir/'VALIDATION.json').read_bytes());assert build['parent_artifact']==parent['artifact'] and build['unchanged_parent_entries']==20789;assert byte_pin((parent_dir/'controls.zip').read_bytes())==parent['archive'];prior=unpack(parent_dir/'controls.zip',parent['packing'])
    for report in [build,validation,original,precision,library]:
        for name,want in report['source_pins'].items():assert proof['source_pins'][name]==want
    for name,want in build['source_pins'].items():assert byte_pin(files['build/source-snapshot/'+name])==want
    assert set(proof['packaged_entries'])==set(build['added_entries']) and len(build['added_entries'])==10
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want
    assert intake['status']=='PASS_THREE_COMPLETE_ORIGINAL_LEAFCUTTER_R_PACKAGE_TREES_ONLY'
    cluster_dir=root/'docs/literature/leafcutter-clusters-development';cluster_proof=strict_json((cluster_dir/'VALIDATION.json').read_bytes());assert byte_pin((cluster_dir/'controls.zip').read_bytes())==cluster_proof['archive'];cluster_files=unpack(cluster_dir/'controls.zip',cluster_proof['packing'])
    for edition,row in intake['editions'].items():
        home='R-source/'+edition;assert files[home+'/official-source-tree.json']==cluster_files['source-intake/'+edition+'/official-source-tree.json'];tree=obj(home+'/official-source-tree.json');assert tree['truncated'] is False;members={r['path']:r for r in tree['tree'] if r['type']=='blob' and (r['path'].startswith('leafcutter/') or r['path'] in ['README.md','LICENSE'])};assert set(members)==set(row['members'])
        for name,want in row['members'].items():
            raw=files[home+'/source/'+name];assert byte_pin(raw)=={k:want[k] for k in ['bytes','sha256']} and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==members[name]['sha']==want['git_blob_sha1'],name
    current=intake['editions']['source-2c9907ef66ad'];profile=obj('current-source/src/main/resources/org/jlinalg/omics/leafcutter-models/SOURCE-PROFILE.json');assert profile['revision']==current['revision']==library['source_revision']=='2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb' and profile['R_package_version']==current['R_package_version']==library['R_package_version']=='0.2.9'
    for name,want in profile['source_members'].items():assert want==current['members'][name]
    stage=obj('library/original-source-stage.json');assert byte_pin(files['library/original-source-stage.json'])==library['source_stage']
    for name,want in stage['members'].items():assert byte_pin(files['library-stage-source/'+name])==want=={k:current['members']['leafcutter/'+name][k] for k in ['bytes','sha256']}
    assert library['status']=='PASS_ORIGINAL_LEAFCUTTER_PRIVATE_R_LIBRARY_AND_LITERAL_FIT_ONLY' and library['namespace_patched'] is False and library['dependency_runtime_unchanged'] is True;assert len(library['health']['namespace_functions_checked'])==24 and library['health']['fit_null_return_code']==library['health']['fit_full_return_code']==0
    assert byte_pin(files['library/library-inventory.json'])==library['library_inventory'] and byte_pin(files['library/Makevars'])==library['compiler_flags']
    for process in library['processes']:assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    addons=obj('R-addons/COMPLETED.json');compatibility=obj('R-compatibility/COMPLETED.json');assert addons['status']=='PASS_EXACT_CRAN_TAILRANK_SOURCE_ONLY' and compatibility['status']=='PASS_EXACT_R42_COMPATIBLE_BIOBASE_OOMPADATA_SOURCES_ONLY'
    packages=[('R-addons',addons,'3.2.6'),*[( 'R-compatibility/'+name,row,{'Biobase':'2.58.0','oompaData':'3.1.3'}[name]) for name,row in compatibility['packages'].items()]];archives={}
    for home,row,version in packages:
        assert row['version']==version;archive=row['archive'];raw=files[home+'/'+archive['filename']];assert byte_pin(raw)=={k:archive[k] for k in ['bytes','sha256']}
        if row['published_md5'] is not None:assert hashlib.md5(raw).hexdigest()==row['published_md5']
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as bundle:
            members={m.name:m for m in bundle.getmembers() if m.isfile()};assert set(members)==set(row['source_members'])
            for name,want in row['source_members'].items():assert byte_pin(bundle.extractfile(members[name]).read())==byte_pin(files[home+'/source/'+name])==want
        archives[archive['filename']]=byte_pin(raw)
    installed_archives=obj('library/addon-archives.json');assert {name.rsplit('/',1)[1]:want for name,want in installed_archives.items()}==archives
    runtime=strict_json(prior['runtime/runtime-before.json']);lib=obj('library/library-inventory.json')
    for home in ['original','precision']:
        assert obj(home+'/runtime-before.json')==obj(home+'/runtime-after.json')==runtime and obj(home+'/library-before.json')==obj(home+'/library-after.json')==lib
        process=obj(home+'/original.process.json');assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    assert obj('library/dependency-before.json')==obj('library/dependency-after.json')==runtime
    assert precision['original_reference']['path'].endswith('model-original-2') and {k:precision['original_reference'][k] for k in ['bytes','sha256']}==byte_pin(files['original/COMPLETED.json'])
    assert precision['reference_unchanged'] and precision['fits_refitted'] is False and precision['lossless_numeric_roundtrip'] is True and obj('precision/ORIGINAL-COMPLETED.json')['fits_refitted'] is False
    reference_inventory=obj('precision/reference-before.json');assert reference_inventory==obj('precision/reference-after.json')
    for name,row in reference_inventory.items():
        if row['kind']=='file':assert byte_pin(files['original/'+name])=={k:row[k] for k in ['bytes','sha256']}
        else:assert row==dict(kind='directory')
    assert files['precision/cases.json']==files['original/cases.json']
    cases,expected,original_checks=source_cases(RetainedPath(files,'precision'));assert original_checks==proof['original_checks']==validation['original_checks'];assert cases==obj('validation/cases.json')['cases'] and expected==obj('validation/expected.json')['cases'] and len(cases)==56
    values=obj('validation/independent-Python-objectives.json');assert len(values)==validation['independent_Python_objectives']==36
    for case,want in zip(cases,expected):
        if case['kind']=='objective':assert math.isclose(independent_posterior(case),want['objective'],rel_tol=2e-10,abs_tol=2e-8)
    errors=[];checks={}
    for jvm in ['windows21','windows25','linux21']:
        version=(files['validation/'+jvm+'.version.stdout']+files['validation/'+jvm+'.version.stderr']).decode('utf8');assert version==validation['jvm_versions'][jvm] and re.search(r'version "'+('25' if jvm=='windows25' else '21')+r'(?:\.|\")',version);assert obj('validation/'+jvm+'.process.json')['exit_code']==0
        report=obj('validation/'+jvm+'/COMPLETED.json');assert report['status']=='PASS_NATIVE_LEAFCUTTER_MODEL_EVALUATIONS_ONLY' and report['cases']==56 and len(set(report['expected_native_rejections']))==14 and report['negative_source_objective_difference_retained'] is True
        count=dict(cases=56,numeric_values=0,exact_values=0,maximum_absolute_error=0.0,expected_native_rejections=14);compare(dict(cases=expected),obj('validation/'+jvm+'/results.json'),jvm,errors,count);assert count==validation['checks'][jvm]==proof['checks'][jvm];checks[jvm]=count
    assert errors==obj('validation/complete-mismatches.json')==[]
    assert obj('history/original-1/COMPLETED.json')['status']=='FAIL2_ORIGINAL_LEAFCUTTER_MODEL_CONTROLS_INCOMPLETE' and obj('history/original-1/original.process.json')['exit_code']!=0
    for home in ['build-1','build-2']:assert obj('history/'+home+'/compile.json')['exit_code']!=0
    assert obj('history/validation-1/windows21.process.json')['exit_code']!=0
    mismatches=obj('history/validation-2/complete-mismatches.json');assert len(mismatches)==6 and all(r['path'].endswith('/p') for r in mismatches);assert all(obj('history/validation-2/'+jvm+'.process.json')['exit_code']==0 for jvm in checks)
    test=obj('comparison-tests/process.json');assert test['exit_code']==0 and test['source_pin']==proof['source_pins']['tools/literature/test_leafcutter_models_comparison.py'] and b'Ran 11 tests' in files['comparison-tests/stderr'] and b'OK' in files['comparison-tests/stderr']
    result=dict(status='passed-retained-native-LeafCutter-model-evaluation-component-evidence',checks=checks,original_checks=original_checks,logical_files=len(files),implementation_credit=False,full_LeafCutter_workflow_admitted=False,whole_goal_complete=False)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    try:verify()
    except Exception as failure:
        print('LEAFCUTTER MODEL EVIDENCE FAIL2: '+repr(failure)+'\nRequired action: preserve original binary fits, complete source/native outputs, precision bindings, warnings and all failures. Correct the named mismatch and retry NEW paths; never refit unchanged models to fix serialization, widen tolerances, omit a field/case or refresh frozen scientific hashes. Full LeafCutter remains missing.',file=sys.stderr);raise SystemExit(2)
