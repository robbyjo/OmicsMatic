"""Synthetic original ALLCools 1.0.8 count/score references; no installs or paper data."""
import argparse, hashlib, importlib.metadata as md, json, os, random, sys
from pathlib import Path

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1048576),b''):h.update(block)
    return dict(bytes=path.stat().st_size,sha256=h.hexdigest())

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    assert not args.out.exists(),'Use a NEW output directory';args.out.mkdir(parents=True)
    import numpy as np, pandas as pd, xarray as xr
    from ALLCools.count_matrix import dataset as original
    versions={n:md.version(n) for n in ('ALLCools','numpy','pandas','scipy','xarray')}
    assert versions==dict(ALLCools='1.0.8',numpy='1.23.5',pandas='1.5.3',scipy='1.10.1',xarray='2023.1.0'),versions
    assert digest(Path(original.__file__))['sha256']=='575122139c8aba672396948bdf504199d23d6eb815011d959265ccf37116b40f','Different original dataset.py; do not refresh this source pin'
    rng=random.Random(20261126)
    groups=[('all-zero',[0]*7,[0]*7),('one-zero',[0],[0]),('one-full',[10],[10]),
            ('one-partial',[7],[13]),('mixed',[0,1,0,5,90,0],[0,2,5,5,100,0]),
            ('saturated',[6000000000,2,0,9000000000],[8000000000,5,0,10000000000]),
            ('uint32-edge',[4294967295,4294967296,0],[4294967295,4294967297,1]),
            ('deep',[1,500000000000,999999999999],[1000000000000]*3)]
    for g in range(160):
        cov=[rng.randrange(0,10**rng.randrange(1,8)) for _ in range(rng.randrange(1,100))]
        mc=[rng.randrange(c+1) for c in cov];groups.append(('random-%03d'%g,mc,cov))
    cutoff_pairs=[(0.,0.),(.9,.9),(.5,.75),(1.,1.),(1e-8,.89995)]
    rows=[];checked=0
    for name,mc,cov in groups:
        matrix=np.array([list(zip(mc,cov))],dtype='int64')
        data=xr.DataArray(matrix,dims=['cell','region','count_type'],coords=[['literal-cell'],['r%d'%i for i in range(len(mc))],['mc','cov']])
        sf=original._cell_sf(pd.DataFrame({'mc':mc,'cov':cov})).values
        # Values immediately around source binary16 cutoffs exercise weak scalar promotion.
        pairs=list(cutoff_pairs)
        if name.startswith('random-'):
            v=float(sf[len(sf)//2]);pairs.extend([(max(0.,float(np.nextafter(v,-np.inf))),min(1.,float(np.nextafter(1-v,np.inf))))])
        for i,(low,high) in enumerate(pairs):
            hypo=original._calculate_pv(data,False,'cell','region',cutoff=low).values[0]
            hyper=original._calculate_pv(data,True,'cell','region',cutoff=high).values[0]
            counts=xr.where(data>np.iinfo(np.uint32).max,np.iinfo(np.uint32).max,data).astype('uint32').values[0]
            pool=float(np.array(mc,dtype='int64').sum()/(np.array(cov,dtype='int64').sum()+0.000001))
            fields=[name+'-%d'%i,str(low),str(high),','.join(map(str,mc)),','.join(map(str,cov)),repr(pool),
                    ','.join(map(str,counts[:,0].tolist())),','.join(map(str,counts[:,1].tolist())),
                    ','.join(repr(float(x)) for x in hypo),','.join(repr(float(x)) for x in hyper)]
            rows.append('\t'.join(fields)+'\n');checked+=len(mc)*4+1
    table=args.out/'cases.tsv'
    with table.open('x',encoding='utf-8',newline='\n')as f:
        f.write('case\thypo_cutoff\thyper_cutoff\tmc\tcov\tpooled_probability\tcount_mc\tcount_cov\thypo\thyper\n');f.writelines(rows)
    receipt=dict(schema_version=1,status='PASS_ORIGINAL_ALLCOOLS_REGION_REFERENCE_GENERATION',cases=len(rows),numeric_values=checked,
                 group_population=len(groups),versions=versions,python=sys.version,original_source=digest(Path(original.__file__)),fixture=digest(table),
                 original_namespace_imported=True,original_source_modified=False,synthetic_only=True,whole_package_admitted=False)
    (args.out/'COMPLETED.json').write_text(json.dumps(receipt,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt),flush=True)

if __name__=='__main__':main()
