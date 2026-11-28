# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export every original Cooler bin/raw pixel; preserve weight NaNs, order and integer counts."""
import sys,json,math,os
from pathlib import Path
import h5py,numpy as np

def main():
    request=Path(sys.argv[1]);out=Path(sys.argv[2]);r=json.loads(request.read_text());assert r['schema_version']==1;out.mkdir();records=[]
    for i,item in enumerate(r['maps']):
        target=out/('%06d'%i);target.mkdir();source=Path(item['file']);assert source.is_file();missing=0;count_sum=0
        with h5py.File(str(source),'r')as f:
            assert f.attrs['format']=='HDF5::Cooler'and f.attrs['storage-mode']=='symmetric-upper';size=int(f.attrs['bin-size']);nbins=int(f.attrs['nbins']);npixels=int(f.attrs['nnz']);assert size==item['bin_size'];assert 0<nbins<=r['maximum_bins']and 0<=npixels<=r['maximum_pixels'];assert f['pixels/count'].dtype.kind in 'iu';chroms=[x.decode('utf-8')if isinstance(x,bytes)else str(x)for x in f['chroms/name'][:]];lengths=[int(x)for x in f['chroms/length'][:]];assert len(chroms)==len(lengths)==int(f.attrs['nchroms']);assert len(f['bins/chrom'])==len(f['bins/start'])==len(f['bins/end'])==len(f['bins/weight'])==nbins;assert len(f['pixels/bin1_id'])==len(f['pixels/bin2_id'])==len(f['pixels/count'])==npixels
            with(target/'bins.tsv').open('x',encoding='utf-8',newline='\n')as stream:
                stream.write('bin_id\tchrom\tstart\tend\tweight\n')
                for start in range(0,nbins,100000):
                    stop=min(nbins,start+100000);cs=f['bins/chrom'][start:stop];ss=f['bins/start'][start:stop];es=f['bins/end'][start:stop];ws=f['bins/weight'][start:stop]
                    for j,(c,s,e,w)in enumerate(zip(cs,ss,es,ws),start):
                        w=float(w);token='NaN'if math.isnan(w)else repr(w);missing+=math.isnan(w);stream.write('%d\t%s\t%d\t%d\t%s\n'%(j,chroms[int(c)],int(s),int(e),token))
            with(target/'pixels.tsv').open('x',encoding='utf-8',newline='\n')as stream:
                stream.write('bin1_id\tbin2_id\tcount\n')
                for start in range(0,npixels,100000):
                    stop=min(npixels,start+100000);aa=f['pixels/bin1_id'][start:stop];bb=f['pixels/bin2_id'][start:stop];cc=f['pixels/count'][start:stop]
                    for a,b,c in zip(aa,bb,cc):
                        count_sum+=int(c);stream.write('%d\t%d\t%d\n'%(int(a),int(b),int(c)))
            records.append(dict(sample=item['sample'],bin_size=size,bins=nbins,pixels=npixels,missing_weight_bins=missing,count_sum=count_sum,chromosomes=[dict(name=n,length=v)for n,v in zip(chroms,lengths)],directory='%06d'%i))
    with(out/'COMPLETED.json').open('x',encoding='utf-8')as stream:json.dump(dict(schema_version=1,status='COMPLETE_ORIGINAL_COOLER_ARRAY_EXPORT_NO_SUBSET_OR_NUMERICAL_REPAIR',h5py=h5py.__version__,numpy=np.__version__,maps=records),stream,indent=2,allow_nan=False);stream.write('\n')
if __name__=='__main__':main()
