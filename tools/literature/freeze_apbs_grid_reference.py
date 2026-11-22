#!/usr/bin/env python3
"""Freeze original APBS grids/PDB2PQR charges with independent numeric truth and invalid-output retention."""
import argparse
import decimal
import gzip
import hashlib
import json
import math
from pathlib import Path
import re
import struct


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def grid(raw):
    text = raw.decode('ascii')
    counts = [int(v) for v in re.search(r'gridpositions counts (\d+) (\d+) (\d+)', text).groups()]
    origin = [float(v) for v in re.search(r'^origin (.+)$', text, re.M).group(1).split()]
    deltas = [[float(v) for v in value.split()] for value in re.findall(r'^delta (.+)$', text, re.M)]
    data = text.split('data follows', 1)[1].split('attribute', 1)[0].split()
    if len(data) != math.prod(counts):
        raise ValueError('Original scalar count mismatch')
    values, invalid = [], []
    for i, token in enumerate(data):
        try:
            value = float(token)
        except ValueError:
            value = float('nan')
        if not math.isfinite(value):
            invalid.append(dict(index=i, token=token))
        values.append(value)
    return counts, origin, deltas, values, invalid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--solver-runs', type=Path, required=True)
    parser.add_argument('--prepared-pqr', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    record = json.loads((args.solver_runs / 'verification.json').read_text())
    if record['status'] != 'completed-original-reference-runs' or record['original_program_sha256'] != '118579e48bb2801eda53777e44f12834ba8c8299d293e0fe2c2b46e32cebfb3d':
        raise ValueError('Require the identified original APBS3.5.0 reference runs')
    cases = {row['case']: row for row in record['cases']}
    parsed, proof, properties = {}, [], {}
    args.out.mkdir(parents=True, exist_ok=False)
    for case in ['sphere-q1-65', 'sphere-q0-65', 'protein-lpbe-salt']:
        source = args.solver_runs / case / 'potential.dx'
        raw = source.read_bytes()
        if sha(raw) != cases[case]['potential_sha256']:
            raise ValueError('Original grid changed:' + case)
        counts, origin, deltas, values, invalid = grid(raw)
        compressed = gzip.compress(raw, compresslevel=9, mtime=0)
        target = args.out / (case + '.dx.gz')
        target.write_bytes(compressed)
        row = dict(case=case, file=target.name, original_sha256=sha(raw), compressed_sha256=sha(compressed),
                   counts=counts, origin=origin, deltas=deltas, grid_points=len(values),
                   status='invalid-original-output-retained' if invalid else 'finite-original-grid',
                   invalid_scalar_count=len(invalid), first_invalid=invalid[:3])
        properties[case + '.compressed_sha256'] = row['compressed_sha256']
        properties[case + '.points'] = str(len(values))
        if not invalid:
            digest = hashlib.sha256()
            for value in values:
                digest.update(struct.pack('<d', value))
            row.update(float64_le_sha256=digest.hexdigest(), minimum=min(values), maximum=max(values), arithmetic_mean=math.fsum(values)/len(values))
            for key in ['float64_le_sha256', 'minimum', 'maximum', 'arithmetic_mean']:
                properties[case + '.' + key] = str(row[key])
        elif case != 'sphere-q0-65':
            raise ValueError('Unexpected invalid reference grid:' + case)
        properties[case + '.invalid_scalar_count'] = str(len(invalid))
        parsed[case] = values
        proof.append(row)
    positive = parsed['sphere-q1-65']
    _, _, _, negative, bad = grid((args.solver_runs / 'sphere-qminus1-65/potential.dx').read_bytes())
    if bad or any(a != -b for a, b in zip(positive, negative)):
        raise ValueError('Original LPBE charge reversal failed')
    _, _, _, doubled, bad = grid((args.solver_runs / 'sphere-q2-65/potential.dx').read_bytes())
    if bad:
        raise ValueError('Original doubled-charge grid is invalid')
    scaling = max(abs(a - 2*b)/max(1, abs(a), abs(2*b)) for a, b in zip(doubled, positive))
    if scaling > 1e-6:
        raise ValueError('Original LPBE charge scaling exceeds decimal output precision')
    raw = args.prepared_pqr.read_bytes()
    if sha(raw) != '9fac1bd44b0708bdf0fce21d031160f6b0e032e56884197d0daabb9b7776a889':
        raise ValueError('Original PARSE preparation differs')
    atoms = [line.split() for line in raw.decode('ascii').splitlines() if line.startswith(('ATOM', 'HETATM'))]
    charge = sum((decimal.Decimal(a[-2]) for a in atoms), decimal.Decimal(0))
    zero = sum(decimal.Decimal(a[-1]) == 0 for a in atoms)
    target = args.out / '1CRN-PARSE.pqr.gz'
    target.write_bytes(gzip.compress(raw, compresslevel=9, mtime=0))
    properties.update({'pqr.compressed_sha256': sha(target.read_bytes()), 'pqr.atoms': str(len(atoms)),
                       'pqr.total_charge_e': str(charge), 'pqr.zero_radius_atoms': str(zero)})
    provenance = dict(schema_version=1, original_APBS_version='3.5.0 Windows x86',
                      original_APBS_archive_sha256='3bf313b338a4362ebeb9a05316605027c93bec6c396e029476a1d8147af8e4c1',
                      original_APBS_program_sha256=record['original_program_sha256'],
                      original_PDB2PQR_version='3.8.0;PARSE;--nodebump;--noopt;--keep-chain;force-field protonation,no declared-pH titration',
                      source_PDB=dict(accession='1CRN', sha256='42199a30a0701864a2a5cc76cd7f35cc544cd0e65fbcf63e03c166543249b811',
                                      url='https://files.rcsb.org/download/1CRN.pdb', license='CC0', usage='https://www.wwpdb.org/about/usage-policies'),
                      grids=proof, pqr=dict(atoms=len(atoms), total_charge_e=str(charge), zero_radius_atoms=zero,
                                           original_sha256=sha(raw), compressed_sha256=properties['pqr.compressed_sha256']),
                      invariants=dict(charge_reversal_nodes=len(positive), charge_scaling_nodes=len(positive),
                                      maximum_relative_decimal_scaling_error=scaling),
                      born_convergence=record['born_convergence'],
                      invalid_case='Zero-RHS APBS3.5.0 returned exit0 but emitted interior -nan(ind) values and NaN energy. Retain/reject this original failed output;do not replace it with synthetic zeros. A separately supplied finite zero grid is valid.',
                      scope='Original data import/interpolation/charge regressions and independent physical diagnostics. Not a project APBS solver integration or paper/version parity.')
    (args.out / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n', encoding='utf-8', newline='\n')
    (args.out / 'reference.properties').write_text(''.join(k + '=' + v + '\n' for k, v in sorted(properties.items())), encoding='ascii', newline='\n')
    (args.out / 'SHA256SUMS').write_text(''.join(sha(path.read_bytes()) + '  ' + path.name + '\n' for path in sorted(args.out.iterdir()) if path.is_file()), encoding='ascii', newline='\n')
    print(json.dumps(dict(status='frozen-original-grids-and-invalid-control', atoms=len(atoms), total_charge_e=str(charge), zero_radius_atoms=zero,
                         finite_grid_points=2*len(positive), invalid_original_scalars=proof[1]['invalid_scalar_count'], charge_scaling_relative_error=scaling)))


if __name__ == '__main__':
    main()
