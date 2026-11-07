# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented synthetic prediction request with only resolved file/output paths changed."""
import argparse
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, pin, save
from validate_mashr_native import JAVA21
from validate_cicero_prediction import audit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir()
    build = json.loads((args.build / 'COMPLETED.json').read_bytes())
    artifact = build['artifact']
    jar = ROOT / artifact['path']
    example = ROOT / 'examples/cicero-prediction-subset'
    paths = [Path(__file__).resolve(), ROOT / 'tools/literature/validate_cicero_prediction.py', *[p for p in example.glob('*') if p.is_file()]]
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    for name in pins:
        p = out / 'source-snapshot' / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes((ROOT / name).read_bytes())
    request = json.loads((example / 'request.json').read_bytes())
    for key in ['predictions', 'peak_metadata', 'accessible_peaks']:
        request[key] = str(example / request[key])
    request['out'] = str(out / 'result')
    save(out / 'request.json', request)
    command = [str(JAVA21 / 'java.exe'), '-Xmx256m', '-cp', str(jar), 'org.jlinalg.cli.CiceroPredictionCli', '--request', str(out / 'request.json')]
    with (out / 'example.stdout').open('xb') as a, (out / 'example.stderr').open('xb') as b:
        run = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=a, stderr=b, timeout=60)
    save(out / 'process.json', dict(command=command, exit_code=run.returncode))
    assert run.returncode == 0
    result = audit(out / 'result')
    assert result['decisions'] == 9 and result['retained'] == 3
    assert all(pin(ROOT / n) == v for n, v in [*pins.items(), *build['source_pins'].items()])
    assert pin(jar) == {k: artifact[k] for k in ['bytes', 'sha256']}
    save(out / 'COMPLETED.json', dict(status='passed-executed-Cicero-prediction-example', artifact=artifact, source_pins=pins,
        comparison=result, implementation_credit=False, whole_goal_complete=False))
    print('Executed example: nine memberships, three retained rows, all original bytes preserved')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable example/source checks')
    main()
