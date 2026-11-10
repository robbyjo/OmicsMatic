# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged predecessor scientific gates and all seven frozen CellChat component gates."""
import argparse, contextlib, gzip, importlib, io, json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from catalog_source_integrity import verify_source
from diamond_catalog import current_catalog_review_context, verify_review_extension
from run_cistarget_catalog_checks import ArchivedCatalogView
BASE = ROOT / 'build/literature-review/diamond-runtime'
PREVIOUS = ['soupx_markers_evidence', 'soupx_ambient_core_evidence', 'soupx_workflow_evidence',
    'dndscv_workflow_evidence', 'smr_statistics_evidence', 'smr_workflow_evidence', 'loftee_audit_evidence',
    'loftee_workflow_evidence', 'verify_cellphonedb_review_context', 'biomart_workflow_evidence',
    'hail_sample_qc_evidence', 'hail_workflow_evidence', 'leafcutter_clusters_evidence',
    'leafcutter_phenotypes_evidence', 'leafcutter_models_evidence', 'leafcutter_workflow_evidence',
    'cellphonedb_statistics_evidence', 'cellphonedb_preparation_evidence',
    'validate_cellphonedb_integration_evidence', 'cellphonedb_hash_seed_evidence',
    'spyrrow_communication_evidence', 'palantir_multiscale_evidence', 'palantir_workflow_evidence',
    'simpleppt_math_evidence', 'scfates_workflow_evidence', 'gseapy_current_evidence',
    'hifiasm_execution_evidence', 'rsubread_matrix_evidence', 'rsubread_result_evidence',
    'rsubread_execution_evidence', 'sniffles_execution_evidence', 'mrmega_workflow_evidence',
    'cistarget_development_evidence']
COMPONENTS = [('verify_cellchat_development', 'cellchat-development'),
    ('verify_cellchat_projection', 'cellchat-projection-development'),
    ('verify_cellchat_legacy_evidence', 'cellchat-legacy-development'),
    ('verify_cellchat_inference_evidence', 'cellchat-inference-development'),
    ('cellchat_workflow_evidence', 'cellchat-workflow-development'),
    ('verify_cellchat_strength_evidence', 'cellchat-strength-development'),
    ('verify_cellchat_network_evidence', 'cellchat-network-development')]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    data = ROOT/'docs/literature/omics-2023-2026'
    renderer = json.loads((BASE/'catalog-render-1/COMPLETED.json').read_bytes())
    assert renderer['status'] == 'PASS_DIAMOND_CATALOG_RENDER399_UNRELATED_TOOL_OBJECTS_UNCHANGED'
    assert all(pin(ROOT/n) == v for n, v in renderer['rendered_files'].items()), 'Render/checksums must be current before gates'
    save(out/'STARTED.json', dict(producer=pin(Path(__file__).resolve()), render=pin(BASE/'catalog-render-1/COMPLETED.json'),
        current_catalog_inputs=renderer['rendered_files'], scope='40 unchanged scientific gates; no model execution or installation'))
    modules = {n: importlib.import_module(n) for n in PREVIOUS}
    checks = {}
    overrides = {'tools/literature/reviewed-uses.json': gzip.decompress((data/'palantir-prior-reviews.json.gz').read_bytes()),
        'docs/literature/omics-2023-2026/papers.json': gzip.decompress((data/'palantir-prior-papers.json.gz').read_bytes())}
    assert len(json.loads(overrides['tools/literature/reviewed-uses.json'])['reviews']) == 980
    with current_catalog_review_context():
        verify_review_extension(ROOT, data, json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
        for name, module in modules.items():
            captured, saved_root = io.StringIO(), None
            if name == 'verify_cellphonedb_review_context':
                saved_root = module.ROOT
                module.ROOT = ArchivedCatalogView(ROOT, overrides)
            try:
                with contextlib.redirect_stdout(captured):
                    result = module.verify()
            finally:
                if saved_root is not None:
                    module.ROOT = saved_root
            (out/(name+'.stdout')).write_text(captured.getvalue(), encoding='utf8', newline='\n')
            if isinstance(result, tuple):
                result = result[0]
            checks[name] = dict(status=result['status'], scope='exact archived980-role metadata; all scientific paths live'
                if saved_root is not None else 'current live scientific sources with exact review-extension context')
            print(name+': passed', flush=True)
    for name, folder in COMPONENTS:
        module = importlib.import_module(name)
        home = ROOT/'docs/literature'/folder
        proof = json.loads((home/'VALIDATION.json').read_bytes())
        # Frozen historical catalog counts are checked inside the original capsule.
        # Current scientific sources are independently checked here without relaxing a verifier.
        for relative, wanted in proof['source_pins'].items():
            verify_source(ROOT, data, relative, wanted['sha256'])
        captured = io.StringIO()
        with contextlib.redirect_stdout(captured):
            module.verify(home, False)
        (out/(name+'.stdout')).write_text(captured.getvalue(), encoding='utf8', newline='\n')
        checks[name] = dict(status='passed-unchanged-frozen-component-and-live-source-pins',
            proof=pin(home/'VALIDATION.json'), scope='frozen original capsule plus strict live scientific source pins; no model refit or installation')
        print(name+': passed', flush=True)
    from verify_diamond_evidence import verify as diamond_verify
    diamond_report, _ = diamond_verify()
    checks['verify_diamond_evidence'] = dict(status=diamond_report['status'], scope='complete frozen original DIAMOND source/transport evidence with strict live source pins')
    assert len(checks) == 41
    files = [Path(__file__).resolve(), ROOT/'tools/literature/diamond_catalog.py',
             *[ROOT/'tools/literature'/(n+'.py') for n in [*PREVIOUS, *[n for n, _ in COMPONENTS]]]]
    save(out/'COMPLETED.json', dict(status='PASS41_UNCHANGED_PREDECESSOR_CELLCHAT_AND_DIAMOND_SCIENTIFIC_GATES',
        checks=checks, current_reviews=1029, prior_metadata_reviews=980,
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in files}, scientific_verifiers_changed=False,
        scientific_source_allowlist_changed=False, whole_goal_complete=False))
    print('Passed41 gates; unchanged verifiers and explicit historical metadata scopes', flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable scientific source gates')
    main()
