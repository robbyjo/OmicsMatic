# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact925+7 metadata extension and one scoped dndscv integration; frozen scientific gates remain strict."""
import contextlib,gzip,json
import soupx_catalog as prior
from setup_deseq2_reference import pin
from catalog_source_integrity import verify_source
from signac_scan_evidence import unpack,byte_pin

load_catalog_registry=prior.load_catalog_registry
MONOCLE_WARNING=prior.MONOCLE_WARNING
MONOCLE_ACTIONS=prior.MONOCLE_ACTIONS
NORMAL_FIT_WARNING=prior.NORMAL_FIT_WARNING
NORMAL_FIT_ACTIONS=prior.NORMAL_FIT_ACTIONS
SOUPX_WARNING=prior.SOUPX_WARNING
SOUPX_ACTIONS=prior.SOUPX_ACTIONS
cicero_metadata=prior.cicero_metadata


def verify_review_extension(root,data,reviews):
    p=json.loads((data/'soupx-catalog-validation.json').read_bytes());assert pin(data/'soupx-catalog-support.zip')==p['archive'];files=unpack(data/'soupx-catalog-support.zip',p)
    for name,value in p['source_pins'].items():verify_source(root,data,name,value['sha256']);assert byte_pin(files['current-source/'+name])==value
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==925;roles=json.loads((data/'dndscv-role-coverage.json').read_bytes());assert roles['status']=='passed-seven-dndscv-primary-role-reviews' and roles['applied'];assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['total_observations'],roles['supporting_pointers'])==(925,7,932,2450,322)
    assert pin(data/'dndscv-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'dndscv-prior-reviews.json.gz').read_bytes())==old_raw;assert byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert gzip.decompress((data/'dndscv-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json'] and pin(data/'dndscv-prior-papers.json.gz')==roles['prior_papers_archive']
    for name in ['soupx-role-coverage.json','soupx-prior-reviews.json.gz','soupx-prior-papers.json.gz','soupx-source-audit.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(raw);draft_path=root/'tools/literature/dndscv-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert pin(draft_path)['sha256']==roles['draft_sha256'] and pin(root/'tools/literature/curate_dndscv_uses.py')['sha256']==roles['curator_sha256'] and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'};assert current['reviews'][:925]==old['reviews'] and current['reviews'][925:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==932 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==322
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    import fitdistrplus_catalog,chipseeker_catalog,meme_discovery_current_catalog,monocle_catalog_context
    targets=[(prior,'verify_review_extension'),(fitdistrplus_catalog,'verify_review_extension'),(monocle_catalog_context,'verify_current_review_extension'),(chipseeker_catalog,'verify_meme_review_extension'),(meme_discovery_current_catalog,'verify_meme_review_extension')];saved=[getattr(module,name) for module,name in targets]
    try:
        for module,name in targets:setattr(module,name,verify_review_extension)
        yield
    finally:
        for (module,name),value in zip(targets,saved):setattr(module,name,value)


def current_meme_metadata(root,data):
    with current_catalog_review_context():return prior.current_meme_metadata(root,data)

def current_chipseeker_metadata(root,data):
    with current_catalog_review_context():return prior.current_chipseeker_metadata(root,data)

def monocle_development_metadata(root,data):
    with current_catalog_review_context():return prior.monocle_development_metadata(root,data)

def normal_fit_metadata(root,data):
    with current_catalog_review_context():return prior.normal_fit_metadata(root,data)

def soupx_metadata(root,data):
    with current_catalog_review_context():return prior.soupx_metadata(root,data)


def dndscv_metadata(root,data):
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);p=json.loads((root/'docs/literature/dndscv-workflow-development/VALIDATION.json').read_bytes());assert p['adapter_admitted'] and p['implementation_credit']
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/DndscvWorkflowCli.java',implementation_scope='Native standard-code coding annotation and192-channel opportunities plus typed unchanged-original dndscv0.0.1.0 selection:192/12/2-rate models, global/local/covariate output, explicit reference/coverage, event limits and optional genesetdnds comparisons. Complete event/reference ledgers, N/L arrays, expected counts, Poisson/BH identities and typed tables audited. Selection optimizers/likelihoods/CI and site extraction remain upstream. No biological event/panel provenance, historical paper commit, mouse reference, pentanucleotide equivalence, IntOGen consensus or paper postfilters inferred. Read DNDSCV_WORKFLOWS.md.',validation_note='64 native coding and17 full transport/semantic checks per JVM on Windows Java21/25 and Linux Java21; complete independent original table/N/L comparisons and actual dependency/source/capacity/timeout/late-failure protocols. Exact source commit43c5e2f1beaaf3ac26df9c046b281ceb0edbfff7, Windows R4.6.1 and selected dependency profile required for upstream execution; missing/forbidden/Linux profiles SKIP3. Analysis installs nothing. FAIL2 admits no atomic result. Original warnings/nonfinite metadata and all failed attempts remain; P056 reported0.1.0 stays unresolved against source0.0.1.0.',source_roles_reviewed=7,source_role_coverage='dndscv-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.DndscvWorkflowCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)


DNDSCV_WARNING='''## dndscv: warnings and required actions

Use the separate development DndscvWorkflowCli and DNDSCV_WORKFLOWS.md. Require exit0 and result_admitted=true; inspect original logs, case statuses and the complete native-event-ledger.tsv. SKIP3 skips dependent analysis when the exact permitted Windows R4.6.1/source/dependency profile is absent. Analysis installs nothing; offline preparation without --allow-install starts no installer. Supply permitted exact private dependencies or skip. Linux upstream execution skips without interoperability fallback.

**FAIL2 admits no result from the atomic batch**, including earlier completed cases. Preserve original errors, partials and cleanup receipts; fix the named input/reference/model/capacity/timeout cause and retry NEW paths. Never omit events or repair IDs/alleles/NA/NaN/Inf. Independent mutation/clone events, panel, reference and coverage are explicit caller assertions; read/spot counts do not establish independent events. Source fits/CI and site coverage extraction are not independently refitted. Original site-depth integer coercion and nonfinite metadata remain. P056 reported0.1.0 differs from selected source0.0.1.0; no paper commit, mouse reference, P146 pentanucleotide model, IntOGen consensus or ambiguous paper postfilter is inferred.

'''
DNDSCV_ACTIONS='''<section id="dndscv-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>dndscv: warnings and required actions</h2><p><strong>Development workflow:</strong> use <code>DndscvWorkflowCli</code> and <code>DNDSCV_WORKFLOWS.md</code>. Require exit0 and result_admitted=true; inspect original logs, case statuses and the complete native event ledger.</p><p><strong>SKIP3:</strong> skip dependent analysis when the exact permitted Windows R4.6.1/source/dependency profile is missing. Analysis installs nothing; offline preparation without --allow-install starts no installer. Supply exact permitted private dependencies or skip. Linux upstream execution skips without interoperability fallback.</p><p><strong>FAIL2 admits no result from the atomic batch.</strong> Preserve failures, partials and cleanup receipts; fix the named input/reference/model/capacity/timeout cause and retry NEW paths. Never omit events or repair IDs/alleles/NA/NaN/Inf.</p><p><strong>Scientific choices:</strong> supply independent mutation/clone events, the actual reference/panel and declared coverage; read/spot counts are not independent events. Source fits/CI and site extraction remain upstream; original site-depth integer coercion and nonfinite metadata remain. P056's reported0.1.0 differs from source0.0.1.0. Paper commits, mouse references, pentanucleotide equivalence, IntOGen consensus and ambiguous postfilters are not inferred.</p></section>'''
