# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact932+12 metadata extension and two scoped SMR/HEIDI integrations; scientific source checks remain strict."""
import contextlib,gzip,json
import dndscv_catalog as prior
from setup_deseq2_reference import pin
from catalog_source_integrity import verify_source
from signac_scan_evidence import unpack,byte_pin

load_catalog_registry=prior.load_catalog_registry
for _name in ['MONOCLE_WARNING','MONOCLE_ACTIONS','NORMAL_FIT_WARNING','NORMAL_FIT_ACTIONS','SOUPX_WARNING','SOUPX_ACTIONS','DNDSCV_WARNING','DNDSCV_ACTIONS','cicero_metadata']:globals()[_name]=getattr(prior,_name)


def historical_readme_document(root,data,archived,expected):
    """README is documentation, handled by an explicit exact-document proof without changing the source whitelist."""
    assert byte_pin(archived)==expected
    current=pin(root/'README.md')
    if current==expected:return
    proof=json.loads((data/'smr-root-readme-evolution.json').read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-exact-README-documentation-evolution' and proof['path']=='README.md' and proof['before']==expected and proof['after']==current and proof['historical_archive']==pin(data/'dndscv-catalog-support.zip') and not proof['scientific_source_exception_added']


def verify_review_extension(root,data,reviews):
    p=json.loads((data/'dndscv-catalog-validation.json').read_bytes());assert pin(data/'dndscv-catalog-support.zip')==p['archive'];files=unpack(data/'dndscv-catalog-support.zip',p)
    for name,value in p['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==value
        if name=='README.md':historical_readme_document(root,data,files['current-source/'+name],value)
        else:verify_source(root,data,name,value['sha256'])
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==932;roles=json.loads((data/'smr-role-coverage.json').read_bytes());assert roles['status']=='passed-seven-SMR-five-HEIDI-primary-role-reviews' and roles['applied'];assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['total_observations'],roles['supporting_pointers'])==(932,12,944,2450,327)
    assert pin(data/'smr-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'smr-prior-reviews.json.gz').read_bytes())==old_raw;assert byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert gzip.decompress((data/'smr-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json'] and pin(data/'smr-prior-papers.json.gz')==roles['prior_papers_archive']
    for name in ['dndscv-role-coverage.json','dndscv-prior-reviews.json.gz','dndscv-prior-papers.json.gz','dndscv-source-audit.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(raw);draft_path=root/'tools/literature/smr-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert pin(draft_path)['sha256']==roles['draft_sha256'] and pin(root/'tools/literature/curate_smr_uses.py')['sha256']==roles['curator_sha256'] and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'};assert current['reviews'][:932]==old['reviews'] and current['reviews'][932:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==944 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==327
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    import fitdistrplus_catalog,chipseeker_catalog,meme_discovery_current_catalog,monocle_catalog_context
    targets=[(prior,'verify_review_extension'),(prior.prior,'verify_review_extension'),(fitdistrplus_catalog,'verify_review_extension'),(monocle_catalog_context,'verify_current_review_extension'),(chipseeker_catalog,'verify_meme_review_extension'),(meme_discovery_current_catalog,'verify_meme_review_extension')];saved=[getattr(module,name) for module,name in targets]
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
    with current_catalog_review_context():return prior.dndscv_metadata(root,data)


def smr_metadata(root,data,tool):
    assert tool in ['smr','heidi'];verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);p=json.loads((root/'docs/literature/smr-workflow-development/VALIDATION.json').read_bytes());assert p['adapter_admitted'] and p['implementation_credit'] and p['admitted_named_tools']==['smr','heidi']
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/SmrWorkflowCli.java',implementation_scope='Typed unchanged-original SMR 1.3.1/1.4.3 single/multi-SNP association and original/new/off HEIDI, with Java GWAS/QTL/BESD/reference, allele/frequency/MAF, preparation, complete-family/top-SNP and rounded ratio/SE/P audits. Dense reference order, sparse row order and literal near ties retained; explicit sample-overlap/source-flag semantics. Original LD pruning, HEIDI mixtures/tails and multi-SNP fitting remain upstream. Input/assembly/population/units, paper corrections/reuse and historical binaries are not inferred. Bounded ingestion; read SMR_WORKFLOWS.md.',validation_note='Both exact original Linux x86_64 editions exercised with complete original case comparisons, portable native transport/adversarial tests on Windows Java21/25 and Linux Java21, preparation boundary controls, restored-runtime examples and actual skip/timeout/capacity/late-failure protocols. Analysis installs nothing; unavailable/forbidden/different dependencies SKIP3. FAIL2 admits no atomic result. Legacy SE rederivation and its explicit one-float-ULP audit limit remain visible; supplied-SE and beta require exact binary32 identity. Both active multi-SNP paths leave theta = 0; no estimated overlap correction is invented.1.3.1 no-reference failure and reported 1.03 remain distinct. No independent full HEIDI/multi refit, biological calibration or whole-paper claim.',source_roles_reviewed=7 if tool=='smr' else 5,source_role_coverage='smr-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.SmrWorkflowCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)


SMR_WARNING='''## SMR/HEIDI: warnings and required actions

Use the separate development SmrWorkflowCli and SMR_WORKFLOWS.md. Require exit 0 and result_admitted=true; inspect every original log, process receipt and native preparation/variant/observation/probe/ratio ledger. SKIP3 skips dependent analysis when the exact permitted Linux x86_64 source/program/ELF closure is absent, different or forbidden. Windows Java upstream execution skips; native audits are portable. Analysis installs nothing. Private setup captures matching local libraries and restores separate original source/program evidence without changing shared libraries.

**FAIL2 admits no atomic-batch result**, including earlier completed cases. Preserve inputs, failed/partial attempts and cleanup receipts; correct the named input/source/capacity/timeout/permission cause and retry NEW paths. Never omit variants/probes, infer genotype/sex/assembly/ancestry, repair IDs/alleles or replace NA/NaN/Inf. Valid empty families retain a warning; unavailable/nonrejected HEIDI is not causal proof.

**Source choices matter.**1.3.1 can rederive SE from beta/P;1.4.3 uses supplied SE. Only legacy derived SE permits an explicit one-float-ULP inverse-CDF discrepancy, retained with both values and a warning. Both active multi-SNP paths leave theta = 0 and do not estimate/filter null SNPs; their literal flag mode is explicitly unestimated. 1.3.1's no-reference path fails; no original function or dummy reference is substituted. Dense source order, sparse row sequence and strict MAF/frequency boundaries remain. Paper-specific references, thresholds/correction families, previously reused results and reported 1.03 stay separate. The adapter has a 512 MiB per-text-table bound; larger ingestion requires extension, not subsampling. Canonical/released JAR and whole-goal completion are not claimed.

'''
SMR_ACTIONS='''<section id="smr-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>SMR/HEIDI: warnings and required actions</h2><p><strong>Development workflow:</strong> use <code>SmrWorkflowCli</code> and <code>SMR_WORKFLOWS.md</code>. Require exit 0 and <code>result_admitted=true</code>; read original logs, process receipts and all native ledgers.</p><p><strong>SKIP3:</strong> skip dependent analysis if the exact permitted Linux x86_64 source/program/ELF profile is absent, different or forbidden. Windows Java upstream execution skips. Analysis installs nothing; private setup leaves shared libraries unchanged.</p><p><strong>FAIL2 admits no result from the atomic batch.</strong> Preserve inputs, errors, partials and cleanup receipts; fix the named cause and retry NEW paths. Never omit variants/probes, infer metadata, repair IDs/alleles or replace NA/NaN/Inf. Empty families and unavailable HEIDI remain explicit; non-rejection is not causal proof.</p><p><strong>Version and scientific limits:</strong>1.3.1 can derive SE from beta/P;1.4.3 keeps supplied SE. Every allowed legacy 1-ULP difference is retained and warned. Both active multi-SNP paths leave theta = 0; the literal overlap flag is unestimated. 1.3.1 no-reference failure and reported 1.03 remain separate. Source order, strict MAF/frequency behavior and paper-specific correction/reference/reuse choices matter. Text-table ingestion is bounded to 512 MiB; do not subsample to hide a capacity failure. HEIDI/multi fitting remains upstream; canonical/released availability and whole-paper/goal completion are not inferred.</p></section>'''
