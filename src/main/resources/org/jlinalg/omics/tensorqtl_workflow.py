# Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Closed calls to byte-guarded original tensorQTL. Saved pickles are never input."""
import csv,gzip,hashlib,importlib.metadata,inspect,json,os,platform,struct,sys,traceback,types,warnings
from pathlib import Path
ROOT=Path('..').resolve();WORK=Path.cwd();UPSTREAM=ROOT/'source/upstream'
report=dict(status='RUNNING_NO_RESULT_ADMITTED',results_admitted=False)
messages=[];events=[];guards=[];objects=0;elements=0;nonfinite=[];optim_failed=[];unconverged=[];cuda_returns=0;torch_dtypes=set()
np=pd=torch=None
ACTION='SKIP3: dependent analysis skipped; restore the exact permitted named source/runtime/package/device or retain the skip. FAIL2: no complete result; preserve original objects, inputs, warnings, traceback and partials, correct the named data/design/numerical/convergence/integrity/process/capacity cause and retry NEW output. No source edits, identity harmonization, count rounding, estimator substitution, nonfinite repair or automatic CPU fallback.'
class Unavailable(Exception):pass
def read(path):return json.loads(Path(path).read_bytes())
def within(path,base):
 try:Path(path).resolve().relative_to(Path(base).resolve());return True
 except ValueError:return False
def emit(path,value):Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
def pin(path):
 h=hashlib.sha256();n=0
 with Path(path).open('rb')as f:
  for x in iter(lambda:f.read(1048576),b''):h.update(x);n+=len(x)
 return dict(bytes=n,sha256=h.hexdigest())
def files_check(profile,bindings,bridge):
 path=ROOT/'source'/profile['runtime_files_resource']
 if pin(path)!=profile['runtime_files']:raise RuntimeError('Packaged runtime-file profile changed')
 with gzip.open(path,'rb')as f:files=json.load(f)
 checked={}
 for kind,entries in files.items():
  if kind in ['R','bridge']and not bridge:continue
  base=Path(bindings['r_home'])if kind=='R'else Path(sys.prefix)
  for relative,wanted in entries.items():
   p=base/relative
   if not p.is_file():raise Unavailable('Required permitted runtime file is missing: '+str(p))
   if not within(p,base.parent.parent if kind=='R'else base):raise RuntimeError('Runtime file escapes its qualified prefix: '+relative)
   if pin(p)!=wanted:raise RuntimeError('Qualified runtime file changed: '+relative+'; restore the exact private profile')
  checked[kind]=len(entries)
 return checked
def canonical(code):return canonical_code(code)
def canonical_code(code):return code.replace(co_filename='<qualified-original-source>',co_consts=tuple(canonical_code(x)if isinstance(x,types.CodeType)else x for x in code.co_consts))
def namespace_guard():
 expected={};captured=[]
 def codes(code):
  for c in code.co_consts:
   if isinstance(c,types.CodeType):expected.setdefault((c.co_filename,c.co_name,c.co_firstlineno),set()).add(canonical(c));codes(c)
 for p in sorted((UPSTREAM/'tensorqtl').glob('*.py')):codes(compile(p.read_bytes(),str(p),'exec',dont_inherit=True))
 for name,module in sorted(list(sys.modules.items())):
  path=getattr(module,'__file__',None)
  if path is None or not within(path,UPSTREAM):continue
  owners=[module]+[x for x in vars(module).values()if inspect.isclass(x)and getattr(x,'__module__',None)==name]
  for owner in owners:
   for key,value in list(vars(owner).items()):
    if isinstance(value,(staticmethod,classmethod)):value=value.__func__
    if not inspect.isfunction(value)or not within(value.__code__.co_filename,UPSTREAM):continue
    code=value.__code__;signature=canonical(code)
    if signature not in expected.get((code.co_filename,code.co_name,code.co_firstlineno),set()):raise RuntimeError('Original source code differs from loaded function: '+name+'.'+key)
    captured.append((owner,key,value,signature,repr(value.__defaults__),repr(value.__kwdefaults__)))
 return captured
def namespace_after():
 for owner,key,value,signature,defaults,kwdefaults in guards:
  current=getattr(owner,key)
  if inspect.ismethod(current):current=current.__func__
  if current is not value or canonical(current.__code__)!=signature or repr(current.__defaults__)!=defaults or repr(current.__kwdefaults__)!=kwdefaults:raise RuntimeError('Original namespace changed: '+key)
def finite(x,label):
 if torch is not None and isinstance(x,torch.Tensor):x=x.detach().cpu().numpy()
 if np is not None and isinstance(x,np.ndarray)and x.dtype.kind in 'fci'and not np.isfinite(x).all():nonfinite.append(label)
 elif np is not None and isinstance(x,(float,np.floating))and not np.isfinite(x):nonfinite.append(label)
def devices(x,depth=0):
 global cuda_returns
 if depth>3:return
 if isinstance(x,torch.Tensor):
  torch_dtypes.add(str(x.dtype))
  if x.is_cuda:cuda_returns+=1
 elif isinstance(x,(tuple,list)):
  for v in x[:64]:devices(v,depth+1)
 elif isinstance(x,dict):
  for v in list(x.values())[:64]:devices(v,depth+1)
def original_object(label,value,extra=None):
 global objects
 objects+=1;part=Path('objects/%06d-record.part'%objects);torch.save(value,part);wanted=pin(part)
 with open('objects.journal','ab')as out,part.open('rb')as source:
  offset=out.tell()+8;out.write(struct.pack('>Q',wanted['bytes']))
  for block in iter(lambda:source.read(1048576),b''):out.write(block)
 digest=hashlib.sha256();remaining=wanted['bytes']
 with open('objects.journal','rb')as verify:
  verify.seek(offset)
  while remaining:
   block=verify.read(min(1048576,remaining))
   if not block:raise RuntimeError('Original-object journal ended early; retain the source part')
   digest.update(block);remaining-=len(block)
 if digest.hexdigest()!=wanted['sha256']:raise RuntimeError('Original-object journal differs; retain the source part')
 row=dict(ordinal=objects,label=label,path='worker/objects.journal',payload_offset=offset,payload_bytes=wanted['bytes'],sha256=wanted['sha256'],serializer='original torch.save object; trusted inspection only')
 if extra:row.update(extra)
 events.append(row)
 with open('objects-index.jsonl','a',encoding='utf-8')as f:f.write(json.dumps(row,ensure_ascii=False,allow_nan=False)+'\n')
 part.unlink()  # Exact bytes are retained in the verified journal record.
 return row
def observer(frame,event,value):
 if event!='return':return
 if frame.f_code in watch_compute:devices(value)
 if frame.f_code in watch_susie and isinstance(value,dict):
  row=original_object('original-susie-return',value,dict(phenotype_id=str(frame.f_back.f_locals.get('phenotype_id','unrecorded')),source_converged=bool(value.get('converged',False)),niter=int(value.get('niter',0)),has_credible_set=value.get('sets',{}).get('cs')is not None))
  if not row['source_converged']:unconverged.append(row['ordinal'])
  for key in ['pip','elbo','lbf_variable','alpha','mu','mu2','sigma2','V']:finite(value.get(key),'susie-return/'+str(row['ordinal'])+'/'+key)
 if frame.f_code in watch_minimize and frame.f_back and within(frame.f_back.f_code.co_filename,UPSTREAM)and hasattr(value,'success'):
  row=original_object('original-scipy-minimize-return',value,dict(source_success=bool(value.success),source_status=int(value.status),source_message=str(value.message),iterations=int(value.get('nit',0)),function_evaluations=int(value.get('nfev',0))))
  if not row['source_success']:optim_failed.append(row['ordinal'])
 if frame.f_code in watch_rqvalue and 'q'in frame.f_locals:
  path=WORK/'objects'/('%06d-original-R-qvalue.rds'%(len(events)+1));robjects.r['saveRDS'](frame.f_locals['q'],file=str(path));events.append(dict(label='original-R-qvalue',path='worker/objects/'+path.name))
def matrix_in(path,id_name):
 frame=pd.read_csv(path,sep='\t',index_col=0,keep_default_na=False,dtype={id_name:str});frame.index=frame.index.astype(str)
 for col in frame:frame[col]=pd.to_numeric(frame[col],errors='raise')
 return frame
def positions(path,id_name,interval=False,center=False):
 frame=pd.read_csv(path,sep='\t',index_col=0,keep_default_na=False,dtype={id_name:str,'chr':str});frame.index=frame.index.astype(str)
 for col in ['start','end']if interval else['pos']:frame[col]=pd.to_numeric(frame[col],errors='raise').astype(np.float64 if center else np.int64)
 return frame
def scalar(x):
 if isinstance(x,np.generic):return scalar(x.item())
 if isinstance(x,float)and not np.isfinite(x):return dict(kind='nonfinite',value='NaN'if np.isnan(x)else'Inf'if x>0 else'-Inf')
 if x is None or isinstance(x,(bool,int,float,str)):return dict(kind='scalar',type=type(x).__name__,value=x)
 raise TypeError('Original result contains an unsupported exported type '+type(x).__name__+'; raw original object retained without admission')
def cell(x,allow_nan=False):
 if x is None:return 'NULL'
 if isinstance(x,(bool,np.bool_)):return'TRUE'if x else'FALSE'
 if isinstance(x,(float,np.floating)):
  if not np.isfinite(x):
   if not(allow_nan and np.isnan(x)):nonfinite.append('original exported numeric cell')
   return'NaN'if np.isnan(x)else'Inf'if x>0 else'-Inf'
  return format(float(x),'.17g')
 text=str(x)
 if any(c in text for c in ['\t','\r','\n','\0']):raise RuntimeError('Original output contains a TSV control character; preserve raw object without admission')
 return text
def rows_out(element,rows,allow_nan_columns=()):
 with (ROOT/'results/values.tsv').open('a',encoding='utf-8',newline='')as f:
  for ordinal,row in enumerate(rows):f.write(str(element)+'\t'+str(ordinal)+'\t'+json.dumps([cell(x,j in allow_nan_columns)for j,x in enumerate(row)],ensure_ascii=False,separators=(',',':'))+'\n')
def export(value,label):
 global elements
 elements+=1;element=elements
 if isinstance(value,pd.Series):
  node=export(value.to_frame(),label);node.update(kind='series',series_name=scalar(value.name),series_dtype=str(value.dtype));return node
 if isinstance(value,pd.DataFrame):
  allowed={'beta_shape1','beta_shape2','true_df','pval_true_df','pval_beta'}if request_parameters['operation']=='CIS_PERMUTE'and request_parameters.get('beta_approx')is False and label=='result/original_cis_permutations'else set()
  for col in value:
   if pd.api.types.is_numeric_dtype(value[col].dtype):
    values=value[col].to_numpy();finite(values[~np.isnan(values)]if col in allowed else values,label+'/'+str(col))
  levels=value.index.nlevels
  rows_out(element,((*(index if levels>1 else(index,)),*row)for index,row in zip(value.index,value.itertuples(index=False,name=None))),[levels+j for j,col in enumerate(value.columns)if col in allowed])
  return dict(kind='dataframe',element=element,shape=list(value.shape),index_levels=levels,index_names=[scalar(x)for x in value.index.names],index_class=type(value.index).__name__,columns=[scalar(x)for x in value.columns],dtypes=[str(x)for x in value.dtypes])
 if isinstance(value,torch.Tensor):
  node=export(value.detach().cpu().numpy(),label);node.update(kind='tensor',torch_dtype=str(value.dtype),source_device=str(value.device));return node
 if isinstance(value,np.ndarray):
  finite(value,label)
  rows_out(element,((x,)for x in value.flat))
  return dict(kind='ndarray',element=element,shape=list(value.shape),dtype=str(value.dtype),length=int(value.size),order='C')
 if isinstance(value,dict):return dict(kind='dict',items=[dict(key=scalar(k),value=export(v,label+'/'+str(k)))for k,v in value.items()])
 if isinstance(value,(tuple,list)):return dict(kind='tuple'if isinstance(value,tuple)else'list',items=[export(x,label+'/'+str(i))for i,x in enumerate(value)])
 node=scalar(value)
 if node['kind']=='nonfinite':nonfinite.append(label)
 return node
def main():
 global np,pd,torch,guards,watch_compute,watch_susie,watch_minimize,watch_rqvalue,robjects,request_parameters
 p=read('parameters.json');bindings=read('runtime-bindings.json');profile=read(ROOT/'source/SOURCE-PROFILES.json')['profiles'][p['profile']];edition=profile['edition'];old=edition=='1.0.5';op=p['operation'];bridge=bool(bindings.get('r_home'))
 request_parameters=p
 if sys.version.split()[0]!=profile['python_version']or sys.platform!='linux'or platform.machine()!='x86_64':raise Unavailable('Unqualified Python edition/platform; require '+profile['python_version']+' Linux x86_64')
 for name,version in profile['packages'].items():
  try:actual=importlib.metadata.version(name)
  except importlib.metadata.PackageNotFoundError:raise Unavailable('Required original Python package missing: '+name+' '+version)
  if actual!=version:raise Unavailable('Unqualified package '+name+': '+actual+'; require '+version)
 if bridge and importlib.metadata.version('rpy2')!=profile['rpy2_version']:raise Unavailable('Unqualified rpy2 edition')
 checked=files_check(profile,bindings,bridge)
 for relative,wanted in profile['original_source']['files'].items():
  if pin(UPSTREAM/relative)!={k:wanted[k]for k in ['bytes','sha256']}:raise RuntimeError('Original tensorQTL source changed: '+relative)
 with open('objects.journal','xb')as f:f.write(b'OMICS-TORCH-JOURNAL1\n')
 sys.path.insert(0,str(UPSTREAM));import numpy as np,pandas as pd,torch
 import tensorqtl
 if tensorqtl.__version__!=edition:raise RuntimeError('Original tensorQTL namespace edition differs')
 from tensorqtl import cis,trans
 torch.set_num_threads(1);np.random.seed(p['seed']);torch.manual_seed(p['seed'])
 if p['device']=='CUDA':
  if not torch.cuda.is_available():raise Unavailable('CUDA explicitly requested but unavailable; restore the declared permitted CUDA runtime/device or retain the skip; no CPU fallback')
  if p.get('cuda_device',0)>=torch.cuda.device_count():raise Unavailable('Requested visible CUDA device is unavailable; select a permitted visible ordinal or retain the skip')
  torch.cuda.set_device(p.get('cuda_device',0));torch.cuda.manual_seed(p['seed']);torch.cuda.reset_peak_memory_stats()
 elif torch.cuda.is_available():raise RuntimeError('CPU execution must hide CUDA before Python starts')
 rmeta=None
 if bridge:
  from rpy2 import robjects
  from rpy2.robjects.packages import importr
  if str(robjects.r('as.character(getRversion())')[0])!=profile['r_version']:raise Unavailable('Unqualified embedded R version')
  if bindings.get('libraries'):robjects.r['.libPaths'](robjects.StrVector(bindings['libraries']))
  if p['requires_R']:
   try:importr('qvalue')
   except Exception as e:raise Unavailable('Original qvalue package cannot load: '+str(e))
   if str(robjects.r('as.character(packageVersion("qvalue"))')[0])!=profile['qvalue_version']:raise Unavailable('Unqualified R qvalue version')
   actual=Path(str(robjects.r('getNamespaceInfo(asNamespace("qvalue"),"path")')[0])).resolve()
   if actual!=(Path(bindings['r_home'])/'library/qvalue').resolve():raise RuntimeError('Original qvalue namespace is shadowed by another library; restore the exact qualified namespace')
  robjects.r('function(path){globalCallingHandlers(warning=function(w){cat(conditionMessage(w),"\\n",file=path,append=TRUE)})}')(str(WORK/'R-warnings.txt'))
  robjects.r['set.seed'](int(p['seed']));rmeta=dict(version=profile['r_version'],qvalue=profile['qvalue_version']if p['requires_R']else'not requested',rpy2=profile['rpy2_version'],mode=profile['rpy2_cffi_mode'])
 elif p['requires_R']:raise Unavailable('This operation requires explicit qualified R home and original qvalue; dependent analysis skipped')
 guards=namespace_guard();watch_compute=set();watch_susie=set();watch_minimize={__import__('scipy.optimize',fromlist=['minimize']).minimize.__code__};watch_rqvalue=set()
 for module in list(sys.modules.values()):
  name=getattr(module,'__file__','')or''
  if name and within(name,UPSTREAM):
   for key in ['calculate_corr','calculate_interaction_nominal','calculate_cis_permutations','susie']:
    fn=getattr(module,key,None)
    if inspect.isfunction(fn):watch_compute.add(fn.__code__)
    if key=='susie'and inspect.isfunction(fn):watch_susie.add(fn.__code__)
   fn=getattr(module,'qvalue',None)
   if inspect.isfunction(fn):watch_rqvalue.add(fn.__code__)
 def rng_state():return dict(numpy=np.random.get_state(),torch=torch.get_rng_state(),cuda=torch.cuda.get_rng_state()if p['device']=='CUDA'else None,R=dict(kind=list(robjects.r('RNGkind()')),seed=list(robjects.globalenv['.Random.seed']))if bridge else None)
 frames={};result={};original_object('rng-before',rng_state())
 if op!='QVALUES':
  frames['genotypes']=matrix_in('genotypes.tsv','variant_id');frames['phenotypes']=matrix_in('phenotypes.tsv','phenotype_id');frames['variants']=positions('variants.tsv','variant_id').rename(columns={'chr':'chrom'})if p['has_variants']else None
  pos=positions('phenotype_positions.tsv','phenotype_id',p.get('position_kind')=='interval')if p['has_phenotype_positions']else None
  if old and pos is not None:pos=pos.rename(columns={'pos':'tss'})
  frames['phenotype_positions']=pos;cov=matrix_in('covariates.tsv','sample_id')if p['has_covariates']else None;frames['covariates']=cov
  if cov is not None and np.linalg.matrix_rank(cov.values-cov.values.mean(0))!=cov.shape[1]:raise ValueError('Centered covariates are rank deficient; no intercept addition, column dropping or design repair')
  groups=pd.read_csv('groups.tsv',sep='\t',index_col=0,dtype=str,keep_default_na=False).iloc[:,0]if p['has_groups']else None
  interaction=matrix_in('interaction.tsv','sample_id')if p['has_interaction']else None
  paired=matrix_in('paired_covariates.tsv','phenotype_id')if p['has_paired_covariates']else None
  frames.update(groups=groups,interaction=interaction,paired_covariates=paired);original_object('prepared-original-inputs',frames)
 if p['has_cis_results']:
  table=pd.read_csv('cis_results.tsv',sep='\t',index_col=0,keep_default_na=False,dtype={x:str for x in ['phenotype_id','variant_id','group_id','chrom','chr']});result['supplied_cis_results']=table.copy(deep=True)
 common={}
 if op!='QVALUES':common=dict(genotype_df=frames['genotypes'],phenotype_df=frames['phenotypes'],covariates_df=cov,maf_threshold=p['maf_threshold'],verbose=True)
 if op.startswith('CIS_'):
  common.update(variant_df=frames['variants'],phenotype_pos_df=frames['phenotype_positions'],window=p['window'])
  if op!='CIS_SUSIE':common['group_s']=groups
 previous=sys.getprofile();sys.setprofile(observer)
 try:
  if op=='TRANS_NOMINAL':
   call=dict(common,interaction_s=interaction.iloc[:,0]if interaction is not None else None,return_sparse=p['return_sparse'],pval_threshold=p['pval_threshold'],alleles=p['alleles'],return_r2=p['return_r2'],batch_size=p['batch_size']);value=trans.map_trans(**call);result['original_trans']=value
   if p['has_trans_filter_positions']:
    filter_pos=positions('trans_filter_positions.tsv','variant_id',center=p['trans_filter_position_kind']=='declared-region-center').rename(columns={'chr':'chrom'});pos=frames['phenotype_positions'];result['original_trans_filtered']=trans.filter_cis(value,pos.T.to_dict()if old else pos,filter_pos,window=p['cis_exclusion_window'])
  elif op=='CIS_NOMINAL':
   call=dict(common,prefix='analysis',output_dir=str(ROOT/'results/original'),write_top=True,write_stats=True)
   if interaction is not None:call['interaction_s'if old else'interaction_df']=interaction.iloc[:,0]if old else interaction;call['maf_threshold_interaction']=p['maf_threshold_interaction'];call['run_eigenmt']=p['run_eigenmt']
   if paired is not None:call['paired_covariate_df']=paired
   value=cis.map_nominal(**call);result['original_return']=value
   for path in sorted((ROOT/'results/original').glob('*.parquet')):result[path.name]=pd.read_parquet(path)
   for path in sorted((ROOT/'results/original').glob('*.txt.gz')):result[path.name]=pd.read_csv(path,sep='\t',keep_default_na=False)
  elif op in ['CIS_PERMUTE','CIS_DISCOVERY']:
   call=dict(common,nperm=p['nperm'],seed=p['seed'],beta_approx=p.get('beta_approx',True))
   if not old:call['random_tiebreak']=p['random_tiebreak']
   if paired is not None:call['paired_covariate_df']=paired
   table=cis.map_cis(**call);result['original_cis_permutations']=table.copy(deep=True)
   if op=='CIS_DISCOVERY'or p.get('qvalues'):
    tensorqtl.calculate_qvalues(table,fdr=p['fdr'],qvalue_lambda=p.get('qvalue_lambda'));result['original_cis_qvalues']=table.copy(deep=True)
   if op=='CIS_DISCOVERY':
    call=dict(common,cis_df=table,nperm=p['nperm'],seed=p['seed'],fdr=p['fdr'])
    if not old:call['random_tiebreak']=p['random_tiebreak']
    result['original_independent']=cis.map_independent(**call)
  elif op=='CIS_INDEPENDENT':
   call=dict(common,cis_df=table,nperm=p['nperm'],seed=p['seed'],fdr=p['fdr'],fdr_col=p['fdr_column'])
   if not old:call['random_tiebreak']=p['random_tiebreak']
   result['original_independent']=cis.map_independent(**call)
  elif op=='QVALUES':tensorqtl.calculate_qvalues(table,fdr=p['fdr'],qvalue_lambda=p.get('qvalue_lambda'));result['original_qvalues']=table
  elif op=='CIS_SUSIE':
   from tensorqtl import susie
   # Import/guard before execution so every original susie return is observed.
   for owner,key,fn,signature,defaults,kwdefaults in namespace_guard():
    if not any(x[0]is owner and x[1]==key for x in guards):guards.append((owner,key,fn,signature,defaults,kwdefaults))
   watch_susie.add(susie.susie.__code__);watch_compute.add(susie.susie.__code__)
   call=dict(common,summary_only=False);call.pop('verbose');s=p['susie'];call.update(L=s['L'],scaled_prior_variance=s['scaled_prior_variance'],estimate_residual_variance=s['estimate_residual_variance'],estimate_prior_variance=s['estimate_prior_variance'],tol=s['tolerance'],coverage=s['coverage'],min_abs_corr=s['min_abs_corr'],max_iter=s['maximum_iterations'])
   if paired is not None:call['paired_covariate_df']=paired
   result['original_susie']=susie.map(**call)
  else:raise ValueError('Unknown original operation')
 finally:sys.setprofile(previous)
 original_object('original-workflow-results',result);original_object('rng-after',rng_state())
 namespace_after();report['runtime_files_checked']=files_check(profile,bindings,bridge);report['namespace_unchanged']=True
 with (ROOT/'results/values.tsv').open('x',encoding='utf-8')as f:f.write('element\trow\tvalues\n')
 tree=export(result,'result');emit(ROOT/'results/objects.json',tree)
 if optim_failed:raise RuntimeError('Original SciPy minimizer reported failure in events '+str(optim_failed)+'; source retains estimates without checking success; NO COMPLETE RESULT')
 if unconverged:raise RuntimeError('Original SuSiE did not converge in events '+str(unconverged)+'; all returns retained even if original map discards them; NO COMPLETE RESULT')
 if nonfinite:raise RuntimeError('Original nonfinite outputs retained: '+str(nonfinite[:16])+'; NO COMPLETE RESULT')
 if p['device']=='CUDA':
  torch.cuda.synchronize()
  if cuda_returns<1:raise RuntimeError('No CUDA tensors observed from original computational functions; CUDA execution is not established')
 report.update(status='completed-with-warnings',results_admitted=True,profile=p['profile'],edition=edition,operation=op,project_seed=p['seed'],author_seed_inferred=False,precision='original Torch float32 mapping; original SciPy/R postprocessing',python=sys.version,packages={k:importlib.metadata.version(k)for k in profile['packages']},R=rmeta,requested_device=p['device'],observed_cuda_returns=cuda_returns,observed_torch_dtypes=sorted(torch_dtypes),cuda_max_memory_allocated=int(torch.cuda.max_memory_allocated())if p['device']=='CUDA'else 0,gpu_name=torch.cuda.get_device_name(torch.cuda.current_device())if p['device']=='CUDA'else None,optimizer_failures=optim_failed,nonconverged_models=unconverged,nonfinite_outputs=nonfinite,original_event_count=len(events),namespace_functions_checked=len(guards),runtime_files_prechecked=checked)
code=2
try:
 with warnings.catch_warnings(record=True)as observed:
  warnings.simplefilter('always');main()
 messages=[str(x.message)for x in observed];code=0
except Unavailable as e:report.update(status='SKIP3_DEPENDENCY_UNAVAILABLE',results_admitted=False,error=str(e));traceback.print_exc(file=open('traceback.txt','w',encoding='utf-8'));code=3
except BaseException as e:report.update(status='FAIL2_NO_COMPLETE_RESULT',results_admitted=False,error=str(e),optimizer_failures=optim_failed,nonconverged_models=unconverged,nonfinite_outputs=nonfinite);traceback.print_exc(file=open('traceback.txt','w',encoding='utf-8'));code=2
finally:
 if 'observed'in globals():messages=[str(x.message)for x in observed]
 report['required_action']=ACTION;report['python_warnings']=messages;report['original_events']=events;emit('report.json',report)
 text='DEVELOPMENT_SCOPE: prepared original tensorQTL workflows; no inferred author recipe or whole-paper reproduction.\n'+ACTION+'\nOriginal mapping uses float32, with original mean imputation/filtering and version-specific branches. Project seeds do not establish bitwise equivalence across CPU/CUDA.\nExplicit CIS_PERMUTE beta_approx=false preserves original undefined NaNs only in beta_shape1,beta_shape2,true_df,pval_true_df,pval_beta; no value is repaired. Other unexpected nonfinite results are refused.\nInspect original stdout/stderr, R-warnings.txt, objects, and events. The paper P157 likelihood-ratio description remains unresolved; original interaction coefficient t-tests are not relabeled.\nOriginal Python warnings:\n'+'\n'.join(messages)+'\n'
 Path('warnings.txt').write_text(text,encoding='utf-8');detail=report.get('error','Original operation completed; inspect scope and original diagnostics');clean=lambda x:x.replace('\t',' ').replace('\r',' ').replace('\n',' ')
 Path('worker-status.tsv').write_text('status\tdetail\trequired_action\n'+report['status']+'\t'+clean(detail)+'\t'+clean(ACTION)+'\n',encoding='utf-8')
sys.exit(code)
