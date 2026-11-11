# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare an exact original DIAMOND runtime privately; analysis never installs software."""
import argparse, hashlib, json, os, platform, re, subprocess, sys, tarfile, traceback, urllib.request
from pathlib import Path


def pin(path):
    digest, count = hashlib.sha256(), 0
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
            count += len(block)
    return dict(bytes=count, sha256=digest.hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--version', choices=['2.0.0', '2.0.15', '2.1.8'], required=True)
    p.add_argument('--binary', type=Path)
    p.add_argument('--allow-download', action='store_true')
    p.add_argument('--lock', type=Path, default=Path(__file__).with_name('diamond-source-lock.json'))
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists(), 'Preserve existing output; choose NEW paths'
    out.mkdir(parents=True)
    action = 'SKIP dependent DIAMOND analysis if its exact runtime is unavailable or installation/download is forbidden. Supply a permitted exact binary or use --allow-download only where allowed, preserve this attempt and retry NEW paths. Analysis installs nothing. Version health is not alignment admission.'
    state = dict(status='SKIP3_DIAMOND_RUNTIME_UNAVAILABLE', analysis_admitted=False,
                 installation_authorized=a.allow_download, required_action=action)
    try:
        assert sys.platform.startswith('linux') and platform.machine().lower() in ['x86_64', 'amd64'], 'Run setup with Linux x86_64 Python3.11+ (explicit WSL is supported)'
        lock = json.loads(a.lock.read_bytes())
        source = lock['versions'][a.version]
        exe = a.binary.resolve() if a.binary else out/'bin/diamond'
        if a.binary is None:
            assert a.allow_download, 'No exact existing binary; download is not authorized'
            archive = out/'original-linux64.tar.gz'
            req = urllib.request.Request(source['download_url'], headers={'User-Agent': 'OmicsMatic-DIAMOND-runtime'})
            with urllib.request.urlopen(req, timeout=120) as response, archive.open('xb') as target:
                total = 0
                while True:
                    chunk = response.read(1048576)
                    if not chunk:
                        break
                    total += len(chunk)
                    assert total <= source['archive']['bytes'], 'Download exceeds exact archive length'
                    target.write(chunk)
                final_url = response.geturl()
            assert pin(archive) == source['archive'], 'Original archive hash differs'
            save(out/'download.json', dict(url=source['download_url'], final_url=final_url, archive=pin(archive)))
            with tarfile.open(archive) as tar:
                members = [m for m in tar.getmembers() if m.isfile() and Path(m.name).name == 'diamond']
                assert len(members) == 1 and members[0].size == source['binary']['bytes']
                exe.parent.mkdir()
                with tar.extractfile(members[0]) as stream, exe.open('xb') as target:
                    while True:
                        chunk = stream.read(1048576)
                        if not chunk:
                            break
                        target.write(chunk)
                exe.chmod(0o755)
        assert exe.is_file() and pin(exe) == source['binary'], 'Missing or changed original binary'
        env = {k: v for k, v in os.environ.items() if not k.startswith('LD_')}
        env.update(PATH='/usr/bin:/bin', LANG='C', LC_ALL='C')
        commands = []
        for name, cmd in [('version', [str(exe), 'version']), ('help', [str(exe), 'help']), ('ldd', ['/usr/bin/ldd', str(exe)])]:
            result = subprocess.run(cmd, cwd=out, env=env, stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
            (out/(name+'.stdout')).write_bytes(result.stdout)
            (out/(name+'.stderr')).write_bytes(result.stderr)
            commands.append(dict(command=cmd, exit_code=result.returncode))
            assert result.returncode == 0, name+' health failed'
            if name == 'version':
                assert ('diamond version '+a.version).encode() in result.stdout+result.stderr
        libraries = set()
        for line in (out/'ldd.stdout').read_text().splitlines():
            assert 'not found' not in line
            match = re.search(r'(?:=>\s+)?(/\S+)\s+\(', line)
            if match:
                libraries.add(Path(match.group(1)).resolve())
        assert libraries, 'Missing original dynamic-loader inventory'
        files = {str(p): pin(p) for p in sorted({exe, *libraries})}
        for path in [Path('/etc/ld.so.cache'), Path('/etc/ld.so.conf'), Path('/etc/ld.so.preload')]:
            if path.is_file():
                files[str(path.resolve())] = pin(path.resolve())
        config = Path('/etc/ld.so.conf.d')
        if config.is_dir():
            files.update({str(p.resolve()): pin(p.resolve()) for p in sorted(config.iterdir()) if p.is_file()})
        assert pin(exe) == source['binary']
        profile = dict(schema_version=1, status='validated-original-diamond-runtime-profile-only',
            source_label=a.version, executable=str(exe), files=files,
            scope='Exact original Linux x86_64 binary plus selected ldd-resolved libraries and loader configuration. OS/kernel and undeclared host objects are not authenticated; no reproducible compilation or historical paper environment is claimed. Analysis installs nothing.')
        save(out/'runtime-profile.json', profile)
        state.update(status='PASS_ORIGINAL_DIAMOND_RUNTIME_PROFILE_ONLY', version=a.version, commands=commands,
            profile=pin(out/'runtime-profile.json'), source_lock=pin(a.lock.resolve()), selected_runtime_files=len(files))
    except Exception as error:
        state['detail'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail', 'Runtime health only; no alignment analysis admitted.')+'\n\nREQUIRED ACTION: '+action+'\n', encoding='utf8')
    print(state['status'], state.get('detail', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-runtime checks')
    raise SystemExit(main())
