"""Run three synthetic transcript-site CLI workflows and audit every output against a full scan."""
from pathlib import Path
from decimal import Decimal
import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys


class Unavailable(Exception):
    """Java cannot run; the affected analysis must remain skipped."""


def check(condition, cause):
    if not condition:
        raise ValueError(cause)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        rows = list(reader)
        check(reader.fieldnames and len(set(reader.fieldnames)) == len(reader.fieldnames), 'Invalid TSV header: ' + str(path))
        check(all(None not in row and all(value is not None for value in row.values()) for row in rows), 'Malformed TSV row: ' + str(path))
        return rows


def write_table(path, header, rows):
    with path.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
        writer.writerow(header)
        writer.writerows(rows)


def reference(variants, transcripts, options):
    """Independent arbitrary-integer/Decimal full scan; no binary-search index."""
    output = {'variants': [], 'transcripts': [], 'hits': [], 'genes': []}
    counts = [[0, 0] for _ in transcripts]
    for vi, variant in enumerate(variants, 1):
        context, vid, chromosome, position, p, _ = variant
        hits, genes, selected_transcripts = [], {}, set()
        if p == 'NA':
            status = 'MISSING_P_EXCLUDED'
        elif Decimal(p) > Decimal(options['p-cutoff']):
            status = 'ABOVE_P_CUTOFF'
        else:
            status = 'UNMAPPED_IN_SUPPLIED_REFERENCE'
            for ti, transcript in enumerate(transcripts, 1):
                gene, tx, tchr, start, end, strand, biotype = transcript
                if chromosome != tchr or biotype not in options['gene-biotypes'].split(','):
                    continue
                for site in ['TSS', 'TES']:
                    if site not in options['sites'].split(','):
                        continue
                    anchor = int(start if (site == 'TSS') == (strand == '+') else end)
                    delta, radius = int(position)-anchor, int(options['radius-bp'])
                    distance = abs(delta)
                    inside = distance <= radius if options['boundary'] == 'INCLUSIVE' else distance < radius
                    if inside:
                        hits.append([str(vi), str(ti), context, vid, gene, tx, chromosome, site, str(anchor), strand, str(delta if strand == '+' else -delta), str(distance)])
                        selected_transcripts.add(ti)
                        counts[ti-1][0 if site == 'TSS' else 1] += 1
                        if gene not in genes:
                            genes[gene] = [set(), 0, distance]
                        genes[gene][0].add(ti)
                        genes[gene][1] += 1
                        genes[gene][2] = min(genes[gene][2], distance)
            if hits:
                status = 'MAPPED'
        output['hits'].extend(hits)
        nearest = str(min(int(row[-1]) for row in hits)) if hits else 'NA'
        output['variants'].append([str(vi), context, vid, chromosome, position, p, status, str(len(hits)), str(len(selected_transcripts)), str(len(genes)), nearest])
        for gene, (txs, count, nearest) in genes.items():
            output['genes'].append([str(vi), context, vid, gene, str(len(txs)), str(count), str(nearest)])
    for ti, transcript in enumerate(transcripts, 1):
        gene, tx, chromosome, start, end, strand, biotype = transcript
        output['transcripts'].append([str(ti), *transcript, start if strand == '+' else end, end if strand == '+' else start,
            str(biotype in options['gene-biotypes'].split(',')).lower(), str('TSS' in options['sites'].split(',')).lower(), str('TES' in options['sites'].split(',')).lower(), *map(str, counts[ti-1])])
    return output


def verify_bundle(bundle, wanted, options, variants, transcripts):
    check(read(bundle/'analysis-status.json') == {'status': 'completed-with-review-warnings', 'exit_code': 0}, 'Analysis did not admit completion')
    session = read(bundle/'session.json')
    check(session['status'] == 'completed-with-review-warnings' and session['external_runtime_required'] is False, 'Session is not a completed Java-only analysis')
    text = (bundle/'manifest.yaml').read_text(encoding='utf-8')
    check(re.search(r'^operation: transcript-site-mapping\s*$', text, re.MULTILINE), 'Unexpected manifest operation')
    check(re.search(r'^analysis_status: completed-with-review-warnings\s*$', text, re.MULTILINE), 'Manifest does not admit completion')
    # This reads only the executable's generated flat ASCII hash map, not general YAML.
    match = re.search(r'^output_sha256: \{(.*?)\}\s*\ncompleted_at:', text, re.MULTILINE | re.DOTALL)
    check(match is not None, 'Unsupported generated manifest hash format')
    content = match.group(1)
    pattern = r'([A-Za-z0-9_./\\-]+): ([0-9a-f]{64})'
    tokens = list(re.finditer(pattern, content))
    check(tokens and re.fullmatch(r'[\s,]*', re.sub(pattern, '', content)), 'Invalid generated hash ledger')
    outputs = {}
    for token in tokens:
        name = token.group(1).replace('\\', '/')
        check(name not in outputs and all(part not in ['', '.', '..'] for part in name.split('/')), 'Invalid/duplicate hash member')
        outputs[name] = token.group(2)
    actual = {p.relative_to(bundle).as_posix() for p in bundle.rglob('*') if p.is_file() and p.name != 'manifest.yaml'}
    check(actual == set(outputs), 'Complete output member population differs')
    for name, digest in outputs.items():
        check(not (bundle/name).is_symlink() and sha(bundle/name) == digest, 'Output hash differs: ' + name)
    ledger = table(bundle/'resource-ledger.tsv')
    check(len(ledger) == 3 and len(session['source_sha256']) == 3, 'Expected carrier and both exact input snapshots')
    for row in ledger:
        original, snapshot = Path(row['source']), bundle/row['snapshot']
        check(sha(original) == sha(snapshot) == row['sha256'] == session['source_sha256'][row['source']], 'Source/snapshot identity differs: ' + row['role'])
        check(original.stat().st_size == snapshot.stat().st_size == int(row['bytes']), 'Source/snapshot size differs')
    definitions = [('variants', 'variant-dispositions.tsv'), ('transcripts', 'transcript-dispositions.tsv'), ('hits', 'site-hits.tsv'), ('genes', 'gene-mappings.tsv')]
    for key, name in definitions:
        rows = table(bundle/name)
        check(len(rows) == len(wanted[key]), 'Complete row count differs: ' + name)
        for row, expected in zip(rows, wanted[key]):
            values = list(row.values())
            check(len(values) == len(expected), 'Output width differs: ' + name)
            if key == 'variants' and expected[5] != 'NA':
                check(Decimal(values[5]) == Decimal(expected[5]), 'Exact decimal P differs')
                values[5] = expected[5]
            check(values == expected, 'Original ordered identity/count/distance/disposition differs: ' + name)
    policy = table(bundle/'mapping-policy.tsv')
    check(len(policy) == 1, 'Expected one explicit mapping policy')
    expected_policy = {key.replace('-', '_'): options[key] for key in ['analysis-id', 'variant-namespace', 'context-namespace', 'reference-id', 'reference-build', 'gene-namespace', 'transcript-namespace', 'radius-bp', 'boundary', 'sites', 'missing-p', 'expected-variant-rows', 'expected-transcript-rows']}
    expected_policy.update(variant_build=options['build'], p_cutoff_decimal=policy[0]['p_cutoff_decimal'], gene_biotypes=','.join(sorted(options['gene-biotypes'].split(','))))
    check(policy[0] == expected_policy and Decimal(policy[0]['p_cutoff_decimal']) == Decimal(options['p-cutoff']), 'Declared mapping/resource policy differs')
    check(session['variant_rows'] == len(variants) and session['transcript_rows'] == len(transcripts) and session['site_hits'] == len(wanted['hits']) and session['unique_variant_gene_mappings'] == len(wanted['genes']), 'Session population differs')
    check(session['scientific_rows'] == 1+sum(len(wanted[k]) for k in wanted), 'Scientific output row population differs')
    scientific_bytes = sum(p.stat().st_size for p in bundle.rglob('*') if p.is_file() and (p.suffix == '.tsv' or p.parent.name == 'source-inputs'))
    check(scientific_bytes == session['scientific_files_and_snapshot_bytes'] and session['snapshot_bytes'] == sum(int(row['bytes']) for row in ledger), 'Scientific/snapshot byte population differs')
    check(session['input_cells'] == (len(variants)+1)*6+(len(transcripts)+1)*7, 'Input cell population differs')
    warnings = (bundle/'WARNINGS.md').read_text(encoding='utf-8')
    check('Required action:' in warnings and 'No analysis installs software' in warnings and 'MISSING P EXCLUSIONS' in warnings and 'REPORTED ZERO P' in warnings, 'Missing interpretation/recovery warnings')
    return {'variants': len(variants), 'transcripts': len(transcripts), 'site_hits': len(wanted['hits']), 'gene_mappings': len(wanted['genes']), 'manifest_members': len(outputs), 'scientific_sha256': {name: sha(bundle/name) for _, name in definitions}}


def verify(args):
    work, jar = args.work.resolve(), args.jar.resolve()
    check(not work.exists(), 'Work directory already exists; choose a NEW directory')
    check(os.name != 'nt' or str(work).isascii() and str(jar).isascii(), 'Use entirely ASCII work/JAR command-line paths on Windows; Unicode input paths belong inside JSON')
    work.mkdir(parents=True)
    record = {'status': 'failed', 'runs': [], 'attempts': [], 'jar': str(jar), 'expected_jar_sha256': args.jar_sha256}
    try:
        check(re.fullmatch('[0-9a-f]{64}', args.jar_sha256) and jar.is_file() and sha(jar) == args.jar_sha256, 'Missing or changed pinned JAR; supply the intended candidate and its SHA-256')
        record['jar_sha256'] = sha(jar)
        try:
            version = subprocess.run([args.java, '-version'], capture_output=True, timeout=30)
        except (OSError, subprocess.TimeoutExpired) as failure:
            raise Unavailable(str(failure)) from failure
        (work/'java-version.stdout').write_bytes(version.stdout)
        (work/'java-version.stderr').write_bytes(version.stderr)
        match = re.search(r'version "(?:1\.)?(\d+)', (version.stdout+version.stderr).decode('utf-8', errors='replace'))
        if version.returncode != 0 or not match or int(match.group(1)) < 17:
            raise Unavailable('Java 17+ could not be admitted; inspect retained java-version logs')
        inputs = work/'inputs-α'
        inputs.mkdir()
        variants = [
            ['trait', 'start', '1', '100', '1e-6', 'α'], ['trait', 'interior', '1', '500000', '1e-1000', 'β'],
            ['trait', 'end', '1', '1000000', '1e-1000', 'γ'], ['trait', 'missing', '1', '100', 'NA', 'δ'],
            ['trait', 'above', '1', '100', '0.000010000000000000000000001', 'ε'], ['trait', 'exact', '1', '100', '1e-5', 'ζ'],
            ['other', 'start', '1', '100', '.9', 'η'], ['trait', 'reverse', '2', '600', '0', 'θ'],
            ['trait', 'left-edge', '1', '10100', '1e-6', 'ι'], ['trait', 'outside', '1', '10101', '1e-6', 'κ'],
            ['trait', 'right-edge', '1', '990000', '1e-6', 'λ'], ['trait', 'chromosome-alias', 'chr1', '100', '1e-6', 'μ']]
        transcripts = [['A', 'a1', '1', '100', '1000000', '+', 'protein_coding'], ['A', 'a2', '1', '100', '1000000', '+', 'protein_coding'],
            ['B', 'b', '2', '500', '600', '-', 'protein_coding'], ['C', 'c', '1', '100', '100', '+', 'protein_coding'], ['L', 'l', '1', '100', '200', '+', 'lncRNA']]
        write_table(inputs/'variants.tsv', ['context', 'variant_id', 'chromosome', 'position', 'p', 'source_note'], variants)
        write_table(inputs/'transcripts.tsv', ['gene_id', 'transcript_id', 'chromosome', 'start', 'end', 'strand', 'gene_biotype'], transcripts)
        for name, boundary, sites, radius, cutoff, biotypes in [('inclusive', 'INCLUSIVE', 'TSS,TES', '10000', '1e-5', 'protein_coding'), ('strict-tss', 'STRICT', 'TSS', '10000', '1e-5', 'protein_coding'), ('exact-sites', 'INCLUSIVE', 'TSS,TES', '0', '1', 'protein_coding,lncRNA')]:
            options = {'method': 'transcript-sites', 'analysis-id': 'synthetic-'+name, 'variants': 'inputs-α/variants.tsv', 'transcripts': 'inputs-α/transcripts.tsv',
                'expected-variant-rows': str(len(variants)), 'expected-transcript-rows': str(len(transcripts)), 'variants-sha256': sha(inputs/'variants.tsv'), 'transcripts-sha256': sha(inputs/'transcripts.tsv'),
                'out': name, 'build': 'synthetic-build', 'reference-build': 'synthetic-build', 'reference-id': 'synthetic-v1', 'gene-namespace': 'gene-v1', 'transcript-namespace': 'transcript-v1', 'variant-namespace': 'variant-v1', 'context-namespace': 'trait-v1',
                'p-cutoff': cutoff, 'radius-bp': radius, 'boundary': boundary, 'sites': sites, 'gene-biotypes': biotypes, 'missing-p': 'EXCLUDE'}
            carrier = work/(name+'.json')
            carrier.write_text(json.dumps(options, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
            wanted = reference(variants, transcripts, options)
            (work/(name+'-reference.json')).write_text(json.dumps(wanted, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
            command = [args.java, '-jar', str(jar), 'fuma-tools', '--site-options-file', str(carrier)]
            attempt = {'name': name, 'command': command, 'exit_code': None}
            record['attempts'].append(attempt)
            result = subprocess.run(command, capture_output=True, timeout=90)
            attempt['exit_code'] = result.returncode
            (work/(name+'.stdout')).write_bytes(result.stdout)
            (work/(name+'.stderr')).write_bytes(result.stderr)
            check(result.returncode == 0, 'Native analysis '+name+' failed; inspect its stderr, WARNINGS.md and partial/ diagnostics')
            record['runs'].append({'name': name, **verify_bundle(work/name, wanted, options, variants, transcripts)})
        check(sha(jar) == record['jar_sha256'], 'JAR changed during verification')
        record['status'] = 'passed-three-complete-synthetic-packaged-workflows'
    except Unavailable as failure:
        record.update(status='skipped-unavailable-or-forbidden-Java', exit_code=3, cause=str(failure))
        raise
    except Exception as failure:
        record.update(status='failed-retained', exit_code=2, cause=str(failure))
        raise
    finally:
        (work/'verification.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': record['status'], 'workflows': len(record['runs']), 'jar_sha256': record['jar_sha256']}))
    print('TRANSCRIPT-SITE EXAMPLES REVIEW REQUIRED. Required action: read every WARNINGS.md, complete input/disposition/hit/gene ledger and verification.json. Declare and justify the reference and mapping policies for your study.', file=sys.stderr)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--jar-sha256', required=True)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    try:
        verify(args)
    except Unavailable as failure:
        parser.exit(3, 'TRANSCRIPT-SITE VERIFICATION / ANALYSIS SKIPPED: '+str(failure)+'\nRequired action: provide Java17+ on a permitted host,using --java if needed. If installation is forbidden,retain the analysis as skipped. No command installs Java or other tools. Inspect retained version logs and verification.json;rerun to a NEW directory.\n')
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as failure:
        parser.exit(2, 'TRANSCRIPT-SITE VERIFICATION FAILED: '+str(failure)+'\nRequired action: inspect retained logs,verification.json and result WARNINGS.md;restore the pinned JAR or correct the named input,artifact,policy or capacity cause and rerun to a NEW directory. Keep existing outputs. Use ASCII work/JAR command-line paths on Windows and declare Unicode data paths inside UTF-8 JSON.\n')
