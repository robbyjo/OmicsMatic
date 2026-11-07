# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal final HTSeq workflows, native primitives, source-bound parents and retained candidates."""
import argparse
import json
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from build_htseq_tools_diagnostics import BASE,JAR


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists()
    load=lambda name:json.loads((BASE/name/'COMPLETED.json').read_bytes())
    artifact=pin(JAR);native=load('final-file-native-matrix-1');binary=load('final-binary-native-matrix-1');work=load('final-workflows-1');integrity=load('final-integrity-1');examples=load('final-file-examples-1');binary_example=load('final-binary-example-1')
    assert all(r['artifact']==artifact for r in [native,binary,work,integrity,examples,binary_example])
    assert native['tests_per_JVM']==217 and binary['tests_per_JVM']==43 and len(native['JVMs'])==len(binary['JVMs'])==3
    assert work['original_analyses']==5 and work['expected_source_failures']==0 and examples['successful_example_counts']==2 and examples['portable_example_verifications']==6
    assert integrity['successful_portable_verifications']==3 and integrity['expected_failures']==33 and integrity['missing_runtime_skips']==3
    assert examples['genuine_source_failures']==1 and len(examples['forced_commit_refusals'])==3 and examples['all_provisional_reports_quarantined']
    assert pin(ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java')==load('tools-build-1')['dispatcher_source']
    assert pin(ROOT/'src/main/java/org/jlinalg/sequence/HtseqBinaryAccessibility.java')==load('binary-build-1')['source']
    diagnostic=load('tools-diagnostics-build-1');updates={r['source']:r['after'] for r in diagnostic['source_derivation']}
    for name,wanted in load('file-count-build-3')['source_pins'].items():assert pin(ROOT/name)==updates.get(name,wanted)
    for result in [native,binary]:
        for name,wanted in result['source_pins'].items():
            if name.startswith(('src/main/java/','src/test/java/')):assert pin(ROOT/name)==wanted
    broad=load('file-count-controls-3');assert broad['original_analyses']==33 and broad['expected_source_failures']==9 and len(broad['cases'])==42
    parent=ROOT/'docs/literature/htseq-read-count-development';parent_record=json.loads((parent/'VALIDATION.json').read_bytes());assert pin(parent/'controls.zip')==parent_record['archive']
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert native['canonical_executable_preserved']==binary['canonical_executable_preserved']==canonical
    sources=[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in ['HtseqFileContract','HtseqCountTable','HtseqCountRuntime','HtseqFileCounting','HtseqToolsCli','JLinAlgCli']]
    sources += [ROOT/'src/main/java/org/jlinalg/sequence/HtseqBinaryAccessibility.java']
    sources += [ROOT/'src/test/java/org/jlinalg/cli'/(n+'.java') for n in ['HtseqFileCountTest','HtseqBinaryAccessibilityTest']]
    for name in ['src/main/resources/org/jlinalg/omics/htseq-file-count','src/main/resources/org/jlinalg/omics/htseq-binary-accessibility',
        'src/test/resources/org/jlinalg/sequence/htseq-file-count','src/test/resources/org/jlinalg/sequence/htseq-binary-accessibility',
        'examples/htseq-file-count','examples/htseq-binary-accessibility']:
        sources += [p for p in (ROOT/name).rglob('*') if p.is_file() and '__pycache__' not in p.parts]
    for name in ['HTSEQ_TOOLS.md','HTSEQ_FILE_COUNT_DEVELOPMENT.md','HTSEQ_BINARY_ACCESSIBILITY_DEVELOPMENT.md']:sources.append(ROOT/'docs/literature'/name)
    for name in ['prepare_htseq_file_profiles.py','prepare_htseq_file_table_fixture.py','build_htseq_file_count_development.py',
        'validate_htseq_file_count_workflow.py','validate_htseq_file_count_native.py','validate_htseq_file_count_integrity.py',
        'collect_htseq_binary_controls.py','prepare_htseq_binary_fixture.py','build_htseq_binary_development.py','validate_htseq_binary_native.py','validate_htseq_binary_example.py',
        'wire_htseq_dispatch.py','build_htseq_tools_development.py','build_htseq_tools_diagnostics.py','prepare_htseq_root_validators.py','prepare_htseq_final_validators.py',
        'validate_htseq_root_workflows.py','validate_htseq_root_integrity.py','validate_htseq_tools_file_native.py','validate_htseq_tools_binary_native.py',
        'validate_htseq_final_workflows.py','validate_htseq_final_integrity.py','validate_htseq_final_file_native.py','validate_htseq_final_binary_native.py','validate_htseq_final_binary_example.py',
        'validate_htseq_file_examples.py','close_htseq_tools.py','htseq_tools_evidence.py','verify_htseq_tools.py']:
        sources.append(ROOT/'tools/literature'/name)
    sources.append(ROOT/'docs/literature/omics-2023-2026/htseq-dispatch-shared-source-compatibility.json')
    sources.append(ROOT/'docs/literature/omics-2023-2026/htseq-role-coverage.json')
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};evidence={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    folders=['file-count-build-1','file-count-build-2','file-count-build-3','file-count-implementation-attempt-1','file-count-implementation-attempt-2',
        'file-count-smoke-1','file-count-smoke-2','file-count-controls-1','file-count-controls-3','file-count-native-matrix-1','file-count-native-matrix-2','file-count-native-matrix-3','file-count-integrity-1',
        'binary-original-1','binary-build-1','binary-native-matrix-1','binary-example-1','dispatch-source-intake-1','tools-build-1','diagnostic-source-intake-1','tools-diagnostics-build-1',
        'root-workflows-1','root-integrity-1','tools-file-native-matrix-1','tools-binary-native-matrix-1',
        'final-workflows-1','final-integrity-1','final-file-native-matrix-1','final-binary-native-matrix-1','final-binary-example-1','final-file-examples-1']
    for name in folders:
        for path in (BASE/name).rglob('*'):
            if path.is_file() and path.suffix!='.jar':evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['root-validator-derivation.json','final-validator-derivation.json']:evidence['derivation/'+name]=(BASE/name).read_bytes()
    chain=[('prepared-read-parent',ROOT/parent_record['artifact']['path']),('file-count',BASE/'file-count-development-3/omicsmatic-0.3.7-htseq-file-count-development.jar'),
        ('binary',BASE/'binary-development/omicsmatic-0.3.7-htseq-binary-development.jar'),('root',BASE/'tools-development/omicsmatic-0.3.7-htseq-tools-development.jar'),('final',JAR)]
    artifacts={};entry_maps={}
    for label,path in chain:
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))
            entries={n:dict(byte_pin(archive.read(n)),directory=archive.getinfo(n).is_dir()) for n in archive.namelist()}
            if label=='final':
                owned=[n for n in entries if n.startswith(('org/jlinalg/cli/Htseq','org/jlinalg/sequence/HtseqBinaryAccessibility','org/jlinalg/omics/htseq-file-count/','org/jlinalg/omics/htseq-binary-accessibility/')) or n=='org/jlinalg/cli/JLinAlgCli.class']
                for n in owned:
                    if not n.endswith('/'):evidence['candidate-owned-entries/'+n]=archive.read(n)
        artifacts[label]=dict(path=path.relative_to(ROOT).as_posix(),**pin(path));entry_maps[label]=entries
        evidence['artifact-entries/'+label+'.json']=(json.dumps(entries,sort_keys=True,indent=2)+'\n').encode('utf8')
    out.mkdir()
    warning='# HTSEQ TOOLS: WARNINGS, ERRORS AND REQUIRED ACTION\n\n'+(ROOT/'docs/literature/HTSEQ_TOOLS.md').read_text(encoding='utf8')+'\n\nThis runtime checkpoint does not itself increment catalog credit. Earlier candidates, genuine upstream failures, negative controls and forced commit failures are retained. Diagnostic fixes change only error naming and incomplete-report quarantine; all numerical/native parent entries remain unchanged.\n'
    (out/'WARNINGS.md').write_text(warning,encoding='utf8',newline='\n')
    layout=pack(out/'controls.zip',evidence)
    proof=dict(schema_version=1,status='passed-scoped-HTSeq-tools-development',artifact=artifacts['final'],archive=pin(out/'controls.zip'),warnings_document=pin(out/'WARNINGS.md'),
        source_pins=pins,**layout,artifact_chain=artifacts,parent_read_count_validation=pin(parent/'VALIDATION.json'),parent_read_count_archive=parent_record['archive'],canonical_executable_preserved=canonical,
        tests_per_JVM=260,file_contract_tests_per_JVM=217,binary_tests_per_JVM=43,JVMs=3,final_successful_count_analyses=7,final_positive_portable_verifications=24,
        final_corruption_or_consistency_refusals=33,final_missing_runtime_skips=3,final_genuine_source_failures=1,final_forced_commit_refusals=3,
        broader_edition_count_calls=42,broader_successful_counts=33,broader_genuine_source_failures=9,broader_positive_portable_verifications=99,
        original_reference_calls=185,original_reference_successes=171,original_reference_source_failures=14,excluded_transport_invalid_reference_calls=59,
        original_reference_versions=['0.5.3','0.6.0','0.11.1','0.13.5','2.1.2'],binary_original_library_matrices=32,binary_original_refusals=2,binary_prepared_reads=742,binary_nonzero_coordinates=234,binary_original_step_spans=1278,
        native_binary_example_JVMs=3,source_roles_reviewed=8,prior_reviews_preserved=890,reviewed_uses_at_role_curation=898,paper_tool_observations_at_role_curation=2446,
        original_runtime_counting=True,native_prepared_read_counting_available=True,native_binary_presence_available=True,portable_VERIFY_available=True,root_dispatch_available=True,
        numerical_parent_classes_preserved=True,diagnostic_only_pre_admission_edits=True,analysis_installs_software=False,Cell_Ranger_excluded=True,
        independent_native_SAM_GTF_parser_or_pairing=False,independent_native_random_assignment=False,original_custom_author_script_recovered=False,
        historical_whole_packages_reproduced=False,whole_paper_reproduced=False,biological_validity_certified=False,execution_authentication=False,full_project_test_suite_run=False,
        catalog_implementation_credit_in_this_checkpoint=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'VALIDATION.json',proof)
    (out/'SHA256SUMS').write_text(''.join(pin(out/n)['sha256']+'  '+n+'\n' for n in ['VALIDATION.json','controls.zip','WARNINGS.md']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=proof['status'],source_pins=len(pins),logical_files=proof['logical_files'],archive=proof['archive'])))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable public evidence checks')
    main()
