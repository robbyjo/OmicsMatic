"""Compare actual packaged workflows with separately launched original Perl/console tools.

The reference path does not call JLinAlg or the new original-process driver.
Literal original flags are declared separately from the typed Java options.
Small hand-checked golden streams supplement those differential comparisons.
"""
import argparse
import csv
import gzip
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import zipfile
import install_trim_galore as installer

ROOT = Path(__file__).resolve().parents[2]


def read(name, sequence, quality=None):
    return ('@' + name + ' original comment', sequence, quality if quality is not None else 'I' * len(sequence))


def fastq(rows):
    return ''.join(header + '\n' + sequence + '\n+\n' + quality + '\n' for header, sequence, quality in rows).encode('utf-8')


def records(path):
    data = path.read_bytes()
    if path.name.endswith('.gz'):
        data = gzip.decompress(data)
    lines = data.decode('utf-8').splitlines(); assert len(lines) % 4 == 0
    return [(lines[i], lines[i+1], lines[i+3]) for i in range(0, len(lines), 4)]


def cygpath(path):
    value = str(path.resolve()).replace('\\', '/')
    return '/cygdrive/' + value[0].lower() + value[2:] if len(value) > 2 and value[1] == ':' else value


def cases():
    result = []
    def add(name, release, a, cli, original, expected, b=None, qc=False, compressed=False, options_file=False):
        result.append(dict(name=name, release=release, a=a, b=b, cli=cli, original=original, expected=expected, qc=qc, compressed=compressed, options_file=options_file))
    adapter = 'AGATCGGAAGAGC'
    single = [read('exact', 'ACGTACGT' + adapter), read('clean', 'GCTAGCTAGC'), read('short', 'AC'), read('poor', 'ACGTACGT', 'IIII!!!!')]
    golden = [read('exact', 'ACGTACGT'), single[1], read('poor', 'ACGT')]
    for version in ['0.6.7', '0.6.10']:
        add('single-quality-' + version, version, single,
            ['--phred', '33', '--quality', '20', '--minimum-length', '3', '--overlap', '3', '--adapter-kind', 'custom', '--adapter1', adapter, '--maximum-n', '0'],
            ['--phred33', '--quality', '20', '--length', '3', '--stringency', '3', '-a', adapter, '--max_n', '0'], {'trimmed_R1': golden}, qc=version == '0.6.7')
    add('quality-zero', '0.6.10', single, ['--phred', '33', '--quality', '0', '--minimum-length', '1', '--overlap', '3', '--adapter-kind', 'custom', '--adapter1', adapter],
        ['--phred33', '--quality', '0', '--length', '1', '--stringency', '3', '-a', adapter], {'trimmed_R1': [golden[0], single[1], single[2], single[3]]})
    warning_sequence = 'CTGACTGCTACGTGTCACAGACGTGATGAA'
    add('original-incomplete-adapter-warning', '0.6.10', [read('warning%d' % i, warning_sequence + adapter) for i in range(32)],
        ['--phred', '33', '--minimum-length', '20', '--adapter-kind', 'custom', '--adapter1', adapter],
        ['--phred33', '--length', '20', '-a', adapter], {'trimmed_R1': [read('warning%d' % i, warning_sequence) for i in range(32)]})
    a = [read('A/1', 'A'*70 + adapter), read('B/1', 'A'*20 + adapter), read('C/1', 'A'*130 + adapter)]
    b = [read('A/2', 'C'*70 + 'CTGTCTCTTATA'), read('B/2', 'C'*70 + 'CTGTCTCTTATA'), read('C/2', 'C'*10 + 'CTGTCTCTTATA')]
    add('paired-wgbs-cuts-and-rescue', '0.6.10', a,
        ['--phred', '33', '--quality', '20', '--minimum-length', '20', '--maximum-length', '60', '--overlap', '3', '--adapter-kind', 'custom', '--adapter1', adapter, '--adapter2', 'CTGTCTCTTATA', '--clip-r2', '15', '--three-prime-clip-r1', '15', '--retain-unpaired', 'true'],
        ['--phred33', '--quality', '20', '--length', '20', '--max_length', '60', '--stringency', '3', '-a', adapter, '--adapter2', 'CTGTCTCTTATA', '--clip_R2', '15', '--three_prime_clip_R1', '15', '--retain_unpaired', '--paired'],
        {'trimmed_R1': [read('A/1', 'A'*55)], 'trimmed_R2': [read('A/2', 'C'*55)], 'unpaired_R1': [read('C/1', 'A'*115)], 'unpaired_R2': [read('B/2', 'C'*55)]}, b=b, qc=True)
    small = [read('adapter', 'C'*25 + 'TGGAATTCTCGG'), read('nineteen', 'C'*19), read('seventeen', 'C'*17)]
    add('auto-small-rna-default', '0.6.10', small, ['--phred', '33'], ['--phred33'], {'trimmed_R1': [read('adapter', 'C'*25), small[1]]})
    add('small-rna-preset', '0.6.7', small, ['--phred', '33', '--adapter-kind', 'small-rna'], ['--phred33', '--small_rna'], {'trimmed_R1': [read('adapter', 'C'*25), small[1]]})
    clean = [read('clean', 'CGT'*8)]
    add('auto-fallback-illumina', '0.6.7', clean, ['--phred', '33'], ['--phred33'], {'trimmed_R1': clean})
    already = [read('already', 'CGT'*8 + 'A')]
    add('auto-already-trimmed', '0.6.10', already, ['--phred', '33', '--already-trimmed-limit', '0'], ['--phred33', '--consider_already_trimmed', '0'], {'trimmed_R1': already})
    for name, preset, sequence, original_flag, version in [
            ('illumina', 'illumina', adapter, '--illumina', '0.6.7'), ('nextera', 'nextera', 'CTGTCTCTTATA', '--nextera', '0.6.10'),
            ('stranded', 'stranded-illumina', 'ACTGTCTCTTATACACATCT', '--stranded_illumina', '0.6.10')]:
        add(name + '-preset', version, [read(name, 'C'*40 + sequence)], ['--phred', '33', '--adapter-kind', preset, '--overlap', '3'],
            ['--phred33', original_flag, '--stringency', '3'], {'trimmed_R1': [read(name, 'C'*40)]})
    add('trim-terminal-n', '0.6.10', [read('Ns', 'NNACGTACGTNN')], ['--phred', '33', '--minimum-length', '1', '--adapter-kind', 'none', '--trim-n', 'true'],
        ['--phred33', '--length', '1', '-a', 'X', '--trim-n'], {'trimmed_R1': [read('Ns', 'ACGTACGT')]})
    ns = [read('upper', 'NACGTACG'), read('lower', 'nnACGTnn'), read('clean', 'ACGTACGT')]
    add('integer-n-legacy', '0.6.7', ns, ['--phred', '33', '--minimum-length', '1', '--adapter-kind', 'none', '--maximum-n', '0'],
        ['--phred33', '--length', '1', '-a', 'X', '--max_n', '0'], {'trimmed_R1': ns[1:]})
    add('fractional-n-original-single-quirk', '0.6.10', ns, ['--phred', '33', '--minimum-length', '1', '--adapter-kind', 'none', '--maximum-n', '0.5'],
        ['--phred33', '--length', '1', '-a', 'X', '--max_n', '0.5'], {'trimmed_R1': ns[1:]})
    pa, pb = [read('N/1', 'NACGTACG')], [read('N/2', 'ACGTACGT')]
    add('fractional-n-original-pair', '0.6.10', pa, ['--phred', '33', '--minimum-length', '1', '--adapter-kind', 'none', '--maximum-n', '0.5'],
        ['--phred33', '--length', '1', '-a', 'X', '--max_n', '0.5', '--paired'], {'trimmed_R1': pa, 'trimmed_R2': pb}, b=pb)
    qa, qb = [read('short/1', 'A'*40)], [read('short/2', 'C'*40)]
    add('both-default-orphans-below-pair-min', '0.6.10', qa, ['--phred', '33', '--minimum-length', '50', '--adapter-kind', 'none', '--retain-unpaired', 'true'],
        ['--phred33', '--length', '50', '-a', 'X', '--retain_unpaired', '--paired'], {'trimmed_R1': [], 'trimmed_R2': [], 'unpaired_R1': qa, 'unpaired_R2': qb}, b=qb)
    add('phred64-original-shift', '0.6.7', [read('quality', 'ACGTACGT', 'hhhh@@@@')], ['--phred', '64', '--quality', '20', '--minimum-length', '1', '--adapter-kind', 'none'],
        ['--phred64', '--quality', '20', '--length', '1', '-a', 'X'], {'trimmed_R1': [read('quality', 'ACGT', 'hhhh')]})
    add('nextseq-original', '0.6.10', [read('G', 'ACGTACGTGGGG')], ['--phred', '33', '--nextseq', '20', '--minimum-length', '1', '--adapter-kind', 'none'],
        ['--phred33', '--nextseq', '20', '--length', '1', '-a', 'X'], {'trimmed_R1': [read('G', 'ACGTACGT')]})
    hard = [read('long', 'ACGTACGTAC', '!#$%&()*+-'), read('short', 'AC'), read('empty', '')]
    add('hardtrim-last', '0.6.10', hard, ['--mode', 'hardtrim3', '--bases', '5'], ['--hardtrim3', '5'], {'trimmed_R1': [read('long', 'CGTAC', '()*+-'), hard[1], hard[2]]})
    opaque = [read('μ-read', 'ACGTACGT' + adapter)]
    add('gzip-unicode-hardtrim-header', '0.6.7', opaque, ['--mode', 'hardtrim5', '--bases', '8'],
        ['--hardtrim5', '8'], {'trimmed_R1': [read('μ-read', 'ACGTACGT')]}, compressed=True)
    add('utf8-options-file-path', '0.6.10', [read('path', 'ACGTACGT')], ['--phred', '33', '--minimum-length', '1', '--adapter-kind', 'none'],
        ['--phred33', '--length', '1', '-a', 'X'], {'trimmed_R1': [read('path', 'ACGTACGT')]}, compressed=True, options_file=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--jar', type=Path, required=True); parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--java', default=shutil.which('java')); parser.add_argument('--report', type=Path, required=True); args = parser.parse_args()
    cache = ROOT / 'build/literature-review/trimgalore-runtime'; directory = Path(tempfile.mkdtemp(prefix='original-packaged-', dir=cache)); steps = []
    marker_path = args.home / installer.MARKER; marker_sha = installer.digest(marker_path); profile = json.loads(marker_path.read_text()); jar_sha = installer.digest(args.jar)
    failure, status = None, 'failed'; comparisons = []
    def run(name, command, cwd, expected=0):
        result = subprocess.run(list(map(str, command)), cwd=cwd, capture_output=True, env=environment, timeout=240)
        log = directory / (name + '.log'); log.write_bytes(result.stdout + result.stderr)
        steps.append(dict(name=name, command=list(map(str, command)), cwd=str(cwd), exit_code=result.returncode, expected_exit=expected, log=log.name, log_sha256=installer.digest(log)))
        assert result.returncode == expected, (name, result.returncode, result.stdout[-1000:], result.stderr[-2000:]); return result
    environment = installer.environment(Path(profile['perl']), Path(profile['python'])); environment.update(PYTHONUTF8='1', PYTHONDONTWRITEBYTECODE='1')
    try:
        lock = json.loads(installer.LOCK.read_text()); assert installer.verified(args.home, lock)
        native_console = Path(profile['python']).parent / 'cutadapt.exe'; console_sha = installer.digest(native_console)
        for case in cases():
            name = case['name']; work = directory / name; work.mkdir(); reference = work / 'independent-original'; reference.mkdir(); native = work / ('native-result-μ' if case['options_file'] else 'native-result')
            inputs = []
            for index, values in enumerate([case['a']] + ([case['b']] if case['b'] is not None else []), 1):
                plain = reference / ('reads%d.fastq' % index); plain.write_bytes(fastq(values))
                source = work / (('input μ %d.fastq' if case['options_file'] else 'input with spaces %d.fastq') % index + ('.gz' if case['compressed'] else ''))
                source.write_bytes(gzip.compress(fastq(values), mtime=0) if case['compressed'] else fastq(values)); inputs.append(source)
            bridge = reference / 'cutadapt-console'
            command = [cygpath(Path(sys.executable)), '-I', '-S', '-B', '-X', 'utf8', str(ROOT / 'src/main/resources/org/jlinalg/omics/trim_galore_cutadapt.py'),
                       '--cutadapt', str(native_console), '--expected-sha256', console_sha, '--audit-dir', 'console-audit', '--']
            bridge.write_text('#!/bin/sh\nexec ' + ' '.join(shlex.quote(str(p)) for p in command) + ' "$@"\n', encoding='utf-8', newline='\n')
            run(name + '-chmod', [profile['perl'], '-e', 'chmod 0755, @ARGV or die $!;', bridge.name], reference)
            original = [profile['perl'], cygpath(args.home / case['release'] / 'trim_galore'), '--path_to_cutadapt', './cutadapt-console', '--dont_gzip', *case['original'], 'reads1.fastq']
            if case['b'] is not None:
                original.append('reads2.fastq')
            run(name + '-independent-original', original, reference)
            command = [args.java, '-jar', args.jar.resolve(), 'trim-galore-tools', '--home', args.home.resolve(), '--release', case['release'], '--reads', inputs[0], '--out', native, *case['cli']]
            if case['b'] is not None:
                command.extend(['--reads2', inputs[1]])
            if case['qc']:
                command.extend(['--fastqc', 'true'])
            if case['options_file']:
                fields = {str(command[i])[2:]: str(command[i+1]) for i in range(4, len(command), 2)}
                recipe = work / 'options.json'; recipe.write_text(json.dumps(fields, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
                command = [args.java, '-jar', args.jar.resolve(), 'trim-galore-tools', '--options-file', recipe]
            result = run(name + '-packaged', command, work)
            for label, expected in case['expected'].items():
                actual = records(native / (label + '.fastq.gz')); assert actual == expected, (name, label, actual, expected)
                hard = '--mode' in case['cli']
                mate = '1' if label.endswith('R1') else '2'
                if hard:
                    mode = case['cli'][case['cli'].index('--mode')+1]; count = case['cli'][case['cli'].index('--bases')+1]
                    original_name = 'reads' + mate + '.' + count + 'bp_' + ('5prime' if mode == 'hardtrim5' else '3prime') + '.fq'
                else:
                    original_name = ('reads' + mate + '_unpaired_' + mate + '.fq') if label.startswith('unpaired') else ('reads' + mate + '_val_' + mate + '.fq') if case['b'] is not None else 'reads1_trimmed.fq'
                assert records(reference / original_name) == actual, (name, 'independent-original', label)
            state = json.loads((native / 'analysis-status.json').read_text())['status']; assert state in ['completed', 'completed-with-qc-warnings']
            if state.endswith('warnings'):
                assert b'QC WARNINGS' in result.stderr and 'Required action' in (native / 'WARNINGS.md').read_text()
            if name == 'original-incomplete-adapter-warning':
                assert state == 'completed-with-qc-warnings'
                warning = (native / 'WARNINGS.md').read_text()
                assert 'adapter sequences may be incomplete' in warning and 'Ignore this warning when trimming primers' in warning
                assert 'adapter sequences may be incomplete' in (reference / 'reads1.fastq_trimming_report.txt').read_text()
            if case['qc']:
                rows = list(csv.DictReader((native / 'fastqc-results.tsv').open(), delimiter='\t')); assert rows
                for qc in (native / 'original').glob('*_fastqc.zip'):
                    with zipfile.ZipFile(qc) as archive:
                        summary = next(n for n in archive.namelist() if n.endswith('/summary.txt'))
                        for line in archive.read(summary).decode().splitlines():
                            flag, module, filename = line.split('\t')
                            assert any(row['module'] == module and row['qc_status'] == flag and row['input_filename'] == filename for row in rows)
            comparisons.append(dict(name=name, release=case['release'], input_reads_or_pairs=len(case['a']), verified_output_reads=sum(map(len, case['expected'].values())), independent_original=True, native_state=state))
            print('PASSED', name, flush=True)
        # Preserve the original ASCII-input restriction and improve the early diagnosis.
        unsupported = directory / 'nonascii-original'; unsupported.mkdir(); (unsupported / 'reads1.fastq').write_bytes(fastq([read('μ-bad', 'ACGTACGT')]))
        first_bridge = directory / 'single-quality-0.6.7/independent-original/cutadapt-console'
        run('nonascii-header-independent-original-failure', [profile['perl'], cygpath(args.home / '0.6.7/trim_galore'), '--path_to_cutadapt', cygpath(first_bridge), '--dont_gzip', '--phred33', '--length', '1', '-a', 'X', 'reads1.fastq'], unsupported, 1)
        rejected = directory / 'nonascii-native'
        run('nonascii-header', [args.java, '-jar', args.jar.resolve(), 'trim-galore-tools', '--home', args.home.resolve(), '--release', '0.6.7', '--reads', unsupported / 'reads1.fastq', '--out', rejected, '--phred', '33', '--minimum-length', '1', '--adapter-kind', 'none'], directory, 2)
        assert 'rejects non-ASCII read headers/comments' in (rejected / 'WARNINGS.md').read_text() and not (rejected / 'results.tsv').exists()
        # Real missing/corrupt profile states must skip rather than install/repair.
        source = directory / 'negative.fastq'; source.write_bytes(fastq([read('A', 'ACGTACGT')]))
        bad = directory / 'changed-private-profile'; shutil.copytree(args.home, bad); (bad / '0.6.10/trim_galore').write_bytes((bad / '0.6.10/trim_galore').read_bytes() + b'\n# changed private original\n')
        for name, home in [('missing-profile', directory / 'absent'), ('changed-original', bad)]:
            output = directory / name
            result = run(name, [args.java, '-jar', args.jar.resolve(), 'trim-galore-tools', '--home', home, '--release', '0.6.10', '--phred', '33', '--reads', source, '--out', output], directory, 3)
            assert b'ANALYSIS SKIPPED' in result.stderr and not (output / 'results.tsv').exists()
            assert 'Required action' in (output / 'WARNINGS.md').read_text()
        assert installer.verified(args.home, lock) and installer.digest(marker_path) == marker_sha and installer.digest(args.jar) == jar_sha
        status = 'passed-original-ordinary-and-hardtrim-workflows'
    except Exception as error:
        failure = repr(error)
    record = dict(status=status, error=failure, directory=str(directory), jar_sha256=jar_sha, runtime_marker_sha256=marker_sha, verifier_sha256=installer.digest(Path(__file__)),
                  comparisons=comparisons, packaged_calls=sum(s['name'].endswith('-packaged') or s['name'] in ['missing-profile', 'changed-original', 'nonascii-header'] for s in steps),
                  independent_original_runs=sum(s['name'].endswith('-independent-original') and s['exit_code'] == 0 for s in steps), steps=steps,
                  windows_path_boundary='Current native Windows JVM Cp1252 launchers do not round-trip non-ANSI CLI path arguments. UTF8options-file passes the actual data path without that conversion;the recipe path on the command line is ASCII. Ordinary Cutadapt rejects UTF8headers;hardtrim preserves them. Both historical failures remain retained.',
                  scope='Actual packaged Java adapter compared with separately launched unmodified original Perl scripts and native Cutadapt console launcher using an older independent LF bridge. Literal original flags and hand-checked golden streams. FastQC ZIP flags are separately matched to the promoted QC table. Negative ordinary-ASCII contract and dependency cases are explicit. No RRBS,explicit poly-A,old-version/old-FastQC or full-paper parity.')
    assert not args.report.exists(), 'Preserve previous validation report'; args.report.parent.mkdir(parents=True, exist_ok=True); args.report.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(dict(status=status, error=failure, packaged_calls=record['packaged_calls'], independent_original_runs=len(comparisons))), flush=True)
    return 0 if status.startswith('passed') else 1


if __name__ == '__main__':
    raise SystemExit(main())
