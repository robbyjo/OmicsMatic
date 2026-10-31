#!/usr/bin/env python3
"""Replay synthetic FUMA chromatin mapping references using only Python3 and a supplied Java JAR."""
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
    parser.add_argument('--case', help='Run one named protocol instead of all80')
    args = parser.parse_args()
    output = args.out.absolute()
    try:
        output.mkdir(parents=True, exist_ok=False)
    except OSError as failure:
        print('ANALYSIS FAILED: '+str(failure)+'\nRequired action: choose a new writable --out directory.', file=sys.stderr)
        return 2
    record = dict(status='running', cases=[], scope='Synthetic positional/eQTL/chromatin subset replay; not full FUMA or paper-data reconstruction.')
    code = 2
    try:
        java = shutil.which(args.java)
        if java is None:
            record['status'] = 'skipped-missing-java'
            record['required_action'] = 'Provide an installed supported Java executable using --java on a permitted host, then rerun to a new directory. No analysis ran; this verifier never installs software.'
            code = 3
            return code
        jar = args.jar.absolute()
        if not jar.is_file():
            raise ValueError('Missing supplied candidate JAR: '+str(jar))
        fixture = Path(__file__).resolve().parent/'fixtures/fuma-chromatin/reference-cases.json'
        provenance = json.loads(fixture.with_name('provenance.json').read_text(encoding='utf-8'))
        if digest(fixture) != provenance['fixture_sha256']:
            raise ValueError('Reference fixture hash differs from recorded provenance')
        data = json.loads(fixture.read_text(encoding='utf-8'))
        if data['schema_version'] != 1 or len(data['cases']) != 80:
            raise ValueError('Unexpected reference schema or protocol count')
        selected = [c for c in data['cases'] if args.case is None or c['name'] == args.case]
        if not selected:
            raise ValueError('Unknown requested protocol: '+str(args.case))
        inputs = output/'inputs'
        inputs.mkdir()
        for name, content in data['inputs'].items():
            if Path(name).name != name or '\\' in name or name in {'.', '..'}:
                raise ValueError('Unsafe fixture resource name')
            (inputs/name).write_text(content, encoding='utf-8', newline='\n')
        record.update(jar=str(jar), candidate_sha256=digest(jar), fixture_sha256=digest(fixture), java=java, original_source_sha256=data['source_sha256'])
        for case in selected:
            name = case['name']
            if not name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-' for c in name):
                raise ValueError('Unsafe reference protocol name')
            options = dict(case['options'])
            for key in ['genes', 'candidates', 'annotations', 'consequences', 'eqtls', 'interactions', 'ci-enhancers', 'ci-promoters']:
                if key in options and options[key] != 'none':
                    resource = options[key]
                    if resource not in data['inputs']:
                        raise ValueError('Unknown fixture resource: '+resource)
                    options[key] = str(inputs/resource)
            options['out'] = str(output/name)
            carrier = output/(name+'.json')
            carrier.write_text(json.dumps(options, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
            command = [java, '-jar', str(jar), 'fuma-tools', '--mapping-options-file', str(carrier), '--no-config']
            result = subprocess.run(command, capture_output=True, timeout=60)
            (output/(name+'.stdout')).write_bytes(result.stdout)
            (output/(name+'.stderr')).write_bytes(result.stderr)
            entry = dict(name=name, exit_code=result.returncode, original_exit_code=case['original_exit_code'])
            record['cases'].append(entry)
            if result.returncode != 0:
                raise ValueError('Native protocol failed: '+name+'; inspect its stderr and WARNINGS.md')
            hashes = {}
            for filename, expected in case['expected_tables'].items():
                if filename not in {'genes.tsv', 'positional-evidence.tsv', 'eqtl-evidence.tsv', 'candidate-disposition.tsv', 'excluded-genes.tsv', 'chromatin-evidence.tsv', 'chromatin-interactions.tsv'}:
                    raise ValueError('Unexpected reference table name')
                actual = output/name/filename
                if actual.read_text(encoding='utf-8') != expected:
                    raise ValueError('Reference mismatch: '+name+'/'+filename)
                hashes[filename] = digest(actual)
            status = json.loads((output/name/'analysis-status.json').read_text(encoding='utf-8'))['status']
            if not status.startswith('completed') or 'Required action' not in (output/name/'WARNINGS.md').read_text(encoding='utf-8'):
                raise ValueError('Missing completed status or actionable review warnings: '+name)
            entry.update(status='passed', table_sha256=hashes)
            print('PASS '+name, flush=True)
        if digest(jar) != record['candidate_sha256'] or digest(fixture) != record['fixture_sha256']:
            raise ValueError('Candidate or reference changed during replay')
        record.update(status='passed', required_action='Review retained source failures, native summary policy and every protocol WARNINGS.md before scientific use; this replay validates only the supplied-resource subset.')
        code = 0
        return code
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        record.update(status='failed', error=str(failure), required_action='Inspect the named failure, retained protocol outputs/stderr and source fixture. Correct the JAR, Java, resource or bound and rerun to a new output directory. No failed replay is a passed analysis.')
        return code
    finally:
        record['completed_protocols'] = sum(c.get('status') == 'passed' for c in record['cases'])
        (output/'verification.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        warning = '# FUMA CHROMATIN REPLAY '+record['status'].upper()+'\n\nRequired action: '+record.get('required_action', 'Inspect retained diagnostics and rerun to a new directory.')+'\n'
        (output/'WARNINGS.md').write_text(warning, encoding='utf-8')
        print(warning.strip(), file=sys.stderr)


if __name__ == '__main__':
    sys.exit(main())
