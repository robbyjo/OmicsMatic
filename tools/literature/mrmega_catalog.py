# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Native MR-MEGA catalog provider with exact prior-review and science bindings."""
import contextlib,copy,gzip,html,json
from sniffles_catalog import *
import sniffles_catalog as prior
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import verify_source

_prior_review_verifier=prior.verify_review_extension


def verify_review_extension(root,data,reviews):
    roles=json.loads((data/'mrmega-role-coverage.json').read_bytes())
    assert roles['status']=='passed-three-MRMEGA-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews'],roles['unchanged_prior_reviews'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'])==(1009,1009,3,1012,355)
    oldraw=gzip.decompress((data/'mrmega-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw)
    assert len(old['reviews'])==1009 and byte_pin(oldraw)['sha256']==roles['before_reviews_sha256']
    assert pin(data/'mrmega-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'mrmega-prior-papers.json.gz')==roles['prior_papers_archive']
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes()
    history=[oldraw,*[gzip.decompress((data/n).read_bytes()) for n in ['sniffles-prior-reviews.json.gz','rsubread-prior-reviews.json.gz','hifiasm-prior-reviews.json.gz','gseapy-prior-reviews.json.gz','palantir-prior-reviews.json.gz']]]
    if raw in history:
        assert reviews==json.loads(raw)['reviews'];_prior_review_verifier(root,data,reviews);return
    path=root/'tools/literature/mrmega-use-reviews.json';draft=json.loads(path.read_bytes())
    assert pin(path)==roles['draft_pin'] and pin(root/'tools/literature/curate_mrmega_uses.py')['sha256']==roles['curator_sha256']
    assert draft['status']=='three-primary-bound-MRMEGA-role-reviews' and {r['paper_id'] for r in draft['reviews']}=={'P072','P105','P162'}
    expected=copy.deepcopy(old)
    for item in draft['reviews']:
        review=item['review'];assert review['tool_id']=='mrmega' and not any((r['pmcid'],r['tool_id'])==(review['pmcid'],'mrmega') for r in expected['reviews']);expected['reviews'].append(review)
    current=json.loads(raw);assert current==expected and reviews==current['reviews']
    assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1012 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==355
    assert reviews[:1009]==old['reviews'] and byte_pin(raw)['sha256']==roles['reviews_sha256']
    corrections=json.loads((data/'mrmega-draft-corrections.json').read_bytes())
    assert pin(data/'mrmega-draft-corrections.json')==roles['corrections_pin']==draft['prior_draft_corrections']
    assert corrections['status']=='corrected-two-unpublished-MRMEGA-draft-count-errors-against-original-XML' and corrections['prior_drafts_preserved'] and corrections['incorrect_drafts_never_applied']
    corrected={r['paper_id']:r['review'] for r in draft['reviews']}
    assert 'six of seven cohorts' in corrected['P072']['implementation_scope_note'] and 'six index variants' in corrected['P162']['use_summary']
    _prior_review_verifier(PriorReviewRoot(root,oldraw),data,old['reviews'])
    verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(oldraw)['sha256'])
    assert len(load_catalog_registry(root))==438


@contextlib.contextmanager
def current_catalog_review_context():
    saved=prior.verify_review_extension
    try:
        prior.verify_review_extension=verify_review_extension
        with prior.current_catalog_review_context():yield
    finally:prior.verify_review_extension=saved


def _wrapped(name):
    def metadata(*args):
        with current_catalog_review_context():return getattr(prior,name)(*args)
    return metadata


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata','soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata','cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata','hifiasm_metadata','rsubread_metadata','sniffles_metadata']:globals()[_name]=_wrapped(_name)


def mrmega_metadata(root,data):
    from mrmega_workflow_evidence import verify
    workflow,_=verify(False);assert workflow['standalone_native_file_workflow_available']
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    built=json.loads((root/'build/literature-review/mrmega-runtime/catalog-root-build21/COMPLETED.json').read_bytes())
    assert built['status']=='PASS_MRMEGA_ROOT_SUPPLEMENT_WITH_UNCHANGED_PARENT_SCIENTIFIC_BYTES'
    routed=json.loads((root/'build/literature-review/mrmega-runtime/catalog-root-validation-2/COMPLETED.json').read_bytes())
    assert routed['status']=='PASS_MRMEGA_ROOT_132_COMPLETE_NATIVE_WORKFLOWS_AND_66_DISPATCH_CONTRACTS' and routed['complete_root_workflow_comparisons']==132
    assert all(pin(root/n)==v for n,v in routed['source_pins'].items()) and all(pin(root/n)==v for n,v in routed['root_artifacts'].items())
    artifact=root/built['published_artifact']['path'];assert pin(artifact)=={k:built['published_artifact'][k] for k in ['bytes','sha256']}
    return dict(implementation_status='native-subset-validated',implementation_source='src/main/java/org/jlinalg/meta/MrMegaWorkflow.java',
        implementation_scope='Native Java MR-MEGA0.2 quantitative/binary GWAS file workflow through mrmega-tools: source-ordered allele alignment, exact float32 frequencies/effects/SE/weights, inferred or supplied ancestry axes, residual-scaled covariance, association/ancestry/residual heterogeneity tests, lnBF and input/output genomic control. Native stable survival probabilities are explicitly named and retain separate original approximation fields in validation. No original MR-MEGA or R installation is needed for analysis. Read MRMEGA_INTEGRATION.md and all warnings. Locus posterior/credible-set construction, CrossMap/METAL/PLINK/SuSiEx, unstated paper inputs/options and whole-paper reproduction remain separate.',
        validation_note='Twenty-two original complete GWAS families passed across two compiler builds and three JVMs:132 complete file flows,4134 full-precision fits,12402 independently R-checked native probabilities,144 actual CLI refusals and six analytic odd-median controls. Root dispatcher validation additionally checks the same native outputs and preserved predecessor help routes. Source odd medians, duplicate overwrites, no_alleles sign flips and observed MDS/bin behavior are retained with explicit warnings; malformed numeric tokens and source-undefined cases fail clearly. Frozen checks inspect integrity and stored numeric relationships; they do not refit current user data, authenticate execution or certify a paper. Canonical executable unchanged.',
        project_adapter_available=True,canonical_adapter_available=False,source_roles_reviewed=3,source_role_coverage='mrmega-role-coverage.json',
        development_candidate=built['published_artifact']['path'],development_candidate_sha256=built['published_artifact']['sha256'],development_cli_class='org.jlinalg.cli.MrMegaWorkflowCli',
        root_cli_class='org.jlinalg.cli.JLinAlgCli',root_command='mrmega-tools',native_workflow_proof='docs/literature/mrmega-workflow-development/VALIDATION.json',
        native_helpers=['src/main/java/org/jlinalg/meta/MrMegaRegression.java','src/main/java/org/jlinalg/meta/MrMegaAncestry.java'],
        analysis_installs_software=False,original_runtime_required=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


MRMEGA_WARNING='''## MR-MEGA: warnings and required actions

Use mrmega-tools with explicit trait, ancestry-axis count, allele, filter and input/output GC settings. Read MRMEGA_INTEGRATION.md and the full native workflow notes. Native analysis requires Java and the recorded project dependency; it installs nothing and needs no original MR-MEGA, compiler or R. Require exit0, run.json PASS_NATIVE_MRMEGA_WITH_WARNINGS and the admitted results.tsv. Read WARNINGS.md, every row/cohort/variant disposition and source SHA-256 bindings before interpretation. Canonical executable unchanged; use the published root supplement on the recorded project classpath.

FAIL2: no result table is admitted. Failed partial files stay under partial/; correct the named file/settings/numeric/capacity/ancestry/rank problem and retry NEW output paths. Existing outputs are refused and preserved. SmallCohortCount variants remain explicit NA rows; do not turn them into nonsignificant tests. Malformed/nonfinite numbers, negative N, zero/negative/underflowing SE, beta sentinel -666, source-undefined singleton medians and singular models are refused rather than coerced to a successful result.

Preserve source0.2 behavior: float32 storage and reciprocal arithmetic; odd-count median index floor(n/2)-1; duplicate overwrite/lambda participation; rejected existing-marker N assignments; first valid coordinate retention; no strand complement/build conversion. no_alleles=true retains a new-marker beta then negates later occurrences because empty EA differs from stored N. Supply actual consistent allele columns unless this original behavior is intended. A missing filter column skips that filter in that cohort with a warning; correct headers when a restriction must apply everywhere. Binary conversion uses the lower OR confidence bound; the required upper bound is unused. Output GC can deflate or inflate association but leaves lnBF/heterogeneity/coefficients unchanged. Original and native coordinate signs remain separate; comparison sign maps do not relabel stored results.

Native stable probability fields are named separately from original subtraction-approximation P fields. Original extreme-tail and zero-test discrepancies remain retained; do not overwrite them with a recalculation. Source-bin300 aliasing is an observed-build compatibility behavior, not portable defined C++ behavior. No genome-build, cohort independence, haplotype or paper-setting inference is performed. Full-paper and historical-binary reproduction remain unproven.

For original-reference regeneration only, SKIP3 means skip dependent validation when download/install/execution is forbidden or the named Linux/compiler/R reference prerequisite is unavailable; supply it only on a permitted host, otherwise leave that reference analysis skipped. Native user analysis has no upstream installation step. Cell Ranger remains excluded by the user. Frozen verification checks source/parent bindings, archived populations and numeric consistency; it does not execute Java/C++/R, independently refit current user data or authenticate historical execution. The broader250-paper goal remains active.

'''
MRMEGA_ACTIONS='<section id="mrmega-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>MR-MEGA: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in MRMEGA_WARNING.strip().split('\n\n')[1:])+'</section>'
