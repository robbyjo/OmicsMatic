# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read complete retained CellPhoneDB5.0.1 prepared-kernel evidence without an external runtime; no named-tool admission."""
import io,json,sys,zipfile
from pathlib import Path
from signac_scan_evidence import byte_pin,strict_json,unpack
from validate_cellphonedb_statistics import compare,independent

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/cellphonedb-statistics-development'


class RetainedPath:
    def __init__(self,files,name):self.files=files;self.name=name
    def __truediv__(self,name):return RetainedPath(self.files,self.name+'/'+str(name))
    def read_bytes(self):return self.files[self.name]


def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable complete component evidence checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert proof['status']=='passed-exact-native-CellPhoneDB-prepared-components-only' and all(proof[k] is False for k in ['implementation_credit','full_CellPhoneDB_workflow_admitted','whole_goal_complete','released_or_deployed']);assert byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as archive:assert len(archive.namelist())<2000 and sum(i.file_size for i in archive.infolist())<134217728
    files=unpack(data/'controls.zip',proof['packing']);obj=lambda name:strict_json(files[name])
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    build=obj('build/COMPLETED.json');validation=obj('validation/COMPLETED.json');original=obj('original/COMPLETED.json');setup=obj('setup/COMPLETED.json');intake=obj('source-intake/COMPLETED.json')
    assert build['status']=='PASS_COMPILED_CELLPHONEDB_COMPONENTS_ONLY_NOT_SCIENTIFIC_VALIDATION' and validation['status']=='PASS_EXACT_COMPLETE_ORIGINAL_NATIVE_CELLPHONEDB_PREPARED_KERNELS_THREE_JVMS_ONLY' and original['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_PREPARED_KERNEL_CONTROLS_ONLY';assert build['artifact']==validation['artifact']==proof['artifact'];assert build['canonical_executable_unchanged']==validation['canonical_executable_unchanged']==proof['canonical_executable_unchanged'];assert validation['exact_finite_numeric_comparison'] is True
    parent=strict_json((root/'docs/literature/leafcutter-workflow-development/VALIDATION.json').read_bytes());assert build['parent_artifact']==parent['artifact']==proof['parent_artifact'] and build['unchanged_parent_entries']==20896
    for report in [build,validation,setup]:
        for name,want in report['source_pins'].items():assert proof['source_pins'][name]==want
    for name,want in build['source_pins'].items():assert byte_pin(files['build/source-snapshot/'+name])==want
    assert set(proof['packaged_entries'])==set(build['added_entries']) and len(build['added_entries'])==19
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want
    assert intake['status']=='PASS_EXACT_COMPLETE_PUBLISHED_CELLPHONEDB_SOURCE_DISTRIBUTIONS_ONLY';edition=intake['versions']['5.0.1'];assert edition['original_license_body_present'] is True and len(edition['members'])==55;distribution=edition['distribution'];wheel=files['source-intake/5.0.1/'+distribution['filename']];assert byte_pin(wheel)=={k:distribution[k] for k in ['bytes','sha256']};metadata=obj('source-intake/5.0.1/pypi-metadata.json');published=[r for r in metadata['urls'] if r['filename']==distribution['filename']];assert len(published)==1 and published[0]['size']==distribution['bytes'] and published[0]['digests']['sha256']==distribution['sha256'] and published[0]['url']==distribution['url']
    with zipfile.ZipFile(io.BytesIO(wheel)) as archive:
        members=[n for n in archive.namelist() if not n.endswith('/')];assert len(members)==len(set(members)) and set(members)==set(edition['members'])
        for name,want in edition['members'].items():assert byte_pin(archive.read(name))==byte_pin(files['source-intake/5.0.1/source/'+name])==want
    source_profile=obj('current-source/src/main/resources/org/jlinalg/omics/cellphonedb-statistics/SOURCE-PROFILE.json');assert source_profile['version']=='5.0.1' and source_profile['source_members']==edition['members'] and source_profile['distribution']==distribution
    license_names=[n for n in edition['members'] if n.endswith('/LICENSE') or n.endswith('/LICENSE.txt')];assert len(license_names)==1 and files['source-intake/5.0.1/source/'+license_names[0]]==files['current-source/src/main/resources/org/jlinalg/omics/cellphonedb-statistics/MIT-LICENSE.txt'];assert b'MIT License' in files['current-source/src/main/resources/org/jlinalg/omics/cellphonedb-statistics/MIT-LICENSE.txt']
    # The other editions' full source is deliberately not repackaged without their original notice audit.
    for version in ['5.0.0','2.1.4']:assert intake['versions'][version]['original_license_body_present'] is False and not any(n.startswith('source-intake/'+version+'/source/') for n in files)
    assert setup['status']=='PASS_PRIVATE_ORIGINAL_CELLPHONEDB_501_REFERENCE_IMPORT_ONLY' and setup['analysis_executed'] is False and setup['runtime_trees_unchanged'] and setup['shared_installer_unchanged'] and setup['shared_archive_bytes_unchanged'] and setup['original_module_source_unchanged'];assert byte_pin(files['setup/COMPLETED.json'])==original['source_pins']['/mnt/d/projects/JLinAlg/build/cellphonedb-case-sensitive-1/setup-1/COMPLETED.json'];assert len(obj('setup/conda-dependency-locks.json')['packages'])==43 and len(set(r['name'] for r in obj('setup/conda-dependency-locks.json')['packages']))==43
    for process in setup['processes']:assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    assert len(setup['processes'])==7 and obj('original/original.process.json')['exit_code']==0 and obj('original/original.process.json')['owned_live_after_cleanup']==[]
    runtime=obj('setup/runtime-before.json');assert runtime==obj('setup/runtime-after.json')==obj('original/runtime-before.json')==obj('original/runtime-after.json') and original['runtime_unchanged'] is True;assert sum(r['kind']=='directory' for r in runtime.values())==2410 and sum(r['kind']!='directory' for r in runtime.values())==27320
    for name,want in edition['members'].items():
        if name.startswith('cellphonedb/'):record=runtime['lib/python3.11/site-packages/'+name];assert record==dict(kind='file',**want)
    assert byte_pin(files['original/COMPLETED.json'])==validation['reference_manifest'] and byte_pin(files['original/original-results.json'])==original['results'] and byte_pin(files['original/native-input.json'])==original['native_input'];assert files['validation/cases.json']==files['original/native-input.json'] and files['validation/expected.json']==files['original/original-results.json']
    bound=[('collect_cellphonedb_statistics_linux.py','current-source/tools/literature/collect_cellphonedb_statistics_linux.py'),('inventory_hail_runtime_linux.py','current-source/tools/literature/inventory_hail_runtime_linux.py'),('cases.json','history/fixtures-2/cases.json'),('COMPLETED.json','history/fixtures-2/COMPLETED.json'),('COMPLETED.json','setup/COMPLETED.json'),('COMPLETED.json','source-intake/COMPLETED.json')]
    for i,(name,captured) in enumerate(bound):assert files['original/bound-input-'+str(i)+'-'+name]==files[captured]
    assert obj('original/STARTED.json')['source_pins']==original['source_pins']
    for name,want in original['source_pins'].items():
        prefix='/mnt/d/projects/JLinAlg/'
        assert name.startswith(prefix)
        if name[len(prefix):].startswith('tools/'):assert want==proof['source_pins'][name[len(prefix):]]
    original_checks=independent(obj('validation/cases.json'),obj('validation/expected.json'),RetainedPath(files,'original'));assert original_checks==validation['original_checks']==proof['original_checks'];checks={};expected=obj('validation/expected.json')
    for jvm in ['windows21','windows25','linux21']:
        report=obj('validation/'+jvm+'/COMPLETED.json');assert report['status']=='PASS_NATIVE_CELLPHONEDB_PREPARED_KERNELS_ONLY' and report['cases']==13 and report['defensive_copy_controls'] and len(set(report['expected_native_rejections']))==20;assert obj('validation/'+jvm+'.process.json')['exit_code']==0;count=dict(cases=13,numeric_values=0,exact_values=0,native_rejections=20,operations=report['operations']);errors=[];compare(expected,obj('validation/'+jvm+'/results.json'),jvm,errors,count);assert not errors and count==proof['checks'][jvm]==validation['checks'][jvm];checks[jvm]=count
        warnings=obj('validation/'+jvm+'/WARNINGS.json');assert len(warnings)==13 and any('Encoded cluster-pair' in w for w in warnings['encoded-pair-column-collisions']) and any('zero is falsy' in w for w in warnings['zero-cutoff-original-falsy-mask']) and any('Inf/NaN' in w for w in warnings['literal-finite-input-overflow'])
    assert obj('validation/complete-mismatches.json')==[] and b'RuntimeWarning: invalid value encountered in multiply' in files['original/original.stderr'] and b'root' in files['setup/pip-install.stderr']
    assert obj('history/original-1/COMPLETED.json')['status']=='FAIL2_CELLPHONEDB_ORIGINAL_CONTROLS_FAILED' and obj('history/original-1/original.process.json')['exit_code']!=0 and obj('history/original-1/COMPLETED.json')['runtime_unchanged'] is True and 'history/original-1/original-results.json' not in files
    assert obj('history/source-intake-1/COMPLETED.json')['status'].startswith('FAIL2') and b'TypeError' in files['history/original-1/original.stderr'] and b'Passing a dict as an indexer' in files['history/original-1/original.stderr']
    assert obj('comparison-tests/process.json')['exit_code']==0 and b'Ran 10 tests' in files['comparison-tests/stderr'] and b'OK' in files['comparison-tests/stderr']
    drafts=obj('paper-drafts/COMPLETED.json');assert drafts['applied'] is False and drafts['prior_reviews_unchanged']==966 and drafts['cellphonedb_roles']==7 and drafts['new_software_definitions']==5 and drafts['new_supporting_pointers']==4;assert byte_pin(files['current-source/tools/literature/cellphonedb-use-reviews.json'])==drafts['draft_pin'] and byte_pin(files['current-source/tools/literature/cellphonedb-named-software-registry.json'])==drafts['registry_extension']
    result=dict(status='passed-retained-exact-CellPhoneDB-prepared-component-evidence',checks=checks,original_checks=original_checks,logical_files=len(files),implementation_credit=False,full_CellPhoneDB_workflow_admitted=False,whole_goal_complete=False)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete scientific evidence checks')
    try:verify()
    except Exception as error:
        print('CELLPHONEDB COMPONENT EVIDENCE FAIL2: '+repr(error)+'\nRequired action: preserve all original/source/native states and failed attempts, correct the exact cause and retry NEW paths. Do not patch the original package, widen numeric tolerance, refresh frozen source hashes or count the full tool as implemented.',file=sys.stderr);raise SystemExit(2)
