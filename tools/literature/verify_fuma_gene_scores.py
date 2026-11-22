#!/usr/bin/env python3
"""Replay synthetic supplied FUMA gene-score annotations using Python3 and a supplied Java JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--case')
    args = parser.parse_args()
    output = args.out.absolute()
    try:
        output.mkdir(parents=True, exist_ok=False)
    except OSError as failure:
        print('ANALYSIS FAILED: '+str(failure)+'\nRequired action: choose a new writable --out directory.', file=sys.stderr)
        return 2
    record = dict(status='running', cases=[], scope='Synthetic supplied raw annotation join only; no scores calculated and no paper-data reconstruction.')
    code = 2
    try:
        java = shutil.which(args.java)
        if java is None:
            record.update(status='skipped-missing-java', required_action='Provide an installed supported Java executable with --java on a permitted host and rerun to a new directory. No analysis ran; this verifier never installs software.')
            code = 3
            return code
        jar = args.jar.absolute()
        fixture = Path(__file__).resolve().parent/'fixtures/fuma-gene-scores/reference-cases.json'
        provenance = json.loads(fixture.with_name('provenance.json').read_text(encoding='utf-8'))
        if digest(fixture) != provenance['fixture_sha256']:
            raise ValueError('Reference fixture hash differs from provenance')
        data = json.loads(fixture.read_text(encoding='utf-8'))
        if data['schema_version'] != 1 or len(data['cases']) != 29:
            raise ValueError('Unexpected fixture schema or case count')
        selected = [c for c in data['cases'] if args.case is None or args.case == c['name']]
        if not selected:
            raise ValueError('Unknown requested case')
        inputs = output/'inputs'
        inputs.mkdir()
        for name, content in data['inputs'].items():
            if Path(name).name != name or '\\' in name or name in {'.', '..'}:
                raise ValueError('Unsafe reference resource name')
            (inputs/name).write_text(content, encoding='utf-8', newline='\n')
        record.update(java=java, candidate_sha256=digest(jar), fixture_sha256=digest(fixture))
        for case in selected:
            name = case['name']
            if not name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-' for c in name):
                raise ValueError('Unsafe case name')
            options = dict(case['options'])
            for key in ['genes', 'candidates', 'annotations', 'interactions', 'gene-scores']:
                options[key] = str(inputs/options[key])
            options['out'] = str(output/name)
            carrier = output/(name+'.json')
            carrier.write_text(json.dumps(options, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
            result = subprocess.run([java, '-jar', str(jar), 'fuma-tools', '--mapping-options-file', str(carrier), '--no-config'], capture_output=True, timeout=60)
            (output/(name+'.stdout')).write_bytes(result.stdout)
            (output/(name+'.stderr')).write_bytes(result.stderr)
            entry = dict(name=name, exit_code=result.returncode)
            record['cases'].append(entry)
            if result.returncode != case['expected_exit_code']:
                raise ValueError('Unexpected exit code: '+name)
            directory = output/name
            warning = (directory/'WARNINGS.md').read_text(encoding='utf-8')
            if 'Required action' not in warning:
                raise ValueError('Missing actionable warning: '+name)
            status = json.loads((directory/'analysis-status.json').read_text(encoding='utf-8'))['status']
            if case['expected_exit_code'] == 2:
                diagnostics = warning+(directory/'session.json').read_text(encoding='utf-8')+result.stderr.decode('utf-8', errors='replace')
                if case['required_error'].lower() not in diagnostics.lower() or (directory/'genes.tsv').exists() or status.startswith('completed'):
                    raise ValueError('Failure did not withhold results or name recovery cause: '+name)
            else:
                if not status.startswith('completed'):
                    raise ValueError('Missing completed status: '+name)
                hashes = {}
                for filename, expected in case['expected_tables'].items():
                    if Path(filename).name != filename or not filename.endswith('.tsv'):
                        raise ValueError('Unsafe expected table name')
                    actual = directory/filename
                    if actual.read_text(encoding='utf-8') != expected:
                        raise ValueError('Reference mismatch: '+name+'/'+filename)
                    hashes[filename] = digest(actual)
                entry['table_sha256'] = hashes
            entry['status'] = 'passed'
            print('PASS '+name, flush=True)
        if digest(jar) != record['candidate_sha256'] or digest(fixture) != record['fixture_sha256']:
            raise ValueError('Candidate or reference changed during replay')
        record.update(status='passed', required_action='Review every score resource identity, raw payload definition, exact-ID join, duplicate/missing policy, original R differences and warning before use. This validates annotation copying only.')
        code = 0
        return code
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        record.update(status='failed', error=str(failure), required_action='Inspect the named mismatch, retained stderr and stage warnings. Correct the JAR/resource/bound and rerun to a new directory; failed controls are never admitted results.')
        return code
    finally:
        record['completed_protocols'] = sum(c.get('status') == 'passed' for c in record['cases'])
        (output/'verification.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        warning = '# FUMA GENE-SCORE REPLAY '+record['status'].upper()+'\n\nRequired action: '+record.get('required_action', 'Inspect retained diagnostics.')+'\n'
        (output/'WARNINGS.md').write_text(warning, encoding='utf-8')
        print(warning.strip(), file=sys.stderr)


if __name__ == '__main__':
    sys.exit(main())
