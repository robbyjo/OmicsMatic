# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact 917+1 role context and two scoped integration updates; no frozen scientific edits."""
import contextlib,gzip,hashlib,json
import monocle_catalog_context as prior
from catalog_source_integrity import verify_source
from meme_discovery_provider_compatibility import verify_provider_derivation,validate_review_extension,BASELINE_REVIEWS_SHA

sha=lambda raw:hashlib.sha256(raw).hexdigest()


def load_catalog_registry(root):
    base=prior.load_catalog_registry(root);extra=json.loads((root/'tools/literature/fitdistrplus-named-software-registry.json').read_bytes())
    assert len(base)==427 and len(extra)==1 and extra[0]['id']=='fitdistrplus' and extra[0]['pattern']==r'\bfitdistrplus\b'
    assert len({r['id'] for r in base+extra})==428;return base+extra


def verify_review_extension(root,data,reviews):
    verify_provider_derivation(root)
    roles=json.loads((data/'fitdistrplus-role-coverage.json').read_bytes());assert roles['status']=='passed-one-fitdistrplus-primary-role-review' and roles['applied']
    assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['total_observations'],roles['supporting_pointers'])==(917,1,918,2450,309)
    packed=(data/'fitdistrplus-prior-reviews.json.gz').read_bytes();assert dict(bytes=len(packed),sha256=sha(packed))==roles['prior_review_archive'];before=gzip.decompress(packed);old=json.loads(before)
    mono=json.loads((data/'monocle-role-coverage.json').read_bytes());older_raw=gzip.decompress((data/'monocle-prior-reviews.json.gz').read_bytes());older=json.loads(older_raw)
    assert sha(before)==mono['reviews_sha256']==roles['before_reviews_sha256'] and sha(older_raw)==mono['before_reviews_sha256']==prior.PRIOR_REVIEWS_SHA
    drafts_raw=(root/'tools/literature/monocle-catalog-use-reviews.json').read_bytes();drafts=json.loads(drafts_raw)
    assert sha(drafts_raw)==mono['draft_sha256'] and sha((root/'tools/literature/curate_monocle_catalog_uses.py').read_bytes())==mono['curator_sha256']
    assert old['reviews'][:905]==older['reviews'] and old['reviews'][905:]==[d['review'] for d in drafts] and len(old['reviews'])==917
    chip=json.loads((data/'chipseeker-role-coverage.json').read_bytes());chip_prior=gzip.decompress((data/'chipseeker-prior-reviews.json.gz').read_bytes());chip_drafts=(root/'tools/literature/chipseeker-use-reviews.json').read_bytes()
    assert sha(older_raw)==chip['reviews_sha256'] and sha(chip_prior)==chip['before_reviews_sha256']==BASELINE_REVIEWS_SHA
    assert sha(chip_drafts)==chip['draft_sha256'] and sha((root/'tools/literature/curate_chipseeker_uses.py').read_bytes())==chip['curator_sha256']
    assert validate_review_extension(chip_prior,older_raw,json.loads(chip_drafts))==older['reviews']
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();current=json.loads(raw);draft_raw=(root/'tools/literature/fitdistrplus-use-review.json').read_bytes();draft=json.loads(draft_raw)
    assert sha(raw)==roles['reviews_sha256'] and sha(draft_raw)==roles['draft_sha256'] and sha((root/'tools/literature/curate_fitdistrplus_use.py').read_bytes())==roles['curator_sha256']
    assert {k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'}
    assert current['reviews'][:917]==old['reviews'] and current['reviews'][917:]==[draft['review']] and reviews==current['reviews']
    assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==918 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==309
    for expected in [mono['reviews_sha256'],prior.PRIOR_REVIEWS_SHA,BASELINE_REVIEWS_SHA]:verify_source(root,data,'tools/literature/reviewed-uses.json',expected)
    assert sha((root/'tools/literature/fitdistrplus-named-software-registry.json').read_bytes())==roles['registry_extension']['sha256'];load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    import chipseeker_catalog,meme_discovery_current_catalog
    targets=[(prior,'verify_current_review_extension'),(chipseeker_catalog,'verify_meme_review_extension'),(meme_discovery_current_catalog,'verify_meme_review_extension')];saved=[getattr(module,name) for module,name in targets]
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
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']);p=json.loads((root/'docs/literature/normal-fit-development/VALIDATION.json').read_bytes());assert p['component_admitted'] and p['tests_per_JVM']==86 and p['packaged_protocols']==51
    return dict(implementation_status='native-subset-validated',implementation_source='src/main/java/org/jlinalg/distributional/NormalFit.java',
        implementation_scope='Native unweighted analytical normal-mle from complete finite observations, with population variance, log likelihood/AIC/BIC and optional explicitly selected normal tails/ordinary BH over every supplied score. No row filtering, missing omission, automatic shuffle, optimizer/Hessian/covariance, weighted/censored fitting, other distributions or inferred paper method/version. Original numerical failures and all-zero SD0.1 return remain preserved as differences. Read NORMAL_FIT_WORKFLOWS.md.',
        validation_note='43 original fitting populations/86 MLE-MME calls and36 tail/BH families;86 checks per JVM on Windows Java21/25 and Linux Java21,51 packaged protocols plus an executed example. Complete fit/test populations and original warnings retained. Native analysis needs Java only; FAIL2 admits no result and retains diagnostics. Analytical MLE is not original optimizer replay or biological calibration.',
        source_roles_reviewed=1,source_role_coverage='fitdistrplus-role-coverage.json',development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.NormalFitToolsCli',canonical_adapter_available=False,analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)


def cicero_metadata(root,data):
    fit=json.loads((root/'docs/literature/cicero-coaccessibility-development/VALIDATION.json').read_bytes());subset=json.loads((root/'docs/literature/cicero-prediction-development/VALIDATION.json').read_bytes());assert fit['adapter_admitted'] and subset['workflow_admitted'] and subset['independent_membership_decisions']==43317
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/CiceroWorkflowCli.java',
        implementation_scope='Separate development original Cicero-Monocle3 1.3.9 co-accessibility workflow from explicit raw counts, prepared coordinates and genome lengths: aggregation, distance calibration, complete glasso models/connections and native axes/counts/nearest/covariance/penalty/reconciliation audits. Java-only supplied-prediction reuse applies explicit coordinate/overlap/endpoint choices and accessibility membership without a gene-expression filter or score repair. Original NA, window dispositions and WI asymmetry remain visible. No historical paper environment, author-specific shuffle/filter direction, TSS annotation, CCAN, gene activity or biological calibration is inferred. Read CICERO_COACCESSIBILITY_DEVELOPMENT.md and CICERO_PREDICTION_SUBSET_DEVELOPMENT.md.',
        validation_note='19,548 complete connection rows including38 retained NA,18 original-runtime protocols,14 semantic refusals and516 checks on each of three JVMs; prediction reuse has43,317 independent memberships,57 runtime protocols and seven contracts per JVM. Fitting requires the exact permitted Windows/R profile; unavailable dependencies SKIP3, any input/source/audit/capacity failure FAIL2 rejects the atomic batch. Prediction subsetting needs Java only and preserves opaque scores.',
        source_roles_reviewed=2,source_role_coverage='monocle-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=fit['artifact']['path'],development_candidate_sha256=fit['artifact']['sha256'],prediction_development_candidate=subset['artifact']['path'],prediction_development_sha256=subset['artifact']['sha256'],analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)

NORMAL_FIT_WARNING='''## fitdistrplus normal-fitting subset: warnings and required actions

Use the separate native development class NormalFitToolsCli and NORMAL_FIT_WORKFLOWS.md. Declare normal-mle explicitly; P043 does not identify its function, estimator, version or shuffle. Java needs no R runtime and installs nothing. Analytical MLE differs from the original numerical optimizer: tiny/near-constant original failures and all-zero SD0.1 return remain retained; native zero variance fails.

**FAIL2 admits no result, including an earlier fit if testing/writing later fails.** Preserve WARNINGS.md, status, captured inputs and unadmitted-partial-results; correct the named input, representability, capacity or heap cause and retry NEW paths. Do not omit NA/NaN/Inf, repair IDs or rescale to force success. Explicit background origin/tail/BH/cutoff choices do not establish normality, independence or calibration. All rows remain; no retention direction is inferred. Ordinary p/q underflow remains zero with separate log tails and prominent warnings. Read the original control differences before interpretation.

'''
NORMAL_FIT_ACTIONS='''<section id="normal-fit-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>fitdistrplus normal-fitting subset: warnings and required actions</h2><p><strong>Native analytical normal-mle:</strong> use the separate development class <code>NormalFitToolsCli</code>, <code>NORMAL_FIT_WORKFLOWS.md</code> and its executed example. Java only; no installation. P043 does not identify its function, estimator or version. This is not original numerical-optimizer, Hessian, weighted/censored or full-package equivalence.</p><p><strong>FAIL2:</strong> no result is admitted, including earlier completed outputs. Preserve warnings, status, captured inputs and unadmitted partials; correct the named cause and retry NEW paths. Never omit NA/NaN/Inf values, repair IDs or silently rescale. Native zero variance fails; original tiny/near-constant failures and all-zero SD0.1 return remain preserved.</p><p><strong>Background and precision review:</strong> explicitly declare origin, tail, BH, cutoff and strict/inclusive rule. No shuffle, calibration or retention direction is inferred. All rows remain. Ordinary numeric underflow stays zero with separate log tails and warnings; zero is not proof of a literally zero probability.</p></section>'''
MONOCLE_WARNING=prior.MONOCLE_WARNING.replace('Cicero runtime integration is still missing.','Scoped original fitting and Java-only prediction-reuse development workflows are now validated; read CICERO_COACCESSIBILITY_DEVELOPMENT.md and CICERO_PREDICTION_SUBSET_DEVELOPMENT.md. Author-specific shuffle, filtering interpretation, annotations and historical settings remain separate.').replace('All 905 prior reviews remain unchanged','All 917 prior reviews remain unchanged')
MONOCLE_ACTIONS=prior.MONOCLE_ACTIONS.replace('with runtime integration missing.','with validated separate fitting and Java-only prediction-reuse development workflows; inspect their guides and runtime requirements.').replace('All 905 prior reviews remain unchanged','All 917 prior reviews remain unchanged')
