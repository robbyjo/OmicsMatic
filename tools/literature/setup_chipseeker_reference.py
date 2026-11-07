# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install an isolated, source-bound original ChIPseeker reference; never run user analysis."""
import argparse
import json
import subprocess
import tarfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from setup_deseq2_reference import ROOT, RSCRIPT, BASES, environment, pin, save


def inventory(bases):
    result = {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        for root in bases:
            files = sorted(p for p in root.rglob('*') if p.is_file())
            result[str(root)] = {p.relative_to(root).as_posix(): value
                                 for p, value in zip(files, pool.map(pin, files))}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--additional-library', action='append', type=Path, default=[])
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    out.mkdir(parents=True)
    extras = [p.resolve(strict=True) for p in args.additional_library]
    assert all(p.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and p.is_dir() for p in extras)
    bases = [*extras, *BASES]
    action = ('Read installer.stdout, installer.stderr and R-RESULT.json. Where permitted install the '
              'named missing dependencies in a NEW private library. Preserve this attempt and existing '
              'libraries. Skip dependent ChIPseeker annotation until the exact package and TxDb APIs load '
              'and the health annotation passes. Do not substitute a different package version silently.')
    before = inventory(bases)
    save(out / 'BASES-BEFORE.json', before)
    result = dict(status='SKIP3_CHIPSEEKER_REFERENCE_UNAVAILABLE', analysis_admitted=False,
                  installation_authorized=args.allow_install, required_action=action)
    bindings = {}
    try:
        if not args.allow_install:
            raise RuntimeError('Download/installation not authorized; dependent analysis skipped')
        source = ROOT / 'build/literature-review/chipseeker-runtime/source-intake-2/1.48.1'
        receipt = json.loads((source / 'SOURCE.json').read_bytes())
        archive = source / 'ChIPseeker_1.48.1.tar.gz'
        assert pin(archive) == receipt['archive']
        with tarfile.open(archive) as tar:
            assert not any(m.name.startswith('ChIPseeker/src/') for m in tar.getmembers())
            description = tar.extractfile('ChIPseeker/DESCRIPTION').read()
            assert b'NeedsCompilation: no' in description
        script = ROOT / 'tools/literature/install_chipseeker_reference.R'
        for path in (Path(__file__).resolve(), script, archive, source / 'SOURCE.json'):
            bindings[path.relative_to(ROOT).as_posix()] = pin(path)
            if path.suffix in {'.py', '.R', '.json'}:
                snapshot = out / 'source-snapshot' / path.relative_to(ROOT)
                snapshot.parent.mkdir(parents=True, exist_ok=True)
                snapshot.write_bytes(path.read_bytes())
        save(out / 'STARTED.json', dict(source_pins=bindings, libraries=[str(p) for p in bases],
                                       original_archive=receipt, analysis_admitted=False))
        command = [str(RSCRIPT), '--vanilla', str(script), str(out / 'R-lib'), str(out),
                   str(archive), *map(str, bases)]
        with (out / 'installer.stdout').open('xb') as stdout, (out / 'installer.stderr').open('xb') as stderr:
            process = subprocess.run(command, cwd=ROOT, env=environment(), stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, timeout=2400, check=False)
        save(out / 'process.json', dict(command=command, exit_code=process.returncode))
        report = json.loads((out / 'R-RESULT.json').read_bytes())
        result.update(report)
        if process.returncode or report['status'] != 'INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY':
            raise RuntimeError(report.get('detail', 'Original package health check did not pass'))
        result['downloads'] = {p.relative_to(out).as_posix(): pin(p)
                               for p in sorted((out / 'downloads').rglob('*')) if p.is_file()}
    except Exception as error:
        result.update(status='SKIP3_CHIPSEEKER_REFERENCE_UNAVAILABLE', detail=str(error))
    finally:
        after = inventory(bases)
        save(out / 'BASES-AFTER.json', after)
        result['preexisting_libraries_unchanged'] = before == after
        result['preexisting_file_count'] = sum(map(len, before.values()))
        result['source_pins'] = bindings
        if before != after:
            result.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED', detail='Inspect before/after inventories')
        if any(pin(ROOT / name) != wanted for name, wanted in bindings.items()):
            result.update(status='FAIL2_SETUP_SOURCE_CHANGED', detail='Installer/source changed during attempt')
        save(out / 'RESULT.json', result)
        (out / 'WARNINGS.md').write_text('# ' + result['status'] + '\n\n' + result.get('detail', '') +
                                       '\n\nRequired action: ' + action +
                                       '\n\nReference setup is not user analysis admission.\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k not in {'downloads', 'source_pins'}}))
    return 0 if result['status'] == 'INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY' else 3


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source bindings')
    raise SystemExit(main())
