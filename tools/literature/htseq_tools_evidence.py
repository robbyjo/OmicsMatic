# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline scope/source/artifact/control audit for the closed HTSeq tools checkpoint."""
import gzip
import json
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

PREFIX='build/literature-review/htseq-runtime/'


def verify(folder=None):
    folder=Path(folder).resolve() if folder else ROOT/'docs/literature/htseq-tools-development'
    assert folder.is_relative_to(ROOT)
    proof=json.loads((folder/'VALIDATION.json').read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-scoped-HTSeq-tools-development'
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    for name,wanted in proof['source_pins'].items():
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,wanted['sha256'])
        assert byte_pin(files['current-source/'+name])==wanted,name
    def record(name):return json.loads(files[PREFIX+name])
    parent=ROOT/'docs/literature/htseq-read-count-development';parent_record=json.loads((parent/'VALIDATION.json').read_bytes())
    assert pin(parent/'VALIDATION.json')==proof['parent_read_count_validation'] and pin(parent/'controls.zip')==proof['parent_read_count_archive']
    assert (parent_record['valid_original_calls'],parent_record['original_successes'],parent_record['original_source_failures'],parent_record['excluded_transport_invalid_calls'])==(185,171,14,59)
    maps={name:json.loads(files['artifact-entries/'+name+'.json']) for name in proof['artifact_chain']}
    transitions=[]
    names=['prepared-read-parent','file-count','binary','root','final']
    for a,b in zip(names,names[1:]):
        old,new=maps[a],maps[b];assert set(old)<=set(new)
        changed={n for n in old if old[n]!=new[n]};added=set(new)-set(old);transitions.append((changed,added))
    assert not transitions[0][0] and all(n.startswith(('org/jlinalg/cli/Htseq','org/jlinalg/omics/htseq-file-count/')) for n in transitions[0][1])
    assert not transitions[1][0] and all(n.startswith(('org/jlinalg/sequence/HtseqBinaryAccessibility','org/jlinalg/omics/htseq-binary-accessibility/')) for n in transitions[1][1])
    assert transitions[2]==({'org/jlinalg/cli/JLinAlgCli.class'},set())
    assert transitions[3]==({'org/jlinalg/cli/HtseqCountRuntime.class','org/jlinalg/cli/HtseqFileCounting.class'},set())
    for label,artifact in proof['artifact_chain'].items():
        path=ROOT/artifact['path']
        if path.exists():
            assert pin(path)=={k:artifact[k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as archive:
                actual={n:dict(byte_pin(archive.read(n)),directory=archive.getinfo(n).is_dir()) for n in archive.namelist()};assert actual==maps[label]
                if label=='final':
                    for name,body in files.items():
                        if name.startswith('candidate-owned-entries/'):assert archive.read(name.removeprefix('candidate-owned-entries/'))==body
    diagnostic=record('tools-diagnostics-build-1/COMPLETED.json');build=record('file-count-build-3/COMPLETED.json')
    assert diagnostic['artifact']=={k:proof['artifact'][k] for k in ['bytes','sha256']} and diagnostic['numeric_and_native_classes_preserved']
    for row in diagnostic['source_derivation']:
        old=files[PREFIX+'diagnostic-source-intake-1/'+Path(row['source']).name];after=files['current-source/'+row['source']]
        assert byte_pin(old)==row['before']==build['source_pins'][row['source']] and byte_pin(after)==row['after']
        if row['source'].endswith('HtseqFileCounting.java'):
            assert old.replace(b'List.of("counts.tsv","summary.tsv","COMPLETED.json","analysis-status.json","WARNINGS.md")',b'List.of("counts.tsv","summary.tsv","verification.json","report.json","COMPLETED.json","analysis-status.json","WARNINGS.md")')==after
        else:
            extra=b'        catch(IOException failed){throw new IOException("Original HTSeq "+mode+" failed: "+String.valueOf(failed.getMessage()).replace("RSEM","HTSeq")+". Read "+work.resolve("process.stderr")+" and process.json; no result is admitted.",failed);}\n'
            assert after.count(extra)==1 and after.replace(extra,b'')==old
    for folder_name,expected in [('final-file-native-matrix-1',217),('final-binary-native-matrix-1',43)]:
        result=record(folder_name+'/COMPLETED.json');assert result['tests_per_JVM']==expected and len(result['JVMs'])==3
        assert result['artifact']=={k:proof['artifact'][k] for k in ['bytes','sha256']}
        for row in result['JVMs']:
            assert row['tests']==row['passed']==expected and row['skipped']==0
            assert record(folder_name+'/'+row['JVM']+'.process.json')['exit_code']==0
            text=files[PREFIX+folder_name+'/'+row['JVM']+'.stdout'].decode('utf8');assert str(expected)+' tests successful' in text and '0 tests failed' in text and '0 tests skipped' in text
    broad=record('file-count-controls-3/COMPLETED.json');assert (len(broad['cases']),broad['original_analyses'],broad['expected_source_failures'])==(42,33,9)
    root=record('final-workflows-1/COMPLETED.json');assert root['original_analyses']==5 and root['expected_source_failures']==0
    assert {r['version'] for r in root['cases']}==set(proof['original_reference_versions'])
    for folder_name,result in [('file-count-controls-3',broad),('final-workflows-1',root)]:
        for case in result['cases']:
            base=folder_name+'/'+case['version']+'-'+case['id']+'/'
            expected=0 if case['status'].startswith('passed-') else 2
            assert record(base+'count-request.process.json')['exit_code']==expected
            status=record(base+'count-run/analysis-status.json');assert status['result_admitted']==(expected==0)
            if expected==0:
                for jvm in ['windows-java21','windows-java25','linux-java21']:assert record(base+'verify-request-'+jvm+'.process.json')['exit_code']==0
    integrity=record('final-integrity-1/COMPLETED.json')
    assert (integrity['successful_portable_verifications'],integrity['expected_failures'],integrity['missing_runtime_skips'])==(3,33,3)
    assert len(integrity['cases'])==36 and sum(r['exit_code']==0 for r in integrity['cases'])==3
    for jvm in ['windows-java21','windows-java25','linux-java21']:
        base='final-integrity-1/missing-runtime-'+jvm+'/'
        assert record(base+'request.process.json')['exit_code']==3 and not record(base+'count/analysis-status.json')['result_admitted']
    examples=record('final-file-examples-1/COMPLETED.json');assert examples['successful_example_counts']==2 and examples['portable_example_verifications']==6
    assert examples['genuine_source_failures']==1 and len(examples['forced_commit_refusals'])==3 and examples['all_provisional_reports_quarantined'] and examples['Unicode_Python2_output_path']
    for failure in examples['forced_commit_refusals']:
        base='final-file-examples-1/commit-'+failure['operation'].lower()+'-'+failure['JVM']+'/'
        assert record(base+'attempt.process.json')['exit_code']==2 and not record(base+'attempt-run/analysis-status.json')['result_admitted']
        keys=['counts.tsv','summary.tsv','report.json'] if failure['operation']=='COUNT' else ['verification.json']
        assert all(PREFIX+base+'attempt-run/partial/'+name in files and PREFIX+base+'attempt-run/'+name not in files for name in keys)
    example=record('final-binary-example-1/COMPLETED.json');assert example['JVMs']==3 and example['sparse_coordinates_per_JVM']==3 and example['capacity_refusals_per_JVM']==1
    raw=files[PREFIX+'binary-original-1/RESULT.json'];fixture=gzip.decompress(files['current-source/src/test/resources/org/jlinalg/sequence/htseq-binary-accessibility/original-matrices.json.gz']);assert raw==fixture
    records=json.loads(raw)['cases'];good=[r for r in records if r['matrix'] is not None];bad=[r for r in records if r['matrix'] is None]
    assert len(good)==32 and len(bad)==2 and all(r['source_error']['type']=='IndexError' for r in bad)
    reads=pairs=spans=0
    for result in good:
        case=result['case'];expected=set();matched=0
        for trace in result['trace']:
            labels=set()
            for steps in trace['original_steps']:
                spans+=len(steps)
                for step in steps:labels.update(step['features'])
            assert sorted(labels)==trace['features'];matched+=bool(labels)
            for label in labels:expected.add((case['peaks'].index(label),case['cells'].index(trace['cell'])))
        matrix=result['matrix'];assert matrix['entries']==list(map(list,sorted(expected))) and matrix['reads_with_overlap']==matched and matrix['processed_reads']==len(case['reads'])
        reads+=matrix['processed_reads'];pairs+=len(expected)
    assert (reads,pairs,spans)==(742,234,1278)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==proof['canonical_executable_preserved']
    for flag in ['analysis_installs_software','independent_native_SAM_GTF_parser_or_pairing','independent_native_random_assignment','original_custom_author_script_recovered',
        'historical_whole_packages_reproduced','whole_paper_reproduced','biological_validity_certified','execution_authentication','full_project_test_suite_run',
        'catalog_implementation_credit_in_this_checkpoint','released','pushed','deployed','whole_goal_complete']:assert proof[flag] is False,flag
    assert proof['tests_per_JVM']==260 and proof['final_successful_count_analyses']==7 and proof['final_positive_portable_verifications']==24 and proof['Cell_Ranger_excluded']
    return proof
