"""Offline synthetic acceptance for registered adjusted two-level interactions.

Run: python subgroup-interactions-workflow.py run NEW_DIR --jar EXECUTABLE_JAR
Verify: python subgroup-interactions-workflow.py verify DIR --jar PINNED_JAR
Requires NumPy, SciPy, statsmodels and PyYAML. All inputs are synthetic.
Pins the executable, verifier and raw numerical fixture before execution.
No model requests, scientific appraisal or human attestations are performed.
"""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import numpy as np
import scipy
from scipy.optimize import minimize
from scipy.special import expit
from scipy.stats import chi2, norm, t
import statsmodels
import statsmodels.api as sm
import yaml

MODES = ['ols-hc3', 'ols-classical', 'logit-lrt', 'firth-lrt', 'nominal', 'two-cohorts',
         'group-mac', 'circular', 'budget-resume', 'missing-feature', 'related',
         'missing-model', 'unsupported-model', 'tiny-units', 'supplied-complete', 'supplied-partial']
WAIT = {'missing-feature', 'related', 'missing-model', 'unsupported-model', 'tiny-units'}
FIELDS = ['beta', 'se', 'ci_lower', 'ci_upper', 'p_value', 'statistic', 'df',
          'reference_beta', 'reference_se', 'comparison_beta', 'comparison_se']
CHECKS = []


def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path, value): path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
def rows(path):
    with path.open(encoding='utf-8', newline='') as stream: return list(csv.DictReader(stream, delimiter='\t'))
def table(path, data):
    with path.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(data[0]), delimiter='\t', lineterminator='\n')
        writer.writeheader(); writer.writerows(data)
def cli(jar, *args):
    result = subprocess.run(['java', '-jar', str(jar), 'study', *map(str, args)], capture_output=True, text=True, encoding='utf-8')
    assert result.returncode == 0, result.stdout+result.stderr
    return result.stdout+result.stderr
def latest(project):
    state = yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    return project/'runs'/state['run_id']/'operations'
def one(folder, kind):
    found = [(p, yaml.safe_load((p/'manifest.yaml').read_text(encoding='utf-8'))) for p in sorted(folder.iterdir()) if (p/'manifest.yaml').is_file()]
    found = [(p, m) for p, m in found if m['operation_type'] == kind]
    assert len(found) == 1, (folder, kind)
    return found[0]


def generate(root, mode):
    project = root/mode; project.mkdir()
    raw = rows(root/'software/data.tsv')
    continuous = sorted(float(r['continuous']) for r in raw); median = (continuous[47]+continuous[48])/2
    pheno = [dict(id=r['id'], continuous=r['continuous'], binary=r['binary'], age=r['age'], group=r['group'], offset=r['offset'],
                  derived=int(float(r['continuous']) > median), patient_note='PRIVATE_PARTICIPANT_SENTINEL') for r in raw]
    table(project/'phenotype.tsv', pheno)
    matrix = [{'feature_id': 'signal', **{r['id']: r['feature'] for r in raw}}, {'feature_id': 'constant', **{r['id']: 1 for r in raw}}]
    if mode == 'tiny-units': matrix[0] = {'feature_id': 'signal', **{r['id']: float(r['feature'])*1e-320 for r in raw}}
    table(project/'matrix.tsv', matrix)
    group = {'id': 'sex', 'column': 'group', 'levels': ['0', '1'], 'rationale': 'Prespecified synthetic contrast, not a biological sex claim.'}
    policy = {'enabled': True, 'independent_rows': True, 'groups': [group]}
    association = {'model': 'ols', 'offset_column': 'offset'}
    if mode in {'logit-lrt', 'firth-lrt'}:
        association.update(model='glm', family='binomial', case_value=1, control_value=0)
        policy['method'] = mode
    elif mode == 'ols-classical': policy['method'] = mode
    if mode == 'missing-model': association.pop('model')
    if mode == 'unsupported-model': association['model'] = 'lmm'
    if mode == 'budget-resume': policy['maximum_work'] = 1
    if mode == 'nominal': policy['adjustment'] = 'NONE'
    if mode == 'missing-feature': policy['features'] = ['signal', 'absent']
    if mode == 'circular':
        group.update(id='derived', column='derived', derived_from=['continuous'])
    analysis = {'mode': 'manual', 'goals': ['subgroup_interactions', 'impact_assessment'],
                'association': association, 'subgroup_interactions': policy}
    dataset = {'id': 'a', 'type': 'expression', 'stage': 'processed_matrix',
               'files': {'matrix': 'matrix.tsv', 'phenotype': 'phenotype.tsv'}, 'columns': {'sample_id': 'id'}}
    if mode == 'related': dataset['metadata'] = {'related_participants': True}
    if mode == 'group-mac':
        genotype = {name: [i % 3 if name == 'common' or i % 2 == 0 else 1 if i == 1 else 0 for i in range(96)] for name in ['common', 'rare-group']}
        content = '##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t'+'\t'.join(r['id'] for r in raw)+'\n'
        for pos, (name, dosage) in enumerate(genotype.items(), 1):
            content += f'1\t{pos*100}\t{name}\tA\tG\t.\tPASS\t.\tGT\t'+'\t'.join(['0/0', '0/1', '1/1'][v] for v in dosage)+'\n'
        (project/'matrix.vcf').write_text(content, encoding='utf-8')
        dataset.update(type='gwas', files={'matrix': 'matrix.vcf', 'phenotype': 'phenotype.tsv'})
    datasets = [dataset]; inputs = {'discovery': ['a']}
    if mode == 'two-cohorts':
        datasets.append({**dataset, 'id': 'b'}); inputs['discovery'].append('b')
    if mode.startswith('supplied-'):
        source = [dict(feature_id='signal', dataset_id='a', group_id='sex', reference_level='F', comparison_level='M', beta=1, se=.1, p_value=.001, adjusted_p=.003, patient_note='PRIVATE_PARTICIPANT_SENTINEL'),
                  dict(feature_id='signal', dataset_id='a', group_id='age', reference_level='young', comparison_level='old', beta=.1, se=.2, p_value=.6, adjusted_p=.9, patient_note='PRIVATE_PARTICIPANT_SENTINEL')]
        table(project/'provided.tsv', source)
        analysis.pop('subgroup_interactions'); analysis.pop('association')
        analysis['supplied_results'] = [{'id': 'provided', 'kind': 'subgroup_interactions', 'reported_status': 'complete' if mode.endswith('complete') else 'partial',
            'files': {'results': 'provided.tsv'}, 'scope': {'phenotype': 'continuous'}, 'method': {'name': 'Externally completed synthetic interaction'},
            'family': {'id': 'source-joint', 'coverage': 'complete', 'tested_count': 2, 'adjustment': 'BY', 'adjusted_p_from_full_family': True}}]
        datasets = []; inputs = {}
        for filename in ['phenotype.tsv', 'matrix.tsv']: (project/filename).unlink()  # Synthetic fixture construction only; no raw inputs requested.
    study = {'schema_version': 1, 'project': {'id': 'interaction-'+mode, 'title': 'Synthetic subgroup interactions', 'description': 'Software acceptance; no biological evidence.'},
             'resources': {'directory': 'fixture-resources', 'network': 'offline', 'downloads': 'deny'}, 'datasets': datasets,
             'hypotheses': [{'id': 'H', 'description': 'Registered synthetic conditional effect modification.', 'inputs': inputs, 'outcome': 'binary' if mode in {'logit-lrt', 'firth-lrt'} else 'continuous',
                             'covariates': ['age', 'group'] if datasets else [], 'analysis': analysis}]}
    write(project/'study.json', study)
    write(project/'input-provenance.json', {p.name: sha(p) for p in project.iterdir() if p.is_file()})
    return project


def amend(project, jar):
    state = yaml.safe_load((project/'state/project-status.yaml').read_text(encoding='utf-8'))
    request = project/'amendment-complete-family.yaml'
    write(request, {'schema_version': 1, 'amendment': {'id': 'complete-family', 'title': 'Synthetic work-budget amendment',
        'base_study_hash': state['resolved_study_hash'], 'proposed_by': {'type': 'external', 'id': 'synthetic-interaction'},
        'source': {'type': 'synthetic-acceptance', 'id': 'budget-resume'}, 'rationale': 'Increase the registered numerical budget; immutable base inputs; no human attestation.'},
        'changes': {'update': {'hypotheses': [{'id': 'H', 'analysis': {'subgroup_interactions': {'maximum_work': 250000000}}}]}}})
    helper = project/'amendment-driver'; helper.mkdir(); source = helper/'InteractionAmendment.java'
    source.write_text('import java.nio.file.Path; import org.jlinalg.study.*; public final class InteractionAmendment { private InteractionAmendment() {} public static void main(String[] a) throws Exception { StudyAmendments.apply(Path.of(a[0]),Path.of(a[1]),new StudyProject.Actor("external","synthetic-interaction",null)); } }', encoding='utf-8')
    result = subprocess.run(['javac', '-Xlint:all', '-Werror', '-cp', str(jar), '-d', str(helper), str(source)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    cli(jar, 'amend', request, '--project', project, '--check')
    result = subprocess.run(['java', '-cp', str(jar)+os.pathsep+str(helper), 'InteractionAmendment', str(request), str(project)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout+result.stderr
    write(project/'amendment-software.json', {'actor_type': 'external', 'human_attestation': False, 'source_sha256': sha(source), 'class_sha256': sha(source.with_suffix('.class'))})


def reference(y, x, c, g, offset, method):
    design = np.column_stack([np.ones(len(y)), g, c, c*g, x, c*x, x*g]); df = len(y)-7
    if method.startswith('ols-'):
        fit = sm.OLS(y-offset, design).fit()
        if method == 'ols-hc3': fit = fit.get_robustcov_results(cov_type='HC3', use_t=True)
        beta, cov = fit.params, fit.cov_params(); statistic = beta[-1]/np.sqrt(cov[-1, -1]); p = 2*t.sf(abs(statistic), df); critical = t.ppf(.975, df)
    elif method == 'logit-lrt':
        fit = sm.GLM(y, design, family=sm.families.Binomial(), offset=offset).fit(maxiter=1000, tol=1e-12)
        null = sm.GLM(y, design[:, :-1], family=sm.families.Binomial(), offset=offset).fit(maxiter=1000, tol=1e-12)
        beta, cov = fit.params, fit.cov_params(); statistic = 2*(fit.llf-null.llf); df = 1; p = chi2.sf(statistic, 1); critical = norm.ppf(.975)
    else:
        def objective(b):
            eta = offset+design@b; probabilities = expit(eta); w = probabilities*(1-probabilities); information = design.T@(w[:, None]*design)
            sign, det = np.linalg.slogdet(information)
            if sign <= 0: return 1e100, np.zeros(7)
            inverse = np.linalg.inv(information); h = w*np.sum((design@inverse)*design, axis=1)
            return -np.sum(y*eta-np.logaddexp(0, eta))-.5*det, -design.T@(y-probabilities+h*(.5-probabilities))
        full = minimize(objective, np.zeros(7), jac=True, method='BFGS', options={'gtol': 1e-8, 'maxiter': 10000})
        def constrained(free):
            value, gradient = objective(np.r_[free, 0.]); return value, gradient[:-1]
        null = minimize(constrained, full.x[:-1], jac=True, method='BFGS', options={'gtol': 1e-8, 'maxiter': 10000})
        assert max(abs(objective(full.x)[1])) < 1e-5 and max(abs(constrained(null.x)[1])) < 1e-5
        beta = full.x; probabilities = expit(offset+design@beta); cov = np.linalg.inv(design.T@((probabilities*(1-probabilities))[:, None]*design))
        statistic = 2*(null.fun-full.fun); df = 1; p = chi2.sf(statistic, 1); critical = norm.ppf(.975)
    ref = np.array([0, 0, 0, 0, 1, np.mean(c), 0.]); comp = ref+np.eye(7)[-1]; se = np.sqrt(cov[-1, -1]); b = beta[-1]
    return dict(zip(FIELDS, [b, se, b-critical*se, b+critical*se, p, statistic, df, ref@beta, np.sqrt(ref@cov@ref), comp@beta, np.sqrt(comp@cov@comp)]))


def close(actual, expected, label, probability=False):
    actual = float(actual)
    tolerance = 3e-5*abs(expected) if probability else 3e-5*max(1, abs(expected))
    assert np.isfinite(actual) and abs(actual-expected) <= tolerance, (label, actual, expected)
    if probability and expected > 0: assert abs(np.log(actual)-np.log(expected)) < 3e-5, (label, actual, expected)
    CHECKS.append({'label': label, 'actual': actual, 'expected': float(expected), 'relative_probability_check': probability})


def verify_stage(root, project, mode, folder, phase):
    path, manifest = one(folder, 'subgroup_interactions'); details = manifest['details']; data = rows(path/details.get('canonical_results', 'subgroup-interactions.tsv'))
    incomplete = mode in WAIT or mode == 'budget-resume' and phase == 0 or mode == 'supplied-partial'
    expected_candidates = set()
    if mode.startswith('supplied-'):
        assert manifest['state'] == 'complete' and details['analysis_executed_by_jlinalg'] is False
        assert details['primary_selection_eligible'] == (not incomplete)
        if not incomplete: expected_candidates.add('signal')
        assert [(r['group_id'], r['adjusted_p']) for r in data] == [('sex', '0.003'), ('age', '0.9')]
        assert not any(yaml.safe_load((p/'manifest.yaml').read_text())['operation_type'] == 'participant_qc' for p in folder.iterdir())
    else:
        assert manifest['state'] == ('waiting_for_input' if incomplete else 'complete'), (mode, phase, manifest['state'])
        assert details['primary_association_changed'] is False
        assert details['family_status'] == ('incomplete_registered_family' if incomplete else 'complete_registered_family')
        if mode == 'group-mac':
            rare = next(r for r in data if r['feature_id'] == 'rare-group')
            assert float(rare['mac_comparison']) == 1 and rare['in_testing_family'] == 'false' and rare['p_value'] == 'NA'
        if mode == 'circular':
            assert details['family_size'] == 0 and all(r['p_value'] == 'NA' for r in data)
            qc, _ = one(folder, 'participant_qc'); diagnostic = yaml.safe_load((qc/'prefit-qc.yaml').read_text())
            contrasts = [r for r in diagnostic['subgroup_comparisons'] if r['variable'] == 'continuous']
            assert contrasts and all(r['p_value'] == 'NA' and r['effect_modification_established'] is False for r in contrasts)
        if incomplete: assert all(r['adjusted_p'] == 'NA' and r['sensitivity_candidate'] == 'false' for r in data)
        if mode == 'tiny-units':
            invalid = next(r for r in data if r['feature_id'] == 'signal')
            assert invalid['p_value'] == 'NA' and 'unrepresentable_in_input_units' in invalid['reason']
        raw = rows(project/'phenotype.tsv'); c = np.array([float(r['age']) for r in raw]); g = np.array([int(r['group']) for r in raw]); offset = np.array([float(r['offset']) for r in raw])
        method = mode if mode in {'ols-classical', 'logit-lrt', 'firth-lrt'} else 'ols-hc3'
        y = np.array([float(r['binary' if method.endswith('lrt') else 'continuous']) for r in raw])
        probabilities = {}
        for row in data:
            if row['status'] != 'complete': continue
            x = np.array([i % 3 for i in range(96)]) if mode == 'group-mac' else np.array([float(r['feature']) for r in rows(root/'software/data.tsv')])
            expected = reference(y, x, c, g, offset, method)
            for field in FIELDS: close(row[field], expected[field], [mode, phase, row['dataset_id'], row['feature_id'], field], field == 'p_value')
            close(row['log_p_value'], np.log(expected['p_value']), [mode, phase, 'logP'])
            assert int(row['n_reference']) == 48 and int(row['n_comparison']) == 48
            probabilities[row['test_id']] = expected['p_value']
        if not incomplete:
            m = details['family_size']; harmonic = sum(1/i for i in range(1, m+1)); previous = 1.; adjusted = {}
            ordered = sorted(probabilities, key=lambda key: (probabilities[key], key))
            for rank in range(len(ordered), 0, -1):
                key = ordered[rank-1]; previous = min(previous, probabilities[key]*m*harmonic/rank); adjusted[key] = previous
            if mode == 'nominal': adjusted = probabilities
            for row in data:
                if row['status'] == 'complete':
                    close(row['adjusted_p'], adjusted[row['test_id']], [mode, phase, row['dataset_id'], 'BY'], True)
                    if adjusted[row['test_id']] < .05: expected_candidates.add(row['feature_id'])
        expected_m = 4 if mode == 'two-cohorts' else 1 if mode == 'group-mac' else 0 if mode == 'circular' else 2
        assert details['family_size'] == expected_m, (mode, details['family_size'])
    inventory, _ = one(folder, 'finding_inventory'); dossiers = [json.loads(line) for line in (inventory/'finding-inventory.jsonl').read_text(encoding='utf-8').splitlines()]
    candidates = {f['finding_id'] for f in dossiers if f['candidate']}
    assert {f['feature_id'] for f in dossiers if f['candidate']} == expected_candidates, (mode, phase, expected_candidates)
    for finding in dossiers:
        assert finding['significant'] is False
        if mode == 'nominal': assert all(caveat['kind'] != 'mr_multiplicity' for caveat in finding['caveats'])
        for observation in finding['observations']:
            if observation.get('kind') == 'subgroup_interaction':
                assert observation['primary'] is False and observation['source_ids']
                assert 'Group-specific significance alone' in observation['required_manuscript_caveat']
                if mode == 'nominal': assert 'not FDR control' in observation['required_manuscript_caveat']
    assert 'PRIVATE_PARTICIPANT_SENTINEL' not in (inventory/'finding-inventory.jsonl').read_text(encoding='utf-8')
    impact, _ = one(folder, 'impact_assessment'); seen = set(); payloads = []
    for payload_path in impact.rglob('evidence.yaml'):
        payload = yaml.safe_load(payload_path.read_text(encoding='utf-8')); encoded = json.dumps(payload)
        assert 'PRIVATE_PARTICIPANT_SENTINEL' not in encoded and str(root) not in encoded
        seen.update(f['finding_id'] for f in payload['findings']); payloads.append(str(payload_path.relative_to(root)))
        for finding in payload['findings']:
            for observation in finding['observations']:
                for source in observation.get('source_ids', []): assert source in payload['source_catalog']
    assert seen == candidates, (mode, phase, seen, candidates)
    for meta in impact.rglob('metadata.yaml'):
        audit = yaml.safe_load(meta.read_text(encoding='utf-8')); assert audit['request_attempted'] is False and audit['request_sent'] is False
    return {'case': mode, 'phase': phase, 'state': manifest['state'], 'rows': len(data), 'candidates': len(candidates), 'unsent_payloads': payloads}


def verify(root, jar):
    CHECKS.clear(); software = json.loads((root/'execution-software.json').read_text(encoding='utf-8'))
    assert sha(jar) == software['jar_sha256']
    for name, digest in software['source_sha256'].items(): assert sha(root/'software'/name) == digest
    results = []; seals = {}
    for mode in software['cases']:
        project = root/mode
        for name, digest in json.loads((project/'input-provenance.json').read_text()).items(): assert sha(project/name) == digest
        history = json.loads((project/'history.json').read_text())
        for phase, relative in enumerate(history):
            folder = project/relative; results.append(verify_stage(root, project, mode, folder, phase))
            for path in sorted(folder.iterdir()):
                manifest = yaml.safe_load((path/'manifest.yaml').read_text(encoding='utf-8')); assert manifest['state'] != 'failed'
                for name, digest in manifest['output_sha256'].items():
                    output = path/name; assert sha(output) == digest; seals[str(output.relative_to(root))] = digest
        cli(jar, 'verify', '--project', project)
    write(root/'independent-numerical-checks.json', CHECKS)
    write(root/'verification.json', {'synthetic_only': True, 'jar_sha256': sha(jar), 'cases': results, 'numerical_checks': len(CHECKS), 'sealed_artifacts': len(seals), 'all_requests_unsent': True})
    write(root/'sealed-artifacts.json', seals)
    print(json.dumps({'cases': len(software['cases']), 'phases': len(results), 'numerical_checks': len(CHECKS), 'sealed_artifacts': len(seals)}))


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('action', choices=['run', 'verify']); parser.add_argument('directory', type=Path); parser.add_argument('--jar', type=Path, required=True); parser.add_argument('--cases', nargs='+', choices=MODES)
    args = parser.parse_args(); root = args.directory.resolve(); jar = args.jar.resolve()
    if args.action == 'run':
        root.mkdir(parents=True, exist_ok=False); software = root/'software'; software.mkdir()
        shutil.copyfile(jar, software/jar.name); jar = software/jar.name
        shutil.copyfile(Path(__file__), software/Path(__file__).name)
        resources = Path(__file__).resolve().parents[2]/'src/test/resources/subgroup-interactions'
        for name in ['data.tsv', 'expected.tsv', 'software.json', 'generate_reference.py']: shutil.copyfile(resources/name, software/name)
        write(root/'execution-software.json', {'jar': str(jar.relative_to(root)), 'jar_sha256': sha(jar), 'source_sha256': {p.name: sha(p) for p in software.iterdir() if p.suffix != '.jar'},
             'cases': args.cases or MODES, 'pinned_before_execution': True, 'python': sys.version, 'numpy': np.__version__, 'scipy': scipy.__version__, 'statsmodels': statsmodels.__version__})
        for mode in args.cases or MODES:
            project = generate(root, mode); command = ['auto', project/'study.json', '--offline', '--actor-type', 'external', '--actor-id', 'synthetic-interaction']
            cli(jar, *command); history = [str(latest(project).relative_to(project))]
            if mode == 'budget-resume':
                amend(project, jar); cli(jar, *command); history.append(str(latest(project).relative_to(project)))
                cli(jar, *command); history.append(str(latest(project).relative_to(project)))
                _, receipt = one(latest(project), 'subgroup_interactions'); assert receipt['reused_from_cache'] is True
            write(project/'history.json', history)
    verify(root, jar)


if __name__ == '__main__': main()
