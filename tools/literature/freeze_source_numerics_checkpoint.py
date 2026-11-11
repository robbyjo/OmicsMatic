# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native numerical references and replay both methods using only the small packaged library."""
import hashlib, json, shutil, subprocess, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux

DEST = ROOT / 'docs/literature/source-numerics-development'


def main():
    assert not DEST.exists()
    predecessor = ROOT / 'docs/literature/nfcore-consensus-development/VALIDATION.json'; old = json.loads(predecessor.read_bytes())
    assert all(pin(ROOT / n) == v for n, v in old['source_pins'].items()) and all(pin(ROOT / n) == v for n, v in old['catalog_unchanged'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == old['canonical_executable_unchanged']
    lowess = json.loads((BASE / 'lowess-controls-2/COMPLETED.json').read_bytes()); filtering = json.loads((BASE / 'independent-filter-controls-2/COMPLETED.json').read_bytes())
    assert lowess['status'] == 'PASS_NATIVE_LOWESS_AGAINST_ORIGINAL_R362_ONLY' and len(lowess['cases']) == 17 and len(lowess['platforms']) == 6
    assert filtering['status'] == 'PASS_COMPLETE_NATIVE_DESEQ126_DEFAULT_FILTERING_ONLY' and len(filtering['cases']) == 12 and len(filtering['platforms']) == 6
    assert all(pin(ROOT / n) == v for data in [lowess, filtering] for n, v in data['source_pins'].items())
    junit = dict(tests=0, failures=0, errors=0, skipped=0); suites = {}
    for p in (BASE / 'numeric-junit-1/build/test-results/test').glob('TEST-*.xml'):
        root = ET.parse(p).getroot(); values = {k: int(root.attrib.get(k, 0)) for k in junit}; suites[p.name] = values
        for k in junit: junit[k] += values[k]
    assert junit == dict(tests=32, failures=0, errors=0, skipped=0)
    assert json.loads((BASE / 'numeric-junit-1/process.json').read_bytes())['exit_code'] == 0
    names = ['src/main/java/org/jlinalg/loess/RLowess.java', 'src/main/java/org/jlinalg/sequence/Deseq2IndependentFiltering.java', 'src/main/java/org/jlinalg/sequence/Deseq2SizeFactors.java',
        'src/test/java/org/jlinalg/loess/RLowessOriginalTest.java', 'src/test/java/org/jlinalg/sequence/Deseq2IndependentFilteringOriginalTest.java',
        'tools/literature/java/RLowessControl.java', 'tools/literature/java/Deseq2FilterControl.java',
        'tools/literature/validate_r_lowess.py', 'tools/literature/validate_deseq126_filter.py', 'tools/literature/build_source_numerics.py',
        'tools/literature/freeze_source_numerics_checkpoint.py', 'tools/literature/verify_source_numerics_checkpoint.py']
    for folder in ['src/main/resources/org/jlinalg/source-numerics', 'src/test/resources/org/jlinalg/loess/r362-lowess', 'src/test/resources/org/jlinalg/sequence/deseq126-filter']:
        names.extend(p.relative_to(ROOT).as_posix() for p in sorted((ROOT / folder).iterdir()) if p.is_file())
    source_pins = {n: pin(ROOT / n) for n in names}; files = {'sources/' + n: ROOT / n for n in names}; directories = set()
    DEST.mkdir(); artifacts = {}; builds = {}
    for compiler in ['21', '25']:
        folder = BASE / ('numerics-build' + compiler + '-1'); build = json.loads((folder / 'COMPLETED.json').read_bytes())
        assert build['status'] == 'PASS_SOURCE_SPECIFIC_NATIVE_NUMERICS_BUILD_ONLY' and build['tested_method_classes_byte_identical']
        assert all(pin(ROOT / n) == v for n, v in build['source_pins'].items())
        artifact = folder / 'source-numerics-development.jar'; assert pin(artifact) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        name = 'source-numerics-javac' + compiler + '.jar'; shutil.copyfile(artifact, DEST / name); artifacts[name] = pin(DEST / name); builds[compiler] = build
    folders = [BASE / n for n in ['lowess-controls-1', 'lowess-controls-2', 'independent-filter-controls-1', 'independent-filter-controls-2', 'numerics-build21-1', 'numerics-build25-1']]
    for folder in folders:
        for p in folder.rglob('*'):
            if any(s.startswith('classes') for s in p.relative_to(folder).parts): continue
            name = 'evidence/' + p.relative_to(BASE).as_posix()
            if p.is_dir(): directories.add(name + '/')
            elif p.is_file() and p.suffix not in ['.jar', '.class']:
                assert not p.is_symlink(); files[name] = p
    for p in (BASE / 'numeric-junit-1').iterdir():
        if p.is_file(): files['focused-junit/' + p.name] = p
    for p in (BASE / 'numeric-junit-1/build/test-results/test').glob('TEST-*.xml'): files['focused-junit/' + p.name] = p
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
    packet = BASE / 'numerics-packaged-controls-1'; assert not packet.exists(); packet.mkdir()
    with zipfile.ZipFile(archive) as z: z.extractall(packet / 'archive')
    classes = packet / 'classes'; classes.mkdir()

    def run(label, command):
        command = list(map(str, command)); result = subprocess.run(command, capture_output=True, stdin=subprocess.DEVNULL, timeout=180)
        (packet / (label + '.stdout')).write_bytes(result.stdout); (packet / (label + '.stderr')).write_bytes(result.stderr)
        save(packet / (label + '.process.json'), dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, (label, result.stderr)
        return result.stdout

    helper_dir = packet / 'archive/sources/tools/literature/java'
    run('javac-helpers', [JAVA / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', DEST / 'source-numerics-javac21.jar', '-d', classes,
        helper_dir / 'RLowessControl.java', helper_dir / 'Deseq2FilterControl.java'])
    checks = []
    for compiler in ['21', '25']:
        jar = DEST / ('source-numerics-javac' + compiler + '.jar')
        for platform, vm in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe'), ('linux21', None)]:
            for method, class_name, folder, expected in [('lowess', 'org.jlinalg.loess.RLowessControl', 'lowess-controls-2', b'cases=17'),
                    ('filter', 'org.jlinalg.sequence.Deseq2FilterControl', 'independent-filter-controls-2', b'populations=12')]:
                root = packet / 'archive/evidence' / folder; label = compiler + '-' + platform + '-' + method
                base = [vm, '-cp', str(classes) + ';' + str(jar), class_name] if vm else ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-cp', linux(classes) + ':' + linux(jar), class_name]
                run(label + '-portable', [*base, '--portable'])
                result = run(label, [*base, root if vm else linux(root)]); assert expected in result
                checks.append(dict(compiler=compiler, platform=platform, method=method, exit_code=0, complete_reference=True))
    packaged = dict(status='PASS_TWELVE_PACKAGED_NATIVE_REFERENCE_REPLAYS_ONLY', checks=checks, external_R_or_native_runtime_used=False)
    save(packet / 'COMPLETED.json', packaged)
    proof = dict(status='PASS_NATIVE_LOWESS_AND_DEFAULT_FILTER_ONLY',
        claims=dict(new_tool_credit=0, total_new_integrations=99, native=19, upstream_adapters=80, including_three_existing=102,
            missing=244, nfcore_status='missing', full_nfcore_adapter=False, fitted_model_admitted=False, whole_pipeline_admitted=False,
            biological_accuracy_certified=False, whole_paper_reproduction=False, whole_goal_complete=False),
        lowess=lowess, filtering=filtering, junit=dict(**junit, suites=suites, scope='Selected new numerical source/tests and unchanged NA-representation dependency only'),
        packaged=packaged, builds=builds, artifacts=artifacts, archive=pin(archive), archive_files=identities, archive_directories=sorted(directories),
        source_pins=source_pins, predecessor_proof=pin(predecessor), catalog_unchanged=old['catalog_unchanged'], canonical_executable_unchanged=old['canonical_executable_unchanged'])
    save(DEST / 'VALIDATION.json', proof)
    (DEST / '.gitattributes').write_text('* -text\n', encoding='utf8', newline='\n')
    (DEST / 'README.md').write_text('# Source-specific native numerical library\n\nRead [the API guide, source alpha/precision warnings and required actions](../SOURCE_NUMERICS.md). This is a small Java classpath library without an executable entrypoint. R and external package/native binaries are unnecessary for native computation. Corresponding Java source, provenance and license notices are embedded.\n\nThe evidence ZIP retains complete original curves/filter families, round-trip inputs, source snapshots, prior failures and focused JUnit reports. Original R3.6.2/DESeq2 1.26.0 dependencies are needed only to repeat upstream generation. Full nf-core/model/biological admission and the broader goal remain pending; zero new catalog-tool credit.\n\nRun `python tools/literature/verify_source_numerics_checkpoint.py` from the repository root before use.\n', encoding='utf8', newline='\n')
    (DEST / 'SHA256SUMS').write_text(''.join(pin(DEST / n)['sha256'] + '  ' + n + '\n' for n in [*artifacts, 'controls.zip', 'VALIDATION.json']), encoding='utf8', newline='\n')
    assert all(pin(ROOT / n) == v for n, v in source_pins.items())
    print('Frozen source numerics:', len(files), 'evidence files;32 focused JUnit tests;12 packaged replays;0 tool credit.', flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source-specific numeric, package or scope checks')
    main()
