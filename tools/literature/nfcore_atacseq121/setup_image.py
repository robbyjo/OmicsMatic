#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a reproducible SIF from the original pinned OCI image with permitted private Apptainer.

Requires Linux Python, the exact separately installed private runtime and explicit
--allow-download true. SOURCE_DATE_EPOCH follows the original image creation date.
This is separate setup and performs no scientific analysis.
"""
import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import time
import traceback
from pathlib import Path

DIGEST = 'sha256:cb843b04a06abd3538f0dfc0e0e4f08c691a79e895638811096c3a94d4767d5c'
EPOCH = '1596038424'
EXPECTED = dict(bytes=1130561536, sha256='f1ccbf611070edec8a0bc774e584f728c88b4411c6d2ca2a70bbe23225a34c4c')
ACTION = ('Setup only. If downloading, installation, unprivileged namespaces or the exact image '
          'are unavailable/forbidden, skip dependent ATAC analysis and use an allowed environment. '
          'Read raw process logs, resolve the named cause and retry a NEW path. Preserve originals '
          'and failed attempts. A built image still needs source/profile and actual workflow validation.')


def save(path, value):
    path.write_text(json.dumps(value, indent=2, allow_nan=False)+'\n', encoding='utf-8', newline='\n')


def pin(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            h.update(block)
    return dict(bytes=path.stat().st_size, sha256=h.hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--container-home', type=Path, required=True)
    parser.add_argument('--allow-download', choices=['true', 'false'], default='false')
    args = parser.parse_args()
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    try:
        if sys.platform != 'linux' or platform.machine() not in ['x86_64', 'amd64']:
            raise FileNotFoundError('Require Linux x86_64, including Linux Python within WSL')
        executable = args.container_home/'bin/apptainer'
        if not executable.is_file() or not os.access(executable, os.X_OK):
            raise FileNotFoundError('Exact permitted private Apptainer executable is absent or forbidden')
        env = {key: value for key, value in os.environ.items() if not key.startswith(('APPTAINER', 'SINGULARITY'))}
        env.update(PATH='/usr/bin:/bin', LANG='C.UTF-8', LC_ALL='C.UTF-8', TZ='UTC',
                   SOURCE_DATE_EPOCH=EPOCH, APPTAINER_CACHEDIR=str(out/'cache'), APPTAINER_TMPDIR=str(out/'tmp'))
        for name in ['cache', 'tmp']:
            (out/name).mkdir()
        records = []

        def run(label, command):
            began = time.monotonic()
            save(out/(label+'.command.json'), dict(command=list(map(str, command)),
                environment={key: env[key] for key in ['SOURCE_DATE_EPOCH', 'APPTAINER_CACHEDIR', 'APPTAINER_TMPDIR']}))
            with (out/(label+'.stdout')).open('xb') as so, (out/(label+'.stderr')).open('xb') as se:
                child = subprocess.Popen(list(map(str, command)), stdin=subprocess.DEVNULL, stdout=so, stderr=se, env=env)
                save(out/'RUNNING.json', dict(stage=label, pid=child.pid))
                try:
                    child.wait(timeout=1800)
                except BaseException:
                    child.terminate()
                    try:
                        child.wait(timeout=20)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=20)
                    raise
            record = dict(stage=label, exit_code=child.returncode, seconds=round(time.monotonic()-began, 3))
            save(out/(label+'.process.json'), record)
            records.append(record)
            if child.returncode:
                message = (out/(label+'.stderr')).read_text(encoding='utf-8', errors='replace')
                if label == 'health' and any(text in message for text in ['Failed to create user namespace', 'user namespace disabled', 'Unprivileged user namespace']):
                    raise PermissionError('Operating environment forbids the required unprivileged container namespace; inspect health.stderr')
                if label == 'pull' and any(text in message for text in ['403 Forbidden', '404 Not Found', '429 Too Many Requests', 'no such host', 'connection refused', 'network is unreachable', 'i/o timeout']):
                    raise FileNotFoundError('Exact original OCI image download is unavailable/forbidden; inspect pull.stderr')
                raise RuntimeError('Setup command failed: '+label+'; inspect retained process logs')
        run('version', [executable, 'version'])
        if (out/'version.stdout').read_bytes() != b'1.5.5-1\n':
            raise ValueError('This setup qualifies exact private Apptainer1.5.5-1; no provider suffix is stripped')
        if args.allow_download != 'true':
            raise PermissionError('Downloading is not enabled. Use --allow-download true only where permitted')
        image = out/'nfcore-atacseq-1.2.1.sif'
        run('pull', [executable, 'pull', image, 'docker://index.docker.io/nfcore/atacseq@'+DIGEST])
        if pin(image) != EXPECTED:
            raise ValueError('Original reproducible image differs from the qualified bytes. Retain it and use the exact admitted runtime/build environment or qualified image cache; never edit expected hashes')
        run('inspect', [executable, 'inspect', '--json', image])
        run('health', [executable, 'exec', '--userns', '--no-home', '--pid', '--cleanenv', image, '/bin/true'])
        state = dict(status='PASS_ORIGINAL_OCI_REPRODUCIBLE_IMAGE_SETUP_AND_NAMESPACE_HEALTH_ONLY',
                     source_container='nfcore/atacseq:1.2.1', platform_digest=DIGEST, source_date_epoch=int(EPOCH),
                     runtime_version='1.5.5-1', image=str(image), image_pin=pin(image), processes=records,
                     scientific_analysis_executed=False, required_action=ACTION)
        save(out/'COMPLETED.json', state)
        print(json.dumps(dict(status=state['status'], image=state['image'], image_pin=state['image_pin'])), flush=True)
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        status = 'SKIP3_IMAGE_SETUP_UNAVAILABLE' if code == 3 else 'FAIL2_IMAGE_SETUP_NO_RESULT'
        save(out/'FAILURE.json', dict(status=status, error=repr(error), traceback=traceback.format_exc(), required_action=ACTION))
        (out/'WARNINGS.md').write_text('# '+status+'\n\n'+str(error)+'\n\n'+ACTION+'\n', encoding='utf-8')
        print(status+': '+str(error)+'\n'+ACTION, file=sys.stderr, flush=True)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
