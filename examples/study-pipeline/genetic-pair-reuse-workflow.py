"""Offline packaged resume of independent MR, coloc and fine-mapping pairs.

All sources/approvals are synthetic. No scientific appraisal, human attestation
or model request is performed. Executable, verifier and fixture generator are
pinned before execution. Numerical checks use independent Python formulae.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import yaml

MODES = ['mr-source', 'coloc-source', 'fine-source', 'mr-add-pairs',
         'mr-correction', 'coloc-prior', 'mr-damaged', 'mr-rerun',
         'mr-unavailable-source', 'mr-revoked', 'mr-waiting']
WAIT = {'mr-unavailable-source', 'mr-revoked', 'mr-waiting'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def rows(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream, delimiter='\t'))


def table(path, data):
    with path.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(data[0]), delimiter='\t', lineterminator='\n')
        writer.writeheader()
        writer.writerows(data)


def cli(jar, *args):
    result = subprocess.run(['java', '-jar', str(jar), 'study', *map(str, args)],
                            capture_output=True, text=True, encoding='utf-8')
    assert result.returncode == 0, result.stdout + result.stderr


def blocked_cli(jar, project, *args):
    before = sorted(p.name for p in (project / 'runs').iterdir())
    result = subprocess.run(['java', '-jar', str(jar), 'study', *map(str, args)],
                            capture_output=True, text=True, encoding='utf-8')
    output = result.stdout + result.stderr
    assert result.returncode != 0 and 'Artifact hash mismatch' in output, output
    assert before == sorted(p.name for p in (project / 'runs').iterdir())
    return {'exit_code': result.returncode, 'output': output, 'no_new_run': True}


def kind(mode):
    return 'colocalization' if mode.startswith('coloc') else 'fine_mapping' if mode.startswith('fine') else 'mendelian_randomization'


def latest(project, operation):
    state = yaml.safe_load((project / 'state/project-status.yaml').read_text(encoding='utf-8'))
    found = []
    for folder in (project / 'runs' / state['run_id'] / 'operations').iterdir():
        path = folder / 'manifest.yaml'
        if path.is_file():
            manifest = yaml.safe_load(path.read_text(encoding='utf-8'))
            if manifest['operation_type'] == operation:
                found.append((folder, manifest))
    assert len(found) == 1, (project, operation)
    return found[0]


def generate(root, mode, fixture):
    fixture.generate(root, mode)
    project = root / mode
    study = json.loads((project / 'study.json').read_text(encoding='utf-8'))
    study['project']['id'] = 'pair-reuse-' + mode
    analysis = study['hypotheses'][0]['analysis']
    analysis['goals'] = [kind(mode), 'impact_assessment']
    analysis['bioinformatics']['mr_testing']['adjustment'] = 'BH'
    genes = []
    for i, ratio in enumerate([.5, .2, .1, .3], 1):
        genes.append({'feature_id': 'g' + str(i), 'gene_symbol': 'SYNTH' + str(i),
                      'gene_id': 'ENSG' + str(i), 'chromosome': '1', 'start': 100, 'end': 300,
                      'genome_build': 'GRCh38'})
        x = rows(project / f'x{i}.tsv')
        y = rows(project / f'y{i}.tsv')
        for first, second in zip(x, y):
            first['p_value'] = '1e-12'
            second['beta'] = float(first['beta']) * ratio * (1 if second['effect_allele'] == first['effect_allele'] else -1)
        table(project / f'x{i}.tsv', x)
        table(project / f'y{i}.tsv', y)
    table(project / 'annotation.tsv', genes)
    study['datasets'][0]['files']['annotation'] = 'annotation.tsv'
    desired = json.loads(json.dumps(analysis['bioinformatics']['genetic_pairs']))
    if mode in {'mr-source', 'coloc-source', 'fine-source', 'mr-waiting'}:
        analysis['bioinformatics']['genetic_pairs'][1]['outcome']['file_role'] = 'unavailable-source'
    if mode == 'mr-add-pairs':
        analysis['bioinformatics']['genetic_pairs'] = desired[:2]
    write(project / 'study.json', study)
    write(project / 'initial-study.json', study)
    write(project / 'desired-pairs.json', desired)
    write(project / 'stable-input-sha256.json', {p.name: sha(p) for p in project.iterdir()
                                              if p.is_file() and p.name != 'stable-input-sha256.json'})
    return project


def amend(project, jar, mode):
    state = yaml.safe_load((project / 'state/project-status.yaml').read_text(encoding='utf-8'))
    pairs = json.loads((project / 'desired-pairs.json').read_text(encoding='utf-8'))
    bio = {'genetic_pairs': pairs}
    analysis = {'bioinformatics': bio}
    if mode == 'mr-correction':
        bio['mr_testing'] = {'primary_estimator': 'ivw-fixed', 'adjustment': 'BY', 'alpha': .05}
    if mode == 'coloc-prior':
        analysis['colocalization'] = {'shared_prior': 1e-6, 'shared_prior_sensitivity': [5e-6, 1e-5]}
    if mode == 'mr-unavailable-source':
        pairs[1]['outcome']['file_role'] = 'unavailable-source'
    if mode == 'mr-revoked':
        pairs[1]['approved'] = False
    amendment = project / 'amendment-resume-pairs.yaml'
    write(amendment, {'schema_version': 1, 'amendment': {
        'id': 'resume-pairs', 'title': 'Synthetic independent pair completion',
        'base_study_hash': state['resolved_study_hash'],
        'proposed_by': {'type': 'external', 'id': 'synthetic-pair-reuse'},
        'source': {'type': 'synthetic-acceptance', 'id': mode},
        'rationale': 'Software fixture only. Preserve base inputs; no scientific appraisal or human attestation.'},
        'changes': {'update': {'hypotheses': [{'id': 'H', 'analysis': analysis}]}}})
    helper = project / 'amendment-driver'
    helper.mkdir()
    source = helper / 'PairReuseAmendment.java'
    source.write_text('import java.nio.file.Path; import org.jlinalg.study.*; public final class PairReuseAmendment { private PairReuseAmendment() {} public static void main(String[] a) throws Exception { StudyAmendments.apply(Path.of(a[0]),Path.of(a[1]),new StudyProject.Actor("external","synthetic-pair-reuse",null)); } }', encoding='utf-8')
    result = subprocess.run(['javac', '-Xlint:all', '-Werror', '-cp', str(jar), '-d', str(helper), str(source)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    cli(jar, 'amend', amendment, '--project', project, '--check')
    result = subprocess.run(['java', '-cp', str(jar) + os.pathsep + str(helper),
                             'PairReuseAmendment', str(amendment), str(project)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    write(project / 'amendment-software.json', {'actor_type': 'external', 'human_attestation': False,
          'source_sha256': sha(source), 'class_sha256': sha(source.with_suffix('.class')),
          'amendment_sha256': sha(project / 'amendments/amendment-resume-pairs.yaml')})


def stage(project, operation):
    folder, manifest = latest(project, operation)
    reuse = manifest['details']['native_pair_reuse']
    return {'directory': str(folder.relative_to(project)), 'state': manifest['state'],
            'whole_operation_reused': manifest.get('reused_from_cache', False),
            'reused_pairs': reuse['reused_pair_count'], 'new_attempts': reuse['new_pair_attempt_count'],
            'manifest_sha256': sha(folder / 'manifest.yaml')}


def lse(values):
    values = list(values)
    if not values:
        return -math.inf
    maximum = max(values)
    return maximum + math.log(sum(math.exp(x-maximum) for x in values))


def verify(root, jar):
    software = json.loads((root / 'execution-software.json').read_text(encoding='utf-8'))
    assert sha(jar) == software['jar_sha256']
    assert sha(Path(__file__)) == software['verifier_sha256']
    seals, cases, checks, payloads = {}, [], 0, []
    for mode in software['cases']:
        project = root / mode
        history = json.loads((project / 'history.json').read_text(encoding='utf-8'))
        assert sha(project / 'study.json') == sha(project / 'initial-study.json')
        for name, digest in json.loads((project / 'stable-input-sha256.json').read_text(encoding='utf-8')).items():
            assert sha(project / name) == digest
        folder, manifest = latest(project, kind(mode))
        assert manifest['state'] == ('waiting_for_input' if mode in WAIT else 'complete'), (mode, manifest['state'])
        second = history[1]
        expected = (0, 0) if mode == 'mr-damaged' else (0, 4) if mode in {'mr-rerun', 'coloc-prior'} else (4, 0) if mode == 'mr-correction' else (2, 2) if mode == 'mr-add-pairs' else (3, 1)
        assert (second['reused_pairs'], second['new_attempts']) == expected, (mode, second, expected)
        if mode == 'mr-damaged':
            assert history[1]['state'] == history[2]['state'] == 'project_integrity_blocked'
            assert history[0]['manifest_sha256'] == history[1]['manifest_sha256'] == history[2]['manifest_sha256']
        elif mode not in WAIT:
            assert history[2]['whole_operation_reused'] is True, (mode, history)
        else:
            assert history[2]['whole_operation_reused'] is False
            assert history[2]['reused_pairs'] == 3 and history[2]['new_attempts'] == 1
        assert not second['whole_operation_reused']
        if mode in {'mr-source', 'coloc-source', 'fine-source', 'mr-add-pairs', 'mr-waiting'}:
            assert history[0]['state'] == 'waiting_for_input'
        if mode == 'mr-damaged':
            assert all(r['no_new_run'] for r in json.loads((project / 'integrity-block-controls.json').read_text(encoding='utf-8')))
        records = {p['feature_id']: p for p in manifest['details']['pairs'] if 'pair_id' in p}
        pending = {'g2'} if mode in WAIT else set()
        if kind(mode) == 'mendelian_randomization':
            results = {r['feature_id']: r for r in rows(folder / 'mr-primary-results.tsv')}
            assert set(results) == {'g1', 'g2', 'g3', 'g4'}
            probabilities = {}
            for feature, result in results.items():
                if feature in pending:
                    assert result['causal_p_value'] == result['adjusted_p'] == 'NA'
                    continue
                index = feature[1:]
                x = rows(project / f'x{index}.tsv')
                y = {r['variant_id']: r for r in rows(project / f'y{index}.tsv')}
                aligned = [(float(r['beta']), float(y[r['variant_id']]['beta']) * (1 if r['effect_allele'] == y[r['variant_id']]['effect_allele'] else -1), float(y[r['variant_id']]['se'])) for r in x]
                precision = sum(b*b/(s*s) for b, _, s in aligned)
                beta = sum(b*y/(s*s) for b, y, s in aligned) / precision
                se = math.sqrt(1/precision)
                probability = math.erfc(abs(beta/se)/math.sqrt(2))
                for name, expected_value in [('estimate', beta), ('standard_error', se), ('causal_p_value', probability)]:
                    assert math.isclose(float(result[name]), expected_value, rel_tol=1e-10, abs_tol=1e-100), (mode, feature, name)
                    checks += 1
                probabilities[feature] = probability
            q, previous = {}, 1.
            factor = sum(1/i for i in range(1, 5)) if mode == 'mr-correction' else 1
            for rank, (feature, p) in reversed(list(enumerate(sorted(probabilities.items(), key=lambda v: (v[1], v[0])), 1))):
                previous = min(previous, p*4*factor/rank)
                q[feature] = previous
            for feature, probability in q.items():
                assert math.isclose(float(results[feature]['adjusted_p']), probability, rel_tol=1e-10, abs_tol=1e-100)
                assert results[feature]['significant_primary'] == str(not pending and probability < .05).lower()
                checks += 1
            assert manifest['details']['mr_testing']['family_size'] == 4
            assert manifest['details']['mr_testing']['unresolved_tests'] == len(pending)
        if kind(mode) == 'colocalization':
            for feature, record in records.items():
                if record['status'] != 'complete':
                    assert record['status'] == 'not_estimable'
                    continue
                pair_folder = folder / record['pair_id']
                effects = []
                for name in ['exposure.effects.tsv', 'outcome.effects.tsv']:
                    groups = {}
                    for r in rows(pair_folder / name):
                        groups.setdefault(r['effect_index'], {})[r['variant_id']] = float(r['log_bayes_factor'])
                    effects.append(groups)
                for r in rows(pair_folder / 'colocalization-results.tsv'):
                    x, y = effects[0][r['trait1_effect_index']], effects[1][r['trait2_effect_index']]
                    keys = sorted(x)
                    logp1, logp2, logp12 = [math.log(float(r[k])) for k in ['trait1_prior', 'trait2_prior', 'shared_prior']]
                    h = [0., logp1+lse(x.values()), logp2+lse(y.values()),
                         logp1+logp2+lse(x[a]+y[b] for a in keys for b in keys if a != b),
                         logp12+lse(x[a]+y[a] for a in keys)]
                    denominator = lse(h)
                    for i, value in enumerate(h):
                        assert math.isclose(float(r[f'pp_h{i}']), math.exp(value-denominator), rel_tol=1e-9, abs_tol=1e-12)
                        checks += 1
        # Verify all current operations; controlled damage remains explicitly retained.
        for manifest_path in sorted((project / 'runs').glob('*/operations/*/manifest.yaml')):
            old = yaml.safe_load(manifest_path.read_text(encoding='utf-8'))
            for name, digest in old.get('output_sha256', {}).items():
                path = manifest_path.parent / name
                relative = str(path.relative_to(root))
                actual = sha(path)
                if mode == 'mr-damaged' and manifest_path.parent == project / history[0]['directory'] and name == 'pair-g1/mr-ivw-fixed.tsv':
                    damage = json.loads((project / 'controlled-damage.json').read_text(encoding='utf-8'))
                    assert actual == damage['actual_sha256'] and digest == damage['original_sha256']
                else:
                    assert actual == digest, relative
                seals[relative] = actual
        inventory, _ = latest(project, 'finding_inventory')
        dossiers = [json.loads(line) for line in (inventory / 'finding-inventory.jsonl').read_text(encoding='utf-8').splitlines()]
        assert {d['feature_id'] for d in dossiers if d['candidate']} == {'g1', 'g2', 'g3', 'g4'}
        for dossier in dossiers:
            reused = records.get(dossier['feature_id'], {}).get('reused_native_result') is True
            contexts = [o for o in dossier['observations'] if o.get('kind') == 'native_genetic_pair_reuse']
            assert bool(contexts) == reused, (mode, dossier['feature_id'])
            for context in contexts:
                assert context['primary'] is False and context['source_ids']
                assert 'not independent replication' in context['required_manuscript_caveat']
        impact, _ = latest(project, 'impact_assessment')
        seen = []
        for path in impact.rglob('evidence.yaml'):
            payload = yaml.safe_load(path.read_text(encoding='utf-8'))
            encoded = json.dumps(payload)
            assert 'private_patient_' not in encoded and str(root) not in encoded
            seen.extend(f['finding_id'] for f in payload['findings'])
            for finding in payload['findings']:
                for observation in finding['observations']:
                    for source in observation.get('source_ids', []):
                        assert source in payload['source_catalog']
                    if observation.get('kind') == 'native_genetic_pair_reuse':
                        assert any('not independent replication' in c['text'] for c in finding['caveats'])
            payloads.append({'case': mode, 'path': str(path.relative_to(root)),
                             'findings': len(payload['findings']), 'sha256': sha(path)})
        assert set(seen) == {d['finding_id'] for d in dossiers if d['candidate']}
        for path in impact.rglob('metadata.yaml'):
            audit = yaml.safe_load(path.read_text(encoding='utf-8'))
            assert audit['request_attempted'] is False and audit['request_sent'] is False
        if mode == 'mr-damaged':
            blocked_cli(jar, project, 'verify', '--project', project)
        else:
            cli(jar, 'verify', '--project', project)
        cases.append({'case': mode, 'passed': True, 'state': 'project_integrity_blocked' if mode == 'mr-damaged' else manifest['state'],
                      'second_run_reused_pairs': second['reused_pairs'], 'second_run_new_attempts': second['new_attempts']})
    report = {'passed': True, 'jar_sha256': sha(jar), 'cases': cases,
              'independent_numerical_checks': checks, 'sealed_outputs': len(seals),
              'actual_unsent_impact_payloads': payloads,
              'scientific_appraisal_executed': False, 'model_requests_attempted': False}
    for name, value in [('verification.json', report), ('sealed-output-sha256.json', seals)]:
        if (root / name).exists():
            assert json.loads((root / name).read_text(encoding='utf-8')) == value
        else:
            write(root / name, value)
    print(json.dumps(report))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['run', 'verify'])
    parser.add_argument('directory', type=Path)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--cases', nargs='+', choices=MODES)
    args = parser.parse_args()
    root, jar = args.directory.resolve(), args.jar.resolve()
    if args.action == 'run':
        root.mkdir(parents=True, exist_ok=False)
        software = root / 'software'
        software.mkdir()
        shutil.copyfile(jar, software / jar.name)
        jar = software / jar.name
        for name in [Path(__file__).name, 'mr-feature-coverage-workflow.py']:
            shutil.copyfile(Path(__file__).with_name(name), software / name)
        modes = args.cases or MODES
        write(root / 'execution-software.json', {'jar': str(jar.relative_to(root)), 'jar_sha256': sha(jar),
              'verifier_sha256': sha(software / Path(__file__).name),
              'generator_sha256': sha(software / 'mr-feature-coverage-workflow.py'), 'cases': modes,
              'pinned_before_execution': True})
        spec = importlib.util.spec_from_file_location('genetic_pair_fixture', software / 'mr-feature-coverage-workflow.py')
        fixture = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(fixture)
        for mode in modes:
            project = generate(root, mode, fixture)
            command = ['auto', project / 'study.json', '--offline', '--actor-type', 'external', '--actor-id', 'synthetic-pair-reuse']
            cli(jar, *command)
            history = [stage(project, kind(mode))]
            if mode == 'mr-damaged':
                folder, _ = latest(project, kind(mode))
                damaged = folder / 'pair-g1/mr-ivw-fixed.tsv'
                original = sha(damaged)
                damaged.write_text('Controlled damaged derived output; retained for invalidation verification.\n', encoding='utf-8')
                write(project / 'controlled-damage.json', {'original_sha256': original, 'actual_sha256': sha(damaged)})
                controls = []
                for _ in range(2):
                    controls.append(blocked_cli(jar, project, *command))
                    current = stage(project, kind(mode))
                    current.update(state='project_integrity_blocked', whole_operation_reused=False, reused_pairs=0, new_attempts=0)
                    history.append(current)
                write(project / 'integrity-block-controls.json', controls)
                write(project / 'history.json', history)
                continue
            elif mode not in {'mr-rerun', 'mr-waiting'}:
                amend(project, jar, mode)
            cli(jar, *command, *(['--rerun'] if mode == 'mr-rerun' else []))
            history.append(stage(project, kind(mode)))
            cli(jar, *command)
            history.append(stage(project, kind(mode)))
            write(project / 'history.json', history)
    verify(root, jar)


if __name__ == '__main__':
    main()
