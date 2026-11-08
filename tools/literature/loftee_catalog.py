# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact944+8 primary-role extension and one scoped LOFTEE integration; historical scientific checks remain strict."""
import contextlib,gzip,json
import smr_catalog as prior
from setup_deseq2_reference import pin
from catalog_source_integrity import verify_source
from signac_scan_evidence import unpack,byte_pin

load_catalog_registry=prior.load_catalog_registry
for _name in ['MONOCLE_WARNING','MONOCLE_ACTIONS','NORMAL_FIT_WARNING','NORMAL_FIT_ACTIONS','SOUPX_WARNING','SOUPX_ACTIONS','DNDSCV_WARNING','DNDSCV_ACTIONS','SMR_WARNING','SMR_ACTIONS','cicero_metadata']:globals()[_name]=getattr(prior,_name)

def historical_readme_document(root,data,archived,expected):
    assert byte_pin(archived)==expected
    if pin(root/'README.md')==expected:return
    proof=json.loads((data/'loftee-root-readme-evolution.json').read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-exact-README-documentation-evolution' and proof['path']=='README.md' and proof['after']==pin(root/'README.md') and not proof['scientific_source_exception_added'];rows=[r for r in proof['historical_documents'] if r['document']==expected];assert len(rows)==1;row=rows[0];assert pin(data/row['archive'])==row['archive_pin']
    assert row['archive'] in ['smr-catalog-support.zip','dndscv-catalog-support.zip'];old_proof=json.loads((data/row['archive'].replace('-support.zip','-validation.json')).read_bytes());assert old_proof['archive']==row['archive_pin'];assert unpack(data/row['archive'],old_proof)['current-source/README.md']==archived

def verify_review_extension(root,data,reviews):
    p=json.loads((data/'smr-catalog-validation.json').read_bytes());assert pin(data/'smr-catalog-support.zip')==p['archive'];files=unpack(data/'smr-catalog-support.zip',p)
    for name,value in p['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==value
        if name=='README.md':historical_readme_document(root,data,files['current-source/'+name],value)
        else:verify_source(root,data,name,value['sha256'])
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==944;roles=json.loads((data/'loftee-role-coverage.json').read_bytes());assert roles['status']=='passed-eight-LOFTEE-primary-role-reviews' and roles['applied'];assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['total_observations'],roles['supporting_pointers'])==(944,8,952,2450,328)
    assert pin(data/'loftee-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'loftee-prior-reviews.json.gz').read_bytes())==old_raw and byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert gzip.decompress((data/'loftee-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json'] and pin(data/'loftee-prior-papers.json.gz')==roles['prior_papers_archive']
    for name in ['smr-role-coverage.json','smr-prior-reviews.json.gz','smr-prior-papers.json.gz','smr-source-audit.json']:assert (data/name).read_bytes()==files['catalog-snapshot/'+name]
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(raw);draft_path=root/'tools/literature/loftee-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert pin(draft_path)['sha256']==roles['draft_sha256'] and pin(root/'tools/literature/curate_loftee_uses.py')['sha256']==roles['curator_sha256'] and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'};assert current['reviews'][:944]==old['reviews'] and current['reviews'][944:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==952 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==328;verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)

@contextlib.contextmanager
def current_catalog_review_context():
    saved=(prior.verify_review_extension,prior.historical_readme_document)
    try:
        prior.verify_review_extension=verify_review_extension;prior.historical_readme_document=historical_readme_document
        with prior.current_catalog_review_context():yield
    finally:prior.verify_review_extension,prior.historical_readme_document=saved

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
    with current_catalog_review_context():return prior.smr_metadata(root,data,tool)

def loftee_metadata(root,data):
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);p=json.loads((root/'docs/literature/loftee-workflow-development/VALIDATION.json').read_bytes());assert p['adapter_admitted'] and p['implementation_credit'] and p['admitted_named_tools']==['loftee']
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/LofteeWorkflowCli.java',implementation_scope='Typed unchanged-original VEP105.0/LOFTEE GRCh38 and cited-fork annotation with supplied indexed GTF/FASTA, optional original GERP/ancestral/SQLite resources, extended splice settings and fork supervision. Native Java verifies complete original inputs, FASTA/FAI/GZI, REF/sample/genotype identity and every returned variant/allele/transcript annotation. HC/LC/OS, flags, non-target empty calls and raw diagnostics remain distinct. VEP cache execution explicitly SKIP3; paper-specific transcript selection, masks, other predictors and downstream tests remain separate. Read LOFTEE_WORKFLOWS.md.',validation_note='Exact original source editions compared over complete synthetic original records and fields, including compressed references/genotypes, extended splice features, ancestral state and conservation queries. Only source-unordered LoF_info token order may differ; raw outputs and token multiplicities retained without numeric tolerance. Portable native checks and actual skip/input/capacity/timeout/late-plugin-error protocols; FAIL2 admits no atomic result. Exact private runtime preparation or SKIP3 where missing/forbidden/different. Whole-file runtime checks remain strict; unreachable Perl5.22 ViennaRNA files are explicitly excluded from the Perl5.32 profile. No biological calibration, paper binary identity, whole-paper reproduction or canonical release availability is inferred.',source_roles_reviewed=8,source_role_coverage='loftee-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.LofteeWorkflowCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)

LOFTEE_WARNING='''## LOFTEE: warnings and required actions

Use the development LofteeWorkflowCli and LOFTEE_WORKFLOWS.md. Require exit0 and result_admitted=true; read WARNINGS.md, original logs, process receipts and complete native ledgers. **SKIP3 skips dependent analysis** when the exact permitted Linux x86_64 runtime/resources are missing, different or forbidden. VEP cache execution is unvalidated and explicitly skipped. Where permitted use the locked private installer/exporter; preserve preparation failures and retry NEW short paths. Analysis installs nothing.

**FAIL2 admits no atomic-batch result**, including earlier successful cases. Original VEP can exit0 after a plugin error; the native audit refuses that output. Keep all inputs, failures and partials, correct the named cause and retry NEW paths. Never repair identifiers/alleles, omit variants/transcripts, manufacture HC/LC, strip flags or replace NA/NaN/Inf. An empty non-target confidence is not a benign call. SQLite conservation requires threads1; original complete-CDS database checking is outside this local profile.

Original extended-splice LoF_info feature order can vary through Perl hash iteration. Raw VCFs remain unchanged; validation preserves every literal token and its multiplicity and compares every other field/order exactly. Runtime checks retain complete SHA256 verification; eight unreachable Perl5.22 ViennaRNA files are explicitly excluded from the Perl5.32 execution closure, with prior identities/failures retained. Synthetic controls do not certify human resource provenance, complete biological transcript coverage, HC calibration, historical VEP/LOFTEE versions or paper masks/MANE/canonical selection. Read the guide's path, index, capacity and timeout requirements. Canonical/released availability and whole-goal completion are not claimed.

'''
LOFTEE_ACTIONS='''<section id="loftee-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>LOFTEE: warnings and required actions</h2><p>Use development <code>LofteeWorkflowCli</code> and <code>LOFTEE_WORKFLOWS.md</code>. Require exit0 and <code>result_admitted=true</code>; read original logs, process receipts and complete native ledgers.</p><p><strong>SKIP3: dependent analysis skipped.</strong> Prepare exact permitted Linux x86_64 dependencies/resources with the locked private installer/exporter, or keep the analysis skipped. VEP cache execution is unvalidated and skipped. Preserve installer errors; retry NEW short paths where permitted. Analysis installs nothing.</p><p><strong>FAIL2: no atomic-batch result admitted, including earlier successful cases.</strong> Original exit0 does not override a plugin error. Preserve all inputs, failures and partials; correct the named cause. Never repair identifiers/alleles, omit variants/transcripts, manufacture HC/LC, remove flags or replace NA/NaN/Inf. SQLite conservation requires threads1.</p><p>Complete reference/index/REF and runtime checks remain strict. Source-unordered extended splice info retains raw order and all literal tokens. Eight unreachable legacy Perl5.22 files are explicitly excluded from the Perl5.32 closure. Synthetic controls do not certify human reference provenance, biological transcript completeness, HC calibration, paper versions or MANE/canonical masks. Read the guide's capacity/timeout/path requirements; no canonical release or whole-paper completion is inferred.</p></section>'''
