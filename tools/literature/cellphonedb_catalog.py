# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""CellPhoneDB source-role/registry extension with strict unchanged predecessor scientific gates."""
import contextlib,gzip,io,json,os
from types import SimpleNamespace
import leafcutter_catalog as prior
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

_frozen_966_verifier=prior.verify_review_extension
for _name in ['MONOCLE_WARNING','MONOCLE_ACTIONS','NORMAL_FIT_WARNING','NORMAL_FIT_ACTIONS','SOUPX_WARNING','SOUPX_ACTIONS','DNDSCV_WARNING','DNDSCV_ACTIONS','SMR_WARNING','SMR_ACTIONS','LOFTEE_WARNING','LOFTEE_ACTIONS','BIOMART_WARNING','BIOMART_ACTIONS','HAIL_WARNING','HAIL_ACTIONS','LEAFCUTTER_WARNING','LEAFCUTTER_ACTIONS']:globals()[_name]=getattr(prior,_name)


def load_catalog_registry(root):
    original=prior.load_catalog_registry(root);extra=json.loads((root/'tools/literature/cellphonedb-named-software-registry.json').read_bytes());assert len(original)==428 and len(extra)==5 and not({r['id'] for r in original}&{r['id'] for r in extra});result=original+extra;assert len(result)==len({r['id'] for r in result})==433;return result


class PriorReviewFile:
    def __init__(self,raw):self.raw=raw
    def read_bytes(self):return self.raw
    def open(self,mode):assert mode=='rb';return io.BytesIO(self.raw)
    def stat(self):return SimpleNamespace(st_size=len(self.raw))


class PriorReviewRoot:
    def __init__(self,root,raw):self.root=root;self.raw=raw
    def __str__(self):return os.fspath(self.root)
    def __fspath__(self):return os.fspath(self.root)
    def __truediv__(self,name):return PriorReviewFile(self.raw) if str(name)=='tools/literature/reviewed-uses.json' else self.root/name


def historical_readme_document(root,data,archived,expected):
    assert byte_pin(archived)==expected
    if pin(root/'README.md')==expected:return
    p=json.loads((data/'cellphonedb-root-readme-evolution.json').read_bytes());assert p['schema_version']==1 and p['status']=='passed-exact-README-documentation-evolution' and p['path']=='README.md' and p['after']==pin(root/'README.md') and p['scientific_source_exception_added'] is False;rows=[r for r in p['historical_documents'] if r['document']==expected];assert len(rows)==1;row=rows[0];assert row['archive'] in ['leafcutter-catalog-support.zip','hail-catalog-support.zip','biomart-catalog-support.zip','loftee-catalog-support.zip','smr-catalog-support.zip','dndscv-catalog-support.zip'] and pin(data/row['archive'])==row['archive_pin'];old=json.loads((data/row['archive'].replace('-support.zip','-validation.json')).read_bytes());assert old['archive']==row['archive_pin'] and unpack(data/row['archive'],old)['current-source/README.md']==archived


def verify_review_extension(root,data,reviews):
    proof=json.loads((data/'leafcutter-catalog-validation.json').read_bytes());assert pin(data/'leafcutter-catalog-support.zip')==proof['archive'];files=unpack(data/'leafcutter-catalog-support.zip',proof)
    for name,want in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==want
        if name=='README.md':historical_readme_document(root,data,files['current-source/'+name],want)
        else:verify_source(root,data,name,want['sha256'])
    old_raw=files['catalog-snapshot/reviewed-uses.json'];old=json.loads(old_raw);assert len(old['reviews'])==966;roles=json.loads((data/'cellphonedb-role-coverage.json').read_bytes());assert roles['status']=='passed-fourteen-CellPhoneDB-and-related-primary-role-reviews' and roles['applied'] and (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'],roles['new_named_observations'])==(966,14,980,340,7)
    assert pin(data/'cellphonedb-prior-reviews.json.gz')==roles['prior_review_archive'] and gzip.decompress((data/'cellphonedb-prior-reviews.json.gz').read_bytes())==old_raw and byte_pin(old_raw)['sha256']==roles['before_reviews_sha256'];assert pin(data/'cellphonedb-prior-papers.json.gz')==roles['prior_papers_archive'] and gzip.decompress((data/'cellphonedb-prior-papers.json.gz').read_bytes())==files['catalog-snapshot/papers.json']
    current_raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(current_raw);drafts=[]
    for name in ['cellphonedb-use-reviews.json','cellphonedb-additional-use-reviews.json']:
        path=root/('tools/literature/'+name);assert pin(path)==roles['draft_pins'][name];drafts.extend(json.loads(path.read_bytes()))
    assert pin(root/'tools/literature/curate_cellphonedb_uses.py')['sha256']==roles['curator_sha256'] and pin(root/'tools/literature/cellphonedb-named-software-registry.json')==roles['registry_extension'];assert byte_pin(current_raw)['sha256']==roles['reviews_sha256'] and {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'} and current['reviews'][:966]==old['reviews'] and current['reviews'][966:]==[d['review'] for d in drafts] and reviews==current['reviews'];assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==980 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==340
    saved=prior.historical_readme_document
    try:
        prior.historical_readme_document=historical_readme_document
        _frozen_966_verifier(PriorReviewRoot(root,old_raw),data,old['reviews'])
    finally:prior.historical_readme_document=saved
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(old_raw)['sha256']);load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    modules=[prior,prior.prior,prior.prior.prior,prior.prior.prior.prior];targets=[(m,n) for m in modules for n in ['verify_review_extension','historical_readme_document']];saved=[getattr(m,n) for m,n in targets]
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
def leafcutter_metadata(root,data):return _metadata('leafcutter_metadata',root,data)


def cellphonedb_metadata(root,data):
    from validate_cellphonedb_integration_evidence import verify
    p=verify(emit=False);verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/CellPhoneDbWorkflowCli.java',implementation_scope='Typed unchanged-original CellPhoneDB5.0.1 simple, statistical-permutation and supplied-DEG workflows with explicit expression, database, metadata, microenvironment, scoring, active-TF, seed and subsampling choices. Native Java recomputes raw mapping/float32 compensated preparation, observed means/fractions/complex minima, strict gates, supplied-DEG flags and P values from actual packed null bits; every original exported table is checked against data-only typed returns. Original scoring, CellSign, random draws and sampling geometry stay upstream. No normalization, DEG fitting, curated database, arbitrary object input, reported5.1/5.0.0/2.1.4 substitution, standalone Squidpy/Spyrrow/COMMOT/iTALK parity, database authenticity or biological/paper claim. Read CELLPHONEDB_WORKFLOWS.md.',validation_note='Fresh exact private installation:43Conda and49Python source archives,52distribution versions and original wheel/import checks; unchanged complete runtime trees. Nineteen paired positive workflows include CSV, numeric canonical labels, dense/CSR/fractional-float32 H5AD, supplied DEGs, scoring, CellSign, entropy sampling and a two-process statistical case. Seventeen deterministic serial cases match complete original output bytes and RNG states. Independent native audit of all19cases passes on WindowsJava21/25 and LinuxJava21. Seven coherent corruption controls,12producer protocols,6public-command controls and6export/platform/setup protocols retain actual exits and atomic admission. Subsampler source None seed remains entropy based; failed sampling requires an explicit fail versus full-count-fallback policy. Original source errors, class sentinels, zero-cutoff behavior, strict tails and all failed historical controls remain visible. No independent random generator/scoring/CellSign/geometry implementation, historical edition parity, cohort-scale or biological calibration claim.',source_roles_reviewed=7,related_source_roles_reviewed=7,source_role_coverage='cellphonedb-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.CellPhoneDbWorkflowCli',runtime_setup_cli_class='org.jlinalg.cli.CellPhoneDbSetupCli',analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)


CELLPHONEDB_WARNING='''## CellPhoneDB: warnings and required actions

Use development **CellPhoneDbWorkflowCli**, separate **CellPhoneDbSetupCli** and **CELLPHONEDB_WORKFLOWS.md**. Require exit0 AND `analysis-status.json result_admitted=true`. Keep the complete directory: original inputs/reports, typed data-only snapshots, native input-disposition ledgers, RNG and selected-cell states, warnings, manifest and process receipts. Debug pickle files remain opaque and are never loaded by the adapter.

**SKIP3: skip dependent analysis** if the exact permitted Linux x86_64 runtime, edition, required archives, micromamba or installation/download permission is missing or forbidden. Export the kit and follow SETUP.md with system Python3.11+, exact micromamba2.10.0 and NEW private setup/prefix paths. Installation needs `--allow-install`; uncached downloads also need `--allow-download`, otherwise supply exact cached archives. A case-insensitive environment filesystem skips before installing. Preserve failed prefixes and all diagnostics. RUN installs nothing. Reported5.1,5.0.0 and2.1.4 are never silently replaced by5.0.1.

**FAIL2: no result from the whole atomic batch is admitted, including earlier successful cases.** Preserve all originals/provisionals, correct the named source-method, input, runtime, numeric, timeout or capacity problem and retry NEW paths. Never omit failed cases, repair IDs/counts, replace missing/NaN/Inf or infer paper settings. Metadata coercion that changes labels fails admission. Supply the intended normalized expression; normalization and differential-expression fitting are separate.

**Subsampling is not deterministic from the global/permutation seeds.** The original full method constructs Subsampler with debug_seed=None, reseeding NumPy from entropy. It catches internal sampling errors and returns full counts. Choose explicit `failure_policy=fail` to reject that fallback, or `retain-original-full-counts` to retain it with a prominent not-subsampled warning. Read subsampling-state.json and actual selected cells/errors/RNG states. A separate seeded kernel control does not replace the full-method entropy behavior. Parallel null draws remain original behavior without a serial/parallel byte-parity promise.

Original gates/tails are strict >threshold and >observed; P values use hits/iterations without plus-one, with source zero/gate rules. P cutoff0 is falsy in significance masking. Simple exported means are ungated. Alias/no-complex/protein-ID source errors, complex-index deconvolution and original UserWarning suppression are retained. Scoring, CellSign, RNG draws and sampling geometry execute original upstream code. No curated database authenticity, generic communication equivalence, paper parameters, whole-paper/biological calibration or canonical release is inferred. Owned-process/wall/output limits are not OS RAM/CPU isolation; complete input/matrix/audit/heap capacities must be respected.

'''
CELLPHONEDB_ACTIONS='''<section id="cellphonedb-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>CellPhoneDB: warnings and required actions</h2><p>Use development <code>CellPhoneDbWorkflowCli</code>, separate <code>CellPhoneDbSetupCli</code> and <code>CELLPHONEDB_WORKFLOWS.md</code>. Require exit0 and <code>result_admitted=true</code>; retain every original input/output, typed snapshot, native disposition ledger, RNG/selected-cell state, manifest, diagnostic and cleanup receipt. Debug pickles remain opaque.</p><p><strong>SKIP3: skip dependent analysis.</strong> Supply the exact permitted Linux x86_64 runtime, edition and archives. Export and follow SETUP.md using Python3.11+, pinned micromamba2.10.0, case-sensitive storage and NEW private paths. Installation/download need their explicit permission flags or exact cached archives. If forbidden, keep analysis skipped. RUN installs nothing;5.1/5.0.0/2.1.4 are never substituted by5.0.1.</p><p><strong>FAIL2: no result from the whole atomic batch, including earlier successes.</strong> Preserve originals/provisionals, resolve the named input/source/runtime/precision/timeout/capacity problem and retry NEW paths. Never drop failures, repair IDs/counts, replace missing/nonfinite states or infer paper parameters. Supply intended normalized expression and DEGs; normalization and DEG fitting are separate.</p><p><strong>Subsampling uses entropy despite the global/permutation seeds.</strong> Original Subsampler initializes with debug_seed=None. Failed sampling returns full counts: explicit <code>failure_policy=fail</code> rejects it; <code>retain-original-full-counts</code> retains it with a not-subsampled warning. Read actual selected cells, RNG states and source errors. Seeded kernel controls and parallel calls do not promise full-method repeatability.</p><p>Retain strict expression/permutation tails, hits/iterations without plus-one, falsy zero-cutoff behavior, ungated simple means, original alias/no-complex errors, complex-index deconvolution and diagnostic suppression. Scoring, CellSign, RNG draws and geometry remain original upstream algorithms. No historical-edition substitution, arbitrary communication-tool parity, database authenticity, biological/whole-paper or canonical-release claim. Owned time/output/process limits do not isolate OS memory/CPU.</p></section>'''
