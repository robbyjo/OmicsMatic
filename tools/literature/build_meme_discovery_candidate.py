# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Assemble additive MEME workflow development classes/resources; preserve every validated parent entry."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, pin


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/meme-runtime').resolve()) and not out.exists(); out.mkdir(parents=True)
    proof = json.loads((ROOT / 'docs/literature/meme-discovery-sites-development/VALIDATION.json').read_bytes()); parent = ROOT / proof['artifact']['path']
    assert pin(parent) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    sources = sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('MemeDiscovery*.java'))
    optional = ROOT / 'src/main/java/org/jlinalg/cli/MemeToolsCli.java'
    if optional.exists(): sources.append(optional)
    resources = sorted(p for p in (ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery').rglob('*') if p.is_file())
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [*sources, *resources, Path(__file__)]}; write(out / 'SOURCE-PINS.json', pins)
    for name in pins:
        target = out / 'source-snapshot' / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes((ROOT / name).read_bytes())
    def run(name, argv):
        command = list(map(str, argv))
        with (out / (name + '.stdout')).open('xb') as a, (out / (name + '.stderr')).open('xb') as b:
            r = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=120)
        write(out / (name + '.json'), dict(command=command, exit_code=r.returncode)); assert r.returncode == 0, (name, (out / (name + '.stderr')).read_text())
    try:
        classes = out / 'classes'; classes.mkdir()
        run('compile', [JAVA21 / 'javac.exe', '--release', '17', '-g', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', parent, '-d', classes, *sources])
        additions = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        additions.update({p.relative_to(ROOT / 'src/main/resources').as_posix(): p.read_bytes() for p in resources})
        jar = out / 'omicsmatic-0.3.7-meme-discovery-development.jar'
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as current:
            assert set(additions).isdisjoint(old.namelist())
            for item in old.infolist(): current.writestr(item, old.read(item.filename))
            for name, raw in sorted(additions.items()): current.writestr(name, raw, compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
            assert len(current.namelist()) == len(set(current.namelist())) and current.testzip() is None
            assert all(old.read(n) == current.read(n) for n in old.namelist()) and set(current.namelist()) - set(old.namelist()) == set(additions)
            old_count = len(old.namelist())
        driver = out / 'MemeDiscoveryAdmissionProbe.java'
        driver.write_text('''package org.jlinalg.cli;
import java.nio.file.*;
public final class MemeDiscoveryAdmissionProbe {
 public static void main(String[] args)throws Exception {
  var context=MemeDiscoveryRuntime.prepare(args[0],Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),()->{});
  if(args.length==5 && args[4].equals("health"))MemeDiscoveryHealth.run(context,()->{});
  context.verify(()->{});MemeRuntimeFiles.write(context.root.resolve("metadata/host.json"),context.document());
  System.out.println("DEPENDENCY_VERSION_PREPARATION_ONLY images="+context.images.size()+" scientific_health="+context.healthy);
 }
}
''', encoding='utf-8', newline='\n')
        test = out / 'test-classes'; test.mkdir()
        run('probe-compile', [JAVA21 / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', jar, '-d', test, driver])
        assert all(pin(ROOT / n) == p for n, p in pins.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        write(out / 'COMPLETED.json', dict(status='compiled-additive-MEME-workflow-candidate-not-scientifically-admitted',
              artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), parent_artifact=proof['artifact'], source_pins=pins,
              additions={n: dict(bytes=len(b), sha256=hashlib.sha256(b).hexdigest()) for n, b in sorted(additions.items())},
              unchanged_parent_entries=old_count, canonical_executable_unchanged=canonical, implementation_credit=False))
        print('Compiled additive MEME workflow candidate with ' + str(len(additions)) + ' exact new entries', flush=True)
    except Exception as failure:
        write(out / 'FAILURE.json', dict(status='failed-MEME-workflow-candidate-build-not-admitted', error=str(failure), source_pins=pins)); raise


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable additive artifact/source checks')
    main()
