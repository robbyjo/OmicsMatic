# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact918+7 metadata extension and one scoped SoupX integration; frozen science stays strict."""
import contextlib,gzip,json
import fitdistrplus_catalog as prior
from setup_deseq2_reference import pin
from catalog_source_integrity import verify_source
from signac_scan_evidence import unpack,byte_pin

load_catalog_registry=prior.load_catalog_registry
MONOCLE_WARNING=prior.MONOCLE_WARNING
MONOCLE_ACTIONS=prior.MONOCLE_ACTIONS
NORMAL_FIT_WARNING=prior.NORMAL_FIT_WARNING
NORMAL_FIT_ACTIONS=prior.NORMAL_FIT_ACTIONS
cicero_metadata=prior.cicero_metadata


def verify_review_extension(root,data,reviews):
    p=json.loads((data/'fitdistrplus-catalog-validation.json').read_bytes());assert pin(data/'fitdistrplus-catalog-support.zip')==p['archive'];files=unpack(data/'fitdistrplus-catalog-support.zip',p)
    for name,value in p['source_pins'].items():verify_source(root,data,name,value['sha256']);assert byte_pin(files['current-source/'+name])==value
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==918
    roles=json.loads((data/'soupx-role-coverage.json').read_bytes());assert roles['status']=='passed-seven-SoupX-primary-role-reviews' and roles['applied'];assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['total_observations'],roles['supporting_pointers'])==(918,7,925,2450,311)
    assert pin(data/'soupx-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'soupx-prior-reviews.json.gz').read_bytes())==old_raw;assert byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert gzip.decompress((data/'soupx-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json'] and pin(data/'soupx-prior-papers.json.gz')==roles['prior_papers_archive']
    for name in ['fitdistrplus-role-coverage.json','fitdistrplus-prior-reviews.json.gz','fitdistrplus-prior-papers.json.gz','fitdistrplus-source-audit.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(raw);draft_path=root/'tools/literature/soupx-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert pin(draft_path)['sha256']==roles['draft_sha256'] and pin(root/'tools/literature/curate_soupx_uses.py')['sha256']==roles['curator_sha256'] and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'};assert current['reviews'][:918]==old['reviews'] and current['reviews'][918:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==925 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==311
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    import chipseeker_catalog,meme_discovery_current_catalog,monocle_catalog_context
    targets=[(prior,'verify_review_extension'),(monocle_catalog_context,'verify_current_review_extension'),(chipseeker_catalog,'verify_meme_review_extension'),(meme_discovery_current_catalog,'verify_meme_review_extension')];saved=[getattr(module,name) for module,name in targets]
    try:
        for module,name in targets:setattr(module,name,verify_review_extension)
        yield
    finally:
        for (module,name),value in zip(targets,saved):setattr(module,name,value)


def current_meme_metadata(root,data):
    from meme_discovery_current_catalog import meme_discovery_metadata
    with current_catalog_review_context():return meme_discovery_metadata(root,data)

def current_chipseeker_metadata(root,data):
    from chipseeker_catalog import chipseeker_metadata
    with current_catalog_review_context():return chipseeker_metadata(root,data)

def monocle_development_metadata(root,data):
    with current_catalog_review_context():return prior.monocle_development_metadata(root,data)

def normal_fit_metadata(root,data):
    with current_catalog_review_context():return prior.normal_fit_metadata(root,data)


def soupx_metadata(root,data):
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);p=json.loads((root/'docs/literature/soupx-workflow-development/VALIDATION.json').read_bytes());assert p['adapter_admitted'] and p['exact_full_corrected_matrix_comparisons']==45
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/SoupXWorkflowCli.java',implementation_scope='Native supplied-cluster TF-IDF marker statistics plus typed unchanged-original SoupX1.6.2/1.3.0 ambient workflows: explicit fixed/per-cell rho, manual global Poisson masks/estimation, current automatic gamma or historical density estimation, and subtraction/soupOnly/multinomial correction with explicit scope/rounding/seed. Complete raw/filtered axes, profiles, diagnostic tables and corrected matrices audited. Original GLM rho, nonfinite metadata and warnings retained. No inferred clusters, manual annotations, empty-droplet calls, paper settings or historical runtime; no independent gamma/KDE/correction refit, STAN or full-package equivalence. Read SOUPX_WORKFLOWS.md.',validation_note='30 checks per JVM on Windows Java21/25 and Linux Java21;14 original-runtime protocols and45 exact complete corrected-matrix comparisons including literal Unicode IDs, explicit zeros and shared input files. Separate original source/function guards, semantic tamper refusals, atomic late-failure rejection, capacity/timeout cleanup and documented example. Exact Windows R4.6.1/dependency profile required for upstream execution; Linux/missing/forbidden profiles SKIP3, no analysis installed. FAIL2 admits no atomic result. Native marker/core components need Java only.',source_roles_reviewed=7,source_role_coverage='soupx-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.SoupXWorkflowCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)


SOUPX_WARNING='''## SoupX: warnings and required actions

Use the separate development SoupXWorkflowCli and SOUPX_WORKFLOWS.md. Require exit0 and result_admitted=true; read original logs and every case status. SKIP3 skips dependent analysis when the exact permitted Windows R4.6.1/SoupX/source/dependency profile is absent. Analysis installs nothing; preparation without --allow-install launches no installation. Supply permitted private dependencies or skip; no version substitution or source patching. Linux upstream execution is explicitly skipped.

**FAIL2 admits no result from the atomic batch**, including earlier completed cases. Preserve warnings, source-status.json, worker output and cleanup receipts; correct the named input/model/capacity/timeout cause and retry NEW paths without omitted observations or ID/NA/NaN/Inf repair. Selected profile droplets are not independently called empty; filtered-cell overlap and supplied-independent raw counts remain prominently warned. Original iterative GLM rho and nonfinite metadata stay unchanged; native analytical rho is a diagnostic. Fractions stay fractional; never silently round for DESeq2. Historical density estimates require explicit application before correction. Manual ALB/Csf1r roles, secondary validation versus corrected-count use and version uncertainty stay separate. No paper-specific reconstruction or biological calibration is claimed.

'''
SOUPX_ACTIONS='''<section id="soupx-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>SoupX: warnings and required actions</h2><p><strong>Development workflow:</strong> use <code>SoupXWorkflowCli</code> and <code>SOUPX_WORKFLOWS.md</code>. Require exit0 and result_admitted=true; read original logs and case status.</p><p><strong>SKIP3:</strong> skip dependent analysis when the exact permitted Windows R4.6.1/SoupX/source profile is missing. Analysis installs nothing. Preparation without --allow-install launches no installation. Supply permitted private dependencies or skip; Linux upstream execution is skipped.</p><p><strong>FAIL2 admits no result from the atomic batch.</strong> Preserve original failures, partials and cleanup receipts; fix the named input/model/capacity/timeout cause and retry NEW paths. Never omit observations or repair IDs/NA/NaN/Inf.</p><p><strong>Scientific choices:</strong> supplied profile droplets are not certified empty; overlap with filtered cells remains warned. Original iterative GLM rho and nonfinite metadata remain. Fractions stay fractional; no silent DESeq2 conversion. Historical density estimates require explicit application. Manual ALB/Csf1r roles and secondary validation are separate; paper settings and biological calibration are not inferred.</p></section>'''
