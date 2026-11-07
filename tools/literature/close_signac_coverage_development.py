# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native coverage/annotation workflow evidence without catalog promotion."""
from signac_coverage_validation import *
from signac_scan_evidence import pack


def main():
    folder=ROOT/'docs/literature/signac-coverage-development';assert not folder.exists()
    native=read(BASE/'coverage-native-matrix-1/COMPLETED.json');examples=read(BASE/'coverage-examples-1/COMPLETED.json')
    integrity=read(BASE/'coverage-integrity-1/COMPLETED.json');recovery=read(BASE/'coverage-recovery-1/COMPLETED.json')
    artifact=pin(JAR);assert all(r['artifact']==artifact for r in [native,examples,integrity,recovery])
    assert native['tests_per_JVM']==660 and len(native['JVMs'])==3 and native['parent_entries_preserved']
    assert examples['analyses']==9 and examples['independent_original_examples']==3 and examples['portable_verifications']==30
    assert integrity['semantic_refusals']==19 and integrity['unchanged_dimension_PNG_pixel_forgery_can_pass']
    assert recovery['controls']==17 and recovery['expected_runtime_skips']==1 and recovery['recovery_in_NEW_output']
    for record in [native,examples,integrity,recovery]:assert all(pin(ROOT/n)==p for n,p in record['source_pins'].items())
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==native['canonical_executable_preserved']
    java=[ROOT/'src/main/java/org/jlinalg'/n for n in [
        'sequence/SignacCoverage.java','sequence/SignacCoverageSampling.java','sequence/SignacCoverageAnnotations.java',
        'plot/SignacCoveragePlot.java','cli/SignacCoverageCli.java','cli/SignacCoverageRequest.java']]
    scripts=[ROOT/'tools/literature'/n for n in [
        'collect_signac_coverage_controls.py','collect_signac_coverage_controls.R','collect_signac_coverage_edges.R',
        'collect_signac_coverage_example_originals.R','prepare_signac_coverage_sources.py','prepare_signac_coverage_edge_sources.py',
        'prepare_signac_coverage_examples.py','signac-coverage-development.init.gradle','signac_coverage_validation.py',
        'validate_signac_coverage_native.py','validate_signac_coverage_examples.py','validate_signac_coverage_integrity.py',
        'validate_signac_coverage_recovery.py','close_signac_coverage_development.py','verify_signac_coverage_development.py']]
    current=java+scripts+[ROOT/'docs/literature/SIGNAC_COVERAGE_DEVELOPMENT.md']+[ROOT/'src/test/java/org/jlinalg/cli'/n for n in ['SignacCoverageTest.java','SignacCoverageContractsTest.java']]
    for path in ['src/main/resources/org/jlinalg/omics/signac-coverage','src/test/resources/org/jlinalg/sequence/signac-coverage','examples/signac-coverage']:
        current += [p for p in (ROOT/path).rglob('*') if p.is_file()]
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in current};evidence={'current-source/'+n:(ROOT/n).read_bytes() for n in sources}
    for name in ['coverage-original-1','coverage-original-2','coverage-original-edges-1','coverage-original-edges-2','coverage-original-edges-3',
                 'coverage-examples-original-1','coverage-examples-original-2','coverage-test-attempt-1','coverage-test-attempt-3','coverage-test-attempt-5',
                 'coverage-cli-probe-1','coverage-native-matrix-1','coverage-examples-1','coverage-integrity-1','coverage-recovery-1']:
        for p in (BASE/name).rglob('*'):
            if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    for p in BASE.glob('coverage-test-attempt-*.log'):evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    # Literal example analyses lie outside the reference working directory.
    for run in examples['runs']:
        directory=ROOT/run['path'];assert files(directory)==run['files']
        for p in directory.rglob('*'):
            if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    directory=ROOT/'build/signac-coverage-empty-demo-NEW';assert files(directory)==examples['empty_failure_files']
    for p in directory.rglob('*'):
        if p.is_file():evidence[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    parents={n:pin(ROOT/('docs/literature/'+n+'/VALIDATION.json')) for n in ['signac-peaks-development','signac-peak-preparation-development','signac-peak-runtime-development','seurat-differential-development']}
    folder.mkdir();(folder/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    warning='''# NATIVE COVERAGE DEVELOPMENT: READ WARNINGS BEFORE INTERPRETING FIGURES

Native Java supplies original-profile endpoint/scaling/rolling/sampling and gene/transcript layout, complete tables, deterministic SVG and PNG previews.660focused tests pass on each of3JVMs.9real native analyses match3complete original composed-plot references;30portable example verifications pass.17recovery controls include14expected failures,2successes and1genuine restricted-Java SKIP3, followed by recovery into NEW outputs.

FAIL2/exit2 admits no result after input, identity, source geometry, empty/nonfinite signal, capacity, integrity or finalization failure. Preserve every attempt and partial output. Read the printed Cause, WARNINGS.md, analysis-status.json, diagnostics.json and all numeric/model tables; correct the named cause and retry into NEW paths. SKIP3/exit3 skips dependent plotting if required full Java desktop/font/PNG components are unavailable or forbidden. Where permitted, provide a suitable full JDK and retry into NEW paths; otherwise retain the skip. Analysis never installs or launches R. No installation was needed for successful native analyses.

Supplied assay-wide nCount values cover the complete object population, including cells with no local reads. Original zero NA scaling slots, unused-factor positional recycling, endpoint offset, persistent file-order chromosome pruning, rolling sums, fixed-seed sampling and qXX rounded-maximum clipping remain explicit. Do not replace these by generic scaling, canonical Tn5 shifts, full-fragment pileup, empirical quantiles or silently repaired identities. Source-unplottable tracks are not negative accessibility findings.

VERIFY replays numbers, model geometry and SVG bytes. It checks PNG format/dimensions/hashes but not raster pixels across fonts.19resealed numeric/model/SVG/capture corruptions are refused. A TEST-ONLY blank white PNG with unchanged dimensions and recomputed transaction/capture hashes is accepted on all3JVMs. It is NOT a scientific figure. Use the replayed SVG for audited geometry; consistency and hashes do not authenticate execution, biology or a full paper. Native styling is not ggplot pixel equivalence.

Historical failed attempts remain: coverage-original-1 used a nonexported annotation namespace lookup; only its annotation controls are invalid. Test attempt1 compared source empty maximum -Inf with ggplot axis +Inf. Test attempt3/CLI probe1 exposed Windows generic-manifest backslash transport, now normalized with duplicate-alias refusal. Test attempt5 used a direct track's relative columns instead of SingleCoveragePlot's genomic relabeling. Edge attempt2 then addressed the wrong outer patchwork data; edge3 retrieves the actual nested coverage panel and verifies it equals the relabeled direct track. Test attempt6 ran with the missing replacement fixture after that failed collector gate and is not a successful validation. Literal-original attempt1 compared equal numeric counts using incompatible R storage types; attempt2 compares numeric types explicitly and succeeds. Attempted code, old fixtures, warnings and failed logs are preserved. Active edge controls are edges-v2.json.gz from edge attempt3; older edges.json.gz is retained history, not the active complete-plot data reference. Final Gradle attempt7 and packaged native matrix1 pass.

Read ../SIGNAC_COVERAGE_DEVELOPMENT.md and ../../../examples/signac-coverage/README.md. Source functions execute unchanged under the retained current R4.6.1 dependency namespace for reference collection; whole historical environments are not reproduced. This native workflow is single assay with accessibility and optional gene/transcript models. Full Signac role review/public integration remains unfinished. Counts remain70new scoped integrations/73including existing adapters/258missing; Cell Ranger stays excluded. Canonical and frozen parents remain preserved. No full project test suite, whole-paper/biological, release, push, deployment or whole-goal completion is claimed.
'''
    (folder/'WARNINGS.md').write_text(warning,encoding='utf8',newline='\n')
    layout=pack(folder/'controls.zip',evidence)
    result=dict(schema_version=1,status='passed-standalone-native-Signac-coverage-annotation-development',artifact=artifact,
        source_pins=sources,archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,
        canonical_executable_preserved=canonical,parents=parents,tests_per_JVM=660,JVMs=3,
        original_sampling_cases=46,original_coverage_track_cases=285,original_scaling_cases=15,missing_group_cases_not_admitted=3,
        original_single_file_cut_cases=6,original_annotation_layout_cases=270,heterogeneous_scaling_cases=6,persistent_pruning_cases=6,
        complete_original_plot_data_cases=3,actual_native_example_analyses=9,independent_original_literal_examples=3,
        example_portable_verifications=30,recovery_controls=17,expected_recovery_failures=14,expected_restricted_runtime_skips=1,
        semantic_resealed_corruptions_refused=19,SVG_bytes_identical_across_JVMs=True,native_R_sampling_replay=True,
        native_numeric_and_annotation_replay=True,deterministic_SVG_replay=True,standalone_analysis_interface=True,
        native_peak_assay_n_counts=[1202,600,1200,600],unchanged_dimension_PNG_pixel_forgery_can_pass=True,
        catalog_counts=dict(papers=250,tools=387,observations=2444,new_scoped_integrations=70,including_existing=73,missing=258),
        Signac_catalog_status='missing',Seurat_catalog_status='method-overlap',Cell_Ranger_excluded=True,
        historical_whole_environments_reproduced=False,ggplot_pixel_equivalence=False,raster_pixel_replay=False,
        execution_authentication=False,host_DLL_authentication=False,biological_accuracy_certified=False,full_paper_reproduction=False,
        catalog_implementation_credit=False,root_dispatch_added=False,full_project_test_suite_run=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in sources.items());save(folder/'VALIDATION.json',result)
    print(json.dumps(dict(status=result['status'],archive=result['archive'],logical_files=layout['logical_files'],source_pins=len(sources))))


if __name__=='__main__':main()
