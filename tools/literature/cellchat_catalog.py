# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""CellChat catalog extension with exact predecessor metadata and live scientific sources."""
import contextlib, copy, gzip, html, json
from cistarget_catalog import *
import cistarget_catalog as prior
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import verify_source, reverse_delta
from run_mrmega_catalog_checks import ArchivedCatalogView

_previous_verify = prior.verify_review_extension


def _current_extension(root, data, reviews):
    roles = json.loads((data/'cellchat-role-coverage.json').read_bytes())
    assert roles['status'] == 'passed-six-cellchat-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews'], roles['unchanged_prior_reviews'], roles['new_reviews'],
            roles['total_reviews'], roles['supporting_pointers'], roles['observations']) == (1018, 1018, 6, 1024, 357, 2466)
    assert pin(data/'cellchat-prior-reviews.json.gz') == roles['prior_review_archive']
    assert pin(data/'cellchat-prior-papers.json.gz') == roles['prior_papers_archive']
    oldraw = gzip.decompress((data/'cellchat-prior-reviews.json.gz').read_bytes())
    old = json.loads(oldraw)
    assert byte_pin(oldraw)['sha256'] == roles['before_reviews_sha256'] and len(old['reviews']) == 1018
    draft = root/'tools/literature/cellchat-use-reviews.json'
    assert pin(draft) == roles['draft_pin']
    assert pin(root/'tools/literature/curate_cellchat_uses.py')['sha256'] == roles['curator_sha256']
    prepared = json.loads(draft.read_bytes())
    assert prepared['status'] == 'six-primary-bound-cellchat-role-reviews'
    assert {r['paper_id'] for r in prepared['reviews']} == {'P060', 'P085', 'P090', 'P100', 'P145', 'P167'}
    expected = copy.deepcopy(old)
    expected['reviews'].extend(r['review'] for r in prepared['reviews'])
    raw = (root/'tools/literature/reviewed-uses.json').read_bytes()
    assert json.loads(raw) == expected and reviews == expected['reviews']
    assert byte_pin(raw)['sha256'] == roles['reviews_sha256']
    assert len(reviews) == len({(r['pmcid'], r['tool_id']) for r in reviews}) == 1024
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews) == 357
    assert all(r['tool_id'] == 'cellchat' for r in reviews[1018:])
    assert roles['required_figure_evidence']['figure_id'] == 'Fig14'
    verify_source(root, data, 'tools/literature/reviewed-uses.json', roles['before_reviews_sha256'])
    return oldraw


def _predecessor_view(root, data, oldraw):
    relative = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    descriptor = json.loads((data/'cellchat-dispatch-shared-source-compatibility.json').read_bytes())
    assert len(descriptor['paths']) == 1
    row = descriptor['paths'][0]
    assert row['path'] == relative and len(row['edits']) == 2
    before = reverse_delta((root/relative).read_bytes(), row)
    verify_source(root, data, relative, row['before_sha256'])
    return ArchivedCatalogView(root, {relative: before, 'tools/literature/reviewed-uses.json': oldraw})


def verify_review_extension(root, data, reviews):
    raw = (root/'tools/literature/reviewed-uses.json').read_bytes()
    roles = json.loads((data/'cellchat-role-coverage.json').read_bytes())
    if byte_pin(raw)['sha256'] != roles['reviews_sha256']:
        # Existing verifiers also call this with exact older archived review views.
        # Their own complete historical population/hash checks remain authoritative.
        return _previous_verify(root, data, reviews)
    oldraw = _current_extension(root, data, reviews)
    _previous_verify(_predecessor_view(root, data, oldraw), data, json.loads(oldraw)['reviews'])


def load_catalog_registry(root):
    data = root/'docs/literature/omics-2023-2026'
    verify_review_extension(root, data, json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    result = prior.load_catalog_registry(root)
    assert len(result) == len({r['id'] for r in result}) == 438
    return result


@contextlib.contextmanager
def current_catalog_review_context():
    saved = prior.verify_review_extension
    try:
        prior.verify_review_extension = verify_review_extension
        with prior.current_catalog_review_context():
            yield
    finally:
        prior.verify_review_extension = saved


def _wrapped(name):
    def metadata(root, data, *extra, **kwargs):
        raw = _current_extension(root, data, json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
        # Only the two explicitly changed shared metadata paths are reconstructed.
        # Every algorithm, worker, resource and independent verifier stays live.
        view = _predecessor_view(root, data, raw)
        with current_catalog_review_context():
            return getattr(prior, name)(view, data, *extra, **kwargs)
    return metadata


for _name in ['current_meme_metadata', 'current_chipseeker_metadata', 'monocle_development_metadata',
    'normal_fit_metadata', 'cicero_metadata', 'soupx_metadata', 'dndscv_metadata', 'smr_metadata',
    'loftee_metadata', 'biomart_metadata', 'hail_metadata', 'leafcutter_metadata', 'cellphonedb_metadata',
    'palantir_metadata', 'scfates_metadata', 'gseapy_metadata', 'hifiasm_metadata', 'rsubread_metadata',
    'sniffles_metadata', 'mrmega_metadata', 'cistarget_metadata']:
    globals()[_name] = _wrapped(_name)


def cellchat_metadata(root, data):
    verify_review_extension(root, data, json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    proofs = {}
    for family in ['inference', 'legacy', 'network', 'strength', 'workflow']:
        name = f'docs/literature/cellchat-{family}-development/VALIDATION.json'
        proof = json.loads((root/name).read_bytes())
        assert not proof['whole_goal_complete'] and not proof['new_catalog_credit']
        assert all(pin(root/n) == value for n, value in proof['source_pins'].items())
        jars = proof.get('development_jars', proof.get('artifacts'))
        home = root/f'docs/literature/cellchat-{family}-development'
        for compiler in [21, 25]:
            jar = f'cellchat-{family}-javac{compiler}.jar'
            assert pin(home/jar) == jars[jar]
        proofs[family] = name
    native_name = 'docs/literature/cellchat-development/VALIDATION.json'
    native = json.loads((root/native_name).read_bytes())
    assert all(pin(root/n) == value for n, value in native['source_pins'].items())
    proofs['native_and_database'] = native_name
    routed = json.loads((root/'build/literature-review/cellchat-runtime/catalog-root-validation-1/COMPLETED.json').read_bytes())
    assert routed['status'] == 'PASS48_COMPLETE_CELLCHAT_ROOT_BUNDLES30_SKIPS6_PREDECESSOR_FLOWS'
    for name, value in routed['source_pins'].items():
        verify_source(root, data, name, value['sha256'])
    artifact = root/'docs/literature/cellchat-catalog-development/cellchat-root-javac21.jar'
    assert pin(artifact) == routed['root_artifacts']['build/literature-review/cellchat-runtime/catalog-root-build21-2/cellchat-root-supplement.jar']
    return dict(implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/CellChatToolsCli.java',
        coverage_note='Scoped original CellChat workflows and complete database exports are available; exact runtimes, source warnings and unresolved study-score limits remain explicit.',
        implementation_scope='Typed Java cellchat-tools dispatch to complete original1.0.0/2.1.2 condition inference, original1.1.3 condition comparison and functional/structural embedding/clustering, and original2.2.0 pooled/per-sample LR/pathway incoming/outgoing strength comparison. Original statistical/graph functions run under explicit pinned private runtimes; native Java checks projection, similarity, axes, source transports, cluster summaries and strength contrasts. Separate database exports preserve complete edition/species resource tables. No installation during analysis or automatic edition/engine/sign/parallel substitution. Read CELLCHAT_INTEGRATION.md and all source warnings.',
        validation_note='Six primary-bound roles distinguish database-resource use from original inference, network comparison/embedding and sample-aware strengths. Frozen component evidence retains original numerical/reference controls, full original workflows, warnings and failed attempts. Root verification passes48 complete saved bundles and30 actual missing-runtime SKIP3 requests across two compiler builds/three JVMs, plus six unchanged complete predecessor flows. Saved-bundle checks do not independently refit all models, authenticate producers or reproduce historical papers. Original negative p-fields, source fallback zeros, FNN/SNN anomalies, missing/nonfinite strengths, parallel RNG warnings and observed UMAP variability remain visible. P060 custom-score aggregation is unresolved: skip that study-score analysis instead of substituting Hill probabilities. Spyrrow, CellTrek, LIANA and FlowSig remain separate tool roles.',
        project_adapter_available=True, canonical_adapter_available=False, source_roles_reviewed=6,
        source_role_coverage='cellchat-role-coverage.json', development_candidate=artifact.relative_to(ROOT).as_posix(),
        development_candidate_sha256=pin(artifact)['sha256'], development_cli_class='org.jlinalg.cli.CellChatToolsCli',
        root_cli_class='org.jlinalg.cli.JLinAlgCli', root_command='cellchat-tools', workflow_proofs=proofs,
        native_helpers=['src/main/java/org/jlinalg/omics/'+n+'.java' for n in ['CellChatRna', 'CellChatProjection', 'CellChatNetworkSimilarity', 'CellChatSignalingStrengths']],
        database_export_source='tools/literature/export_cellchat_database.py', analysis_installs_software=False,
        original_runtime_required=True, whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False)


CELLCHAT_WARNING = '''## CellChat: warnings and required actions

Use cellchat-tools with the published root supplement plus its recorded canonical dependency, explicit workflow family and exact source/runtime profile. Require exit0, result_admitted=true and successful complete --verify. Read CELLCHAT_INTEGRATION.md, WARNINGS.md, original stdout/stderr, provenance, complete comparison tables and every captured parent's warnings. Analysis installs nothing.

SKIP3 means skip the dependent analysis. Install/download the named exact prerequisites only where allowed, using new private paths; if forbidden or unavailable, leave analysis skipped. FAIL2 admits no new result, including earlier successful steps in the attempt. Preserve the files, correct the named cause and retry NEW output directories. Native/logical capacities do not enforce host RAM/disk quotas or authenticate producers.

Original source warnings remain scientifically material: negative/out-of-range p-fields are invalid p-values; graph and rankNet fallback zeros are not successful estimates. Original FNN duplicate/omitted neighbors and SNN values outside[0,1] are retained without clipping, rescaling or repair. Missing groups/features remain NA; original NaN/Inf tests remain nonfinite. Do not turn any of these into a valid association or null result.

Original1.1.3 default automatic parallel clustering emits RNG-misuse warnings. Preserve them; the source reports potentially invalid random-number use. Explicit original sequential clustering is a separate analysis. The unchanged original UMAP constructor omits random_state, and separate default runs produced different coordinates/co-clustering despite source seeds. Neither seeds nor sequential R clustering establish exact whole-pipeline replay. Do not inject future.seed/random_state, silently replace the UMAP engine/metric/orientation, or infer historical paper execution.

P060 uses CellChatDB with a custom score whose printed component-product aggregation remains unresolved: skip its study-score analysis until the exact implementation is supplied; resource export alone is supported. P167 uses CellChatDB within a separate Spyrrow neighborhood score, and Spyrrow remains missing in the catalog. CellTrek, LIANA and FlowSig are separate methods. P145 comparison direction is explicit because the primary text does not resolve its sign convention. Tested source candidates do not identify historical paper environments. Cell Ranger remains excluded by the user; the full250-paper goal remains active.

'''
CELLCHAT_ACTIONS = '<section id="cellchat-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>CellChat: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in CELLCHAT_WARNING.strip().split('\n\n')[1:])+'</section>'
