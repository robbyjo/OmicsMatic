# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original consensus controls and replay the packaged native calculation after extraction."""
import hashlib, json, shutil, subprocess, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux

DEST = ROOT / 'docs/literature/nfcore-consensus-development'


def main():
    assert not DEST.exists()
    old_dir = ROOT / 'docs/literature/nfcore-captured-development'; old = json.loads((old_dir / 'VALIDATION.json').read_bytes())
    assert all(pin(ROOT / n) == v for n, v in old['source_pins'].items())
    assert all(pin(ROOT / n) == v for n, v in old['catalog_unchanged'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == old['canonical_executable_unchanged']
    controls = json.loads((BASE / 'consensus-controls-1/COMPLETED.json').read_bytes())
    cli = json.loads((BASE / 'consensus-cli-controls-2/COMPLETED.json').read_bytes())
    assert controls['status'] == 'PASS_COMPLETE_NATIVE_CONSENSUS_AGAINST_ORIGINAL_TOOLS_ONLY'
    assert len(controls['cases']) == 12 and all(len(r['checks']) == 6 and all(c['passed'] for c in r['checks']) for r in controls['cases'])
    assert controls['original_full_peak_population_replayed_byte_exactly']
    assert cli['status'] == 'PASS_PACKAGED_COMPLETE_NATIVE_CONSENSUS_AUDITS_ONLY' and len(cli['controls']) == 10
    assert cli['positive_platforms'] == 4 and cli['refusals'] == 6 and cli['coordinate_warning_prominent']
    assert all(pin(ROOT / n) == v for p in [controls, cli] for n, v in p['source_pins'].items())
    DEST.mkdir(); artifacts = {}; builds = {}
    for compiler in ['21', '25']:
        folder = BASE / ('consensus-build' + compiler + '-2'); build = json.loads((folder / 'COMPLETED.json').read_bytes())
        artifact = folder / 'nfcore-consensus-development.jar'
        assert build['status'] == 'PASS_NATIVE_CONSENSUS_BUILD_ONLY' and build['tested_native_classes_byte_identical']
        assert all(pin(ROOT / n) == v for n, v in build['source_pins'].items())
        assert pin(artifact) == cli['artifacts'][compiler] == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        name = 'nfcore-consensus-javac' + compiler + '.jar'; shutil.copyfile(artifact, DEST / name)
        artifacts[name] = pin(DEST / name); builds[compiler] = build
    names = ['src/main/java/org/jlinalg/workflow/NfcoreChipseq121Consensus.java', 'src/main/java/org/jlinalg/cli/NfcoreConsensusAuditCli.java',
        'tools/literature/java/NfcoreConsensusControl.java', 'tools/literature/build_nfcore_consensus.py',
        'tools/literature/validate_nfcore_consensus.py', 'tools/literature/validate_nfcore_consensus_cli.py',
        'tools/literature/freeze_nfcore_consensus_checkpoint.py', 'tools/literature/verify_nfcore_consensus_checkpoint.py']
    source_pins = {n: pin(ROOT / n) for n in names}; files = {'sources/' + n: ROOT / n for n in names}; directories = set()
    for folder in [BASE / n for n in ['consensus-controls-1', 'consensus-cli-controls-1', 'consensus-cli-controls-2',
            'consensus-build21-1', 'consensus-build25-1', 'consensus-build21-2', 'consensus-build25-2']]:
        for p in folder.rglob('*'):
            if any(s.startswith('classes') for s in p.relative_to(folder).parts): continue
            name = 'evidence/' + p.relative_to(BASE).as_posix()
            if p.is_dir(): directories.add(name + '/')
            elif p.is_file() and p.suffix not in ['.jar', '.class']:
                assert not p.is_symlink(); files[name] = p
    for name in files:
        parent = Path(name).parent
        while parent.as_posix() != '.': directories.add(parent.as_posix() + '/'); parent = parent.parent
    archive = DEST / 'controls.zip'; identities = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(directories):
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.external_attr = 0o40755 << 16; z.writestr(item, b'')
        for name, p in sorted(files.items()):
            raw = p.read_bytes(); identities[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16; z.writestr(item, raw)
    packet = BASE / 'consensus-checkpoint-packaged-1'; assert not packet.exists(); packet.mkdir()
    with zipfile.ZipFile(archive) as z: z.extractall(packet / 'archive')
    classes = packet / 'classes'; classes.mkdir(); rows = []

    def run(label, command):
        command = list(map(str, command))
        result = subprocess.run(command, capture_output=True, stdin=subprocess.DEVNULL, timeout=90)
        (packet / (label + '.stdout')).write_bytes(result.stdout); (packet / (label + '.stderr')).write_bytes(result.stderr)
        save(packet / (label + '.process.json'), dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, (label, result.stderr)
        return result.stdout

    helper = packet / 'archive/sources/tools/literature/java/NfcoreConsensusControl.java'
    run('javac-helper', [JAVA / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', DEST / 'nfcore-consensus-javac21.jar', '-d', classes, helper])
    case = packet / 'archive/evidence/consensus-controls-1/complete-original-3357-peaks'
    for compiler in ['21', '25']:
        jar = DEST / ('nfcore-consensus-javac' + compiler + '.jar')
        for platform, vm in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
            output = run(compiler + '-' + platform, [vm, '-cp', str(jar) + ';' + str(classes), 'org.jlinalg.workflow.NfcoreConsensusControl', case, 'BROAD', '1'])
            assert b'rows=1472 source_peaks=3357' in output; rows.append(dict(compiler=compiler, platform=platform, exit_code=0))
        output = run(compiler + '-linux21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-cp', linux(jar) + ':' + linux(classes), 'org.jlinalg.workflow.NfcoreConsensusControl', linux(case), 'BROAD', '1'])
        assert b'rows=1472 source_peaks=3357' in output; rows.append(dict(compiler=compiler, platform='linux21', exit_code=0))
    package = dict(status='PASS_SIX_EXTRACTED_PACKAGED_COMPLETE_NATIVE_CONSENSUS_COMPARISONS', checks=rows, source_peaks=3357, retained_intervals=1472)
    save(packet / 'COMPLETED.json', package)
    proof = dict(status='PASS_NATIVE_ORIGINAL_CONSENSUS_COMPONENT_ONLY',
        claims=dict(new_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80, including_three_existing=102,
            missing=244, nfcore_status='missing', full_nfcore_adapter=False, scientific_result_admitted=False,
            biological_accuracy_certified=False, whole_paper_reproduction=False, whole_goal_complete=False),
        controls=controls, cli=cli, packaged=package, builds=builds, artifacts=artifacts, archive=pin(archive),
        archive_files=identities, archive_directories=sorted(directories), source_pins=source_pins,
        predecessor_proof=pin(old_dir / 'VALIDATION.json'), predecessor_artifact=pin(old_dir / 'nfcore-chipseq121-audit-javac21.jar'),
        catalog_unchanged=old['catalog_unchanged'], canonical_executable_unchanged=old['canonical_executable_unchanged'])
    save(DEST / 'VALIDATION.json', proof)
    (DEST / '.gitattributes').write_text('* -text\n', encoding='utf8', newline='\n')
    (DEST / 'README.md').write_text('# Original native consensus checkpoint\n\n**Full nf-core remains pending; zero additional tool credit.** Read [the guide, coordinate warning and required actions](../NFCORE_CONSENSUS.md). The two JARs retain every predecessor member except the entrypoint manifest and add the native consensus component. Original notices remain embedded.\n\nThe ZIP contains complete synthetic and full 3357-peak control populations, original tables/scripts/notices, sources, current packaged reports and earlier diagnostic versions. Large original reads/image/workflow objects remain local. Run `python tools/literature/verify_nfcore_consensus_checkpoint.py` from the repository root before use.\n', encoding='utf8', newline='\n')
    (DEST / 'SHA256SUMS').write_text(''.join(pin(DEST / n)['sha256'] + '  ' + n + '\n' for n in [*artifacts, 'controls.zip', 'VALIDATION.json']), encoding='utf8', newline='\n')
    assert all(pin(ROOT / n) == v for n, v in source_pins.items())
    print('Frozen native consensus checkpoint:', len(files), 'evidence files;6 extracted complete comparisons;0 tool credit.', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete native/source, package or scope assertions')
    main()
