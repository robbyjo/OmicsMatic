# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal scoped original scDRS workflows and retained attempts without changing catalog or live root."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from signac_scan_evidence import pack, byte_pin
from verify_scdrs_workflow_evidence import HOME, PREFIX, STATUS, SOURCE_FILES, inspect


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists() and not HOME.exists()
    out.mkdir(parents=True)
    HOME.mkdir(parents=True)
    (HOME / '.gitattributes').write_bytes(b'* -text\n')
    state = dict(status='FAIL2_SCDRS_WORKFLOW_EVIDENCE_SEAL', new_catalog_credit=False)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    try:
        matrix = json.loads((BASE / 'workflow-validation-2/COMPLETED.json').read_bytes())
        assert matrix['status'] == 'PASS_TYPED_SCDRS_WORKFLOWS_MATCH_ORIGINAL_CLI_DATA_AND_THREE_SAVED_JVMS'
        assert len(matrix['cases']) == 23 and matrix['numerical_and_gene_weight_coordinates'] == 877998
        current = ['workflow-validation-2', 'workflow-safety-2', 'bh-reference-2', 'dispatch-validation-1',
            'workflow-build21-7', 'workflow-build25-6', 'dispatch-build21-1', 'dispatch-build25-1',
            'exported-setup-controls-1', 'capacity-extension-1', 'workflow-lock-1', 'workflow-notice-intake-1',
            'workflow-profile-1.0.0-1', 'workflow-profile-1.0.1-1', 'workflow-profile-1.0.2-1',
            'workflow-profile-installed-1', 'workflow-reference-setup-1', 'thousand-gene-controls-1',
            'consolidated-workflow-cases-1', 'paper-role-drafts-1']
        prior = ['original-cli-foundation-1', 'original-cli-foundation-2', 'typed-workflow-smoke-1',
            'workflow-validation-1', 'workflow-extra-validation-1', 'workflow-p169-version-validation-1',
            'workflow-safety-1', 'bh-reference-1', 'workflow-controls-1', 'workflow-species-covariate-controls-1',
            'extra-workflow-cases-1', 'p169-control-case-1', 'workflow-lock-intake-1',
            *['workflow-build21-' + str(i) for i in range(1, 7)],
            *['workflow-build25-' + str(i) for i in range(1, 6)]]
        omitted = []
        objects = {}

        def intern(raw):
            digest = byte_pin(raw)['sha256']
            if digest in objects:
                assert objects[digest] == raw
            else: objects[digest] = raw
            return objects[digest]

        def collect(groups):
            result = {}
            for group in groups:
                directory = BASE / group
                assert directory.is_dir(), group
                for path in sorted(directory.rglob('*')):
                    if not path.is_file(): continue
                    relative = path.relative_to(directory)
                    parts = relative.parts
                    setup = group.startswith('workflow-profile-') or group in ['workflow-lock-1', 'workflow-reference-setup-1', 'workflow-lock-intake-1']
                    skipped = setup and parts[0] in ['scdrs-source', 'archives', '.conda', '.mamba', 'mamba-root', 'micromamba', 'wheelhouse', 'wheels', 'numba-cache', 'matplotlib-cache']
                    skipped |= group == 'exported-setup-controls-1' and any(s in parts for s in ['scdrs-source', 'numba-cache', 'matplotlib-cache'])
                    if skipped:
                        omitted.append(dict(path=path.relative_to(ROOT).as_posix(), **pin(path)))
                        continue
                    assert not path.is_symlink(), path
                    assert not path.name.startswith('private-primary-'), path
                    result[PREFIX + group + '/' + relative.as_posix()] = intern(path.read_bytes())
            return result

        current_files = collect(current)
        prior_files = collect(prior)
        # Census receipts contain pointers and hashes only; full primary paragraphs stay private.
        for group, target in [('paper-census-plural-1', current_files), ('paper-census-1', prior_files)]:
            target[PREFIX + group + '/COMPLETED.json'] = intern((BASE / group / 'COMPLETED.json').read_bytes())
        failed = BASE / 'workflow-seal-1'
        assert json.loads((failed / 'COMPLETED.json').read_bytes())['status'] == 'FAIL2_SCDRS_WORKFLOW_EVIDENCE_SEAL'
        prior_files[PREFIX + 'workflow-seal-1/COMPLETED.json'] = (failed / 'COMPLETED.json').read_bytes()
        retained = {p.relative_to(failed).as_posix(): pin(p) for p in sorted((failed / 'retained-public-files').iterdir()) if p.is_file()}
        assert retained and retained['retained-public-files/prior-attempts.zip']['bytes'] > 95_000_000
        prior_files[PREFIX + 'workflow-seal-1/retained-public-file-pins.json'] = (json.dumps(retained, indent=2) + '\n').encode()
        second = BASE / 'workflow-seal-2'
        assert json.loads((second / 'COMPLETED.json').read_bytes())['status'] == 'FAIL2_SCDRS_WORKFLOW_EVIDENCE_SEAL'
        prior_files[PREFIX + 'workflow-seal-2/COMPLETED.json'] = (second / 'COMPLETED.json').read_bytes()
        retained = {p.relative_to(second).as_posix(): pin(p) for p in sorted((second / 'retained-public-files').iterdir()) if p.is_file()}
        prior_files[PREFIX + 'workflow-seal-2/retained-public-file-pins.json'] = (json.dumps(retained, indent=2) + '\n').encode()
        for name in SOURCE_FILES:
            current_files['current-source/' + name] = intern((ROOT / name).read_bytes())
        archives = {}
        for name, files in [('controls.zip', current_files), ('prior-attempts.zip', prior_files)]:
            record = pack(HOME / name, files)
            assert (HOME / name).stat().st_size < 95_000_000, ('Retain and repartition oversized archive', name)
            archives[name] = dict(archive=pin(HOME / name), **record)
        artifacts = {}
        for compiler, group in [('21', 'workflow-build21-7'), ('25', 'workflow-build25-6')]:
            for source, name in [(BASE / group / 'scdrs-workflow-development.jar', 'scdrs-workflow-javac' + compiler + '.jar'),
                (BASE / ('dispatch-build' + compiler + '-1') / 'scdrs-root-supplement.jar', 'scdrs-root-javac' + compiler + '.jar')]:
                shutil.copyfile(source, HOME / name)
                assert pin(source) == pin(HOME / name)
                artifacts[name] = pin(HOME / name)
        predecessors = {}
        for folder in ['scdrs-native-development', 'mtag-workflow-development']:
            for path in sorted((ROOT / 'docs/literature' / folder).iterdir()):
                if path.is_file(): predecessors[path.relative_to(ROOT).as_posix()] = pin(path)
        saved = ['workflow-validation-2/' + c['case'] + '/bundle' for c in matrix['cases']] + ['dispatch-validation-1/bundle']
        proof = dict(schema_version=1, status=STATUS, new_catalog_credit=False, workflow_ready_for_catalog=True,
            staged_root_command_available=True, live_root_modified=False, catalog_promotion_pending=True,
            source_roles_reviewed=6, supported_source_versions=['1.0.0', '1.0.1', '1.0.2'],
            original_workflow_cases=23, original_output_coordinates=877998, saved_jvm_checks=69,
            root_original_workflows=1, root_saved_jvm_checks=3, refusal_and_saved_tamper_controls=36,
            bh_exact_double_values=67161, bh_strict_cutoff_decisions=67161,
            whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False,
            independent_scoring_loess_covariate_or_graph_refit=False, hashes_are_authenticity=False,
            large_atlas_benchmarked=False, participant_data_or_article_prose_distributed=False,
            upstream_runtime_binaries_or_bulk_package_archives_distributed=False,
            bundled_three_source_audited_wheels_distributed=True,
            source_pins={name: pin(ROOT / name) for name in SOURCE_FILES}, artifacts=artifacts, archives=archives,
            predecessor_evidence_files=predecessors, saved_bundle_paths=saved,
            retained_current_groups=current, retained_prior_groups=prior,
            omitted_setup_runtime_extractions_archives_and_caches=omitted,
            omission_scope='Only setup source extractions, large runtime archives and setup caches are omitted and pinned. Complete installed/source inventories, dependency lock, setup logs and three small source-audited bundled wheels remain. Original scDRS source snapshots are retained in the pinned native predecessor. Every selected successful synthetic analysis bundle and refused/tampered test attempt is retained complete. Full primary article prose remains private.',
            canonical_executable_unchanged=before,
            required_action='Read SCDRS_INTEGRATION.md. Require wrapper exit0, result_admitted=true, COMPLETED.json and complete saved --verify before interpretation. SKIP3 skips dependent work when exact permitted prerequisites are missing or forbidden; supply them where permitted or leave it skipped. FAIL2 admits no partial, repaired or nonfinite result. Preserve each complete attempt and resolve its named cause before retrying NEW paths. Runtime setup is explicit and separate from analysis. Catalog and live-root promotion remain pending; the full250-paper goal is unfinished.')
        save(HOME / 'VALIDATION.json', proof)
        verified, files = inspect()
        assert verified == proof
        (HOME / 'SHA256SUMS').write_text(''.join(pin(path)['sha256'] + '  ' + path.name + '\n' for path in sorted(HOME.iterdir()) if path.is_file() and path.name != 'SHA256SUMS'), encoding='utf8', newline='\n')
        state.update(status='PASS_SCDRS_WORKFLOW_EVIDENCE_SEALED_WITHOUT_CATALOG_CREDIT',
            validation=pin(HOME / 'VALIDATION.json'), artifacts=artifacts, logical_files=len(files),
            archive_bytes={n: pin(HOME / n)['bytes'] for n in archives})
    except Exception as error:
        import traceback
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(canonical) == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable complete evidence, source, failed-attempt or canonical preservation checks')
    raise SystemExit(main())
