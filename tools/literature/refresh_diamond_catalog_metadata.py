# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Trim metadata EOF blank lines, preserve prior evidence and prove identical Python AST."""
import argparse, ast, copy, json, shutil
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save
from signac_scan_evidence import pack, unpack, byte_pin
from verify_diamond_catalog import DATA, verify


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    source = ROOT/'tools/literature/diamond_catalog.py'
    before = source.read_bytes()
    after = before.rstrip(b'\n')+b'\n'
    assert before == after+b'\n\n'
    assert ast.dump(ast.parse(before), include_attributes=True) == ast.dump(ast.parse(after), include_attributes=True)
    oldproof = json.loads((DATA/'diamond-catalog-validation.json').read_bytes())
    oldseal = json.loads((BASE/'catalog-seal-1/COMPLETED.json').read_bytes())
    assert pin(DATA/'diamond-catalog-support.zip') == oldproof['archive']
    for name in ['diamond-catalog-validation.json', 'diamond-catalog-support.zip', 'SHA256SUMS']:
        shutil.copy2(DATA/name, out/('before-'+name))
    (out/'before-diamond_catalog.py').write_bytes(before)
    (out/'after-diamond_catalog.py').write_bytes(after)
    files = unpack(out/'before-diamond-catalog-support.zip', oldproof)
    logical = 'current-source/tools/literature/diamond_catalog.py'
    assert files[logical] == before
    files[logical] = after
    receipt = dict(status='PASS_METADATA_EOF_BLANK_LINES_REMOVED_IDENTICAL_AST',
        path=source.relative_to(ROOT).as_posix(), before=byte_pin(before), after=byte_pin(after),
        python_ast_and_attributes_identical=True, scientific_source_or_results_changed=False,
        rendered_pages_or_catalog_data_changed=False, previous_archive=oldproof['archive'],
        previous_proof=pin(out/'before-diamond-catalog-validation.json'), whole_goal_complete=False)
    save(out/'REFRESH.json', receipt)
    files['metadata-refresh/REFRESH.json'] = (out/'REFRESH.json').read_bytes()
    files['metadata-refresh/refresh_diamond_catalog_metadata.py'] = Path(__file__).read_bytes()
    packing = pack(out/'diamond-catalog-support.zip', files)
    source.write_bytes(after)
    proof = copy.deepcopy(oldproof)
    proof.update(packing)
    proof['archive'] = pin(out/'diamond-catalog-support.zip')
    proof['source_pins'][source.relative_to(ROOT).as_posix()] = pin(source)
    proof['source_pins'][Path(__file__).relative_to(ROOT).as_posix()] = pin(Path(__file__))
    proof['metadata_eof_refresh'] = receipt
    (DATA/'diamond-catalog-support.zip').write_bytes((out/'diamond-catalog-support.zip').read_bytes())
    save(DATA/'diamond-catalog-validation.json', proof)
    prior = (DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines()
    order = [line.split('  ', 1)[1] for line in prior]
    manifest = {p.name: pin(p)['sha256'] for p in sorted(DATA.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'}
    assert set(order) == set(manifest)
    (DATA/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order), encoding='utf8', newline='\n')
    assert all(pin(DATA/n) == v for n, v in oldproof['catalog_snapshot'].items())
    assert all(pin(ROOT/n) == v for n, v in {**oldproof['page_pins'], **oldproof['component_files']}.items())
    verify()
    sealed = copy.deepcopy(oldseal)
    sealed['source_pins'] = proof['source_pins']
    sealed['published_files'] = {p.relative_to(ROOT).as_posix(): pin(p) for p in [DATA/'diamond-catalog-validation.json', DATA/'diamond-catalog-support.zip', DATA/'SHA256SUMS']}
    sealed['final_manifest_files'] = {p.name: pin(p) for p in DATA.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    sealed['metadata_eof_refresh'] = receipt
    save(out/'COMPLETED.json', sealed)
    print(receipt['status'], 'prior archive retained; complete refreshed catalog verifies', flush=True)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact AST/evidence preservation checks')
    main()
