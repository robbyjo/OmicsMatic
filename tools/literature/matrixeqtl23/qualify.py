"""Run a packaged native MatrixEQTL profile against retained independent R controls.

All output paths must be new. Does not install software or grant catalog credit.
Original R outputs and article source remain private. Missing prerequisites skip.
"""
import argparse,copy,hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
from compare import compare

def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):n+=len(b);h.update(b)
 assert n==Path(p).stat().st_size
 return dict(bytes=n,sha256=h.hexdigest())
def write(p,value):
 with Path(p).open('x',encoding='utf-8',newline='\n')as f:json.dump(value,f,indent=2,allow_nan=False);f.write('\n')
def run(args):
 root=args.root.resolve();evidence=args.evidence.resolve();out=args.out.resolve();producer=args.producer.resolve()
 assert out.is_relative_to(evidence)and not out.exists()and producer.is_file();out.mkdir();before=pin(producer)
 controls=[(evidence/'development-Windows-2',name)for name in ['all','cis-trans','cis-only','gls','no-covariates','female-stratum','male-stratum']]+[(evidence/'additional-controls-Windows-1',name)for name in ['tiled','underflow']]
 records=[]
 def execute(arguments,case,label,expected=0):
  cmd=[str(args.java),'-Xmx1024m','-jar',str(producer),'matrixeqtl-native',*arguments]
  write(case/(label+'-command.json'),dict(argv=cmd,producer=before))
  with(case/(label+'.log')).open('xb')as log:result=subprocess.run(cmd,cwd=root,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=300)
  if result.returncode!=expected:raise ValueError((case.name,label,result.returncode,(case/(label+'.log')).read_text(errors='replace')[-1800:]))
 for parent,name in controls:
  source=parent/name;case=out/name;case.mkdir();members={}
  for path in source.iterdir():
   if path.is_file()and(path.suffix=='.tsv'or path.name in ['R-session.txt','original.log']):
    shutil.copyfile(path,case/path.name);members[path.name]=pin(path);assert members[path.name]==pin(case/path.name)
  parameters=read(source/'request.json');parameters['out']='capture';write(case/'request.json',parameters)
  execute(['--request',str(case/'request.json')],case,'native')
  execute(['--verify',str(case/'capture'),'--out',str(case/'replay')],case,'replay')
  comparison=compare(case)
  assert all(pin(source/name)==identity and pin(case/name)==identity for name,identity in members.items())
  write(case/'COMPLETED.json',dict(comparison=comparison,original_control_members=members,source_control_path=str(source),producer=before))
  records.append(comparison);print('PASS_PACKAGED_ORIGINAL_COMPARISON',name,comparison['counts'],flush=True)
 assert float(read(out/'underflow/capture/results.json')['all']['eqtls'][0]['pvalue'])==sys.float_info.min
 negative=out/'negative';negative.mkdir();negatives=[]
 source=out/'all';template=read(source/'request.json')
 for name,expected in [('forbidden',3),('missing-input',3),('capacity',2),('reordered-samples',2),('infinite-input',2),('interaction',2),('duplicate-id',2),('bad-covariance',2),('saved-input-tamper',2),('saved-population-tamper',2)]:
  case=negative/name;case.mkdir();p=copy.deepcopy(template)
  for key in ['snps','genes','covariates','snp_positions','gene_positions','error_covariance']:
   if p[key]is not None:shutil.copyfile(source/p[key],case/p[key])
  if name.startswith('saved-'):
   shutil.copytree(source/'capture',case/'capture')
   if name=='saved-input-tamper':(case/'capture/inputs/genes.tsv').write_text('changed\n')
   else:(case/'capture/UNEXPECTED').write_text('unique\n')
   execute(['--verify',str(case/'capture'),'--out',str(case/'refusal')],case,'refusal',expected);status=read(case/'refusal/analysis-status.json')
  else:
   if name=='forbidden':p['execution_policy']='FORBID'
   elif name=='missing-input':p['genes']='missing.tsv'
   elif name=='capacity':p['max_pairs']=1
   elif name=='interaction':p['model']='modelLINEAR_CROSS'
   elif name=='bad-covariance':p['error_covariance']='snps.tsv'
   else:
    path=case/'genes.tsv';text=path.read_text()
    if name=='reordered-samples':text=text.replace('id\tS0\tS1\t','id\tS1\tS0\t',1)
    elif name=='infinite-input':rows=text.splitlines();r=rows[1].split('\t');r[1]='Inf';rows[1]='\t'.join(r);text='\n'.join(rows)+'\n'
    elif name=='duplicate-id':text=text.replace('\ng1\t','\ng0\t',1)
    path.write_text(text)
   p['out']='refusal';write(case/'request.json',p);execute(['--request',str(case/'request.json')],case,'refusal',expected);status=read(case/'refusal/analysis-status.json')
  assert status['exit_code']==expected and not status['result_admitted'];negatives.append(dict(case=name,exit_code=expected,result_admitted=False))
  print('PASS_PACKAGED_REFUSAL',name,expected,flush=True)
 assert pin(producer)==before
 result=dict(status='PASS_PACKAGED_NATIVE_MATRIXEQTL23_ORIGINAL_CONTROLS',producer=before,java=str(args.java),platform=sys.platform,cases=records,refusals=negatives,source_version='2.3',whole_package_qualified=False,exact_author_recipe_qualified=False,new_family_credit=0,whole_goal_complete=False)
 write(out/'COMPLETED.json',result);return result
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,required=True);parser.add_argument('--evidence',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--producer',type=Path,required=True);parser.add_argument('--java',type=Path,required=True);args=parser.parse_args()
 try:run(args)
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
  print(('SKIP3: Missing permitted prerequisite; dependent qualification skipped. 'if code==3 else'FAIL2: No complete qualification admitted. ')+str(error)+'. Retain this attempt and retry a NEW path after resolving the cause.',file=sys.stderr);raise SystemExit(code)
