"""Fixed synthetic CPU health; never opens a caller-supplied model or dataset."""
from pathlib import Path
import importlib.metadata as md
import csv,hashlib,json,os,platform,shutil,sys,sysconfig,traceback

PINNED={'scvi-tools':'1.3.0','torch':'2.6.0+cpu','lightning':'2.5.0.post0','torchmetrics':'1.6.1','anndata':'0.11.4','numpy':'2.0.2','pandas':'2.2.3','scipy':'1.15.2','scikit-learn':'1.6.1','h5py':'3.13.0','jax':'0.4.35','jaxlib':'0.4.35','flax':'0.10.2','optax':'0.2.4','chex':'0.1.87','orbax-checkpoint':'0.6.4','tensorstore':'0.1.67','numpyro':'0.15.3','numba':'0.61.0','llvmlite':'0.44.0','mudata':'0.3.1','pyro-ppl':'1.9.1'}
import importlib.util
_spec=importlib.util.spec_from_file_location('omicsmatic_scvi_provider_common',Path(__file__).with_name('scvi_provider_common.py'))
_provider_io=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(_provider_io)
out=Path(sys.argv[1]);out.mkdir(exist_ok=False)
class UnavailableProfile(RuntimeError):pass
phase='dependency'
def write(name,value):(out/name).write_text(json.dumps(value,sort_keys=True,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8')
def sha(path):
 with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
try:
 versions={name:md.version(name) for name in PINNED}
 missing={name:dict(expected=version,actual=versions[name]) for name,version in PINNED.items() if versions[name]!=version}
 if missing:raise UnavailableProfile('Pinned CPU profile differs: '+repr(missing))
 if platform.system()!='Linux' or platform.machine()!='x86_64' or sys.version_info[:3]!=(3,12,3) or sys.flags.isolated!=1 or not sys.dont_write_bytecode:raise UnavailableProfile('Requires isolated Linux x86_64 Python3.12.3 with bytecode writes disabled')
 import anndata as ad,numpy as np,pandas as pd,scipy.sparse as sp,torch,scvi
 from scvi.model import SCVI,SCANVI
 from scvi.external import SysVI
 from scvi.distributions import NegativeBinomial,ZeroInflatedNegativeBinomial
 if torch.version.cuda is not None or torch.cuda.is_available():raise UnavailableProfile('This profile requires original CPU-only PyTorch')
 phase='health'
 torch.set_num_threads(1);torch.set_num_interop_threads(1);torch.use_deterministic_algorithms(True)
 rows=32;genes=['g'+str(i) for i in range(8)];cells=['health_β_'+str(i) for i in range(rows)]
 counts=(np.arange(rows*8).reshape(rows,8)%9+1).astype(np.float32)
 obs=pd.DataFrame({'batch':['b'+str(i%2) for i in range(rows)],'site':['s'+str(i%3) for i in range(rows)],'age':np.arange(rows,dtype=np.float32)+20,'label':pd.Categorical(['type_B' if i%4<2 else 'type_A' for i in range(rows)],categories=['type_B','type_A','Unlabeled'])},index=cells)
 data=ad.AnnData(X=sp.csr_matrix(counts),obs=obs,var=pd.DataFrame(index=genes));data.layers['counts']=data.X.copy()
 common=dict(accelerator='cpu',devices=1,max_epochs=1,train_size=.75,validation_size=.25,batch_size=8,enable_progress_bar=False,enable_checkpointing=False,logger=None,deterministic=True,benchmark=False,early_stopping=False)
 stages=[]
 def matrix(name,ids,columns,values):
  values=np.asarray(values);assert values.shape==(len(ids),len(columns)) and np.isfinite(values).all()
  with (out/name).open('w',encoding='utf-8',newline='') as f:
   w=csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE);w.writerow(['cell_id',*columns])
   for cell,row in zip(ids,values):w.writerow([cell,*[repr(float(x)) for x in row]])
 def capture(name,model):
  ids=list(model.adata.obs_names);latent=model.get_latent_representation(give_mean=True);dims=['latent_'+str(i) for i in range(latent.shape[1])];matrix(name+'-latent.tsv',ids,dims,latent)
  model.save(str(out/(name+'-owned-model')),save_anndata=False)
  reloaded=type(model).load(str(out/(name+'-owned-model')),adata=model.adata.copy(),accelerator='cpu',device=1)
  matrix(name+'-reload-latent.tsv',list(reloaded.adata.obs_names),dims,reloaded.get_latent_representation(give_mean=True))
  labels=None
  if isinstance(model,SCANVI):
   prob=model.predict(soft=True);labels=list(prob.columns);matrix(name+'-probabilities.tsv',ids,labels,prob.to_numpy());reload_probability=reloaded.predict(soft=True);matrix(name+'-reload-probabilities.tsv',list(reloaded.adata.obs_names),list(reload_probability.columns),reload_probability.to_numpy())
   with (out/(name+'-labels.tsv')).open('w',encoding='utf-8',newline='') as f:
    w=csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE);w.writerow(['cell_id','label']);w.writerows(zip(ids,model.predict()))
  history=model.history
  if not isinstance(history,dict) or not history:raise RuntimeError('Missing original training history')
  with (out/(name+'-history.tsv')).open('w',encoding='utf-8',newline='') as f:
   w=csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE);w.writerow(['metric','epoch','value'])
   for metric,frame in sorted(history.items()):
    if len(frame.columns)!=1:raise RuntimeError('Unexpected metric columns')
    for epoch,value in zip(frame.index,frame.iloc[:,0]):
     if not np.isfinite(float(value)):raise RuntimeError('Nonfinite original training metric')
     w.writerow([str(metric),int(epoch),repr(float(value))])
  with (out/(name+'-splits.tsv')).open('w',encoding='utf-8',newline='') as f:
   w=csv.writer(f,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE);w.writerow(['partition','cell_id'])
   for partition,indices in [('train',model.train_indices),('validation',model.validation_indices),('test',model.test_indices)]:
    if indices is not None:
     for i in indices:w.writerow([partition,ids[int(i)]])
  stages.append(dict(name=name,cells=ids,genes=list(model.adata.var_names),latent_dimensions=latent.shape[1],classes=labels))
 scvi.settings.seed=68001;SCVI.setup_anndata(data,layer='counts',batch_key='batch',categorical_covariate_keys=['site'],continuous_covariate_keys=['age'])
 base=SCVI(data,n_layers=1,n_hidden=16,n_latent=3,dispersion='gene-batch',encode_covariates=True);base.train(**common);capture('scvi',base)
 scvi.settings.seed=68002;scan=SCANVI.from_scvi_model(base,labels_key='label',unlabeled_category='Unlabeled');scan.train(n_samples_per_label=4,**common);capture('scanvi',scan)
 query=data[:8,:].copy();query.obs_names=['query_β_'+str(i) for i in range(8)];query.obs['batch']='query_batch';query.obs['label']='Unlabeled'
 scvi.settings.seed=68003;mapped=SCANVI.load_query_data(query,scan,accelerator='cpu',device=1,inplace_subset_query_vars=False,freeze_dropout=False,freeze_expression=True,freeze_decoder_first_layer=True,freeze_batchnorm_encoder=True,freeze_batchnorm_decoder=False,freeze_classifier=True)
 mapped.train(**dict(common,train_size=1.,validation_size=None,plan_kwargs=dict(weight_decay=0.,n_epochs_kl_warmup=1)));capture('query',mapped)
 for name,prior in [('sysvi-normal','standard_normal'),('sysvi-vamp','vamp')]:
  signed=ad.AnnData(X=sp.csr_matrix((counts-counts.mean(axis=0)).astype(np.float32)),obs=obs.drop(columns='label').copy(),var=pd.DataFrame(index=genes))
  scvi.settings.seed=68004 if prior=='standard_normal' else 68005;SysVI.setup_anndata(signed,batch_key='batch',categorical_covariate_keys=['site'],continuous_covariate_keys=['age'],weight_batches=False)
  model=SysVI(signed,prior=prior,n_hidden=16,n_latent=3,n_layers=1);model.train(**common);capture(name,model)
 with (out/'kernels.tsv').open('w',encoding='utf-8',newline='') as f:
  w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(['count','mean','theta','logit','nb','zinb'])
  for x in [0,1,7]:
   for mu in [.25,3.]:
    for theta in [.5,2.]:
     for logit in [-2.,1.]:
      x0,mu0,th0,log0=[torch.tensor(v,dtype=torch.float64) for v in [x,mu,theta,logit]]
      w.writerow([x,mu,theta,logit,repr(float(NegativeBinomial(mu=mu0,theta=th0).log_prob(x0))),repr(float(ZeroInflatedNegativeBinomial(mu=mu0,theta=th0,zi_logits=log0).log_prob(x0)))])
 identity=_provider_io.runtime_identity(out)
 write('probe.json',dict(identity,schema_version=1,status='completed-fixed-original-CPU-health-only',stages=stages,scope='Synthetic CPU transport,training,save/reload and scalar likelihood health only;not biological accuracy,convergence,arbitrary-model trust or complete public adapter.',warning='SysVI1.3.0 defaultVamp uses gene count for its pseudoinput cell-index bound. This32cell8gene health keeps original source selection;never silently clamp or change the bound.'))
 print('PASS fixed5stage original CPU health;native complete output audit required',flush=True)
except BaseException as error:
 unavailable=phase=='dependency' and (isinstance(error,(UnavailableProfile,md.PackageNotFoundError,ModuleNotFoundError)) or isinstance(error,OSError) and error.errno in (2,13))
 write('probe-failure.json',dict(status='SKIP3-unavailable-exact-profile' if unavailable else 'FAIL2-runtime-health-retained-no-model-analysis',phase=phase,error=repr(error),traceback=traceback.format_exc(),required_action='Retain this attempt. Supply the exact permitted CPU profile,correct the named cause,and rerun admission to a NEW directory. A missing or forbidden runtime must leave dependent analysis skipped;do not substitute a historical release.'))
 if unavailable:raise SystemExit(3)
 raise
