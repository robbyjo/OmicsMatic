# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise root MEME routing, real copied bundles, prominent runtime skips and existing-output protection on Windows."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, pin


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


def files(root):
    return {p.relative_to(root).as_posix(): pin(p) for p in root.rglob('*') if p.is_file()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True); parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); base = ROOT / 'build/literature-review/meme-runtime'
    assert out.is_relative_to(base) and not out.exists(); out.mkdir()
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']; bundle = args.bundle.resolve()
    assert pin(jar) == {k: build['artifact'][k] for k in ['bytes','sha256']} and (bundle / 'COMPLETED.json').is_file()
    original = files(bundle); canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); results = []
    for jvm, java in [('windows-java21', JAVA21 / 'java.exe'), ('windows-java25', 'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')]:
        folder = out / jvm; folder.mkdir()
        def run(label, tail, expected):
            command = [str(java), '-Xmx768m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-jar', str(jar), 'meme-tools', *tail]
            r = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
            (folder / (label + '.stdout')).write_bytes(r.stdout); (folder / (label + '.stderr')).write_bytes(r.stderr)
            result = dict(JVM=jvm, label=label, command=command, expected_exit=expected, exit_code=r.returncode); results.append(result)
            save(folder / (label + '.process.json'), result)
            assert r.returncode == expected, (label,r.returncode,r.stderr.decode('utf8',errors='replace'))
            if expected: assert b'REQUIRED ACTION:' in r.stderr and (b'SKIP3:' if expected == 3 else b'FAIL2:') in r.stderr
            return r
        help_result = run('help',['--help'],0); assert b'DISCOVER' in help_result.stdout and b'VERIFY' in help_result.stdout
        profile_result = run('profiles',['--profiles'],0); assert set(profile_result.stdout.decode().split()) == {'MEME_4_9_1_BASE','MEME_4_9_1_PATCH2','MEME_5_5_4','MEME_5_5_9'}
        template = json.loads(run('legacy-template',['--template','MEME_4_9_1_PATCH2'],0).stdout); assert template['seed'] is None and template['search_size'] is None
        save(folder / 'verify.json',dict(schema_version=1,operation='VERIFY',bundle=str(bundle)))
        assert b'VERIFIED_CAPTURED_MEME_DISCOVERY' in run('verify-real-result',['--request',str(folder / 'verify.json')],0).stdout
        run('unknown-option',['--arbitrary-command'],2)
        recipe = json.loads(run('modern-template',['--template','MEME_5_5_9'],0).stdout)
        resource = ROOT / 'src/main/resources/org/jlinalg/omics/meme-discovery'
        recipe.update(primary=str(resource / 'health-primary.fa'),background=str(resource / 'health-background.bg'),minimum_width=10,maximum_width=10)
        save(folder / 'recipe.json',recipe)
        operation = dict(schema_version=1,operation='DISCOVER',recipe=str(folder / 'recipe.json'),runtime_home=str(folder / 'missing-home'),build_evidence=str(folder / 'missing-build'),out=str(folder / 'skipped-result'))
        save(folder / 'discover.json',operation); run('missing-required-Linux-runtime',['--request',str(folder / 'discover.json')],3)
        failure = json.loads((folder / 'skipped-result/FAILURE.json').read_bytes()); assert not failure['analysis_admitted'] and failure['status']=='SKIP3_ANALYSIS_SKIPPED'
        operation['out'] = str(bundle); save(folder / 'existing-output.json',operation)
        run('existing-output-refusal',['--request',str(folder / 'existing-output.json')],2)
        assert files(bundle) == original
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    save(out / 'COMPLETED.json',dict(status='passed-Windows-root-MEME-routes-positive-VERIFY-skips-and-preserved-existing-output',artifact=build['artifact'],
        cases=len(results),records=results,positive_portable_verifications=2,expected_runtime_skips=2,preserved_existing_output_refusals=2,
        canonical_executable_unchanged=canonical,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},original_fitting_in_this_test=False,implementation_credit=False))
    print('Passed '+str(len(results))+' root cases; real bundles unchanged; all required diagnostics prominent')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable root dispatch/source controls')
    main()
