#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete saved original graphs and exercise the actual packaged pipeline/refusal interface."""
from pathlib import Path
import argparse,copy,json,os,subprocess,traceback
ACTION='Required action: retain all diagnostics; provide permitted Java21/25, the adapter, complete saved controls and NEW output. Missing/forbidden prerequisites are SKIP3; correct FAIL2 source/input/numeric contradictions without changing original controls. These packaged graph/refusal tests do not repeat original fitting, reload RDS or authenticate producers.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--request',required=True,type=Path);p.add_argument('--runtime-request',type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;(out/'requests').mkdir();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,args,expected=0,needle=None):
   command=list(map(str,[a.java.resolve(),'-Xmx2g','-jar',a.jar.resolve(),*args]));r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected or (needle and needle.encode() not in r.stdout+r.stderr):raise ValueError('Packaged control differs: '+label)
   if b'Required action:' not in r.stdout+r.stderr:raise ValueError('Prominent recovery guidance missing: '+label)
   target=out/label
   if (target/'analysis-status.json').exists():
    s=json.loads((target/'analysis-status.json').read_bytes())
    if s['result_admitted'] or s['pipeline_admitted'] or s['exit_code']!=expected:raise ValueError('Portable/refusal control admitted an original pipeline: '+label)
   if (target/'process').exists():raise ValueError('Portable/refusal control launched an original process: '+label)
  run('help',['--help']);graphs=[a.reference/'original/prepared-graph.json']
  for state in sorted((a.reference/'pipelines').glob('*/analysis-status.json')):
   if json.loads(state.read_bytes())['result_admitted']:graphs.append(state.parent/'prepared-graph.json')
  if len(graphs)!=7:raise ValueError('Require all seven complete saved original graph controls, including disconnected Inf values')
  for i,path in enumerate(graphs):run('original-graph-'+str(i),['--audit-graph',path.resolve(),'--out',out/('original-graph-'+str(i))])
  original=json.loads(graphs[0].read_bytes());bad=[]
  def graph(label,mutate,needle='FAIL2'):
   d=copy.deepcopy(original);mutate(d);bad.append((label,d,needle))
  graph('wrong-cell-distance',lambda d:d['expected_cell_pseudotime'].__setitem__(0,'99999'),'pseudotime differs')
  graph('wrong-graph-distance',lambda d:d['expected_graph_pseudotime'].__setitem__(0,'99999'),'pseudotime differs')
  graph('false-infinity',lambda d:d['expected_cell_pseudotime'].__setitem__(0,'Inf'),'infinity differs')
  graph('missing-value',lambda d:d['expected_cell_pseudotime'].__setitem__(0,'NA'),'Invalid original pseudotime token')
  graph('missing-cell-result',lambda d:d['expected_cell_pseudotime'].pop(),'population differs')
  graph('missing-graph-result',lambda d:d['expected_graph_pseudotime'].pop(),'population differs')
  graph('wrong-root-index',lambda d:d['expected_root_indices'].__setitem__(0,479),'differs')
  graph('unknown-root',lambda d:d.update(roots=['UNKNOWN_PRINCIPAL']))
  graph('duplicate-cell',lambda d:d['cell_ids'].__setitem__(1,d['cell_ids'][0]))
  graph('negative-edge',lambda d:d['edges'][0].update(weight=-1),'Negative projected-graph weight')
  graph('wrong-edge-mode',lambda d:d.update(edge_weight_mode='unweighted'),'edge fields differ')
  graph('unknown-graph-field',lambda d:d.update(unexpected=True),'Unknown field')
  for label,d,needle in bad:
   path=out/'requests'/(label+'.json');save(path,d);run(label,['--audit-graph',path,'--out',out/label],2,needle)
  request=json.loads(a.request.read_bytes());refusals=[]
  def req(label,edit,expected=2,needle='FAIL2',template=request):
   d=copy.deepcopy(template);d['out']=str(out/label);edit(d);path=out/'requests'/(label+'.json');save(path,d);run(label,['--request',path],expected,needle);refusals.append(label)
  req('execution-forbidden',lambda d:d.update(execution_policy='FORBID'),3,'execution FORBID')
  req('wrong-monocle-version',lambda d:d.update(version='1.3.3'),2,'no edition substitution')
  req('wrong-batchelor-version',lambda d:d.update(batchelor_version='1.18.0'),2,'no edition substitution')
  req('unknown-request-field',lambda d:d.update(unexpected=True),2,'Unknown field')
  req('two-root-kinds',lambda d:d.update(root_pr_nodes=['Y_1']),2,'exactly one explicit')
  req('missing-roots',lambda d:d.pop('root_cells'),2,'exactly one explicit')
  req('duplicate-roots',lambda d:d.update(root_cells=['C001','C001']),2,'Duplicate')
  req('roots-on-learn',lambda d:d.update(mode='LEARN'),2,'LEARN accepts no roots')
  req('implicit-units',lambda d:d.pop('expression_units'),2,'Expected Nextflow JSON text')
  req('numeric-batch',lambda d:d['metadata_types'].update(Batch=dict(type='numeric')),2,'declared factor')
  req('executable-residual',lambda d:d['alignment'].update(residual_terms=['system("id")']),2,'Undeclared residual')
  req('invalid-normalization',lambda d:d['preprocessing'].update(norm_method='IMPUTE'),2,'original normalization')
  req('invalid-clustering',lambda d:d['clustering'].update(method='kmeans'),2,'leiden or louvain')
  req('invalid-graph-fingerprint',lambda d:d.update(expected_principal_graph_sha256='not-a-hash'),2,'SHA256')
  linux_controls=0
  if a.runtime_request:
   if os.name=='nt':raise ValueError('Pre-R input controls require actual Linux Java')
   runtime=json.loads(a.runtime_request.read_bytes());inputs=out/'bad-inputs';inputs.mkdir()
   req('missing-rscript',lambda d:d.update(rscript=str(out/'absent-Rscript')),3,'Missing permitted Rscript',runtime);linux_controls+=1
   req('missing-library',lambda d:d.update(r_libraries=[str(out/'absent-library')]),3,'Missing declared R library',runtime);linux_controls+=1
   for label,text,needle in [
    ('sparse-duplicate','%%MatrixMarket matrix coordinate integer general\n64 480 2\n1 1 2\n1 1 3\n','Duplicate Matrix Market'),
    ('sparse-incomplete','%%MatrixMarket matrix coordinate integer general\n64 480 2\n1 1 2\n','Incomplete Matrix Market'),
    ('sparse-axis','%%MatrixMarket matrix coordinate integer general\n63 480 1\n1 1 2\n','dimensions differ'),
    ('fractional-raw','%%MatrixMarket matrix coordinate real general\n64 480 1\n1 1 1.00000000000000001\n','Fractional raw-count token')]:
    path=inputs/(label+'.mtx');path.write_text(text,encoding='ascii',newline='\n');req(label,lambda d,path=path:d.update(expression=str(path),expression_format='MATRIX_MARKET'),2,needle,runtime);linux_controls+=1
  save(out/'COMPLETED.json',dict(status='PASS_PACKAGED_MONOCLE137_COMPLETE_ORIGINAL_GRAPHS_AND_REFUSAL_CONTROLS',calls=calls,complete_original_graphs=len(graphs),numeric_identity_refusals=len(bad),request_refusals=len(refusals),linux_pre_R_controls=linux_controls,original_fitting_repeated=False,RDS_reloaded=False,producer_authenticated=False,new_tool_credit=0,whole_goal_complete=False));print('PASS_PACKAGED_MONOCLE137',len(calls),'controls; no original fitting repeated',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION))
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
