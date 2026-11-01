"""Source-reviewed conversion/quantification roles and explicit unfinished runtime status."""
import json
from catalog_source_integrity import verify_source
from rsem_development_validation import verify_runtime_development


def quantification_metadata(root, data, tool_id):
    proof = json.loads((data / 'rsem-native-validation.json').read_text(encoding='utf-8'))
    roles = json.loads((data / 'quantification-role-coverage.json').read_text(encoding='utf-8'))
    assert proof['status'] == 'passed-native-RSEM-arithmetic-and-original-release-development-protocols'
    assert proof['tests_per_JVM'] == 22 and proof['original_function_vectors_per_JVM'] == 668
    assert proof['original_quantifications_completed'] == 22 and proof['retained_original_zero_alignment_failures'] == 3
    assert not proof['public_quantification_adapter_available'] and not proof['whole_paper_reproduced']
    assert roles['reviews_applied'] and len(roles['roles']) == 20
    for item in proof['source_evidence']:
        verify_source(root, data, item['path'], item['sha256'])
    common = dict(implementation_status='missing', project_adapter_available=False,
                  canonical_adapter_available=False, source_roles_reviewed=10,
                  source_role_coverage='quantification-role-coverage.json', whole_paper_reproduced=False)
    if tool_id == 'rsem':
        runtime = json.loads((root / 'docs/literature/RSEM_RUNTIME_VALIDATION.json').read_text(encoding='utf-8'))
        assert runtime['status'] == 'passed-source-profile-host-binding-and-original-fitting-development'
        assert runtime['tests_per_JVM'] == 153 and runtime['existing_native_science_tests_per_JVM'] == 111
        assert runtime['completed_original_fits'] == 22 and runtime['complete_original_scientific_files'] == 364
        assert runtime['complete_reference_files'] == 30 and runtime['retained_expected_original_failures'] == 3
        assert not runtime['source_to_build_provenance_verified'] and not runtime['scientific_runtime_admitted']
        assert not runtime['public_fitting_workflow_available'] and not runtime['whole_goal_complete']
        verify_runtime_development(root, data, runtime)
        return dict(common, implementation_source='src/main/java/org/jlinalg/sequence/RsemAbundance.java',
                    native_helper_source='src/main/java/org/jlinalg/sequence/RsemAbundance.java',
                    native_abundance_API_available=True, public_quantification_CLI_available=False,
                    native_complete_result_audit_API_available=True, native_alignment_audit_API_available=True,
                    runtime_development_components_available=True,
                    source_build_development_available=True, source_to_build_provenance_verified=True,
                    runtime_admission_available=False, workflow_validation='rsem-native-validation.json',
                    implementation_scope=RSEM_SCOPE, validation_note=RSEM_VALIDATION,
                    coverage_note='Native arithmetic and complete ordinary file/alignment audits are validated. Five exact source builds and their staged original wrappers pass fixed reference/fitting and build-evidence drift controls, with an independently checked metadata/synthetic-result bundle. Public fitting/setup and completed scientific runtime/input admission remain unfinished, so RSEM stays missing.')
    if tool_id == 'bcl2fastq':
        policy = json.loads((data / 'bcl2fastq-runtime-policy.json').read_text(encoding='utf-8'))
        assert policy['analysis_status'] == 'SKIPPED' and not policy['software_downloaded_or_installed']
        return dict(common, implementation_source=None, native_helper_source=None,
                    runtime_admission_available=False, runtime_analysis_status='skipped-license-and-instrument-requirements-unresolved',
                    workflow_validation='bcl2fastq-runtime-policy.json', implementation_scope=BCL_SCOPE,
                    validation_note='Ten original paper roles are reviewed. No software download, installation, runtime execution or native conversion implementation is claimed.',
                    coverage_note='The separate Illumina license and instrument/data requirements are unresolved. Runtime analysis is skipped and this tool remains missing.')
    raise ValueError('Unexpected conversion/quantification tool: ' + tool_id)


RSEM_SCOPE = (
    'Native Java effective lengths, mappability/theta polishing, TPM/FPKM and ordinary EM gene aggregation are available through RsemAbundance. '
    'Exact transcript identities, complete populations, noise, poly-A tails, minimum effective length and zero-expression fallback are preserved. '
    'Release selection retains the older no-alignable-read failure; the 1.3.2 fix is not substituted for 1.2.28/1.3.0/1.3.1. '
    'These methods consume unrounded model quantities; they do not fit EM/Gibbs, recover model parameters from rounded tables, or produce posterior intervals. '
    'Native Java readers now audit complete ordinary reference/count/model/theta/result files and canonical SAM alignments, with a runnable Java result-audit example. '
    'Five exact original source builds and staged wrappers reproduce fixed reference/fitting controls with complete compiler-prefix snapshots, source/build receipt links and build-evidence drift checks. Earlier failed attempts and the earlier source snapshot remain retained. Persistent protected scientific runtime/input admission, public portable setup and a public fitting command/examples remain unfinished. '
    'RSEM therefore remains missing. Precomputed Xena data and a previously published bulk comparator are distinguished from new quantification. '
    'Read RSEM_DEVELOPMENT.md; no genome/aligner, allele-specific, pRSEM, biological or full-paper equivalence is inferred.')

RSEM_VALIDATION = (
    'The baseline 22 arithmetic tests retain 668 original-function vectors per JVM. The current exact-package suite has 171 tests: Windows Java 21/25 pass 154 with seventeen explicit Linux-only skips; Linux Java 21 passes all 171. All 111 existing native science tests pass on every profile, with strict Java 17/Javadoc. '
    'Five exact source builds execute 35 original version/reference/quantification commands: 22 complete quantifications and three retained historical zero-alignment failures. '
    'Paired-end, reverse-stranded, single-end and posterior/credibility/read-start settings are exercised. All 364 original scientific files and 30 reference files match byte for byte, with independent complete native result audits. Six dependency/build-evidence change controls pass for every release. '
    'The oldest build explicitly regenerates three distributed configuration files; all other original source bytes are unchanged and earlier guard/build failures remain retained. '
    'An independent bundle verifier checks original fixed output populations, source/build receipt links and every staged program hash against its compiled-source graph. Source setup is internal and tested on the declared Linux host; public installation/fitting admission remains unfinished. Private runtime evidence is not a portable admission receipt or a public project quantification workflow.')

BCL_SCOPE = (
    'Runtime analysis is SKIPPED while the separate Illumina license and instrument/data requirements are unresolved. '
    'No bcl2fastq software was downloaded, installed or executed, and no native converter is claimed. '
    'Required action: obtain a permitted installation, confirm applicable license and instrument/data eligibility, and supply the exact release, run folder and sample sheet before runtime admission. '
    'General download/install authorization and the earlier FIMO research-use answer do not establish these separate requirements. '
    'The ten reviewed uses retain reported releases, the unresolved literal V3 label, mismatch allowances and unstated index/cycle policies. '
    'Instrument base calling, FASTQ conversion, barcode demultiplexing and later cell/hashtag processing remain distinct. '
    'The tool remains missing; Cell Ranger remains excluded. Read BCL2FASTQ_RUNTIME_STATUS.md.')

QUANT_WARNING = (
    '**BCL2FASTQ RUNTIME ANALYSIS IS SKIPPED:** no software was downloaded, installed or executed. '
    'Resolve the separate vendor license and instrument/data eligibility, then supply a permitted exact runtime, run folder and sample sheet before admission. '
    'General installation permission and the FIMO research-use answer do not establish these requirements. Read BCL2FASTQ_RUNTIME_STATUS.md.\n\n'
    '**RSEM QUANTIFICATION ADAPTER IS UNFINISHED:** native arithmetic and private original-release protocols are validated, '
    'but the project does not yet provide an admitted RSEM fitting command. Do not use native abundance arithmetic as an EM/Gibbs fitter or reconstruct unrounded model quantities from rounded result tables. '
    'Internal exact source builds now pass fixed original controls; earlier compiler-discovery,temporary-file and changing-host failures remain retained. If a required compiler/runtime cannot be installed or executed, SKIP dependent fitting, retain the named cause and supply a permitted working toolchain/runtime before retrying to NEW paths. '
    'Use the exact historical release and retain its failure behavior. Read RSEM_DEVELOPMENT.md. Both tools remain missing in the integration count.\n\n')

QUANT_ACTIONS = (
    '<section id="quantification-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere;scroll-margin-top:10rem">'
    '<h2>RSEM and bcl2fastq: availability and required actions</h2>'
    '<p><strong>bcl2fastq runtime analysis is skipped.</strong> No software was downloaded, installed or executed. '
    'Before attempting it, obtain a permitted installation, confirm the applicable license and instrument/data eligibility, '
    'and supply the exact release, run folder and sample sheet. General installation permission and the earlier FIMO research-use answer do not establish these separate requirements. '
    'Read the <a href="https://support.illumina.com/content/dam/illumina-support/documents/downloads/software/bcl2fastq/bcl2fastq2-v2-20-eula.pdf">vendor license</a> '
    'and repository file <code>docs/literature/BCL2FASTQ_RUNTIME_STATUS.md</code>.</p>'
    '<p><strong>The public RSEM quantification adapter is unfinished.</strong> Native effective-length/expression/ordinary-EM aggregation methods and private original-release protocols are validated. '
    'They do not fit EM/Gibbs models or establish portable runtime admission. Supply actual unrounded model quantities to the Java API; '
    'do not reconstruct them from two-decimal output tables. Exact release selection retains older no-alignable-read failures. '
    'Read repository file <code>docs/literature/RSEM_DEVELOPMENT.md</code> and the '
    '<a href="https://deweylab.github.io/RSEM/rsem-calculate-expression.html">original quantification instructions</a>.</p>'
    '<p><strong>Internal source-build controls pass; public fitting remains unfinished.</strong> Five exact builds and staged wrappers reproduce fixed controls, with build receipts and all original scientific files checked. '
    'If a required compiler/runtime cannot be installed or executed, <strong>skip dependent fitting</strong>, retain the named cause and supply a permitted working toolchain/runtime before retrying to NEW paths. '
    'Earlier failed attempts remain retained. Read repository file <code>docs/literature/RSEM_RUNTIME_DEVELOPMENT.md</code>.</p>'
    '<p><strong>Both tools remain missing in the integration count.</strong> The twenty reviewed paper roles retain precomputed data reuse, historical releases, '
    'the unresolved bcl2fastq V3 label and separate downstream analyses. Cell Ranger remains excluded.</p></section>')
