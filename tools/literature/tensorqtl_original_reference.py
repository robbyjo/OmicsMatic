"""Independent direct-original tensorQTL calls on declared synthetic TSV fixtures.

Does not import project worker code, change original namespaces, or load saved
pickle inputs. Original source, runtime and raw returned objects stay private.
This is a test harness, not a recovered author pipeline or production adapter.
"""
import argparse,json,os,sys,hashlib
from pathlib import Path

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--request',required=True);parser.add_argument('--out',required=True);args=parser.parse_args()
 request_path=Path(args.request);r=json.loads(request_path.read_bytes());out=Path(args.out);out.mkdir();captured={};(out/'inputs').mkdir()
 def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
 for key in['genotypes','variants','phenotypes','phenotype_positions','covariates','paired_covariates','groups','interaction','cis_results','trans_filter_positions']:
  if key in r:
   path=Path(r[key]);before=sha(path);target=out/'inputs'/key;target.write_bytes(path.read_bytes());assert before==sha(path)==sha(target);captured[key]=dict(path=str(path),sha256=before);r[key]=str(target)
 source_files={str(p.relative_to(r['source_directory'])):sha(p)for p in Path(r['source_directory']).rglob('*')if p.is_file()and'__pycache__'not in p.parts}
 (out/'INTAKE.json').write_text(json.dumps(dict(inputs=captured,original_source=source_files),indent=2)+'\n')
 sys.path.insert(0,r['source_directory'])
 import numpy as np,pandas as pd,torch,tensorqtl
 from tensorqtl import cis,trans
 old=tensorqtl.__version__=='1.0.5';op=r['operation']
 if op=='CIS_SUSIE':from tensorqtl import susie
 from rpy2 import robjects
 torch.set_num_threads(1);np.random.seed(r['seed']);torch.manual_seed(r['seed']);robjects.r['set.seed'](r['seed'])
 if r['device']=='CUDA':
  assert torch.cuda.is_available();torch.cuda.set_device(r.get('cuda_device',0));torch.cuda.manual_seed(r['seed'])
 else:assert not torch.cuda.is_available()
 def matrix(name):
  frame=pd.read_csv(r[name],sep='\t',index_col=0,keep_default_na=False,dtype={dict(genotypes='variant_id',phenotypes='phenotype_id',covariates='sample_id',paired_covariates='phenotype_id',interaction='sample_id')[name]:str})
  frame.index=frame.index.astype(str)
  for col in frame:frame[col]=pd.to_numeric(frame[col],errors='raise')
  return frame
 def positions(name,variant=False,center=False):
  frame=pd.read_csv(r[name],sep='\t',index_col=0,keep_default_na=False,dtype={'chr':str,'variant_id'if variant else'phenotype_id':str});frame.index=frame.index.astype(str)
  for col in frame.columns:
   if col!='chr':frame[col]=pd.to_numeric(frame[col],errors='raise').astype(np.float64 if center else np.int64)
  return frame.rename(columns={'chr':'chrom'}if variant else{'pos':'tss'}if old else{})
 result={};groups=None;paired=None;interaction=None
 if op!='QVALUES':
  g=matrix('genotypes');p=matrix('phenotypes');cov=matrix('covariates')if'covariates'in r else None
  call=dict(genotype_df=g,phenotype_df=p,covariates_df=cov,maf_threshold=r.get('maf_threshold',.05 if op=='TRANS_NOMINAL'else 0),verbose=True)
  if op.startswith('CIS_'):
   v=positions('variants',True);pos=positions('phenotype_positions');call.update(variant_df=v,phenotype_pos_df=pos,window=r.get('window',1000000))
   if'groups'in r:groups=pd.read_csv(r['groups'],sep='\t',index_col=0,dtype=str,keep_default_na=False).iloc[:,0]
   if op!='CIS_SUSIE':call['group_s']=groups
  if'interaction'in r:interaction=matrix('interaction')
  if'paired_covariates'in r:paired=matrix('paired_covariates')
 if'cis_results'in r:
  table=pd.read_csv(r['cis_results'],sep='\t',index_col=0,keep_default_na=False,dtype={x:str for x in['phenotype_id','variant_id','group_id','chrom','chr']});result['supplied_cis_results']=table.copy(deep=True)
 if op=='CIS_NOMINAL':
  original=out/'original';original.mkdir();call.update(prefix='analysis',output_dir=str(original),write_top=True,write_stats=True)
  if interaction is not None:call['interaction_s'if old else'interaction_df']=interaction.iloc[:,0]if old else interaction;call.update(maf_threshold_interaction=r.get('maf_threshold_interaction',.05),run_eigenmt=r.get('run_eigenmt',False))
  if paired is not None:call['paired_covariate_df']=paired
  result['original_return']=cis.map_nominal(**call)
  for path in sorted(original.glob('*.parquet')):result[path.name]=pd.read_parquet(path)
  for path in sorted(original.glob('*.txt.gz')):result[path.name]=pd.read_csv(path,sep='\t',keep_default_na=False)
 elif op in['CIS_PERMUTE','CIS_DISCOVERY']:
  call.update(nperm=r.get('nperm',10000),seed=r['seed'],beta_approx=r.get('beta_approx',True))
  if not old:call['random_tiebreak']=r.get('random_tiebreak',False)
  if paired is not None:call['paired_covariate_df']=paired
  table=cis.map_cis(**call);result['original_cis_permutations']=table.copy(deep=True)
  if op=='CIS_DISCOVERY'or r.get('qvalues',False):
   tensorqtl.calculate_qvalues(table,fdr=r.get('fdr',.05),qvalue_lambda=r.get('qvalue_lambda'));result['original_cis_qvalues']=table.copy(deep=True)
  if op=='CIS_DISCOVERY':
   call.pop('beta_approx');call['cis_df']=table;call['fdr']=r.get('fdr',.05);call.pop('paired_covariate_df',None);result['original_independent']=cis.map_independent(**call)
 elif op=='CIS_INDEPENDENT':
  call.update(cis_df=table,fdr=r.get('fdr',.05),fdr_col=r.get('fdr_column','qval'),nperm=r.get('nperm',10000),seed=r['seed'])
  if not old:call['random_tiebreak']=r.get('random_tiebreak',False)
  result['original_independent']=cis.map_independent(**call)
 elif op=='QVALUES':tensorqtl.calculate_qvalues(table,fdr=r.get('fdr',.05),qvalue_lambda=r.get('qvalue_lambda'));result['original_qvalues']=table
 elif op=='TRANS_NOMINAL':
  call.update(interaction_s=interaction.iloc[:,0]if interaction is not None else None,return_sparse=r.get('return_sparse',True),pval_threshold=r.get('pval_threshold',1e-5),alleles=r.get('alleles',2),return_r2=r.get('return_r2',False),batch_size=r.get('batch_size',20000));result['original_trans']=trans.map_trans(**call)
  if'trans_filter_positions'in r:result['original_trans_filtered']=trans.filter_cis(result['original_trans'],positions('phenotype_positions').T.to_dict()if old else positions('phenotype_positions'),positions('trans_filter_positions',True,r['trans_filter_position_kind']=='declared-region-center'),window=r.get('cis_exclusion_window',5000000))
 elif op=='CIS_SUSIE':
  call.pop('verbose');s=r.get('susie',{});call.update(summary_only=False,L=s.get('L',10),scaled_prior_variance=s.get('scaled_prior_variance',.2),estimate_residual_variance=s.get('estimate_residual_variance',True),estimate_prior_variance=s.get('estimate_prior_variance',True),tol=s.get('tolerance',.001),coverage=s.get('coverage',.95),min_abs_corr=s.get('min_abs_corr',.5),max_iter=s.get('maximum_iterations',200))
  if paired is not None:call['paired_covariate_df']=paired
  result['original_susie']=susie.map(**call)
 else:raise ValueError('No direct reference operation '+op)
 torch.save(result,out/'original-return.pt')
 # Human-readable synthetic-only values. Type, dtype and order are explicit.
 def scalar(x):
  if isinstance(x,np.generic):return scalar(x.item())
  if isinstance(x,float)and not np.isfinite(x):return'NaN'if np.isnan(x)else'Inf'if x>0 else'-Inf'
  if x is None or isinstance(x,(bool,int,float,str)):return x
  if isinstance(x,tuple):return[scalar(v)for v in x]
  raise TypeError('Unexported direct-original type '+type(x).__name__)
 def standard(x):
  if isinstance(x,pd.Series):return dict(type='series',name=scalar(x.name),data=standard(x.to_frame()))
  if isinstance(x,pd.DataFrame):return dict(type='dataframe',shape=list(x.shape),columns=[scalar(k)for k in x.columns],index_names=[scalar(k)for k in x.index.names],index=[scalar(k)for k in x.index],dtypes=[str(t)for t in x.dtypes],data=[[scalar(v)for v in row]for row in x.itertuples(index=False,name=None)])
  if isinstance(x,torch.Tensor):return dict(type='tensor',dtype=str(x.dtype),device=str(x.device),data=standard(x.detach().cpu().numpy()))
  if isinstance(x,np.ndarray):return dict(type='array',shape=list(x.shape),dtype=str(x.dtype),data=[scalar(v)for v in x.flat])
  if isinstance(x,dict):return dict(type='dict',items=[[scalar(k),standard(v)]for k,v in x.items()])
  if isinstance(x,(tuple,list)):return dict(type=type(x).__name__,items=[standard(v)for v in x])
  return dict(type='scalar',value=scalar(x))
 (out/'REFERENCE.json').write_text(json.dumps(standard(result),ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
 assert all(sha(out/'inputs'/key)==row['sha256']==sha(row['path'])for key,row in captured.items())
 assert all(sha(Path(r['source_directory'])/name)==wanted for name,wanted in source_files.items())
 (out/'COMPLETED.json').write_text(json.dumps(dict(status='DIRECT_ORIGINAL_CALL_RETURNED',edition=tensorqtl.__version__,operation=op,device=r['device'],seed=r['seed'],request_sha256=hashlib.sha256(request_path.read_bytes()).hexdigest(),intake_sha256=sha(out/'INTAKE.json'),input_hashes_rechecked=True,original_source_hashes_rechecked=True,scope='Synthetic numerical reference; not optimizer/convergence admission or study replication'),indent=2)+'\n')
if __name__=='__main__':main()
