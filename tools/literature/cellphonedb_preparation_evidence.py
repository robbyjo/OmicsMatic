# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Re-read exact native preparation and complete original-method references without executing Python analysis, pickles or an external runtime."""
import io,json,sys,zipfile
from pathlib import Path
from types import SimpleNamespace
from signac_scan_evidence import byte_pin,strict_json,unpack
from validate_cellphonedb_preparation import source_tables,verify_ledgers
from validate_cellphonedb_statistics import compare

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/cellphonedb-preparation-development'


class RetainedPath:
    def __init__(self,files,name):self.files=files;self.name=name
    def __truediv__(self,name):return RetainedPath(self.files,self.name+'/'+str(name))
    def read_bytes(self):return self.files[self.name]
    def open(self,mode):
        assert mode=='rb';return io.BytesIO(self.read_bytes())
    def stat(self):return SimpleNamespace(st_size=len(self.read_bytes()))


def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Never disable complete preparation evidence checks')
    proof=strict_json((data/'VALIDATION.json').read_bytes());assert proof['status']=='passed-exact-native-CellPhoneDB-preparation-and-original-full-method-references-only' and all(proof[k] is False for k in ['implementation_credit','public_execution_adapter_admitted','whole_goal_complete','released_or_deployed']);assert byte_pin((data/'controls.zip').read_bytes())==proof['archive']
    with zipfile.ZipFile(data/'controls.zip') as archive:assert len(archive.namelist())<4000 and sum(i.file_size for i in archive.infolist())<134217728
    files=unpack(data/'controls.zip',proof['packing']);obj=lambda name:strict_json(files[name])
    for name,want in proof['source_pins'].items():assert byte_pin((root/name).read_bytes())==byte_pin(files['current-source/'+name])==want,name
    build=obj('build/COMPLETED.json');validation=obj('validation/COMPLETED.json');original=obj('original/COMPLETED.json');fixtures=obj('fixtures/COMPLETED.json');pandas=obj('pandas-source/COMPLETED.json')
    parent=strict_json((root/'docs/literature/cellphonedb-statistics-development/VALIDATION.json').read_bytes());assert build['status']=='PASS_COMPILED_CELLPHONEDB_PREPARATION_ONLY_NOT_SCIENTIFIC_VALIDATION' and validation['status']=='PASS_EXACT_ORIGINAL_NATIVE_CELLPHONEDB_PREPARATION_THREE_JVMS_ONLY';assert build['artifact']==validation['artifact']==proof['artifact'] and build['canonical_executable_unchanged']==validation['canonical_executable_unchanged']==proof['canonical_executable_unchanged'];assert build['parent_artifact']==proof['parent_artifact']==parent['artifact'] and build['unchanged_parent_entries']==20915
    for report in [build,validation,pandas]:
        for name,want in report['source_pins'].items():assert proof['source_pins'][name]==want
    for name,want in build['source_pins'].items():assert byte_pin(files['build/source-snapshot/'+name])==want
    assert set(proof['packaged_entries'])==set(build['added_entries']) and len(build['added_entries'])==19
    for name,want in proof['packaged_entries'].items():assert byte_pin(files['candidate-added-entries/'+name])==want
    assert pandas['status']=='PASS_EXACT_PANDAS_223_GROUP_MEAN_SOURCE_ONLY' and pandas['version']=='2.2.3';profile=obj('current-source/src/main/resources/org/jlinalg/omics/cellphonedb-preparation/SOURCE-PROFILE.json');assert profile['pandas_revision']==pandas['revision'] and profile['pandas_source_members']==pandas['source_members'] and profile['pandas_version']=='2.2.3' and profile['CellPhoneDB_version']=='5.0.1' and profile['curated_database_included'] is False
    for name,want in pandas['source_members'].items():assert byte_pin(files['pandas-source/source/'+name])=={k:want[k] for k in ['bytes','sha256']}
    assert files['pandas-source/source/LICENSE']==files['current-source/src/main/resources/org/jlinalg/omics/cellphonedb-preparation/PANDAS-BSD-3.txt'] and b'BSD 3-Clause License' in files['pandas-source/source/LICENSE'];assert byte_pin((root/'src/main/resources/org/jlinalg/omics/cellphonedb-statistics/SOURCE-PROFILE.json').read_bytes())==profile['CellPhoneDB_source_profile']
    assert fixtures['status']=='PASS_NINETEEN_SYNTHETIC_CELLPHONEDB_FULL_WORKFLOW_INPUT_CONTROLS_ONLY' and fixtures['cases']==19 and fixtures['synthetic_database_variants']==3 and fixtures['curated_database_included'] is False
    for name,want in fixtures['files'].items():assert byte_pin(files['fixtures/'+name])==want
    for variant,home in [('synthetic-cellphonedb.zip','synthetic-database-tables'),('synthetic-alias-cellphonedb.zip','synthetic-alias-database-tables'),('synthetic-noncoincident-id-cellphonedb.zip','synthetic-noncoincident-database-tables')]:
        with zipfile.ZipFile(io.BytesIO(files['fixtures/'+variant])) as archive:
            assert len(archive.namelist())==len(set(archive.namelist()))==8
            for name in archive.namelist():assert archive.read(name)==files['fixtures/'+home+'/'+name]
    assert byte_pin(files['original/COMPLETED.json'])==validation['reference_manifest'];assert original['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_FULL_WORKFLOW_CONTROLS_ONLY' and original['runtime_unchanged'] and obj('original/runtime-before.json')==obj('original/runtime-after.json');process=obj('original/original.process.json');assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
    assert byte_pin(files['original/runtime-before.json'])==parent['packing']['logical_file_pins']['setup/runtime-after.json'] and byte_pin(files['original/bound-input-4-COMPLETED.json'])==parent['packing']['logical_file_pins']['setup/COMPLETED.json'] and byte_pin(files['original/bound-input-5-COMPLETED.json'])==parent['packing']['logical_file_pins']['source-intake/COMPLETED.json']
    assert byte_pin(files['original/original-results.json'])==original['results'] and byte_pin(files['original/native-input.json'])==original['native_input'];assert files['original/native-input.json']==files['validation/cases.json'];assert files['original/bound-input-0-collect_cellphonedb_workflows_linux.py']==files['current-source/tools/literature/collect_cellphonedb_workflows_linux.py'] and files['original/bound-input-2-cases.json']==files['fixtures/cases.json'] and files['original/bound-input-3-COMPLETED.json']==files['fixtures/COMPLETED.json']
    for name,want in original['source_pins'].items():
        prefix='/mnt/d/projects/JLinAlg/';assert name.startswith(prefix)
        if name[len(prefix):].startswith('tools/'):assert proof['source_pins'][name[len(prefix):]]==want
    request,expected,original_checks=source_tables(RetainedPath(files,'original'));assert expected==obj('validation/expected.json') and original_checks==proof['original_checks']==validation['original_checks'];checks={};ledgers={}
    for jvm in ['windows21','windows25','linux21']:
        report=obj('validation/'+jvm+'/COMPLETED.json');assert report['status']=='PASS_NATIVE_CELLPHONEDB_TYPED_PREPARATION_ONLY' and report['cases']==15 and report['defensive_copy_controls'] and len(set(report['expected_native_rejections']))==15;assert report['original_full_workflow_error_controls']==[c['id'] for c in request['cases'] if 'expected_original_error' in c] and obj('validation/'+jvm+'.process.json')['exit_code']==0;count=dict(cases=15,numeric_values=0,exact_values=0,native_rejections=15,operations=report['operations']);errors=[];compare(expected,obj('validation/'+jvm+'/results.json'),jvm,errors,count);assert not errors and count==proof['checks'][jvm]==validation['checks'][jvm];checks[jvm]=count;ledgers[jvm]=verify_ledgers(request,obj('validation/'+jvm+'/ledgers.json'));assert ledgers[jvm]==proof['ledger_checks'][jvm]==validation['ledger_checks'][jvm]
    assert obj('validation/complete-mismatches.json')==[] and validation['exact_finite_numeric_comparison'] and validation['public_execution_adapter_admitted'] is False
    # Every previous atomic collection failed; its actual source, provisional files and diagnostics remain retained.
    for i in range(1,6):
        marker=obj('history/original-'+str(i)+'/COMPLETED.json');assert marker['status']=='FAIL2_CELLPHONEDB_ORIGINAL_CONTROLS_FAILED' and marker['runtime_unchanged'] and obj('history/original-'+str(i)+'/original.process.json')['exit_code']!=0 and 'history/original-'+str(i)+'/ORIGINAL-COMPLETED.json' not in files
    assert obj('comparison-tests/process.json')['exit_code']==0 and b'Ran 7 tests' in files['comparison-tests/stderr'] and b'OK' in files['comparison-tests/stderr']
    database=obj('database-provenance/COMPLETED.json');assert database['status']=='PASS_EXACT_OFFICIAL_CELLPHONEDB_DATABASE_CAPTURE_ONLY' and database['revision']=='71ffa8a163389913907d40993bec2821a111f64d' and database['repository_license_metadata'] is None and database['license_files']==[] and database['public_database_bundling_admitted'] is False and database['MIT_software_grant_inferred_for_database'] is False;assert not any(n.startswith('database-provenance/source/') or n.startswith('database-provenance/database-tables/') for n in files)
    result=dict(status='passed-retained-native-CellPhoneDB-preparation-and-full-original-reference-evidence',checks=checks,ledger_checks=ledgers,original_checks=original_checks,logical_files=len(files),implementation_credit=False,public_execution_adapter_admitted=False,whole_goal_complete=False)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    try:verify()
    except Exception as error:
        print('CELLPHONEDB PREPARATION EVIDENCE FAIL2: '+repr(error)+'\nRequired action: preserve the full source/alias/cast/output families and failed attempts, correct the named cause and retry NEW paths. Do not patch the original package, change a frozen scientific hash, widen numeric comparison or count a reference collector as a completed tool adapter.',file=sys.stderr);raise SystemExit(2)
