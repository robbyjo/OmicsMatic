# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate complete frozen Salmon workflows before scoped public adapter credit."""
import hashlib
import json
from pathlib import PurePosixPath
import stat
import zipfile
from catalog_source_integrity import verify_source
from salmon_catalog import salmon_metadata as legacy_metadata

STATUS = 'passed-typed-Salmon-bulk-workflows-portable-bundles-and-source-populations'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def verify(root, data):
    folder = root / 'docs/literature/salmon-bulk-workflow'
    proof = json.loads((folder / 'VALIDATION.json').read_bytes())
    assert proof['schema_version'] == 1 and proof['status'] == STATUS
    assert proof['exact_editions'] == ['1.0.0', '1.1.0', '1.5.2', '1.8.0', '1.9.0']
    sums = dict(line.split('  ', 1)[::-1] for line in (folder / 'SHA256SUMS').read_text().splitlines())
    assert set(sums) == {a['path'] for a in proof['archives']} | {'VALIDATION.json', 'HISTORICAL-SOURCE-ALIASES.json'}
    for name, expected in sums.items():
        assert sha((folder / name).read_bytes()) == expected
    assert len(proof['archives']) == 6
    current_sources = set()
    for archive in proof['archives']:
        path = folder / archive['path']; assert path.name == archive['path'] and path.stat().st_size == archive['bytes']
        census = proof['archive_files'][archive['path']]
        with zipfile.ZipFile(path) as z:
            assert len(z.infolist()) == len(set(z.namelist())) == len(census) == archive['entries'] and z.testzip() is None
            for entry in z.infolist():
                name = entry.filename; member = PurePosixPath(name)
                assert not member.is_absolute() and '..' not in member.parts and '\\' not in name and stat.S_IFMT(entry.external_attr >> 16) == stat.S_IFREG
                raw = z.read(entry); expected = census[name]; assert len(raw) == expected['bytes'] and sha(raw) == expected['sha256']
                if name.startswith('source/'):
                    relative = name[len('source/'):]; assert relative in proof['source_pins'] and proof['source_pins'][relative] == expected
                    verify_source(root, data, relative, expected['sha256']); current_sources.add(relative)
    assert current_sources == set(proof['source_pins'])
    platforms = proof['public_platform_validation']; assert sorted(r['name'] for r in platforms) == ['linux-java21', 'windows-java21', 'windows-java25']
    assert all(r['tests'] == r['passed'] == 449 and r['skipped'] == 0 and len(r['portable']) == 8 for r in platforms)
    original = proof['original_workflow_validation']; assert len(original) == 2
    runs = [r for epoch in original for r in epoch['runs']]
    assert sorted(r['version'] for r in runs) == proof['exact_editions'] and all(len(r['fits']) == 7 and r['strict_population_skipped'] and r['original_insufficient_mass_rejected'] for r in runs)
    v19 = next(r for r in runs if r['version'] == '1.9.0')
    assert all(v19[k] for k in ['bootstrap_capacity_rejected_before_fit', 'late_finalization_revoked', 'unexpected_finalization_error_revoked'])
    totals = proof['controlled_totals']
    assert (totals['original_workflow_fits'], totals['original_workflow_rows'], totals['original_workflow_bootstrap_values']) == (36, 1275, 32720)
    assert (totals['documented_fits'], totals['documented_rows'], totals['documented_bootstrap_values']) == (2, 80, 4200)
    assert (totals['old_decimal_replay_failure_unadmitted_rows'], totals['old_decimal_replay_failure_unadmitted_bootstrap_values']) == (40, 2000)
    assert (totals['late_finalization_unadmitted_rows'], totals['late_finalization_unadmitted_bootstrap_values']) == (80, 240)
    roles = json.loads((data / 'salmon-role-coverage.json').read_bytes()); assert roles == proof['source_role_validation']
    assert roles['applied'] and roles['reviewed_roles'] == 8 and roles['total_reviews'] == 793 and roles['supporting_pointers'] == 241
    claims = proof['claims']; assert all(claims[k] for k in ['typed_public_quantification_command_available', 'runtime_setup_export_available', 'permitted_download_install_available', 'controlled_original_fits_admitted'])
    assert not any(claims[k] for k in ['producer_host_readmitted_by_portable_verification', 'whole_paper_reproduced', 'biological_accuracy_certified', 'whole_goal_complete', 'released', 'pushed', 'deployed'])
    artifact = root / proof['artifact']['path']
    if artifact.exists():
        assert artifact.stat().st_size == proof['artifact']['bytes'] and sha(artifact.read_bytes()) == proof['artifact']['sha256']
    return proof


def salmon_metadata(root, data):
    # Earlier source/role/decoder evidence stays immutable and independently
    # checked. The complete workflow is a distinct later checkpoint.
    result = legacy_metadata(root, data)
    proof = verify(root, data)
    result.update(implementation_status='upstream-adapter-validated', implementation_source='src/main/java/org/jlinalg/cli/SalmonToolsCli.java',
        coverage_note='New scoped original Salmon bulk quantification integration, with eight reviewed source roles; unknown paper settings and downstream tools remain separate.',
        implementation_scope='The source salmon-tools command builds and captures exact original Linux x86_64 Salmon 1.0.0, 1.1.0, 1.5.2, 1.8.0, 1.9.0 dense indexes and runs paired/single FASTQ or query-grouped transcriptome SAM/BAM fits. '
        'Typed requests preserve exact reference/decoy/header/library choices, EM or VB, sequence/GC/position bias, complete bootstraps and capacities. Native Java validates unchanged complete inputs, every original reference/decoy ledger, quantification row and inferential value. '
        'The original process and current host/runtime have separate complete gates. Strict source/declared population mismatch skips before fitting; explicitly selected source-runtime views retain false declared-retained coverage without relabeling or zero imputation. '
        'Setup export and permitted offline/download installation are separate from analysis. Whole captured bundles verify on Windows without opening producer paths. Alevin/single-cell modes, genomic alignment projection, arbitrary flags, cross-edition index reuse, tximport/gene aggregation and downstream models require separate contracts. '
        'Read SALMON_INDEX_DEVELOPMENT.md and examples/salmon-bulk/README.md. Older canonical/released executables lack this command.',
        validation_note='449 focused actual-JAR cases pass without skips on Windows Java 21/25 and Linux Java 21. Across all five exact editions, 36 complete controlled workflow fits retain 1275 quantification rows and 32720 complete bootstrap values; the documented root-command example adds 80 rows and 4200 values. '
        'Paired EM/VB with all three bias flags, single reads, no replicates, SAM, labeled BAM conversion and explicit source-runtime populations pass complete replay. Strict population mismatches skip; original insufficient mass, bootstrap capacity, late cancellation and unexpected errors admit no prefix. '
        'The original short-reference/decoy discrepancy, source 1.0.0 unavailable SHA512 links and automatic-vs-observed library distinctions remain explicit. The earlier generic-double replay failure retains 40 unadmitted rows and 2000 values. '
        'Separate original four-target positional-bias crashes remain unresolved; no requested bias is disabled silently. Runtime health and repeated synthetic controls do not calibrate biology, production scale or whole-paper reproduction.',
        project_adapter_available=True, canonical_adapter_available=False, development_adapter_cli='salmon-tools', public_quantification_CLI_available=True,
        complete_quantification_output_audit_available=True, native_input_reference_decoy_API_available=True, runtime_admission_gate_available=True,
        controlled_original_fits_admitted=True, scientific_runtime_admitted=False, development_candidate=proof['artifact']['path'], development_candidate_sha256=proof['artifact']['sha256'],
        workflow_validation='../salmon-bulk-workflow/VALIDATION.json', runtime_profiles=['linux-x86_64-salmon-' + v for v in proof['exact_editions']],
        runtime_setup_export_available=True, download_install_available=True, runtime_installer='tools/literature/install_salmon.py',
        whole_paper_reproduced=False, biological_accuracy_certified=False, paper_exact_unknown_settings_available=False)
    return result


SALMON_WARNING = '''
## Salmon: complete original bulk workflows, target populations and required recovery

Use source-built **salmon-tools** and read **SALMON_INDEX_DEVELOPMENT.md**, **salmon-bulk-workflow/VALIDATION.json**, **salmon-role-coverage.json** and **examples/salmon-bulk/README.md**. Older released/canonical 0.3.7 executables lack this command. Exact original Linux x86_64 editions: **1.0.0, 1.1.0, 1.5.2, 1.8.0, 1.9.0**; use Linux Java inside WSL on Windows. Portable complete captured verification and setup export also work on Windows Java.

**Choose scientific settings explicitly.** Index/reference/decoy/header, paired/single/transcriptome-alignment, library, EM/VB, sequence/GC/position bias and complete bootstrap choices remain recorded. “SAF Pattern” is not guessed into a library code. Single-end reads need an explicit fragment mean/SD. Versioned identities and raw sequence/quality bytes are preserved. Genomic spliced BAM is not automatically projected into transcriptome alignments.

**A successful original process can omit a declared retained transcript.** The original short-reference/decoy classifier has 15 retained boundary discrepancies across five editions. The default strict policy skips before fitting. Review every exact row of **REFERENCE-LEDGER.jsonl**. A separately selected **ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER** policy admits only that explicitly selected population and retains **declared retained coverage=false**. Never relabel omitted transcripts as declared decoys or impute zero expression. Raw references removed by original preprocessing remain in the ledger with reasons separate from duplicate-cluster membership.

**Required recovery:** export **--export-runtime-setup NEW_DIRECTORY**, read RUN-SETUP.txt and use only permitted **--allow-install**/**--allow-download**; complete offline archives use **--archive-dir**. Installation is never attempted during fitting. **SKIP3:** required exact runtime, helper, resource or admitted capability unavailable/forbidden means skip dependent analysis. **FAIL2:** invalid input, changed evidence, process, numerical, capacity, cancellation or finalization failure admits no result. Preserve all partials, read WARNINGS.md/FAILURE.json, metadata/input-audit.json, OUTPUT-AUDIT.json and every original log; correct the named cause and use NEW paths. Require COMPLETED.json and successful whole-family **--verify** before downstream use. Metadata estimates are not JVM heap guarantees.

**Preserve source uncertainty.** Small-reference positional-bias crashes remain unresolved; never silently disable requested bias. Keep original burn-in/effective-length/model warnings. Original 1.0.0 quantification lacks two SHA512 links; they remain unavailable. Automatic reported library type and observed orientation counts differ. TPM is relative abundance and estimated counts can be fractional; neither is raw integer reads. Do not invent P227's percentage denominator or unknown paper settings. Gene aggregation, tximport, DESeq2, SUPPA2, MAJIQTL and alevin are separate capabilities.

All **449 actual-JAR tests** pass on three JVMs without skips. **36 controlled original workflow fits** retain **1,275 rows and 32,720 bootstrap values**; the documented example adds **80 rows and 4,200 values**. Synthetic populations are reused across versions. Rejected late outputs and the earlier exact-decimal replay failure remain unadmitted. These checks do not certify biological accuracy, production scale, whole-paper reproduction, execution authenticity or current producer-host admission by portable verification. Cell Ranger remains excluded.

'''

SALMON_ACTIONS = '''<section id="salmon-required-actions" style="border-left:5px solid #c78417;background:#c7841712;padding:1rem;margin:1rem 0;overflow-wrap:anywhere"><h2>Salmon: exact original bulk workflows and complete target populations</h2>
<p>Use source-built <strong>salmon-tools</strong>. Read <code>SALMON_INDEX_DEVELOPMENT.md</code>, <code>salmon-bulk-workflow/VALIDATION.json</code>, <code>salmon-role-coverage.json</code> and <code>examples/salmon-bulk/README.md</code>. Older canonical/released 0.3.7 executables lack this command. Exact original Linux x86_64 editions: <strong>1.0.0, 1.1.0, 1.5.2, 1.8.0, 1.9.0</strong>. Execution uses Linux Java, including WSL on Windows; Windows Java also verifies complete captured bundles and exports setup.</p>
<p><strong>Declare the reference/index, library, EM/VB, bias and complete inferential population.</strong> Preserve original FASTA/FASTQ/SAM/BAM, exact versioned identities and every reference/decoy ledger row. Single-end reads require a fragment mean/SD. “SAF Pattern” is not a library code. Genomic spliced alignments require separate explicit transcriptome projection.</p>
<p><strong>Short-reference/decoy omissions remain explicit.</strong> Strict declared-retained requests skip before fitting. An explicitly chosen <code>ORIGINAL_RUNTIME_WITH_EXPLICIT_LEDGER</code> result keeps <strong>declared retained coverage=false</strong>; never relabel missing transcripts or impute zeros. Original preprocessing removals and duplicate-cluster membership remain separate facts.</p>
<p><strong>SKIP3: unavailable/forbidden required runtime, helper, installation/download, resource or admitted capability means skip dependent analysis. FAIL2: input, integrity, original process, numerical, capacity, cancellation or finalization failure admits no result.</strong> Export setup separately with <code>--export-runtime-setup</code>; use only permitted <code>--allow-install</code>/<code>--allow-download</code> or complete offline archives. Preserve every attempt; read <code>WARNINGS.md</code>, <code>FAILURE.json</code>, complete input/output ledgers and all original logs. Correct the named cause and use NEW paths. Require <code>COMPLETED.json</code> plus successful <code>--verify</code>.</p>
<p><strong>Keep original uncertainty and warnings.</strong> Small-reference positional-bias crashes remain unresolved; do not silently disable requested bias. Retain burn-in/model/effective-length fallbacks, original 1.0.0 unavailable SHA512 links, and automatic-versus-observed library distinctions. TPM and fractional estimated counts are not raw integer reads. Downstream gene aggregation/models and unknown paper settings require separate contracts.</p>
<p><strong>Validation:</strong> 449 actual-JAR tests per JVM on Windows Java 21/25 and Linux Java 21 without skips; 36 complete original workflow fits retain 1275 rows and 32720 bootstrap values, plus the documented 80-row/4200-value example. Rejected outputs remain unadmitted. Reused synthetic controls do not certify biological accuracy, whole-paper reproduction, production scale or execution authenticity. Portable verification does not readmit the producer host. Cell Ranger remains excluded.</p></section>'''
