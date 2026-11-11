# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify scoped original scDRS workflow evidence; optionally replay complete saved bundles without fitting."""
import argparse
import gzip
import json
import os
import subprocess
import sys
import traceback
import zipfile
from pathlib import Path, PureWindowsPath
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux
from signac_scan_evidence import unpack, byte_pin, safe
from catalog_source_integrity import verify_source, reverse_delta

HOME = ROOT / 'docs/literature/scdrs-workflow-development'
PREFIX = 'build/literature-review/scdrs-runtime/'
STATUS = 'PASS_SCOPED_ORIGINAL_SCDRS_THREE_VERSIONS_TYPED_WORKFLOWS_AND_STAGED_ROOT'
HELPERS = ['build_scdrs_workflow_development', 'build_scdrs_dispatch', 'create_scdrs_workflow_controls',
    'create_scdrs_species_covariate_controls', 'create_scdrs_thousand_gene_controls', 'validate_scdrs_workflows',
    'validate_scdrs_tools_safety', 'validate_scdrs_bh_reference', 'validate_scdrs_export_setup',
    'validate_scdrs_capacity_extension', 'validate_scdrs_dispatch', 'intake_scdrs_workflow_lock',
    'intake_scdrs_workflow_notices', 'intake_scdrs_paper_census', 'scdrs_registry', 'curate_scdrs_uses',
    'seal_scdrs_workflow_evidence', 'verify_scdrs_workflow_evidence']
SOURCE_FILES = [
    *[p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('Scdrs*.java'))],
    *[p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'src/main/resources/org/jlinalg/omics/scdrs').iterdir()) if p.is_file()],
    'src/test/java/org/jlinalg/cli/ScdrsCapacityReferenceProbe.java',
    'src/test/java/org/jlinalg/cli/ScdrsWorkflowReferenceProbe.java',
    'src/main/java/org/jlinalg/cli/JLinAlgCli.java', 'docs/literature/SCDRS_INTEGRATION.md',
    *['tools/literature/' + n + '.py' for n in HELPERS]]


def j(files, name):
    return json.loads(files[PREFIX + name])


def subtree(files, relative):
    prefix = PREFIX + relative.rstrip('/') + '/'
    return {n[len(prefix):]: v for n, v in files.items() if n.startswith(prefix)}


def bundle(files, relative):
    raw = subtree(files, relative)
    done = json.loads(raw['COMPLETED.json'])
    assert done['status'] == 'COMPLETE_SCOPED_SCDRS_BUNDLE' and done['result_admitted']
    assert done['manifest'] == byte_pin(raw['manifest.json'])
    manifest = json.loads(raw['manifest.json'])
    assert manifest == dict(schema_version=1, files={n: byte_pin(v) for n, v in raw.items() if n not in ('COMPLETED.json', 'manifest.json')})
    session = json.loads(raw['session.json'])
    assert session['status'] == 'PASS_SCOPED_ORIGINAL_SCDRS_WORKFLOW' and session['result_admitted'] and session['runtime_unchanged']
    assert not session['whole_paper_reproduced'] and not session['producer_authenticated']
    audit = json.loads(raw['analysis-summary.json'])
    assert audit['all_output_coordinates_checked'] and not audit['independent_scoring_loess_covariate_or_graph_refit'] and not audit['producer_authenticated']
    assert raw['source-worker.py'] == files['current-source/src/main/resources/org/jlinalg/omics/scdrs/run_scdrs_workflow.py']
    return raw, audit


def inspect(home=HOME):
    proof = json.loads((home / 'VALIDATION.json').read_bytes())
    assert proof['status'] == STATUS and proof['workflow_ready_for_catalog'] and proof['source_roles_reviewed'] == 6
    assert not proof['new_catalog_credit'] and not proof['live_root_modified'] and not proof['whole_goal_complete']
    assert set(proof['source_pins']) == set(SOURCE_FILES)
    data = ROOT / 'docs/literature/omics-2023-2026'
    for n, wanted in proof['source_pins'].items():
        verify_source(ROOT, data, n, wanted['sha256'])
    files = {}
    for name, record in proof['archives'].items():
        assert pin(home / name) == record['archive']
        part = unpack(home / name, record)
        assert not set(files) & set(part)
        files.update(part)
    for n, wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/' + n]) == wanted
    for n, wanted in proof['artifacts'].items():
        assert pin(home / n) == wanted
    for n, wanted in proof['predecessor_evidence_files'].items():
        assert pin(ROOT / n) == wanted, ('Changed predecessor scientific evidence', n)
    from verify_scdrs_native_evidence import inspect as native
    native()
    matrix = j(files, 'workflow-validation-2/COMPLETED.json')
    assert matrix['status'] == 'PASS_TYPED_SCDRS_WORKFLOWS_MATCH_ORIGINAL_CLI_DATA_AND_THREE_SAVED_JVMS'
    assert matrix['original_data_bytes_identical'] and len(matrix['cases']) == 23 and matrix['saved_bundle_verifications'] == 69
    assert matrix['numerical_and_gene_weight_coordinates'] == 877998
    cases = j(files, 'workflow-validation-2/cases.json')
    assert [c['case'] for c in cases] == [c['case'] for c in matrix['cases']]
    saved = []
    for row, case in zip(matrix['cases'], cases):
        relative = 'workflow-validation-2/' + row['case'] + '/bundle'
        raw, audit = bundle(files, relative)
        assert audit['version'] == row['version'] and audit['operation'] == row['operation']
        assert row['saved_platforms'] == ['windows21', 'windows25', 'linux21']
        original = {n.removeprefix('original/'): v for n, v in raw.items() if n.startswith('original/')}
        if case['preexisting_original_output']:
            parts = PureWindowsPath(case['preexisting_original_output']).parts
            index = parts.index('scdrs-runtime')
            gold_path = '/'.join(parts[index + 1:])
        else:
            gold_path = 'workflow-validation-2/' + row['case'] + '/golden/original-output'
        gold = subtree(files, gold_path)
        assert set(original) == set(gold) and len(original) == row['original_output_files']
        for name in original:
            a, b = original[name], gold[name]
            if name.endswith('.gz'): a, b = gzip.decompress(a), gzip.decompress(b)
            assert a == b, ('Original CLI data differ', row['case'], name)
        saved.append(relative)
    root = j(files, 'dispatch-validation-1/COMPLETED.json')
    assert root['status'] == 'PASS_STAGED_SCDRS_ROOT_ORIGINAL_WORKFLOW_THREE_SAVED_JVMS_AND_LEGACY_ROUTES'
    assert (root['root_help_checks'], len(root['tool_help_checks']), root['original_analyses'], root['saved_replays']) == (3, 9, 1, 3)
    assert root['original_data_bytes_identical'] and root['export_passed'] and not root['predecessor_scientific_bytes_changed']
    bundle(files, 'dispatch-validation-1/bundle')
    saved.append('dispatch-validation-1/bundle')
    assert saved == proof['saved_bundle_paths']
    safety = j(files, 'workflow-safety-2/COMPLETED.json')
    assert safety['status'] == 'PASS_SCDRS_PREREQUISITE_INPUT_PROCESS_AND_RESEALED_SAVED_CONTRACT_CONTROLS_ONLY'
    assert len(safety['refused_runs']) == 15 and len(safety['saved_refusals']) == 21 and safety['baseline_unchanged']
    assert all(r['exit_code'] in (2, 3) and not r.get('result_admitted', False) for r in safety['refused_runs'])
    assert all(r['refused'] and r['verifier_read_only'] for r in safety['saved_refusals'])
    assert not safety['saved_checks_claim_independent_refitting_or_authenticity']
    bh = j(files, 'bh-reference-2/COMPLETED.json')
    assert bh['status'] == 'PASS_NATIVE_SCDRS_BH_EXACT_STATSMODELS_AND_STRICT_CUTOFF_THREE_JVMS' and bh['cases'] == 19
    assert len(bh['summaries']) == 3 and all(r['exact_double_q_values'] == r['strict_cutoff_decisions'] == 22387 for r in bh['summaries'])
    assert not bh['source_repaired']
    exported = j(files, 'exported-setup-controls-1/COMPLETED.json')
    assert exported['status'] == 'PASS_EXPORTED_SCDRS_ASSETS_OFFLINE_PROFILE_AND_PREREQUISITE_INTEGRITY_REFUSALS_ONLY'
    assert exported['no_implicit_download'] and exported['corrupted_archive_refused'] and not exported['scientific_analysis_executed']
    for name, wanted in exported['exported_assets'].items():
        assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/scdrs/' + name]) == wanted
    census = j(files, 'paper-census-plural-1/COMPLETED.json')
    assert census['papers_audited'] == 250 and len(census['mention_papers']) == 6 and all(r['matched_by_registry'] for r in census['mention_papers'])
    roles = j(files, 'paper-role-drafts-1/COMPLETED.json')
    assert roles['status'] == 'PASS_SIX_SCDRS_PRIMARY_ROLES_AND_PLURAL_CENSUS_DRAFT' and not roles['applied']
    lock = json.loads(files['current-source/src/main/resources/org/jlinalg/omics/scdrs/scdrs-workflow-lock.json'])
    assert len(lock['packages']) == 25 and len(lock['wheels']) == 46
    for version in ['1.0.0', '1.0.1', '1.0.2']:
        profile = j(files, 'workflow-profile-' + version + '-1/runtime.json')
        assert profile['source_commit'] == lock['sources'][version]['commit'] and profile['version'] == version
        assert profile['source_lock'] == byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/scdrs/scdrs-workflow-lock.json'])
        assert profile['preparer'] == byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/scdrs/prepare_scdrs_runtime.py'])
        assert profile['versions']['distributions'] == lock['installed_distributions'] and profile['setup_only']
        assert not profile['hashes_are_authenticity']
        assert j(files, 'workflow-profile-' + version + '-1/setup.json')['status'] == 'PASS_PRIVATE_HISTORICAL_SCDRS_RUNTIME_PROFILE_ONLY'
    installed = j(files, 'workflow-profile-installed-1/setup.json')
    assert installed['status'] == 'PASS_PRIVATE_HISTORICAL_SCDRS_RUNTIME_PROFILE_ONLY' and not installed['analysis_executed']
    from build_mtag_dispatch import without_line_numbers
    for compiler, group in [('21', 'workflow-build21-7'), ('25', 'workflow-build25-6')]:
        standalone = home / ('scdrs-workflow-javac' + compiler + '.jar')
        supplement = home / ('scdrs-root-javac' + compiler + '.jar')
        predecessor = ROOT / 'docs/literature/mtag-workflow-development' / ('mtag-root-javac' + compiler + '.jar')
        with zipfile.ZipFile(standalone) as worker, zipfile.ZipFile(supplement) as rootjar, zipfile.ZipFile(predecessor) as old:
            assert all(rootjar.read(n) == old.read(n) for n in old.namelist() if n != 'org/jlinalg/cli/JLinAlgCli.class')
            for n in worker.namelist():
                if n.endswith('.class') or n.startswith('org/jlinalg/omics/scdrs/'):
                    if n == 'org/jlinalg/cli/SimpleJson.class': assert without_line_numbers(rootjar.read(n)) == without_line_numbers(worker.read(n))
                    else: assert rootjar.read(n) == worker.read(n)
        delta = j(files, 'dispatch-build' + compiler + '-1/staged-root-delta.json')['paths'][0]
        before = files[PREFIX + 'dispatch-build' + compiler + '-1/root.before.java']
        after = files[PREFIX + 'dispatch-build' + compiler + '-1/source-snapshot/src/main/java/org/jlinalg/cli/JLinAlgCli.java']
        assert len(delta['edits']) == 2 and reverse_delta(after, delta) == before
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof, files


def replay(platform, out, proof, files):
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SCDRS_SAVED_WORKFLOW_REPLAY', original_fits_executed=False, new_catalog_credit=False)
    try:
        compiler = '25' if platform == 'windows25' else '21'
        jar = HOME / ('scdrs-workflow-javac' + compiler + '.jar')
        java = Path('C:/Program Files') / ('Common Files/Oracle/Java/javapath/java.exe' if compiler == '25' else 'Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')
        prefix = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java'] if platform == 'linux21' else [str(java)]
        version = subprocess.run(prefix + ['-version'], capture_output=True, stdin=subprocess.DEVNULL, timeout=30)
        (out / 'java-version.stderr').write_bytes(version.stderr)
        if version.returncode: raise FileNotFoundError('Selected permitted Java prerequisite unavailable')
        def census(directory):
            return {p.relative_to(directory).as_posix(): pin(p) for p in sorted(directory.rglob('*')) if p.is_file()}
        records = []
        for relative in proof['saved_bundle_paths']:
            target = out / 'bundles' / relative
            target.mkdir(parents=True)
            for name, raw in subtree(files, relative).items():
                safe(name)
                path = target / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(raw)
            before = census(target)
            command = prefix + ['-Xmx2g', '-jar', linux(jar) if platform == 'linux21' else str(jar), '--verify', linux(target) if platform == 'linux21' else str(target)]
            label = relative.replace('/', '_')
            with (out / (label + '.stdout')).open('xb') as so, (out / (label + '.stderr')).open('xb') as se:
                result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=600)
            save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode))
            assert result.returncode == 0 and census(target) == before, relative
            records.append(dict(bundle=relative, passed=True))
        state.update(status='PASS24_SCDRS_SAVED_WORKFLOW_REPLAYS_WITHOUT_PYTHON', platform=platform, cases=records, bundles_unchanged=True)
    except FileNotFoundError as error:
        state.update(status='SKIP3_SCDRS_SAVED_JAVA_PREREQUISITE_UNAVAILABLE', cause=repr(error))
    except Exception as error:
        state['cause'] = repr(error)
        (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    state['required_action'] = 'SKIP dependent verification/interpretation if exact permitted Java is unavailable. FAIL2 admits no verified result. Preserve every attempt, resolve its named cause and retry NEW paths. This helper installs nothing and performs no scientific Python fit.'
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Complete saved Java contract replay only.') + '\n\nREQUIRED ACTION: ' + state['required_action'] + '\n', encoding='utf8')
    save(out / 'COMPLETED.json', state)
    print(state['status'], platform, state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS') else 3 if state['status'].startswith('SKIP3') else 2


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--replay', choices=['windows21', 'windows25', 'linux21'])
    p.add_argument('--out', type=Path)
    a = p.parse_args()
    try:
        proof, files = inspect()
        if a.replay:
            assert a.out is not None
            return replay(a.replay, a.out.resolve(), proof, files)
        assert a.out is None
        print(json.dumps(dict(status=STATUS, workflow_cases=23, coordinates=877998, saved_bundles=24, safety_controls=36, source_roles_reviewed=6, new_catalog_credit=False)))
        return 0
    except Exception as error:
        print('FAIL2_SCDRS_WORKFLOW_EVIDENCE: ' + repr(error) + '\nREQUIRED ACTION: retain evidence and resolve the named source/archive/contract mismatch. No repaired or partial result is admitted.', file=sys.stderr)
        traceback.print_exc()
        return 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable complete source, evidence, original-output or predecessor checks')
    raise SystemExit(main())
