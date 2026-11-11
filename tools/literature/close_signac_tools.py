# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the public Signac workflow evidence separately from released executables and catalog credit."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack
from signac_tools_evidence import parent_bindings
from validate_signac_tools_native import BASE, JAR


def main():
    folder = ROOT/'docs/literature/signac-tools-development'
    assert not folder.exists()
    native = json.loads((BASE/'tools-native-matrix-2/COMPLETED.json').read_bytes())
    matrix = json.loads((BASE/'tools-public-workflows-3/COMPLETED.json').read_bytes())
    artifact = pin(JAR)
    assert artifact == native['artifact'] == matrix['artifact']
    assert native['tests_per_JVM']==1679 and matrix['completed_analyses']==21 and matrix['successful_portable_verifications']==51
    parents, source_checks = parent_bindings()
    canonical = pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    assert native['canonical_executable_preserved']==matrix['canonical_executable_preserved']==canonical
    sources = [ROOT/'src/main/java/org/jlinalg/cli'/name for name in ['JLinAlgCli.java','SignacToolsCli.java','SignacNormalizationCli.java']]
    sources += [ROOT/'src/test/java'/name for name in ['org/jlinalg/cli/SignacToolsDispatchTest.java','org/jlinalg/cli/SignacNormalizationContractsTest.java']]
    sources += [ROOT/'src/test/resources/org/jlinalg/sequence/signac-normalization/original-controls.zip']
    for name in ['signac-normalize','signac-tools']:
        sources += [p for p in (ROOT/'src/main/resources/org/jlinalg/omics'/name).rglob('*') if p.is_file()]
    sources += [p for p in (ROOT/'examples/signac-normalize').rglob('*') if p.is_file()]
    for name in ['prepare_signac_tools_sources.py','wire_signac_dispatch.py','validate_signac_tools_native.py',
        'validate_signac_tools_workflows.py','prepare_signac_normalize_example.py','prepare_signac_use_reviews.py',
        'signac-use-reviews.json','signac-source-additions.json','collect_signac_omics.py','curate_signac_uses.py',
        'signac_tools_evidence.py','verify_signac_tools.py','close_signac_tools.py']:
        sources.append(ROOT/'tools/literature'/name)
    sources += [ROOT/'docs/literature/SIGNAC_TOOLS.md']
    sources += [ROOT/'docs/literature/omics-2023-2026'/name for name in ['signac-role-coverage.json',
        'signac-dispatch-shared-source-compatibility.json','signac-use-shared-source-compatibility.json']]
    source_pins = {p.relative_to(ROOT).as_posix():pin(p) for p in sorted(set(sources))}
    evidence = {'current-source/'+n:(ROOT/n).read_bytes() for n in source_pins}
    directories = ['tools-native-matrix-1','tools-native-matrix-2','tools-public-workflows-1','tools-public-workflows-2','tools-public-workflows-3','tools-evidence-attempt-1',
        'tools-source-intake-1','tools-test-attempt-1','primary-role-curation-dry-1']
    for name in directories:
        for path in (BASE/name).rglob('*'):
            if path.is_file():
                evidence[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    for name in ['tools-tests-1.log','tools-tests-2.log','tools-tests-3.log','primary-role-curation-dry-2/verification.json',
        'primary-role-curation-applied-1/verification.json','primary-role-curation-applied-1/before-papers.json.gz',
        'primary-use-audit-3/DRAFT-REVIEW-PROOF.json']:
        path=BASE/name
        evidence[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    audit=json.loads((BASE/'primary-use-audit-3/PRIMARY-USES.json').read_bytes())
    evidence['primary-source-census.json']=(json.dumps(dict(papers_scanned=250,census=audit['census'],
        software_papers=10,institutional_SignAC_collision='P007'),indent=2)+'\n').encode('utf8')
    for name in parents:
        evidence['parent-records/'+name+'/VALIDATION.json']=(ROOT/'docs/literature'/name/'VALIDATION.json').read_bytes()
    folder.mkdir()
    warning='''# SIGNAC DEVELOPMENT: WARNINGS AND REQUIRED ACTION

The public signac-tools route dispatches11 scoped workflows. Java normalization is independent of R/SVD. Original upstream fits require each declared permitted runtime; analysis never installs software. Native coverage needs full Java desktop/font/PNG components. MACS calling requires Linux Java and an exact private MACS/Python home; completed-bundle verification also works on Windows.

SKIP3 means the required runtime/capability is unavailable or forbidden: skip dependent analysis. Where permitted, provision the exact declared profile using the linked workflow guide and retry into NEW paths; otherwise retain the skip. FAIL2 admits no result, including partial files. Read the concrete console cause, WARNINGS.md, analysis-status.json, diagnostics and raw process logs. Preserve attempts, correct the named input/profile/integrity/process/capacity cause and retry into NEW paths. Read warnings even after exit0.

Native TF-IDF retains stored zeros and every original feature/cell coordinate. Supplied IDF is already on the desired method scale; computed IDF uses cells/total-feature-count. Source NaN-to-zero behavior and retained Inf remain explicit. Skip downstream finite-only analyses on Inf. Empty variable-feature selection is valid; skip LSI rather than silently relax cutoffs. LSI consumes original integer counts and normalizes internally. Feature selection follows the retained current1.17.1 assay-wrapper contract even for historical arithmetic profiles.

1679 actual-JAR tests pass without skips on Windows Java21/25 and Linux Java21.21 real public analyses and51 portable VERIFY calls pass; two genuine missing-runtime skips and two refusal controls pass.40 public normalized coordinates match retained original controls, including nonfinite values. Every prior scientific JAR entry is byte-identical; only the existing root dispatcher changes.1373 frozen-parent source bindings and exact parent proof/archive hashes are checked; prior whole scientific matrices are retained, not rerun by this interface checkpoint.

Historical failed attempts are retained. Initial dispatch-test arguments were incorrectly unwrapped by JUnit; the corrected harness passes. Public matrix attempt1 incorrectly required COMPLETED.json for every workflow although older workflows use completed analysis-status; its successful fits and failed harness remain visible. The first caption-curation guard incorrectly rejected the collector's already-supported exact reviewed Results anchor; the corrected guard reuses existing extraction without changing rules.

Independent evidence attempt1 was not admitted: the advertised overflow example used method3 and remained finite, so its nonfinite assertion failed. The entire first evidence bundle and invalidation notice are retained. The corrected example uses original method1 with supplied IDF, producing three Inf coordinates; the additional actual-JAR regression and fresh public matrix validate that precise behavior. Scientific native arithmetic was unchanged.

Ten reviewed paper roles include the P134 explicit logistic-regression caption and exclude the P007 institutional SignAC collision. Unknown settings stay unknown. P158 nuclear signal is ambiguous and is not silently mapped to NucleosomeSignal. P187's fragments-in-peaks denominator is unstated. P225 reports FindMarkers and is not relabeled FindMotifs. Other packages, custom transformations, models and interpretation remain separate. Cell Ranger is excluded.

Current dependency profiles and extracted historical functions do not reproduce historical whole packages. Markers preserve partial-comparison warnings; omitted groups are not negative results. Some upstream fits/RNG/KDE are captured and audited without independent native replay. Coverage SVG replays; PNG pixel replay is outside scope. Hashes and consistency do not authenticate execution or biology. Existing chromVAR receives no duplicate credit. Read ../SIGNAC_TOOLS.md and the linked per-workflow guides.

This proof freezes a scoped public interface; catalog promotion is a separate checked step. It is not a release, push, deployment, complete project test suite, biological certification, whole-paper reproduction or whole-goal completion. The canonical executable and every frozen parent remain preserved.
'''
    (folder/'WARNINGS.md').write_text(warning,encoding='utf8',newline='\n')
    layout=pack(folder/'controls.zip',evidence)
    proof=dict(schema_version=1,status='passed-public-scoped-Signac-tools-workflows',
        artifact=dict(path=JAR.relative_to(ROOT).as_posix(),**artifact),source_pins=source_pins,
        archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,
        parents=parents,parent_source_checks=source_checks,canonical_executable_preserved=canonical,
        tests_per_JVM=1679,JVMs=3,public_workflows=11,public_analyses=21,portable_verifications=51,
        expected_runtime_skips=2,expected_failures=2,prior_scientific_classes_preserved=True,
        native_normalization_standalone=True,root_dispatch_available=True,source_roles_reviewed=10,
        analysis_installed_software=False,historical_whole_packages=False,independent_all_upstream_fit_replay=False,
        PNG_pixel_replay=False,execution_authentication=False,biological_validity_certified=False,
        whole_paper_reproduced=False,full_project_test_suite_run=False,catalog_implementation_credit=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in source_pins.items())
    save(folder/'VALIDATION.json',proof)
    (folder/'SHA256SUMS').write_text(''.join(pin(folder/n)['sha256']+'  '+n+'\n' for n in ['VALIDATION.json','controls.zip','WARNINGS.md']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=proof['status'],archive=proof['archive'],logical_files=layout['logical_files'],source_pins=len(source_pins))))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable freeze guards')
    main()
