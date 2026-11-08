# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the entire unchanged original basic module and record full communication outputs plus CPython hash/norm controls. No pickle file is loaded."""
import argparse,hashlib,importlib.util,importlib.machinery,json,math,os,random,struct,sys,traceback,types
from pathlib import Path


def state(value):
    if isinstance(value,float) and not math.isfinite(value):return 'NaN' if math.isnan(value) else '+Inf' if value>0 else '-Inf'
    if isinstance(value,list):return [state(v) for v in value]
    if isinstance(value,tuple):return [state(v) for v in value]
    if isinstance(value,dict):return {k:state(v) for k,v in value.items()}
    return value
def save(path,value):path.write_text(json.dumps(state(value),ensure_ascii=True,allow_nan=False,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--overlay',required=True,type=Path);parser.add_argument('--source',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out==args.out and not out.exists();out.mkdir();assert sys.version_info[:3]==(3,11,17) and sys.hash_info.algorithm=='siphash13' and sys.hash_info.width==64 and sys.flags.hash_randomization==0 and sys.flags.ignore_environment==0 and sys.flags.safe_path and sys.flags.no_user_site;sys.path.insert(0,str(args.overlay));import numpy as np;import pandas as pd;import importlib.metadata as metadata
    assert np.__version__=='1.26.4' and pd.__version__=='2.2.3';source=args.source.resolve();source_pins={str(p):pin(p) for p in source.rglob('*') if p.is_file()};spec=importlib.util.spec_from_file_location('spyrrow.commu.basic',source/'spyrrow/commu/basic.py');original=importlib.util.module_from_spec(spec);sys.modules[spec.name]=original;spec.loader.exec_module(original);functions={name:(getattr(original,name),getattr(original,name).__code__) for name in ['get_point_pairs','interaction_activity','cal_cell_communication']};rng=random.Random(41771);cases=[]
    def add(name,genes,cells,expression,x,y,ligands,receptors,threshold,dtype='float64'):
        cases.append(dict(id=name,genes=genes,cells=cells,expression=expression,x=x,y=y,ligands=ligands,receptors=receptors,threshold=threshold,dtype=dtype))
    genes=['L1','L2','R1','R2'];cells=['C','A','B','D'];expression=[[1.,9.,4.,16.],[4.,1.,25.,0.],[9.,16.,1.,4.],[0.,4.,9.,1.]];ligands=['L1','L1','L2'];receptors=['R1','R2','R1'];xy=([0.,1.,3.,0.],[0.,0.,4.,.25])
    for name,threshold in [('directional-six-families',6.),('strict-five-boundary',5.),('nextafter-five-up',math.nextafter(5.,math.inf)),('nextafter-five-down',math.nextafter(5.,0.))]:add(name,genes,cells,expression,*xy,ligands,receptors,threshold)
    add('coincident-distinct-cells',genes,cells,expression,[0.,0.,1.,4.],[0.,0.,0.,0.],ligands,receptors,1.)
    add('duplicate-gene-first-column',['L1','R1','L1'],['b','a'],[[1.,4.,999.],[9.,16.,888.]],[0.,.5],[0.,0.],['L1'],['R1'],1.)
    add('duplicate-and-unmapped-database',genes,cells,expression,*xy,ligands+['L1','absent','L2'],receptors+['R1','R1','unknown'],6.)
    add('zip-truncated-ligands',genes,cells,expression,*xy,ligands+['L2','extra'],receptors,6.)
    add('zip-truncated-receptors',genes,cells,expression,*xy,ligands,receptors+['R2','extra'],6.)
    add('finite-product-overflow',['L','R'],['x','y'],[[1e300,1e300],[1e300,1e300]],[0.,.5],[0.,0.],['L'],['R'],1.)
    add('subnormal-product-underflow',['L','R'],['x','y'],[[1e-300,1e-300],[1e-300,1e-300]],[0.,.5],[0.,0.],['L'],['R'],1.)
    add('source-sequential-positive-sums',['L','R0','R1','R2','R3'],['x','y'],[[1e32,1.,1e-30,1.,1e-30],[1e32,1.,1e-30,1.,1e-30]],[0.,.5],[0.,0.],['L']*4,['R0','R1','R2','R3'],1.)
    add('extreme-coordinate-subtraction',['L','R'],['low','high','zero','near'],[[1.,4.]]*4,[-float.fromhex('0x1.fffffffffffffp1023'),float.fromhex('0x1.fffffffffffffp1023'),0.,1.],[0.]*4,['L'],['R'],float.fromhex('0x1.fffffffffffffp1023'))
    add('subnormal-coordinate-distance',genes,cells,expression,[0.,1e-320,2e-320,3e-320],[0.,0.,0.,0.],ligands,receptors,2e-320)
    unicode=['é','α','𝒜','😀'];add('unicode-native-hash-storage',unicode,['é-cell','α-cell','😀-cell'],[[1.,4.,9.,16.],[4.,9.,16.,25.],[9.,16.,25.,36.]],[0.,.5,1.],[0.,0.,0.],['é','α','𝒜','😀'],['α','😀','é','𝒜'],2.)
    for name,n,g,k,threshold in [('many-source-set-resizes',4,120,150,2.),('random-complete-family',32,70,125,2.),('larger-complete-family',140,50,150,2.)]:
        names=['G'+str(i) for i in range(g)];add(name,names,['cell-'+str(i) for i in range(n)],[[rng.random()*20 for j in range(g)] for i in range(n)],[rng.random()*10 for i in range(n)],[rng.random()*10 for i in range(n)],[names[i%g] for i in range(k)],[names[(i*17+3)%g] for i in range(k)],threshold)
    # Give the resize case known adjacent cells; the random cases retain every selected pair.
    cases[-3]['x']=[0.,.25,.5,.75];cases[-3]['y']=[0.]*4
    add('no-surviving-interactions',genes,cells,expression,*xy,['unknown'],['absent'],6.)
    f32=lambda value:struct.unpack('<f',struct.pack('<f',value))[0];add('source-float32-dataframe-values',genes,cells,[[f32(v+.1) for v in row] for row in expression],*xy,ligands,receptors,6.,dtype='float32')
    add('signed-zero-expression',['L','R'],['x','y'],[[-0.,1.],[1.,-0.]],[0.,.5],[0.,0.],['L'],['R'],1.)
    results=[]
    for c in cases:
        count=pd.DataFrame(c['expression'],index=c['cells'],columns=c['genes'],dtype=c['dtype']);frame=original.cal_cell_communication(count,pd.Series(c['x']),pd.Series(c['y']),[c['ligands'],c['receptors']],c['threshold']);pairs=original.get_point_pairs(list(zip(c['x'],c['y'])),c['cells'],c['threshold']);results.append(dict(id=c['id'],index=frame.index.tolist(),columns=frame.columns.tolist(),values=frame.to_numpy().tolist(),dtypes=[str(d) for d in frame.dtypes],pairs=pairs));print('PASS_ORIGINAL_COMPONENT',c['id'],frame.shape,flush=True)
    negative=[]
    for name,count,x,y,database,threshold in [('no-pairs',pd.DataFrame([[1.,2.],[3.,4.]],index=['a','b'],columns=['L','R']),[0.,10.],[0.,0.],[['L'],['R']],1.),('duplicate-cell-index',pd.DataFrame([[1.,2.],[3.,4.]],index=['a','a'],columns=['L','R']),[0.,.5],[0.,0.],[['L'],['R']],1.),('colon-gene-key',pd.DataFrame([[1.,2.],[3.,4.]],index=['a','b'],columns=['L:a','R']),[0.,.5],[0.,0.],[['L:a'],['R']],1.)]:
        try:original.cal_cell_communication(count,pd.Series(x),pd.Series(y),database,threshold)
        except Exception as error:negative.append(dict(id=name,type=type(error).__name__,message=str(error)))
        else:raise AssertionError('Expected original error: '+name)
    hashes=['','L','R','A\x00B','é','α','𝒜','😀']+['key-'+''.join(rng.choice('aZ019éα😀') for _ in range(rng.randrange(1,40))) for i in range(5000)];hash_results=[hash(value) for value in hashes];set_cases=[hashes[:n] for n in [0,1,4,5,6,19,100,5008]]+[['set-key-'+str(i) for i in range(85000)]];set_results=[list(set(values)) for values in set_cases];distance_inputs=[[0.,0.,3.,4.],[0.,0.,0.,0.],[0.,0.,5e-324,5e-324],[0.,0.,1e-320,2e-320],[math.inf,math.nan,0.,0.],[math.inf,0.,math.inf,0.]]
    for i in range(2000):
        values=[]
        for j in range(4):
            while True:
                value=struct.unpack('<d',rng.getrandbits(64).to_bytes(8,'little'))[0]
                if math.isfinite(value):break
            values.append(value)
        distance_inputs.append(values)
    distance_results=[math.dist(v[:2],v[2:]) for v in distance_inputs]
    for name,(function,code) in functions.items():assert getattr(original,name) is function and function.__code__ is code
    # Load the exact cn body through namespace parents, without the unrelated root-package imports.
    # Its unguarded path expression raises NameError before a pickle file can be opened.
    for name,path in [('spyrrow',source/'spyrrow'),('spyrrow.commu',source/'spyrrow/commu')]:
        spec=importlib.machinery.ModuleSpec(name,loader=None,is_package=True);spec.submodule_search_locations=[str(path)];sys.modules[name]=importlib.util.module_from_spec(spec)
    cn_spec=importlib.util.spec_from_file_location('spyrrow.commu.cn',source/'spyrrow/commu/cn.py');cn=importlib.util.module_from_spec(cn_spec)
    try:cn_spec.loader.exec_module(cn)
    except NameError as error:(out/'cn-import.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');assert "nam" in str(error);cn_error=dict(type=type(error).__name__,message=str(error),root_package_initialization_executed=False,component_namespace_parents=True,pickle_file_loaded=False)
    else:raise AssertionError('Published unguarded cn module unexpectedly imported')
    save(out/'cases.json',dict(cases=cases,hash_inputs=hashes,set_inputs=set_cases,distance_inputs=distance_inputs));save(out/'original-results.json',dict(cases=results,hashes=hash_results,sets=set_results,distances=distance_results,expected_original_errors=negative,cn_module_import_error=cn_error));assert all(pin(Path(n))==want for n,want in source_pins.items());save(out/'COMPLETED.json',dict(status='PASS_UNCHANGED_SPYRROW_COMMUNICATION_COMPONENT_REFERENCES_ONLY',cases=len(cases),complete_numeric_cells=sum(len(c['columns'])*len(c['index']) for c in results),source_members=source_pins,hash_seed=0,hash_algorithm=sys.hash_info.algorithm,hash_cases=len(hashes),set_groups=len(set_cases),largest_set_input=max(map(len,set_cases)),distance_cases=len(distance_inputs),original_errors=negative,cn_module_import_error=cn_error,functions_or_code_objects_patched=False,full_package_imported=False,pickle_file_loaded=False,python=sys.version,versions={n:metadata.version(n) for n in ['numpy','pandas','scipy','opencv-python-headless','shapely','networkx','matplotlib']},implementation_credit=False,whole_goal_complete=False));print('PASS_UNCHANGED_SPYRROW_COMPONENT_REFERENCES_ONLY',len(cases),flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original component controls')
    main()
