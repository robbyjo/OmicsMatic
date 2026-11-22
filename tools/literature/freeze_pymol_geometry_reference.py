#!/usr/bin/env python3
"""Freeze numeric fixtures from an actual original PyMOL collector report."""
import argparse
import hashlib
import json
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True,help='New fixture directory')
    args=parser.parse_args();r=json.loads(args.report.read_text(encoding='utf-8'))
    assert r['status']=='passed-original-numeric-collection-and-headless-rendering'
    assert r['source_commit']=='f51e58f6b08308c41c85b9d12a23231f49ca325a'
    assert r['pymol_version'][0]=='3.1.0' and len(r['measurements'])==128 and len(r['fits'])==96
    args.out.mkdir(parents=True,exist_ok=False)
    def table(name,header,rows):
        (args.out/name).write_text('\t'.join(header)+'\n'+''.join('\t'.join(map(str,row))+'\n' for row in rows),encoding='utf-8')
    coordinates=[axis+str(i) for i in range(1,5) for axis in ['x','y','z']]
    table('measurements.tsv',['case',*coordinates,'distance','angle','dihedral'],
          [[m['case'],*[v for point in m['points'] for v in point],m['distance'],m['angle'],m['dihedral']] for m in r['measurements']])
    table('fit-summary.tsv',['case','kind','pairs','pair_fit_rmsd','observed_post_rmsd'],
          [[f['case'],f['kind'],len(f['mobile']),f['original_pair_fit_rmsd'],f['observed_post_rmsd']] for f in r['fits']])
    pairs=[]
    for f in r['fits']:
        assert len(f['mobile'])==len(f['target'])==len(f['transformed'])==len(f['observed_post_residuals'])
        for i,(m,t,p,d) in enumerate(zip(f['mobile'],f['target'],f['transformed'],f['observed_post_residuals']),1):pairs.append([f['case'],i,*m,*t,*p,d])
    assert len(pairs)==1335
    table('fit-pairs.tsv',['case','pair','mobile_x','mobile_y','mobile_z','target_x','target_y','target_z','post_x','post_y','post_z','post_residual'],pairs)
    provenance=dict(engine='Open-Source PyMOL',version='3.1.0',source_commit=r['source_commit'],source_url='https://github.com/schrodinger/pymol-open-source/tree/v3.1.0',source_archive_sha256='54306d65060bd58ed8b3dab1a8af521aeb4fd417871f15f463ff05ccb4e121fe',license='LicenseRef-PyMOL;BSD-like',report_sha256=hashlib.sha256(args.report.read_bytes()).hexdigest(),measurement_cases=128,measurement_values=384,fit_cases=96,explicit_fit_pairs=1335,input_precision='Coordinates read back from original float32 atom storage',output_precision='Original measurements/fit return values and transformed float32 coordinates',native_contract='Double Euclidean distance,angle,signed torsion and proper unweighted all-pairs rigid fitting; supplied correspondences;no atom/sequence matching,scale,reflection,outlier rejection,periodic images,energy or uncertainty inference',tolerances=dict(distance_angstrom=1e-5,angle_degrees=5e-5,fit_rmsd_angstrom=1e-5,transformed_coordinates_angstrom=5e-5,post_residual_angstrom=5e-5),scope='Synthetic original numeric reference only. No bitwise identity, align/super algorithm equivalence, historical/commercial version, APBS/plugin/manual-modelling or paper-cohort reproduction claim.')
    (args.out/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8')
    (args.out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(args.out.iterdir()) if p.is_file()),encoding='utf-8')
    print(json.dumps(dict(status='frozen-actual-original-numeric-reference',measurement_cases=128,fit_cases=96,fit_pairs=1335)))

if __name__=='__main__':main()
