# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""GENESIS catalog metadata and exact predecessor metadata views; scientific sources remain live."""
import contextlib
import gzip
import html
import json
from snprelate_catalog import *
import snprelate_catalog as prior
from setup_deseq2_reference import ROOT, pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import reverse_delta, verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView

_previous_verify = prior.verify_review_extension


def predecessor_view(root, data, restore_catalog=True):
    overrides = {}; name = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'; descriptor = data / 'genesis-dispatch-shared-source-compatibility.json'
    if descriptor.exists():
        document = json.loads(descriptor.read_bytes()); assert len(document['paths']) == 1
        row = document['paths'][0]; assert row['path'] == name and len(row['edits']) == 2
        raw = (root / name).read_bytes()
        before = raw if byte_pin(raw)['sha256'] == row['before_sha256'] else reverse_delta(raw, row)
        verify_source(root, data, name, row['before_sha256']); overrides[name] = before
    if restore_catalog and (data / 'genesis-prior-reviews.json.gz').exists():
        overrides['tools/literature/reviewed-uses.json'] = gzip.decompress((data / 'genesis-prior-reviews.json.gz').read_bytes())
        overrides['docs/literature/omics-2023-2026/papers.json'] = gzip.decompress((data / 'genesis-prior-papers.json.gz').read_bytes())
    return ArchivedCatalogView(root, overrides)


def _current_extension(root, data, reviews):
    coverage = json.loads((data / 'genesis-source-role-coverage.json').read_bytes())
    assert coverage['status'] == 'PASS_FIVE_GENESIS_SCOPES_AND_SEPARATE_PRIMARY_SUPPLEMENT_POINTER' and coverage['applied']
    assert (coverage['reviewed_roles'], coverage['supporting_pointers'], coverage['observations']) == (1038, 369, 2475)
    for name, key in [('genesis-prior-reviews.json.gz', 'prior_review_archive'), ('genesis-prior-papers.json.gz', 'prior_papers_archive'), ('genesis-applied-scope-reviews.json', 'applied_updates')]:
        assert pin(data / name) == coverage[key]
    assert pin(root / 'tools/literature/apply_genesis_catalog_roles.py') == coverage['curator_pin']
    raw = gzip.decompress((data / 'genesis-prior-reviews.json.gz').read_bytes()); old = json.loads(raw)['reviews']
    updates = json.loads((data / 'genesis-applied-scope-reviews.json').read_bytes())['updates']; assert len(updates) == 5
    actual = json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews']; assert actual == reviews
    assert len(old) == len(actual) == len({(r['pmcid'], r['tool_id']) for r in actual}) == 1038
    changes = {(r['after']['pmcid'], r['after']['tool_id']): r for r in updates}; assert len(changes) == 5
    for before, after in zip(old, actual, strict=True):
        key = after['pmcid'], after['tool_id']
        if key in changes:
            assert key[1] == 'genesis' and before == changes[key]['before'] and after == changes[key]['after']
            assert {k: v for k, v in before.items() if k != 'implementation_scope_note'} == {k: v for k, v in after.items() if k != 'implementation_scope_note'}
        else:
            assert before == after
    assert sum(len(r.get('supporting_evidence', [])) for r in actual) == 369
    assert pin(root / 'tools/literature/reviewed-uses.json') == coverage['current_reviews']
    assert pin(data / 'papers.json') == coverage['current_papers']
    supplement = coverage['separate_primary_supplement']; assert pin(root / supplement['path']) == {k: supplement[k] for k in ['bytes', 'sha256']}
    assert supplement['explicit_function'] == 'pcairPartition' and supplement['reported_kinship_cutoff'] == .044
    assert supplement['reported_version'] == '2.16.1' and supplement['downstream_unreported_functions_not_inferred'] and not supplement['pdf_or_article_prose_redistributed']
    verify_source(root, data, 'tools/literature/reviewed-uses.json', coverage['reviews_before']['sha256'])
    return raw


def verify_review_extension(root, data, reviews):
    file = data / 'genesis-source-role-coverage.json'
    if not file.exists() or pin(root / 'tools/literature/reviewed-uses.json') != json.loads(file.read_bytes())['current_reviews']:
        return _previous_verify(predecessor_view(root, data, False), data, reviews)
    old = _current_extension(root, data, reviews)
    _previous_verify(predecessor_view(root, data), data, json.loads(old)['reviews'])


def load_catalog_registry(root):
    data = root / 'docs/literature/omics-2023-2026'
    verify_review_extension(root, data, json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    result = prior.load_catalog_registry(predecessor_view(root, data)); assert len(result) == len({r['id'] for r in result}) == 440
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
    def metadata(root, data, *args, **kwargs):
        return getattr(prior, name)(predecessor_view(root, data), data, *args, **kwargs)
    return metadata


for _name in ['current_meme_metadata', 'current_chipseeker_metadata', 'monocle_development_metadata', 'normal_fit_metadata', 'cicero_metadata',
              'soupx_metadata', 'dndscv_metadata', 'smr_metadata', 'loftee_metadata', 'biomart_metadata', 'hail_metadata', 'leafcutter_metadata',
              'cellphonedb_metadata', 'palantir_metadata', 'scfates_metadata', 'gseapy_metadata', 'hifiasm_metadata', 'rsubread_metadata',
              'sniffles_metadata', 'mrmega_metadata', 'cistarget_metadata', 'cellchat_metadata', 'diamond_metadata', 'snprelate_metadata']:
    globals()[_name] = _wrapped(_name)


def genesis_metadata(root, data):
    _current_extension(root, data, json.loads((root / 'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    from verify_genesis_tools_evidence import inspect
    proof, _ = inspect()
    artifact = root / 'docs/literature/genesis-tools-development/genesis-root-javac21.jar'
    return dict(implementation_status='upstream-adapter-validated', implementation_source='src/main/java/org/jlinalg/cli/GenesisWorkflowToolsCli.java',
        coverage_note='Scoped original GENESIS2.42.0 PC-AiR, supplied-PC PC-Relate, Gaussian/binomial/Poisson models and single-variant tests, with native matrix partition/rank/input/output audits. Five paper roles reviewed; historical and unstated settings remain explicit.',
        implementation_scope='Typed genesis-tools with an exact private Windows R4.6.1/longdouble64 runtime. Explicit diploid biallelic autosomal hard calls, complete FID/IID populations and caller-supplied matrices, PCs and model designs. PC-AiR supports declared exact/randomized ancestry components and matrix partitions; PC-Relate preserves original IBD and scaled connected-component covariance. Models support ordinary/mixed/group fits, Gaussian two-stage rank transforms and single-variant Score, SPA, BinomiRare or one-covariate GxE Wald tests. Native Java checks finite rank/quantiles, complete genotypes/QC/order, covariance, model design/convergence/effective engine, association algebra, carrier probabilities and Poisson-binomial PMFs. Estimator fitting remains upstream. True mixed CMP currently fails in original2.42.0 and admits no result; ordinary CMP can execute a prominently labelled BinomiRare fallback. Missing permitted dependencies skip3; source/scientific failures admit no result2. No automatic software installation or source repair. Read GENESIS_INTEGRATION.md.',
        validation_note='61 declared option-branch controls:53 native component successes,five scientific refusals,three original errors; no project result admitted by that component collection.13 root-routed literal examples yield11 admitted original workflows and two retained source/scientific refusals,with33 saved replays,12 help checks and an explicit missing-runtime skip. Separate frozen controls include756264 complete finite-rank score comparisons,24 rank API JUnit executions,87 coherently resealed scientific refusals and prior source-field/partition controls. Repeated synthetic inputs are not independent biological datasets. Original signs,uncomputed placeholders,unconstrained IBD,boundary auxiliaries and effective GLM/test fallbacks remain visible. No independent model/PCA/kinship refit, historical-paper reproduction, software authenticity or production-scale claim.',
        project_adapter_available=True, canonical_adapter_available=False, source_roles_reviewed=5, source_role_coverage='genesis-source-role-coverage.json',
        development_candidate=artifact.relative_to(ROOT).as_posix(), development_candidate_sha256=pin(artifact)['sha256'],
        development_cli_class='org.jlinalg.cli.GenesisWorkflowToolsCli', root_cli_class='org.jlinalg.cli.JLinAlgCli', root_command='genesis-tools',
        workflow_proof='docs/literature/genesis-tools-development/VALIDATION.json', native_components=['GenesisPcairPartition', 'GenesisRankNormalisation'],
        original_runtime_required=True, analysis_installs_software=False, supported_source_versions=['2.42.0'], whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False)


GENESIS_WARNING = '''## GENESIS: warnings and required actions

Use the matching development genesis-tools command and GENESIS_INTEGRATION.md. Require exit0, result_admitted=true and a successful complete --verify. Review WARNINGS.md, scientific-audit.json, original logs and every association disposition before downstream use. The released0.3.7 executable alone lacks this command; the root supplement needs its separately pinned canonical dependency.

SKIP3: missing, forbidden or unsupported exact prerequisites mean SKIP the dependent original analysis. Required action: supply the permitted private Windows x86_64 R4.6.1/longdouble64 and GENESIS2.42.0 profile. SPA needs SPAtest3.1.2; BinomiRare needs poibin1.6; potentially mixed CMP additionally needs COMPoissonReg0.8.2. Install/download only where permitted, into NEW private paths; otherwise leave analysis skipped. Analysis never installs software. Windows/Linux Java17+ can verify saved bundles without R.

FAIL2: NO RESULT ADMITTED, including partial original output. Preserve the attempt, inspect the named input/process/capacity/scientific cause, resolve it and retry NEW paths. Original true mixed CMP currently errors on an out-of-bounds n.carrier write; report the preserved original error upstream. An original P value above1 is refused without clipping. Do not change source,versions,settings,cutoffs or IDs merely to hide a failure. A deliberate different analysis needs its own explicit request and validation.

Read effective methods: zero variance can switch to GLM; ordinary CMP can execute BinomiRare; non-binomial SPA can execute Score. Original SPA flags distinguish unrecalculated,converged and nonconverged variants; nonconvergence can retain Score P values. Never report those fallbacks as the requested mixed/CMP/SPA calculation. Original auxiliary FFT roundoff and strict density ordering remain visible; native PMFs are not substituted for P values.

PC-AiR projected one-component outputs can lack dimensions/identities; they remain refusals without reshaping. Forced partitions retaining related pairs,unassessable required fields and nonconverged/omitted model designs admit no dependent result. PC-Relate covariance thresholds apply after scaling and retain weak edges within connected components. Two-stage fitting records an extra original first-stage control; native ranks do not independently refit the model. GxE.Stat and Joint.Stat are original square roots of Wald quantities. Logical capacities are not OS quotas; external Windows system DLLs are not independently pinned.

Five primary paper roles are reviewed. P128's separately reviewed Supplementary Note7 reports historical GENESIS2.16.1 pcairPartition cutoff0.044; downstream PCA/kinship/model calls remain unstated. P130 reports2.26.0 PC-AiR ten PCs; P142 reports PC-Relate-informed GLMMs without exact version/calls; P191's non-European PC-AiR is separate from PLINK2/SAIGE steps; P219 reports twenty PCs without naming the function. Current2.42.0 controls do not reproduce those historical or unspecified analyses. Cell Ranger remains excluded and the full250-paper goal remains active.

'''
GENESIS_ACTIONS = '<section id="genesis-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>GENESIS: warnings and required actions</h2>' + ''.join('<p>' + html.escape(p) + '</p>' for p in GENESIS_WARNING.strip().split('\n\n')[1:]) + '</section>'

# Contextualize the older warning text without changing its frozen source or
# scientific claims. All other SNPRelate warning bytes remain unchanged.
_old = 'GENESIS remains missing despite private runtime setup: ordinary PCA and SNPRelate KING do not implement PC-AiR, PC-Relate or relatedness-adjusted models.'
_new = 'GENESIS now has a separate scoped current2.42.0 integration; read GENESIS_INTEGRATION.md. SNPRelate KING and ordinary PCA stay distinct from GENESIS PC-AiR, PC-Relate and relatedness-adjusted models.'
assert prior.SNPRELATE_WARNING.count(_old) == 1
SNPRELATE_WARNING = prior.SNPRELATE_WARNING.replace(_old, _new)
SNPRELATE_ACTIONS = '<section id="snprelate-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>SNPRelate: warnings and required actions</h2>' + ''.join('<p>' + html.escape(p) + '</p>' for p in SNPRELATE_WARNING.strip().split('\n\n')[1:]) + '</section>'
