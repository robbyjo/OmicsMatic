#!/usr/bin/env python3
"""Freeze successful original PyMOL typed runs for dependency-free Java audit regressions."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

CASES = ('pdb-measure', 'cif-author', 'cif-label', 'pdb-model2', 'pdb-edits-render',
         'align-rigid', 'align-gap-outlier', 'super-rigid', 'super-gap-outlier')
ROOT = Path(__file__).resolve().parents[2]


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True, help='New fixture directory; existing paths are preserved')
    args = parser.parse_args()
    verification = json.loads((args.source / 'verification.json').read_text())
    if verification['status'] != 'passed' or tuple(row['case'] for row in verification['cases']) != CASES or any(row['exit_code'] for row in verification['cases']):
        raise ValueError('Require the complete nine-case successful original run')
    lock = (ROOT / 'tools/literature/pymol-runtime-lock.json').read_bytes()
    worker = (ROOT / 'src/main/resources/org/jlinalg/omics/pymol_worker.py').read_bytes()
    entries, records = {}, []
    for name in CASES:
        folder = args.source / name / 'output'
        session = json.loads((folder / 'session.json').read_text())
        audit = json.loads((folder / 'native-audit.json').read_text())
        if (session['status'] != 'completed-with-warnings' or audit['status'] != 'passed-independent-native-audit'
                or session['runtime_lock_sha256'] != sha(lock) or session['workflow_script_sha256'] != sha(worker)
                or session['workflow_exit_code'] != 0):
            raise ValueError('Original worker/runtime/audit identity differs: ' + name)
        result = json.loads((folder / 'original-results.json').read_text())
        paths = {'admitted-recipe.json', 'original-results.json', 'native-audit.json'}
        paths.update(result['initial'].values())
        paths.update(result['final_ledgers'].values())
        paths.update(result['exports'].values())
        recipe = json.loads((folder / 'admitted-recipe.json').read_text())
        paths.update(item['staged_path'] for item in recipe['structures'])
        for step in result['steps']:
            paths.update(step['before'].values())
            paths.update(step['after'].values())
            if step['operation'] == 'render':
                paths.add(step['result']['image'])
        hashes = {}
        for relative in sorted(paths):
            path = folder / relative
            if not path.resolve().is_relative_to(folder.resolve()) or path.is_symlink() or not path.is_file() or path.stat().st_size > 64 * 1048576:
                raise ValueError('Invalid fixture member: ' + relative)
            raw = path.read_bytes()
            entries[name + '/' + relative] = raw
            hashes[relative] = sha(raw)
        records.append(dict(case=name, sha256=hashes, runtime_versions=session['runtime_versions'],
                            runtime_lock_sha256=session['runtime_lock_sha256'],
                            runtime_marker_sha256=session['runtime_marker_sha256'],
                            worker_sha256=session['workflow_script_sha256']))
    args.out.mkdir(parents=True, exist_ok=False)
    archive = args.out / 'original-workflows.zip'
    with zipfile.ZipFile(archive, 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for name, raw in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            bundle.writestr(info, raw)
    provenance = dict(schema_version=1, scope='Original open-source PyMOL 3.1.0 execution plus independent Java audit regression fixtures; no historical/commercial parity',
                      source_data=dict(accession='1CRN', pdb='https://files.rcsb.org/download/1CRN.pdb',
                                       cif='https://files.rcsb.org/download/1CRN.cif',
                                       usage_policy='https://www.wwpdb.org/about/usage-policies', license='CC0'),
                      derived_cases='Rigid rotation/translation, deleted target residues 5-7 and displaced residue20 CA; PDB models5/9 select ordinal2; explicit graph/torsion/GLN rotamer edits',
                      archive_sha256=sha(archive.read_bytes()), entries=len(entries), cases=records)
    (args.out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8')
    (args.out / 'SHA256SUMS').write_text(''.join(sha(path.read_bytes()) + '  ' + path.name + '\n' for path in [archive, args.out / 'provenance.json']), encoding='utf-8')
    print(json.dumps(dict(status='frozen', entries=len(entries), bytes=archive.stat().st_size, archive_sha256=provenance['archive_sha256'])))


if __name__ == '__main__':
    main()
