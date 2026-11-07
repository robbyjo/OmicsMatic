# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete native prediction controls, source discovery and exact additive entries."""
import json
from pathlib import Path
import zipfile
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack, byte_pin
from cicero_prediction_evidence import BASE, FIT


def main():
    base = ROOT / BASE
    target = ROOT / 'docs/literature/cicero-prediction-development'
    assert not target.exists()
    build = json.loads((base / 'cicero-prediction-build-3/COMPLETED.json').read_bytes())
    controls = json.loads((base / 'cicero-prediction-validation-2/COMPLETED.json').read_bytes())
    example = json.loads((base / 'cicero-prediction-example-1/COMPLETED.json').read_bytes())
    assert controls['artifact'] == example['artifact'] == build['artifact']
    intake = json.loads((ROOT / FIT / 'source-intake-1/COMPLETED.json').read_bytes())
    assert pin(ROOT / 'tools/literature/intake_fitdistrplus_source.py') == intake['source_pin']
    files = {}
    attempts = [BASE + name for name in ['cicero-prediction-build-1', 'cicero-prediction-build-2', 'cicero-prediction-build-3',
        'cicero-prediction-validation-1', 'cicero-prediction-validation-2', 'cicero-prediction-example-1', 'cicero-prediction-source-review-1']]
    attempts.extend(FIT + name for name in ['source-role-scan-1', 'source-role-scan-2', 'source-intake-1', 'located-package-1'])
    for folder in attempts:
        assert (ROOT / folder).is_dir()
        for path in (ROOT / folder).rglob('*'):
            if path.is_file() and path.suffix != '.jar' and not path.name.endswith('.private.json'):
                files[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    artifact = build['artifact']
    jar, parent = ROOT / artifact['path'], ROOT / build['parent_artifact']['path']
    assert pin(jar) == {k: artifact[k] for k in ['bytes', 'sha256']}
    assert pin(parent) == {k: build['parent_artifact'][k] for k in ['bytes', 'sha256']}
    with zipfile.ZipFile(parent) as before, zipfile.ZipFile(jar) as after:
        old = {n: byte_pin(before.read(n)) for n in before.namelist()}
        new = {n: byte_pin(after.read(n)) for n in after.namelist()}
        assert all(new[n] == value for n, value in old.items())
        assert set(new) - set(old) == set(build['added_entries'])
        for name in build['added_entries']:
            files['candidate-owned-entry/' + name] = after.read(name)
    for label, entries in [('parent', old), ('candidate', new)]:
        files['artifact-' + label + '-entries.json'] = (json.dumps(entries, sort_keys=True, indent=2) + '\n').encode('utf8')
    names = set()
    proofs = [build, controls, example,
        json.loads((base / 'cicero-prediction-source-review-1/COMPLETED.json').read_bytes()),
        json.loads((ROOT / FIT / 'source-role-scan-2/COMPLETED.json').read_bytes())]
    for proof in proofs:
        for name, value in proof.get('source_pins', {}).items():
            assert pin(ROOT / name) == value
            if not name.startswith('build/'):
                names.add(name)
    names.update(['tools/literature/intake_fitdistrplus_source.py', 'tools/literature/seal_cicero_prediction.py',
        'tools/literature/cicero_prediction_evidence.py', '.gitattributes', 'docs/literature/CICERO_PREDICTION_SUBSET_DEVELOPMENT.md',
        'docs/literature/omics-2023-2026/cicero-prediction-checkout-shared-source-compatibility.json'])
    names.update(path.relative_to(ROOT).as_posix() for path in (ROOT / 'examples/cicero-prediction-subset').glob('*') if path.is_file())
    pins = {name: pin(ROOT / name) for name in sorted(names)}
    files.update({'current-source/' + name: (ROOT / name).read_bytes() for name in pins})
    parent_proofs = {name: pin(ROOT / name) for name in ['docs/literature/cicero-coaccessibility-development/VALIDATION.json',
        'docs/literature/omics-2023-2026/monocle-catalog-validation.json']}
    held = json.loads((ROOT / 'build/literature-review/push-checkpoint-2026-11-07-cicero-workflow-1/selection.json').read_bytes())['preserved']
    assert len(held) == 18 and all(pin(ROOT / name) == value for name, value in held.items())
    files['preserved-unrelated-worktree-pins.json'] = (json.dumps(held, indent=2) + '\n').encode('utf8')
    target.mkdir()
    layout = pack(target / 'controls.zip', files)
    assert (target / 'controls.zip').stat().st_size < 100_000_000
    warning = target / 'WARNINGS.md'
    warning.write_text('''# Native Cicero supplied-prediction reuse: warnings and required actions

**Require exit0 and result_admitted=true. Admission certifies accessibility membership only.** Use the separate development JAR and executed example in [the guide](../CICERO_PREDICTION_SUBSET_DEVELOPMENT.md). The canonical executable, release and catalog integration count remain unchanged.

**PREDICTION PAYLOAD NOT VALIDATED:** original scores and strings, including NA/NaN/Inf, remain byte for byte. Subsetting neither repairs them nor establishes valid statistics, calibration or biology. Inspect the supplied prediction provenance and limitations before interpretation. Empty subsets are filtering dispositions, not negative biological findings.

**FAIL2 rejects the entire cell-type family.** Late errors reject completed earlier subsets and retain them under unadmitted-partial-results. Read analysis-status.json, WARNINGS.md, membership.tsv, captured inputs and provenance. Correct the named malformed-input, missing-ID, heap, storage or capacity cause and retry NEW output paths. Preserve failures; do not rename IDs, alter scores or drop rows to force success. Invalid request syntax can fail before an output bundle exists; stderr gives the action.

No R or external installation is needed for this native subsetter. The separate original Cicero fitting workflow still needs its exact permitted runtime and reports SKIP3 when unavailable. This workflow does not generate or refit predictions. Coordinate convention, minimum overlap and source-only/both-endpoints scope are required explicit choices, not inferred paper settings. Source-only mode never tests target expression or target interval metadata.

Literal chromosomes and identifiers are retained. Witnesses follow accessible-input order; complete counts and every decision are exported. No genome aliases, liftover, score threshold, target annotation or expression filter is inferred. Logical capacities are not OS quotas; input captures and metadata need storage beyond the generated-table limit.

P153 establishes prediction reuse/accessibility subsetting, not a new fit. P043 newly identifies fitdistrplus for a shuffled normal background. Its function, method, version, shuffle and filtering-direction interpretation remain unresolved. The first exploratory name search missed citation-attached digits and is retained as failed; the corrected scan verifies all250 XML hashes/DOIs. Official fitdistrplus1.2-6 source was checksum-verified; nothing was installed or fit. Namespace loading, normal-fitting implementation and catalog registration remain pending. Do not silently reverse P043's filtering wording.

Seven contracts and19 runtime protocols passed on each of Windows Java21/25 and Linux Java21, plus an executed example. All43,317 successful membership decisions and retained row bytes have independent brute-force audits. Source and entry hashes prove consistency, not execution authenticity, paper reproduction or biological validity. The earlier compilation failure remains retained and unadmitted.

The catalog remains74 scoped integrations; discovered fitdistrplus and remaining Cicero/Monocle roles still require work. Cell Ranger is excluded. py-monocle runtime/license preference remains pending. The full250-paper goal remains active.
''', encoding='utf8', newline='\n')
    positive = [row for jvm in controls['JVMs'] for row in jvm['protocols'] if row['result_admitted']]
    assert len(positive) == 18
    save(target / 'VALIDATION.json', dict(schema_version=1, status='passed-native-Cicero-prediction-reuse-and-source-discovery-controls',
        archive=pin(target / 'controls.zip'), warnings_document=pin(warning), source_pins=pins, parent_proofs=parent_proofs,
        artifact=artifact, parent_artifact=build['parent_artifact'], unchanged_parent_entries=build['unchanged_parent_entries'],
        added_entries=build['added_entries'], canonical_executable_unchanged=build['canonical_executable_unchanged'],
        successful_families=19, independent_membership_decisions=sum(row['decisions'] for row in positive)+example['comparison']['decisions'],
        retained_prediction_rows=sum(row['retained'] for row in positive)+example['comparison']['retained'], runtime_protocols=57,
        tests_per_JVM=7, JVMs=3, workflow_admitted=True, implementation_credit=False, whole_paper_reproduced=False,
        fitdistrplus_catalog_registration_pending=True, whole_goal_complete=False, **layout))
    (target / 'SHA256SUMS').write_text(''.join(pin(target / name)['sha256'] + '  ' + name + '\n' for name in ['controls.zip', 'WARNINGS.md', 'VALIDATION.json']), encoding='utf8', newline='\n')
    print('Sealed', len(files), 'logical files;', (target / 'controls.zip').stat().st_size, 'archive bytes')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source checks')
    main()
