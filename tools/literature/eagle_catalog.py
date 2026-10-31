"""Validated Eagle metadata and prominent recovery text for the literature site."""
import hashlib
import json


def eagle_metadata(root, data):
    proof = json.loads((data / 'eagle-validation.json').read_text(encoding='utf-8'))
    assert proof['status'] == 'passed-scoped-Eagle-workflow-and-source-role-validation'
    assert proof['original_corpus_phasing_runs'] == 40 and proof['paired_genotype_calls'] == 692743
    assert proof['native_tests_per_JVM'] == 169 and proof['source_roles_reviewed'] == 11
    assert proof['source_role_reviews_applied'] and not proof['whole_paper_reproduced']
    for entry in proof['evidence']:
        assert hashlib.sha256((root / entry['path']).read_bytes()).hexdigest() == entry['sha256'], entry['path']
    return dict(
        implementation_status='upstream-adapter-validated',
        implementation_source='src/main/java/org/jlinalg/cli/EagleToolsCli.java',
        implementation_scope='Original pinned Eagle 2.4.1 cohort/reference phasing through eagle-tools, with native Java genotype/map/join checks and BCF2.2 conversion. Plain/gzip/BGZF VCF or plain/BGZF BCF2.2 with legacy VCF4.0-4.3 headers; mixed roles and site-only exclusions are explicit. Protected original binaries, optional provenance-only source indexes, complete conversion audits, regenerated validated CSI, matching ledgers, resource limits and full output audits. Native checks run on Windows/Linux; original phasing requires an admitted Linux x86_64 runtime. Read EAGLE_WORKFLOWS.md, EAGLE_BCF_STATUS.md and EAGLE_SOURCE_REVIEW.md. Source indexes are never queried or certified against the source. Missing/forbidden dependencies or unsupported editions/formats skip3; invalid data, integrity, execution or capacity fails2. Analysis never installs. Historical 2.3/2.3.5, exact 2.4/server pipelines, automatic panel acquisition, imputation and biological phase accuracy or whole-paper parity remain outside this scope.',
        validation_note='169 strict tests on Windows Java 21/25 and Linux Java 21; 54 exact-JAR portable protocols. All 40 original phasing corpus protocols match complete genotype reports across 692,743 calls. Six copyable BCF native checks and four direct-original BCF comparisons match 512 calls and complete variant rows. Eleven source-verified roles retain Finch-or-Eagle ambiguity, reported editions and separate GLIMPSE2/Minimac/Beagle/RFMix steps. Require COMPLETED.json, its matching manifest hash and every listed file hash; preserve failed/provisional bundles and rerun to a NEW output. No release or hosted deployment is claimed.',
        project_adapter_available=True, canonical_adapter_available=False,
        development_adapter_cli='eagle-tools', development_candidate=proof['candidate'],
        development_candidate_sha256=proof['candidate_sha256'],
        workflow_validation='eagle-validation.json', source_role_coverage='eagle-role-coverage.json',
        source_roles_reviewed=11, whole_paper_reproduced=False,
        native_BCF2_2_conversion_available=True, source_index_correspondence_certified=False,
        runtime_profile='linux-x86_64-eagle-2.4.1',
        native_helpers=['src/main/java/org/jlinalg/genetics/EagleGeneticMap.java',
                        'src/main/java/org/jlinalg/genetics/EagleGenotypes.java',
                        'src/main/java/org/jlinalg/genetics/EagleReferenceJoin.java',
                        'src/main/java/org/jlinalg/cli/EagleBcf.java',
                        'src/main/java/org/jlinalg/cli/EagleSourceInputs.java'])


EAGLE_WARNING = '''**EAGLE: EXPLICIT INPUTS, SOURCE INDEX PROVENANCE AND REQUIRED RECOVERY.** Use the development `eagle-tools` command and read EAGLE_WORKFLOWS.md, EAGLE_BCF_STATUS.md and EAGLE_SOURCE_REVIEW.md. Native checks launch no phasing process. Original phasing requires Linux x86_64 Java and the admitted Eagle 2.4.1 private home. Export setup with `eagle-tools --export-runtime-setup NEW_DIRECTORY`; run its Python helper with install/download permission flags only where each action is permitted. Analysis never installs software.

**Unavailable or forbidden dependency, historical edition, BCF2.1 or newer header semantics: SKIP3; skip the dependent analysis. Invalid data, integrity, execution or capacity: FAIL2; no result is admitted.** Required action: inspect WARNINGS.md, analysis-status.json and retained ledgers, correct the named cause, and rerun to a NEW output. Require COMPLETED.json and its matching manifest/file hashes. Retain failed and provisional outputs.

**A source index is provenance only:** explicitly declared indexes are protected and hashed, never queried or certified against the original BCF. Fresh prepared-input CSI files are generated and validated. Required action: retain source-intake/, binary/index snapshots, decoded VCF and conversion/matching ledgers. Declare build, map, region, missingness/ploidy and allele/exclusion policies; review changes and warnings. A current 2.4.1 source-behavior check is not historical/server, imputation, biological phase accuracy or whole-paper certification. Cell Ranger remains excluded.

'''

EAGLE_ACTIONS = '''<section id="eagle-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1.2rem;margin:1.5rem 0;line-height:1.6;overflow-wrap:anywhere"><h2>Eagle: phasing inputs and required recovery</h2>
<p>Use development <code>eagle-tools</code>. Read <code>EAGLE_WORKFLOWS.md</code>, <code>EAGLE_BCF_STATUS.md</code> and <code>EAGLE_SOURCE_REVIEW.md</code>. Native input checks work on Windows/Linux and launch no phasing process. Original phasing requires Linux x86_64 Java and an admitted private Eagle 2.4.1 home. Export setup with <code>eagle-tools --export-runtime-setup NEW_DIRECTORY</code>; run its Python helper with install/download permission flags only where each action is permitted. Analysis never installs software.</p>
<p><strong>Unavailable or forbidden dependency/edition/capability: SKIP3; skip the dependent analysis. Invalid input,integrity,execution or capacity: FAIL2; no result is admitted.</strong> Required action: read <code>WARNINGS.md</code>, <code>analysis-status.json</code> and retained ledgers; correct the named cause and rerun to a <strong>NEW output</strong>. Require <code>COMPLETED.json</code>, its matching manifest hash and every listed file hash. Preserve failed/provisional bundles.</p>
<p><strong>Source indexes are provenance only.</strong> A declared index is protected and hashed, never queried or certified against the source. Fresh prepared-input CSI files are generated and validated. Required action: retain <code>source-intake/</code>, original binary/index snapshots, decoded VCF and conversion/matching ledgers. Review explicit build,map,region,missingness/ploidy and allele/exclusion choices. BCF2.1,newer header semantics and historical editions remain unavailable. Current 2.4.1 tests do not establish historical/server, imputation, biological phase accuracy or whole-paper parity. Eleven paper roles preserve reported editions and the Finch-or-Eagle ambiguity. Cell Ranger remains excluded.</p></section>'''
