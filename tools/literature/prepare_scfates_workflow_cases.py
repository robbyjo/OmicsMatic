# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export exact original synthetic controls to strict public CSV requests, without running analysis."""
import argparse,copy,csv,json,struct,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--runtime',type=Path,default=ROOT/'build/literature-review/scfates-runtime/workflow-setup-3/COMPLETED.json');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);reference=ROOT/'build/literature-review/scfates-runtime/original-reference-2/original';complete=json.loads((reference/'COMPLETED.json').read_bytes());assert complete['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_CPU_ARITHMETIC_AND_WORKFLOWS_ONLY';(out/'inputs').mkdir();(out/'requests').mkdir();(out/'runs').mkdir();source_pins={};specifications={};source_cases={}
    def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
    def array(home,raw):
        if raw['kind']=='dataframe':
            columns=[array(home,v) for v in raw['column_data']];assert all(len(c)==len(raw['index']) for c in columns);return [[column[i] for column in columns] for i in range(len(raw['index']))]
        assert raw['kind']=='binary-numeric-array' and raw['dtype']=='<f8';path=home/raw['path'];assert pin(path)=={k:raw[k] for k in ['bytes','sha256']};source_pins[path.relative_to(ROOT).as_posix()]=pin(path);values=struct.unpack('<'+'d'*(raw['bytes']//8),path.read_bytes());shape=raw['shape']
        if len(shape)==1:return list(values)
        assert len(shape)==2;return [list(values[i*shape[1]:(i+1)*shape[1]]) for i in range(shape[0])]
    def write_table(name,role,ids,columns,values):
        file=out/'inputs'/(name+'.'+role+'.csv')
        with file.open('x',encoding='utf8',newline='') as stream:
            writer=csv.writer(stream,quoting=csv.QUOTE_ALL,lineterminator='\r\n');writer.writerow(['node_id' if role=='initialization' else 'cell_id',*columns]);writer.writerows([[identifier,*[format(float(v),'.17g') for v in row]] for identifier,row in zip(ids,values,strict=True)])
        return dict(path=linux(file),format='csv',source='Exact retained original synthetic scFates software control; no paper or biological reproduction')
    for case in complete['workflows']:
        name=case['id'];home=reference/name;path=home/'input.json';raw=json.loads(path.read_bytes());source_pins[path.relative_to(ROOT).as_posix()]=pin(path);values=array(home,raw['coordinates']);columns=['coordinate_'+str(i) for i in range(len(values[0]))];cells=raw['cells'];spec=dict(id=name,method='tree-root-pseudotime',input=write_table(name,'coordinates',cells,columns,values),global_seed=case['global_seed'],tree=dict(nodes=case['nodes'],sigma=case['sigma'],**{'lambda':case['lam']},metric='euclidean',nsteps=case['nsteps'],err_cut=case['err_cut'],seed=case['tree_seed']),root=dict(mode='obs' if case['automatic_root'] else 'node',tips_only=case['automatic_root'],min_val=case['automatic_root']),pseudotime=dict(n_map=case['n_map'],n_jobs=case['n_jobs'],seed=case['pseudotime_seed']),policies={key:'retain-with-warning' for key in ['nonconvergence','nonfinite_diagnostics','zero_assignment_rows','removed_nodes','missing_milestones']})
        if raw['weights'] is not None:spec['weights']=write_table(name,'weights',cells,columns,array(home,raw['weights']))
        if raw['initialization'] is not None:
            init=array(home,raw['initialization']);spec['initialization']=write_table(name,'initialization',['initial-node-'+str(i) for i in range(len(init))],columns,init)
        if case['automatic_root']:
            signal=array(home,raw['root_signal']['data']);spec['root']['values']=write_table(name,'root-values',cells,['root_signal'],[[v] for v in signal])
        else:spec['root']['node']=0
        specifications[name]=spec;source_cases[name]=dict(original_status=case['status'],expected_error=case.get('error_type'),reference=home.relative_to(ROOT).as_posix())
    positive=[specifications[v['id']] for v in complete['workflows'] if v['status']=='complete-original-workflow-only'];assert len(positive)==9
    def request(name,analyses):
        value=dict(schema_version=1,mode='RUN',edition='scfates-1.2.5_simpleppt-1.1.7',runtime=linux(a.runtime),out=linux(out/'runs'/name),analyses=analyses,timeout_seconds=1800,maximum_output_bytes=4294967296,maximum_audit_operations=2000000000,maximum_cells=5000,maximum_components=16,maximum_nodes=4096,maximum_matrix_cells=25000000,python_hash_seed=0);save(out/'requests'/(name+'.json'),value)
    request('positive-batch',positive);request('late-source-error',[*positive[:2],specifications['paper-200-node-settings']]);request('late-sampling-error',[positive[0],specifications['source-too-many-nodes']]);invalid=copy.deepcopy(positive[0]);invalid['tree']['nsteps']=0;request('invalid-zero-iterations',[invalid]);policy=None
    for spec in positive:
        local=json.loads((reference/spec['id']/'observed-final-ppt-step.json').read_bytes())['locals'];error=local['err']
        if isinstance(error,(int,float)) and error>=local['err_cut']:policy=copy.deepcopy(spec);break
    assert policy is not None;policy['policies']['nonconvergence']='fail';request('nonconvergence-policy',[policy]);quoted=copy.deepcopy(positive[0]);raw=json.loads((reference/positive[0]['id']/'input.json').read_bytes());cells=raw['cells'].copy();special=['NA','null','  spaced cell  ','cell,comma','cell"quote','cell\tTAB','cell\nLINE','cell\rRETURN','cell \U0001f9ec','e\u0301','\u00e9'];cells[:len(special)]=special;values=array(reference/positive[0]['id'],raw['coordinates']);quoted['id']='quoted-identifiers';quoted['input']=write_table(quoted['id'],'coordinates',cells,['axis,one','axis\ttwo','axis\nthree'],values);request('quoted-identifiers',[quoted]);files={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()};assert all(pin(ROOT/n)==v for n,v in source_pins.items());save(out/'COMPLETED.json',dict(status='PASS_EXACT_ORIGINAL_SCFATES_PUBLIC_INPUT_CONTROLS_PREPARED_ONLY',files=files,source_pins=source_pins,cases=source_cases,positive_cases=9,requests=6,analysis_executed=False,implementation_credit=False,whole_goal_complete=False));print('Prepared9 original positive cases, late source errors, explicit failure policy and quoted/Unicode/control-character identity controls')

if __name__=='__main__':raise SystemExit(main())
