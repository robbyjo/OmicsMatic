# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the standalone typed original MACS/Signac workflow without catalog promotion."""
from signac_peaks_validation import *
from signac_scan_evidence import pack


def main():
    folder=ROOT/'docs/literature/signac-peaks-development';assert not folder.exists()
    native=read(BASE/'peaks-native-matrix-1/COMPLETED.json')
    matrix=read(BASE/'peaks-workflow-matrix-3/COMPLETED.json')
    recovery=read(BASE/'peaks-recovery-1/COMPLETED.json')
    integrity=read(BASE/'peaks-integrity-1/COMPLETED.json')
    examples=read(BASE/'peaks-examples-2/COMPLETED.json')
    artifact=pin(JAR);assert all(v['artifact']==artifact for v in [native,matrix,recovery,integrity,examples])
    assert native['tests_per_JVM']==2072 and len(native['JVMs'])==3
    assert (matrix['actual_analyses'],matrix['successful_analyses'],matrix['portable_verifications'])==(27,24,81)
    assert matrix['expected_empty_input_refusals']==3 and matrix['successful_original_MACS_calls']==30
    assert recovery['requested_controls']==17 and recovery['owned_children_left_live']==0
    assert integrity['semantic_refusals']==19 and integrity['coordinated_forgery_can_pass']
    assert examples['literal_peak_examples']==5 and examples['native_feature_matrix']['count_sum']==3602
    assert examples['native_feature_matrix']['input_fragment_lines']==6600
    for record in [native,matrix,recovery,integrity,examples]:
        assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    new_java=[ROOT/'src/main/java/org/jlinalg/cli'/('SignacPeaks'+n+'.java') for n in ['Cli','Contracts','Inputs','Runtime','Outputs']]
    new_java += [ROOT/'src/main/java/org/jlinalg/sequence'/n for n in ['SignacSequenceOrder.java','SignacPeakOutput.java']]
    scripts=[ROOT/'tools/literature'/n for n in [
        'collect_signac_peak_ordering.R','prepare_signac_peak_workflow_sources.py','signac-peaks-development.init.gradle',
        'probe_signac_peaks_workflow.py','signac_peaks_validation.py','validate_signac_peaks_native.py',
        'validate_signac_peaks_workflow.py','validate_signac_peaks_recovery.py','validate_signac_peaks_integrity.py',
        'prepare_signac_peaks_examples.py','validate_signac_peaks_examples.py',
        'close_signac_peaks_development.py','verify_signac_peaks_development.py']]
    source_files=new_java+scripts+[ROOT/'src/main/resources/org/jlinalg/omics/signac_peaks_runtime.py',
        ROOT/'src/test/java/org/jlinalg/cli/SignacPeakOutputTest.java',ROOT/'src/test/java/org/jlinalg/cli/SignacPeaksContractsTest.java',
        ROOT/'docs/literature/SIGNAC_PEAKS_DEVELOPMENT.md']
    for root in [ROOT/'src/main/resources/org/jlinalg/omics/signac-peaks',ROOT/'src/test/resources/org/jlinalg/sequence/signac-peak-output',ROOT/'examples/signac-peaks']:
        source_files += [p for p in root.rglob('*') if p.is_file()]
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in source_files}
    evidence={'current-source/'+n:(ROOT/n).read_bytes() for n in sources}
    folders=['peaks-compile-failure-1','peaks-ordering-original-1','peaks-ordering-original-2','peaks-ordering-original-3',
             'peaks-workflow-smoke-1','peaks-workflow-smoke-2','peaks-workflow-smoke-3','peaks-workflow-smoke-4','peaks-workflow-smoke-5',
             'peaks-native-matrix-1','peaks-workflow-matrix-1','peaks-workflow-matrix-2','peaks-workflow-matrix-3',
             'peaks-recovery-1','peaks-integrity-1','peaks-examples-1','peaks-examples-2']
    for name in folders:
        for p in (BASE/name).rglob('*'):
            if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    # Literal example analyses live outside the reference working directory.
    for record in examples['runs']:
        directory=ROOT/record['path'];assert files(directory)==record['files']
        for p in directory.rglob('*'):
            if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    directory=ROOT/'build/signac-peaks-feature-demo-NEW'
    assert files(directory)==examples['native_feature_matrix']['files']
    for p in directory.rglob('*'):
        if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    parents={name:pin(ROOT/('docs/literature/'+name+'/VALIDATION.json')) for name in [
        'signac-peak-preparation-development','signac-peak-runtime-development','seurat-differential-development']}
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    assert canonical==native['canonical_executable_preserved']
    folder.mkdir();(folder/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    warnings='''# STANDALONE DEVELOPMENT: READ WARNINGS BEFORE USING PEAK RESULTS

This milestone admits a typed original-MACS capture workflow with native Java splitting, transport, chromosome ordering and interval reduction. It does not grant whole-Signac or catalog credit. Totals remain70new scoped integrations/73including existing adapters/258missing. Cell Ranger remains excluded.

SKIP3/exit3 means required exact permitted Linux MACS/Python requirements are unavailable or forbidden: skip dependent peak analysis; supply the named private runtime only where allowed and retry into NEW outputs. FAIL2/exit2 admits no result after input, source, process, integrity, capacity or finalization failure. Preserve all attempts and partials, read each named error/log and correct the cause before retrying into NEW paths. No analysis-time installer, alternate peak model or silent repair is used.

All-zero successful pooled/noncombined output is distinct from source failure. Combined grouped calling with no nonempty source groups fails, matching original reduce(NULL). Zero-record fragment source files are rejected before launch; independent original MACS also fails on empty inputs. Label substitutions/collisions and BED/BEDPE/read-support/duplicate semantics require review. Combined intervals discard source p/q statistics and scores.

VERIFY checks capture consistency and native surrounding calculations; it does not independently refit MACS statistics, authenticate execution/full host libraries, reconcile every forensic spreadsheet or validate biology.19 resealed semantic corruptions are refused. A deliberately consistent fabricated source p value with changed published copy and rebuilt hashes is accepted on3JVMs. That clearly labelled TEST-ONLY fixture is never a scientific result.

Retained attempts: the first ordering collector used an unqualified R start/end accessor and its combined coordinates are invalid; use only ordering-original-3 controls. An ambiguous Java validate import caused compile attempt1, then was corrected. Workflow matrix attempt1 expected group memberships in the wrong coordinate order. Attempt2 assumed empty input reached MACS, while complete input scanning correctly refused it first. Matrix3 audits the unchanged real captures from attempt2 with those verifier expectations corrected; it does not rerun analyses. Example attempt1 expected all6600input lines in peaks, while independent interval counting correctly found3602overlapping lines. Example2 audits those unchanged literal analyses and corrects that expectation. Failed attempts and their attempted code/docs remain retained and labelled; they are not silently deleted or counted as additional successful validations.

Read ../SIGNAC_PEAKS_DEVELOPMENT.md, ../../../examples/signac-peaks/README.md and captured WARNINGS.md, analysis-status.json, prepared-summary.json, diagnostics.json and every original process log before analysis. Java calling requires Linux/WSL; Java-only verification and native feature counting work on Windows too. Private runtimes are not distributed in this evidence archive.

No historical whole-environment, full-paper/biological, full project test suite, release, push, deployment or whole-goal completion claim is made. Canonical artifacts and frozen parents remain unchanged.
'''
    (folder/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n')
    layout=pack(folder/'controls.zip',evidence)
    result=dict(schema_version=1,status='passed-standalone-typed-original-MACS-Signac-peak-development',
        artifact=artifact,source_pins=sources,archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,
        canonical_executable_preserved=canonical,parents=parents,tests_per_JVM=2072,JVMs=3,
        original_sequence_rank_cases=1830,original_grouped_reduction_cases=208,original_peak_parser_cases=4,
        matrix_analyses=27,matrix_successful_analyses=24,matrix_early_empty_input_refusals=3,matrix_original_MACS_calls=30,
        matrix_portable_verifications=81,recovery_controls=17,semantic_resealed_corruptions_refused=19,
        literal_peak_examples=5,example_portable_verifications=18,native_feature_matrix_rows=3,native_feature_matrix_columns=4,
        native_feature_matrix_count_sum=3602,synthetic_input_fragment_lines=6600,coordinated_statistical_forgery_can_pass=True,
        standalone_typed_original_analysis_interface=True,native_surrounding_geometry_and_reduction=True,
        exact_MACS_editions=VERSIONS,Signac_profiles=['Signac-1.9.0','Signac-1.14.0'],
        catalog_counts=dict(papers=250,tools=387,observations=2444,new_scoped_integrations=70,including_existing=73,missing=258),
        Signac_catalog_status='missing',Seurat_catalog_status='method-overlap',Cell_Ranger_excluded=True,
        native_MACS_statistical_replay=False,execution_authentication=False,host_DLL_authentication=False,
        historical_whole_environment_reproduced=False,catalog_implementation_credit=False,root_dispatch_added=False,
        full_paper_reproduction=False,biological_accuracy_certified=False,full_project_test_suite_run=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in sources.items());save(folder/'VALIDATION.json',result)
    print(json.dumps(dict(status=result['status'],archive=result['archive'],logical_files=layout['logical_files'],source_pins=len(sources))))


if __name__=='__main__':main()
