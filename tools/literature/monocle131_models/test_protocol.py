#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual packaged CLI against saved original statistics and adversarial controls."""
from pathlib import Path
import argparse,copy,json,os,shutil,subprocess,traceback
ACTION='Required action: retain all process logs and failed controls. Provide a permitted Java21/25, the model adapter and exact adjacent JDistlib0.10.2, complete saved controls and NEW output. Missing/forbidden prerequisites are SKIP3; resolve FAIL2 contradictions without changing original references. Portable controls do not repeat original fitting, reload RDS or authenticate producers.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java',required=True,type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--models',required=True,type=Path);p.add_argument('--pearson',required=True,type=Path);p.add_argument('--request',required=True,type=Path);p.add_argument('--runtime-request',type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;(out/'requests').mkdir();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']};jar=a.jar.resolve()
  def run(label,args,expected=0,needle=None,artifact=jar):
   command=list(map(str,[a.java.resolve(),'-Xmx2g','-jar',artifact,*args]));r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected or (needle and needle.encode() not in r.stdout+r.stderr):raise ValueError('Packaged control differs: '+label)
   if b'Required action:' not in r.stdout+r.stderr:raise ValueError('Prominent recovery instruction missing: '+label)
   target=out/label
   if (target/'analysis-status.json').is_file():
    state=json.loads((target/'analysis-status.json').read_bytes())
    if state['result_admitted'] or state['exit_code']!=expected:raise ValueError('Audit/refusal admitted an original analysis: '+label)
   return row
  run('help',['--help'])
  original=[]
  for state in sorted(a.models.glob('*/analysis-status.json')):
   if not json.loads(state.read_bytes())['result_admitted']:continue
   for path in sorted(state.parent.glob('analyses/*/statistics.json')):
    label='original-'+state.parent.name+'-'+path.parent.name;run(label,['--audit-statistics',path,'--out',out/label]);original.append(path)
  if len(original)!=10:raise ValueError('Expected all ten successful complete saved original model analyses')
  run('pearson-original',['--audit-pearson',a.pearson.resolve(),'--out',out/'pearson-original'])
  if json.loads((out/'pearson-original/native-audit.json').read_bytes())['cases']!=16:raise ValueError('Incomplete original Pearson population')
  source=json.loads(original[0].read_bytes());pearson=json.loads(a.pearson.read_bytes());bad=[]
  def stat(label,mutate,needle):
   d=copy.deepcopy(source);mutate(d);bad.append((label,'--audit-statistics',d,needle))
  stat('wrong-statistic',lambda d:d['rows'][0].update(test_val='12345'),'statistic differs')
  stat('wrong-p',lambda d:d['rows'][0].update(p_value='0.45'),'tail probability differs')
  stat('false-underflow',lambda d:d['rows'][0].update(p_value='0'),'underflow differs')
  stat('wrong-q',lambda d:d['rows'][0].update(q_value='0.8'),'adjustment differs')
  stat('bh-substitution',lambda d:d.update(adjustment='BH'),'default-Holm')
  stat('wrong-distribution',lambda d:d['rows'][0].update(distribution='GAUSSIAN'),'distribution')
  stat('wrong-df',lambda d:d['rows'][0].update(distribution='T',residual_df='1'),'tail probability differs')
  stat('zero-se',lambda d:d['rows'][0].update(std_err='0'),'standard error')
  stat('missing-row',lambda d:d['rows'].pop(),'Incomplete term')
  stat('duplicate-row',lambda d:d['rows'].append(copy.deepcopy(d['rows'][0])),'Duplicate gene')
  stat('duplicate-gene',lambda d:d['genes'].append(d['genes'][0]),'Duplicate')
  stat('wrong-observations',lambda d:d['rows'][0].update(observations=119),'observation population')
  stat('unknown-stat-field',lambda d:d.update(unexpected=True),'Unknown field')
  def corr(label,mutate,needle):
   d=copy.deepcopy(pearson);mutate(d);bad.append((label,'--audit-pearson',d,needle))
  corr('empty-pearson',lambda d:d.update(results=[]),'Empty Pearson')
  corr('duplicate-pearson',lambda d:d['results'].append(copy.deepcopy(d['results'][0])),'Duplicate Pearson')
  corr('wrong-pair-count',lambda d:d['results'][0].update(pairs=6),'pair count')
  corr('wrong-positive',lambda d:d['results'][0].update(positive=False),'positive predicate')
  corr('wrong-correlation',lambda d:d['results'][0].update(coefficient='0.5'),'coefficient differs')
  corr('wrong-kind',lambda d:d['results'][2].update(kind='value'),'value kind')
  corr('wrong-na-token',lambda d:d['results'][2].update(coefficient='NaN'),'token differs')
  corr('infinite-input',lambda d:d['results'][0]['x'].__setitem__(0,'Inf'),'infinite arguments')
  corr('short-population',lambda d:d['results'][0].update(x=['1']*4,y=['1']*4),'at least five')
  corr('unknown-pearson-field',lambda d:d['results'][0].update(unexpected=True),'Unknown field')
  for label,mode,d,needle in bad:
   q=out/'requests'/(label+'.json');save(q,d);run(label,[mode,q,'--out',out/label],2,needle)
  request=json.loads(a.request.read_bytes());requests=[]
  def req(label,mutate,expected,needle,template=request):
   d=copy.deepcopy(template);d['out']=str(out/label);mutate(d);q=out/'requests'/(label+'.json');save(q,d);run(label,['--request',q],expected,needle);requests.append(label)
   if (out/label/'process').exists():raise ValueError('Refusal unexpectedly launched original process: '+label)
  req('execution-forbidden',lambda d:d.update(execution_policy='FORBID'),3,'execution FORBID')
  req('wrong-version',lambda d:d.update(version='1.3.3'),2,'no edition substitution')
  req('unknown-request',lambda d:d.update(unexpected=True),2,'Unknown field')
  req('wrong-family',lambda d:d['analyses'][0].update(family='binomial'),2,'supported single-component')
  req('executable-formula',lambda d:d['analyses'][0].update(terms=['system("id")']),2,'Undeclared model column')
  req('implicit-units',lambda d:d.pop('expression_units'),2,'Expected Nextflow JSON text')
  req('pooled-models',lambda d:d['analyses'].append(copy.deepcopy(d['analyses'][0])),2,'Unique portable model IDs')
  req('invalid-q',lambda d:d['analyses'][0]['selection'].update(maximum_q=0),2,'thresholds')
  req('factor-slope-selection',lambda d:d['analyses'][0]['selection'].update(term='Sample'),2,'numeric slope')
  runtime_controls=0
  if a.runtime_request:
   if os.name=='nt':raise ValueError('Pre-R input controls require actual Linux Java, not a simulated platform')
   runtime=json.loads(a.runtime_request.read_bytes());inputs=out/'bad-inputs';inputs.mkdir()
   if runtime['expression_format']!='TSV':raise ValueError('Pre-R refusal bank requires the complete TSV runtime-request fixture')
   original_expression=Path(runtime['expression']).read_text()
   req('missing-rscript',lambda d:d.update(rscript=str(out/'absent-Rscript')),3,'Missing permitted Rscript',runtime);runtime_controls+=1
   req('missing-library',lambda d:d.update(r_libraries=[str(out/'absent-library')]),3,'Missing declared R library',runtime);runtime_controls+=1
   def input_case(label,raw,fmt,needle,units=None):
    f=inputs/(label+'.data');f.write_text(raw,encoding='utf8',newline='\n')
    def edit(d):
     d.update(expression=str(f),expression_format=fmt)
     if units:d['expression_units']=units
    req(label,edit,2,needle,runtime)
   input_case('sparse-duplicate','%%MatrixMarket matrix coordinate real general\n8 120 2\n1 1 2\n1 1 3\n','MATRIX_MARKET','Duplicate Matrix Market')
   input_case('sparse-incomplete','%%MatrixMarket matrix coordinate real general\n8 120 2\n1 1 2\n','MATRIX_MARKET','Incomplete Matrix Market')
   input_case('sparse-zero-index','%%MatrixMarket matrix coordinate real general\n8 120 1\n0 1 2\n','MATRIX_MARKET','one-based')
   input_case('sparse-axis','%%MatrixMarket matrix coordinate real general\n7 120 1\n1 1 2\n','MATRIX_MARKET','dimensions differ')
   input_case('fractional-raw','%%MatrixMarket matrix coordinate real general\n8 120 1\n1 1 1.00000000000000000001\n','MATRIX_MARKET','Fractional raw-count token','raw-integer-counts')
   input_case('fractional-integer-format','%%MatrixMarket matrix coordinate integer general\n8 120 1\n1 1 1.0\n','MATRIX_MARKET','literal integer tokens')
   lines=original_expression.splitlines();first=lines[1].split('\t');first[1]='-1';lines[1]='\t'.join(first)
   input_case('negative-expression','\n'.join(lines)+'\n','TSV','Negative expression')
   runtime_controls+=7
   req('entry-capacity',lambda d:d.update(maximum_entries=1),2,'entry capacity',runtime);runtime_controls+=1
   req('input-byte-capacity',lambda d:d.update(maximum_input_bytes=1),2,'oversized Nextflow file',runtime);runtime_controls+=1
  isolated=out/'without-math';isolated.mkdir();shutil.copyfile(jar,isolated/jar.name)
  run('missing-math',['--audit-statistics',original[0],'--out',out/'missing-math'],3,'JDistlib',isolated/jar.name)
  shutil.copyfile(jar.parent/'jdistlib-all-0.10.2.jar',isolated/'jdistlib-all-0.10.2.jar')
  with (isolated/'jdistlib-all-0.10.2.jar').open('ab') as f:f.write(b'\n')
  run('wrong-math-integrity',['--audit-statistics',original[0],'--out',out/'wrong-math-integrity'],2,'binary integrity differs',isolated/jar.name)
  save(out/'COMPLETED.json',dict(status='PASS_PACKAGED_MONOCLE131_ORIGINAL_STATISTICS_PEARSON_AND_REFUSAL_CONTROLS',calls=calls,complete_original_model_analyses=len(original),original_pearson_cases=16,numeric_refusals=len(bad),request_refusals=len(requests),linux_pre_R_controls=runtime_controls,original_fitting_repeated=False,RDS_reloaded=False,producer_authenticated=False,new_tool_credit=0,whole_goal_complete=False));print('PASS_PACKAGED_MONOCLE131',len(calls),'controls; original fitting not repeated',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2
  if owned:save(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION))
  print('SKIP3' if code==3 else 'FAIL2',repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
