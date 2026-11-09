# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact six-role GSEApy extension with strict unchanged scientific sources and historical metadata views."""
import contextlib,copy,gzip,html,json
from scfates_catalog import *
import scfates_catalog as prior
import palantir_catalog as frozen
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

_frozen_988_verifier=frozen.verify_review_extension


def verify_review_extension(root,data,reviews):
    roles=json.loads((data/'gseapy-role-coverage.json').read_bytes());assert roles['status']=='passed-six-GSEApy-primary-role-reviews' and roles['applied'] and (roles['prior_reviews'],roles['unchanged_prior_reviews'],roles['refined_existing_reviews'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'])==(988,987,1,5,993,344)
    oldraw=gzip.decompress((data/'gseapy-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw);assert len(old['reviews'])==988 and byte_pin(oldraw)['sha256']==roles['before_reviews_sha256'] and pin(data/'gseapy-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'gseapy-prior-papers.json.gz')==roles['prior_papers_archive'];proof=json.loads((data/'scfates-catalog-validation.json').read_bytes());assert pin(data/'scfates-catalog-support.zip')==proof['archive'];files=unpack(data/'scfates-catalog-support.zip',proof);assert files['current-source/tools/literature/reviewed-uses.json']==oldraw and files['catalog-snapshot/papers.json']==gzip.decompress((data/'gseapy-prior-papers.json.gz').read_bytes())
    for n,v in proof['source_pins'].items():assert byte_pin(files['current-source/'+n])==v;verify_source(root,data,n,v['sha256'])
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();legacy=gzip.decompress((data/'palantir-prior-reviews.json.gz').read_bytes())
    if raw in [oldraw,legacy]:assert reviews==json.loads(raw)['reviews'];_frozen_988_verifier(root,data,reviews);return
    current=json.loads(raw);draftpath=root/'tools/literature/gseapy-use-reviews.json';draft=json.loads(draftpath.read_bytes());assert pin(draftpath)==roles['draft_pin'] and pin(root/'tools/literature/curate_gseapy_uses.py')['sha256']==roles['curator_sha256'];expected=copy.deepcopy(old);assert draft['status']=='six-primary-bound-GSEApy-role-reviews' and len(draft['reviews'])==6
    for d in draft['reviews']:
        r=d['review'];assert r['tool_id']=='gseapy';matches=[i for i,e in enumerate(expected['reviews']) if (e['pmcid'],e['tool_id'])==(r['pmcid'],r['tool_id'])]
        if matches:assert d['paper_id']=='P062' and len(matches)==1;expected['reviews'][matches[0]]=r
        else:expected['reviews'].append(r)
    assert current==expected and reviews==current['reviews'] and len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==993 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==344 and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert [r for r in old['reviews'] if r['tool_id']!='gseapy']==[r for r in reviews if r['tool_id']!='gseapy'];_frozen_988_verifier(PriorReviewRoot(root,oldraw),data,old['reviews']);verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(oldraw)['sha256']);assert len(load_catalog_registry(root))==438


@contextlib.contextmanager
def current_catalog_review_context():
    saved=frozen.verify_review_extension
    try:
        frozen.verify_review_extension=verify_review_extension
        with frozen.current_catalog_review_context():yield
    finally:frozen.verify_review_extension=saved


def _wrapped(name):
    def metadata(*args):
        with current_catalog_review_context():return getattr(prior,name)(*args)
    return metadata


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata','soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata','cellphonedb_metadata','palantir_metadata','scfates_metadata']:globals()[_name]=_wrapped(_name)


def gseapy_metadata(root,data):
    from gseapy_current_evidence import verify
    p=verify(False);assert p['full_gseapy_adapter_admitted'] and not p['implementation_credit'];verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/GseapyWorkflowCli.java',implementation_scope='Original GSEApy1.0.4/1.1.2/1.1.3/1.1.8 explicit-class local Enrichr, prerank, phenotype/gene-set GSEA, ssGSEA and GSVA on declared data-only float64 JSON and local gene sets. Native Java audits observed enrichment, original-null NES/P/FDR/FWER, complete sample/term families, leading genes and full reports with atomic batch admission. Original preprocessing, permutations/RNG and GSVA kernels remain upstream; native normalized-null sorting requires finite inputs. No historical remote library recovery, two-tailed substitution, inferred paper settings, full-package or whole-paper reproduction. Read GSEAPY_EXECUTION.md.',validation_note='Ninety fresh original workflows pass:18 legacy1.0.4 and24 per newer edition. Complete native replay passes WindowsJava21/25 and LinuxJava21;40420 numeric,111240 identity and5410 TSV states in fresh runs. Eighteen portable contracts,36 scientific input rejections,15 corruptions, seven real late-source/policy/timeout/output/live-cancellation failures and the exact six-gene example pass. All earlier failed attempts remain retained and unadmitted. Original ssGSEA SymRank no-op, legacy signed phenotype curves, zero-permutation placeholders, Poisson clipping and nonfinite diagnostics remain explicit. Six primary roles preserve remote/local distinctions, comparison-specific backgrounds and unresolved methods; P098 reports a two-tailed test, whereas original local stats uses a one-sided upper tail.',source_roles_reviewed=6,source_role_coverage='gseapy-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.GseapyWorkflowCli',runtime_setup_cli_class='org.jlinalg.cli.GseapyExecutionSetupCli',native_helpers=['src/main/java/org/jlinalg/enrichment/GseapyStatistics.java','src/main/java/org/jlinalg/enrichment/GseapyRankedStatistics.java','src/main/java/org/jlinalg/cli/GseapyExecutionAudit.java'],analysis_installs_software=False,independent_permutation_generation=False,independent_gsva_refit=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


GSEAPY_WARNING='''## GSEApy: warnings and required actions

Use development GseapyWorkflowCli, separate GseapyExecutionSetupCli and GSEAPY_EXECUTION.md. Require exit0 AND analysis-status.json result_admitted=true. Read WARNINGS.md, native-audit.json, original stdout/stderr, original-error.traceback, manifests and process receipts. Keep all inputs and failed/provisional outputs. The canonical executable is unchanged.

SKIP3: skip dependent analysis when the exact permitted Linux x86_64 Python3.11.17 base, four original namespaces, receipt, storage or installation requirements are unavailable. Export the kit and read SETUP.md. Prepare NEW case-sensitive paths; install only with permitted --allow-install, and obtain uncached exact archives with permitted --allow-download or a verified cache. Otherwise retain the skip. RUN installs nothing. Declare1.0.4/1.1.2/1.1.3/1.1.8 explicitly;1.0.4 has no GSVA. Do not infer a paper edition.

FAIL2: no result from the whole requested batch, including earlier completed cases. Preserve every original error and provisional table, correct the named input/source/numeric/runtime/timeout/capacity cause and retry NEW paths. Never refresh old scientific binding hashes. A successful original worker or an existing CSV alone does not admit a result. Owned-process, wall-time and sampled-output supervision is not hard OS CPU/memory containment.

Supply explicit local libraries, backgrounds, literal IDs, matrices/sample classes, scientific settings and source-change/nonfinite/empty-result policies. Native local Enrichr uses a one-sided upper hypergeometric tail; P098 reports two-tailed enrichment, an unresolved paper-method discrepancy. Historical Enrichr resource/server contents are not reconstructed. P062 does not resolve the GSEA mode and P079 does not resolve gsva versus ssgsea; do not infer those APIs. Keep comparison-specific gene universes and input-selection thresholds separate from enrichment correction families.

Original1.0.4 convenience ssGSEA ignores named options; this adapter binds original classes. All editions retain the fast ssGSEA sum equation and SymRank no-op. Original permuted ssGSEA P/FDR diagnostics carry the source publication warning. Legacy phenotype curves remain raw signed while ES is weighted;1.1.8 weights both. Leading genes follow the original curve. Original Poisson clipping, case conversion and aggregation require declared policies; no identifier repair, epsilon, add-one correction or substitute ranked BH is introduced.

Read source NaN/Inf diagnostics and zero-permutation NES placeholders. Native normalized-null sorting supports finite inputs only; an unsupported family fails the whole batch. Original preprocessing, RNG/permutations and GSVA fitting remain upstream. No independent calibration, full-package, biological or whole-paper reproduction. Cell Ranger remains excluded; the250-paper implementation goal stays active.

'''
GSEAPY_ACTIONS='<section id="gseapy-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>GSEApy: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in GSEAPY_WARNING.strip().split('\n\n')[1:])+'</section>'
