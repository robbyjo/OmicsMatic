# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile an original ridoutlier caller and capture exact synthetic boundary behavior."""
from pathlib import Path
import argparse,json,math,os,random,shutil,subprocess,zipfile
from setup_smartpca_reference import COMMIT,pin,save
ROOT=Path(__file__).resolve().parents[2]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--setup',type=Path,required=True);parser.add_argument('--intake',type=Path,required=True)
    parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    out=a.out.resolve();runtime=a.runtime.resolve();assert not out.exists() and not runtime.exists();out.mkdir(parents=True);runtime.mkdir(parents=True)
    source=json.loads((a.intake/'SOURCE.json').read_bytes());assert source['commit']==COMMIT
    original=a.intake/'original-source'
    for row in source['members']:assert pin(original/row['path'])=={k:row[k] for k in ('bytes','sha256')}
    setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['source_commit']==COMMIT and pin(Path(setup['binary']))==setup['binary_identity']
    prefix=Path(setup['dependency_prefix']);archive=a.setup/'source-build/src/nicksrc/libnick.a';archive_before=pin(archive)
    harness=ROOT/'tools/literature/smartpca_reference/outlier_harness.c';shutil.copyfile(harness,out/'outlier_harness.c')
    shutil.copyfile(Path(__file__),out/'generator.py');env=dict(os.environ,OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',LC_ALL='C')
    commands=[('compile-original-ridoutlier',['gcc','-O2','-fcommon','-I'+str(original/'include'),'-I'+str(prefix/'include'),'-c',str(original/'src/eigensrc/smartsubs.c'),'-o',str(runtime/'original-smartsubs.o')]),
        ('link-caller',['gcc','-std=gnu11','-O2','-fcommon','-I'+str(original/'include'),'-I'+str(prefix/'include'),str(harness),str(runtime/'original-smartsubs.o'),str(archive),'-L'+str(prefix/'lib'),'-Wl,-rpath,'+str(prefix/'lib'),'-lgsl','-lopenblas','-lm','-lpthread','-o',str(runtime/'outlier-reference')])]
    for name,command in commands:
        result=subprocess.run(command,env=env,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
        save(out/(name+'.json'),dict(command=command,exit_code=result.returncode));assert result.returncode==0,result.stderr
    cases=[('strict-equality',[[1.],[-1.],[1.],[-1.]],0,1.),('one-ulp-below-cutoff',[[1.],[-1.],[1.],[-1.]],0,math.nextafter(1.,0.)),
        ('all-sample-mean',[[1.]]+[[0.]]*7,0,2.),('leave-one-mean',[[1.]]+[[0.]]*7,1,2.),
        ('leave-one-variance-divides-by-n',[[3.],[1.],[-1.],[-3.]],1,1.5),
        ('constant-axis',[[1.]]*6,0,1.),('constant-axis-leave-one',[[1.]]*6,1,1.),
        ('two-sample-bypass',[[1.],[-1.]],0,.1),('disabled',[[1.]]+[[0.]]*7,2,.1),
        ('zero-axes',[[],[],[],[]],0,1.),('first-axis-per-sample',[[8.,9.],[-8.,0.],[0.,-9.],[0.,0.],[0.,0.],[0.,0.],[0.,0.],[0.,0.]],0,1.3)]
    rng=random.Random(382910)
    for i in range(12):
        matrix=[[rng.uniform(-2,2) for _ in range(3)] for _ in range(16)];matrix[i%16][i%3]*=12
        cases.append(('random-'+str(i),matrix,i%2,1.8))
    records=[]
    for name,matrix,mode,threshold in cases:
        folder=out/name;folder.mkdir();raw=(f'{len(matrix)} {len(matrix[0])} {mode} {threshold:.17g}\n'+''.join(' '.join(format(x,'.17g') for x in row)+'\n' for row in matrix)).encode()
        (folder/'input.txt').write_bytes(raw);result=subprocess.run([str(runtime/'outlier-reference')],input=raw,env=env,capture_output=True,timeout=30)
        (folder/'original.stdout').write_bytes(result.stdout);(folder/'original.stderr').write_bytes(result.stderr);assert result.returncode==0,result.stderr
        output=json.loads(result.stdout);record=dict(case=name,vectors=matrix,mode=mode,threshold=threshold,result=output,exit_code=result.returncode,
            source_commit=COMMIT,original_function_source=pin(original/'src/eigensrc/smartsubs.c'),binary=pin(runtime/'outlier-reference'))
        save(folder/'reference.json',record);records.append(record)
    assert pin(archive)==archive_before
    with zipfile.ZipFile(out/'original-outlier-reference.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for path in sorted(out.rglob('*')):
            if path.is_file() and path.name!='original-outlier-reference.zip':z.write(path,path.relative_to(out).as_posix())
    save(out/'COMPLETED.json',dict(status='PASS_UNCHANGED_ORIGINAL_RIDOUTLIER_FUNCTION_CALLS',cases=len(records),
        source_commit=COMMIT,original_function_modified=False,caller_source=pin(harness),binary=pin(runtime/'outlier-reference'),
        original_static_library_unchanged=archive_before,archive=pin(out/'original-outlier-reference.zip'),runtime=str(runtime),
        workflow_qualified=False,catalog_credit=0,new_reviews=0,whole_goal_complete=False))
    print('PASS:',len(records),'unchanged original ridoutlier calls; source library unchanged.')
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable original source checks')
    main()
