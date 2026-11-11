# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze tested process/capture and native component evidence; give no nf-core credit."""
import json, shutil, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE

DEST = ROOT / 'docs/literature/nfcore-captured-development'


def main():
    assert not DEST.exists()
    old = json.loads((ROOT / 'docs/literature/nfcore-engine-development/VALIDATION.json').read_bytes())
    assert all(pin(ROOT / n) == v for n, v in old['source_pins'].items())
    assert all(pin(ROOT / n) == v for n, v in old['catalog_unchanged'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == old['canonical_executable_unchanged']
    proof = dict(status='PASS_CAPTURED_PROCESS_AND_NATIVE_COMPONENT_CHECKPOINT_ONLY',
        claims=dict(nfcore_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80,
            including_three_existing=102, missing=244, nfcore_status='missing', whole_goal_complete=False,
            full_nfcore_adapter=False, whole_pipeline_scientific_admission=False,
            biological_accuracy_certified=False, paper_reproduction=False),
        catalog_unchanged=old['catalog_unchanged'], canonical_executable_unchanged=old['canonical_executable_unchanged'])
    expected = {
        'matrix': ('captured-engine-matrix-3', 'PASS_NFCORE_PRIMARY_CONFIG_ENGINE_AND_LITERAL_CAPTURE_CONTROLS_ONLY'),
        'integrity': ('captured-engine-integrity-1', 'PASS_NFCORE_CROSS_COMPILER_EXECUTION_AND_SEMANTIC_INTEGRITY_CONTROLS_ONLY'),
        'process21': ('independent-process-controls-2', 'PASS_NFCORE_ANCHORED_PROCESS_IDENTITY_AND_INDEPENDENT_CLEANUP'),
        'process25': ('independent-process-controls-25-7', 'PASS_NFCORE_ANCHORED_PROCESS_IDENTITY_AND_INDEPENDENT_CLEANUP'),
        'mappings': ('mapping-controls-2', 'PASS_ORIGINAL_CHIPSEQ121_CHECKED_MAPPING_GRAPH_CONTROLS'),
        'frip': ('frip-controls-2', 'PASS_NATIVE_FRIP_AND_BAM_IDENTITY_AGAINST_ORIGINAL_TOOLS'),
        'identity': ('scientific-input-identity-controls-3', 'PASS_ORIGINAL_FILE_IDENTITY_AND_NAMED_GZIP_CAPTURE_CONTROLS_ONLY'),
        'components': ('scientific-cross-platform-4', 'PASS_FOUR_CROSS_PLATFORM_COMPLETE_COMPONENT_AUDITS_ONLY'),
        'validator_errors': ('validator-failure-exits-1', 'PASS_VALIDATOR_FAIL2_EXIT_AND_WARNING_CONTROLS')}
    for key, (folder, status) in expected.items():
        data = json.loads((BASE / folder / 'COMPLETED.json').read_bytes())
        assert data['status'] == status, (key, data)
        for source_key in ['source_pins', 'build_source_pins']:
            assert all(pin(ROOT / n) == v for n, v in data.get(source_key, {}).items())
        proof[key] = data
    assert len(proof['matrix']['controls']) == 15 and all(r['passed'] for r in proof['matrix']['controls'])
    assert len(proof['integrity']['executions']) == 3 and len(proof['integrity']['saved_verification']) == 12
    assert len(proof['mappings']['cases']) == 11 and len(proof['frip']['cases']) == 8
    assert len(proof['identity']['platforms']) == 6
    assert len(proof['components']['cases']) == 4 and proof['components']['exact_reports_agree']
    DEST.mkdir()
    artifacts, builds = {}, {}
    for compiler in ['21', '25']:
        for family, version, original, target in [
            ('captured', '7', 'nfcore-captured-development.jar', 'nfcore-captured-javac'),
            ('scientific', '5', 'nfcore-scientific-development.jar', 'nfcore-chipseq121-audit-javac')]:
            folder = BASE / (family + '-build' + compiler + '-' + version)
            build = json.loads((folder / 'COMPLETED.json').read_bytes()); artifact = folder / original
            assert build['status'].startswith('PASS_') and pin(artifact) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
            assert all(pin(ROOT / n) == v for n, v in build['source_pins'].items())
            name = target + compiler + '.jar'; shutil.copyfile(artifact, DEST / name)
            artifacts[name] = pin(DEST / name); builds[family + compiler] = build
    assert proof['matrix']['artifact']['sha256'] == artifacts['nfcore-captured-javac21.jar']['sha256']
    assert proof['integrity']['artifact']['sha256'] == artifacts['nfcore-captured-javac25.jar']['sha256']
    for compiler in ['21', '25']:
        assert proof['process' + compiler]['artifact'] == artifacts['nfcore-captured-javac' + compiler + '.jar']
        assert proof['components']['artifact_pins'][compiler] == artifacts['nfcore-chipseq121-audit-javac' + compiler + '.jar']
    sources = [*(ROOT / n for n in set().union(*(set(v['source_pins']) for v in builds.values()))),
        *(ROOT / 'tools/literature' / n for n in ['validate_nfcore_chipseq_mappings.py', 'validate_nfcore_frip.py',
          'validate_nfcore_process.py', 'validate_nfcore_scientific_files.py', 'validate_nfcore_chipseq_complete.py',
          'freeze_nfcore_captured_checkpoint.py', 'verify_nfcore_captured_checkpoint.py']),
        *sorted((ROOT / 'tools/literature/java').glob('Nfcore*Control.java')),
        *sorted((ROOT / 'tools/literature/fixtures/nfcore').iterdir())]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    files = {'sources/' + n: ROOT / n for n in source_pins}
    folders = [*(BASE / folder for folder, _ in expected.values()),
        *(BASE / ('captured-build' + c + '-' + str(i)) for c in ['21', '25'] for i in range(1, 8)),
        *(BASE / ('scientific-build' + c + '-' + str(i)) for c in ['21', '25'] for i in range(1, 6)),
        *(BASE / n for n in ['mapping-controls-1', 'frip-controls-1', 'scientific-input-identity-controls-1',
            'scientific-cross-platform-1', 'scientific-cross-platform-2', 'scientific-cross-platform-3', 'scientific-input-identity-controls-2', 'scientific-full-audit-control-2',
            'scientific-full-audit-control-3', 'captured-engine-matrix-1', 'captured-engine-matrix-2',
            'captured-chipseq121-1', 'captured-chipseq121-2', 'captured-chipseq121-2-resume-1',
            'captured-chipseq121-3', 'captured-chipseq121-3-resume-1',
            'captured2-leftovers-inspection-1', 'captured2-leftovers-apply-1'])]
    for folder in folders:
        if not folder.exists(): continue
        for p in folder.rglob('*'):
            parts = p.relative_to(folder).parts
            if any(s.startswith('classes') or s.endswith('-views') or s in ['named-inputs', 'original-complete-attempt'] for s in parts): continue
            if p.is_file() and p.suffix not in ['.jar', '.class']:
                assert not p.is_symlink(); files['evidence/' + p.relative_to(BASE).as_posix()] = p
    # Large execution intake: metadata only, not an incomplete bundle presented as standalone.
    attempt = BASE / 'scientific-cross-platform-2/original-complete-attempt'
    completion = json.loads((attempt / 'completion.json').read_bytes())
    outputs = json.loads((attempt / 'outputs.json').read_bytes())
    assert completion['stats']['succeeded'] == 88 and completion['stats']['cached'] == 9
    assert all(completion['stats'][k] == 0 for k in ['failed', 'ignored', 'aborted'])
    assert completion['trace']['taskRows'] == 97
    assert sum(r['kind'] == 'file' for r in outputs['members'].values()) == 367
    process = json.loads((attempt / 'process/process.json').read_bytes())
    assert process['exit_code'] == 0 and process['owned_live_after_cleanup'] == []
    proof['execution_intake'] = dict(completion=completion, output_files=367,
        manifest=pin(attempt / 'manifest.json'), standalone_objects_publicly_bundled=False,
        scientific_result_admitted=False, simulated_PCR_derivative=True,
        exit_code=process['exit_code'], owned_live_after_cleanup=process['owned_live_after_cleanup'])
    for p in [*attempt.iterdir(), *(attempt / 'process').iterdir()]:
        if p.is_file(): files['execution-intake/' + p.relative_to(attempt).as_posix()] = p
    # Explicitly retain the interrupted empty audit as an observation, without inventing a cause/result.
    history = BASE / 'captured-checkpoint-history-2'; assert not history.exists(); history.mkdir()
    save(history / 'interruption.json', dict(path='scientific-full-audit-1', observed_empty=True,
        completion_present=False, result_admitted=False,
        note='WSL was observed restarted before the result could be recovered. A separate durable retry records a missing packaged Java dependency. No cause or success is imputed to the interrupted attempt.'))
    files['history/interruption.json'] = history / 'interruption.json'
    predecessor = BASE / 'unpublished-captured-checkpoint-1'
    save(history / 'unpublished-checkpoint.json', dict(validation=pin(predecessor / 'VALIDATION.json'),
        archive=pin(predecessor / 'controls.zip'), preserved_local_path=predecessor.relative_to(ROOT).as_posix(),
        reason='Preserved before fixing two developer validators to return exit2 and write required-action warnings on failure. Scientific artifacts unchanged. Their successful controls and failure-exit controls were rerun.'))
    files['history/unpublished-checkpoint.json'] = history / 'unpublished-checkpoint.json'
    archive = DEST / 'controls.zip'; identities = {}; directories = set()
    for name in files:
        parent = Path(name).parent
        while parent.as_posix() != '.': directories.add(parent.as_posix() + '/'); parent = parent.parent
    # Preserve empty directories that form part of complete small control attempts.
    for folder in folders:
        if not folder.exists(): continue
        for d in folder.rglob('*'):
            parts = d.relative_to(folder).parts
            if any(s.startswith('classes') or s.endswith('-views') or s in ['named-inputs', 'original-complete-attempt'] for s in parts): continue
            if d.is_dir(): directories.add('evidence/' + d.relative_to(BASE).as_posix() + '/')
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(directories):
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.external_attr = 0o40755 << 16; z.writestr(item, b'')
        for name, path in sorted(files.items()):
            identity = pin(path); identities[name] = identity
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16
            z.writestr(item, path.read_bytes())
    proof.update(artifacts=artifacts, builds=builds, source_pins=source_pins,
        archive=pin(archive), archive_files=identities, archive_directories=sorted(directories),
        predecessor_proof=pin(ROOT / 'docs/literature/nfcore-engine-development/VALIDATION.json'))
    save(DEST / 'VALIDATION.json', proof)
    (DEST / '.gitattributes').write_text('* -text\n', encoding='utf8', newline='\n')
    (DEST / 'README.md').write_text('# Captured nf-core development checkpoint\n\n**No full nf-core integration or scientific admission; zero new tool credit.**\n\nRead [the guide and required actions](../NFCORE_CAPTURED_COMPONENTS.md). Both compiler variants retain the frozen foundation. Scientific audit JARs additionally contain exact existing Java readers and their dependencies, native compression libraries and original notices from the unchanged canonical distribution.\n\n`controls.zip` retains complete small controls, source snapshots, comparison reports, failed attempts and large-workflow metadata. Large original read/image/BAM/work trees remain local; execution-intake metadata is not a standalone workflow bundle. Do not present it as one.\n\nRun `python tools/literature/verify_nfcore_captured_checkpoint.py` from the repository root before use. SHA256SUMS covers the four JARs, evidence ZIP and validation record.\n', encoding='utf8', newline='\n')
    (DEST / 'SHA256SUMS').write_text(''.join(pin(DEST / n)['sha256'] + '  ' + n + '\n' for n in [*artifacts, 'controls.zip', 'VALIDATION.json']), encoding='utf8', newline='\n')
    assert all(pin(ROOT / n) == v for n, v in source_pins.items())
    print('Frozen', len(files), 'evidence files;', len(artifacts), 'development JARs; zero nf-core credit.', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable evidence, source or claim assertions')
    main()
