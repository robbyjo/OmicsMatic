# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact952+8 Hail role extension; frozen predecessor scientific/source gates remain strict."""
import contextlib,gzip,io,json
from types import SimpleNamespace
import biomart_catalog as prior
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

load_catalog_registry=prior.load_catalog_registry
for _name in ['MONOCLE_WARNING','MONOCLE_ACTIONS','NORMAL_FIT_WARNING','NORMAL_FIT_ACTIONS','SOUPX_WARNING','SOUPX_ACTIONS','DNDSCV_WARNING','DNDSCV_ACTIONS','SMR_WARNING','SMR_ACTIONS','LOFTEE_WARNING','LOFTEE_ACTIONS','BIOMART_WARNING','BIOMART_ACTIONS']:globals()[_name]=getattr(prior,_name)
_frozen_952_verifier=prior.prior.verify_review_extension

class PriorReviewFile:
    def __init__(self,raw):self.raw=raw
    def read_bytes(self):return self.raw
    def open(self,mode):assert mode=='rb';return io.BytesIO(self.raw)
    def stat(self):return SimpleNamespace(st_size=len(self.raw))

class PriorReviewRoot:
    """The archived952-role input to the frozen952 validator; all scientific source paths still resolve to the real repository."""
    def __init__(self,root,raw):self.root=root;self.raw=raw
    def __fspath__(self):return str(self.root)
    def __truediv__(self,name):return PriorReviewFile(self.raw) if str(name)=='tools/literature/reviewed-uses.json' else self.root/name

def historical_readme_document(root,data,archived,expected):
    assert byte_pin(archived)==expected
    if pin(root/'README.md')==expected:return
    proof=json.loads((data/'hail-root-readme-evolution.json').read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-exact-README-documentation-evolution' and proof['path']=='README.md' and proof['after']==pin(root/'README.md') and proof['scientific_source_exception_added'] is False;rows=[r for r in proof['historical_documents'] if r['document']==expected];assert len(rows)==1;row=rows[0];assert row['archive'] in ['biomart-catalog-support.zip','loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip'] and pin(data/row['archive'])==row['archive_pin'];old=json.loads((data/row['archive'].replace('-support.zip','-validation.json')).read_bytes());assert old['archive']==row['archive_pin'] and unpack(data/row['archive'],old)['current-source/README.md']==archived

def verify_review_extension(root,data,reviews):
    proof=json.loads((data/'biomart-catalog-validation.json').read_bytes());assert pin(data/'biomart-catalog-support.zip')==proof['archive'];files=unpack(data/'biomart-catalog-support.zip',proof)
    for name,want in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==want
        if name=='README.md':historical_readme_document(root,data,files['current-source/'+name],want)
        else:verify_source(root,data,name,want['sha256'])
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==952;roles=json.loads((data/'hail-role-coverage.json').read_bytes());assert roles['status']=='passed-eight-Hail-primary-role-reviews' and roles['applied'] and (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'])==(952,8,960,331)
    assert pin(data/'hail-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'hail-prior-reviews.json.gz').read_bytes())==old_raw and byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert pin(data/'hail-prior-papers.json.gz')==roles['prior_papers_archive'] and gzip.decompress((data/'hail-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json']
    current_raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(current_raw);draft_path=root/'tools/literature/hail-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert pin(draft_path)['sha256']==roles['draft_sha256'] and pin(root/'tools/literature/curate_hail_uses.py')['sha256']==roles['curator_sha256'] and byte_pin(current_raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'} and current['reviews'][:952]==old['reviews'] and current['reviews'][952:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==960 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==331
    # Validate the complete archived952 input through its unchanged validator.
    # Real source checks still use the exact reverse-delta chain for current960.
    saved=prior.prior.historical_readme_document
    try:
        prior.prior.historical_readme_document=historical_readme_document
        _frozen_952_verifier(PriorReviewRoot(root,old_raw),data,old['reviews'])
    finally:prior.prior.historical_readme_document=saved
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)

@contextlib.contextmanager
def current_catalog_review_context():
    targets=[(prior,'verify_review_extension'),(prior.prior,'verify_review_extension'),(prior,'historical_readme_document'),(prior.prior,'historical_readme_document')];saved=[getattr(m,n) for m,n in targets]
    try:
        for m,n in targets:setattr(m,n,verify_review_extension if n=='verify_review_extension' else historical_readme_document)
        with prior.current_catalog_review_context():yield
    finally:
        for (m,n),value in zip(targets,saved):setattr(m,n,value)

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
def biomart_metadata(root,data):return _metadata('biomart_metadata',root,data)

def hail_metadata(root,data):
    from hail_workflow_evidence import verify
    p=verify(emit=False);verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/HailWorkflowCli.java',implementation_scope='Typed unchanged-original Hail0.2.139/0.2.130 VCF/BGZF/MatrixTable workflows: sample/variant QC, explicit entry/row/column masks, annotations/selections/keys, LD/pruning/PCA, GRCh37-to-GRCh38 locus liftover and original LOCAL hailtop.Batch resource DAGs. Complete native outputs and typed schemas/states retain source calls, IDs, order, alleles, missing/NaN/Inf and strand. Portable native Java sample-QC component is separately validated. No arbitrary joint-calling framework, SERVICE/cloud execution, implicit allele repair, paper mask,0.2.78/0.2.116 parity or whole-paper claim. Read HAIL_WORKFLOWS.md.',validation_note='Both original editions execute ten complete cases with full native readback. Independent audits check every original call plus QC/AF,64 LD values,38 PCA equations,9 liftover loci,42 axis-filtered entries,72 BGZF roundtrip entries and original Batch graph/4outputs. Final candidate passes against fresh offline exact private installation. Missing/different/forbidden dependencies SKIP3; FAIL2 admits no atomic result. Typed requests:3profiles+26rejections on WindowsJava21/25/LinuxJava21. Export/preparation denial, existing-runtime capture, host/runtime/service skips, timeout, late liftover and failed job preserve atomic admission/owned cleanup. Complete runtime/JDK before-after trees and302 original wheel members including backendJAR remain exact. Synthetic local controls do not certify cohort/cloud scale, arbitrary job biology or historical paper parameters.',source_roles_reviewed=8,source_role_coverage='hail-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.HailWorkflowCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)

HAIL_WARNING='''## Hail: warnings and required actions

Use development **HailWorkflowCli**, separate **HailWorkflowSetupCli** and **HAIL_WORKFLOWS.md**. Require exit0 AND `analysis-status.json result_admitted=true`; keep the entire output, `manifest.json`, native `.mt`/`.ht`/`.bm` families, typed snapshots, original logs and process receipts. Native `HailSampleQc` has its own Java-only input profile.

**SKIP3: dependent analysis is skipped** when the exact permitted Linux x86_64 Python/Hail/Spark/Java11 runtime or explicit job executable is unavailable, changed or forbidden. Export the setup kit and use CAPTURE for an allowed existing runtime, or INSTALL in NEW private paths where permitted. `--allow-install` and, for missing archives, `--allow-download` are explicit. Preserve failed prefixes/logs. Analysis installs nothing. Required SERVICE/cloud execution is unconfigured and skipped before input capture; no credentials or cloud jobs are used.

**FAIL2: no result from the whole atomic batch is admitted**, including earlier successful cases. Preserve inputs and all provisional native outputs; correct the named input, runtime, operation, capacity, timeout or failed-job cause and retry NEW output paths. Never drop failing rows/cases, repair identifiers/alleles, replace missing/NaN/Inf or substitute releases/builds. Liftover retains source allele strings and reports strand; it does not certify target-reference alleles. Unmapped loci are retained missing or fail the whole batch under the explicit policy.

Batch output paths and input basenames must meet the guide's ASCII/path contract because the original LocalBackend uses shell resource paths. Keep complete files; dense snapshots and LD matrices must fit explicit capacities. No cohort-scale performance, SERVICE/cloud/RCTD program, arbitrary joint-genotyping framework, biological calibration, paper-specific thresholds,0.2.78/0.2.116 binary parity, whole-paper reproduction or canonical release is inferred. Original diagnostics and historical Java21 warnings remain retained; the current upstream workflows use Java11.

'''
HAIL_ACTIONS='''<section id="hail-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Hail: warnings and required actions</h2><p>Use development <code>HailWorkflowCli</code>, separate <code>HailWorkflowSetupCli</code> and <code>HAIL_WORKFLOWS.md</code>. Require exit0 and <code>result_admitted=true</code>; retain native files, complete typed snapshots, manifest, original diagnostics and cleanup receipts.</p><p><strong>SKIP3: skip dependent analysis.</strong> Supply the exact permitted Linux x86_64 Python/Hail/Spark/Java11 runtime and explicit job executables. CAPTURE reads an existing allowed runtime; INSTALL uses NEW private paths and explicit install/download flags where permitted. Preserve preparation errors. Analysis installs nothing. Required SERVICE/cloud execution is unconfigured and skipped before data capture.</p><p><strong>FAIL2: no atomic-batch result admitted, including earlier successful cases.</strong> Keep inputs, errors and provisional native files; correct the named input/runtime/operation/capacity/timeout/job cause and retry NEW paths. Never omit failing cases, repair IDs/alleles, replace missing/NaN/Inf or substitute releases/builds. Liftover retains source alleles and reports strand; choose missing retention or whole-batch failure explicitly.</p><p>Read the Batch ASCII/path and dense-output capacity limits. LOCAL dependencies and synthetic original controls do not certify cloud/cohort scale, job biology, paper masks, historical0.2.78/0.2.116 parity, whole-paper reproduction or canonical release availability. Historical diagnostics remain intact; current upstream workflows use Java11.</p></section>'''
