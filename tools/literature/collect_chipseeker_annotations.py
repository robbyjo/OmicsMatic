# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a synthetic TxDb and collect installed original ChIPseeker annotation controls."""
import argparse
import itertools
import json
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, RSCRIPT, BASES, environment, pin, save

VERSIONS = ('1.22.1', '1.26.2', '1.34.1', '1.48.1')
PRIORITY = ['Promoter', '5UTR', '3UTR', 'Exon', 'Intron', 'Downstream', 'Intergenic']


def invoke(command, folder, timeout=600):
    with (folder / 'stdout.log').open('xb') as stdout, (folder / 'stderr.log').open('xb') as stderr:
        result = subprocess.run(command, cwd=ROOT, env=environment(), stdin=subprocess.DEVNULL,
                                stdout=stdout, stderr=stderr, timeout=timeout)
    save(folder / 'process.json', dict(command=command, exit_code=result.returncode))
    return result.returncode


def cases():
    rows = []
    def add(name, **options):
        base = dict(id=name, tss_region=[-3000, 3000], level='transcript', priority=PRIORITY,
                    add_flank_gene_info=False, flank_distance=5000, downstream_distance=3000,
                    ignore_overlap=False, ignore_upstream=False, ignore_downstream=False, overlap='TSS')
        rows.append(dict(base, **options))
    for level in ('gene', 'transcript'):
        for a, b in ((-3000, 3000), (-2000, 1000), (-1000, 1000), (0, 0)):
            add(f'{level}-promoter-{a}-{b}', level=level, tss_region=[a, b])
        for overlap, ignore, up, down in itertools.product(('TSS', 'all'), (False, True), (False, True), (False, True)):
            add(f'{level}-{overlap}-{int(ignore)}{int(up)}{int(down)}', level=level,
                overlap=overlap, ignore_overlap=ignore, ignore_upstream=up, ignore_downstream=down)
        for d in (1000, 3500):
            add(f'{level}-downstream-{d}', level=level, downstream_distance=d)
        add(f'{level}-exon-first', level=level, priority=['Exon', 'Intron', '5UTR', '3UTR', 'Promoter', 'Downstream', 'Intergenic'])
        for distance in (3000, 5000):
            add(f'{level}-flanks-{distance}', level=level, add_flank_gene_info=True, flank_distance=distance)
    assert len(rows) == len({row['id'] for row in rows})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime-setup', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--allow-install-editions', action='store_true')
    parser.add_argument('--installed-editions', type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    setup = args.runtime_setup.resolve()
    result = json.loads((setup / 'RESULT.json').read_bytes())
    assert result['status'] == 'INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY'
    assert result['preexisting_libraries_unchanged'] is True
    out.mkdir()
    setup_plan = json.loads((setup / 'STARTED.json').read_bytes())
    libraries = [setup / 'R-lib', *map(Path, setup_plan['libraries'])]
    scripts = [Path(__file__).resolve(), *(ROOT / 'tools/literature' / name for name in
                ('prepare_chipseeker_annotation_fixture.R', 'collect_chipseeker_annotation.R', 'install_chipseeker_edition.R'))]
    sources = ROOT / 'build/literature-review/chipseeker-runtime/source-intake-2'
    txdb_source = ROOT / 'build/literature-review/chipseeker-runtime/txdbmaker-source-intake-1'
    txdb_receipt = json.loads((txdb_source / 'SOURCE.json').read_bytes())
    txdb_builder = txdb_source / 'selected-source/txdbmaker/R/makeTxDb.R'
    assert pin(txdb_builder) == txdb_receipt['selected_files']['txdbmaker/R/makeTxDb.R']
    scripts.append(txdb_builder)
    bindings = {p.relative_to(ROOT).as_posix(): pin(p) for p in scripts}
    for version in VERSIONS:
        folder = sources / version
        receipt = json.loads((folder / 'SOURCE.json').read_bytes())
        assert pin(folder / f'ChIPseeker_{version}.tar.gz') == receipt['archive']
        for name, expected in receipt['source_members'].items():
            file = folder / 'selected-source' / name
            assert pin(file) == expected
            bindings[file.relative_to(ROOT).as_posix()] = expected
    for name in bindings:
        copy = out / 'source-snapshot' / name
        copy.parent.mkdir(parents=True, exist_ok=True)
        copy.write_bytes((ROOT / name).read_bytes())
    save(out / 'STARTED.json', dict(source_pins=bindings, libraries=[str(p) for p in libraries],
                                   historical_dependencies_recreated=False, analysis_admitted=False))
    fixture_process = out / 'fixture-process'; fixture_process.mkdir()
    fixture = out / 'fixture'
    assert invoke([str(RSCRIPT), '--vanilla', str(scripts[1]), str(fixture), os.pathsep.join(map(str, libraries)), str(txdb_builder)], fixture_process) == 0
    summaries = []
    fixture_pins = {p.relative_to(fixture).as_posix(): pin(p) for p in fixture.rglob('*') if p.is_file()}
    reused_pins = {}
    for version in VERSIONS:
        edition = out / version; edition.mkdir()
        edition_libraries = libraries
        if version != '1.48.1':
            if args.installed_editions:
                installation = args.installed_editions.resolve() / version / 'installation'
                prior = json.loads((installation / 'process.json').read_bytes())
                assert prior['exit_code'] == 0 and prior['command'][3] == version
                source_receipt = json.loads((args.installed_editions.resolve() / 'STARTED.json').read_bytes())
                installer_name = scripts[3].relative_to(ROOT).as_posix()
                assert source_receipt['source_pins'][installer_name] == pin(scripts[3])
                private = installation / 'R-lib'
                for file in private.rglob('*'):
                    if file.is_file():
                        reused_pins[file.relative_to(ROOT).as_posix()] = pin(file)
                edition_libraries = [private, *libraries]
                save(edition / 'reused-installation.json', dict(path=str(private), prior_process=prior,
                     admission='Version and exact original function body/formal checks run again in the fresh collector; historical dependencies are not reproduced.'))
            elif not args.allow_install_editions:
                save(edition / 'SKIP3.json', dict(status='SKIP3_INSTALL_NOT_AUTHORIZED', version=version))
                summaries.append(dict(version=version, status='SKIP3_INSTALL_NOT_AUTHORIZED'))
                continue
            else:
                installer = edition / 'installation'; installer.mkdir()
                private = installer / 'R-lib'
                command = [str(RSCRIPT), '--vanilla', str(scripts[3]), version,
                           str(sources / version / f'ChIPseeker_{version}.tar.gz'), str(private),
                           os.pathsep.join(map(str, libraries))]
                if invoke(command, installer):
                    summaries.append(dict(version=version, status='SKIP3_ORIGINAL_EDITION_UNAVAILABLE',
                        required_action='Read installation logs. Supply this exact edition in a NEW private library where permitted; skip its dependent analysis until it loads. Do not substitute a modern edition.'))
                    save(edition / 'SKIP3.json', summaries[-1])
                    continue
                edition_libraries = [private, *libraries]
        plan = edition / 'PLAN.json'
        save(plan, dict(version=version, source_R=(sources / version / 'selected-source/ChIPseeker/R').as_posix(), cases=cases()))
        collector = edition / 'controls'; collector.mkdir()
        code = invoke([str(RSCRIPT), '--vanilla', str(scripts[2]), str(plan), str(fixture), str(collector),
                       os.pathsep.join(map(str, edition_libraries))], edition)
        if code:
            summaries.append(dict(version=version, status='FAIL2_CONTROL_CAPTURE_FAILED', exit_code=code))
        else:
            raw = json.loads((collector / 'ORIGINAL.json').read_bytes())
            summaries.append(dict(version=version, status='captured-original-annotation-controls',
                calls=len(raw['cases']), successes=sum(p['source_error'] is None for p in raw['cases']),
                original_errors=sum(p['source_error'] is not None for p in raw['cases'])))
        print(json.dumps(summaries[-1]), flush=True)
    assert all(pin(ROOT / name) == expected for name, expected in bindings.items())
    assert all(pin(ROOT / name) == expected for name, expected in reused_pins.items())
    assert fixture_pins == {p.relative_to(fixture).as_posix(): pin(p) for p in fixture.rglob('*') if p.is_file()}
    save(out / 'RESULT.json', dict(editions=summaries, source_pins=bindings, historical_dependencies_recreated=False,
        reused_libraries_unchanged=True, reused_library_files=reused_pins, analysis_admitted=False, fixture_files=fixture_pins))
    (out / 'WARNINGS.md').write_text('# ORIGINAL CONTROL CAPTURE: WARNINGS AND REQUIRED ACTIONS\n\n'
        'These are synthetic original-package controls; they do not admit a project adapter or biological result.\n\n'
        'Read fixture-process/stderr.log: the synthetic TxDb constructor reports that its chromosome genome version '
        'is unavailable. The explicit SYNTHETIC metadata does not establish a real reference assembly.\n\n'
        'Read every VERSION/controls/WARNINGS.md and ORIGINAL.json for original per-case warnings and errors. '
        'An original error is not a valid empty result. Any SKIP3 or FAIL2 in RESULT.json requires retaining this '
        'attempt, correcting the named installation or capture cause and retrying into NEW paths. No partial '
        'or failed capture is passing evidence.\n', encoding='utf-8')
    if all(p['status'] == 'captured-original-annotation-controls' for p in summaries):
        save(out / 'COMPLETED.json', dict(status='captured-four-original-ChIPseeker-annotation-editions-not-adapter-admission',
            calls=sum(p['calls'] for p in summaries), successes=sum(p['successes'] for p in summaries), original_errors=sum(p['original_errors'] for p in summaries)))
    else:
        raise SystemExit(3)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original source checks')
    main()
