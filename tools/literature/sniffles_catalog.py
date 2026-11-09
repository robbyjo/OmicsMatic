# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Final Sniffles catalog provider; unavailable until execution proof and six reviews pass.

This module does not mutate the current catalog or grant availability on import.
"""
import contextlib,copy,gzip,html,json
from rsubread_catalog import *
import rsubread_catalog as prior
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import verify_source

_frozen_1003_verifier=prior.verify_review_extension


def verify_review_extension(root,data,reviews):
    roles=json.loads((data/'sniffles-role-coverage.json').read_bytes())
    assert roles['status']=='passed-six-Sniffles-primary-role-reviews' and roles['applied']
    assert (roles['prior_reviews'],roles['unchanged_prior_reviews'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'],roles['checked_method_detail_links'])==(1003,1003,6,1009,353,1)
    oldraw=gzip.decompress((data/'sniffles-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw)
    assert len(old['reviews'])==1003 and byte_pin(oldraw)['sha256']==roles['before_reviews_sha256']
    assert pin(data/'sniffles-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'sniffles-prior-papers.json.gz')==roles['prior_papers_archive']
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes()
    history=[oldraw,*[gzip.decompress((data/name).read_bytes()) for name in ['rsubread-prior-reviews.json.gz','hifiasm-prior-reviews.json.gz','gseapy-prior-reviews.json.gz','palantir-prior-reviews.json.gz']]]
    if raw in history:
        assert reviews==json.loads(raw)['reviews'];_frozen_1003_verifier(root,data,reviews);return
    path=root/'tools/literature/sniffles-use-reviews.json';draft=json.loads(path.read_bytes())
    assert pin(path)==roles['draft_pin'] and pin(root/'tools/literature/curate_sniffles_uses.py')['sha256']==roles['curator_sha256']
    assert draft['status']=='six-primary-bound-Sniffles-role-reviews' and len(draft['reviews'])==6
    assert {item['paper_id'] for item in draft['reviews']}=={'P033','P102','P129','P164','P247','P248'}
    expected=copy.deepcopy(old)
    for item in draft['reviews']:
        review=item['review'];assert review['tool_id']=='sniffles'
        assert not any((r['pmcid'],r['tool_id'])==(review['pmcid'],'sniffles') for r in expected['reviews'])
        expected['reviews'].append(review)
    current=json.loads(raw);assert current==expected and reviews==current['reviews']
    assert len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1009 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==353
    assert reviews[:1003]==old['reviews'] and byte_pin(raw)['sha256']==roles['reviews_sha256']
    p102=next(item['review'] for item in draft['reviews'] if item['paper_id']=='P102')['supporting_evidence'][0]
    assert p102['evidence_role']=='method-details' and p102['paragraph_index']==36 and p102['named_anchor']['paragraph_index']==37
    _frozen_1003_verifier(PriorReviewRoot(root,oldraw),data,old['reviews'])
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


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata','soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata','cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata','hifiasm_metadata','rsubread_metadata']:globals()[_name]=_wrapped(_name)


def sniffles_metadata(root,data):
    from sniffles_execution_evidence import verify
    proof,_=verify(False)
    assert proof['declared_calling_workflow_available'] and not proof['implementation_credit'] and not proof['whole_goal_complete']
    verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/SnifflesWorkflowCli.java',
        implementation_scope='Native complete Sniffles 1/2 VCF data access plus original source SV discovery, named tandem-repeat input, supplied-site genotyping and existing HP/PS read-assignment phasing for exact1.0.7/2.0.2/2.0.7/2.3.2/2.4 editions. Typed per-analysis options, unchanged BAM/CRAM/FASTA capture, complete original pysam scans/new indexes, local matching CRAM reference caches, full original logs/VCFs/INFO/FORMAT/sample values and whole-batch admission. Original calling/clustering/genotyping remains upstream. Legacy event-segment size and modern alignment length are distinct settings. Read SNIFFLES_WORKFLOWS.md. SNF/pickle combining, caller consensus/merging, haplotagging, paper-specific unstated filters and whole-package/paper reproduction are outside this integration.',
        validation_note='Availability requires the final source-bound execution proof, complete direct-original full-record/header comparisons and the current failure/verification controls; development builds or provisional cases alone never grant credit. Both compiler artifacts preserve the frozen native VCF class family. Original source/runtime/data checks surround every upstream process and final admission; Java-only stages retain data/population/capacity checks. Known legacy initial-deletion and DUP-to-INV behavior is retained and warned about; fatal diagnostics with exit0 reject the complete batch. Read the exact final proof and all original warnings. Frozen verification is structural consistency, not independent SV refitting, runtime health, authenticity or biological certification.',
        project_adapter_available=True,canonical_adapter_available=False,source_roles_reviewed=6,source_role_coverage='sniffles-role-coverage.json',
        development_candidate=proof['artifact']['path'],development_candidate_sha256=proof['artifact']['sha256'],
        development_cli_class='org.jlinalg.cli.SnifflesWorkflowCli',runtime_binding_cli_class='org.jlinalg.cli.SnifflesRuntimeCli',
        frozen_verification_cli_class='org.jlinalg.cli.SnifflesVerifyCli',runtime_installation_command='tools/literature/install_sniffles_runtime_linux.py',
        native_helpers=['src/main/java/org/jlinalg/genetics/SnifflesVcf.java'],
        analysis_installs_software=False,independent_SV_calling=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


SNIFFLES_WARNING='''## Sniffles: warnings and required actions

Use the standalone source-bound workflow and read SNIFFLES_WORKFLOWS.md. Require exit0 AND root COMPLETED.json status PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW with analysis_admitted=true for the entire batch. Every case PROVISIONAL receipt is subordinate to that master. A failed, skipped, cancelled or incomplete batch admits no earlier case. Original exit0 alone is insufficient: source2.0.2 can print a fatal annotation error and leave a zero-byte VCF. Preserve complete stdout/stderr, warnings, process ledgers and raw VCFs.

SKIP3: skip dependent calling if installation/execution is forbidden or the exact permitted Linux x86_64 runtime, Python/standard library, original sources, dependencies or binding is unavailable. Windows needs Linux Java in a permitted WSL environment. Where permitted, install the exact private runtime with explicit --allow-download --allow-install into NEW paths, then bind separately. Analysis installs nothing. Do not replace shared Python/libraries or refresh old scientific binding hashes to accept another host/edition. Cell Ranger remains excluded.

FAIL2: none of the batch is admitted. Retain inputs and every original/provisional output, correct the named data, integrity, process, capacity or filesystem error, and retry into NEW paths. CANCEL_REQUESTED requests owned cleanup. Existing output paths are refused. File/process/thread/RSS accounting is sampled; it is not an OS security sandbox or hard aggregate memory reservation. Full source/runtime/data checks surround each original process and final admission; pure Java bookkeeping checks frozen data and capacities.

Preserve source-edition semantics. legacy_minimum_segment_size is only1.0.7 --min_seq_size, an event-segment setting; minimum_alignment_length is only modern --min-alignment-length. They are not equivalent read filters. Wrong-edition settings skip before execution. Omitted/null scalars leave original defaults. Literal sample labels and captured legacy BAM-path labels remain unchanged with explicit mappings. Read-support RE/SUPPORT is not an inferred paper depth field. Phasing consumes existing HP/PS assignments and does not perform WhatsHap haplotagging.

Original1.0.7 omitted an initial deletion in constructed controls and emitted INV for a declared DUP with zero support/0/0 during force genotyping. Inspect original types/support/genotypes; do not fabricate or relabel calls. Empty complete callsets can be legitimate; a fatal zero-byte VCF is failed output. SURVIVOR/Jasmine merging, Iris polishing, bcftools consensus and downstream paper filters remain separate. Unreported commands, boolean filter rules and depth fields remain unresolved. Frozen verification does not execute the caller, check today's runtime, independently refit SVs or certify biological/paper reproduction. The canonical executable remains unchanged and the250-paper goal remains active.

'''
SNIFFLES_ACTIONS='<section id="sniffles-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Sniffles: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in SNIFFLES_WARNING.strip().split('\n\n')[1:])+'</section>'
