"""Fixed original CPU model execution for closed native scientific requests."""
from pathlib import Path
import copy,csv,importlib.util,json,sys,traceback,warnings

_spec=importlib.util.spec_from_file_location('omicsmatic_scvi_provider_common',Path(__file__).with_name('scvi_provider_common.py'))
_io=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(_io)
request_path=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(exist_ok=False)
phase='dependency';reported_warnings=[]
_show=warnings.showwarning
def _warning(message,category,filename,lineno,file=None,line=None):
 reported_warnings.append(dict(message=str(message),category=category.__name__,filename=str(filename),line=lineno))
 _show(message,category,filename,lineno,file,line)
warnings.showwarning=_warning;warnings.simplefilter('always')

try:
 _io.versions()
 import anndata as ad,numpy as np,pandas as pd,scipy.io as sio,torch,scvi
 from scvi.model import SCVI,SCANVI
 from scvi.external import SysVI
 if torch.version.cuda is not None or torch.cuda.is_available():raise _io.UnavailableProfile('Original CPU-only PyTorch required')
 torch.set_num_threads(1);torch.set_num_interop_threads(1);torch.use_deterministic_algorithms(True)
 request=json.loads(request_path.read_text(encoding='utf-8'));q=request['scientific']
 if request['schema']!='omicsmatic-scvi-execution-v1' or q['schema']!='omicsmatic-scvi-scientific-request-v1' or q['release']!='1.3.0' or not q['runtime_admission_required'] or q['model_analysis_completed']:raise ValueError('Native scientific execution protocol differs')
 common=dict(accelerator='cpu',devices=1,enable_progress_bar=False,enable_checkpointing=False,logger=None,deterministic=True,benchmark=False)
 def scalar(value):
  if isinstance(value,(str,np.str_)):return dict(kind='string',value=str(value))
  if isinstance(value,(bool,np.bool_)):return dict(kind='boolean',value=str(bool(value)).lower())
  if isinstance(value,(int,np.integer)):return dict(kind='integer',value=str(int(value)))
  if isinstance(value,(float,np.floating)):
   if not np.isfinite(value):raise ValueError('Nonfinite learned category')
   return dict(kind='float32' if isinstance(value,np.float32) else 'float64',value=float(value).hex())
  if value is None:return dict(kind='null',value='')
  raise ValueError('Unsupported learned category type '+str(type(value)))
 def scalar_key(value):
  s=scalar(value);return (s['kind'],s['value'])
 def decode(value):
  kind=value['kind'];x=value['value']
  if kind=='string':return x
  if kind=='integer':return int(x)
  if kind=='boolean':return x=='true'
  if kind=='float32':return np.float32(float.fromhex(x))
  if kind=='float64':return float.fromhex(x)
  if kind=='null':return None
  raise ValueError('Unsupported native category conversion')
 def data(spec):
  root=Path(spec['matrix']);genes=(root/'genes.tsv').read_text(encoding='utf-8').splitlines();cells=(root/'cells.tsv').read_text(encoding='utf-8').splitlines()
  matrix=sio.mmread(root/'matrix.mtx').T.tocsr().astype(np.float32)
  obs=pd.read_csv(spec['metadata'],sep='\t',index_col='cell_id',dtype=str,keep_default_na=False,na_filter=False,quoting=csv.QUOTE_NONE)
  if list(obs.index)!=cells or matrix.shape!=(len(cells),len(genes)):raise ValueError('Complete admitted cell/gene population differs')
  bridge=spec.get('query_bridge')
  if bridge:
   # Native bridge validates all source/target roles and exact observed mappings first.
   original=obs.copy();obs=obs.rename(columns=bridge['rename'])
   for source,entries in bridge['values'].items():
    mapping={e['source']:decode(e['target']) for e in entries};target=bridge['rename'].get(source,source)
    if not set(original[source]).issubset(mapping):raise ValueError('Incomplete reviewed category conversion')
    obs[target]=pd.Series([mapping[x] for x in original[source]],index=obs.index,dtype=object)
  for column in spec['continuous']:obs[column]=obs[column].astype(np.float32)
  for column,order in spec.get('categorical_order',{}).items():
   if len(set(order))!=len(order) or not set(obs[column]).issubset(order):raise ValueError('Declared categorical order does not cover observed values')
   obs[column]=pd.Categorical(obs[column],categories=order)
  result=ad.AnnData(X=matrix,obs=obs,var=pd.DataFrame(index=genes))
  if spec['layer'] is not None:result.layers[spec['layer']]=matrix.copy()
  return result
 def table(name,header,rows):
  with (out/name).open('x',encoding='utf-8',newline='') as stream:
   writer=csv.writer(stream,delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE);writer.writerow(header);writer.writerows(rows)
 def matrix(name,cells,columns,values):
  values=np.asarray(values)
  if values.dtype!=np.float32 or values.shape!=(len(cells),len(columns)) or not np.isfinite(values).all():raise ValueError('Original complete export shape/float32 precision differs')
  table(name,['cell_id',*columns],([cell,*[repr(float(x)) for x in row]] for cell,row in zip(cells,values)))
 stages=[]
 def capture(name,model,seed,epochs):
  cells=list(model.adata.obs_names);scvi.settings.seed=seed
  latent=model.get_latent_representation(give_mean=True);columns=['latent_'+str(i) for i in range(latent.shape[1])]
  matrix(name+'-latent.tsv',cells,columns,latent)
  model_dir=out/(name+'-model');model.save(str(model_dir),save_anndata=False)
  reloaded=type(model).load(str(model_dir),adata=model.adata.copy(),accelerator='cpu',device=1)
  scvi.settings.seed=seed;matrix(name+'-reload-latent.tsv',list(reloaded.adata.obs_names),columns,reloaded.get_latent_representation(give_mean=True))
  classes=[]
  if isinstance(model,SCANVI):
   scvi.settings.seed=seed;prob=model.predict(soft=True);classes=[scalar(x) for x in prob.columns];aliases=['class_'+str(i) for i in range(len(classes))]
   matrix(name+'-probabilities.tsv',cells,aliases,prob.to_numpy());scvi.settings.seed=seed;rp=reloaded.predict(soft=True)
   if [scalar(x) for x in rp.columns]!=classes:raise ValueError('Reloaded learned class order differs')
   matrix(name+'-reload-probabilities.tsv',list(reloaded.adata.obs_names),aliases,rp.to_numpy())
   lookup={scalar_key(x):aliases[i] for i,x in enumerate(prob.columns)};scvi.settings.seed=seed
   table(name+'-labels.tsv',['cell_id','label'],zip(cells,[lookup[scalar_key(x)] for x in model.predict()]))
  history=model.history
  if not isinstance(history,dict) or not history:raise ValueError('Original dictionary training history is missing')
  history_rows=[]
  for metric,frame in sorted(history.items()):
   if len(frame.columns)!=1:raise ValueError('Original training metric width differs')
   for epoch,value in zip(frame.index,frame.iloc[:,0]):
    if not np.isfinite(float(value)):raise ValueError('Original training history contains a nonfinite value')
    history_rows.append([str(metric),int(epoch),repr(float(value))])
  table(name+'-history.tsv',['metric','epoch','value'],history_rows)
  table(name+'-splits.tsv',['partition','cell_id'],([partition,cells[int(i)]] for partition,indices in [('train',model.train_indices),('validation',model.validation_indices),('test',model.test_indices)] if indices is not None for i in indices))
  stages.append(dict(name=name,family='SCANVI' if isinstance(model,SCANVI) else 'SYSVI' if isinstance(model,SysVI) else 'SCVI',cells=cells,genes=list(model.adata.var_names),latent_dimensions=latent.shape[1],classes=classes,model_sha256=_io.sha(model_dir/'model.pt'),epochs_requested=epochs,inference_seed=seed,strict_original_reload_completed=True))
 phase='model-execution';d=data(request['data']);spec=request['data'];scvi.settings.seed=q['seed']
 if q['operation']=='FIT':
  kwargs=copy.deepcopy(q['model_kwargs']);setup=dict(layer=spec['layer'],batch_key=spec['batch'],categorical_covariate_keys=spec['categorical'],continuous_covariate_keys=spec['continuous'])
  if q['family']=='SYSVI':
   SysVI.setup_anndata(d,**setup,weight_batches=q['setup_weight_batches'])
   if 'pseudoinputs_data_indices' in kwargs:kwargs['pseudoinputs_data_indices']=np.asarray(kwargs['pseudoinputs_data_indices'],dtype=np.int64)
   model=SysVI(d,**kwargs);name='fit'
  else:
   if spec['labels'] is not None:setup['labels_key']=spec['labels']
   SCVI.setup_anndata(d,**setup);model=SCVI(d,**kwargs);name='base'
  model.train(**copy.deepcopy(q['training_kwargs']),**common);capture(name,model,q['seed'],q['training_kwargs']['max_epochs'])
  if q['annotation'] is not None:
   a=q['annotation'];scvi.settings.seed=a['seed'];model=SCANVI.from_scvi_model(model,labels_key=spec['labels'],unlabeled_category=spec['unlabeled'],**copy.deepcopy(a['model_kwargs']))
   model.train(**copy.deepcopy(a['training_kwargs']),**common);capture('reference',model,a['seed'],a['training_kwargs']['max_epochs'])
 elif q['operation']=='QUERY':
  if q['family'] not in ['SCVI','SCANVI']:raise ValueError('Unsupported original query family')
  cls=SCVI if q['family']=='SCVI' else SCANVI
  # Original scArches loader applies its registry and strict module.load_state_dict.
  model=cls.load_query_data(d,request['reference_directory'],accelerator='cpu',device=1,inplace_subset_query_vars=False,**copy.deepcopy(q['load_query_kwargs']))
  model.train(**copy.deepcopy(q['training_kwargs']),**common);capture('query',model,q['seed'],q['training_kwargs']['max_epochs'])
 else:raise ValueError('Unsupported closed original operation')
 _io.write_json(out/'warnings.json',reported_warnings)
 _io.write_json(out/'execution.json',dict(_io.runtime_identity(out),schema_version=1,status='completed-original-model-execution-native-audit-required',scientific_request_sha256=_io.sha(request_path),stages=stages,model_analysis_completed=False))
 print('PASS fixed original CPU model execution;complete native admission required',flush=True)
except BaseException as error:
 unavailable=phase=='dependency' and isinstance(error,(_io.UnavailableProfile,ModuleNotFoundError))
 _io.write_json(out/'execution-failure.json',dict(status='SKIP3-unavailable-exact-profile' if unavailable else 'FAIL2-original-execution-retained-no-admitted-analysis',phase=phase,error=repr(error),traceback=traceback.format_exc(),warnings=reported_warnings,required_action='Retain this failed attempt. Supply the exact permitted profile or correct the named input/model cause and rerun to a NEW output path. Do not use partial outputs as an admitted analysis.'))
 if unavailable:raise SystemExit(3)
 raise
