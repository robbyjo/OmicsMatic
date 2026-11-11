# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline source/evidence/original-output audit for the standalone typed peak workflow."""
import csv
import hashlib
import io
import json
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

PREFIX='build/literature-review/signac-runtime/'


def rows(raw):
    return list(csv.DictReader(io.StringIO(raw.decode('utf8')),delimiter='\t'))


def main():
    if not __debug__:raise RuntimeError('Run with assertions enabled; never Python -O')
    folder=ROOT/'docs/literature/signac-peaks-development';proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['status']=='passed-standalone-typed-original-MACS-Signac-peak-development'
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    def obj(name):return json.loads(files[name])
    def raw(name):return files[PREFIX+name]
    def record(name):return json.loads(raw(name))
    for name,wanted in proof['source_pins'].items():
        assert pin(ROOT/name)==wanted,name
        assert byte_pin(files['current-source/'+name])==wanted,name
    for name,wanted in proof['parents'].items():
        parent=ROOT/('docs/literature/'+name);assert pin(parent/'VALIDATION.json')==wanted
        p=json.loads((parent/'VALIDATION.json').read_bytes())
        assert pin(parent/'controls.zip')==p['archive']
    parent=ROOT/'docs/literature/signac-peak-runtime-development'
    reference=unpack(parent/'controls.zip',json.loads((parent/'VALIDATION.json').read_bytes()))
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_preserved']
    jar=ROOT/'build/literature-review/signac-runtime/peaks-tools-development/omicsmatic-0.3.7-signac-peaks-development.jar'
    if jar.exists():assert pin(jar)==proof['artifact']
    assert proof['catalog_counts']==dict(papers=250,tools=387,observations=2444,new_scoped_integrations=70,including_existing=73,missing=258)
    assert proof['Signac_catalog_status']=='missing' and proof['Seurat_catalog_status']=='method-overlap' and proof['Cell_Ranger_excluded']
    for key in ['native_MACS_statistical_replay','execution_authentication','host_DLL_authentication',
                'historical_whole_environment_reproduced','catalog_implementation_credit','root_dispatch_added',
                'full_paper_reproduction','biological_accuracy_certified','full_project_test_suite_run',
                'released','pushed','deployed','whole_goal_complete']:assert proof[key] is False,key
    assert proof['standalone_typed_original_analysis_interface'] and proof['native_surrounding_geometry_and_reduction']
    ordering=record('peaks-ordering-original-3/original/RESULT.json')
    assert len(ordering['ranking'])==1830 and len(ordering['combined'])==208
    assert ordering['versions']==dict(R='4.6.1',Seqinfo='1.2.0',GenomeInfoDb='1.48.0',GenomicRanges='1.64.0')
    assert ordering==obj('current-source/src/test/resources/org/jlinalg/sequence/signac-peak-output/ordering.json')
    profile=obj('current-source/src/main/resources/org/jlinalg/omics/signac-peaks/SOURCE-PROFILES.json')
    assert profile['ordering']==ordering['versions'] and set(profile['MACS2'])==set(proof['exact_MACS_editions'])
    assert profile['required_python']=='3.9.20' and profile['required_packages']==dict(numpy='1.24.4')
    for name,wanted in profile['files'].items():assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/signac-peaks/'+name])==wanted
    for name in ['Seqinfo','GenomicRanges']:
        source=record('peaks-ordering-original-3/'+name+'-SOURCE.json')
        assert byte_pin(raw('peaks-ordering-original-3/'+name+'-official.tar.gz'))==source['archive']
    native=record('peaks-native-matrix-1/COMPLETED.json');assert native['artifact']==proof['artifact']
    assert native['tests_per_JVM']==2072 and len(native['JVMs'])==3 and native['parent_entries_preserved']
    for run in native['JVMs']:
        assert run['tests']==run['passed']==2072 and run['skipped']==0
        assert record('peaks-native-matrix-1/'+run['JVM']+'.process.json')['exit_code']==0
        text=raw('peaks-native-matrix-1/'+run['JVM']+'.stdout').decode('utf8')
        assert '2072 tests successful' in text and '0 tests failed' in text
    assert not record('peaks-native-matrix-1/parent-entries.json')['changed']

    def family(path,pins,peak=True,admitted=True):
        assert {n.removeprefix(path+'/') for n in files if n.startswith(path+'/')}==set(pins)
        for n,wanted in pins.items():assert byte_pin(files[path+'/'+n])==wanted
        if not peak:return
        seal=obj(path+'/capture-files.json');assert seal['authentication'] is False
        assert seal['files']=={n:p['sha256'] for n,p in pins.items() if n!='capture-files.json'}
        state=obj(path+'/analysis-status.json');assert state['results_admitted']==admitted
        if admitted:
            assert state['status']=='completed-with-warnings' and state['all_requested_calls_completed']
            assert obj(path+'/COMPLETED.json')['results_admitted']
            assert obj(path+'/COMPLETED.json')['statistical_replay'] is False
            for n in pins:
                if n.startswith('process/') and n.endswith('/process.json'):
                    process=obj(path+'/'+n);assert process['status']=='completed-process-only' and process['exit_code']==0
                    assert not process['owned_live_after_cleanup']
        else:assert 'FAIL2' in state['status'] or 'SKIP3' in state['status']

    def expected(version,cases,labels,broad=False):
        output=[];index=proof['exact_MACS_editions'].index(version)+1
        for case,label in zip(cases,labels):
            path=PREFIX+'peaks-macs-original-'+str(index)+'/'+version+'/'+case+'/fixture_peaks.'+('broadPeak' if broad else 'narrowPeak')
            for line in reference[path].decode('ascii').splitlines():
                v=line.split('\t');output.append(dict(group=label,chromosome=v[0],start=str(int(v[1])+1),end=v[2],strand='*',
                    name=v[3].replace('fixture_peak_',label+'_peak_',1),score=v[4],fold_change=v[6],negative_log10_pvalue=v[7],
                    negative_log10_qvalue=v[8],relative_summit_position='' if broad else v[9]))
        return output

    matrix=record('peaks-workflow-matrix-3/COMPLETED.json');assert matrix['artifact']==proof['artifact']
    assert len(matrix['runs'])==27 and matrix['newly_executed_analyses']==0
    successful=empty=refused=calls=0
    for run in matrix['runs']:
        label=run['version']+'-'+run['case'];path=PREFIX+'peaks-workflow-matrix-3/'+label+'-结果'
        failed=run['case']=='empty-input';family(path,run['files'],admitted=not failed)
        for platform in ['windows-java21','windows-java25','linux-java21']:
            p=record('peaks-workflow-matrix-3/'+label+'-verify-'+platform+'.process.json')
            assert p['exit_code']==p['expected_exit_code']==(2 if failed else 0)
        if failed:
            assert 'Fragment source contains no data records' in obj(path+'/analysis-status.json')['error'];refused+=1;continue
        case=run['case'];grouped=case.startswith('grouped');refs=['group-A','group-B'] if grouped else [dict(pooled='pooled-BED',gzip='pooled-gzip-BED',support='support-does-not-weight',bedpe='pooled-BEDPE',broad='pooled-broad',empty='no-significant-peaks')[case]]
        actual=rows(files[path+'/peaks.tsv']);assert actual==expected(run['version'],refs,['A','B'] if grouped else ['fixture'],case=='broad')
        successful+=1;calls+=2 if grouped else 1
        if case=='empty':assert actual==[];empty+=1
        if case=='grouped':
            combined=rows(files[path+'/combined-peaks.tsv']);assert [v['peak_called_in'] for v in combined]==['A','B','A,B']
            assert [json.loads(v['groups_json']) for v in combined]==[['A'],['B'],['A','B']]
    assert (successful,empty,refused,calls)==(24,3,3,30)

    recovery=record('peaks-recovery-1/COMPLETED.json');assert recovery['artifact']==proof['artifact']
    assert len(recovery['runs'])==17 and recovery['owned_children_left_live']==0 and recovery['recovery_in_new_output']
    for run in recovery['runs']:
        path=PREFIX+'peaks-recovery-1/'+run['case']+'-结果';family(path,run['files'],admitted=run['admitted'])
        p=record('peaks-recovery-1/'+run['case']+'.process.json');assert p['exit_code']==run['expected_exit_code']
    family(PREFIX+'peaks-recovery-1/recovered-NEW-结果',recovery['recovered_files'])
    integrity=record('peaks-integrity-1/COMPLETED.json');assert integrity['artifact']==proof['artifact']
    assert integrity['semantic_refusals']==19 and integrity['originals_preserved'] and integrity['coordinated_forgery_can_pass']
    assert integrity['forged_fixture_is_scientific_result'] is False
    for run in integrity['runs']:
        path=PREFIX+'peaks-integrity-1/'+run['control']+'-TEST-ONLY'
        # These contain deliberately invalid but consistently resealed captures; do not admit them.
        assert {n.removeprefix(path+'/') for n in files if n.startswith(path+'/')}==set(run['files'])
        assert record('peaks-integrity-1/'+run['control']+'-verify-windows-java21.process.json')['exit_code']==2
    forged=PREFIX+'peaks-integrity-1/coordinated-statistics-TEST-ONLY-NOT-A-SCIENTIFIC-RESULT'
    family(forged,integrity['forged_files'])
    assert rows(files[forged+'/peaks.tsv'])[0]['negative_log10_pvalue']=='0.25'
    for platform in ['windows-java21','windows-java25','linux-java21']:
        assert record('peaks-integrity-1/coordinated-forgery-verify-'+platform+'.process.json')['exit_code']==0
    assert proof['coordinated_statistical_forgery_can_pass']

    examples=record('peaks-examples-2/COMPLETED.json');assert examples['artifact']==proof['artifact']
    assert examples['literal_peak_examples']==5 and examples['portable_verifications']==18 and examples['newly_executed_analyses']==0
    for run in examples['runs']:family(run['path'],run['files'])
    target='build/signac-peaks-feature-demo-NEW';feature=examples['native_feature_matrix'];family(target,feature['files'],peak=False)
    regions=rows(files['build/signac-peaks-grouped-demo-NEW/regions.tsv']);cells=['cell1','cell2','cell3','cell4']
    expected_counts=[[0]*4 for _ in regions];input_rows=files['current-source/examples/signac-peaks/fragments.tsv'].decode('ascii').splitlines()
    for line in input_rows:
        chrom,start,end,cell,support=line.split('\t')
        for i,r in enumerate(regions):
            if chrom==r['chromosome'] and int(start)<int(r['end']) and int(end)>=int(r['start']):expected_counts[i][cells.index(cell)]+=1
    assert len(input_rows)==6600 and expected_counts==feature['expected'] and sum(map(sum,expected_counts))==3602
    lines=[v for v in files[target+'/matrix.mtx'].decode('ascii').splitlines() if not v.startswith('%')]
    shape=list(map(int,lines[0].split()));actual=[[0]*shape[1] for _ in range(shape[0])]
    for line in lines[1:]:i,j,v=line.split();actual[int(i)-1][int(j)-1]=int(float(v))
    assert shape[:2]==[3,4] and actual==expected_counts
    # Retain the failed source/validator histories rather than recasting them as successful runs.
    for name in ['peaks-workflow-matrix-1','peaks-workflow-matrix-2','peaks-examples-1']:
        assert PREFIX+name+'/FAILURE.json' in files and PREFIX+name+'/validator-attempted.py' in files
    assert PREFIX+'peaks-ordering-original-1/SUPERSEDED.md' in files
    print(json.dumps(dict(status='passed-offline-standalone-typed-MACS-Signac-peak-development',source_pins=len(proof['source_pins']),
         logical_files=proof['logical_files'],tests_per_JVM=2072,independent_matrix_matches=24,early_empty_input_refusals=3,
         semantic_refusals=19,feature_count_sum=3602,coordinated_statistical_forgery_can_pass=True,catalog_implementation_credit=False)))


if __name__=='__main__':
    try:main()
    except Exception as e:
        print('SIGNAC PEAK EVIDENCE FAIL2: '+str(e)+'\nRequired action: preserve evidence, correct the named source/archive/capture mismatch and repeat verification. No new peak result or catalog promotion is admitted.',file=sys.stderr)
        raise SystemExit(2)
