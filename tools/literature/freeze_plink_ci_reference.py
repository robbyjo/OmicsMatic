#!/usr/bin/env python3
"""Freeze actual CI reports only after their retained mathematical gate passes."""
from pathlib import Path
import argparse
import gzip
import hashlib
import io
import json

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--math-proof', type=Path, required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    proof = json.loads((source / 'verification.json').read_text())
    math_proof = json.loads(args.math_proof.read_text())
    assert proof['status'].startswith('passed-original-CI-process-contracts')
    assert math_proof['status'] == 'passed' and math_proof['original_proof_sha256'] == sha(source / 'verification.json')
    assert len(proof['steps']) == 30 and math_proof['independently_compared_SE_and_limits'] == 6912
    dest = ROOT / 'src/test/resources/org/jlinalg/genetics/plink-confidence'
    dest.mkdir(exist_ok=False)
    files = {}
    cases = ['name\tengine\tmodel\tlevel\tphenotype\tupstream_errors']
    for step in proof['steps']:
        report = Path(step['report'])
        assert step['exit_code'] == 0 and sha(report) == step['report_sha256']
        name = step['name'] + '.tsv.gz'
        content = io.BytesIO()
        with gzip.GzipFile(filename='', fileobj=content, mode='wb', mtime=0) as stream:
            stream.write(report.read_bytes())
        (dest / name).write_bytes(content.getvalue())
        files[name] = sha(dest / name)
        cases.append('\t'.join([name, step['engine'].upper(), step['model'].upper().replace('-', '_'),
                                step['level'], step['phenotype'], str(step['upstream_errors'])]))
    (dest / 'cases.tsv').write_text('\n'.join(cases) + '\n', encoding='utf-8')
    files['cases.tsv'] = sha(dest / 'cases.tsv')
    provenance = dict(scope='Actual original confidence/SE report fixtures on synthetic128sample96variant inputs. '
                            'No patient data or independent Firth estimator claim. Ordinary estimates and limits '
                            'passed independent NumPy/SciPy checks with the documented original inverse-normal '
                            'approximation bound; separated/Firth rows passed only reported-column consistency checks.',
                      files_sha256=files, producer_sha256=sha(__file__), original_reports=30, original_rows=2880,
                      original_proof_sha256=sha(source / 'verification.json'), mathematical_proof_sha256=sha(args.math_proof),
                      runtime_identity=proof['runtime_identity'], original_version_probes=proof['version_probes'],
                      input_sha256=proof['input_sha256'], cases=proof['steps'], mathematical_scope=math_proof['scope'],
                      original_inverse_normal_relative_error_bound=math_proof['original_inverse_normal_relative_error_bound'],
                      inverse_normal_sources=math_proof['inverse_normal_sources'])
    (dest / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(status='frozen', reports=30, rows=2880, files=len(files) + 1)))


if __name__ == '__main__':
    main()
