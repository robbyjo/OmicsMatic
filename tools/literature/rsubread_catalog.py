# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Six primary Rsubread roles and one original bulk-counting integration; all preceding science remains strict."""
import contextlib,copy,gzip,html,json
from hifiasm_catalog import *
import hifiasm_catalog as prior
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

_frozen_1000_verifier=prior.verify_review_extension


def verify_review_extension(root,data,reviews):
    roles=json.loads((data/'rsubread-role-coverage.json').read_bytes())
    assert roles['status']=='passed-six-Rsubread-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews'],roles['unchanged_prior_reviews'],roles['refined_existing_reviews'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'])==(1000,997,3,3,1003,349)
    oldraw=gzip.decompress((data/'rsubread-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw)
    assert len(old['reviews'])==1000 and byte_pin(oldraw)['sha256']==roles['before_reviews_sha256']
    assert pin(data/'rsubread-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'rsubread-prior-papers.json.gz')==roles['prior_papers_archive']
    proof=json.loads((data/'hifiasm-catalog-validation.json').read_bytes());assert pin(data/'hifiasm-catalog-support.zip')==proof['archive'];files=unpack(data/'hifiasm-catalog-support.zip',proof)
    assert files['current-source/tools/literature/reviewed-uses.json']==oldraw and files['catalog-snapshot/papers.json']==gzip.decompress((data/'rsubread-prior-papers.json.gz').read_bytes())
    for name,value in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==value
        if name=='docs/literature/omics-2023-2026/papers.json':
            assert files['current-source/'+name]==files['catalog-snapshot/papers.json']==gzip.decompress((data/'rsubread-prior-papers.json.gz').read_bytes())
        else:verify_source(root,data,name,value['sha256'])
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes()
    history=[oldraw,*[gzip.decompress((data/name).read_bytes()) for name in ['hifiasm-prior-reviews.json.gz','gseapy-prior-reviews.json.gz','palantir-prior-reviews.json.gz']]]
    if raw in history:assert reviews==json.loads(raw)['reviews'];_frozen_1000_verifier(root,data,reviews);return
    draftpath=root/'tools/literature/rsubread-use-reviews.json';draft=json.loads(draftpath.read_bytes())
    assert pin(draftpath)==roles['draft_pin'] and pin(root/'tools/literature/curate_rsubread_uses.py')['sha256']==roles['curator_sha256']
    assert draft['status']=='six-primary-bound-Rsubread-role-reviews' and len(draft['reviews'])==6;expected=copy.deepcopy(old)
    for item in draft['reviews']:
        review=item['review'];assert review['tool_id']=='rsubread';matches=[i for i,r in enumerate(expected['reviews']) if (r['pmcid'],r['tool_id'])==(review['pmcid'],review['tool_id'])]
        if matches:assert item['paper_id'] in ['P013','P029','P223'] and len(matches)==1;expected['reviews'][matches[0]]=review
        else:assert item['paper_id'] in ['P133','P139','P172'];expected['reviews'].append(review)
    current=json.loads(raw);assert current==expected and reviews==current['reviews']
    assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1003 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==349
    assert byte_pin(raw)['sha256']==roles['reviews_sha256']
    assert [r for r in old['reviews'] if r['tool_id']!='rsubread']==[r for r in reviews if r['tool_id']!='rsubread']
    _frozen_1000_verifier(PriorReviewRoot(root,oldraw),data,old['reviews']);verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(oldraw)['sha256']);assert len(load_catalog_registry(root))==438


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


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata','soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata','cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata','hifiasm_metadata']:globals()[_name]=_wrapped(_name)


def rsubread_metadata(root,data):
    from rsubread_execution_evidence import verify
    p,_=verify(False);assert p['declared_counting_workflow_available'] and not p['implementation_credit']
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/RsubreadWorkflowCli.java',
        implementation_scope='Original Rsubread 2.26.0/R4.6.1 and selected unmodified 2.23.2 commit 666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6/R4.4.1 Windows x86_64 bulk short-read counting. Explicit per-library paired/read-pair/strand settings, GTF/SAF and extra attributes, fractional/stranded/fragment options, complete original RDS/annotation/summary/14-field junction outputs, literal target mappings and atomic batch admission. Java retains named counts and typed value tables and checks chromosome/strand union lengths and full result relationships. Read RSUBREAD_WORKFLOWS.md. Original read assignment and junction discovery/counting remain upstream. Long reads, single-cell/UMI, alignment, read-group expansion, per-read reports, double-GeneID interpretation and whole-package/paper reproduction are not exposed.',
        validation_note='Both JDK21/25 compiler artifacts independently pass 58 complete separate-original count-list comparisons: 50 WindowsJVM21 and eight WindowsJVM25 per compiler. Portable frozen verification passes Windows25/Linux21; actual Linux binding/counting skips. Real forbidden/missing/mutated/shadow-import skips, late original/native/capacity failures, cancellation, deeper inconsistent-result rejections and original-process timeout/output/interruption controls remain retained. Beginner private GPL package/source export, data export, bind/count/verify examples pass. All14 prior value classes and R resources remain unchanged; four new CLI compiler-bytecode differences are recorded. Six primary roles preserve explicit versus unreported settings, independent downstream methods and the unknown paper binary among22 commits reporting2.23.2. The P133 author ATAC call leaves checkFragLength/countReadPairs unspecified; maxFragLength alone does not enable filtering. Original input parsing/identifier coercion and complete R object serialization are not independently reproduced in Java. Read warnings and original objects; no scientific binding hash is refreshed.',
        source_roles_reviewed=6,source_role_coverage='rsubread-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,
        development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.RsubreadWorkflowCli',
        runtime_binding_cli_class='org.jlinalg.cli.RsubreadRuntimeCli',runtime_export_command='python tools/literature/export_rsubread_runtime_package.py',
        native_helpers=['src/main/java/org/jlinalg/sequence/RsubreadCountMatrix.java','src/main/java/org/jlinalg/sequence/RsubreadDataFrame.java','src/main/java/org/jlinalg/sequence/RsubreadCountResult.java'],
        analysis_installs_software=False,independent_read_counting=False,independent_junction_counting=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


RSUBREAD_WARNING='''## Rsubread: warnings and required actions

Use the standalone development RsubreadWorkflowCli and RSUBREAD_WORKFLOWS.md. Require exit 0 AND root COMPLETED.json analysis_admitted=true for the entire batch. Read WARNINGS.md, FINAL-ERROR.txt when present, original stdout/stderr, original-error.txt, case-info.json, full original RDS objects and process receipts. PROVISIONAL files, pending receipts and upstream exit 0 alone do not admit a result. The canonical executable is unchanged.

SKIP3: skip dependent analysis when the exact permitted Windows x86_64 R/private-package/runtime binding is unavailable or execution/installation is forbidden. Export the original GPL package and complete corresponding source to NEW paths with export_rsubread_runtime_package.py and explicit --allow-install only when permitted. Provide the matching clean original R home and bind NEW paths with RsubreadRuntimeCli. Analysis installs nothing. These original runtime profiles do not run on Linux; native frozen VERIFY is portable. Cell Ranger remains excluded.

FAIL2: no earlier case in the batch is admitted. Preserve raw inputs, all original objects and complete failed/provisional output. Correct the named input, source, runtime, capacity or filesystem cause and retry NEW paths. Never refresh known hashes, automatically substitute an edition, silently change units/IDs or truncate a result. Mutable packages and shadow imports are rejected before R starts. Cancellation includes final verification; owned-process cleanup and sampled output checks are not hard OS memory/disk containment.

Declare each library's paired_end, count_read_pairs and strand. Original Rsubread ignores countReadPairs for single-end inputs; retain requested and effective declarations. Fragment thresholds are inactive when checkFragLength=false. Original source requires requireBothEndsMapped=true when length checking is enabled; inconsistent options fail instead of being repaired. P133's linked ATAC call leaves checkFragLength and countReadPairs unspecified. Its later sample rename is separate. Preserve original duplicate-basename full-path targets and the explicit input mapping.

The 2.23.2 label occurs in22 recovered upstream commits; the selected original source retains development warnings and does not identify the paper commit/binary. Unreported paper editions/APIs/annotations/flags stay unresolved. P139's generic loading statement is not proof of featureCounts, P172 does not identify exon aggregation, and P223 does not establish a standalone juncCounts function. DESeq2, STAR, peak construction, seqinr and other downstream methods remain separate.

Read all capacities and supply adequate Java AND original R/native memory/storage. Large original objects are produced before native table limits are checked; no hard aggregate RSS reservation is claimed. Original R may coerce numeric/logical-looking IDs and literal NA; preserve raw inputs and RDS rather than repairing them. Double-storage GeneID interpretation is unsupported. Java validates stored intervals/union lengths and complete value relationships, not source-GTF parsing parity, independent read/junction assignment or full R object attributes. VERIFY is frozen structural consistency, not fresh original execution or authentication. No whole-package, biological or whole-paper claim; the250-paper goal remains active.

'''
RSUBREAD_ACTIONS='<section id="rsubread-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Rsubread: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in RSUBREAD_WARNING.strip().split('\n\n')[1:])+'</section>'
