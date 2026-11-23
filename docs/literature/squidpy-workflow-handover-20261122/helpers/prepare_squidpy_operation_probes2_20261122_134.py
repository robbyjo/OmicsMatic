from pathlib import Path
R=Path('D:/projects/JLinAlg')
source=(R/'build/probe_squidpy_operations_20261122_134.py').read_text(encoding='utf-8')
source=source.replace("nanostring=B/'nanostring'","nanostring=B/'nanostring-input'")
source=source.replace("nanostring_directory=str(nanostring))","nanostring_directory=str(nanostring),limits=limits)")
anchor="results=[]\nfor name,operation,options in cases:"
assert anchor in source
extra='''# Actual explicit data intake probes; matrices/identifiers remain unchanged.
from scipy.io import mmwrite
prepared=op.dataset(copy.deepcopy(spec),limits,anndata_version)
prepared.write_h5ad(B/'input.h5ad')
loaded=op.read_input(dict(format='h5ad',file=str(B/'input.h5ad')),limits,anndata_version)
assert list(loaded.obs_names)==spec['obs_names'] and list(loaded.var_names)==spec['var_names']
assert np.array_equal(loaded.X,prepared.X)
from scipy.sparse import csr_matrix
mmwrite(str(B/'input.mtx'),csr_matrix(prepared.X.T),symmetry='general',precision=17)
with (B/'cells.tsv').open('x',encoding='utf-8') as stream:
 stream.write('cell_id\\tcluster\\tlibrary\\tscore\\n')
 for i,name in enumerate(spec['obs_names']):stream.write(name+'\\t'+spec['obs']['cluster']['values'][i]+'\\t'+spec['obs']['library']['values'][i]+'\\t'+repr(spec['obs']['score']['values'][i])+'\\n')
with (B/'features.tsv').open('x',encoding='utf-8') as stream:
 stream.write('feature_id\\thighly_variable\\n')
 for i,name in enumerate(spec['var_names']):stream.write(name+'\\t'+str(spec['var']['highly_variable']['values'][i]).lower()+'\\n')
definition=dict(format='matrix_market',matrix=str(B/'input.mtx'),cells=str(B/'cells.tsv'),features=str(B/'features.tsv'),orientation='features_by_cells',dtype='float64',
 obs_columns={k:{a:v for a,v in c.items() if a!='values'} for k,c in spec['obs'].items()},var_columns={k:dict(kind=c['kind']) for k,c in spec['var'].items()})
loaded=op.read_input(definition,limits,anndata_version)
assert list(loaded.obs_names)==spec['obs_names'] and list(loaded.var_names)==spec['var_names']
assert np.array_equal(loaded.X.toarray(),prepared.X)
op.write_json(B/'INTAKE-CHECKS.json',dict(status='H5AD_AND_MATRIX_MARKET_INTAKES_MATCH_PREPARED_SOURCE',shape=list(loaded.shape)))
results=[]
for name,operation,options in cases:''' 
source=source.replace(anchor,extra)
target=R/'build/probe_squidpy_operations2_20261122_134.py';assert not target.exists();target.write_text(source,encoding='utf-8')
launcher=(R/'build/launch_squidpy_operation_probes_20261122_134.py').read_text(encoding='utf-8')
launcher=launcher.replace("'-1-launch'","'-2-launch'").replace("'-1']","'-2']").replace('probe_squidpy_operations_20261122_134.py','probe_squidpy_operations2_20261122_134.py').replace("'-cache-1'","'-cache-2'").replace("'-mpl-1'","'-mpl-2'")
target=R/'build/launch_squidpy_operation_probes2_20261122_134.py';assert not target.exists();target.write_text(launcher,encoding='utf-8')
