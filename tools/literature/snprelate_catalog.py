# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""SNPRelate/GENESIS registry and exact predecessor metadata views; scientific sources always remain live."""
import gzip
import json
import contextlib
import html
from diamond_catalog import *
import diamond_catalog as prior
from setup_deseq2_reference import ROOT,pin
from catalog_source_integrity import reverse_delta,verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView
_previous_verify=prior.verify_review_extension


def _current_extension(root,data,reviews):
    roles=json.loads((data/'snprelate-role-coverage.json').read_bytes())
    assert roles['status']=='PASS_NINE_CITATION_NORMALIZED_PRIMARY_PACKAGE_ROLES' and roles['applied']
    assert (roles['prior_reviews'],roles['new_reviews'],roles['total_reviews'],roles['observations'],roles['supporting_pointers'])==(1029,9,1038,2475,369)
    for name,key in [('snprelate-prior-reviews.json.gz','prior_review_archive'),('snprelate-prior-papers.json.gz','prior_papers_archive')]:assert pin(data/name)==roles[key]
    oldraw=gzip.decompress((data/'snprelate-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw)['reviews']
    draft=json.loads((data/'snprelate-applied-role-reviews.json').read_bytes())
    assert pin(data/'snprelate-applied-role-reviews.json')==roles['applied_draft_pin']
    assert pin(root/'tools/literature/apply_snprelate_catalog.py')==roles['curator_pin']
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes()
    assert reviews==json.loads(raw)['reviews']==old+draft['reviews'] and len(old)==1029 and len(draft['reviews'])==9
    assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1038
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews)==369
    assert {r['tool_id'] for r in draft['reviews']}=={'snprelate','genesis'}
    assert pin(root/'tools/literature/reviewed-uses.json')==roles['current_reviews']
    verify_source(root,data,'tools/literature/reviewed-uses.json',roles['prior_reviews_sha256'])
    return oldraw


def verify_review_extension(root,data,reviews):
    roles=data/'snprelate-role-coverage.json'
    if not roles.exists() or pin(root/'tools/literature/reviewed-uses.json')!=json.loads(roles.read_bytes())['current_reviews']:
        return _previous_verify(predecessor_view(root,data,restore_catalog=False),data,reviews)
    oldraw=_current_extension(root,data,reviews)
    _previous_verify(predecessor_view(root,data),data,json.loads(oldraw)['reviews'])


def predecessor_view(root,data,restore_catalog=True):
    overrides={};name='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    descriptor=data/'snprelate-dispatch-shared-source-compatibility.json'
    if descriptor.exists():
        row=json.loads(descriptor.read_bytes())['paths'][0];assert row['path']==name and len(row['edits'])==2
        raw=(root/name).read_bytes()
        before=raw if prior.byte_pin(raw)['sha256']==row['before_sha256'] else reverse_delta(raw,row)
        verify_source(root,data,name,row['before_sha256']);overrides[name]=before
    archive=data/'snprelate-prior-reviews.json.gz'
    if restore_catalog and archive.exists():
        overrides['tools/literature/reviewed-uses.json']=gzip.decompress(archive.read_bytes())
        overrides['docs/literature/omics-2023-2026/papers.json']=gzip.decompress((data/'snprelate-prior-papers.json.gz').read_bytes())
    return ArchivedCatalogView(root,overrides)


def load_catalog_registry(root):
    data=root/'docs/literature/omics-2023-2026'
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    old=prior.load_catalog_registry(predecessor_view(root,data))
    extra=json.loads((root/'tools/literature/snprelate-named-software-registry.json').read_bytes())
    assert len(old)==438 and {r['id'] for r in extra}=={'snprelate','genesis'} and not ({r['id'] for r in old}&{r['id'] for r in extra})
    result=old+extra;assert len(result)==len({r['id'] for r in result})==440;return result


@contextlib.contextmanager
def current_catalog_review_context():
    saved=prior.verify_review_extension
    try:
        prior.verify_review_extension=verify_review_extension
        with prior.current_catalog_review_context():yield
    finally:prior.verify_review_extension=saved


def _wrapped(name):
    def metadata(root,data,*args,**kwargs):return getattr(prior,name)(predecessor_view(root,data),data,*args,**kwargs)
    return metadata


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata',
    'soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata',
    'cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata','hifiasm_metadata','rsubread_metadata',
    'sniffles_metadata','mrmega_metadata','cistarget_metadata','cellchat_metadata','diamond_metadata']:
    globals()[_name]=_wrapped(_name)


def snprelate_metadata(root,data):
    _current_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    from verify_snprelate_tools_evidence import verify
    proof,_=verify()
    artifact=root/'docs/literature/snprelate-tools-development/snprelate-root-javac21.jar'
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/SnprelateToolsCli.java',
        coverage_note='Scoped original SNPRelate1.46.0 KING-robust, LD pruning, GRM and exact/randomized PCA; native full genotype/QC/identity auditing. Four papers are reviewed; historical settings remain explicit.',
        implementation_scope='Typed snprelate-tools on an explicitly pinned private Windows R4.6.1 runtime. Complete variant-major diploid biallelic hard calls, numeric chromosome/position order and literal FID/IID/variant sidecars. Explicit GDS layout, QC, family context, LD estimator/window/start/seed, six GRM methods or exact/randomized PCA options. Native Java independently checks complete genotype transport, SNPRelate1.46 scalar QC and KING coefficients. LD/GRM/PCA execute unchanged upstream; saved verification checks full output contracts and PCA invariants without independently refitting those estimators. Explicit setup/profile preparation is separate; analysis installs nothing. Java-only saved verification works on Windows and Linux. Read SNPRELATE_INTEGRATION.md.',
        validation_note='320 complete source-case dispositions:186 preceding KING/LD/GRM successes,54 empty-QC preflight refusals and80 successful original PCA analyses.11520 complete KING/GRM matrix cells and complete source PCA fields/signs/placeholders are compared. Current verifier replays all240 prior bundles480 times on Windows/Linux; PCA has160 saved checks.60 focused JUnit executions,40 resealed scientific/protocol mutation refusals, four helper/skip/preservation checks and33 root commands include four actual original analyses. Source nonfinite outputs and Weighted-to-EIGMIX reporting alias remain unchanged. Raw DSPEVX uncomputed NaNs, randomized projection zero padding, seed state and source signs remain visible. No historical paper reproduction, independent LD/GRM/PCA refit, producer authentication, biological accuracy or production-cohort performance claim.',
        project_adapter_available=True,canonical_adapter_available=False,source_roles_reviewed=4,source_role_coverage='snprelate-role-coverage.json',
        development_candidate=artifact.relative_to(ROOT).as_posix(),development_candidate_sha256=pin(artifact)['sha256'],
        development_cli_class='org.jlinalg.cli.SnprelateToolsCli',root_cli_class='org.jlinalg.cli.JLinAlgCli',root_command='snprelate-tools',
        workflow_proof='docs/literature/snprelate-tools-development/VALIDATION.json',original_runtime_required=True,analysis_installs_software=False,
        supported_source_versions=['1.46.0'],whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


SNPRELATE_WARNING='''## SNPRelate: warnings and required actions

Use snprelate-tools from the standalone development JAR, or its root supplement with the separately pinned canonical dependency. Read SNPRELATE_INTEGRATION.md. Require exit0, result_admitted=true and a successful complete --verify. Review WARNINGS.md and downstream_eligible before using results. The released0.3.7 executable has no new command by itself.

SKIP3: missing, forbidden or unsupported prerequisites mean skip the dependent original analysis. Required action: supply the exact private Windows R4.6.1/SNPRelate1.46.0/gdsfmt1.48.2/RhpcBLASctl0.23-42 runtime and create its closed file profile using the exported helper. Download/install only where allowed; otherwise keep analysis skipped. Linux can verify saved bundles but has no admitted original-analysis runtime profile. Analysis never installs software.

FAIL2: admit no result. Preserve the attempt, inspect its input, runtime, process, capacity or scientific discrepancy, correct the stated cause and retry NEW paths. Do not sort, drop variants, rename IDs, fill genotypes, replace source nonfinite values, clamp eigenvalues, change seeds/methods or switch versions to suppress failure. Empty QC populations are refused even where original sample-first source returns a NaN matrix. Duplicate original variant IDs and unassessable source values make downstream_eligible=false.

Declare physical GDS layout and every scientific choice. Sample-first missingness uses1-called/N; SNP-first uses missing/N. Explicit QC bypass can retain monomorphic/all-missing variants. LD threshold is absolute; estimator, window and start/seed matter. Weighted is reported as EIGMIX upstream. Exact PCA preserves all n source eigenvalues and DSPEVX uncomputed NaNs. Randomized PCA preserves source projection rank/zero padding; it is not an exact eigen decomposition. Norm/orthogonality/eigen residual checks are invariants, not independent fitting. Logical capacities are not hard OS quotas; Windows system DLLs outside the pinned R distribution are not closed independently.

The citation-normalized census corrects the earlier three-paper SNPRelate list: P130,P170,P191,P216. P130 reports1.30.1 in a collective package list;1.46.0 does not reproduce it. P170 names SNPRelate PCA after separate PLINK pruning without version/function settings. P191 uses SNPRelate KING before separate GENESIS PC-AiR. P216 names snpgdsLDpruning; downstream PLINK PCA stays separate. Require exact paper settings before claiming reproduction. GENESIS remains missing despite private runtime setup: ordinary PCA and SNPRelate KING do not implement PC-AiR, PC-Relate or relatedness-adjusted models. Cell Ranger remains excluded and the full250-paper goal remains active.

'''
SNPRELATE_ACTIONS='<section id="snprelate-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>SNPRelate: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in SNPRELATE_WARNING.strip().split('\n\n')[1:])+'</section>'
