# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact append-only metadata context; frozen scientific providers and registry remain unchanged."""
import contextlib
import gzip
import hashlib
import json
import re
from catalog_source_integrity import verify_source
from meme_discovery_provider_compatibility import (verify_provider_derivation,validate_review_extension,BASELINE_REVIEWS_SHA)

REGISTRY_SHA='8c1a86f28a9dc10a8e9c46a7f906a5be34328f621a19611a66609fa6a8179065'
PRIOR_REVIEWS_SHA='fe5fcfa8b49bf14f4ec83636e877fb56fb3d544a8dd976c1d3602e940fff771f'

MONOCLE_WARNING='''
## Monocle, Cicero and py-monocle: distinct tools and required actions

Nine reviewed Monocle uses distinguish Monocle2 differential tests, Monocle3 trajectories/GLMs, a modified covariate-aware Moran test, protein-activity inputs and platform/wrapper context. Separate validated native prepared-graph, Monocle3 1.4.27 and Monocle2 2.26.0 development workflows are available; the canonical JAR is unchanged. Full reviewed-role coverage remains missing. Read MONOCLE_SOURCE_REVIEW.md, MONOCLE_TOOLS_DEVELOPMENT.md and MONOCLE2_DIFFERENTIAL_DEVELOPMENT.md; inspect the paper's own scope note. Source candidates labeled 1.3.3/1.3.7 are not proof of the paper commits or historical execution.

**SKIP3: skip dependent analysis if the exact runtime/dependencies are unavailable or installation is forbidden.** Monocle2 requires Linux Java with the declared private R4.2.1 profile, including separate HSMMSingleCell1.18.0. Windows users can invoke Linux Java through WSL using Linux paths. Analysis installs nothing. Provision only where permitted; otherwise retain the skip. Do not substitute Monocle generations/versions, treat protein activity or TPM as raw counts, infer roots or silently replace a custom test with stock graph_test.

**FAIL2: no atomic batch result is admitted on any failed original gene, error, timeout, capacity or audit mismatch.** Free-NB and binary/proportion binomialff controls fail in original Monocle2 under this exact VGAM profile. Failed-gene p/q=1 remains failure, not a valid null. Preserve WARNINGS.md, status, original objects and transcripts; correct the named cause and retry NEW paths without dropping genes, imputation or identifier/value repair.

**Cicero is separate co-accessibility software.** P043 reports one failed subclass, shuffled-background fitting and a filtering sentence whose direction must not be silently reversed. P153 subsets published predictions using peak accessibility; it does not establish a new fitting run. Cicero runtime integration is still missing.

**py-monocle has noncommercial community terms.** P024 calls it a wrapper, whereas inspected upstream describes a distinct Python implementation. No py-monocle runtime was installed or analyzed. The implementation decision awaits the user license preference; the restricted source remains in private research intake and is not bundled into the GPL project. Cell Ranger remains excluded. All 905 prior reviews remain unchanged; paper-wide semantic completeness and the implementation goal remain open.

'''

MONOCLE_ACTIONS='''<section id="monocle-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Monocle, Cicero and py-monocle: warnings and required actions</h2>
<p>Nine reviewed Monocle roles distinguish generations, trajectories, gene models, custom tests and protein-activity inputs. Separate validated native, Monocle3 and Monocle2 development workflows are available; full reviewed-role coverage remains missing and the canonical JAR is unchanged. Read <code>MONOCLE_SOURCE_REVIEW.md</code>, <code>MONOCLE_TOOLS_DEVELOPMENT.md</code>, <code>MONOCLE2_DIFFERENTIAL_DEVELOPMENT.md</code> and each paper scope note. Version-matching source candidates do not establish paper commits or historical execution.</p>
<p><strong>SKIP3: skip dependent analysis if exact dependencies are unavailable or installation is forbidden.</strong> Monocle2 requires Linux Java and the declared private R4.2.1 profile with separate HSMMSingleCell1.18.0. Windows users can invoke Linux Java through WSL with Linux paths. Analysis installs nothing; provision only where permitted, otherwise retain the skip. Never substitute generations, infer roots, treat protein activity/TPM as raw counts or replace a modified test with stock graph_test.</p>
<p><strong>FAIL2: no result of a failed atomic batch is admitted.</strong> Retain original errors, failed genes, p/q=1, warnings and objects; they are not valid nulls. Free-NB and binomialff controls fail upstream under the declared profile. Inspect status and WARNINGS.md, correct the named cause and retry NEW paths without dropping genes, imputation or identifier/value repair.</p>
<p><strong>Cicero:</strong> separate co-accessibility software, with runtime integration missing. P043 retains its reported failed subclass and ambiguous filtering direction. P153 reuses predictions with accessibility-only subsetting; it is not evidence of a new fitting run.</p>
<p><strong>py-monocle:</strong> separate Python implementation under noncommercial community terms; P024 calls it a wrapper. No runtime was installed or analyzed, and implementation awaits the user license preference. Restricted source is kept in private intake. Cell Ranger remains excluded. All 905 prior reviews remain unchanged; full 250-paper semantic and implementation work continues.</p></section>'''


def sha(raw):return hashlib.sha256(raw).hexdigest()


def load_catalog_registry(root):
    original=(root/'tools/literature/tool-registry.json').read_bytes();assert sha(original)==REGISTRY_SHA
    base=json.loads(original);extra=json.loads((root/'tools/literature/monocle-named-software-registry.json').read_bytes());assert len(base)==425 and [r['id'] for r in extra]==['cicero','py-monocle']
    assert len({r['id'] for r in base+extra})==427
    for row in extra:
        assert set(row)=={'id','name','pattern','role','current_coverage','existing_source','coverage_note'} and row['current_coverage']=='missing' and row['existing_source'] is None
        assert len(row['pattern'])<1024;re.compile(row['pattern'])
    return base+extra


def verify_current_review_extension(root,data,reviews):
    verify_provider_derivation(root)
    roles=json.loads((data/'monocle-role-coverage.json').read_bytes());assert roles['status']=='passed-twelve-Monocle-related-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews_preserved'],roles['new_reviews'],roles['total_reviews'],roles['total_observations'],roles['supporting_pointers'])==(905,12,917,2449,309)
    archive=(data/'monocle-prior-reviews.json.gz').read_bytes();assert dict(bytes=len(archive),sha256=sha(archive))==roles['prior_review_archive'];prior_raw=gzip.decompress(archive);assert sha(prior_raw)==PRIOR_REVIEWS_SHA==roles['before_reviews_sha256']
    # Recheck the exact older 898 -> 905 derivation, not just the final count.
    chip=json.loads((data/'chipseeker-role-coverage.json').read_bytes());old_archive=(data/'chipseeker-prior-reviews.json.gz').read_bytes();assert dict(bytes=len(old_archive),sha256=sha(old_archive))==chip['prior_review_archive']
    assert sha(prior_raw)==chip['reviews_sha256'] and chip['before_reviews_sha256']==BASELINE_REVIEWS_SHA
    old_drafts_raw=(root/'tools/literature/chipseeker-use-reviews.json').read_bytes();assert sha(old_drafts_raw)==chip['draft_sha256'] and sha((root/'tools/literature/curate_chipseeker_uses.py').read_bytes())==chip['curator_sha256']
    assert validate_review_extension(gzip.decompress(old_archive),prior_raw,json.loads(old_drafts_raw))==json.loads(prior_raw)['reviews']
    draft_raw=(root/'tools/literature/monocle-catalog-use-reviews.json').read_bytes();assert sha(draft_raw)==roles['draft_sha256'] and sha((root/'tools/literature/curate_monocle_catalog_uses.py').read_bytes())==roles['curator_sha256']
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();assert sha(raw)==roles['reviews_sha256'];prior=json.loads(prior_raw);current=json.loads(raw);drafts=json.loads(draft_raw)
    assert {k:v for k,v in prior.items() if k!='reviews'}=={k:v for k,v in current.items() if k!='reviews'}
    assert current['reviews'][:905]==prior['reviews'] and current['reviews'][905:]==[d['review'] for d in drafts]
    assert reviews==current['reviews'] and len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==917
    assert [(r['paper_id'],r['review']['tool_id']) for r in drafts if r['review']['tool_id']!='monocle']==[('P024','py-monocle'),('P043','cicero'),('P153','cicero')]
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews)==309
    verify_source(root,data,'tools/literature/reviewed-uses.json',BASELINE_REVIEWS_SHA);verify_source(root,data,'tools/literature/reviewed-uses.json',PRIOR_REVIEWS_SHA)
    extra_raw=(root/'tools/literature/monocle-named-software-registry.json').read_bytes();assert dict(bytes=len(extra_raw),sha256=sha(extra_raw))==roles['registry_extension'];load_catalog_registry(root)


@contextlib.contextmanager
def current_catalog_review_context():
    import chipseeker_catalog
    import meme_discovery_current_catalog
    targets=[chipseeker_catalog,meme_discovery_current_catalog];saved=[m.verify_meme_review_extension for m in targets]
    try:
        for module in targets:module.verify_meme_review_extension=verify_current_review_extension
        yield
    finally:
        for module,original in zip(targets,saved):module.verify_meme_review_extension=original


def current_meme_metadata(root,data):
    from meme_discovery_current_catalog import meme_discovery_metadata
    with current_catalog_review_context():return meme_discovery_metadata(root,data)


def current_chipseeker_metadata(root,data):
    from chipseeker_catalog import chipseeker_metadata
    with current_catalog_review_context():return chipseeker_metadata(root,data)


def monocle_development_metadata(root,data):
    reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];verify_current_review_extension(root,data,reviews)
    differential=json.loads((root/'docs/literature/monocle2-differential-development/VALIDATION.json').read_bytes());trajectory=json.loads((root/'docs/literature/monocle-tools-development/VALIDATION.json').read_bytes())
    assert differential['adapter_admitted'] and differential['dense_sparse_original_rows']==720 and not differential['implementation_credit']
    return dict(implementation_status='missing',implementation_source=None,role='Monocle2 differential testing and Monocle3 trajectories, gene models or custom tests',
        coverage_note='Validated separate native prepared-graph and original Monocle2/Monocle3 development workflows are available. Complete reviewed-role support remains open; inspect each paper scope and version. Cicero and py-monocle have separate rows.',
        implementation_scope='Native Java prepared-graph ordering, original Monocle3 1.4.27 raw-count trajectories/GLMs/stock Moran and original Monocle2 2.26.0 typed differential testing are separately validated. Canonical JAR is unchanged. Paper-reported 1.3.3/1.3.7 sources are captured candidates, not historical execution controls. Modified covariate-aware Moran tests, unspecified protein-activity workflows, paper roots/embeddings and missing formulas are not inferred. py-monocle is a separate Python implementation; Cicero-M3 is a co-accessibility package. No complete Monocle integration credit is awarded yet.',
        validation_note='Monocle2: 720 dense/sparse original rows plus 36 additional successful-family rows and 12 example rows; 10 contracts on three JVMs and 13 runtime protocols. Retain upstream free-NB/binomial failures and failed-gene p/q=1 as unadmitted. Monocle3 and native ordering have distinct complete evidence. Runtime SKIP3 requires exact permitted dependencies or skipping dependent analysis; FAIL2 rejects complete atomic batches. See MONOCLE2_DIFFERENTIAL_DEVELOPMENT.md and MONOCLE_TOOLS_DEVELOPMENT.md.',
        partial_development_workflows_available=True,canonical_adapter_available=False,source_roles_reviewed=9,source_role_coverage='monocle-role-coverage.json',
        differential_development_candidate=differential['artifact']['path'],differential_development_sha256=differential['artifact']['sha256'],trajectory_development_candidate=trajectory['artifact']['path'],trajectory_development_sha256=trajectory['artifact']['sha256'],
        analysis_installs_software=False,whole_paper_reproduced=False,whole_goal_complete=False)
