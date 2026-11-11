# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact960+6 LeafCutter role extension; frozen predecessor scientific/source gates remain strict."""
import contextlib,gzip,io,json,os
from types import SimpleNamespace
import hail_catalog as prior
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

load_catalog_registry=prior.load_catalog_registry
for _name in ['MONOCLE_WARNING','MONOCLE_ACTIONS','NORMAL_FIT_WARNING','NORMAL_FIT_ACTIONS','SOUPX_WARNING','SOUPX_ACTIONS','DNDSCV_WARNING','DNDSCV_ACTIONS','SMR_WARNING','SMR_ACTIONS','LOFTEE_WARNING','LOFTEE_ACTIONS','BIOMART_WARNING','BIOMART_ACTIONS','HAIL_WARNING','HAIL_ACTIONS']:globals()[_name]=getattr(prior,_name)
_frozen_960_verifier=prior.verify_review_extension

class PriorReviewFile:
    def __init__(self,raw):self.raw=raw
    def read_bytes(self):return self.raw
    def open(self,mode):assert mode=='rb';return io.BytesIO(self.raw)
    def stat(self):return SimpleNamespace(st_size=len(self.raw))

class PriorReviewRoot:
    """The archived960-role input to the frozen960 validator; all scientific source paths still resolve to the real repository."""
    def __init__(self,root,raw):self.root=root;self.raw=raw
    def __str__(self):return os.fspath(self.root)
    def __fspath__(self):return os.fspath(self.root)
    def __truediv__(self,name):return PriorReviewFile(self.raw) if str(name)=='tools/literature/reviewed-uses.json' else self.root/name

def historical_readme_document(root,data,archived,expected):
    assert byte_pin(archived)==expected
    if pin(root/'README.md')==expected:return
    proof=json.loads((data/'leafcutter-root-readme-evolution.json').read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-exact-README-documentation-evolution' and proof['path']=='README.md' and proof['after']==pin(root/'README.md') and proof['scientific_source_exception_added'] is False;rows=[r for r in proof['historical_documents'] if r['document']==expected];assert len(rows)==1;row=rows[0];assert row['archive'] in ['hail-catalog-support.zip','biomart-catalog-support.zip','loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip'] and pin(data/row['archive'])==row['archive_pin'];old=json.loads((data/row['archive'].replace('-support.zip','-validation.json')).read_bytes());assert old['archive']==row['archive_pin'] and unpack(data/row['archive'],old)['current-source/README.md']==archived

def verify_review_extension(root,data,reviews):
    proof=json.loads((data/'hail-catalog-validation.json').read_bytes());assert pin(data/'hail-catalog-support.zip')==proof['archive'];files=unpack(data/'hail-catalog-support.zip',proof)
    for name,want in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==want
        if name=='README.md':historical_readme_document(root,data,files['current-source/'+name],want)
        else:verify_source(root,data,name,want['sha256'])
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==960;roles=json.loads((data/'leafcutter-role-coverage.json').read_bytes());assert roles['status']=='passed-six-LeafCutter-primary-role-reviews' and roles['applied'] and (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'])==(960,6,966,334)
    assert pin(data/'leafcutter-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'leafcutter-prior-reviews.json.gz').read_bytes())==old_raw and byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert pin(data/'leafcutter-prior-papers.json.gz')==roles['prior_papers_archive'] and gzip.decompress((data/'leafcutter-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json']
    current_raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(current_raw);draft_path=root/'tools/literature/leafcutter-use-reviews.json';drafts=json.loads(draft_path.read_bytes());assert pin(draft_path)['sha256']==roles['draft_sha256'] and pin(root/'tools/literature/curate_leafcutter_uses.py')['sha256']==roles['curator_sha256'] and byte_pin(current_raw)['sha256']==roles['reviews_sha256'];assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'} and current['reviews'][:960]==old['reviews'] and current['reviews'][960:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==966 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==334
    # Validate the complete archived960 input through its unchanged validator.
    # Real source checks still use the exact reverse-delta chain for current966.
    saved=prior.historical_readme_document
    try:
        prior.historical_readme_document=historical_readme_document
        _frozen_960_verifier(PriorReviewRoot(root,old_raw),data,old['reviews'])
    finally:prior.historical_readme_document=saved
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)

@contextlib.contextmanager
def current_catalog_review_context():
    targets=[(m,n) for m in [prior,prior.prior,prior.prior.prior] for n in ['verify_review_extension','historical_readme_document']];saved=[getattr(m,n) for m,n in targets]
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

def hail_metadata(root,data):return _metadata('hail_metadata',root,data)


def leafcutter_metadata(root,data):
    from leafcutter_workflow_evidence import verify
    result=verify(root=root,data=root/'docs/literature/leafcutter-workflow-development',emit=False);proof=json.loads((root/'docs/literature/leafcutter-workflow-development/VALIDATION.json').read_bytes());verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);assert result['implementation_credit'] is True
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/LeafCutterWorkflowCli.java',implementation_scope='Unchanged original source2c9907ef66ad/R package0.2.9 RegTools junction clustering, phenotype preparation/PCA and original differential-splicing fits with explicit thresholds, covariates, source sample selection and atomic complete pipelines. Native Java independently audits counts, preprocessing, PCA geometry, retained fitted posteriors, effects and LRT/BH values. Complete original files, RDS fits, source states, warnings, IDs and sample maps remain retained. No BAM extraction, downstream QTL engine, LeafViz, bgzip/tabix execution, historical0.2.6/0.2.7 binary parity or whole-paper reproduction. Read LEAFCUTTER_WORKFLOWS.md.',validation_note='Eight complete original/public cases match all six original differential TSV tables byte for byte;206829 native audit operations. Independent retained readback checks16 objectives,18 intron effects,14 filtered clusters and original PCA/NaN states. Actual final candidate repeats all cases against a fresh offline exact239-archive private installation. Nine host/version/runtime/permission/failure/timeout/output-protection checks retain atomic admission and owned cleanup. Three actual input-contract controls reject numeric/logical sample IDs and wrong ratio headers before upstream execution. Case-insensitive setup/cache or prefix parents skip before installer invocation. All compiler warnings, failed case-collision installation and auditor failures are retained. Namespace24 functions/five Stan models and complete runtime/library before-after inventories remain bound. Native checks do not independently refit, certify global optima or establish paper-specific masks/axes.',source_roles_reviewed=7,new_source_roles_reviewed=6,source_role_coverage='leafcutter-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],development_cli_class='org.jlinalg.cli.LeafCutterWorkflowCli',runtime_setup_cli_class='org.jlinalg.cli.LeafCutterWorkflowSetupCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)


LEAFCUTTER_WARNING='''## LeafCutter: warnings and required actions

Use development **LeafCutterWorkflowCli**, separate **LeafCutterWorkflowSetupCli** and **LEAFCUTTER_WORKFLOWS.md**. Require exit0 AND `analysis-status.json result_admitted=true`. Preserve the whole directory: manifest, original source/counts, full native outputs, RDS fits, lossless reports, sample/identifier maps, warnings and process receipts.

**SKIP3: skip dependent analysis** if the exact permitted Linux x86_64 Python/R/Stan runtime, edition, dependency or installation is unavailable, changed or forbidden. CAPTURE binds existing matching original build receipts; INSTALL requires explicit permission flags, a supplied permitted micromamba and NEW private paths. Missing archives also require download permission or an exact flat cache. Setup/cache and prefix parents must be case-sensitive; otherwise preparation skips before invoking the installer or creating a software prefix. Prefer native Linux/WSL storage; see the guide for permitted Windows folder preparation. Preserve failed prefixes and compiler diagnostics. RUN installs nothing.

**FAIL2: no atomic-batch result admitted, including earlier completed cases/stages.** Retain all originals and provisionals, correct the named input/runtime/model/precision/timeout/process/capacity cause and retry NEW paths. Never drop a failed row/case, repair counts/IDs, replace NA/NaN, infer paper thresholds or substitute editions. Read the two distinct count headers and explicit groups-file-order selection. All-numeric/logical sample-ID columns require an external explicit string-ID mapping, because original R can coerce them into positional indexing.

Original phenotype PCA treats introns as observations and samples as features; literal X differs from chrX, and undefined explained-variance ratios remain NaN. Source minimum-reads gates individual introns as well as clusters. P201 reports50reads/500kb while current source defaults are30reads/100kb; historical equivalence is unproved. Original add_chr changes whole vectors according to the first ID; raw/reported IDs remain mapped. Exon endpoint annotation ignores strand and does not certify assembly correctness. Original suppressed optimizer console output and discarded null-refit return codes remain outside what upstream exposes. Native audits evaluate retained estimates without independent refitting/global-optimum or biological-calibration claims. LeafViz, BAM extraction, QTL engines, indexing, paper preprocessing and0.2.6/0.2.7 parity remain separate. P238's comparison does not resolve fresh quantification versus reused weights. No canonical release, whole-paper reproduction or completed250-paper goal is inferred.

'''
LEAFCUTTER_ACTIONS='''<section id="leafcutter-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>LeafCutter: warnings and required actions</h2><p>Use development <code>LeafCutterWorkflowCli</code>, separate <code>LeafCutterWorkflowSetupCli</code> and <code>LEAFCUTTER_WORKFLOWS.md</code>. Require exit0 and <code>result_admitted=true</code>; preserve the whole directory, complete original files, RDS fits, lossless reports, manifest, sample/identifier maps, warnings and cleanup receipts.</p><p><strong>SKIP3: skip dependent analysis.</strong> Supply the exact permitted Linux x86_64 Python/R/Stan runtime and edition. CAPTURE requires matching original build receipts; INSTALL needs explicit install/download permissions or exact cached archives, a supplied permitted micromamba and NEW private paths. Setup/cache and prefix parents must be case-sensitive; unsuitable storage skips before installer invocation or prefix creation. See the guide for permitted Linux/WSL or Windows folder preparation. Preserve failed prefixes and compiler diagnostics. Analysis installs nothing.</p><p><strong>FAIL2: no result from the whole atomic batch, including earlier completed stages/cases.</strong> Keep originals and provisional files; correct the named input, runtime, model, numeric, timeout, process or capacity cause and retry NEW paths. Never omit failures, repair counts/IDs, replace NA/NaN, infer thresholds or substitute editions. Read the count-header and groups-file-order contract; R-coercible numeric/logical sample IDs require an explicit external mapping.</p><p>PCA uses introns as observations and samples as features; X differs from chrX, and undefined ratios remain NaN. Current30reads/100kb defaults do not establish P201 historical50reads/500kb parity. Original chromosome prefixing uses the first ID; endpoint gene annotation ignores strand. Retain source maps and annotation provenance. Suppressed optimizer diagnostics and discarded refit return codes are not exposed by upstream. No independent optimization, biological calibration, LeafViz/QTL/BAM/indexing workflow, historical0.2.6/0.2.7 parity, P238 fresh-run inference, whole-paper reproduction or canonical release is claimed.</p></section>'''

