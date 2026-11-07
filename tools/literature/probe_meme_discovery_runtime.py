# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run one real current-host dependency/version census; never claim scientific health from this probe."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/meme-runtime'


def linux(path):
    path = Path(path).resolve()
    return '/mnt/' + path.drive[0].lower() + path.as_posix()[2:]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact-build', type=Path, required=True)
    parser.add_argument('--build-evidence', type=Path, required=True)
    parser.add_argument('--profile', choices=['MEME_4_9_1_BASE', 'MEME_4_9_1_PATCH2', 'MEME_5_5_4', 'MEME_5_5_9'], required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--scientific-health', action='store_true')
    args = parser.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists(); out.mkdir(parents=True)
    proof = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes())
    assert proof['status'] == 'compiled-additive-MEME-workflow-candidate-not-scientifically-admitted'
    jar = ROOT / proof['artifact']['path']; build = json.loads((args.build_evidence / 'COMPLETED.json').read_bytes())
    helper = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec']
    r = subprocess.run([*helper, '/usr/bin/python3', '-c', "import tempfile;print(tempfile.mkdtemp(prefix='omicsmatic-meme-admission-',dir='/var/tmp'))"], capture_output=True, text=True, timeout=30)
    assert r.returncode == 0, r.stderr
    area = r.stdout.strip(); assert area.startswith('/var/tmp/omicsmatic-meme-admission-') and '\n' not in area
    command = [*helper, '/usr/bin/java', '-Xmx512m', '-XX:ActiveProcessorCount=2', '-cp',
               linux(args.artifact_build / 'test-classes') + ':' + linux(jar), 'org.jlinalg.cli.MemeDiscoveryAdmissionProbe',
               args.profile, build['prefix'], linux(args.build_evidence), area + '/runtime']
    if args.scientific_health: command.append('health')
    record = dict(command=command, linux_area=area, profile=args.profile, artifact=proof['artifact'],
                  status='dependency-version-probes-only-not-scientific-admission', implementation_credit=False)
    (out / 'STARTED.json').write_text(json.dumps(record, indent=2) + '\n')
    with (out / 'stdout.log').open('xb') as stdout, (out / 'stderr.log').open('xb') as stderr:
        result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=1800)
    record['exit_code'] = result.returncode
    (out / 'TERMINAL.json').write_text(json.dumps(record, indent=2) + '\n')
    # Files are copied as evidence, never treated as a current-host ticket.
    source = Path('\\\\wsl.localhost\\JLinAlg-Omics-SAIGE-20261026') / area.lstrip('/').replace('/', '\\')
    try:
        if source.is_dir(): shutil.copytree(source, out / 'captured')
    except OSError as copy_failure:
        (out / 'CAPTURE-WARNING.txt').write_text(str(copy_failure) + '\nOriginal native evidence remains at ' + area + '\n')
    print(args.profile + ' dependency/version preparation exit ' + str(result.returncode), flush=True)
    if result.returncode:
        print((out / 'stderr.log').read_text()[-4000:], flush=True)
        raise SystemExit(result.returncode)
    assert ('scientific_health=true' if args.scientific_health else 'scientific_health=false') in (out / 'stdout.log').read_text()


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable original runtime probe checks')
    main()
