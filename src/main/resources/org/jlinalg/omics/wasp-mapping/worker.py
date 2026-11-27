# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run exact bound original WASP scripts, or census fixed-type HDF5 content. No installation."""
import hashlib,json,os,random,runpy,sys
from pathlib import Path

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')

def hdf_census(paths,output,maximum_cells):
    import numpy as np
    import tables
    result=[];total=0
    def column(node,name,dtype,shape,reader):
        nonlocal total
        assert dtype.kind!='O','Object/pickled HDF5 data is outside the original fixed-type WASP domain'
        cells=int(np.prod(shape,dtype=object))if shape else 1;total+=cells
        assert total<=maximum_cells,'Complete HDF5 cell census capacity exceeded'
        digest=hashlib.sha256();length=shape[0]if shape else 1
        for start in range(0,length,65536):
            value=np.asarray(reader(start,min(start+65536,length)))
            assert value.dtype==dtype,'HDF5 dtype changed during complete census'
            digest.update(value.tobytes(order='C'))
        return dict(name=name,dtype=dtype.str,shape=[int(n)for n in shape],cells=cells,sha256=digest.hexdigest())
    for path in paths:
        nodes=[]
        with tables.open_file(path,'r')as hdf:
            for node in hdf.walk_nodes('/'):
                if isinstance(node,tables.Group):nodes.append(dict(path=node._v_pathname,kind='group'));continue
                if isinstance(node,tables.Table):
                    cols=[]
                    for name in node.colnames:
                        dtype=node.coldtypes[name];cols.append(column(node,name,dtype,(node.nrows,),lambda a,b,n=name:node.read(a,b,field=n)))
                    entry=dict(path=node._v_pathname,kind='table',rows=int(node.nrows),columns=cols)
                    if node._v_pathname.startswith('/samples_')and 'name'in node.colnames:
                        entry['sample_names']=[row['name'].decode('utf-8')for row in node]
                    if 'pos'in node.colnames:
                        previous=0;minimum=None;maximum=None;nonpositive=0;nonincreasing=0
                        for start in range(0,node.nrows,65536):
                            values=node.read(start,min(start+65536,node.nrows),field='pos')
                            for value in values:
                                value=int(value);nonpositive+=value<1;nonincreasing+=value<=previous;previous=value
                                minimum=value if minimum is None else min(minimum,value);maximum=value if maximum is None else max(maximum,value)
                        entry['positions']=dict(minimum=minimum,maximum=maximum,nonpositive=nonpositive,nonincreasing=nonincreasing)
                    nodes.append(entry)
                elif isinstance(node,(tables.Array,tables.CArray,tables.EArray)):
                    dtype=node.atom.dtype;shape=node.shape
                    entry=dict(path=node._v_pathname,kind='array',data=column(node,None,dtype,shape,lambda a,b:node.read(a,b)))
                    if Path(path).name=='haplotypes.h5':
                        assert dtype==np.dtype('int8'),'Original haplotype/phase cells must be signed int8'
                        invalid=0;is_phase=node._v_pathname.startswith('/phase_')
                        for start in range(0,shape[0],65536):
                            value=node.read(start,min(start+65536,shape[0]));invalid+=int(np.count_nonzero((value<(0 if is_phase else -1))|(value>1)))
                        entry['invalid_original_cell_values']=invalid
                    nodes.append(entry)
                else:raise ValueError('Unsupported or linked HDF5 node: '+node._v_pathname)
        result.append(dict(role=Path(path).name,nodes=nodes))
    # Independently check every index cell against the complete ordered SNP
    # positions. A hash alone cannot establish that a supplied HDF index maps
    # coordinates to the right row. Work in fixed-size chunks without a dense
    # second chromosome-sized array or an in-memory position dictionary.
    by_role={Path(path).name:path for path in paths}
    index_nodes={row['path']:row for item in result if item['role']=='snp-index.h5'for row in item['nodes']}
    with tables.open_file(by_role['snp-table.h5'],'r')as snps,tables.open_file(by_role['snp-index.h5'],'r')as indices:
        for table in snps.walk_nodes('/',classname='Table'):
            index=indices.get_node(table._v_pathname)
            assert len(index.shape)==1 and index.atom.dtype==np.dtype('<i8'),'Original SNP index must be a signed int64 vector'
            assert 'pos'in table.colnames and table.coldtypes['pos']==np.dtype('<i8'),'Original SNP positions must be signed int64'
            def positions():
                for start in range(0,table.nrows,65536):
                    for row,value in enumerate(table.read(start,min(start+65536,table.nrows),field='pos'),start):yield row,int(value)
            cursor=iter(positions());pending=next(cursor,None);mismatches=0
            for start in range(0,index.shape[0],65536):
                stop=min(start+65536,index.shape[0]);expected=np.full(stop-start,-1,dtype='<i8')
                while pending is not None and pending[1]<=stop:
                    row,position=pending
                    assert position>start,'SNP positions must be positive, unique and increasing'
                    expected[position-start-1]=row;pending=next(cursor,None)
                mismatches+=int(np.count_nonzero(index.read(start,stop)!=expected))
            assert pending is None,'SNP positions exceed the complete index coordinate domain'
            index_nodes[table._v_pathname]['variant_index_correspondence']=dict(checked_cells=int(index.shape[0]),SNP_rows=int(table.nrows),mismatched_cells=mismatches)
    save(output,dict(status='COMPLETE_FIXED_TYPE_HDF5_POPULATION_CENSUS',files=result,cells=total,object_arrays_or_external_links_followed=False))

def main():
    operation=sys.argv[1]
    if operation=='hdf-census':
        hdf_census(sys.argv[4:],Path(sys.argv[2]),int(sys.argv[3]));return
    if operation!='original':raise ValueError('Choose original or hdf-census')
    source=Path(sys.argv[2]);script=sys.argv[3];seed=int(sys.argv[4])
    assert script in ['find_intersecting_snps.py','filter_remapped_reads.py','rmdup_pe.py']
    assert str(source.resolve(strict=True))==str(source)and source.is_dir()
    # Python is launched with -B -s and a completely controlled environment.
    # PYTHONHASHSEED is explicit at interpreter startup so original set iteration
    # remains reproducible without sorting or patching original implementations.
    assert os.environ.get('PYTHONDONTWRITEBYTECODE')=='1'and os.environ.get('PYTHONNOUSERSITE')=='1'
    assert os.environ.get('PYTHONHASHSEED','').isdigit()
    assert not sys.flags.ignore_environment,'Original set iteration requires the explicit startup PYTHONHASHSEED'
    assert sys.flags.safe_path,'Launch original execution with Python3.11 -P so owned output/input files cannot shadow imports'
    sys.path.insert(0,str(source));random.seed(seed)
    sys.argv=[str(source/script),*sys.argv[5:]]
    runpy.run_path(str(source/script),run_name='__main__')

if __name__=='__main__':main()
