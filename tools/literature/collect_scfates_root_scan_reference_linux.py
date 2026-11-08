# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual compiled SciPy1.11.4 positive-tree scans over unchanged complete bound runtimes."""
import argparse,json,os,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();assert out==out.resolve() and not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_SCFATES_ROOT_SCAN_CONTROLS',implementation_credit=False,whole_goal_complete=False,cases=[]);before={};roots={}
    try:
        assert sys.platform=='linux' and sys.version_info[:3]==(3,11,17) and sys.flags.safe_path and sys.flags.no_user_site and sys.flags.dont_write_bytecode and not sys.flags.ignore_environment and not sys.flags.optimize and os.environ['PYTHONHASHSEED']=='0';binding=json.loads(a.runtime.read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_SCFATES_WORKFLOW_RUNTIME_ONLY' and binding['runtime_trees_unchanged'] and binding['shared_inputs_unchanged'] and not binding['namespace_patched'];assert sys.executable==binding['runtime_python'];roots={'base':Path(binding['base_environment']),'runtime':Path(binding['runtime_environment'])}
        for name,root in roots.items():
            before[name]=inventory(root,progress=out/(name+'-before.progress.json'));save(out/(name+'-before.json'),before[name]);assert before[name]==json.loads((a.runtime.parent/(name+'-before.json')).read_bytes())
        sys.path.insert(0,str(roots['runtime']/'dependencies'));import numpy as np;import scipy;from scipy.sparse import csr_matrix;from scipy.sparse.csgraph import shortest_path;assert scipy.__version__=='1.11.4' and np.__version__=='1.26.4';assert Path(scipy.__file__).resolve().is_relative_to(roots['runtime']/'dependencies');rng=np.random.default_rng(20261108);specs=[]
        specs.append(('asymmetric-line',[[0,10,0],[1,0,20],[0,2,0]]));specs.append(('asymmetric-fork',[[0,9,0,0],[1,0,.125,4],[0,8,0,0],[0,.5,0,0]]));small=3.5514510979981937e-4;large=3.5514510983900607e-4;specs.append(('observed-directional-roundoff',[[0,large],[small,0]]))
        for k in [2,3,4,7,16,31,64]:
            for replicate in range(4):
                weights=np.zeros((k,k))
                for j in range(1,k):parent=int(rng.integers(j));weights[parent,j]=10**rng.uniform(-8,8);weights[j,parent]=10**rng.uniform(-8,8)
                specs.append(('tree-'+str(k)+'-'+str(replicate),weights))
        numeric=0
        for name,weights in specs:
            weights=np.asarray(weights,dtype=np.float64);k=len(weights);dist=shortest_path(csr_matrix(weights),directed=False,indices=np.arange(k));expected=np.empty_like(dist)
            for root in range(k):
                values=np.full(k,np.nan);values[root]=0;queue=[root]
                for v in queue:
                    for j in range(k):
                        if weights[v,j]>0 and np.isnan(values[j]):values[j]=values[v]+min(weights[v,j],weights[j,v]);queue.append(j)
                assert len(queue)==k;expected[root]=values
            assert np.array_equal(dist.view(np.uint64),expected.view(np.uint64)),name+' original compiled tree scan differs';numeric+=dist.size
            if name=='asymmetric-line':assert dist[0].tolist()==[0.,1.,3.]
            state['cases'].append(dict(id=name,weights=weights.tolist(),distance_bits=[[format(int(v),'016x') for v in row] for row in dist.view(np.uint64)]))
        state.update(status='PASS_31_COMPILED_SCIPY_POSITIVE_TREE_SCANS_ONLY',numeric_distance_states=numeric,source_pins={str(Path(__file__).resolve()):pin(Path(__file__).resolve()),str(a.runtime):pin(a.runtime)},namespace_patched=False,original_graphs_modified=False,scope='Positive connected trees only; preserve distinct directional values. No general asymmetric graph claim.')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        unchanged=True
        for name,tree in before.items():after=inventory(roots[name],progress=out/(name+'-after.progress.json'));save(out/(name+'-after.json'),after);unchanged &= tree==after
        state['all_runtime_trees_unchanged']=unchanged
        if not unchanged:state.update(status='FAIL2_ORIGINAL_ROOT_SCAN_RUNTIME_CHANGED',error='Complete runtime changed; no reference family admitted')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original root-scan checks')
    raise SystemExit(main())
