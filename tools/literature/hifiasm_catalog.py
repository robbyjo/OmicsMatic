# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seven hifiasm source roles and one bounded original-assembler integration; predecessor scientific sources stay strict."""
import contextlib,copy,gzip,html,json
from gseapy_catalog import *
import gseapy_catalog as prior
from cellphonedb_catalog import PriorReviewRoot
from setup_deseq2_reference import pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source

_frozen_993_verifier=prior.verify_review_extension


def verify_review_extension(root,data,reviews):
    roles=json.loads((data/'hifiasm-role-coverage.json').read_bytes());assert roles['status']=='passed-seven-hifiasm-primary-role-reviews' and roles['applied'] and (roles['prior_reviews'],roles['unchanged_prior_reviews'],roles['new_reviews'],roles['total_reviews'],roles['supporting_pointers'])==(993,993,7,1000,347);oldraw=gzip.decompress((data/'hifiasm-prior-reviews.json.gz').read_bytes());old=json.loads(oldraw);assert len(old['reviews'])==993 and byte_pin(oldraw)['sha256']==roles['before_reviews_sha256'] and pin(data/'hifiasm-prior-reviews.json.gz')==roles['prior_review_archive'] and pin(data/'hifiasm-prior-papers.json.gz')==roles['prior_papers_archive'];proof=json.loads((data/'gseapy-catalog-validation.json').read_bytes());assert pin(data/'gseapy-catalog-support.zip')==proof['archive'];files=unpack(data/'gseapy-catalog-support.zip',proof);assert files['current-source/tools/literature/reviewed-uses.json']==oldraw and files['catalog-snapshot/papers.json']==gzip.decompress((data/'hifiasm-prior-papers.json.gz').read_bytes())
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value;verify_source(root,data,name,value['sha256'])
    raw=(root/'tools/literature/reviewed-uses.json').read_bytes();historical=[oldraw,gzip.decompress((data/'gseapy-prior-reviews.json.gz').read_bytes()),gzip.decompress((data/'palantir-prior-reviews.json.gz').read_bytes())]
    if raw in historical:assert reviews==json.loads(raw)['reviews'];_frozen_993_verifier(root,data,reviews);return
    draftpath=root/'tools/literature/hifiasm-use-reviews.json';draft=json.loads(draftpath.read_bytes());assert pin(draftpath)==roles['draft_pin'] and pin(root/'tools/literature/curate_hifiasm_uses.py')['sha256']==roles['curator_sha256'] and draft['status']=='seven-primary-bound-hifiasm-role-reviews' and len(draft['reviews'])==7;expected=copy.deepcopy(old);expected['reviews'].extend(row['review'] for row in draft['reviews']);current=json.loads(raw);assert current==expected and reviews==current['reviews'] and len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1000 and sum(len(r.get('supporting_evidence',[])) for r in reviews)==347 and byte_pin(raw)['sha256']==roles['reviews_sha256'];assert current['reviews'][:993]==old['reviews'] and all(r['tool_id']=='hifiasm' for r in current['reviews'][993:]);_frozen_993_verifier(PriorReviewRoot(root,oldraw),data,old['reviews']);verify_source(root,data,'tools/literature/reviewed-uses.json',byte_pin(oldraw)['sha256']);assert len(load_catalog_registry(root))==438


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


for _name in ['current_meme_metadata','current_chipseeker_metadata','monocle_development_metadata','normal_fit_metadata','cicero_metadata','soupx_metadata','dndscv_metadata','smr_metadata','loftee_metadata','biomart_metadata','hail_metadata','leafcutter_metadata','cellphonedb_metadata','palantir_metadata','scfates_metadata','gseapy_metadata']:globals()[_name]=_wrapped(_name)


def hifiasm_metadata(root,data):
    from hifiasm_execution_evidence import verify
    p=verify(False);assert p['full_declared_hifiasm_workflow_admitted'] and not p['implementation_credit'];verify_review_extension(root,data,json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
    return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/HifiasmWorkflowCli.java',implementation_scope='Six immutable original hifiasm source profiles: 0.14.2-r315,0.15.1-r328,0.16.1-r375,0.19.5-r587,0.19.5-r592 and 0.22.0-r689. Declared HiFi/trio/Hi-C/UL/ONT families, explicit threads/Bloom/purge/post-join settings, complete native read and paired-identity admission, streaming YAK2 inspection, every original GFA view and literal FASTA export. Original correction, overlap construction, consensus and phasing execute upstream. Older 0.14.2/0.15.1 Hi-C is excluded after retained crashes. No inferred paper settings, independent consensus/phasing, original Yak Bloom/dirty-runtime reproduction, whole package, genome quality or whole-paper claim. Read HIFIASM.md.',validation_note='Complete original source builds and runtime bindings, separately executed original assembly controls, fresh public assembly graph comparisons, three-JVM native read/GFA/parental checks, source-compatible streaming parental inspection, strict retained-summary consistency and actual skip/failure/cancellation controls are bound by the separate execution proof. Seven primary roles distinguish partial phasing, default versus explicit-l2-u options, trio/hybrid/ONT-only assembly, published-assembly reuse and separate scaffolding/QC. Unreported revisions and settings remain unresolved. Use the documented capacities; native graph memory and original-process resources still limit large genomes.',source_roles_reviewed=7,source_role_coverage='hifiasm-role-coverage.json',project_adapter_available=True,canonical_adapter_available=False,development_candidate=p['artifact']['path'],development_candidate_sha256=p['artifact']['sha256'],development_cli_class='org.jlinalg.cli.HifiasmWorkflowCli',runtime_setup_cli_class='org.jlinalg.cli.HifiasmSetupCli',runtime_binding_cli_class='org.jlinalg.cli.HifiasmRuntimeCli',native_helpers=['src/main/java/org/jlinalg/assembly/HifiasmReads.java','src/main/java/org/jlinalg/assembly/HifiasmParentalKmers.java','src/main/java/org/jlinalg/assembly/HifiasmYak.java','src/main/java/org/jlinalg/assembly/HifiasmGfa.java'],analysis_installs_software=False,independent_consensus=False,independent_phasing=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)


HIFIASM_WARNING='''## hifiasm: warnings and required actions

Use the development HifiasmWorkflowCli and HIFIASM.md. Require exit 0 AND COMPLETED.json analysis_admitted=true for the entire batch. Read WARNINGS.md, error.traceback.txt when present, original stderr, all graph audits and owned-process receipts. Per-case PROVISIONAL files, GFA/FASTA files and upstream exit 0 alone do not admit a result. The canonical executable is unchanged.

SKIP3: skip dependent analysis when permitted Linux x86_64 Java, exact original build/runtime receipt or required helpers are unavailable. Export the bundled original MIT-licensed sources with HifiasmSetupCli. Compile only with permitted allow_install and compatible make/GCC/G++/zlib development files; bind NEW paths with HifiasmRuntimeCli. Otherwise retain the skip. Analysis installs nothing. Cell Ranger remains excluded.

FAIL2: no earlier case in the requested batch is admitted. Preserve original inputs, complete failed/provisional outputs and named nested error details. Correct the input, runtime, source, process or capacity cause and retry NEW paths. Never refresh scientific binding hashes. All earlier output bytes and completed populations are checked before admission. Cancellation includes final verification; source correction/assembly/phasing remain upstream.

Declare an exact edition. The 0.19.5 release is r587; the separate r592 profile binds one immutable official commit, not an identified paper binary. Unreported revision suffixes remain unresolved. Hi-C in 0.14.2-r315/0.15.1-r328 is excluded after retained original crashes; retain SKIP3 or explicitly choose an appropriate separately validated newer edition. Never substitute an edition automatically.

ONT-only 0.22.0-r689 requires genuine FASTQ qualities. FASTA is rejected before upstream's exit 0 error; do not invent experimental qualities. Preserve literal read IDs, U/case/ambiguity and paired observation order. Duplicate/malformed inputs fail instead of being repaired. Parent databases must remain unchanged; streaming inspection does not reproduce Yak Bloom admission, reconstruct window provenance or freeze a dirty build.

Read effective-limits.json. Small-control defaults are unsuitable for WGS without explicit input/storage/heap/original-process resources. Parental inspection holds one bounded bucket, while GFA parsing holds one complete graph. Original hifiasm still needs its own memory. Hard per-process limits plus sampled footprint/owned-process checks do not create a hard aggregate RSS bound. Do not truncate data to conceal a capacity failure.

Default HiFi graphs may be partially phased. Preserve missing-sequence/gap/virtual-read diagnostics and omission ledgers; do not invent bases, sum overlapping graph views as genome sizes or infer raw-length bounds for corrected-read coordinates. RagTag, gap filling, polishing, BUSCO, mapping and Merqury are separate steps. Structural/native checks do not certify consensus accuracy, complete phasing, genome quality or paper reproduction. The 250-paper goal remains active.

'''
HIFIASM_ACTIONS='<section id="hifiasm-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>hifiasm: warnings and required actions</h2>'+''.join('<p>'+html.escape(p)+'</p>' for p in HIFIASM_WARNING.strip().split('\n\n')[1:])+'</section>'
