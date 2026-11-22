"""Independent original Scrublet graph/score reference; takes FIXTURES NEW_OUTPUT_DIR."""
import csv
import hashlib
import importlib
import json
from pathlib import Path
import sys
import warnings
import numpy as np
from scipy.io import mmread
import scrublet

source,out=map(Path,sys.argv[1:]);out.mkdir(parents=True,exist_ok=False)
module=importlib.import_module('scrublet.scrublet');helper=importlib.import_module('scrublet.helper_functions')
x=mmread(source/'counts.mtx',spmatrix=True).T.tocsc()
original=module.get_knn_graph;captured={}
def observe(*args,**kwargs):
    graph=original(*args,**kwargs);captured['graph']=graph.copy();return graph
module.get_knn_graph=observe
obj=scrublet.Scrublet(x,n_neighbors=10,expected_doublet_rate=.1,stdev_doublet_rate=.02,sim_doublet_ratio=2,random_state=42)
try:
    with warnings.catch_warnings(record=True) as messages:
        warnings.simplefilter('always');obj.scrub_doublets(n_prin_comps=10,use_approx_neighbors=False,verbose=False)
finally:module.get_knn_graph=original
graph=captured['graph'];scores=np.r_[obj.doublet_scores_obs_,obj.doublet_scores_sim_];errors=np.r_[obj.doublet_errors_obs_,obj.doublet_errors_sim_]
with (out/'scrublet-neighbor-scores.tsv').open('w',encoding='utf-8',newline='') as stream:
    w=csv.writer(stream,delimiter='\t',lineterminator='\n');w.writerow(['id','simulated_neighbors','neighbors','simulated_observed_ratio','expected_rate','rate_sd','score','standard_error'])
    for i in range(len(graph)):w.writerow([i,int((graph[i]>=x.shape[0]).sum()),graph.shape[1],2.,.1,.02,repr(float(scores[i])),repr(float(errors[i]))])
(out/'provenance.json').write_text(json.dumps({'source_sha256':{Path(m.__file__).name:hashlib.sha256(Path(m.__file__).read_bytes()).hexdigest() for m in [module,helper]},'warnings':[str(w.message) for w in messages]},indent=2)+'\n',encoding='utf-8')
