"""Exercise all six native scopes through the packaged Java executable."""
import argparse
import csv
import gzip
import hashlib
import json
from pathlib import Path
import platform
import subprocess


ROOT = Path(__file__).resolve().parents[2]


def table(path):
    with path.open(encoding='utf8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def reads(path):
    with gzip.open(path, 'rt', encoding='utf8') as stream:
        lines = stream.read().splitlines()
    assert len(lines) % 4 == 0
    return [(lines[i], lines[i + 1], lines[i + 3]) for i in range(0, len(lines), 4)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--verify-bundle', type=Path, help='Optionally replay an existing bundle produced on another Java/platform')
    args = parser.parse_args()
    jar, out = args.jar.resolve(), args.out.resolve()
    if out.exists():
        raise SystemExit('Require a new output directory')
    out.mkdir(parents=True)
    example = ROOT / 'examples/native-tools'
    calls = []

    def run(name, command, request=None):
        destination = out / name
        arguments = [args.java, '-jar', str(jar), *map(str, command)]
        if request is None:
            arguments += ['--out', str(destination)]
        else:
            request_path = out / (name + '.json')
            request_path.write_text(json.dumps(request, indent=2) + '\n', encoding='utf8')
            arguments += ['--request', str(request_path)]
        process = subprocess.run(arguments, cwd=ROOT, capture_output=True, timeout=180)
        (out / (name + '.stdout')).write_bytes(process.stdout)
        (out / (name + '.stderr')).write_bytes(process.stderr)
        assert process.returncode == 0, (name, process.returncode, process.stderr.decode(errors='replace'))
        status = json.loads((destination / 'analysis-status.json').read_bytes())
        assert status['result_admitted'] is True and status['engine'] == 'native-java', (name, status)
        calls.append({'name': name, 'command': arguments, 'exit_code': process.returncode, 'status': status})
        return destination

    enrichment = run('enrichment', ['gseapy-native', '--input', example / 'enrichr.json', '--edition', '1.1.8', '--identifier-policy', 'source'])
    result = table(enrichment / 'results.tsv')
    assert len(result) == 2 and abs(float(result[0]['P-value']) - .8) < 1e-13 and abs(float(result[1]['P-value']) - 1 / 15) < 1e-13
    qc = run('qc', ['sequence-qc', '--engine', 'native', '--method', 'fastqc', '--reads', example / 'reads_R1.fastq', '--reads2', example / 'reads_R2.fastq', '--no-config'])
    assert [r['reads'] for r in table(qc / 'results.tsv')] == ['3', '3']
    trim = run('trim', ['sequence-qc', '--engine', 'native', '--method', 'cutadapt', '--reads', example / 'reads_R1.fastq', '--reads2', example / 'reads_R2.fastq', '--adapter3', 'AGATCGGAAGAGC', '--adapter3-2', 'ACGT', '--quality-cutoff', '20', '--minimum-length', '6', '--error-rate', '0', '--no-config'])
    assert [r[1] for r in reads(trim / 'trimmed_R1.fastq.gz')] == ['ACGTTGCA', 'GGCCTTAACC']
    assert [r[1] for r in reads(trim / 'trimmed_R2.fastq.gz')] == ['TTGCAACG', 'CCAATTGG']
    mapping = out / 'reports.tsv'
    mapping.write_text('sample_id\tfastqc_zip\tcutadapt_json\nS1\tqc/read1_native_qc.zip\ttrim/cutadapt.json\nS2\tqc/read2_native_qc.zip\t\n', encoding='utf8')
    report = run('report', ['sequence-qc', '--engine', 'native', '--method', 'multiqc', '--reports', mapping, '--no-config'])
    assert len(table(report / 'results.tsv')) == 3 and (report / 'multiqc_report.html').is_file()
    sorted_run = run('sorted', ['hts-tools', '--engine', 'native', '--method', 'sam-sort', '--input', example / 'reads.sam', '--create-index', 'true', '--max-records-in-ram', '1', '--no-config'])
    assert (sorted_run / 'alignment.bai').is_file()
    selected = run('selected', ['hts-tools', '--engine', 'native', '--method', 'sam-view', '--input', sorted_run / 'alignment.bam', '--region', 'chr1:1-100', '--no-config'])
    assert {r['metric']: r['value'] for r in table(selected / 'results.tsv')}['output_records'] == '1'
    run('index', ['hts-tools', '--engine', 'native', '--method', 'sam-index', '--input', sorted_run / 'alignment.bam', '--no-config'])
    flags = run('flags', ['hts-tools', '--engine', 'native', '--method', 'sam-flagstat', '--input', example / 'reads.sam', '--no-config'])
    assert json.loads((flags / 'flagstat.json').read_bytes())['QC-passed reads']['mapped'] == 2
    specification = json.loads((example / 'htseq-count.json').read_bytes())
    specification.update(alignment=str(example / 'reads.sam'), annotation=str(example / 'genes.gtf'), out=str(out / 'count'))
    count = run('count', ['htseq-tools', '--engine', 'native'], specification)
    assert {r['feature']: float(r['count']) for r in table(count / 'counts.tsv')} == {'geneA': 1., 'geneB': 1.}
    verification = run('verify', ['htseq-tools', '--engine', 'native'], {'schema_version': 1, 'operation': 'VERIFY', 'run': str(count), 'out': str(out / 'verify')})
    assert json.loads((verification / 'verification.json').read_bytes())['native_file_preparation_and_count_replayed'] is True
    if args.verify_bundle is not None:
        run('cross-verify', ['htseq-tools', '--engine', 'native'], {'schema_version': 1, 'operation': 'VERIFY', 'run': str(args.verify_bundle.resolve()), 'out': str(out / 'cross-verify')})
    java = subprocess.run([args.java, '-version'], capture_output=True, timeout=30)
    receipt = {'status': 'PASS_ALL_SIX_NATIVE_SCOPES_PACKAGED', 'platform': platform.platform(),
               'java_version': (java.stdout + java.stderr).decode(errors='replace'),
               'jar': str(jar), 'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'calls': calls,
               'scope': 'Ten real packaged Java commands, six bounded native workflow scopes, synthetic inputs and known output assertions. No upstream analysis runtime or installation was used.'}
    (out / 'verification.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': receipt['status'], 'commands': len(calls), 'java': args.java, 'jar_sha256': receipt['jar_sha256']}))


if __name__ == '__main__':
    main()
