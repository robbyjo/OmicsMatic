# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal original synthetic Cicero objects, failed attempts, source and additive JAR entries."""
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack, byte_pin
from cicero_workflow_evidence import BASE, FINAL, audit_connections


def main():
    base = ROOT / BASE
    target = ROOT / 'docs/literature/cicero-coaccessibility-development'
    assert not target.exists()
    proofs = {key: json.loads((base / name / 'COMPLETED.json').read_bytes()) for key, name in FINAL.items()}
    build = proofs['build']
    artifact = build['artifact']
    assert all(p['artifact'] == artifact for p in proofs.values())
    assert len(proofs['variants']['protocols']) == 18 and proofs['contracts']['tests_per_JVM'] == 516
    jar, parent = ROOT / artifact['path'], ROOT / build['parent_artifact']['path']
    assert pin(jar) == {k: artifact[k] for k in ['bytes', 'sha256']}
    assert pin(parent) == {k: build['parent_artifact'][k] for k in ['bytes', 'sha256']}
    files = {}
    attempts = ['cicero-workflow-profile-1', 'cicero-native-controls-1', 'cicero-glasso-source-intake-1',
                'cicero-precision-diagnostic-1', 'cicero-precision-diagnostic-2', 'cicero-readiness-probe-2',
                *['cicero-workflow-build-' + str(i) for i in range(1, 9)],
                *['cicero-workflow-validation-' + str(i) for i in [1, 2, 3, 5, 6, 7, 8]],
                *['cicero-workflow-contracts-' + str(i) for i in range(1, 6)],
                'cicero-workflow-variants-1', 'cicero-workflow-variants-2',
                'cicero-semantic-recovery-1', 'cicero-semantic-recovery-2', 'cicero-workflow-example-1']
    for name in attempts:
        assert (base / name).is_dir(), name
        for path in (base / name).rglob('*'):
            if path.is_file() and path.suffix != '.jar':
                files[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    with zipfile.ZipFile(parent) as before, zipfile.ZipFile(jar) as after:
        old = {n: byte_pin(before.read(n)) for n in before.namelist()}
        new = {n: byte_pin(after.read(n)) for n in after.namelist()}
        assert all(new[n] == v for n, v in old.items()) and set(new) - set(old) == set(build['added_entries'])
        for name in build['added_entries']:
            files['candidate-owned-entry/' + name] = after.read(name)
    for label, entries in [('parent', old), ('candidate', new)]:
        files['artifact-' + label + '-entries.json'] = (json.dumps(entries, sort_keys=True, indent=2) + '\n').encode('utf8')
    names = set(build['source_pins'])
    for key, proof in proofs.items():
        if 'source_pin' in proof:
            name = 'tools/literature/' + {'baseline': 'validate_cicero_workflow.py', 'variants': 'validate_cicero_workflow_variants.py'}[key]
            assert pin(ROOT / name) == proof['source_pin']
            names.add(name)
        for name, value in proof.get('source_pins', {}).items():
            assert pin(ROOT / name) == value
            if not name.startswith('build/'):
                names.add(name)
    names.update(['tools/literature/prepare_cicero_workflow_profile.py', 'tools/literature/prepare_cicero_native_controls.py',
                  'tools/literature/seal_cicero_workflow.py', 'tools/literature/cicero_workflow_evidence.py',
                  '.gitattributes', 'docs/literature/CICERO_COACCESSIBILITY_DEVELOPMENT.md',
                  'docs/literature/omics-2023-2026/cicero-workflow-checkout-shared-source-compatibility.json'])
    names.update(p.relative_to(ROOT).as_posix() for p in (ROOT / 'examples/cicero-coaccessibility').glob('*') if p.is_file())
    pins = {n: pin(ROOT / n) for n in sorted(names)}
    files.update({'current-source/' + n: (ROOT / n).read_bytes() for n in pins})
    parents = {n: pin(ROOT / n) for n in ['docs/literature/monocle2-differential-development/VALIDATION.json',
                'docs/literature/omics-2023-2026/monocle-catalog-validation.json']}
    totals = [0, 0]
    count = 0
    for name in list(files):
        if '/results/' in name and name.endswith('/models.json') and any(name.startswith(BASE + FINAL[k] + '/') for k in ['baseline', 'variants', 'example']):
            rows, missing = audit_connections(files, name.rsplit('/', 1)[0] + '/')
            totals[0] += rows
            totals[1] += missing
            count += 1
    assert count == 9
    held = json.loads((ROOT / 'build/literature-review/push-checkpoint-2026-11-07-monocle-catalog-2/selection.json').read_bytes())['preserved']
    assert len(held) == 18 and all(pin(ROOT / n) == v for n, v in held.items())
    files['preserved-unrelated-worktree-pins.json'] = (json.dumps(held, indent=2) + '\n').encode('utf8')
    target.mkdir()
    layout = pack(target / 'controls.zip', files)
    assert (target / 'controls.zip').stat().st_size < 100_000_000
    warning = target / 'WARNINGS.md'
    warning.write_text('''# Cicero development adapter: warnings and required actions

**Require exit 0 and `analysis-status.json` `result_admitted=true`.** Use the separate additive development JAR and the executed example in [the guide](../CICERO_COACCESSIBILITY_DEVELOPMENT.md). This checkpoint does not change the canonical executable or award additional catalog integration credit.

**SKIP3 — dependent analysis skipped:** this original-runtime profile requires Windows Java, R4.6.1, Cicero1.3.9, Monocle3 1.4.27 and the exact dependency versions in SOURCE-PROFILE.json. Missing, forbidden, mismatched or unsupported runtimes produce no admitted result. Provision the recorded private runtime only where permitted; analysis installs nothing. Otherwise retain the skip and do not interpret output as an analysis. Linux native methods are tested; Linux upstream execution needs a separately validated profile.

**FAIL2 — entire batch rejected:** any input mismatch, source error, model error flag, timeout, capacity breach or native audit failure rejects every case in the atomic batch, including earlier completed cases. Read WARNINGS.md, analysis-status.json, worker/worker-report.json, source-status.json, stdout/stderr and process-receipt.json. Correct the stated cause and retry NEW paths. Preserve all attempts. Do not force success by dropping features, repairing identifiers, changing values or replacing nonfinite scores.

**Original unresolved connections are NA.** Mixed positive/negative contributions produce source NA, not zero, absence or a valid null. Select retain-source-NA or fail-on-NA explicitly. Empty/too-large source windows retain dispositions under an explicit policy; they are not fitted evidence. All-zero peak rows are retained using zero-valued dimension anchors, recorded and independently audited without adding observations. Zero-total cells are refused.

**Original precision asymmetry is preserved.** Original glasso1.11 constructs precision columns separately and returns asymmetric WI with error flag zero. Raw WI and quantified asymmetry remain unchanged. Cicero uses W for connections. Java independently checks axes, counts, aggregation, nearest-neighbor membership, covariance inputs, penalties and every reconciled connection. It does not independently refit glasso, replay RNG/FNN tie selection, certify inverse accuracy or authenticate execution. Do not symmetrize original matrices.

**All original message fragments remain prominent.** These include Monocle3 saveRDS warnings about unsaved Annoy/HNSW indices. This workflow uses prepared coordinates and original FNN aggregation; it does not claim learned Monocle graph/index restoration. Peak geometry uses strict endpoint inclusion; source Peak2 is a factor. Literal identifiers, declared coordinate labels and original order remain unchanged.

Earlier attempts are retained and unadmitted: unavailable UTF-8 locale, named-vector export mismatch, incorrect precision-symmetry assumption and incorrect factor-order assumption. Complete original fits were preserved during corrections to the adapter audit/exporter. Both precision diagnostics remain; the first used display-rounded JSON, and the second retains full precision.

The archive contains synthetic controls and original source, not a reproduction of a paper. CCANs, gene activity, custom shuffled-background/FDR workflows, historical environments and Cicero prediction reuse remain separate work. Logical capacities are not OS quotas. The 250-paper goal remains active; the catalog still reports 74 scoped integrations. Cell Ranger remains excluded. The py-monocle license decision remains pending and no py-monocle runtime was installed or executed.
''', encoding='utf8', newline='\n')
    save(target / 'VALIDATION.json', dict(schema_version=1, status='passed-original-Cicero-coaccessibility-development-controls',
        archive=pin(target / 'controls.zip'), warnings_document=pin(warning), source_pins=pins, parent_proofs=parents,
        artifact=artifact, parent_artifact=build['parent_artifact'], unchanged_parent_entries=build['unchanged_parent_entries'],
        added_entries=build['added_entries'], canonical_executable_unchanged=build['canonical_executable_unchanged'],
        audited_exports=count, audited_connection_rows=totals[0], retained_NA_rows=totals[1], runtime_protocols=18,
        semantic_corruption_refusals=14, tests_per_JVM=516, JVMs=3, adapter_admitted=True,
        implementation_credit=False, whole_paper_reproduced=False, whole_goal_complete=False, **layout))
    (target / 'SHA256SUMS').write_text(''.join(pin(target / n)['sha256'] + '  ' + n + '\n' for n in ['controls.zip', 'WARNINGS.md', 'VALIDATION.json']), encoding='utf8', newline='\n')
    print('Sealed %s logical files, %s admitted rows (%s NA); archive %s bytes' % (len(files), totals[0], totals[1], (target / 'controls.zip').stat().st_size))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/control bindings')
    main()
