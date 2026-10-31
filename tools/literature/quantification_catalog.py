"""Source-reviewed conversion/quantification roles and explicit unfinished runtime status."""
import json
from catalog_source_integrity import verify_source


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
        return dict(common, implementation_source='src/main/java/org/jlinalg/sequence/RsemAbundance.java',
                    native_helper_source='src/main/java/org/jlinalg/sequence/RsemAbundance.java',
                    native_abundance_API_available=True, public_quantification_CLI_available=False,
                    runtime_admission_available=False, workflow_validation='rsem-native-validation.json',
                    implementation_scope=RSEM_SCOPE, validation_note=RSEM_VALIDATION,
                    coverage_note='Native model arithmetic is validated. The original reference/quantification runtime is privately tested; a public protected adapter is unfinished, so RSEM stays missing.')
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
    'Five private original releases complete synthetic quantification protocols, but protected reference/model/alignment admission, portable setup, result audits, a public command and examples remain unfinished. '
    'RSEM therefore remains missing. Precomputed Xena data and a previously published bulk comparator are distinguished from new quantification. '
    'Read RSEM_DEVELOPMENT.md; no genome/aligner, allele-specific, pRSEM, biological or full-paper equivalence is inferred.')

RSEM_VALIDATION = (
    '22 exact-package tests per Windows Java 21/25 and Linux Java 21, with 668 original-function vectors per JVM, zero skips and strict Java 17/Javadoc. '
    'Five source releases execute 35 original version/reference/quantification commands: 22 complete quantifications and three retained historical zero-alignment failures. '
    'Paired-end, reverse-stranded, single-end and posterior/credibility/read-start settings are exercised. Complete identities, finite output fields and rounded gene/isoform sums are audited. '
    'The oldest build explicitly regenerates three distributed configuration files; all other original source bytes are unchanged and earlier guard/build failures remain retained. '
    'Private runtime evidence is not a portable admission receipt or a public project quantification workflow.')

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
    '<p><strong>Both tools remain missing in the integration count.</strong> The twenty reviewed paper roles retain precomputed data reuse, historical releases, '
    'the unresolved bcl2fastq V3 label and separate downstream analyses. Cell Ranger remains excluded.</p></section>')
