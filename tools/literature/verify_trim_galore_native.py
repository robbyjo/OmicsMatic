"""Compare the actual packaged native command with frozen original emissions."""
import argparse
import csv
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'src/test/resources/trim-galore'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def records(path):
    data = gzip.decompress(path.read_bytes()) if path.name.endswith('.gz') else path.read_bytes()
    lines = data.decode('utf-8').splitlines()
    assert len(lines) % 4 == 0, path
    result = []
    for offset in range(0, len(lines), 4):
        header, sequence, plus, quality = lines[offset:offset+4]
        assert plus == '+' and len(sequence) == len(quality), path
        result.append((header, sequence, quality))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--java', default=shutil.which('java'))
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    cache = ROOT / 'build/literature-review/trimgalore-runtime'
    cache.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix='native-packaged-', dir=cache))
    steps = []
    original = sha(args.jar)
    error, state = None, 'failed'
    observed_rows = 0

    def run(name, options, expected=0):
        command = [args.java, '-jar', str(args.jar.resolve()), 'trim-galore-reads', *map(str, options)]
        child = subprocess.run(command, capture_output=True, timeout=180)
        log = directory / (name + '.log')
        log.write_bytes(child.stdout + child.stderr)
        steps.append(dict(name=name, command=command, exit_code=child.returncode, expected_exit=expected, log=log.name, log_sha256=sha(log)))
        assert child.returncode == expected, (name, child.returncode, child.stdout, child.stderr)
        return child

    try:
        provenance = json.loads((FIXTURES / 'reference-provenance.json').read_text(encoding='utf-8'))
        assert provenance['reference_sha256'] == sha(FIXTURES / 'reference.tsv')
        groups = {}
        with (FIXTURES / 'reference.tsv').open(encoding='utf-8', newline='') as stream:
            for row in csv.DictReader(stream, delimiter='\t', quoting=csv.QUOTE_NONE):
                groups.setdefault((row['release'], row['case']), []).append(row)
        assert len(groups) == 38
        for (release, case), rows in groups.items():
            name = release + '-' + case
            work = directory / name; work.mkdir()
            first = rows[0]
            paired = first['mode'] == 'pair'
            inputs = []
            for mate in range(1, 3 if paired else 2):
                source = work / ('reads%d.fastq' % mate)
                with source.open('w', encoding='utf-8', newline='\n') as stream:
                    for row in rows:
                        stream.write(row['header%d' % mate] + '\n' + row['seq%d' % mate] + '\n+\n' + row['qual%d' % mate] + '\n')
                inputs.append(source)
            hashes = {str(path): sha(path) for path in inputs}
            destination = work / 'result'
            options = ['--reads', inputs[0], '--out', destination]
            if paired:
                options.extend(['--reads2', inputs[1]])
            if first['mode'].startswith('hard'):
                options.extend(['--mode', 'hardtrim5' if first['mode'] == 'hard5' else 'hardtrim3', '--bases', first['hard_count']])
            else:
                options.extend(['--mode', 'finalize', '--release', release, '--input-stage', 'post-cutadapt', '--minimum-length', first['min_length']])
                for key, flag in [('clip5_1', 'clip-r1'), ('clip3_1', 'three-prime-clip-r1'), ('clip5_2', 'clip-r2'), ('clip3_2', 'three-prime-clip-r2'), ('max_length', 'maximum-length')]:
                    if first[key] != '0':
                        options.extend(['--' + flag, first[key]])
                if first['max_n'] != '~':
                    options.extend(['--maximum-n', first['max_n']])
                if paired and first['rescue'] == '1':
                    options.extend(['--retain-unpaired', 'true'])
                    for key, flag in [('orphan_min1', 'orphan-min1'), ('orphan_min2', 'orphan-min2')]:
                        if first[key] != '~':
                            options.extend(['--' + flag, first[key]])
            child = run(name, options)
            for prefix, mate, file in [('out1', 1, 'trimmed_R1'), ('out2', 2, 'trimmed_R2'), ('orphan1', 1, 'unpaired_R1'), ('orphan2', 2, 'unpaired_R2')]:
                expected = [(row['header%d' % mate], row[prefix + '_seq'], row[prefix + '_qual']) for row in rows if row[prefix + '_seq'] != '~']
                path = destination / (file + '.fastq.gz')
                should_exist = file == 'trimmed_R1' or paired and (file == 'trimmed_R2' or first['rescue'] == '1')
                assert path.is_file() == should_exist, (name, file)
                if path.is_file():
                    assert records(path) == expected, (name, file)
            status = json.loads((destination / 'analysis-status.json').read_text())['status']
            assert status in ['completed', 'completed-with-qc-warnings']
            if status.endswith('warnings'):
                assert b'QC WARNINGS' in child.stderr
                assert 'Required action' in (destination / 'WARNINGS.md').read_text()
            ledger = (destination / 'read-status.tsv').read_text(encoding='utf-8').splitlines()
            assert len(ledger) == len(rows) + 1
            for path, digest in hashes.items():
                assert sha(Path(path)) == digest and digest in (destination / 'manifest.yaml').read_text()
            observed_rows += len(rows)
            print(name, 'PASSED', len(rows), flush=True)
        # A real malformed pair must fail after preserving an already-written prefix.
        a, b = directory / 'failure-r1.fastq', directory / 'failure-r2.fastq'
        a.write_text('@A/1\nACGT\n+\nIIII\n@B/1\nACGT\n+\nIIII\n')
        b.write_text('@A/2\nTGCA\n+\nIIII\n@wrong/2\nTGCA\n+\nIIII\n')
        failed = directory / 'failed-pair'
        child = run('mismatched-pair', ['--mode', 'finalize', '--release', '0.6.10', '--input-stage', 'post-cutadapt', '--minimum-length', '1', '--reads', a, '--reads2', b, '--out', failed], 2)
        assert b'READ PROCESSING FAILED' in child.stderr
        assert not (failed / 'results.tsv').exists() and (failed / 'partial/trimmed_R1.fastq.gz').is_file()
        assert 'Required action' in (failed / 'WARNINGS.md').read_text()
        # Compressed input, opaque Unicode/comment headers and exact qualities.
        compressed = directory / 'opaque.fastq.gz'
        payload = '@μ-read original\tcomment\nACGT\n+\n!#$%\n'.encode('utf-8')
        compressed.write_bytes(gzip.compress(payload, mtime=0))
        opaque = directory / 'opaque-result'
        run('gzip-opaque-header', ['--mode', 'hardtrim3', '--bases', '2', '--reads', compressed, '--out', opaque, '--gzip', 'false'])
        assert records(opaque / 'trimmed_R1.fastq') == [('@μ-read original\tcomment', 'GT', '$%')]
        assert sha(args.jar) == original and observed_rows == 1692
        state = 'passed-native-original-read-operation-subsets'
    except Exception as failure:
        error = repr(failure)
    record = dict(status=state, error=error, directory=str(directory), jar_sha256=original,
                  verifier_sha256=sha(Path(__file__)), fixture_sha256=sha(FIXTURES / 'reference.tsv'),
                  original_case_groups=38, observed_read_rows=observed_rows, packaged_calls=len(steps), steps=steps,
                  scope='Actual native packaged command against original 0.6.7/0.6.10 emission fixtures, plus failure quarantine and gzip/opaque-header contracts. No original upstream analysis adapter, adapter alignment, quality, RRBS, poly-A or whole-paper parity claim.',
                  recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    args.report.parent.mkdir(parents=True, exist_ok=True)
    assert not args.report.exists(), 'Refuse to overwrite a previous validation report'
    args.report.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: record[k] for k in ['status', 'error', 'jar_sha256', 'observed_read_rows', 'packaged_calls']}), flush=True)
    return 0 if state.startswith('passed') else 1


if __name__ == '__main__':
    raise SystemExit(main())
