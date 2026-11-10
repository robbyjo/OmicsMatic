# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only checks/materialization of the original CellChat network checkpoint."""
import argparse, json
from pathlib import Path, PurePosixPath
from verify_cellchat_strength_evidence import Evidence, fingerprint, pin, rows
ROOT = Path(__file__).resolve().parents[2]


def verify(home, check_current=False, materialize=None, target=None):
    proof = json.loads((home / 'VALIDATION.json').read_bytes())
    assert proof['status'] == 'PASS_ORIGINAL_CELLCHAT_NETWORK_DEVELOPMENT_ONLY'
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and not proof['all_cellchat_roles_completed']
    assert (proof['new_scoped_integrations'], proof['including_existing_adapters'], proof['missing']) == (93, 96, 248)
    for name, value in {**proof['development_jars'], **proof['example_and_addendum_files']}.items():
        assert pin(home / name) == value
    if check_current:
        for name, value in proof['source_pins'].items():
            assert pin(ROOT / name) == value, name
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    with Evidence(home, proof) as e:
        portable = 0
        for name in proof['portable_suites']:
            record = e.json('evidence/' + name + '/COMPLETED.json')
            assert record['status'] == 'PASS_COMPLETE_ORIGINAL_NETWORK_OFFLINE_BUNDLES'
            assert record['bundles'] == len(record['records']) == 4 and not record['R_or_Python_model_executed']
            assert all(r['exit_code'] == 0 for r in record['records'])
            portable += record['bundles']
        assert portable == 24
        reference_checks = refits = 0
        for name in ['network-reference-checks-1', 'network-reference-variant-1']:
            r = e.json('evidence/' + name + '/checks/COMPLETED.json')
            assert r['status'] == 'PASS_ORIGINAL_NETWORK_TRANSPORT_AND_CAPTURED_STATE_CLUSTER_REFITS'
            assert not r['UMAP_independently_reproduced'] and not r['parallel_clustering_reproduced']
            reference_checks += len(r['checks'])
            refits += len(r['sequential_original_refits'])
        assert (reference_checks, refits) == (34, 4)
        r = e.json('evidence/network-admission-controls-2/COMPLETED.json')
        assert r['status'] == 'PASS_NETWORK_SKIP_FAILURE_AND_REHASHED_REFUSALS'
        assert (r['analysis_dispositions'], r['offline_tamper_refusals']) == (12, 11)
        assert r['owned_timeout_terminal'] and len(r['records']) == 23
        assert all(v['exit_code'] == v['expected_exit'] for v in r['records'])
        timeout = e.json('evidence/network-admission-controls-2/owned-timeout/analysis/COMPLETED.json')
        assert not timeout['result_admitted'] and timeout['status'].startswith('FAIL2')
        terminal = e.json('evidence/network-admission-controls-2/owned-timeout/analysis/owned-children.json')
        assert terminal['all_terminal']
        first = e.json('evidence/network-admission-controls-1/COMPLETED.json')
        assert first['status'] == 'FAIL2_NETWORK_ADMISSION_CONTROLS'
        assert 'FileNotFoundError' in first['error']
        resource = e.json('evidence/network-standard-resource-check-1/COMPLETED.json')
        assert resource['status'] == 'PASS_STANDARD_AND_BOTH_ISOLATED_NETWORK_RESOURCES_IDENTICAL'
        assert resource['held_files_unchanged'] == 18
        delta = e.json('evidence/network-final-controller-delta-1/COMPLETED.json')
        assert delta['status'] == 'PASS_ONLY_OWNED_CONTROLLER_AND_DEBUG_LINE_TABLES_CHANGED'
        assert delta['original_R_and_native_scientific_checks_unchanged']
        qa = e.json('evidence/network-plot-qa-2/OBSERVED.json')
        assert qa['status'] == 'PASS_READ_ONLY_ORIGINAL_PLOT_REVIEW_WITH_SOURCE_LIMITATION'
        assert len(qa['reviewed']) == 4 and not qa['original_plots_changed']
        for case in proof['complete_bundles']:
            prefix = 'evidence/' + case + '/'
            complete = e.json(prefix + 'COMPLETED.json')
            assert complete['result_admitted'] is True
            assert complete['status'] == 'completed-original-cellchat-network-with-source-warnings'
            assert {n for n in proof['members'] if n.startswith(prefix)} == {prefix+n for n in complete['files']} | {prefix+'COMPLETED.json'}
            for name, value in complete['files'].items():
                assert fingerprint(e.raw(prefix+name)) == value
            report = e.json(prefix + 'results/worker-report.json')
            assert report['status'] == 'COMPLETED_ORIGINAL_CELLCHAT_NETWORK_WORKER_ONLY' and not report['analysis_admitted']
            warning = e.raw(prefix + 'WARNINGS.md').decode('utf8')
            provenance = e.json(prefix + 'provenance.json')
            assert complete['required_action'] in warning and 'ORIGINAL UMAP REPRODUCIBILITY WARNING' in warning
            assert not provenance['native_UMAP_and_kmeans_independently_refitted']
            assert not provenance['source_similarity_rescaled'] and not provenance['exact_whole_pipeline_replay_claimed']
            assert provenance['original_parallel_rng_warnings'] == (8 if 'parallel-' in case else 0)
            for field, value in provenance['parent_original_source_diagnostics'].items():
                assert field+'='+str(value) in warning
            for kind in ['functional', 'structural']:
                points = rows(e.raw(prefix+'results/'+kind+'/points.tsv'))
                assert len(points) == (7 if 'fixed-k' in case else 8)
                assert len({r['cluster'] for r in points}) == (2 if 'fixed-k' in case else 3)
        addendum = json.loads((home / 'P085-network-source-addendum.json').read_bytes())
        assert addendum['required_figure_role']['figure_id'] == 'Fig14'
        assert not addendum['primary_article_text_redistributed'] and not addendum['historical_execution_claimed']
        if materialize is not None:
            assert target is not None and not target.exists()
            prefix = materialize.rstrip('/') + '/'
            selected = [n for n in proof['members'] if n.startswith(prefix)]
            assert selected, 'Unknown evidence prefix'
            target.mkdir(parents=True)
            for name in sorted(selected, key=lambda n: (n.endswith('/COMPLETED.json'), n)):
                relative = PurePosixPath(name[len(prefix):])
                dest = target.joinpath(*relative.parts)
                assert dest.resolve().is_relative_to(target.resolve())
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(e.raw(name))
            print('Materialized exact evidence into NEW directory:', target)
    print(json.dumps(dict(status='PASS_READ_ONLY_ORIGINAL_CELLCHAT_NETWORK_EVIDENCE', complete_bundles=4,
          portable_bundle_checks=24, separate_original_checks=34, captured_state_sequential_refits=4,
          analysis_dispositions=12, rehashed_tamper_refusals=11, new_catalog_credit=False,
          whole_goal_complete=False), indent=2))
    return proof


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable frozen evidence checks')
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home', type=Path, default=ROOT / 'docs/literature/cellchat-network-development')
    p.add_argument('--check-current-source', action='store_true')
    p.add_argument('--materialize')
    p.add_argument('--out', type=Path)
    a = p.parse_args()
    try:
        verify(a.home, a.check_current_source, a.materialize, a.out)
    except Exception as error:
        raise SystemExit('FAIL2 CELLCHAT NETWORK EVIDENCE: ' + repr(error) +
            '\nREQUIRED ACTION: Preserve the attempt and correct the named integrity/source mismatch. '
            'Materialization alone admits no result. No model execution or installation was performed.')
