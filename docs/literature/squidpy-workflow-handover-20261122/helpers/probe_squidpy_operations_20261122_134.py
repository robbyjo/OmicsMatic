"""Direct original-operation probes; not Java workflow qualification/admission."""
from pathlib import Path
import argparse,copy,importlib.metadata as metadata,json,sys,traceback,warnings
parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,required=True);parser.add_argument('--tools',type=Path,required=True);args=parser.parse_args()
B=args.out;B.mkdir();sys.path.insert(0,str(args.tools))
import operations as op
import numpy as np
import pandas as pd
import anndata as ad
import squidpy as sq
from PIL import Image
version=metadata.version('squidpy');anndata_version=metadata.version('anndata')
limits=dict(maximum_cells=10000,maximum_features=10000,maximum_entries=1000000,maximum_dense_entries=1000000,maximum_working_bytes=1073741824,maximum_work=2000000000,threads=1)
rng=np.random.RandomState(134);n=36
spec=dict(schema_version=1,obs_names=['cell-'+str(i) for i in range(n)],var_names=['LigA','RecB','LIGC','RECD','flat','Other'],
 X=dict(format='dense',dtype='float64',shape=[n,6],values=np.column_stack([rng.poisson(3,(n,4)),np.repeat(2.,n),rng.poisson(4,n)]).tolist()),
 obs=dict(cluster=dict(kind='categorical',categories=['beta','alpha'],ordered=True,values=['alpha' if i%3 else 'beta' for i in range(n)]),
 library=dict(kind='categorical',categories=['sample-B','sample-A'],ordered=False,values=['sample-A' if i<18 else 'sample-B' for i in range(n)]),
 score=dict(kind='float64',values=[i/7 for i in range(n)])),
 var=dict(highly_variable=dict(kind='bool',values=[True,True,True,True,False,False])),
 obsm=dict(spatial=dict(format='dense',dtype='float64',shape=[n,2],values=[[float(i%6)+(.013 if i%2 else 0.),float(i//6)] for i in range(n)])))
rows=[0];columns=[];weights=[]
for i in range(n):
 pairs=sorted([((i-1)%n,.5),((i+1)%n,1.3)])
 for j,w in pairs:columns.append(j);weights.append(w)
 rows.append(len(columns))
graph=dict(format='csr',dtype='float64',shape=[n,n],indptr=rows,indices=columns,data=weights)
spec['obsp']={'spatial_connectivities':graph,'expression_connectivities':copy.deepcopy(graph)}
spec['raw']=dict(var_names=spec['var_names'],X=copy.deepcopy(spec['X']))
op.write_json(B/'prepared.json',spec)
pairs=dict(columns=['source','target','resource','curated'],rows=[['Liga','Recb','artificial',True],['LIGC','RECD','artificial',True],['LIGA','RECD','artificial',False],['Liga','Recb','duplicate-kept-in-source-input',True]],
 provenance=dict(kind='synthetic-only',database_downloaded=False,description='Prepared local artificial pairs; no biological database claim'))
op.write_json(B/'interactions.json',pairs)
nanostring=B/'nanostring';nanostring.mkdir();(nanostring/'CellComposite').mkdir();(nanostring/'CellLabels').mkdir()
(nanostring/'counts.csv').write_text('cell_ID,fov,LigA,RecB\n2,2,8,1\n1,1,3,7\n5,1,2,2\n',encoding='utf-8')
(nanostring/'metadata.csv').write_text('cell_ID,fov,CenterX_local_px,CenterY_local_px,CenterX_global_px,CenterY_global_px,annotation\n1,1,1,2,10,20,A\n2,2,3,4,30,40,B\n9,1,5,6,50,60,unmatched\n',encoding='utf-8')
(nanostring/'fov.csv').write_text('fov,x_global_px,y_global_px\n1,100,200\n2,300,400\n',encoding='utf-8')
for fov in [1,2]:
 Image.fromarray(np.full((6,8,3),30*fov,dtype=np.uint8)).save(nanostring/'CellComposite'/('synthetic_F'+str(fov)+'.png'))
 Image.fromarray(np.full((6,8),fov,dtype=np.uint16)).save(nanostring/'CellLabels'/('synthetic_F'+str(fov)+'.png'))
seed_key='rng' if version=='1.8.4' else 'seed'
cases=[('knn','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3)),
 ('radius','SPATIAL_NEIGHBORS',dict(coord_type='generic',radius=1.1)),
 ('delaunay-range','SPATIAL_NEIGHBORS',dict(coord_type='generic',delaunay=True,radius=[0.,2.])),
 ('grid','SPATIAL_NEIGHBORS',dict(coord_type='grid',n_neighs=4,n_rings=2,set_diag=True)),
 ('spectral','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3,transform='spectral')),
 ('moran-permutations','SPATIAL_AUTOCORR',dict(genes=spec['var_names'][:4],n_perms=19,n_jobs=1,show_progress_bar=False,**{seed_key:17})),
 ('geary','SPATIAL_AUTOCORR',dict(genes=spec['var_names'][:4],mode='geary',transformation=False,corr_method=None)),
 ('undefined-fdr','SPATIAL_AUTOCORR',dict(genes=spec['var_names'],corr_method='fdr_bh')),
 ('raw','SPATIAL_AUTOCORR',dict(genes=spec['var_names'][:4],use_raw=True,corr_method=None)),
 ('expression-graph','SPATIAL_AUTOCORR',dict(connectivity_key='expression_connectivities',genes=spec['var_names'][:4],corr_method=None)),
 ('ligrec','LIGREC',dict(cluster_key='cluster',use_raw=False,n_perms=19,n_jobs=1,show_progress_bar=False,corr_method=None,**{seed_key:17})),
 ('ligrec-fdr','LIGREC',dict(cluster_key='cluster',use_raw=False,n_perms=19,n_jobs=1,show_progress_bar=False,corr_method='fdr_bh',corr_axis='clusters',**{seed_key:17}))]
if version!='1.1.2':cases += [('library','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3,library_key='library')),
 ('nanostring','READ_NANOSTRING',dict(counts_file='counts.csv',meta_file='metadata.csv',fov_file='fov.csv'))]
if version=='1.8.4':cases += [('obs-attribute','SPATIAL_AUTOCORR',dict(attr='obs',genes=['score'],corr_method=None))]
results=[]
for name,operation,options in cases:
 record=dict(case=name,operation=operation,options=options,status='STARTED')
 try:
  with warnings.catch_warnings(record=True) as messages:
   warnings.simplefilter('always')
   data=op.dataset(copy.deepcopy(spec),limits,anndata_version)
   request=dict(operation=operation,options=options,expected_squidpy=version,interactions_file=str(B/'interactions.json'),nanostring_directory=str(nanostring))
   record['output']=op.call(data,request,B/name,sq)
  record.update(status='ORIGINAL_OPERATION_RETURNED_AND_EXPORTED',warnings=[dict(category=w.category.__name__,message=str(w.message)) for w in messages])
 except Exception as error:record.update(status='FAILED_RETAINED',exception=type(error).__name__,message=str(error),traceback=traceback.format_exc())
 results.append(record);op.write_json(B/(name+'-RESULT.json'),record);print(json.dumps({k:record[k] for k in ['case','status']},sort_keys=True),flush=True)
op.write_json(B/'RESULT.json',dict(status='DEVELOPMENT_ORIGINAL_OPERATION_PROBES_FINISHED',squidpy=version,anndata=anndata_version,
 results=results,completed=sum(r['status']=='ORIGINAL_OPERATION_RETURNED_AND_EXPORTED' for r in results),
 failed=sum(r['status']=='FAILED_RETAINED' for r in results),java_workflow_envelope_qualified=False,new_family_credit=0))
