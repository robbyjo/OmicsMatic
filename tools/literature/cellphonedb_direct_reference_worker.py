# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Uninstrumented original calls for paired safe-worker controls. Pickles are opaque; only original returned data is exported."""
import importlib.metadata as metadata,json,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(sys.argv[3]).resolve()))
from codec import clean,save
import numpy as np
import pandas as pd
from cellphonedb.src.core.methods import cpdb_analysis_method as simple
from cellphonedb.src.core.methods import cpdb_statistical_analysis_method as statistical
from cellphonedb.src.core.methods import cpdb_degs_analysis_method as degs


def legacy(value):
    if isinstance(value,pd.DataFrame):return {'$dataframe':dict(index=legacy(value.index.to_list()),index_names=legacy(value.index.names),columns=legacy(value.columns.to_list()),column_names=legacy(value.columns.names),dtypes=[str(d) for d in value.dtypes],values=legacy(value.values.tolist()))}
    if isinstance(value,np.ndarray):return {'$ndarray':dict(dtype=str(value.dtype),shape=list(value.shape),values=legacy(value.tolist()))}
    if isinstance(value,np.generic):return legacy(value.item())
    if isinstance(value,float) and not np.isfinite(value):return 'NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf'
    if isinstance(value,dict):return {k:legacy(v) for k,v in value.items()}
    if isinstance(value,(list,tuple)):return [legacy(v) for v in value]
    if isinstance(value,type):return {'$python_class':value.__module__+'.'+value.__qualname__}
    if value is None or isinstance(value,(int,float,bool,str)):return value
    raise TypeError('Uncaptured original returned value: '+str(type(value)))


def main():
    home=Path(sys.argv[1]);request=json.loads(Path(sys.argv[2]).read_bytes());assert metadata.version('cellphonedb')=='5.0.1' and sys.gettrace() is None;records=[]
    for case in request['analyses']:
        out=home/case['id'];out.mkdir();result_home=out/'original';result_home.mkdir();kwargs=dict(cpdb_file_path=case['database']['path'],meta_file_path=case['metadata']['path'],counts_file_path=case['counts']['path'],counts_data=case['counts_data'],output_path=str(result_home),separator=case['separator'],threshold=case['threshold'],result_precision=case['result_precision'],debug=True,output_suffix=case['id'],score_interactions=case['score_interactions'],threads=case['threads'])
        for name,arg in [('microenvironments','microenvs_file_path'),('degs','degs_file_path'),('active_tfs','active_tfs_file_path')]:
            if name in case:kwargs[arg]=case[name]['path']
        if case['method']=='statistical':
            options=case['statistical'];sampling=options['subsampling'];kwargs.update(iterations=options['iterations'],debug_seed=options['debug_seed'],pvalue=options['pvalue'],subsampling=sampling['enabled'],subsampling_log=sampling['log'],subsampling_num_pc=sampling['pcs'],subsampling_num_cells=sampling['cells'])
        np.random.seed(case['global_seed']);save(out/'rng-before.json',clean(np.random.get_state()));result={'simple':simple.call,'statistical':statistical.call,'degs':degs.call}[case['method']](**kwargs);save(out/'rng-after.json',clean(np.random.get_state()));assert isinstance(result,dict) and result and sys.gettrace() is None;save(out/'returned.json',clean(result));save(out/'returned.legacy.json',legacy(result));records.append(dict(id=case['id'],method=case['method'],threads=case['threads'],namespace_instrumented=False,pickle_deserialized=False));print('PASS_UNINSTRUMENTED_ORIGINAL',case['id'],flush=True)
    save(home/'COMPLETED.json',dict(status='PASS_UNINSTRUMENTED_ORIGINAL_CELLPHONEDB_RETURN_CONTROLS_ONLY',cases=records,implementation_credit=False,pickle_deserialized=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original control checks')
    main()
