#!/usr/bin/env python3
"""Check a packaged native command against frozen actual-original numeric fixtures.

Requires Python's standard library and Java. Does not install or execute PyMOL.
Choose a new ASCII output path on Windows, as in the documented recipe workflow.
"""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    with path.open(encoding='utf-8',newline='') as stream:
        return list(csv.DictReader(stream,delimiter='\t'))


def write(path,header,rows):
    with path.open('w',encoding='utf-8',newline='') as stream:
        out=csv.writer(stream,delimiter='\t',lineterminator='\n')
        out.writerow(header)
        out.writerows(rows)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True,help='New output directory; ASCII path recommended on Windows')
    parser.add_argument('--java',action='append',help='Java executable; repeat to check several JVMs; default java on PATH')
    args=parser.parse_args()
    jar=args.jar.resolve();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    reference=ROOT/'src/test/resources/org/jlinalg/protein/pymol-geometry'
    for line in (reference/'SHA256SUMS').read_text(encoding='utf-8').splitlines():
        digest,name=line.split('  ',1)
        assert Path(name).name==name and sha(reference/name)==digest,name
    measurements=read(reference/'measurements.tsv');summaries=read(reference/'fit-summary.tsv');pairs=read(reference/'fit-pairs.tsv')
    assert len(measurements)==128 and len(summaries)==96 and len(pairs)==1335
    inputs=out/'inputs';inputs.mkdir()
    header=['measurement_id','kind',*[p+a for p in 'abcd' for a in 'xyz']]
    expected=[];rows=[]
    for m in measurements:
        for kind,count in [('distance',2),('angle',3),('dihedral',4)]:
            identity=m['case']+'-'+kind
            xyz=[m[a+str(i)] for i in range(1,count+1) for a in 'xyz']
            rows.append([identity,kind,*xyz,*['']*(12-count*3)])
            expected.append((identity,kind,float(m[kind])))
    measure=inputs/'measure.tsv';write(measure,header,rows)
    fit_header=['pair_id',*[prefix+a for prefix in ['mobile_','target_'] for a in 'xyz']]
    selected=summaries[:6]
    by_case={s['case']:[p for p in pairs if p['case']==s['case']] for s in selected}
    for case,case_pairs in by_case.items():
        write(inputs/('fit-'+case+'.tsv'),fit_header,
              [[p['pair'],*[p[k] for k in fit_header[1:]]] for p in case_pairs])
    line=inputs/'line.tsv'
    write(line,fit_header,[['a',0,0,0,0,0,0],['b',1,0,0,1,0,0],['c',2,0,0,2,0,0]])
    record=dict(status='running',candidate_sha256=sha(jar),verifier_sha256=sha(Path(__file__)),
                fixture_manifest_sha256=sha(reference/'SHA256SUMS'),runs=[],numeric_values_checked=0,
                fit_pairs_checked=0,scope='Actual packaged native execution against frozen original PyMOL3.1.0 fixtures;no fresh PyMOL execution,installer,typed upstream adapter,whole-paper or release claim.')

    def save():
        (out/'verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')

    def near(actual,target,tolerance,label):
        value=float(actual)
        assert math.isfinite(value) and abs(value-target)<=tolerance,(label,value,target,tolerance)
        record['numeric_values_checked']+=1

    def run(java,label,options,code):
        directory=out/label
        command=[java,'-jar',str(jar),'molecular-geometry',*options,'--out',str(directory)]
        process=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=90)
        (out/(label+'.stdout')).write_bytes(process.stdout)
        (out/(label+'.stderr')).write_bytes(process.stderr)
        record['runs'].append(dict(label=label,command=command,exit_code=process.returncode,
                                  stdout_sha256=hashlib.sha256(process.stdout).hexdigest(),
                                  stderr_sha256=hashlib.sha256(process.stderr).hexdigest()))
        save()
        assert process.returncode==code,(label,process.returncode,process.stderr.decode(errors='replace'))
        assert 'Required action:' in (directory/'WARNINGS.md').read_text(encoding='utf-8')
        status=json.loads((directory/'analysis-status.json').read_text(encoding='utf-8'))['status']
        assert status==('completed-with-warnings' if code==0 else 'failed')
        if code:assert not (directory/'results.tsv').exists()
        return directory

    try:
        for index,java in enumerate(args.java or ['java'],1):
            prefix='java'+str(index)
            directory=run(java,prefix+'-measure',['--operation','measure','--input',str(measure),'--units','angstrom'],0)
            actual=read(directory/'results.tsv');assert len(actual)==384
            for row,(identity,kind,value) in zip(actual,expected):
                assert row['measurement_id']==identity and row['kind']==kind
                assert row['unit']==('angstrom' if kind=='distance' else 'degree')
                near(row['value'],value,1e-5 if kind=='distance' else 5e-5,identity)
            for s in selected:
                directory=run(java,prefix+'-fit-'+s['case'],['--operation','fit','--input',str(inputs/('fit-'+s['case']+'.tsv')),'--units','angstrom','--degeneracy','error'],0)
                summary=read(directory/'results.tsv');assert len(summary)==1
                assert int(summary[0]['pairs'])==int(s['pairs']) and summary[0]['rotation_unique']=='true'
                near(summary[0]['fitted_rmsd_angstrom'],float(s['pair_fit_rmsd']),1e-5,'fit-'+s['case'])
                actual=read(directory/'fit-pairs.tsv');case_pairs=by_case[s['case']]
                assert len(actual)==len(case_pairs)
                record['fit_pairs_checked']+=len(actual)
                for row,pair in zip(actual,case_pairs):
                    assert row['pair_id']==pair['pair']
                    for a in 'xyz':near(row['fitted_'+a+'_angstrom'],float(pair['post_'+a]),5e-5,'post-'+a)
                    near(row['residual_angstrom'],float(pair['post_residual']),5e-5,'residual')
            directory=run(java,prefix+'-nonunique',['--operation','fit','--input',str(line),'--units','angstrom','--degeneracy','allow-nonunique'],0)
            assert 'NONUNIQUE FIT EXPLICITLY ALLOWED' in (directory/'WARNINGS.md').read_text(encoding='utf-8')
            controls=[('units',['--operation','measure','--input',str(measure),'--units','invalid']),
                      ('row-cap',['--operation','measure','--input',str(measure),'--units','angstrom','--max-rows','1']),
                      ('byte-cap',['--operation','measure','--input',str(measure),'--units','angstrom','--max-bytes','1']),
                      ('degenerate',['--operation','fit','--input',str(line),'--units','angstrom','--degeneracy','error']),
                      ('missing-policy',['--operation','fit','--input',str(line),'--units','angstrom']),
                      ('missing-input',['--operation','measure','--input',str(inputs/'absent.tsv'),'--units','angstrom'])]
            for name,options in controls:run(java,prefix+'-control-'+name,options,2)
        record.update(status='passed-packaged-native-original-numeric-contracts',
                      successful_workflows=sum(r['exit_code']==0 for r in record['runs']),
                      failure_controls=sum(r['exit_code']==2 for r in record['runs']))
    except Exception as error:
        record.update(status='failed-retained',error=str(error))
        raise
    finally:
        save()
        print(json.dumps({k:v for k,v in record.items() if k!='runs'},indent=2))


if __name__=='__main__':main()
