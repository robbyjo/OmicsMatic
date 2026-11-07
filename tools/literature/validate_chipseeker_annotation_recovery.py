# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual-JAR dependency, process, identity, selection and publication recovery controls."""
import argparse
import csv
import io
import json
from pathlib import Path
import subprocess
import tarfile
from setup_deseq2_reference import ROOT, RSCRIPT, environment, pin, save
from validate_mashr_native import JAVA21


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--artifact-build', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    args = p.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists(); out.mkdir()
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes()); jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ('bytes', 'sha256')}
    assert all(pin(ROOT / name) == expected for name, expected in build['source_pins'].items())
    original = ROOT / 'build/literature-review/chipseeker-runtime/annotation-original-2'
    libraries = json.loads((original / 'STARTED.json').read_bytes())['libraries']; fixture = original / 'fixture'
    base = dict(schema_version=1, mode='ANNOTATE', version='1.48.1', peaks=str(fixture / 'peaks.tsv'),
                txdb=str(fixture / 'synthetic-txdb.sqlite'), reference_build='SYNTHETIC_CHIPSEEKER_CONTROL_V1',
                coordinates='one-based-inclusive', rscript=str(RSCRIPT), r_libraries=libraries,
                out='result', timeout_seconds=300, analyses=[dict(id='annotation')])
    runs = []; bindings = {Path(__file__).resolve().relative_to(ROOT).as_posix(): pin(Path(__file__))}
    def invoke(name, changes, expected, bundle=True):
        folder = out / name; folder.mkdir(); request = dict(base, **changes); request_file = folder / 'request.json'; save(request_file, request)
        command = [str(JAVA21 / 'java.exe'), '-Xmx768m', '-XX:ActiveProcessorCount=2', '-Dfile.encoding=UTF-8', '-cp', str(jar),
                   'org.jlinalg.cli.ChipSeekerAnnotationCli', '--request', str(request_file)]
        with (folder / 'stdout.log').open('xb') as stdout, (folder / 'stderr.log').open('xb') as stderr:
            run = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=600)
        save(folder / 'process.json', dict(command=command, exit_code=run.returncode, expected=expected))
        assert run.returncode == expected, str(folder) + ': inspect retained logs'
        target = Path(request['out']); target = target if target.is_absolute() else folder / target
        if bundle:
            state = json.loads((target / 'analysis-status.json').read_bytes())
            assert state['result_admitted'] == (expected == 0)
            warnings = (target / 'WARNINGS.md').read_text(encoding='utf-8')
            assert 'REQUIRED ACTION' in warnings
            if expected:
                assert not (target / 'results').exists()
                assert ('NO RESULT' if expected == 2 else 'SKIP3') in (folder / 'stderr.log').read_text(encoding='utf-8')
        else:
            assert 'REQUIRED ACTION' not in (folder / 'stdout.log').read_text(encoding='utf-8')
        runs.append(dict(name=name, expected_exit=expected, observed_exit=run.returncode)); print(json.dumps(runs[-1]), flush=True)
        return target
    invoke('missing-rscript', dict(rscript=str(out / 'missing/Rscript.exe')), 3)
    invoke('missing-library', dict(r_libraries=[str(out / 'missing-library')]), 3)
    invoke('wrong-edition', dict(version='1.22.1'), 3)
    invoke('wrong-txdb-hash', dict(txdb_sha256='0' * 64), 2)
    invoke('peak-capacity', dict(maximum_peaks=2), 2)
    invoke('input-byte-capacity', dict(maximum_input_bytes=1), 2)
    invoke('worker-timeout', dict(timeout_seconds=1), 2)
    invoke('worker-output-capacity', dict(maximum_output_bytes=1), 2)
    for name in ('worker-timeout', 'worker-output-capacity'):
        receipt = json.loads((out / name / 'result/worker/process-receipt.json').read_bytes())
        assert receipt['owned_process_cleanup'] and all(not p['alive_after_cleanup'] for p in receipt['owned_process_cleanup'])
    tiny = invoke('native-audit-capacity', dict(maximum_native_operations=1), 0)
    report = json.loads((tiny / 'report.json').read_bytes())
    assert report['analyses'][0]['native_nearest_audit'] == 'SKIPPED_NO_NATIVE_RESULT'
    assert 'NATIVE AUDIT SKIPPED' in (tiny / 'WARNINGS.md').read_text(encoding='utf-8')
    for name, changes in [('unknown-option', dict(arbitrary_R_expression='quit()')), ('coordinate-convention', dict(coordinates='BED')),
                          ('fractional-schema', dict(schema_version=1.0)), ('case-folded-ids', dict(analyses=[dict(id='A'), dict(id='a')]))]:
        invoke(name, changes, 2, False)
    existing = out / 'existing-output'; existing.mkdir(); sentinel = existing / 'user.txt'; sentinel.write_bytes(b'USER OUTPUT: PRESERVE EXACTLY\r\n')
    before = {p.relative_to(existing).as_posix(): pin(p) for p in existing.rglob('*') if p.is_file()}
    invoke('existing-output-refusal', dict(out=str(existing)), 2, False)
    assert before == {p.relative_to(existing).as_posix(): pin(p) for p in existing.rglob('*') if p.is_file()}
    unicode = out / '输入-λ'; unicode.mkdir(); peaks = unicode / '峰.tsv'
    with (fixture / 'peaks.tsv').open(encoding='utf-8', newline='') as stream:rows = list(csv.DictReader(stream, delimiter='\t'))
    for i, row in enumerate(rows):row['peak_id'] = 'NA' if i == 0 else ('重复"λ' if i in {1, 2} else '峰' + str(i + 1))
    with peaks.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=['peak_id', 'contig', 'start', 'end'], delimiter='\t', lineterminator='\n', quoting=csv.QUOTE_NONE, quotechar=None)
        writer.writeheader(); writer.writerows(rows)
    unicode_result = invoke('unicode-identities-and-selection', dict(peaks=str(peaks), out=str(unicode / '结果'),
        analyses=[dict(id='annotation', select_annotation_prefixes=['Promoter'])]), 0)
    actual = json.loads((unicode_result / 'results/annotation/original-output.json').read_bytes())
    gold = json.loads((original / '1.48.1/controls/transcript-promoter--3000-3000/original-output.json').read_bytes())
    for row in gold['annotations']:row['peak_id'] = rows[row['input_ordinal'] - 1]['peak_id']
    assert actual == gold
    with (unicode_result / 'results/annotation/query-ledger.tsv').open(encoding='utf-8', newline='') as stream:
        ledger = list(csv.DictReader(stream, delimiter='\t'))
    assert len(ledger) == 70 and [r['peak_id'] for r in ledger] == [r['peak_id'] for r in rows]
    with (unicode_result / 'results/annotation/selected-query-ordinals.tsv').open(encoding='utf-8', newline='') as stream:
        selected = list(csv.DictReader(stream, delimiter='\t'))
    assert [int(r['input_ordinal']) for r in selected] == [r['input_ordinal'] for r in gold['annotations'] if r['annotation'].startswith('Promoter')]
    assert [r['peak_id'] for r in selected] == [r['peak_id'] for r in gold['annotations'] if r['annotation'].startswith('Promoter')]
    # A deliberately changed, private source package must be refused even with
    # unchanged package version metadata. This is negative-control software only.
    changed = out / 'deliberately-changed-source'; changed.mkdir()
    source = ROOT / 'build/literature-review/chipseeker-runtime/source-intake-2/1.48.1/ChIPseeker_1.48.1.tar.gz'
    bindings[source.relative_to(ROOT).as_posix()] = pin(source)
    archive = changed / 'TAMPERED_NOT_ORIGINAL_ChIPseeker_1.48.1.tar.gz'; changes = []
    with tarfile.open(source) as old, tarfile.open(archive, 'w:gz') as new:
        for entry in old.getmembers():
            data = old.extractfile(entry).read() if entry.isfile() else None
            if entry.name == 'ChIPseeker/R/getNearestFeatureIndicesAndDistances.R':
                before_data = data
                assert data.count(b'sameStrand = FALSE') == 1
                data = data.replace(b'sameStrand = FALSE', b'sameStrand = TRUE')
                entry.size = len(data); changes.append(dict(member=entry.name, before_sha256=__import__('hashlib').sha256(before_data).hexdigest(), after_sha256=__import__('hashlib').sha256(data).hexdigest()))
            new.addfile(entry, io.BytesIO(data) if data is not None else None)
    assert len(changes) == 1
    save(changed / 'DELIBERATE-CHANGE.json', dict(scope='source identity refusal control only; not an original package or admitted analysis', changes=changes, archive=pin(archive)))
    library = changed / 'R-lib'; installer = ROOT / 'tools/literature/install_chipseeker_edition.R'
    bindings[installer.relative_to(ROOT).as_posix()] = pin(installer)
    command = [str(RSCRIPT), '--vanilla', str(installer), '1.48.1', str(archive), str(library), ';'.join(libraries)]
    with (changed / 'install.stdout').open('xb') as stdout, (changed / 'install.stderr').open('xb') as stderr:
        install = subprocess.run(command, cwd=ROOT, env=environment(), stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=300)
    save(changed / 'install-process.json', dict(command=command, exit_code=install.returncode))
    assert install.returncode == 0, 'Inspect negative-control-only installation logs; no source guard claim admitted'
    rejected = invoke('same-version-changed-function-refused', dict(r_libraries=[str(library), *libraries]), 3)
    rejection = json.loads((rejected / 'worker/worker-report.json').read_bytes())
    assert rejection['cases'] == [] and 'Loaded original body/formals mismatch: getNearestFeatureIndicesAndDistances' in rejection['detail']
    assert all(pin(ROOT / name) == expected for name, expected in bindings.items())
    assert all(pin(ROOT / name) == expected for name, expected in build['source_pins'].items())
    save(out / 'COMPLETED.json', dict(status='passed-actual-JAR-ChIPseeker-annotation-recovery-controls', artifact=build['artifact'],
        source_pins=bindings, runs=runs, deliberately_modified_runtime_admitted=False, existing_output_preserved=True,
        unicode_identities_and_literal_selection_preserved=True, workflow_or_catalog_admitted=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable artifact, source or recovery checks')
    main()
