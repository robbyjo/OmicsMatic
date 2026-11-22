#!/usr/bin/env python3
"""Run the public packaged PyMOL command against original fixtures and failure controls.

Requires an already admitted Linux runtime. Never installs or downloads software.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import platform
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CASES = ('pdb-measure', 'cif-author', 'cif-label', 'pdb-model2', 'pdb-edits-render',
         'align-rigid', 'align-gap-outlier', 'super-rigid', 'super-gap-outlier')
ACTION = ('Required action: inspect verification.json and each case WARNINGS.md/session.json. '
          'Use Linux Java and an admitted permitted Ubuntu24.04 x86_64 PyMOL home; '
          'supply the exact locked local wheel where allowed, or keep dependency analysis skipped. '
          'Correct named input/process/audit failures and rerun to a new directory. '
          'Failed/skipped partials are not successful results.')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False, indent=2) + '\n', encoding='utf-8')


def check(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True, help='New verification directory')
    parser.add_argument('--java', default='java')
    args = parser.parse_args()
    output = args.out.absolute()
    output.mkdir(parents=True, exist_ok=False)
    rows = []
    record = dict(status='running', started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                  verifier_sha256=sha(Path(__file__)), cases=rows,
                  scope='Actual public packaged original PyMOL workflows, native audits and semantic failure controls; no historical/commercial edition parity or whole-goal completion')
    result_code = 2
    try:
        if platform.system() != 'Linux' or platform.machine().lower() not in ('x86_64', 'amd64'):
            record.update(status='skipped-unsupported-host', error='Use Linux Java inside an allowed Ubuntu24.04 x86_64 environment (WSL on Windows)')
            result_code = 3
            return result_code
        jar, home = args.jar.absolute(), args.home.absolute()
        check(jar.is_file(), 'Executable JAR is missing')
        record.update(jar_sha256=sha(jar), jar=str(jar), home=str(home))
        archive = ROOT / 'src/test/resources/org/jlinalg/protein/pymol-tools/original-workflows.zip'
        check(sha(archive) == '867e2d6873a4032c3b4145902518c830f6d0264ee43a29b3631ed5e449052185', 'Original fixture archive differs')
        provenance = json.loads((archive.parent / 'provenance.json').read_text(encoding='utf-8'))
        check(provenance['archive_sha256'] == sha(archive), 'Fixture provenance differs')
        records = {row['case']: row for row in provenance['cases']}
        record['original_fixture_archive_sha256'] = sha(archive)
        fit_counts, numbers, pairs, steps_verified = {}, 0, 0, 0

        def execute(label, recipe, expected=0, needle=None, recipe_file=None):
            nonlocal numbers, pairs, steps_verified
            folder = output / label
            folder.mkdir()
            recipe_path = recipe_file or (folder / 'recipe.json')
            if recipe_file is None:
                write(recipe_path, recipe)
            command = [args.java, '-jar', str(jar), 'pymol-tools', '--recipe', str(recipe_path),
                       '--home', str(home), '--out', str(folder / 'analysis')]
            completed = subprocess.run(command, capture_output=True, timeout=720)
            (folder / 'stdout').write_bytes(completed.stdout)
            (folder / 'stderr').write_bytes(completed.stderr)
            session_path = folder / 'analysis/session.json'
            session = json.loads(session_path.read_text(encoding='utf-8')) if session_path.is_file() else {}
            row = dict(case=label, command=command, exit_code=completed.returncode, expected_exit_code=expected,
                       status=session.get('status'), error=session.get('error'), recipe_sha256=sha(recipe_path))
            rows.append(row)
            write(output / 'verification.json', record)
            print(json.dumps(row, ensure_ascii=False), flush=True)
            if completed.returncode == 3:
                raise RuntimeError('DEPENDENCY_SKIPPED: ' + str(session.get('error')))
            check(completed.returncode == expected, label + ': unexpected public command exit')
            check('Required action:' in (folder / 'analysis/WARNINGS.md').read_text(encoding='utf-8'), label + ': missing prominent required action')
            if expected:
                check(session.get('status') == 'failed' and not (folder / 'analysis/original-results.json').exists(), label + ': failed scientific result was not quarantined')
                error = session.get('error', '')
                original = folder / 'analysis/partial/original-failure.json'
                if original.exists():
                    error += json.loads(original.read_text(encoding='utf-8')).get('error', '')
                check(needle.lower() in error.lower(), label + ': wrong failure reason: ' + error)
                row['semantic_error_asserted'] = needle
                return None
            check(session.get('status') == 'completed-with-warnings', label + ': incomplete analysis status')
            audit = json.loads((folder / 'analysis/native-audit.json').read_text(encoding='utf-8'))
            check(audit['status'] == 'passed-independent-native-audit', label + ': native audit incomplete')
            original = json.loads((folder / 'analysis/original-results.json').read_text(encoding='utf-8'))
            for step in audit['steps']:
                check(step['status'] == 'passed-independent-native-audit', label + ': incomplete step audit')
                steps_verified += 1
                if step['operation'] == 'measure':
                    numbers += 1
                if step['operation'] in ('align', 'super'):
                    pairs += step['inlier_pairs_verified']
                    fit_counts[label] = step['inlier_pairs_verified']
            row.update(native_steps_verified=len(audit['steps']), original_results_sha256=sha(folder / 'analysis/original-results.json'),
                       native_audit_sha256=sha(folder / 'analysis/native-audit.json'))
            return original

        with zipfile.ZipFile(archive) as bundle:
            def reference(case, member):
                raw = bundle.read(case + '/' + member)
                check(len(raw) <= 64 * 1048576 and hashlib.sha256(raw).hexdigest() == records[case]['sha256'][member], 'Reference member differs: ' + case + '/' + member)
                return raw

            for case in CASES:
                frozen = json.loads(reference(case, 'admitted-recipe.json'))
                expected = json.loads(reference(case, 'original-results.json'))
                folder = output / (case + '-sources')
                folder.mkdir()
                structures = []
                for item in frozen['structures']:
                    path = folder / (item['id'] + '.' + item['format'])
                    path.write_bytes(reference(case, item['staged_path']))
                    structures.append(dict(id=item['id'], path=str(path), format=item['format'], state=item['state'], cif_namespace=item['cif_namespace']))
                actual = execute(case, dict(schema_version=1, structures=structures, steps=frozen['steps']))
                check(len(actual['steps']) == len(expected['steps']), 'Original step count differs')
                for old, new in zip(expected['steps'], actual['steps']):
                    if old['operation'] == 'measure':
                        delta = abs(old['result']['value'] - new['result']['value'])
                        check(delta <= .001, case + ': original reference measurement differs')
                    if old['operation'] in ('align', 'super'):
                        check(old['result']['inlier_pairs'] == new['result']['inlier_pairs'], case + ': original reference correspondence differs')
                        check(all(abs(a-b) <= .001 for a, b in zip(old['result']['original_summary'], new['result']['original_summary'])), case + ': original reference alignment summary differs')
        check(fit_counts['align-gap-outlier'] != fit_counts['super-gap-outlier'], 'align/super lost their distinct gapped correspondences')
        # Run the actual public recipe files, with their paths resolved as documented.
        # Copy to a Unicode/space-named folder to exercise the Linux path boundary.
        examples = output / 'public examples-μ'
        examples.mkdir()
        source = ROOT / 'examples/pymol-tools/1CRN.pdb'
        check(sha(source) == '42199a30a0701864a2a5cc76cd7f35cc544cd0e65fbcf63e03c166543249b811', 'Example 1CRN source differs')
        (examples / source.name).write_bytes(source.read_bytes())
        for name in ('measure-render', 'edit-rotamer', 'align'):
            public_source = ROOT / ('examples/pymol-tools/' + name + '.json')
            copied_recipe = examples / (name + '.json')
            copied_recipe.write_bytes(public_source.read_bytes())
            check(sha(copied_recipe) == sha(public_source), 'Public recipe copy differs')
            execute('public-' + name, None, recipe_file=copied_recipe)
        structure = dict(id='protein', path=str(examples / '1CRN.pdb'), format='pdb', state=1, cif_namespace='author')
        def atom(name, resi='1'):
            return dict(structure='protein', chain='A', resi=resi, name=name, alt='')
        def mutation(target, rotamer=1, shift=.3, resi='1', id='mutation'):
            return dict(id=id, operation='mutate', residue=dict(structure='protein', chain='A', resi=resi),
                        to=target, library='backbone-independent', rotamer=rotamer, hydrogens='none', max_backbone_shift=shift)
        def recipe(steps):
            return dict(schema_version=1, structures=[structure], steps=steps)
        # Synthetic geometry, not a chemically validated ligand. Check original
        # canonical multi-letter symbols against the native uppercase input reader.
        element_source = output / 'element-symbols.pdb'
        atom_rows = [('C', 'C', (0, 0, 0)), ('CL', 'CL', (3, 4, 0)), ('BR', 'BR', (0, 3, 0)),
                     ('MG', 'MG', (0, 0, 4)), ('SE', 'SE', (0, 0, 3)), ('N', 'N', (0, 4, 0))]
        lines = []
        for serial, (name, element, xyz) in enumerate(atom_rows, 1):
            chars = [' '] * 80
            for start, value in [(0, 'HETATM'), (6, f'{serial:5d}'), (12, f'{name:<4}'), (17, 'LIG'), (21, 'A'),
                                 (22, '   1'), (30, ''.join(f'{value:8.3f}' for value in xyz)), (54, '  1.00  0.00'), (76, f'{element:>2}')]:
                chars[start:start+len(value)] = value
            lines.append(''.join(chars))
        element_source.write_text('\n'.join(lines) + '\nEND\n', encoding='utf-8')
        element_cif = output / 'element-symbols.cif'
        headers = ['group_PDB', 'id', 'type_symbol', 'label_atom_id', 'label_comp_id', 'label_alt_id',
                   'label_asym_id', 'label_seq_id', 'auth_asym_id', 'Cartn_x', 'Cartn_y', 'Cartn_z',
                   'occupancy', 'B_iso_or_equiv', 'pdbx_PDB_model_num']
        cif_rows = ['HETATM ' + str(i) + ' ' + element.capitalize() + ' ' + name + ' LIG . A 1 A '
                    + ' '.join(str(v) for v in xyz) + ' 1 0 1'
                    for i, (name, element, xyz) in enumerate(atom_rows, 1)]
        element_cif.write_text('data_elements\nloop_\n' + '\n'.join('_atom_site.' + h for h in headers)
                               + '\n' + '\n'.join(cif_rows) + '\n#\n', encoding='utf-8')
        element_structure = dict(id='elements', path=str(element_source), format='pdb', state=1, cif_namespace='author')
        cif_structure = dict(id='cifElements', path=str(element_cif), format='cif', state=1, cif_namespace='author')
        element_steps = [dict(id='elements' + str(i), operation='measure', kind='distance',
                              atoms=[dict(structure='elements', element=a), dict(structure='elements', element=b)])
                         for i, (a, b) in enumerate([('cl', 'c'), ('BR', 'mg'), ('Se', 'N'), ('Mg', 'C')])]
        element_steps += [dict(id='cifElements' + str(i), operation='measure', kind='distance',
                               atoms=[dict(structure='cifElements', element=a), dict(structure='cifElements', element=b)])
                          for i, (a, b) in enumerate([('CL', 'C'), ('br', 'MG'), ('se', 'n'), ('mg', 'c')])]
        execute('canonical-element-symbols', dict(schema_version=1, structures=[element_structure, cif_structure], steps=element_steps))
        amino = 'ALA ARG ASN ASP CYS GLN GLU GLY HIS ILE LEU LYS MET PHE PRO SER THR TRP TYR VAL'.split()
        execute('canonical-mutation-targets', recipe([mutation(target, id='to' + target) for target in amino]))
        execute('ambiguous-atom-control', recipe([dict(id='bad', operation='measure', kind='distance', atoms=[dict(structure='protein', name='CA'), atom('N')])]), 2, 'Selection requires')
        execute('rotamer-range-control', recipe([mutation('GLN', rotamer=999)]), 2, 'Integer outside declared bound')
        execute('linked-sidechain-control', recipe([mutation('GLN', resi='3')]), 2, 'linked side chain')
        execute('backbone-shift-control', recipe([mutation('GLN', shift=0)]), 2, 'backbone shift')
        execute('undefined-dihedral-control', recipe([dict(id='bad', operation='measure', kind='dihedral', atoms=[atom('N'), atom('N'), atom('CA'), atom('C')])]), 2, 'undefined angle/torsion')
        check(sha(jar) == record['jar_sha256'], 'Executable changed during validation')
        record.update(status='passed-original-pymol-packaged-workflows', successful_workflows=sum(row['expected_exit_code']==0 for row in rows),
                      failure_controls=sum(row['expected_exit_code']==2 for row in rows), native_steps_verified=steps_verified,
                      measurements_verified=numbers, original_inlier_pairs_verified=pairs, fit_pair_counts=fit_counts)
        result_code = 0
    except Exception as failure:
        skipped = str(failure).startswith('DEPENDENCY_SKIPPED:')
        record.update(status='skipped-dependency-required' if skipped else 'failed-retained', error=type(failure).__name__ + ': ' + str(failure))
        result_code = 3 if skipped else 2
    finally:
        record.update(completed_utc=dt.datetime.now(dt.timezone.utc).isoformat(), required_action=ACTION)
        write(output / 'verification.json', record)
        (output / 'WARNINGS.md').write_text('# PYMOL PACKAGED VALIDATION ' + ('PASSED WITH REVIEW WARNINGS' if result_code==0 else 'SKIPPED' if result_code==3 else 'FAILED') + '\n\n' + record.get('error', 'Review per-case warnings, figures and final atom/bond ledgers. APBS and historical/commercial edition parity are outside this validation.') + '\n\n' + ACTION + '\n', encoding='utf-8')
    return result_code


if __name__ == '__main__':
    raise SystemExit(main())
