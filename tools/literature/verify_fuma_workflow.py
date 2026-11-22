#!/usr/bin/env python3
"""Exercise typed supplied-resource FUMA workflows, failures, skips and optional original MAGMA."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def magma(recipe, root, home):
    resources = root/'src/test/resources/magma'
    recipe['build'] = 'GRCh38'
    for name in ['mapping', 'gene_set']:
        recipe[name]['annotation-id'] = 'synthetic-GRCh38-v1'
    recipe['magma_annotate'] = {'method': 'annotate-native', 'genes': str(resources/'genes.tsv'), 'snps': str(resources/'snps.tsv')}
    recipe['magma_gene'] = {'home': str(home), 'reference-panel': str(resources/'reference'), 'ancestry': 'SYNTHETIC', 'input-mode': 'summary', 'pvalues': str(resources/'gwas.tsv'), 'model': 'mean', 'seed': '42'}
    recipe['magma_enrichment'] = {'method': 'sets', 'home': str(home), 'sets': str(resources/'sets.annot'), 'seed': '42'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--magma-home', type=Path, help='Select three additional original MAGMA model workflows; omitted means native/control scope only.')
    parser.add_argument('--case', help='Run one named control or original-magma-mean/top/multi protocol.')
    parser.add_argument('--unicode-paths', action='store_true', help='Use Unicode resource/output paths inside an ASCII JSON carrier, avoiding Windows launcher argument conversion.')
    args = parser.parse_args()
    output = args.out.absolute()
    try:
        output.mkdir(parents=True, exist_ok=False)
    except OSError as failure:
        print('ANALYSIS FAILED: '+str(failure)+'\nRequired action: choose a new writable output directory.', file=sys.stderr)
        return 2
    record = dict(status='running', cases=[], scope='Synthetic typed workflow integration and control tests. Native-only scope unless --magma-home explicitly selects original association workflows; no historical-paper or full hosted FUMA reconstruction.')
    code = 2
    try:
        java = shutil.which(args.java)
        if java is None:
            record.update(status='skipped-missing-java', required_action='Supply a supported permitted Java executable with --java and rerun to a new directory. No analysis ran; no automatic installation occurs.')
            code = 3
            return code
        root = Path(__file__).resolve().parents[2]
        jar = args.jar.absolute()
        source = root/'examples/fuma/workflow'
        source_files = [*source.iterdir(), *(root/'examples/fuma/loci').glob('*.tsv')]
        source_hashes = {str(p): digest(p) for p in source_files if p.is_file()}
        record.update(candidate_sha256=digest(jar), java=java, input_sha256=source_hashes)
        protocols = [('native-full', 0), ('without-scores', 0), ('separate-scores', 0), ('missing-score-retain', 0), ('missing-score-error', 2), ('duplicates-first', 0), ('extra-rows', 2), ('coordinate-mismatch', 2), ('empty-loci', 3), ('background-subset', 3), ('namespace', 2), ('missing-magma', 3), ('mapping-failure-independent', 2), ('external-query', 0), ('unknown-root', 2), ('reserved-out', 2), ('declaration-only', 3)]
        if args.magma_home:
            protocols += [('original-magma-'+model, 0) for model in ['mean', 'top', 'multi']]
        if args.case:
            protocols = [(name, expected) for name, expected in protocols if name == args.case]
            if not protocols:
                raise ValueError('Unknown/unselected protocol; original MAGMA requires --magma-home')
        for name, expected_code in protocols:
            inputs = output/(name+'-inputs'+(' Unicode Ω' if args.unicode_paths else ''))
            shutil.copytree(source, inputs)
            for filename in ['gwas.tsv', 'ref.tsv', 'ld.tsv']:
                shutil.copy2(root/'examples/fuma/loci'/filename, inputs/filename)
            recipe = json.loads((inputs/'options.json').read_text(encoding='utf-8'))
            recipe.pop('out')
            for key in ['gwas', 'reference', 'ld']:
                recipe['loci'][key] = Path(recipe['loci'][key]).name
            if name == 'without-scores':
                for key in ['gene-scores', 'gene-score-id', 'score-duplicates', 'score-missing']:
                    recipe['mapping'].pop(key)
            if name == 'separate-scores':
                (inputs/'candidate-scores.tsv').write_text((inputs/'variant-annotations.tsv').read_text().replace('\t10\t2', '\t99\t2'), encoding='utf-8')
                recipe['variant_resources'].update(candidate_score_source='file', candidate_score_file='candidate-scores.tsv')
                recipe['mapping']['pos-cadd'] = '20'
            if name.startswith('missing-score-'):
                (inputs/'gene-scores.tsv').write_text('ensg\tsymbol\tchr\tpos\tpLI\nG1\tONE\t1\t1\t.1\n', encoding='utf-8')
                recipe['mapping']['score-missing'] = 'retain' if name.endswith('retain') else 'error'
            if name == 'duplicates-first':
                with (inputs/'gene-scores.tsv').open('a', encoding='utf-8') as stream:
                    stream.write('G1\tDUP\t1\t1\t.999\t99\n')
                recipe['mapping']['score-duplicates'] = 'first'
            if name == 'extra-rows':
                with (inputs/'variant-annotations.tsv').open('a', encoding='utf-8') as stream:
                    stream.write('1:999999:A:G\t1\t999999\t10\t2\n')
                recipe['variant_resources']['extra_rows'] = 'error'
            if name == 'coordinate-mismatch':
                path = inputs/'variant-annotations.tsv'
                path.write_text(path.read_text().replace('\t100\t', '\t101\t', 1), encoding='utf-8')
            if name == 'empty-loci':
                recipe['loci'].update({'lead-p': '1e-300', 'empty': 'retain'})
            if name == 'background-subset':
                recipe['gene_set'].pop('biotypes')
                recipe['gene_set']['background'] = 'background.txt'
                (inputs/'background.txt').write_text('G1\nG3\nG4\n', encoding='utf-8')
            if name == 'namespace':
                path = inputs/'genes.tsv'
                path.write_text(path.read_text().replace('G2\tTWO\t', 'G2\tG1\t'), encoding='utf-8')
            if name in ['missing-magma', 'mapping-failure-independent'] or name.startswith('original-magma-'):
                magma(recipe, root, args.magma_home.absolute() if name.startswith('original-magma-') else inputs/'absent-magma-home')
                if name == 'mapping-failure-independent':
                    recipe['mapping']['genes'] = 'absent-genes.tsv'
                if name.startswith('original-magma-'):
                    recipe['magma_gene']['model'] = name.removeprefix('original-magma-')
            if name == 'external-query':
                for key in ['loci', 'variant_resources', 'mapping']:
                    recipe.pop(key)
                recipe['gene_set_query_policy'] = 'external'
                recipe['gene_set']['query'] = 'query.txt'
                (inputs/'query.txt').write_text('G1\nG2\n', encoding='utf-8')
            if name == 'unknown-root':
                recipe['arbitrary_command'] = 'never executed'
            if name == 'reserved-out':
                recipe['mapping']['out'] = 'forbidden-override'
            if name == 'declaration-only':
                recipe.pop('loci')
                recipe.pop('variant_resources')
                mapping = recipe['mapping']
                for key in list(mapping):
                    if key.startswith('pos-') or key == 'window-bp':
                        mapping.pop(key)
                mapping.update({'positional': 'none', 'candidates': 'candidates.tsv', 'annotations': 'annotations.tsv', 'chromatin': 'supplied', 'interactions': 'interactions.tsv', 'ci-membership': 'source-global', 'ci-coordinates': 'source-closed', 'ci-enhancers': 'none', 'ci-promoters': 'promoters.tsv', 'ci-cadd': '0', 'ci-rdb': 'none', 'ci-states': 'none', 'ci-binary': 'none'})
                (inputs/'candidates.tsv').write_text('uid\trsID\tchr\tpos\tgwasP\tCADD\tRDB\tlocus\tindependent_rsIDs\nu1\trs1\t1\t100\t1e-10\t10\t2\t1\trs1\nu2\trs2\t1\t1000\t.01\t10\t2\t1\trs2\n', encoding='utf-8')
                (inputs/'annotations.tsv').write_text('uid\tCADD\tRDB\nu1\t10\t2\nu2\t10\t2\n', encoding='utf-8')
                (inputs/'interactions.tsv').write_text('interaction_id\tregion1\tregion2\tgenes\trsIDs\ttissue\nI1\t1:100-100\t1:900-3100\tG2\trs2\tA\nI2\t1:1000-1000\t1:1-200\tG1\trs1\tB\n', encoding='utf-8')
                (inputs/'promoters.tsv').write_text('region2\tgenes\n1:900-3100\tG2\n', encoding='utf-8')
            # Keep only this carrier path on argv. Some Windows Java launchers
            # replace non-ASCII command arguments before application code runs.
            path_keys = {'gwas', 'reference', 'ld', 'genes', 'candidates', 'annotations', 'consequences', 'eqtls', 'interactions', 'ci-enhancers', 'ci-promoters', 'gene-scores', 'query', 'background', 'gmt-manifest', 'home', 'snps', 'annotation', 'reference-panel', 'pvalues', 'gene-bundle', 'sets', 'properties', 'joint', 'candidate_score_file'}
            for section in recipe.values():
                if isinstance(section, dict):
                    for key, value in section.items():
                        if key in path_keys and value != 'none':
                            path = Path(value)
                            section[key] = str(path if path.is_absolute() else inputs/path)
            directory = output/(name+(' output Ω' if args.unicode_paths else ''))
            recipe['out'] = str(directory)
            carrier = output/(name+'.json')
            carrier.write_text(json.dumps(recipe, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
            process = subprocess.run([java, '-jar', str(jar), 'fuma-tools', '--method', 'workflow', '--workflow-options-file', str(carrier), '--no-config'], capture_output=True, timeout=120)
            (output/(name+'.stdout')).write_bytes(process.stdout)
            (output/(name+'.stderr')).write_bytes(process.stderr)
            entry = dict(name=name, directory=str(directory), exit_code=process.returncode)
            record['cases'].append(entry)
            dependency_skip = name.startswith('original-magma-') and process.returncode == 3
            if dependency_skip:
                expected_code = 3
            if process.returncode != expected_code:
                raise ValueError('Unexpected workflow exit code: '+name+'; inspect retained stage warnings/logs')
            if name in ['unknown-root', 'reserved-out']:
                if directory.exists():
                    raise ValueError('Invalid recipe created admitted output: '+name)
            else:
                manifest = json.loads((directory/'manifest.json').read_text(encoding='utf-8'))
                marker = json.loads((directory/'analysis-status.json').read_text(encoding='utf-8'))
                if manifest['exit_code'] != expected_code or marker['exit_code'] != expected_code or 'Required action' not in (directory/'WARNINGS.md').read_text(encoding='utf-8'):
                    raise ValueError('Missing matching final status or recovery: '+name)
                for filename, expected_hash in manifest['output_sha256'].items():
                    if digest(directory/filename) != expected_hash:
                        raise ValueError('Completed stage output changed: '+name+'/'+filename)
                for filename, expected_hash in manifest['source_and_stage_sha256'].items():
                    if digest(Path(filename)) != expected_hash:
                        raise ValueError('Admitted source or stage changed: '+filename)
                stages = manifest['stages']
                if expected_code == 0 and not all(stage['exit_code'] == 0 for stage in stages.values()):
                    raise ValueError('Completed workflow contains incomplete stage')
                if name == 'mapping-failure-independent' and stages['magma-annotation']['exit_code'] != 0:
                    raise ValueError('Independent annotation did not complete after mapping failure')
                if name == 'missing-magma' and [stages[k]['exit_code'] for k in ['gene-set', 'magma-annotation', 'magma-gene', 'magma-enrichment']] != [0, 0, 3, 3]:
                    raise ValueError('Missing dependency did not skip only dependent branch')
                if name in ['empty-loci', 'background-subset', 'declaration-only'] and (directory/'gene-set/all-tests.tsv').exists():
                    raise ValueError('Ineligible derived query ran a test')
                if expected_code == 0:
                    tests = table(directory/'gene-set/all-tests.tsv')
                    if len(tests) != 1 or not math.isclose(float(tests[0]['p']), 1/6, rel_tol=1e-13) or tests[0]['selected_size'] != '2':
                        raise ValueError('Independent hypergeometric N4,m2,n2,k2 reference differs')
                    if name != 'external-query':
                        genes = table(directory/'mapping/genes.tsv')
                        if [g['gene'] for g in genes] != ['G1', 'G2'] or (directory/'gene-set-query/query.txt').read_text().splitlines() != ['G1', 'G2']:
                            raise ValueError('Mapped actual-link query differs')
                        if name == 'without-scores' and any(k.startswith('score.') for k in genes[0]):
                            raise ValueError('Disabled scores changed original output schema')
                        if name == 'missing-score-retain' and genes[1]['score.pLI'] != 'NA':
                            raise ValueError('Unmatched score was not retained as missing')
                        if name not in ['without-scores', 'missing-score-retain'] and genes[0]['score.pLI'] != '0.123456789012345678901':
                            raise ValueError('Raw score precision was lost')
                        if name == 'separate-scores' and any(r['CADD'] != '99' for r in table(directory/'variant-preparation/candidates.tsv')):
                            raise ValueError('Candidate score source was replaced by annotation score')
                if name.startswith('original-magma-') and not dependency_skip:
                    model = name.removeprefix('original-magma-')
                    actual = table(directory/'magma-gene/results.tsv')
                    reference = [line.split() for line in (root/'src/test/resources/magma'/('summary-'+model+'.genes.out.txt')).read_text().splitlines() if line.strip() and not line.startswith('#')]
                    expected = [dict(zip(reference[0], row)) for row in reference[1:]]
                    if actual != expected or len(actual) != 201:
                        raise ValueError('Original genome-wide MAGMA results differ from frozen model reference')
                    entry['original_magma_gene_rows'] = len(actual)
                    outcomes = table(directory/'magma-gene/gene-status.tsv')
                    if len(outcomes) != 202:
                        raise ValueError('MAGMA omitted annotated gene outcomes')
                    entry['original_magma_annotated_genes'] = len(outcomes)
                entry['stage_exit_codes'] = {key: value['exit_code'] for key, value in stages.items()}
                entry['table_sha256'] = {p.relative_to(directory).as_posix(): digest(p) for p in sorted(directory.rglob('*.tsv'))}
            entry['status'] = 'skipped-required-runtime' if dependency_skip else 'passed'
            print('PASS '+name, flush=True)
        if digest(jar) != record['candidate_sha256'] or any(digest(Path(p)) != h for p, h in source_hashes.items()):
            raise ValueError('Candidate or example inputs changed during validation')
        skipped = any(c.get('status') == 'skipped-required-runtime' for c in record['cases'])
        record.update(status='partially-skipped' if skipped else 'passed', required_action='Read root and stage warnings, ID/resource dispositions, source hashes, original runtime terms and explicit background/model/build choices. Supply the permitted original MAGMA runtime if an explicitly selected association branch was skipped, then rerun to a new directory. Synthetic tests establish the typed subset only; unavailable broader analyses remain skipped.')
        code = 3 if skipped else 0
        return code
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        record.update(status='failed', error=str(failure), required_action='Inspect the named failure and retained stderr/stage warnings. Supply permitted dependencies or correct inputs, then rerun to a new directory. No failed workflow is admitted as complete.')
        return code
    finally:
        record['completed_protocols'] = sum(c.get('status') == 'passed' for c in record['cases'])
        (output/'verification.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
        warning = '# FUMA WORKFLOW REPLAY '+record['status'].upper()+'\n\nRequired action: '+record.get('required_action', 'Inspect retained diagnostics.')+'\n'
        (output/'WARNINGS.md').write_text(warning, encoding='utf-8')
        print(warning.strip(), file=sys.stderr)


if __name__ == '__main__':
    sys.exit(main())
