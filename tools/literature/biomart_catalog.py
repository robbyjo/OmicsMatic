# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One scoped biomaRt availability delta; all952 existing source-role reviews and scientific checks remain unchanged."""
import contextlib,json
import loftee_catalog as prior
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin

load_catalog_registry=prior.load_catalog_registry
for _name in ['MONOCLE_WARNING','MONOCLE_ACTIONS','NORMAL_FIT_WARNING','NORMAL_FIT_ACTIONS','SOUPX_WARNING','SOUPX_ACTIONS','DNDSCV_WARNING','DNDSCV_ACTIONS','SMR_WARNING','SMR_ACTIONS','LOFTEE_WARNING','LOFTEE_ACTIONS']:globals()[_name]=getattr(prior,_name)

def historical_readme_document(root,data,archived,expected):
    assert byte_pin(archived)==expected
    if pin(root/'README.md')==expected:return
    p=json.loads((data/'biomart-root-readme-evolution.json').read_bytes());assert p['schema_version']==1 and p['status']=='passed-exact-README-documentation-evolution' and p['path']=='README.md' and p['after']==pin(root/'README.md') and not p['scientific_source_exception_added'];rows=[r for r in p['historical_documents'] if r['document']==expected];assert len(rows)==1;row=rows[0];assert row['archive'] in ['loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip'] and pin(data/row['archive'])==row['archive_pin'];old=json.loads((data/row['archive'].replace('-support.zip','-validation.json')).read_bytes());assert old['archive']==row['archive_pin'] and unpack(data/row['archive'],old)['current-source/README.md']==archived

@contextlib.contextmanager
def current_catalog_review_context():
    saved=prior.historical_readme_document
    try:
        prior.historical_readme_document=historical_readme_document
        with prior.current_catalog_review_context():yield
    finally:prior.historical_readme_document=saved

def verify_review_extension(root,data,reviews):
    with current_catalog_review_context():return prior.verify_review_extension(root,data,reviews)

def _metadata(name,*args):
    with current_catalog_review_context():return getattr(prior,name)(*args)
def current_meme_metadata(root,data):return _metadata('current_meme_metadata',root,data)
def current_chipseeker_metadata(root,data):return _metadata('current_chipseeker_metadata',root,data)
def monocle_development_metadata(root,data):return _metadata('monocle_development_metadata',root,data)
def normal_fit_metadata(root,data):return _metadata('normal_fit_metadata',root,data)
def cicero_metadata(root,data):return _metadata('cicero_metadata',root,data)
def soupx_metadata(root,data):return _metadata('soupx_metadata',root,data)
def dndscv_metadata(root,data):return _metadata('dndscv_metadata',root,data)
def smr_metadata(root,data,tool):return _metadata('smr_metadata',root,data,tool)
def loftee_metadata(root,data):return _metadata('loftee_metadata',root,data)

def biomart_metadata(root,data):
    from biomart_workflow_evidence import verify
    p=verify();verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);coverage=json.loads((data/'biomart-source-coverage.json').read_bytes());assert coverage['status']=='passed-seven-existing-biomaRt-primary-role-reviews' and coverage['new_reviews_added']==0 and coverage['reviewed_uses_preserved']==952
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/BiomartWorkflowCli.java',implementation_scope='Typed unchanged-original biomaRt2.60.1/2.68.0 getBM annotation via explicit registry/database/dataset/assembly binding and original R table conversion. Native Java17+ plans complete source XML, numeric/quote coercions, balanced5000-value splitting and first-filter-fastest Cartesian batches; it verifies actual HTTP and source console representations without replacing identifiers. Original RDS, typed NA/NaN/Inf states, row names, source column order, one-to-many/repeated rows, warnings and complete raw responses are retained. Windows x86_64 R4.6.1 retrieval; portable native planning. No enrichment, nearest-gene distance/tie rule, paper-specific query/mask or historical R4.4 image is inferred. Read BIOMART_WORKFLOWS.md.',validation_note='51 complete independent original R dataframe comparisons/1868 rows, both original editions on GRCh37 and Ensembl115/116 archives, typed-state and explicit HTML fallback controls. Native planning matches76 original batches and10 expected errors on WindowsJava21/25 and LinuxJava21. Exact private runtime copies, drift/missing/forbidden dependency and unsupported-platform skips, output/HTTP capacities, pre-write source preservation, redirects, late failures and owned timeout cleanup are checked. Final candidate changes one diagnostic-only JAR entry; current additional checks/complete Java25 original retrieval and native comparisons are retained separately from the full prior matrix. SKIP3 skips dependent analysis; FAIL2 admits no whole-batch result. Read every phase worker-report.json and WARNINGS.md. No paper reproduction or canonical release availability is claimed.',source_roles_reviewed=7,source_role_coverage='biomart-source-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.BiomartWorkflowCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)

BIOMART_WARNING='''## biomaRt: warnings and required actions

Use development `org.jlinalg.cli.BiomartWorkflowCli` and **BIOMART_WORKFLOWS.md**. Require exit0 and `result_admitted=true`; read `WARNINGS.md`, every phase's `worker-report.json` warnings/error/trace_error fields, original RDS/typed tables, process receipts and complete HTTP/query ledgers.

**SKIP3 skips dependent annotation** if exact permitted Windows x86_64 R4.6.1/biomaRt dependencies or the requested endpoint/release/assembly are unavailable, different or forbidden. Native query planning is portable; original R execution on unsupported platforms remains skipped. Where permitted prepare the exact private runtime; the installer does not resolve arbitrary dependency versions. Preserve preparation errors and existing libraries. Analysis installs nothing and never falls back to another release or genome build.

**FAIL2 admits no atomic-batch result**, including earlier successful queries. Correct the named input/runtime/service/capacity/timeout cause and retry NEW output paths without omitting values/rows. HTTP capture capacity errors name `maximum_http_bytes` and its recovery action. Keep all originals and partials. Never merge symbols, deduplicate one-to-many/repeated source rows, fabricate unmatched-gene rows, repair identifiers, replace NA/NaN/Inf or infer a paper mask.

Original R performs table/header conversion and can reorder columns. Its numeric-to-integer and quote substitution are recorded with original tokens. HTML fallback is explicit; original source globally replaces TSV with HTML, including filter values. Original C-locale console text may escape Unicode while actual HTTP retains the identifier. Temporary chunk caching can reuse a request while repeating result rows. Read both representations and all warnings. Metadata binding is not independent biological-content authentication or a multi-query database transaction. Enrichment, closest-gene distance/tie rules, historical R4.4 images and whole-paper reproduction remain separate.

'''
BIOMART_ACTIONS='''<section id="biomart-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>biomaRt: warnings and required actions</h2><p>Use development <code>BiomartWorkflowCli</code> and <code>BIOMART_WORKFLOWS.md</code>. Require exit0 and <code>result_admitted=true</code>. Read <code>WARNINGS.md</code>, every phase's <code>worker-report.json</code>, original RDS/typed tables and complete HTTP/query ledgers.</p><p><strong>SKIP3: dependent annotation skipped.</strong> Prepare exact permitted Windows x86_64 R4.6.1 and biomaRt dependencies or restore the explicit endpoint/release/assembly. Missing, different, forbidden and unsupported runtimes remain skipped. The installer is not a general dependency resolver. Analysis installs nothing; no release/build fallback.</p><p><strong>FAIL2: no batch result admitted, including earlier successful queries.</strong> Preserve inputs, errors and partials. Correct the named cause and retry NEW output paths. Increase <code>maximum_http_bytes</code> within its supported bound for a capture-capacity error; never omit data. Never repair identifiers, merge symbols, collapse one-to-many rows, fabricate unmatched-gene rows or replace NA/NaN/Inf.</p><p>Native Java planning is portable; original R retains table conversion, column order, row names and typed states. Original quote/numeric coercions, Unicode console escapes and temporary chunk caching remain explicit. HTML fallback requires permission and globally replaces TSV with HTML even inside filter values. Metadata does not authenticate biological content or create a database transaction. Enrichment, nearest-gene distance/tie rules, paper masks and historical R4.4 images remain separate.</p></section>'''
