# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen GC controls, real sampler populations, source pins, examples and unchanged parent/catalog."""
import json,re,zipfile
from pathlib import Path
from signac_scan_evidence import unpack
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';EVIDENCE=ROOT/'docs/literature/chromvar-gc-development'
def read(p):return json.loads(p.read_bytes())
def main():
    target=BASE/'background-gc-final-integrity-1.json';assert not target.exists();record=read(EVIDENCE/'VALIDATION.json')
    assert record['status']=='passed-native-chromVAR-GC-and-original-background-intake-development' and pin(EVIDENCE/'controls.zip')==record['archive'] and pin(EVIDENCE/'WARNINGS.md')==record['warnings_document'] and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    files=unpack(EVIDENCE/'controls.zip',record);assert len(files)==record['logical_files'] and all(files['current-source/'+n]==(ROOT/n).read_bytes() for n in record['source_pins'])
    prefix='build/literature-review/signac-runtime/'
    def archived(n):return json.loads(files[prefix+n])
    native=archived('background-gc-controls-1/COMPLETED.json');examples=archived('background-gc-example-2/COMPLETED.json');sampler=archived('background-original-controls-1/COMPLETED.json')
    assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==49 and v['skipped']==0 for v in native['platform_validation'])
    original=archived('background-gc-controls-1/fixture/original.json');cases=archived('background-gc-controls-1/fixture/cases.json');assert len(cases['cases'])==22 and len(cases['ranges'])==21 and len(original['profiles'])==2
    assert original['profiles']['1.16.0']==original['profiles']['1.34.1']
    capture=read(ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-gc/CAPTURE.json')
    for name,value in capture['files'].items():assert pin(ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-gc'/name)==value
    assert len(examples['runs'])==3 and examples['existing_output_refusals']==3
    for run in examples['runs']:
        assert run['source_formula_values_match'] and run['warnings_present'] and run['existing_output_preserved']
        for name,value in run['inventory'].items():assert pin(ROOT/run['analysis_path']/name)==value
        assert b'UNDEFINED_GC_BIAS' in files[run['analysis_path']+'/WARNINGS.md']
    failure=archived('background-gc-example-1/FAILURE.json');assert failure['status']=='FAILED_CHECKED_NATIVE_GC_EXAMPLES' and b'InvalidPathException' in files[prefix+'background-gc-example-1/windows-java25.stderr']
    assert archived('background-gc-example-1/windows-java21.process.json')['exit_code']==0 and archived('background-gc-example-1/windows-java21-existing-output-refused.process.json')['exit_code']==1
    background_cases=archived('background-original-controls-1/cases.json');background_original=archived('background-original-controls-1/original.json');assert len(background_cases['cases'])==15 and len(background_original['results'])==45
    successes=failures=0
    for case in background_cases['cases']:
        rows=[v for v in background_original['results'] if v['id']==case['id']];assert len(rows)==3 and {v['profile'] for v in rows}=={'1.16.0-source','1.34.1-package','1.34.1-public-Matrix-interface'}
        values=[{k:v for k,v in row.items() if k!='profile'} for row in rows];assert values[0]==values[1]==values[2]
        row=rows[0];assert len(row['rng_before'])==len(row['rng_after'])==626
        if row['ok']:
            successes+=1;assert row['columns']==case['niterations'] and row['rows']==len(background_cases['datasets'][case['dataset']]['counts']) and all(1<=int(v)<=row['rows'] for r in row['background'] for v in r)
        else:failures+=1;assert row['error']
    assert (successes,failures)==(9,6) and sampler['all_source_public_backgrounds_and_RNG_states_identical']
    resource=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-background'
    for n in ['utils.cpp','get_background_peaks_core.R']:assert (resource/'1.16.0'/n).read_bytes()==(resource/'1.34.1'/n).read_bytes()
    parent=ROOT/'docs/literature/chromvar-deviations-development';prior=read(parent/'VALIDATION.json');assert pin(parent/'VALIDATION.json')==record['parent_validation'] and pin(parent/'controls.zip')==record['parent_archive'] and len(prior['source_pins'])==437 and all(pin(ROOT/n)==v for n,v in prior['source_pins'].items())
    for a in [record['artifact'],record['parent_artifact']]:assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(ROOT/record['parent_artifact']['path']) as a,zipfile.ZipFile(ROOT/record['artifact']['path']) as b:assert all(a.read(n)==b.read(n) for n in a.namelist()) and len(set(b.namelist())-set(a.namelist()))==4
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_preserved'];catalog=json.loads(files['unchanged-catalog.json']);assert len(catalog)==402
    for n,v in catalog.items():assert pin(ROOT/'docs/literature/omics-2023-2026'/n)==v
    assert not any(record[k] for k in ['end_user_background_adapter_available','independent_native_RNG_replay','installed_DLL_source_authentication','historical_whole_packages_executed','BSgenome_container_methods_executed','full_Signac_workflow_available','root_dispatch_available','catalog_implementation_credit','released','pushed','deployed','whole_goal_complete'])
    links=0
    for doc in [ROOT/'docs/literature/CHROMVAR_GC_DEVELOPMENT.md',ROOT/'examples/chromvar-gc-bias/README.md']:
        for link in re.findall(r'\]\(([^)]+)\)',doc.read_text(encoding='utf8')):
            if not re.match(r'^[a-zA-Z]+:',link):assert (doc.parent/link.split('#',1)[0]).resolve().exists();links+=1
    result=dict(status='passed-independent-chromVAR-GC-background-intake-integrity',archive=record['archive'],source_pins=len(record['source_pins']),native_tests_per_platform=49,platforms=3,original_sequence_populations=22,indexed_reference_ranges=21,checked_example_runs=3,existing_output_refusals=3,retained_Java25_path_failure=True,original_background_protocols=45,background_successful_populations=9,background_retained_source_failures=6,exact_RNG_states_match=True,parent_437_source_pins_unchanged=True,catalog_checksums=402,new_integrations=68,including_existing=71,missing=259,canonical_preserved=True,local_document_links_checked=links,end_user_background_adapter_available=False,pushed=False,whole_goal_complete=False)
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent GC milestone verification')
    main()
