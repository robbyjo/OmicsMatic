# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original synthetic KING reports for ordinary Java regression tests."""
import argparse, json, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    for compiler in [21, 25]:
        proof = json.loads((BASE/f'native-reference{compiler}-1/COMPLETED.json').read_bytes())
        assert proof['status'] == 'PASS_NATIVE_KING960_COMPLETE_ORIGINAL_COUNT_AND_BINARY_COMPARISONS'
        assert all(pin(ROOT/n) == v for n, v in proof['source_pins'].items())
    reference = BASE/'reference-controls-3'
    original = json.loads((reference/'COMPLETED.json').read_bytes())
    assert original['status'] == 'PASS_COMPLETE_ORIGINAL_PLINK_RELATEDNESS_REFERENCE_CONTROLS'
    cases = json.loads((reference/'inputs/cases.json').read_bytes())
    files = {}
    for case in cases:
        name = case['id']
        prefix = reference/case['prefix']
        for extension in ['bed', 'bim', 'fam']:
            files[name+'/cohort.'+extension] = prefix.with_suffix('.'+extension).read_bytes()
        for member, source in [('original.kin0', 'counts'), ('original.king.bin', 'binary'), ('original.king.id', 'binary')]:
            files[name+'/'+member] = (reference/name/source/member).read_bytes()
    files['SOURCE.json'] = (json.dumps(dict(status='ORIGINAL_SYNTHETIC_REFERENCE_ONLY',
        original_runtime_profile=original['source_profile'], binaries=original['binary_pins'],
        source_options=['--make-king-table counts cols=fid,id,nsnp,hethet,ibs0,ibs1,kinship', '--make-king triangle bin'],
        source_version='PLINK v2.0.0-b.1.2 64-bit (24 Oct2026)', case_variant_counts=[c['variants'] for c in cases],
        samples=16, pairs_per_case=120, total_pairs=960, paper_data=False,
        biological_validation=False, historical_paper_reproduced=False), indent=2)+'\n').encode()
    target = ROOT/'src/test/resources/org/jlinalg/genetics/native-king-original-reference.zip'
    assert not target.exists()
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(files.items()):
            item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            item.compress_type, item.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
            z.writestr(item, raw)
    with zipfile.ZipFile(target) as z:
        assert set(z.namelist()) == set(files) and all(z.read(n) == raw for n, raw in files.items())
    save(out/'COMPLETED.json', dict(status='PASS_FROZEN_ORIGINAL_NATIVE_KING_TEST_REFERENCE',
        artifact=dict(path=target.relative_to(ROOT).as_posix(), **pin(target)),
        source_producer=pin(Path(__file__)), source_original=pin(reference/'COMPLETED.json'),
        cases=8, pairs=960, original_binaries_redistributed=False, new_catalog_credit=False, whole_goal_complete=False))
    print('Frozen8 original synthetic cases/960 complete pair reports and binary coefficients')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original-data/round-trip checks')
    main()
