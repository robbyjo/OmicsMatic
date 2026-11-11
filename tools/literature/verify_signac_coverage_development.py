# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline source/archive/original-reference audit for native coverage development."""
import csv
import gzip
import io
import json
import math
import struct
import sys
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

PREFIX='build/literature-review/signac-runtime/'


def table(raw):return list(csv.DictReader(io.StringIO(raw.decode('utf8')),delimiter='\t'))


def main():
    if not __debug__:raise RuntimeError('Never disable assertions with Python -O')
    folder=ROOT/'docs/literature/signac-coverage-development';proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['status']=='passed-standalone-native-Signac-coverage-annotation-development'
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    def obj(name):return json.loads(files[name])
    def record(name):return obj(PREFIX+name)
    for name,wanted in proof['source_pins'].items():assert pin(ROOT/name)==wanted and byte_pin(files['current-source/'+name])==wanted,name
    for name,wanted in proof['parents'].items():
        p=ROOT/('docs/literature/'+name);assert pin(p/'VALIDATION.json')==wanted
        parent=json.loads((p/'VALIDATION.json').read_bytes());assert pin(p/'controls.zip')==parent['archive']
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==proof['canonical_executable_preserved']
    jar=ROOT/'build/literature-review/signac-runtime/coverage-tools-development/omicsmatic-0.3.7-signac-coverage-development.jar'
    if jar.exists():assert pin(jar)==proof['artifact']
    assert proof['catalog_counts']==dict(papers=250,tools=387,observations=2444,new_scoped_integrations=70,including_existing=73,missing=258)
    assert proof['Signac_catalog_status']=='missing' and proof['Seurat_catalog_status']=='method-overlap' and proof['Cell_Ranger_excluded']
    for key in ['historical_whole_environments_reproduced','ggplot_pixel_equivalence','raster_pixel_replay','execution_authentication',
                'host_DLL_authentication','biological_accuracy_certified','full_paper_reproduction','catalog_implementation_credit',
                'root_dispatch_added','full_project_test_suite_run','released','pushed','deployed','whole_goal_complete']:assert proof[key] is False,key
    for key in ['native_R_sampling_replay','native_numeric_and_annotation_replay','deterministic_SVG_replay','standalone_analysis_interface','SVG_bytes_identical_across_JVMs']:assert proof[key] is True,key
    controls=record('coverage-original-2/original/RESULT.json')
    assert {k:len(controls[k]) for k in ['sampling','coverage','scaling','cut','annotations']}==dict(sampling=46,coverage=285,scaling=15,cut=6,annotations=270)
    assert all('error' not in row['result']['value'] for row in controls['annotations'])
    assert json.loads(gzip.decompress(files['current-source/src/test/resources/org/jlinalg/sequence/signac-coverage/controls.json.gz']))==controls
    edges=record('coverage-original-edges-3/original/RESULT.json')
    assert json.loads(gzip.decompress(files['current-source/src/test/resources/org/jlinalg/sequence/signac-coverage/edges-v2.json.gz']))==edges
    assert len(edges['full'])==3 and len(edges['scaling'])==len(edges['pruning'])==6
    for row in edges['full']:
        value=row['result']['value'];assert value['rows_from_complete_plot'] and value['direct_track_rows_identical']
        assert files[PREFIX+'coverage-original-edges-3/original/'+value['plot_saved']].startswith(b'\x89PNG\r\n\x1a\n')
    profile=obj('current-source/src/main/resources/org/jlinalg/omics/signac-coverage/SOURCE-PROFILES.json')
    assert profile['dependencies']==controls['versions']
    for name,wanted in profile['files'].items():assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/signac-coverage/'+name])==wanted
    for download in profile['R_downloads']:
        assert download['edition']=='tags/R-4-6-1' and download['url'].startswith('https://svn.r-project.org/R/tags/R-4-6-1/')
        assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/signac-coverage/R4.6.1-'+download['name']])==download['archive']
    native=record('coverage-native-matrix-1/COMPLETED.json');assert native['artifact']==proof['artifact'] and native['tests_per_JVM']==660 and len(native['JVMs'])==3
    assert not record('coverage-native-matrix-1/parent-entries.json')['changed']
    for run in native['JVMs']:
        assert run['tests']==run['passed']==660 and run['skipped']==0
        assert record('coverage-native-matrix-1/'+run['JVM']+'.process.json')['exit_code']==0
        stdout=files[PREFIX+'coverage-native-matrix-1/'+run['JVM']+'.stdout'].decode('utf8');assert '660 tests successful' in stdout and '0 tests failed' in stdout

    def family(path,pins,admitted=True):
        assert {n.removeprefix(path+'/') for n in files if n.startswith(path+'/')}==set(pins)
        for name,wanted in pins.items():assert byte_pin(files[path+'/'+name])==wanted
        seal=obj(path+'/capture-files.json');assert seal['authentication'] is False and seal['files']=={n:v['sha256'] for n,v in pins.items() if n!='capture-files.json'}
        state=obj(path+'/analysis-status.json');assert state['results_admitted']==admitted
        if admitted:
            assert state['status']=='completed-with-warnings' and seal['directories']==['inputs','source']
            manifest=obj(path+'/manifest.json');assert manifest['native_arithmetic_replay'] and manifest['deterministic_SVG_replay']
            assert manifest['raster_font_replay'] is False and manifest['execution_authentication'] is False
            completed=obj(path+'/COMPLETED.json');assert completed['results_admitted'] and completed['execution_authentication'] is False
            diagnostic=obj(path+'/diagnostics.json');png=files[path+'/coverage.png'];assert png.startswith(b'\x89PNG\r\n\x1a\n')
            assert struct.unpack('>II',png[16:24])==(diagnostic['plot_width'],diagnostic['plot_height'])
        else:assert 'FAIL2' in state['status'] or 'SKIP3' in state['status']

    refs=record('coverage-examples-original-2/original/RESULT.json');originals={row['example']:row for row in refs['results']}
    assert len(originals)==3 and all('error' not in row['result']['value'] for row in originals.values())
    assert files[PREFIX+'coverage-examples-original-2/exit.txt']==b'0'
    for row in originals.values():assert files[PREFIX+'coverage-examples-original-2/original/'+row['result']['value']['plot_saved']].startswith(b'\x89PNG\r\n\x1a\n')
    examples=record('coverage-examples-1/COMPLETED.json');assert examples['artifact']==proof['artifact'] and examples['analyses']==9 and examples['portable_verifications']==30
    svg={};rows_compared=0
    for run in examples['runs']:
        path=run['path'];family(path,run['files']);expected=originals[run['example']]['result']['value'];actual=table(files[path+'/coverage.tsv']);assert len(actual)==len(expected['rows'])
        for a,e in zip(actual,expected['rows']):
            assert a['group']==e['group'] and int(a['position'])==e['position'] and int(a['cut_count'])==e['count']
            for old,new in [('norm.value','normalized'),('coverage','coverage')]:assert math.isfinite(float(a[new])) and abs(float(a[new])-e[old])<=2e-12*max(1,abs(e[old]))
        rows_compared+=len(actual)
        for old,new in [('labels','annotation-labels.tsv'),('exons','annotation-exons.tsv'),('plus','annotation-plus.tsv'),('minus','annotation-minus.tsv')]:
            actual_model=table(files[path+'/'+new]);expected_model=expected['annotation'][old];assert len(actual_model)==len(expected_model)
            for a,e in zip(actual_model,expected_model):
                assert a['chromosome']==e['seqnames'] and int(a['dodge'])==e['dodge']
                for key in ['strand','gene_name','tx_id','gene_biotype']:assert a[key]==e[key]
                for key in ['start','end']+(['width','position'] if old=='labels' else []):assert abs(float(a[key])-e[key])<=1e-11*max(1,abs(e[key]))
        image=files[path+'/coverage.svg']
        if run['example'] in svg:assert svg[run['example']]==image
        else:svg[run['example']]=image
        for platform in ['windows-java21','windows-java25','linux-java21']:
            receipt=record('coverage-examples-1/'+run['platform']+'-'+run['example']+'-'+platform+'-verify.process.json');assert receipt['exit_code']==0
    family('build/signac-coverage-empty-demo-NEW',examples['empty_failure_files'],False)
    assert record('coverage-examples-1/literal-empty-failure.process.json')['exit_code']==2
    provenance=obj('current-source/examples/signac-coverage/INPUT-PROVENANCE.json')
    assert provenance['peak_assay_counts']['cells']==['cell1','cell2','cell3','cell4'] and provenance['peak_assay_counts']['n_counts']==[1202,600,1200,600]
    assert proof['native_peak_assay_n_counts']==[1202,600,1200,600]

    integrity=record('coverage-integrity-1/COMPLETED.json');assert integrity['artifact']==proof['artifact'] and integrity['semantic_refusals']==19 and integrity['original_preserved']
    for run in integrity['runs']:
        path=PREFIX+'coverage-integrity-1/'+run['control']+'-TEST-ONLY'
        assert {n.removeprefix(path+'/') for n in files if n.startswith(path+'/')}==set(run['files'])
        assert record('coverage-integrity-1/'+run['control']+'.process.json')['exit_code']==2
    forged=PREFIX+'coverage-integrity-1/white-PNG-TEST-ONLY-NOT-A-SCIENTIFIC-FIGURE';family(forged,integrity['forged_files'])
    assert files[forged+'/coverage.svg']==files['build/signac-coverage-gene-demo-NEW/coverage.svg']
    assert files[forged+'/coverage.png']!=files['build/signac-coverage-gene-demo-NEW/coverage.png']
    assert integrity['unchanged_dimension_PNG_pixel_forgery_can_pass'] and integrity['forged_PNG_is_scientific_figure'] is False
    for platform in ['windows-java21','windows-java25','linux-java21']:assert record('coverage-integrity-1/white-PNG-limit-'+platform+'-verify.process.json')['exit_code']==0

    recovery=record('coverage-recovery-1/COMPLETED.json');assert recovery['artifact']==proof['artifact'] and recovery['controls']==17 and recovery['expected_failures']==14 and recovery['expected_runtime_skips']==1
    for run in recovery['runs']:
        family(run['analysis_path'],run['files'],run['expected_exit_code']==0)
        process=record('coverage-recovery-1/'+run['case']+'.process.json');assert process['exit_code']==process['expected_exit_code']==run['expected_exit_code']
    skip=files[PREFIX+'coverage-recovery-1/no-desktop-runtime.stderr'].decode('utf8');assert 'SKIP3' in skip and 'NoClassDefFoundError' in skip and 'REQUIRED ACTIONS' in skip
    family(PREFIX+'coverage-recovery-1/recovered-NEW-结果',recovery['recovery_files'])
    assert files[PREFIX+'coverage-recovery-1/recovered-NEW-结果/coverage.tsv']==files['build/signac-coverage-gene-demo-NEW/coverage.tsv']
    # Invalid collectors/tests remain visible and are never recast as passing reference data.
    for name in ['coverage-original-1/collector-attempted.R','coverage-original-edges-1/collector-attempted.R','coverage-original-edges-2/collector-attempted.R',
                 'coverage-examples-original-1/collector-attempted.R','coverage-test-attempt-1/tests.xml','coverage-test-attempt-3/tests.xml','coverage-test-attempt-5/tests.xml','coverage-test-attempt-6.log']:
        assert PREFIX+name in files,name
    assert proof['unchanged_dimension_PNG_pixel_forgery_can_pass'] and proof['raster_pixel_replay'] is False
    print(json.dumps(dict(status='passed-offline-native-Signac-coverage-annotation-development',source_pins=len(proof['source_pins']),logical_files=proof['logical_files'],
         tests_per_JVM=660,actual_native_examples=9,original_composed_plot_rows_compared=rows_compared,semantic_refusals=19,
         recovery_controls=17,genuine_runtime_skip=True,PNG_pixel_forgery_can_pass=True,catalog_implementation_credit=False)))


if __name__=='__main__':
    try:main()
    except Exception as e:
        print('NATIVE COVERAGE EVIDENCE FAIL2: '+str(e)+'\nRequired action: preserve the evidence, correct the named source/archive/capture mismatch and repeat verification. No new figure or catalog promotion is admitted.',file=sys.stderr)
        raise SystemExit(2)
