"""Source-validated RepeatMasker development capability and required recovery."""
import hashlib
import json


def repeat_metadata(root, data):
    proof = json.loads((data / 'repeatmasker-validation.json').read_text(encoding='utf-8'))
    roles = json.loads((data / 'repeatmasker-role-coverage.json').read_text(encoding='utf-8'))
    assert proof['status'] == 'passed-scoped-RepeatMasker-public-workflows-and-source-roles'
    assert proof['public_corpus_cases'] == 13 and proof['current_profile_cases'] == 4 and proof['historical_profile_cases'] == 9
    assert proof['native_tests_per_JVM'] == 295 and proof['source_roles_reviewed'] == 11
    assert proof['public_runtime_admission_both_profiles'] and proof['current_package_documented_example_passed']
    assert proof['source_role_reviews_applied'] and not proof['whole_paper_reproduced']
    assert len(roles['roles']) == 11 and roles['unsupported_reported_editions_require_explicit_skip']
    for item in proof['source_evidence']:
        assert hashlib.sha256((root / item['path']).read_bytes()).hexdigest() == item['sha256'], item['path']
    return dict(
        implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/RepeatToolsCli.java',
        implementation_scope=REPEAT_SCOPE,
        validation_note=REPEAT_VALIDATION,
        coverage_note='The pre-expansion inventory lacked this workflow. The current source supplies native annotation reuse and a scoped original RepeatMasker development adapter. Historical editions and full-paper reconstruction remain explicit restrictions.',
        project_adapter_available=True,
        canonical_adapter_available=False,
        runtime_admission_available=True,
        development_adapter_cli='repeat-tools',
        runtime_preparation_cli='repeat-tools --export-runtime-setup NEW_DIRECTORY',
        development_candidate=proof['candidate'],
        development_candidate_sha256=proof['candidate_sha256'],
        original_scientific_candidate=proof['original_scientific_candidate'],
        original_scientific_candidate_sha256=proof['original_scientific_candidate_sha256'],
        current_package_previous_payload_byte_identity=True,
        workflow_validation='repeatmasker-validation.json',
        source_role_coverage='repeatmasker-role-coverage.json',
        source_roles_reviewed=11,
        whole_paper_reproduced=False,
        biological_absence_certified=False,
        native_annotation_reuse_available=True,
        original_search_available=True,
        runtime_profiles=['linux-x86_64-repeatmasker-4.2.6-custom-rmblast', 'linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast'],
        native_helpers=['src/main/java/org/jlinalg/genetics/RepeatAnnotations.java', 'src/main/java/org/jlinalg/cli/RepeatFasta.java', 'src/main/java/org/jlinalg/cli/RepeatStageEvidence.java'])


REPEAT_SCOPE = (
    'Native Java CHECK/MASK/AUDIT for explicit OUT or UCSC repeat annotations and plain/gzip FASTA, plus original two-stage SEARCH through repeat-tools. '
    'Admitted Linux x86_64 profiles cover RepeatMasker 4.2.6 with supplied custom libraries and 4.1.5 with supplied libraries, explicit species/simple-repeat requests and the pinned Dfam 3.7 resource. '
    'Typed recipes preserve explicit scientific flags, sequence/library/resource identities, query-ID/case staging, complete output populations and separate runtime/study completion. '
    'Both stages retain commands, logs, cleanup, complete trees and host captures; verification replays every mapped header/base and checks every annotation and mask value. '
    'Native operations need Java 17+; original programs require an exact admitted Linux runtime, including Linux Java inside WSL on Windows. '
    'Missing/forbidden/unsupported prerequisites skip3; invalid data, changed integrity, execution, capacity or interruption fails2 with retained diagnostics. Analysis never installs or substitutes editions. '
    'Other historical versions, unlicensed RepBase resources, unstated study settings, library construction, downstream classification/annotation pipelines and whole-paper or biological accuracy remain outside this scope. '
    'Read REPEATMASKER_PUBLIC_WORKFLOW.md, REPEATMASKER_SOURCE_REVIEW.md and every run warning before using outputs.'
)

REPEAT_VALIDATION = (
    '295 focused exact-package tests on Windows Java 21/25 and Linux Java 21, strict Java 17 compilation and Javadoc; platform skips are recorded. '
    'Thirteen planned public original controls cover both profiles, N/SOFT/X masks, no-hit absence and an independent repeat run; every complete output and owned tree passes a fresh independent final census. '
    'Both public runtime admissions pass. Fresh mapped Unicode/gzip current and historical searches, the copyable prepare/search/verify/overwrite-refusal example, '
    'a 10,002,721-base control under a 128 MiB heap and a deliberately rehashed false counter rejection pass. '
    'The current package retains every earlier JAR member byte and passes the three-JVM regression matrix and a fresh documented original search. '
    'Eleven source roles distinguish three precomputed-resource reuse cases from eight search/masking uses; historical editions and unstated resource releases are retained. '
    'Require COMPLETED.json, matching manifests and --verify success. Failed attempts remain retained; no release, deployment or full-paper reconstruction is claimed.'
)

REPEAT_WARNING = (
    '**REPEATMASKER: REVIEW RUNTIME, RESOURCES AND COMPLETE OUTPUTS.** Use the source-built `repeat-tools` workflow and read '
    '[the public guide](REPEATMASKER_PUBLIC_WORKFLOW.md) and [source-role limits](REPEATMASKER_SOURCE_REVIEW.md). '
    'Native annotation reuse requires Java 17+; original SEARCH requires an explicitly admitted exact Linux runtime and a NEW output. '
    'Where installation is permitted, export the pinned setup helper, install the declared payload and admit it separately. '
    'If prerequisites are missing, forbidden or unsupported, leave the dependent analysis SKIP3 and supply the named exact dependency or choose a supported profile after review. '
    'For FAIL2, inspect retained diagnostics, correct the named data/integrity/process/capacity cause and rerun to NEW paths. Never repair sealed homes. '
    'Keep exact recipe/input/runtime paths for verification. Review explicit query/assembly/library choices, source-ID mappings and genuine no-hit absences; completion does not certify biological absence or a whole paper. '
    'Unreported Dfam releases and unsupported historical editions are never inferred. RepBase-dependent analysis requires a permitted licensed local resource. Cell Ranger remains excluded.\n\n'
)

REPEAT_ACTIONS = '''<section id="repeat-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere;scroll-margin-top:10rem">
<h2>RepeatMasker: runtime, resource and result checks</h2>
<p><strong>Scoped development workflow available:</strong> use <code>repeat-tools</code> from a current source build. Native annotation reuse needs Java 17+; original SEARCH needs a separately admitted exact Linux runtime. Use Linux Java inside WSL on Windows. Runtime admission alone is not a study result.</p>
<p><strong>Required action:</strong> read the public workflow and source-role guides, declare assembly/library/settings and staging policies, use a NEW output directory, inspect every <code>WARNINGS.md</code> and require <code>COMPLETED.json</code>, matching hashes and successful <code>--verify</code>. Exact external recipe/input/runtime paths must remain available.</p>
<p><strong>SKIP3:</strong> leave the dependent analysis skipped when installation, dependencies, permissions or an edition are unavailable. Where permitted, export the pinned setup helper, install the declared payload and admit a new runtime. RepBase requires a permitted licensed local resource; a paper's default Dfam does not identify a release.</p>
<p><strong>FAIL2:</strong> retain failed/interrupted evidence, inspect the named input, integrity, process or capacity error, correct it and retry to NEW paths. Stabilize changing host dependencies; never repair a sealed payload/runtime or weaken its hash guard.</p>
<p>Current 4.2.6 supplied-library and historical 4.1.5/Dfam 3.7 profiles have explicit tested scopes. Other historical editions, whole-paper reconstruction and biological absence are not certified. A genuine no-hit result retains absent original masked/table outputs. Cell Ranger remains excluded.</p>
</section>'''
