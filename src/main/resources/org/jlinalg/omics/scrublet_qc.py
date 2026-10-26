"""Pinned original Scrublet process; native Java owns input staging and result validation."""
import csv
import hashlib
import importlib
import importlib.metadata as metadata
import json
import math
from pathlib import Path
import sys
import traceback
import warnings

PINS={'scrublet':'0.2.3','annoy':'1.17.3','numpy':'2.5.4','scipy':'1.18.1','scikit-learn':'1.9.1','scikit-image':'0.26.0'}


def write_table(path,header,rows):
    with path.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.writer(stream,delimiter='\t',lineterminator='\n',quotechar=None,quoting=csv.QUOTE_NONE);writer.writerow(header);writer.writerows(rows)


def run(config,out,record):
    try:
        record['versions']={name:metadata.version(name) for name in PINS}
        if record['versions']!=PINS:raise RuntimeError('Pinned Scrublet/scientific dependency versions differ; supply the tested environment')
        import numpy as np
        import scipy.sparse as sparse
        from scipy.io import mmread
        import scrublet
        import annoy  # Required when an approximate analysis is requested; probe the native extension too.
    except Exception as failure:
        record.update(status='skipped-dependency-unavailable',error=str(failure));return 3
    module=importlib.import_module('scrublet.scrublet');helper=importlib.import_module('scrublet.helper_functions')
    record['source_sha256']={str(Path(m.__file__).name):hashlib.sha256(Path(m.__file__).read_bytes()).hexdigest() for m in [module,helper]}
    if record['source_sha256']!={'scrublet.py':'c2065e0acac00c405da36c2cc3fd9fc9453b907d5f66987a9efdee906181b1dc','helper_functions.py':'bfd54fc68d3dde827615e9ea2ccbd0927fc52811fbdb34700acb34cbdcd5c70e'}:
        record.update(status='skipped-dependency-unavailable',error='Scrublet0.2.3 scientific source differs from the validated unmodified PyPI files');return 3
    if config.get('probe'):
        record['status']='available-not-yet-analysis-validated';return 0
    counts=Path(config['counts']);features=[line.split('\t')[0] for line in (counts/'features.tsv').read_text(encoding='utf-8').splitlines()];cells=(counts/'barcodes.tsv').read_text(encoding='utf-8').splitlines()
    matrix=mmread(counts/'matrix.mtx',spmatrix=True).T.tocsc().astype(np.float64);matrix.sum_duplicates();matrix.sort_indices()
    if matrix.shape!=(len(cells),len(features)) or not np.isfinite(matrix.data).all() or (matrix.data<0).any():raise ValueError('Canonical sparse matrix/ID contract changed')
    if len(cells)<3 or np.any(np.asarray(matrix.sum(axis=1)).ravel()<=0):raise ValueError('Scrublet requires at least three cells with positive selected-feature totals; inspect counts and QC independently')
    if config['count_kind']=='estimate' and config['umi_subsampling']!=1:raise ValueError('UMI binomial subsampling requires raw integer counts, not fractional estimates')
    n_sim=int(len(cells)*config['simulation_ratio'])
    if n_sim<1:raise ValueError('Simulation ratio yields no synthetic doublets')
    obj=scrublet.Scrublet(matrix,sim_doublet_ratio=config['simulation_ratio'],n_neighbors=config.get('neighbors'),expected_doublet_rate=config['expected_rate'],stdev_doublet_rate=config['rate_sd'],random_state=config['seed'])
    adjusted=int(round(obj.n_neighbors*(1+n_sim/len(cells))))
    if adjusted<1 or adjusted>=len(cells)+n_sim:raise ValueError('Adjusted neighborhood must be positive and smaller than the combined observed/simulated population')
    original=module.get_knn_graph;captured={}
    def observe_graph(*args,**kwargs):
        graph=original(*args,**kwargs)
        if graph.ndim!=2 or graph.shape[0]!=len(cells)+n_sim or np.any(graph<0) or np.any(graph>=len(cells)+n_sim):raise ValueError('Upstream neighbor graph violates its population contract')
        captured['simulated_neighbors']=(graph>=len(cells)).sum(axis=1);captured['neighbors']=graph.shape[1]
        return graph
    module.get_knn_graph=observe_graph
    try:
        scores,called=obj.scrub_doublets(synthetic_doublet_umi_subsampling=config['umi_subsampling'],use_approx_neighbors=config['approximate'],distance_metric=config['metric'],min_counts=config['min_counts'],min_cells=config['min_cells'],min_gene_variability_pctl=config['variability_percentile'],log_transform=config['log_transform'],mean_center=config['mean_center'],normalize_variance=config['normalize_variance'],n_prin_comps=config['components'],svd_solver=config['svd_solver'],get_doublet_neighbor_parents=False,verbose=False)
        if config.get('threshold') is not None:called=obj.call_doublets(threshold=config['threshold'],verbose=False)
    finally:module.get_knn_graph=original
    if captured.get('neighbors')!=adjusted:raise ValueError('Upstream effective neighbor count differs from the declared adjusted size')
    if not np.isfinite(scores).all() or not np.isfinite(obj.doublet_errors_obs_).all() or not np.isfinite(obj.doublet_scores_sim_).all() or not np.isfinite(obj.doublet_errors_sim_).all():raise ValueError('Upstream score or uncertainty is not finite')
    selected=set(int(x) for x in obj._gene_filter);nd=captured['simulated_neighbors'];threshold=float(obj.threshold_) if called is not None else None
    write_table(out/'observed.tsv',['cell_id','score','standard_error','simulated_neighbors','neighbors','predicted_doublet'],([name,repr(float(scores[i])),repr(float(obj.doublet_errors_obs_[i])),int(nd[i]),adjusted,'NA' if called is None else str(bool(called[i])).lower()] for i,name in enumerate(cells)))
    parents=obj.doublet_parents_
    if parents.shape!=(n_sim,2) or np.any(parents<0) or np.any(parents>=len(cells)):raise ValueError('Upstream synthetic parent identities violate the input contract')
    write_table(out/'simulated.tsv',['simulated_id','parent1','parent2','score','standard_error','simulated_neighbors','neighbors'],(['SIM'+str(i),cells[int(parents[i,0])],cells[int(parents[i,1])],repr(float(obj.doublet_scores_sim_[i])),repr(float(obj.doublet_errors_sim_[i])),int(nd[len(cells)+i]),adjusted] for i in range(n_sim)))
    write_table(out/'gene-selection.tsv',['feature_id','selected'],([name,str(i in selected).lower()] for i,name in enumerate(features)))
    if config.get('write_manifold'):
        for name,ids,data in [('observed',cells,obj.manifold_obs_),('simulated',['SIM'+str(i) for i in range(n_sim)],obj.manifold_sim_)]:
            if not np.isfinite(data).all():raise ValueError('Upstream manifold is not finite')
            write_table(out/('manifold-'+name+'.tsv'),['cell_id']+['PC'+str(i+1) for i in range(data.shape[1])],([id]+[repr(float(v)) for v in row] for id,row in zip(ids,data)))
    def finite(value):
        value=float(value);return value if math.isfinite(value) else None
    record.update(status='completed',observed_cells=len(cells),simulated_cells=n_sim,effective_simulation_ratio=n_sim/len(cells),neighbors=obj.n_neighbors,adjusted_neighbors=adjusted,selected_genes=len(selected),threshold=threshold,threshold_source='not-called' if called is None else 'automatic' if config.get('threshold') is None else 'explicit',detected_doublet_rate=finite(obj.detected_doublet_rate_) if called is not None else None,detectable_doublet_fraction=finite(obj.detectable_doublet_fraction_) if called is not None else None,overall_doublet_rate=finite(obj.overall_doublet_rate_) if called is not None else None,config=config)
    if called is None:record.setdefault('warnings',[]).append('AUTOMATIC THRESHOLD FAILED: scores are retained but predictions are NA. Inspect simulated-score distributions and supply an explicit reviewed threshold before removing cells.')
    if called is not None and record['detectable_doublet_fraction']==0:record.setdefault('warnings',[]).append('NO DETECTABLE SIMULATED DOUBLETS: overall doublet-rate estimate is undefined; inspect the threshold and input model.')
    if called is not None and record['overall_doublet_rate'] is not None and record['overall_doublet_rate']>1:record.setdefault('warnings',[]).append('ESTIMATED OVERALL RATE EXCEEDS ONE: inspect doublet detectability, threshold and the independent-library assumption.')
    if config['count_kind']=='estimate':record.setdefault('warnings',[]).append('FRACTIONAL/PROCESSED INPUT: this explicitly declared estimate matrix is not raw UMI data. Standard doublet-simulation/count assumptions and rate calibration require separate justification.')
    return 0


def main():
    config=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'));out=Path(config['out']);record={};code=2;captured=[]
    try:
        with warnings.catch_warnings(record=True) as captured:
            warnings.simplefilter('always');code=run(config,out,record)
    except Exception as failure:
        record.update(status='failed',error=str(failure));traceback.print_exc()
    record.setdefault('warnings',[]).extend(str(w.category.__name__)+': '+str(w.message) for w in captured)
    (out/'worker-status.json').write_text(json.dumps(record,indent=2,allow_nan=False)+'\n',encoding='utf-8');return code


if __name__=='__main__':raise SystemExit(main())
