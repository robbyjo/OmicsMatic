#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual packaged CLI on full original graphs and specific contradictions; no R fitting."""
from pathlib import Path
import argparse,copy,json,os,subprocess,traceback
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);java=a.java_bin/('java.exe' if os.name=='nt' else 'java');calls=[]
 try:
  def call(label,args,expected,needle=None):
   command=list(map(str,[java,'-Xmx2g','-jar',a.jar.resolve(),*args]));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row)
   if r.returncode!=expected or needle is not None and needle.encode() not in r.stdout+r.stderr:raise ValueError('Specific packaged control failed: '+label)
   save(out/(label+'.process.json'),row)
  cases=sorted((a.reference/'cases').iterdir());assert len(cases)==6
  for case in cases:call('original-'+case.name,['--audit-graph',case/'prepared-graph.json','--out',out/('original-'+case.name)],0,'PASS_NATIVE_SUPPLIED_GRAPH_ORDERING_COMPARISON_ONLY')
  original=json.loads((cases[0]/'prepared-graph.json').read_bytes());mutations=[]
  def mutation(label,change,cause):
   g=copy.deepcopy(original);change(g);mutations.append((label,g,cause))
  mutation('wrong-cell-distance',lambda g:g['expected_cell_pseudotime'].__setitem__(0,str(float(g['expected_cell_pseudotime'][0])+1)),'finite pseudotime differs')
  mutation('wrong-graph-distance',lambda g:g['expected_graph_pseudotime'].__setitem__(0,str(float(g['expected_graph_pseudotime'][0])+1)),'finite pseudotime differs')
  mutation('wrong-root-index',lambda g:g['expected_root_indices'].__setitem__(0,(g['expected_root_indices'][0]+1)%len(g['cell_ids'])),'root index differs')
  mutation('negative-edge',lambda g:g['edges'][0].__setitem__('weight',-1),'Negative graph weight')
  mutation('changed-positive-edge',lambda g:g['edges'][0].__setitem__('weight',g['edges'][0]['weight']*2+1),'finite pseudotime differs')
  mutation('nan-coordinate',lambda g:g['cell_coordinates'][0].__setitem__(0,'NaN'),'Require numeric JSON value')
  mutation('unknown-principal-root',lambda g:g.__setitem__('roots',['NOT_A_PRINCIPAL_POINT']),'root principal point is absent')
  mutation('incomplete-cell-reference',lambda g:g['expected_cell_pseudotime'].pop(),'population differs')
  mutation('nan-original-distance',lambda g:g['expected_cell_pseudotime'].__setitem__(0,'NaN'),'NA/NaN pseudotime')
  mutation('weighted-edge-in-unit-mode',lambda g:g.__setitem__('edge_weight_mode','unweighted'),'Unweighted edge must not contain weights')
  for label,g,needle in mutations:
   path=out/(label+'.json');save(path,g);call(label,['--audit-graph',path,'--out',out/label],2,needle);state=json.loads((out/label/'analysis-status.json').read_bytes());assert not state['graph_admitted'] and not state['result_admitted']
  request=dict(schema_version=1,mode='RUN',version='1.3.3',count_units='raw-integer-counts',embedding_slot='UMAP',counts=str(a.reference.resolve()/'counts.tsv'),cells=str(a.reference.resolve()/'cells.tsv'),genes=str(a.reference.resolve()/'genes.tsv'),out=str(out/'unused'),rscript=str(out/'absent-Rscript'),r_libraries=[str(out/'absent-library')],source_root=str(out/'absent-source'),execution_policy='FORBID',cluster_levels=['stem','upper','lower'],partition_levels=['1'],seed=173,use_partition=False,close_loop=False,ncenter=130,minimal_branch_len=15,root_pr_nodes=['Y_1'],require_terminal_roots=True)
  controls=[('forbidden',{},3,'SKIP3'),('runtime-unavailable',dict(execution_policy='ALLOW'),3,'SKIP3'),('unsupported-partitions',dict(partition_levels=['all']),2,'partition levels'),('unknown-code-field',dict(r_expression='stop(1)'),2,'Unknown field'),('wrong-expression-units',dict(count_units='protein-activity'),2,'raw-integer-counts'),('wrong-edition',dict(version='1.4.27'),2,'no version substitution'),('both-root-types',dict(root_cells=['C0001']),2,'exactly one'),('bad-boolean',dict(close_loop='FALSE'),2,'JSON boolean')]
  for label,changes,expected,needle in controls:
   r=copy.deepcopy(request);r.update(changes);r['out']=str(out/label);path=out/(label+'.request.json');save(path,r);call(label,['--request',path],expected,needle);assert not (out/label/'process').exists()
   if expected==3:
    state=json.loads((out/label/'analysis-status.json').read_bytes());assert not state['result_admitted'] and not state['graph_admitted']
  result=dict(status='PASS_PACKAGED_MONOCLE133_GRAPH_SIX_ORIGINAL_TEN_NUMERIC_IDENTITY_REFUSALS_EIGHT_SCHEMA_SKIP_CONTROLS',calls=calls,original_graphs=6,specific_graph_refusals=10,schema_skip_controls=8,original_fitting_repeated=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  save(out/'FAILURE.json',dict(cause=repr(e),traceback=traceback.format_exc(),calls=calls));print('FAIL2_PACKAGED_MONOCLE133_GRAPH_CONTROLS',repr(e),flush=True);return 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Do not disable complete control assertions')
 raise SystemExit(main())
