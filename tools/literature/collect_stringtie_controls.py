# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unmodified official StringTie binaries on complete synthetic assembly/quantification/merge controls."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
VERSIONS = ('1.3.0', '1.3.6', '2.1.2', '2.1.7', '2.2.1', '3.0.0', '3.0.3')


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def invoke(command, cwd, prefix, seconds=30):
    start = time.monotonic()
    with (cwd / (prefix + '.stdout')).open('xb') as stdout, (cwd / (prefix + '.stderr')).open('xb') as stderr:
        try:
            process = subprocess.run(command, cwd=cwd, stdout=stdout, stderr=stderr, timeout=seconds, check=False)
            result = dict(command=command, exit_code=process.returncode, timed_out=False)
        except subprocess.TimeoutExpired:
            result = dict(command=command, exit_code=None, timed_out=True)
        except OSError as error:
            result = dict(command=command, exit_code=None, timed_out=False, launch_error=str(error))
    result.update(elapsed_seconds=time.monotonic() - start, stdout=pin(cwd / (prefix + '.stdout')), stderr=pin(cwd / (prefix + '.stderr')))
    save(cwd / (prefix + '.process.json'), result)
    return result


def fixture(out, samtools):
    target = out / 'inputs'
    target.mkdir()
    models = [('ga', 'ta', 'chrA', '+', [(101, 250), (401, 550)]),
              ('ga', 'ta.short', 'chrA', '+', [(121, 250), (401, 520)]),
              ('gb', 'tb', 'chrA', '-', [(2001, 2150), (2401, 2550)]),
              ('gc', 'tc.uncovered', 'chrB', '+', [(3001, 3300)])]
    gtf = []
    for gene, tx, contig, strand, exons in models:
        attrs = f'gene_id "{gene}"; transcript_id "{tx}"; gene_name "{gene.upper()}";'
        gtf.append(f'{contig}\tcontrol\ttranscript\t{exons[0][0]}\t{exons[-1][1]}\t.\t{strand}\t.\t{attrs}\n')
        for number, (start, end) in enumerate(exons, 1):
            gtf.append(f'{contig}\tcontrol\texon\t{start}\t{end}\t.\t{strand}\t.\t{attrs} exon_number "{number}";\n')
    (target / 'guide.gtf').write_text(''.join(gtf), encoding='ascii', newline='\n')
    (target / 'reference.sizes').write_text('chrA\t12000\nchrB\t10000\n', encoding='ascii', newline='\n')
    header = '@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chrA\tLN:12000\n@SQ\tSN:chrB\tLN:10000\n'
    rows, number = [], 0
    for contig, strand, exons in [('chrA', '+', models[0][-1]), ('chrA', '-', models[2][-1]), ('chrA', '+', [(6001, 6150), (6401, 6550)])]:
        for left, right in exons:
            for start in range(left, right - 49, 10):
                for replicate in range(12):
                    number += 1
                    fields = [f'read{number:05}', '16' if strand == '-' else '0', contig, str(start), '60', '50M', '*', '0', '0', 'ACGT' * 12 + 'AC', 'I' * 50, 'NH:i:1', 'XS:A:' + strand]
                    rows.append((start, '\t'.join(fields) + '\n'))
        gap = exons[1][0] - exons[0][1] - 1
        for replicate in range(40):
            number += 1
            start = exons[0][1] - 24
            fields = [f'junction{number:05}', '16' if strand == '-' else '0', contig, str(start), '60', f'25M{gap}N25M', '*', '0', '0', 'ACGT' * 12 + 'AC', 'I' * 50, 'NH:i:1', 'XS:A:' + strand]
            rows.append((start, '\t'.join(fields) + '\n'))
    (target / 'reads.sam').write_text(header + ''.join(row for _, row in sorted(rows, key=lambda r: r[0])), encoding='ascii', newline='\n')
    (target / 'empty.sam').write_text(header, encoding='ascii', newline='\n')
    for stem in ('reads', 'empty'):
        receipt = invoke([str(samtools), 'view', '--no-PG', '-b', '-o', str(target / (stem + '.bam')), str(target / (stem + '.sam'))], out, 'samtools-' + stem)
        assert receipt['exit_code'] == 0 and not receipt['timed_out']
    save(out / 'FIXTURE.json', dict(status='complete-synthetic-fixture-not-biological-data', alignment_rows=len(rows), spliced_rows=120,
                                  reference_transcripts=4, reference_genes=3, unannotated_supported_loci=1,
                                  uncovered_reference_transcripts=['tc.uncovered'], reference_models=models,
                                  samtools=pin(samtools), inputs={p.name: pin(p) for p in sorted(target.iterdir())},
                                  sequence_note='Artificial aligned sequences; StringTie consumes alignment geometry without a reference FASTA. This is not an aligner or sequence-truth benchmark.'))


def worker(out, intake):
    assert platform.system() == 'Linux' and platform.machine() == 'x86_64'
    original = json.loads((intake / 'COMPLETED.json').read_bytes())
    assert [e['version'] for e in original['editions']] == list(VERSIONS)
    fixture_proof = json.loads((out / 'FIXTURE.json').read_bytes())
    assert all(pin(out / 'inputs' / name) == value for name, value in fixture_proof['inputs'].items())
    editions = []
    for edition in original['editions']:
        version = edition['version']
        binary = intake / edition['executable']
        assert pin(binary) == edition['executable_pin']
        root_license = intake / version / 'source' / ('stringtie-' + version) / 'LICENSE'
        assert root_license.relative_to(intake).as_posix() in edition['licenses']
        assert pin(root_license) == edition['licenses'][root_license.relative_to(intake).as_posix()]
        license_name = root_license.read_text().splitlines()[0]
        assert license_name in ('MIT License', 'Artistic License 2.0')
        binary.chmod(binary.stat().st_mode | 0o100)
        folder = out / version
        folder.mkdir()
        version_receipt = invoke([str(binary), '--version'], folder, 'version', 10)
        if version_receipt['exit_code'] != 0 or (folder / 'version.stdout').read_text().strip() != version:
            save(folder / 'SKIPPED.json', dict(status='runtime-unavailable-analysis-skipped', process=version_receipt,
                                             action='Read original version stderr/process receipt; supply a permitted matching Linux x86_64 environment and exact official binary/dependencies. Preserve this attempt and rerun into NEW paths.'))
            editions.append(dict(version=version, status='skipped-runtime-unavailable', runtime=version_receipt))
            save(out / 'PROGRESS.json', editions)
            continue
        bam, guide = str(out / 'inputs/reads.bam'), str(out / 'inputs/guide.gtf')
        cases = []
        requests = [('assembly', [bam]), ('guided', [bam, '-G', guide]),
                    ('estimate', [bam, '-G', guide, '-e', '-A', 'gene-abundance.tsv', '-B']),
                    ('empty-estimate', [str(out / 'inputs/empty.bam'), '-G', guide, '-e', '-A', 'gene-abundance.tsv', '-B']),
                    ('paper171-literal-rf', [bam, '-m', '150', '-t', '-f', '0.3', '-rf']),
                    ('paper171-documented-rf', [bam, '-m', '150', '-t', '-f', '0.3', '--rf'])]
        for name, args in requests:
            case = folder / name
            case.mkdir()
            receipt = invoke([str(binary), *args, '-p', '1', '-o', 'transcripts.gtf'], case, 'fit')
            cases.append(dict(case=name, process=receipt, files={p.relative_to(case).as_posix(): pin(p) for p in sorted(case.rglob('*')) if p.is_file()}))
            save(folder / 'PROGRESS.json', cases)
        if all((folder / name / 'transcripts.gtf').is_file() for name in ('assembly', 'guided')):
            case = folder / 'merge'
            case.mkdir()
            args = [str(binary), '--merge', '-G', guide, '-m', '150', '-o', 'transcripts.gtf',
                    str(folder / 'assembly/transcripts.gtf'), str(folder / 'guided/transcripts.gtf')]
            receipt = invoke(args, case, 'fit')
            cases.append(dict(case='merge', process=receipt, files={p.relative_to(case).as_posix(): pin(p) for p in sorted(case.rglob('*')) if p.is_file()}))
        assert pin(binary) == edition['executable_pin']
        result = dict(version=version, license=license_name, binary_pin=pin(binary), runtime=version_receipt, cases=cases,
                      status='captured-original-protocols-not-project-adapter-validation')
        save(folder / 'CAPTURED.json', result)
        editions.append(result)
        save(out / 'PROGRESS.json', editions)
    assert all(pin(out / 'inputs' / name) == value for name, value in fixture_proof['inputs'].items())
    result = dict(status='captured-original-StringTie-protocols-audit-required', host=dict(system=platform.system(), machine=platform.machine(), release=platform.release()),
                  editions=editions, fixture=pin(out / 'FIXTURE.json'), original_intake=pin(intake / 'COMPLETED.json'),
                  implementation_credit=False, project_adapter_validated=False,
                  warnings='Original outputs and failures are retained. Exact paper flags, transcript population, printed abundance, Ballgown and historical zero-coverage differences still require native audits. No biological or complete-paper replication claim.')
    save(out / 'CAPTURED.json', result)
    failures = [dict(version=e['version'], case=c['case'], process=c['process']) for e in editions for c in e.get('cases', []) if c['process']['exit_code'] != 0 or c['process']['timed_out']]
    print(json.dumps(dict(status=result['status'], editions=len(editions), protocols=sum(len(e.get('cases', [])) for e in editions), retained_failed_protocols=failures)))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--intake', type=Path, required=True)
    parser.add_argument('--linux-worker', action='store_true')
    parser.add_argument('--samtools', type=Path, default=ROOT / 'build/omics-hts/ucrt64/bin/samtools.exe')
    args = parser.parse_args()
    out, intake = args.out.resolve(), args.intake.resolve()
    assert out.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve())
    assert intake.is_relative_to((ROOT / 'build/literature-review/stringtie-runtime').resolve())
    if args.linux_worker:
        return worker(out, intake)
    assert not out.exists() and platform.system() == 'Windows'
    out.mkdir(parents=True)
    fixture(out, args.samtools.resolve())
    def linux(path):
        text = str(path.resolve())
        assert text[1:3] == ':\\'
        return '/mnt/' + text[0].lower() + '/' + text[3:].replace('\\', '/')
    command = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/python3', '-I', '-B', linux(Path(__file__)),
               '--linux-worker', '--intake', linux(intake), '--out', linux(out)]
    receipt = invoke(command, out, 'linux-worker', 240)
    assert receipt['exit_code'] == 0 and not receipt['timed_out']
    result = json.loads((out / 'CAPTURED.json').read_bytes())
    print(json.dumps(dict(status=result['status'], editions=len(result['editions']), protocols=sum(len(e.get('cases', [])) for e in result['editions']))))
    return 0


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact original binary and complete fixture checks')
    raise SystemExit(main())
