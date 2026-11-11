# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""MTAG catalog metadata; exact predecessor metadata views retain strict live scientific-source checks."""
import contextlib
import gzip
import html
import json
from genesis_catalog import *
import genesis_catalog as prior
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import reverse_delta,verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView

_previous_verify=prior.verify_review_extension


def predecessor_view(root,data,restore_catalog=True):
    overrides={};name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';descriptor=data/'mtag-dispatch-shared-source-compatibility.json'
    if descriptor.exists():
        document=json.loads(descriptor.read_bytes());assert len(document['paths'])==1
        row=document['paths'][0];assert row['path']==name and len(row['edits'])==2
        raw=(root/name).read_bytes();before=raw if byte_pin(raw)['sha256']==row['before_sha256'] else reverse_delta(raw,row)
        verify_source(root,data,name,row['before_sha256']);overrides[name]=before
    if restore_catalog and (data/'mtag-prior-reviews.json.gz').exists():
        overrides['tools/literature/reviewed-uses.json']=gzip.decompress((data/'mtag-prior-reviews.json.gz').read_bytes())
        overrides['docs/literature/omics-2023-2026/papers.json']=gzip.decompress((data/'mtag-prior-papers.json.gz').read_bytes())
    return ArchivedCatalogView(root,overrides)


def _old_context(root,data,restore_catalog=True):
    old=predecessor_view(root,data,restore_catalog)
    # GENESIS binds both its root review file AND data/papers.json. These two
    # views must refer to the same exact pre-MTAG catalog, without changing
    # any predecessor verifier or scientific-source rule.
    return old,old/'docs/literature/omics-2023-2026'


def _current_extension(root,data,reviews):
    coverage=json.loads((data/'mtag-source-role-coverage.json').read_bytes())
    assert coverage['status']=='PASS_FIVE_MTAG_PRIMARY_ROLES_AND_SEPARATE_AUTHOR_EQUATION' and coverage['applied']
    assert (coverage['reviewed_roles'],coverage['supporting_pointers'],coverage['observations'],coverage['prior_reviews_preserved'])==(1043,374,2475,1038)
    for name,key in [('mtag-prior-reviews.json.gz','prior_review_archive'),('mtag-prior-papers.json.gz','prior_papers_archive'),('mtag-applied-reviews.json','applied_reviews')]:assert pin(data/name)==coverage[key]
    assert pin(root/'tools/literature/curate_mtag_uses.py')==coverage['curator_pin']
    before=gzip.decompress((data/'mtag-prior-reviews.json.gz').read_bytes());old=json.loads(before)['reviews'];assert len(old)==1038
    actual=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];assert actual==reviews and actual[:1038]==old and len(actual)==1043
    assert len({(r['pmcid'],r['tool_id']) for r in actual})==1043 and all(r['tool_id']=='mtag' for r in actual[1038:])
    assert sum(len(r.get('supporting_evidence',[])) for r in actual)==374
    updates=json.loads((data/'mtag-applied-reviews.json').read_bytes())['updates'];assert len(updates)==5 and [r['review'] for r in updates]==actual[1038:]
    assert pin(root/'tools/literature/reviewed-uses.json')==coverage['current_reviews'] and pin(data/'papers.json')==coverage['current_papers']
    old_papers=json.loads(gzip.decompress((data/'mtag-prior-papers.json.gz').read_bytes()));papers=json.loads((data/'papers.json').read_bytes());assert len(papers)==len(old_papers)==250
    by={r['paper_id']:r for r in updates};assert sorted(by)==['P077','P088','P090','P112','P115']
    for a,b in zip(old_papers,papers,strict=True):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        if b['paper_id'] in by:
            row=by[b['paper_id']];assert next(t for t in a['tools'] if t['tool_id']=='mtag')==row['before_observation']
            assert next(t for t in b['tools'] if t['tool_id']=='mtag')==row['after_observation']
            assert [t for t in a['tools'] if t['tool_id']!='mtag']==[t for t in b['tools'] if t['tool_id']!='mtag']
        else:assert a==b
    assert sum(len(p['tools']) for p in papers)==2475
    equation=coverage['separate_author_equation'];assert pin(root/equation['path'])=={k:equation[k] for k in ['bytes','sha256']}
    eq=json.loads((root/equation['path']).read_bytes());assert eq['expression']=='n_snp_eff = 1 / (2 * MAF * (1 - MAF) * SE^2)' and eq['formula_is_separate_from_paragraph_text'] and not eq['automatic_beta_se_or_sample_size_substitution']
    dimension=coverage['dimension_evidence'];assert pin(root/dimension['path'])=={k:dimension[k] for k in ['bytes','sha256']}
    verify_source(root,data,'tools/literature/reviewed-uses.json',coverage['reviews_before']['sha256'])
    return before


def verify_review_extension(root,data,reviews):
    coverage=data/'mtag-source-role-coverage.json'
    if not coverage.exists() or pin(root/'tools/literature/reviewed-uses.json')!=json.loads(coverage.read_bytes())['current_reviews']:
        old,old_data=_old_context(root,data,False);return _previous_verify(old,old_data,reviews)
    before=_current_extension(root,data,reviews);old,old_data=_old_context(root,data)
    return _previous_verify(old,old_data,json.loads(before)['reviews'])


def load_catalog_registry(root):
    data=root/'docs/literature/omics-2023-2026';verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    old,old_data=_old_context(root,data);result=prior.load_catalog_registry(old);assert len(result)==len({r['id'] for r in result})==440
    return result


@contextlib.contextmanager
def current_catalog_review_context():
    saved=prior.verify_review_extension
    try:
        prior.verify_review_extension=verify_review_extension
        with prior.current_catalog_review_context():yield
    finally:prior.verify_review_extension=saved


def _wrapped(name):
    def metadata(root,data,*args,**kwargs):
        old,old_data=_old_context(root,data);return getattr(prior,name)(old,old_data,*args,**kwargs)
    return metadata


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata',
              'soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata',
              'cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata','hifiasm_metadata','rsubread_metadata',
              'sniffles_metadata','mrmega_metadata','cistarget_metadata','cellchat_metadata','diamond_metadata','snprelate_metadata','genesis_metadata']:
    globals()[_name]=_wrapped(_name)


def mtag_metadata(root,data):
    _current_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    from verify_mtag_workflow_evidence import inspect
    from verify_mtag_dimensions import inspect as dimensions
    proof,files,summary=inspect();dimension_proof,dimension_files=dimensions()
    artifact=root/'docs/literature/mtag-workflow-development/mtag-root-javac21.jar'
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/MtagToolsCli.java',
        coverage_note='Scoped original MTAG1.0.8 common-SNP workflows with explicit inputs, native effect/tail/identity/diagnostic audits and saved verification. Five primary paper roles reviewed; original input/model/default and version gaps remain explicit.',
        implementation_scope='Typed mtag-tools supervises unchanged original1.0.8 in a private exact Linux x86_64 Python2.7.15/NumPy1.16.4/pandas0.24.2/SciPy1.2.1 environment. Explicit ordered two-to-eight-trait autosomal biallelic SNP TSV/gzip/bzip2 inputs; provided/LDSC residual covariance, provided/GMM/numerical genetic covariance, perfect/equal-h2 constraints and supported maxFDR grid or spike/slab choices. Native Java audits complete parsed identities/coordinates/allele operations, every trait output, full-matrix effect algebra, source SciPy-tail zeros and every diagnostic ledger row. Fitting/full source QC/maxFDR formulas remain upstream. Default freq can bypass internal MAF munging; violating retained SNPs are refused. Ineffective no-flip, broken n_max/custom grids, union meta_format and NPY covariance intake are skipped; no replacement source/model/header/threshold is inferred. Result per-SNP N and source GWAS-equivalent N remain distinct. Read MTAG_INTEGRATION.md.',
        validation_note='25 original option controls:20 admitted workflows,4 expected scientific refusals,1 capability skip;172625 admitted trait-result rows.60 portable option replays,3 admitted root workflows plus1 source error/3 skips,9 root saved replays,24 portable safety/tamper protocols and5 setup refusals. Additional original4-through8-trait workflows include the full default4-trait10-interval maxFDR grid and15 saved replays. Source-bound Cephes tails retain386088 finite comparisons and35853 numerical zeros across3 JVMs. Reused synthetic populations are not independent biological datasets. No independent LDSC/maxFDR fitting, producer authenticity, biological accuracy or whole-paper reconstruction is certified.',
        project_adapter_available=True,canonical_adapter_available=False,source_roles_reviewed=5,source_role_coverage='mtag-source-role-coverage.json',
        development_candidate=artifact.relative_to(ROOT).as_posix(),development_candidate_sha256=pin(artifact)['sha256'],
        development_cli_class='org.jlinalg.cli.MtagToolsCli',root_cli_class='org.jlinalg.cli.JLinAlgCli',root_command='mtag-tools',
        workflow_proof='docs/literature/mtag-workflow-development/VALIDATION.json',dimension_proof='docs/literature/mtag-dimensions-development/VALIDATION.json',
        native_components=['MtagEstimator','MtagScipy121NormalTail','MtagAlgebra'],original_runtime_required=True,analysis_installs_software=False,runtime_setup_export_available=True,
        download_install_available=True,supported_source_versions=['1.0.8'],whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


MTAG_WARNING='''## MTAG: warnings and required actions

Use current-source mtag-tools and MTAG_INTEGRATION.md. The matching root supplement requires its separately pinned canonical0.3.7 dependency; the released executable alone has no new command. Analysis uses Linux Java, including WSL on Windows. Windows/Linux Java can perform saved verification without Python or refitting.

SKIP3: SKIP dependent analysis when exact prerequisites are missing, forbidden or unavailable. Export --export-runtime-helper NEW_DIRECTORY. Prepare the private original1.0.8/Python2 runtime with Linux Python3.11+; installation/download require explicit --allow-install/--allow-download and NEW permitted paths, with complete --archive-dir archives for offline setup. If installation is forbidden or cannot be completed, keep the analysis skipped. Analysis never installs software. Preserve existing environments; source and runtime identities are checked before and after every original analysis.

FAIL2: NO RESULT ADMITTED for invalid input, integrity, process, capacity or unassessable scientific results, including partial original output. Python2 MTAG can catch a scientific error and return process exit0. Require the wrapper exit0, result_admitted=true, COMPLETED.json and successful complete --verify. Read WARNINGS.md, session.json, scientific-audit.json, every native ledger and original log. Preserve the whole attempt and unadmitted-original outputs, resolve the named cause and retry NEW paths. Do not repair IDs, NaN/Inf, covariance, P values or settings to hide failure. Hashes prove integrity, not producer authenticity.

Original source limitations are explicit. The default freq header can bypass internal MAF filtering; native admission requires each retained SNP to satisfy its declared strict cutoff. Choose an existing author-specified frequency column explicitly; do not silently rename/prefilter. no_allele_flipping=true is ineffective upstream; non-null n_max and custom maxFDR grids hit source errors. These requests are SKIP3, as are the unadmitted union meta_format and NPY covariance modes. Original beta/SE mode is disabled. Original SciPy P=0 underflow is retained; it is not mathematical zero and is not clipped or replaced by the older native estimator tail.

Keep N definitions distinct. Trait output N is the original per-SNP input N; the source GWAS-equivalent maximum N is reported in mtag.log. P115 explicitly uses MTAG-derived effective N downstream in LDSC, while distinguishing case/control and imputation effective sizes. Choose and label the intended downstream value. P088's per-SNP SE/MAF equation has a separately reviewed primary-source receipt; the adjacent paragraph alone does not contain its formula, and no beta/SE input mode is automatically enabled.

Declare complete models and capacities. Do not replace the four-trait default10-interval FDR grid with a smaller grid after failure. Its complete unpruned preflight has3268760 points; source pruning follows its original algorithm. Current controls include4-through8 traits with finite original outputs and15 saved replays. Logical capacities are not OS memory quotas; system loader/libraries are not independently pinned. MaxFDR fitting and full source QC remain upstream. Reused synthetic controls do not validate biological assumptions, paper pipelines or historical commits.

Five primary paper roles preserve different trait screens, QC and downstream uses. Three report1.0.8; P112/P115 leave the version unstated. P115 reports input-sensitive findings and heritability inflation; a successful execution is not a biological-validity guarantee. Do not infer missing source commits, trait sets or author commands. Cell Ranger remains excluded; the full250-paper goal and paper-wide semantic review remain open.

'''
MTAG_ACTIONS='<section id="mtag-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>MTAG: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in MTAG_WARNING.strip().split('\n\n')[1:])+'</section>'
