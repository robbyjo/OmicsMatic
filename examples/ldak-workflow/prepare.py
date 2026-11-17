#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare original LDAK tutorial summaries/tagging and two explicit workflow requests."""
from pathlib import Path
import argparse
import base64
import importlib.util
import json
import os
import platform
import shutil
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('ldak_runtime_setup', ROOT/'tools/literature/ldak_original/setup.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
ACTION = ('SKIP3: skip dependent analysis when permitted Linux x86_64 execution, the prepared '
          'original runtime or exact tutorial archive is unavailable. Prepare the runtime '
          'separately; supply --archive PATH or explicitly allow the tutorial download. '
          'FAIL2: no complete prepared tutorial; preserve partials and both original consoles, '
          'resolve the named discrepancy and retry NEW output. Preparation is not a '
          'completed SumHer/REML analysis, a BLD-LDAK reference or a paper reproduction.')


def save(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def prop(value):
    """ASCII Java-properties value, including UTF-16 surrogate pairs for non-BMP paths."""
    raw = str(value).encode('utf-16-be')
    result = []
    for start in range(0, len(raw), 2):
        unit = int.from_bytes(raw[start:start+2], 'big')
        char = chr(unit)
        if char in '\\ :=#!':
            result.append('\\'+char)
        elif unit < 32 or unit > 126:
            result.append('\\u'+format(unit, '04x'))
        else:
            result.append(char)
    return ''.join(result)


def request(path, values):
    with Path(path).open('x', encoding='ascii', newline='\n') as stream:
        stream.write(''.join(key+'='+prop(value)+'\n' for key, value in sorted(values.items())))


def runtime_files(runtime):
    if not runtime.is_dir():
        raise setup.Prerequisite('Prepared runtime directory is missing: '+str(runtime))
    marker_path = runtime/'COMPLETED.json'
    if marker_path.is_symlink():
        raise ValueError('Prepared runtime receipt must be a regular unlinked file')
    if not marker_path.is_file():
        raise setup.Prerequisite('Runtime is not prepared; require the setup helper completion receipt')
    profile = json.loads(setup.PROFILE.read_bytes())
    marker = json.loads(marker_path.read_bytes())
    if marker['status'] != 'READY_PINNED_ORIGINAL_LDAK52_LINUX_RUNTIME_ONLY' or marker['original_binary'] != profile['binary']:
        raise ValueError('Prepared runtime profile differs from the selected original 5.2 edition')
    expected = marker['files']
    if set(p.name for p in runtime.iterdir()) != set(expected) | {'COMPLETED.json'}:
        raise ValueError('Prepared runtime membership differs; do not repair its saved receipt')
    for name, identity in expected.items():
        path = runtime/name
        if Path(name).name != name or path.is_symlink() or not path.is_file() or setup.pin(path) != identity:
            raise ValueError('Prepared runtime member differs: '+name)
    if setup.pin(runtime/'ldak5.2.linux') != profile['binary'] or setup.pin(runtime/'LICENSE-UPSTREAM.txt') != profile['license']:
        raise ValueError('Original executable or GPL notice identity differs')
    return {**expected, 'COMPLETED.json':setup.pin(marker_path)}


def unpack(archive, target, profile):
    if setup.pin(archive) != profile['archive']:
        raise ValueError('Tutorial archive size/SHA-256 mismatch; never refresh the pin')
    with zipfile.ZipFile(archive) as saved:
        entries = saved.infolist()
        if len(entries) != len(profile['archive_members']) or {i.filename:i.file_size for i in entries} != profile['archive_members']:
            raise ValueError('Tutorial archive membership differs; generic extraction is forbidden')
        target.mkdir()
        for name, identity in profile['selected_inputs'].items():
            if Path(name).name != name:
                raise ValueError('Invalid selected tutorial input name')
            with saved.open(name) as source, (target/name).open('xb') as output:
                shutil.copyfileobj(source, output, 1048576)
            if setup.pin(target/name) != identity:
                raise ValueError('Original selected tutorial input differs: '+name)


def original(out, name, executable, arguments, seconds):
    folder = out/name
    folder.mkdir()
    command = [str(executable), *arguments, '--max-threads', '1']
    environment = {k:v for k,v in os.environ.items() if not k.startswith(('OMP_', 'MKL_')) and k not in ['LD_PRELOAD', 'LD_LIBRARY_PATH']}
    environment.update(OMP_NUM_THREADS='1', MKL_NUM_THREADS='1', LC_ALL='C')
    began = time.monotonic()
    with (folder/'original.stdout').open('xb') as so, (folder/'original.stderr').open('xb') as se:
        try:
            child = subprocess.Popen(command, cwd=folder, env=environment, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
        except (PermissionError, FileNotFoundError, OSError) as error:
            raise setup.Prerequisite('Original execution unavailable or forbidden: '+str(error)) from error
        timed_out = False
        try:
            child.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            child.kill()
            child.wait()
    save(folder/'PROCESS.json', dict(command=command, pid=child.pid, exit_code=child.returncode, timed_out=timed_out, seconds=round(time.monotonic()-began, 3), environment_policy='remove-LD_PRELOAD-LD_LIBRARY_PATH-OMP_*-MKL_*;OMP_NUM_THREADS=1;MKL_NUM_THREADS=1;LC_ALL=C'))
    diagnostics, errors = [], 0
    for name in ['original.stdout', 'original.stderr']:
        for number, line in enumerate((folder/name).read_text(encoding='utf-8').splitlines(), 1):
            lower = line.lstrip().lower()
            if lower.startswith(('warning', 'error', 'fatal')):
                fatal = lower.startswith(('error', 'fatal'))
                errors += int(fatal)
                diagnostics.append(dict(console=name, line=number, level='error' if fatal else 'warning', text_base64=base64.b64encode(line.encode()).decode()))
    save(folder/'DIAGNOSTICS.json', diagnostics)
    if child.returncode or timed_out or errors:
        raise ValueError('Original '+folder.name+' failed; inspect its PROCESS.json and both original consoles')
    return folder


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument('--runtime', type=Path, required=True, help='Exact directory produced by ldak_original/setup.py')
    parser.add_argument('--archive', type=Path, help='Exact original Test_Datasets/data.zip for offline preparation')
    parser.add_argument('--allow-download', action='store_true', help='Allow only the pinned tutorial-data download; does not install a runtime')
    parser.add_argument('--wall-seconds', type=int, default=120, help='Per original preparation stage, 1..3600 (default 120)')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = None
    try:
        destination = args.out.absolute()
        runtime = args.runtime.resolve()
        if destination.exists() or destination.is_symlink():
            raise ValueError('Output exists; retain it and choose NEW output')
        if destination.resolve().is_relative_to(runtime) or runtime.is_relative_to(destination.resolve()):
            raise ValueError('Output overlaps the prepared runtime; preserve its exact membership')
        if args.archive is not None and args.archive.resolve().is_relative_to(destination.resolve()):
            raise ValueError('Output contains the supplied tutorial archive')
        if not 1 <= args.wall_seconds <= 3600:
            raise ValueError('wall-seconds must be 1..3600')
        destination.mkdir(parents=True, exist_ok=False)
        out = destination.resolve()
        (out/'WARNINGS.md').write_text('# Original tutorial preparation: required actions\n\n'+ACTION+'\n\nThe quant.pheno tutorial is not an author study. The single-category tagging model uses unit weights, power -0.25 and a 1000 kb window; it is not BLD-LDAK. The illustrative alpha grid is -0.5,-0.25,0. Original tutorial SumHer may replace zero statistics or low tagging values; the generated request explicitly permits recorded substitutions. Review every original warning and the subsequent workflow WARNINGS.md before using a result.\n', encoding='utf-8')
        if platform.system() != 'Linux' or platform.machine().lower() not in ['x86_64', 'amd64']:
            raise setup.Prerequisite('Original tutorial preparation requires Linux x86_64; invoke a permitted WSL/Linux shell explicitly')
        files = runtime_files(runtime)
        captured = out/'runtime'
        captured.mkdir()
        for name, identity in files.items():
            shutil.copyfile(runtime/name, captured/name)
            if setup.pin(captured/name) != identity or setup.pin(runtime/name) != identity:
                raise ValueError('Runtime changed during capture: '+name)
        executable = captured/'ldak5.2.linux'
        executable.chmod(0o500)
        profile_path = Path(__file__).with_name('tutorial-profile.json')
        profile = json.loads(profile_path.read_bytes())
        shutil.copyfile(profile_path, out/'tutorial-profile.json')
        archive = out/'data.zip'
        setup.obtain(profile['archive_url'], archive, profile['archive'], args.allow_download, args.archive)
        unpack(archive, out/'inputs', profile)
        bfile = (out/'inputs/human').as_posix()
        phenotype = (out/'inputs/quant.pheno').as_posix()
        linear = original(out, 'linear', executable, ['--linear', 'result', '--bfile', bfile, '--pheno', phenotype], args.wall_seconds)
        tagging = original(out, 'tagging', executable, ['--calc-tagging', 'result', '--bfile', bfile, '--ignore-weights', 'YES', '--power', '-0.25', '--window-kb', '1000'], args.wall_seconds)
        for path in [linear/'result.summaries', tagging/'result.tagging']:
            if not path.is_file() or path.stat().st_size == 0:
                raise ValueError('Missing original preparation output: '+str(path))
        for name, identity in files.items():
            if setup.pin(captured/name) != identity or setup.pin(runtime/name) != identity:
                raise ValueError('Captured/source runtime changed during original preparation: '+name)
        for name, identity in profile['selected_inputs'].items():
            if setup.pin(out/'inputs'/name) != identity:
                raise ValueError('Original tutorial input changed during preparation: '+name)
        common = dict(executable=executable.as_posix(), license=(captured/'LICENSE-UPSTREAM.txt').as_posix(), execution='allow', wall_ms=str(args.wall_seconds*1000), max_bytes='1000000000', max_files='10000', upstream_adjustments='allow-recorded', max_iterations='100', tolerance='0.000001')
        request(out/'sumher.properties', dict(common, mode='sumher', tagging=(tagging/'result.tagging').as_posix(), summary=(linear/'result.summaries').as_posix(), check_sums='YES', allow_ambiguous='YES', inflation='none', solver='maximum-likelihood', prevalence='-', ascertainment='-', cutoff='-', max_predictors='1000000'))
        request(out/'thin-reml.properties', dict(common, mode='kinship-reml', bfile=bfile, phenotype=phenotype, covariates='-', weights='-', predictors='-', thinning='YES', window_r2='0.98', window_kb='100', alpha_grid='-0.5,-0.25,0', alpha_selection='max-alt-likelihood-first-grid-tie', constrain='YES', hwe_stand='YES', block_variants='128', max_numeric_bytes='100000000', max_samples='10000', max_variants='1000000', max_metadata_bytes='100000000', max_line_bytes='16384'))
        inventory = {p.relative_to(out).as_posix():setup.pin(p) for p in sorted(out.rglob('*')) if p.is_file()}
        save(out/'COMPLETED.json', dict(status='READY_ORIGINAL_TUTORIAL_REQUESTS_NOT_COMPLETED_ANALYSES', files=inventory, original_preparation_stages_executed=2, requests=['sumher.properties', 'thin-reml.properties'], tagging_model='unit weights; power -0.25; 1000 kb window; not BLD-LDAK', tutorial_not_paper_analysis=True, original_source_runtime_unchanged=True, required_action=ACTION))
        print('PASS original tutorial requests prepared: '+str(out)+'\nRead WARNINGS.md. Run and verify each request using the matching current source-built application; preparation alone is not a completed analysis.')
        return 0
    except Exception as error:
        skipped = isinstance(error, (setup.Prerequisite, PermissionError))
        label = 'SKIP3' if skipped else 'FAIL2'
        if out is not None:
            save(out/('SKIPPED.json' if skipped else 'FAILURE.json'), dict(status=label, detail=str(error), required_action=ACTION))
        print(label+': '+str(error)+'\n'+ACTION, file=sys.stderr)
        return 3 if skipped else 2


if __name__ == '__main__':
    raise SystemExit(main())
