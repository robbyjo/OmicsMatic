# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal native scDRS component controls, source edges and retained failed attempts; no catalog credit."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack
from intake_scdrs_reference import VERSIONS
from verify_scdrs_native_evidence import DEST, BASE, SOURCE_FILES, STATUS, inspect


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists() and not DEST.exists()
    out.mkdir(parents=True)
    DEST.mkdir(parents=True)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    files = {}
    groups = ['native-build21-1', 'native-build25-1', 'native-validation-1', 'native-validation-2',
              'source-edge-controls-1', 'source-edge-controls-2', 'numerical-source-intake-1', 'guide-example-1']
    for group in groups:
        for path in sorted((BASE / group).rglob('*')):
            if path.is_file(): files[group + '/' + path.relative_to(BASE / group).as_posix()] = path.read_bytes()
    for path in sorted((BASE / 'reference-setup-1').iterdir()):
        if path.is_file() and path.name != 'micromamba': files['reference-setup-1/' + path.name] = path.read_bytes()
    files['guide-example-1/helper-at-attempt.py'] = (ROOT / 'build/literature-review/validate_scdrs_guide_example.py').read_bytes()
    intake = BASE / 'source-intake-1'
    record = json.loads((intake / 'COMPLETED.json').read_bytes())
    files['upstream/COMPLETED.json'] = (intake / 'COMPLETED.json').read_bytes()
    for version, profile in record['profiles'].items():
        assert profile['commit'] == VERSIONS[version]
        for name, wanted in profile['files'].items():
            path = intake / version / 'source' / name
            assert pin(path) == wanted
            files['upstream/' + version + '/source/' + name] = path.read_bytes()
    for name in SOURCE_FILES: files['current-source/' + name] = (ROOT / name).read_bytes()
    evidence = pack(DEST / 'controls.zip', files)
    assert pin(DEST / 'controls.zip')['bytes'] < 95_000_000
    artifacts = {}
    for tag in ['21', '25']:
        for kind in ['api', 'consumer']:
            name = 'scdrs-' + kind + '-javac' + tag + '.jar'
            shutil.copyfile(BASE / ('native-build' + tag + '-1') / ('scdrs-' + kind + '.jar'), DEST / name)
            artifacts[name] = pin(DEST / name)
    validation = json.loads(files['native-validation-2/COMPLETED.json'])
    assert validation['status'] == 'PASS_NATIVE_SCDRS_COMPONENTS_THREE_HISTORICAL_SOURCES_THREE_JVMS'
    proof = dict(schema_version=1, status=STATUS, source_commits=VERSIONS, new_catalog_credit=False,
        full_adapter_validated=False, whole_goal_complete=False, scdrs_remains_missing=True,
        native_components=['weighted_raw_scores', 'background_correction', 'cell_empirical_scores',
            'pooled_empirical_probability', 'group_association', 'correlation_monte_carlo', 'bh_explicit_family'],
        numeric_comparisons=sum(s['numerical_values_compared'] for s in validation['summaries']),
        actual_upstream_score_cell_controls=9, original_numerical_controls=21, junit_passes=24,
        max_float32_output_ulps=0, article_prose_redistributed=False, synthetic_controls_only=True,
        scientific_inputs_or_upstream_sources_repaired=False,
        failed_attempts_retained=['native-validation-1'],
        source_pins={name: pin(ROOT / name) for name in SOURCE_FILES}, artifacts=artifacts,
        archive=pin(DEST / 'controls.zip'), evidence=evidence, canonical_executable_unchanged=before,
        historical_catalog_snapshot=dict(new_scoped_integrations=98, including_existing_adapters=101, missing=245,
            summary=pin(ROOT / 'docs/literature/omics-2023-2026/summary.json')),
        required_action='Complete the typed original-runtime scDRS adapter, graph/downstream workflows, runtime admission, '
            'saved verification, root dispatch and all five primary paper-role reviews before catalog admission. '
            'SKIP3 skips dependent analysis for missing/forbidden prerequisites. FAIL2 admits no partial result; '
            'resolve the named cause and retry NEW paths while preserving every attempt.')
    save(DEST / 'validation.json', proof)
    (DEST / '.gitattributes').write_text('* -text\n', encoding='ascii', newline='\n')
    (DEST / 'SHA256SUMS').write_text(''.join(pin(p)['sha256'] + '  ' + p.name + '\n'
        for p in sorted(DEST.iterdir()) if p.is_file() and p.name not in ['.gitattributes', 'SHA256SUMS']),
        encoding='ascii', newline='\n')
    inspect()
    assert pin(canonical) == before
    result = dict(status='PASS_NATIVE_SCDRS_COMPONENT_PUBLIC_EVIDENCE_SEALED_ONLY',
        archive=pin(DEST / 'controls.zip'), logical_files=len(files), new_catalog_credit=False,
        full_adapter_validated=False, canonical_executable_unchanged=True)
    save(out / 'COMPLETED.json', result)
    print(json.dumps(result))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable source/evidence/canonical checks')
    main()
