# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check prominent real Windows runtime skips and copied-bundle scientific/receipt corruption refusals."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, pin


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


def remint(root):
    capture_path = root / 'NATIVE-CAPTURE.json'
    if capture_path.exists():
        capture = json.loads(capture_path.read_bytes()); previous = capture['files']; captured = {'.': dict(kind='directory', mode=previous['.']['mode'])}
        for p in sorted((root / 'native').rglob('*')):
            name = p.relative_to(root / 'native').as_posix(); mode = previous.get(name, {}).get('mode', 493 if p.is_dir() else 420)
            captured[name] = dict(kind='directory', mode=mode) if p.is_dir() else dict(kind='file', mode=mode, **pin(p))
        capture['files'] = captured; save(capture_path, capture)
    path = root / 'manifest.json'; manifest = json.loads(path.read_bytes()); files = {'.': dict(kind='directory')}
    for p in sorted(root.rglob('*')):
        name = p.relative_to(root).as_posix()
        if name in {'manifest.json', 'COMPLETED.json'}: continue
        assert not p.is_symlink()
        files[name] = dict(kind='directory') if p.is_dir() else dict(kind='file', **pin(p))
    manifest['files'] = files; save(path, manifest)
    complete = json.loads((root / 'COMPLETED.json').read_bytes()); complete['manifest'] = pin(path); save(root / 'COMPLETED.json', complete)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--bundle', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); base = ROOT / 'build/literature-review/meme-runtime'
    assert out.is_relative_to(base) and not out.exists(); out.mkdir()
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); records = []
    jvms = [('windows-java21', JAVA21 / 'java.exe'), ('windows-java25', 'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')]
    def invoke(folder, label, operation, java, expected):
        folder.mkdir(exist_ok=True); source = folder / (label + '.json'); save(source, operation)
        argv = [str(java), '-Xmx768m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-cp', str(jar), 'org.jlinalg.cli.MemeToolsCli', '--request', str(source)]
        r = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
        (folder / (label + '.stdout')).write_bytes(r.stdout); (folder / (label + '.stderr')).write_bytes(r.stderr)
        record = dict(label=label, command=argv, expected_exit=expected, exit_code=r.returncode); records.append(record); save(folder / (label + '.process.json'), record)
        assert r.returncode == expected, (label, r.returncode, r.stderr.decode('utf8', errors='replace'))
        if expected: assert b'REQUIRED ACTION:' in r.stderr and (b'SKIP3:' if expected == 3 else b'FAIL2:') in r.stderr
    for label, java in jvms:
        folder = out / ('required-Linux-runtime-' + label); folder.mkdir()
        raw = json.loads(MEME_TEMPLATE)
        raw['primary'] = str(ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery/health-primary.fa')
        raw['background'] = str(ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery/health-background.bg')
        save(folder / 'recipe.json', raw)
        result = folder / 'result'
        invoke(folder, 'discover', dict(schema_version=1, operation='DISCOVER', recipe=str(folder / 'recipe.json'), runtime_home=str(folder / 'missing-home'), build_evidence=str(folder / 'missing-build-evidence'), out=str(result)), java, 3)
        failure = json.loads((result / 'FAILURE.json').read_bytes()); assert failure['status'] == 'SKIP3_ANALYSIS_SKIPPED' and not failure['analysis_admitted']
        assert not (result / 'COMPLETED.json').exists() and 'REQUIRED ACTION:' in (result / 'WARNINGS.md').read_text(encoding='utf8')
        invoke(folder, 'refuse-failed-bundle', dict(schema_version=1, operation='VERIFY', bundle=str(result)), java, 2)
    if args.bundle:
        bundle = args.bundle.resolve(); assert bundle.is_relative_to(base) and (bundle / 'COMPLETED.json').is_file()
        def jchange(path, change):
            value = json.loads(path.read_bytes()); change(value); save(path, value)
        mutations = [
            ('original-command', 'native/fit/process.json', lambda p: jchange(p, lambda d: d['command'].append('--unrequested'))),
            ('process-limit', 'native/fit/process.json', lambda p: jchange(p, lambda d: d['limits'].__setitem__('wallMillis', d['limits']['wallMillis'] + 1))),
            ('owned-live-worker', 'native/fit/process.json', lambda p: jchange(p, lambda d: d.__setitem__('owned_live_after_cleanup', [999999]))),
            ('source-exit-failure', 'native/fit/process.json', lambda p: jchange(p, lambda d: d.__setitem__('exit_code', 1))),
            ('request-model', 'native/prepared/request.json', lambda p: jchange(p, lambda d: d.__setitem__('occurrence', 'ANR'))),
            ('raw-input', 'native/prepared/inputs/primary.fa', lambda p: p.write_bytes(p.read_bytes().replace(b'ACGT', b'TGCA', 1))),
            ('hidden-warning', 'WARNINGS.md', lambda p: p.write_text('All warnings suppressed.\n', encoding='utf8')),
            ('host-profile', 'native/runtime/metadata/host.json', lambda p: jchange(p, lambda d: d.__setitem__('profile', 'UNKNOWN'))),
            ('health-not-passed', 'native/runtime/metadata/host.json', lambda p: jchange(p, lambda d: d.__setitem__('fresh_scientific_health_passed', False))),
            ('build-prefix', 'native/runtime/metadata/build/BUILD.json', lambda p: jchange(p, lambda d: d.__setitem__('compiled_prefix', '/different/home'))),
            ('health-recipe', 'native/runtime/health/request.json', lambda p: jchange(p, lambda d: d.__setitem__('dirichlet_beta', 20))),
            ('health-raw-input', 'native/runtime/health/original-primary.fa', lambda p: p.write_bytes(p.read_bytes() + b'\n')),
            ('HTML-replay', 'native/render/replayed.html', lambda p: p.write_bytes(p.read_bytes() + b'\n<!--changed-->\n')),
            ('PNG-CRC', 'native/fit/result/logo1.png', lambda p: p.write_bytes(p.read_bytes()[:40] + bytes([p.read_bytes()[40] ^ 1]) + p.read_bytes()[41:])),
            ('EPS-terminator', 'native/fit/result/logo1.eps', lambda p: p.write_bytes(p.read_bytes() + b'\nchanged after EOF\n')),
            ('unknown-output', 'native/fit/result/extra.txt', lambda p: p.write_text('undeclared output\n', encoding='utf8')),
            ('text-score-metadata', 'native/fit/result/meme.txt', lambda p: p.write_bytes(re.sub(rb'(log-odds matrix:[^\r\n]*n=\s*)[0-9]+', rb'\g<1>1', p.read_bytes(), count=1))),
            ('false-completion', 'COMPLETED.json', lambda p: jchange(p, lambda d: d.__setitem__('analysis_admitted', False))),
        ]
        for label, member, mutate in mutations:
            folder = out / label; folder.mkdir(); copied = folder / 'bundle'; shutil.copytree(bundle, copied)
            target = copied / member; before = target.read_bytes() if target.exists() else None; mutate(target)
            assert not target.exists() or target.read_bytes() != before, 'Ineffective corruption control: ' + label
            remint(copied)
            for jvm, java in jvms: invoke(folder, jvm, dict(schema_version=1, operation='VERIFY', bundle=str(copied)), java, 2)
    assert pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']} and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    save(out / 'COMPLETED.json', dict(status='passed-Windows-MEME-recovery-and-bundle-refusal-controls', artifact=build['artifact'], records=records,
        expected_missing_runtime_skips=2, refused_failed_bundles=2, reminted_corruption_refusals=len(records) - 4,
        source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))}, scientific_result_admitted=False, implementation_credit=False))
    print('Passed ' + str(len(records)) + ' expected skips/refusals with prominent required actions', flush=True)


MEME_TEMPLATE = '''{"schema_version":1,"profile":"MEME_5_5_9","primary":"","background":"","occurrence":"ZOOPS","both_strands":true,"motifs":2,"minimum_width":10,"maximum_width":10,"seed":17,"search_size":0,"dirichlet_beta":0.01,"maximum_iterations":50,"convergence_distance":0.00001,"maximum_motif_evalue":null,"minimum_sites":null,"maximum_sites":null,"limits":{}}'''

if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable refusal controls')
    main()
