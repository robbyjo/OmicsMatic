#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve original prepared inputs and generate NumPy/SciPy file-format controls."""
from pathlib import Path
import argparse,hashlib,io,json,os,shutil,subprocess,sys,time,zipfile
import numpy as np
import scipy
from scipy.sparse import csr_matrix,csc_matrix,coo_matrix,save_npz

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    root=a.root.resolve();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
    assert np.__version__=='1.19.5' and scipy.__version__=='1.5.2'
    source=root/'build/pops/source-audit-1/upstream/pops.py'
    assert pin(source.read_bytes())['sha256']=='35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'
    old=root/'build/pops/original-controls-1';frozen=json.loads((root/'docs/literature/pops-native-development/PRIVATE-RECORDS.json').read_bytes())['files']
    target=out/'files';target.mkdir();names=[]
    for row in json.loads((old/'CASES.json').read_bytes()):
        name=row['name'];names.append(name);dest=target/name;dest.mkdir()
        for f in sorted((old/name).iterdir()):
            if f.name in ['annotation.tsv','features.rows.txt','features.cols.0.txt','features.mat.0.npy','scores.tsv','score-covariates.tsv','error-covariance.npz','magma.genes.out','magma.genes.raw','subset.txt','controls.txt','params.properties','PROCESS.json','original.preds','original.coefs','original.marginals']:
                raw=f.read_bytes();assert pin(raw)==frozen['original-controls-1/'+name+'/'+f.name];(dest/f.name).write_bytes(raw)
    base=target/'ridge-custom-gls';cov=csr_matrix(np.loadtxt(str(old/'ridge-custom-gls/covariance.tsv'),delimiter='\t'))
    additional=[]
    for kind in ['csc','coo-duplicates','csr-duplicates','dense-c','dense-fortran-big','cross-chromosome']:
        dest=target/('format-'+kind);shutil.copytree(base,dest)
        for name in ['original.preds','original.coefs','original.marginals','PROCESS.json']:(dest/name).unlink()
        dense=cov.toarray();covname='covariance.npz'
        if kind=='csc':save_npz(str(dest/covname),csc_matrix(dense))
        elif kind=='coo-duplicates':
            coo=cov.tocoo();r=np.repeat(coo.row,2);c=np.repeat(coo.col,2);v=np.repeat(coo.data/2,2)
            save_npz(str(dest/covname),coo_matrix((v,(r,c)),shape=cov.shape))
        elif kind=='csr-duplicates':save_npz(str(dest/covname),csr_matrix((np.repeat(cov.data/2,2),np.repeat(cov.indices,2),cov.indptr*2),shape=cov.shape))
        else:
            if kind=='cross-chromosome':dense[0,24]=float('nan');dense[24,0]=1.5
            covname='covariance.npy';np.save(str(dest/covname),np.asfortranarray(dense.astype('>f8')) if kind=='dense-fortran-big' else dense)
        before=json.loads((base/'PROCESS.json').read_bytes());command=before['command'];flags=command[2:]
        for i,flag in enumerate(flags):
            if flag.startswith('/'):flags[i]=str(dest/Path(flag).name)
        flags[flags.index('--feature_mat_prefix')+1]=str(dest/'features');flags[flags.index('--out_prefix')+1]=str(dest/'original')
        flags[flags.index('--y_error_cov_path')+1]=str(dest/covname)
        command=[sys.executable,str(source)]+flags;started=time.monotonic();env=os.environ.copy();env.update(OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',PYTHONHASHSEED='0')
        with (dest/'original.stdout').open('xb') as so,(dest/'original.stderr').open('xb') as se:r=subprocess.run(command,env=env,stdout=so,stderr=se,timeout=180)
        record=dict(name=dest.name,command=command,exit_code=r.returncode,seconds=round(time.monotonic()-started,3),covariance_file=covname)
        save(dest/'PROCESS.json',record);assert r.returncode==0,(kind,(dest/'original.stderr').read_text()[-3000:])
        additional.append(record);names.append(dest.name)
    npydir=target/'npy';npydir.mkdir();index=[]
    for dtype in ['f8','f4','i1','i2','i4','i8','u1','u2','u4','u8','b1']:
        values=np.array([[0,1,2],[3,4,5],[6,7,8]],dtype=dtype)
        if dtype=='f8':values[0,1]=-2.5;values[1,2]=1e-200;values[2,1]=-1e200
        elif dtype=='f4':values[0,1]=-2.5;values[1,2]=np.float32(1e-30);values[2,1]=np.float32(-1e30)
        elif dtype=='u8':values[0,0]=2**64-1;values[0,1]=2**63+4097;values[2,2]=2**63-1
        elif dtype=='i8':values[0,0]=-2**63;values[2,2]=2**63-1
        for endian in ['<','>']:
            for order in ['C','F']:
                for version in [(1,0),(2,0),(3,0)]:
                    array=np.array(values,dtype=np.dtype(dtype).newbyteorder(endian),order=order)
                    name='%s-%s-%s-v%d.npy'%(dtype,'little' if endian=='<' else 'big',order,version[0])
                    with (npydir/name).open('xb') as f:np.lib.format.write_array(f,array,version=version,allow_pickle=False)
                    expected=name+'.tsv';np.savetxt(str(npydir/expected),array.astype(np.float64),delimiter='\t',fmt='%.17g')
                    index.append(name+'\t'+expected)
    (npydir/'index.tsv').write_text('file\texpected\n'+'\n'.join(index)+'\n')
    np.save(str(npydir/'vector.npy'),np.array([0,2**64-1,2**63+4097],dtype='>u8'))
    np.save(str(npydir/'scalar.npy'),np.array('csr',dtype='S3'));np.save(str(npydir/'unicode.npy'),np.array('csc',dtype='>U3'))
    np.save(str(npydir/'empty.npy'),np.asfortranarray(np.zeros((0,2),dtype='f8')))
    np.save(str(npydir/'nonfinite.npy'),np.array([[np.nan,np.inf,-np.inf]],dtype='>f8'))
    save(target/'CASES.json',names)
    save(target/'SOURCE.json',dict(pops=pin(source.read_bytes()),numpy=np.__version__,scipy=scipy.__version__,python=sys.version,producer=pin(Path(__file__).read_bytes()),saved_original_cases=13,fresh_original_format_cases=additional,npy_matrix_cases=len(index)))
    members={f.relative_to(target).as_posix():pin(f.read_bytes()) for f in sorted(target.rglob('*')) if f.is_file()}
    save(out/'MEMBERS.json',members)
    with zipfile.ZipFile(out/'pops-workflow-inputs.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in members:z.write(target/name,name)
    save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_POPS_INPUTS_AND_NUMPY_SCIPY_FORMAT_REFERENCES',saved_original_cases=13,fresh_original_format_cases=6,npy_matrix_cases=len(index),files=len(members),archive=pin((out/'pops-workflow-inputs.zip').read_bytes())))
    print(json.dumps(json.loads((out/'COMPLETED.json').read_bytes())),flush=True)

if __name__=='__main__':main()
