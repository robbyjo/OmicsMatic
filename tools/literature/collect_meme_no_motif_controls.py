# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture real original no-motif behavior after other live runtime censuses finish; install nothing."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, pin, linux
from run_meme_discovery_workflows import PROFILES


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); base = ROOT / 'build/literature-review/meme-runtime'; out = args.out.resolve()
    assert out.is_relative_to(base) and not out.exists(); out.mkdir()
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
    source = ROOT / 'tools/literature/java/MemeNoMotifSourceTrace.java'
    bound = {p.relative_to(ROOT).as_posix(): pin(p) for p in [source, Path(__file__)]}
    for p in [source, Path(__file__)]:
        target = out / 'source-snapshot' / p.relative_to(ROOT); target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(p, target)
    classes = out / 'classes'; classes.mkdir()
    r = subprocess.run([str(JAVA21 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), str(source)], capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(r.stdout); (out / 'compile.stderr').write_bytes(r.stderr)
    assert r.returncode == 0, r.stderr.decode('utf8', errors='replace')
    profiles = []
    for profile, folder in PROFILES.items():
        evidence = base / folder; record = json.loads((evidence / 'COMPLETED.json').read_bytes())
        profiles.append(dict(profile=profile, home=record['prefix'], evidence=linux(evidence)))
    save(out / 'plan.json', dict(profiles=profiles))
    argv = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java', '-Xmx768m', '-XX:ActiveProcessorCount=2',
        '-cp', linux(classes) + ':' + linux(jar), 'org.jlinalg.cli.MemeNoMotifSourceTrace', linux(out / 'plan.json'), linux(out)]
    save(out / 'STARTED.json', dict(command=argv, artifact=build['artifact'], source_pins=bound, analysis_admitted=False))
    with (out / 'stdout.log').open('xb') as a, (out / 'stderr.log').open('xb') as b:
        r = subprocess.run(argv, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=900)
    save(out / 'TERMINAL.json', dict(command=argv, exit_code=r.returncode)); assert r.returncode == 0, (out / 'stderr.log').read_text(encoding='utf8')
    result = json.loads((out / 'SOURCE_TRACES.json').read_bytes()); area = result['native_area']; assert area.startswith('/var/tmp/omicsmatic-meme-empty-source-')
    native = Path('\\\\wsl.localhost\\JLinAlg-Omics-SAIGE-20261026') / area.lstrip('/').replace('/', '\\')
    shutil.copytree(native, out / 'captured-native')
    assert all(pin(ROOT / n) == wanted for n,wanted in bound.items())
    save(out / 'COMPLETED.json', dict(status='captured-four-original-zero-motif-source-behavior-controls', artifact=build['artifact'], source_pins=bound, profiles=len(result['cases']),
        original_exits=[r.get('original_exit_code') for r in result['cases']], original_motif_counts=[r.get('reported_motifs') for r in result['cases']], public_runtime_or_analysis_admitted=False))
    print(json.dumps(dict(profiles=len(result['cases']), original_exits=[r.get('original_exit_code') for r in result['cases']], motif_counts=[r.get('reported_motifs') for r in result['cases']])))


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable original source/trace bindings')
    main()
