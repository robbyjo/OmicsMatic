"""Inspect only exact reviewed model files selected by the native caller; pickle may execute code."""
from pathlib import Path
import collections,hashlib,importlib.util,inspect,json,math,os,sys,traceback
_spec=importlib.util.spec_from_file_location('omicsmatic_scvi_provider_common',Path(__file__).with_name('scvi_provider_common.py'))
common=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(common)
out=Path(sys.argv[2]);out.mkdir(exist_ok=False)
class UnsupportedModel(RuntimeError):pass
try:
 common.versions()
 import numpy as np,torch,scvi
 from scvi.module import VAE,SCANVAE
 from scvi.external.sysvi import SysVAE
 torch.set_num_threads(1);torch.set_num_interop_threads(1)
 if torch.version.cuda is not None or torch.cuda.is_available():raise common.UnavailableProfile('CPU-only profile required')
 request=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'));limits=request['limits'];models=request['models']
 if not models or len(models)>limits['models']:raise RuntimeError('Complete model population exceeds capacity')
 output_records=[];total_bytes=0;total_tensors=0
 for index,entry in enumerate(models):
  if entry['id']!='model_'+str(index):raise RuntimeError('Unexpected native model ordinal')
  source=Path(entry['path']);source_hash=common.sha(source)
  model=torch.load(source,map_location='cpu',weights_only=False)
  if not isinstance(model,dict) or set(model)!=set(['model_state_dict','var_names','attr_dict']):raise UnsupportedModel('Requires the original scVI single-file save format;no legacy conversion is inferred')
  attrs=model['attr_dict'];registry=attrs['registry_'];family=registry['model_name'];release=registry['scvi_version']
  if family not in ['SCVI','SCANVI','SysVI']:raise UnsupportedModel('Observed original model family '+str(family)+' is outside this declared profile')
  if release!='1.3.0':raise UnsupportedModel('Observed saved release '+str(release)+' is not substituted by1.3.0')
  target=out/entry['id'];target.mkdir();descriptors=[];nodes=[0]
  def bound(depth):
   nodes[0]+=1
   if depth>64 or nodes[0]>limits['metadata_nodes']:raise RuntimeError('Complete model metadata exceeds retained/node capacity')
  def scalar(value):
   if isinstance(value,(str,np.str_)):return dict(kind='string',value=str(value))
   if isinstance(value,(bool,np.bool_)):return dict(kind='boolean',value='true' if bool(value) else 'false')
   if isinstance(value,(int,np.integer)):return dict(kind='integer',value=str(int(value)))
   if isinstance(value,(float,np.floating)):
    if not math.isfinite(float(value)):raise UnsupportedModel('Nonfinite reference category')
    return dict(kind='float32' if isinstance(value,np.float32) else 'float64',value=float(value).hex())
   if value is None:return dict(kind='null',value='')
   raise UnsupportedModel('Reference category type needs explicit supported mapping: '+type(value).__module__+'.'+type(value).__qualname__)
  def plain(value,depth=0):
   bound(depth)
   if value is None or isinstance(value,(str,bool,int)):return value
   if isinstance(value,np.generic):return plain(value.item(),depth+1)
   if isinstance(value,float):
    if not math.isfinite(value):raise RuntimeError('Nonfinite saved metadata')
    return value
   if isinstance(value,np.ndarray):return [plain(v,depth+1) for v in value.tolist()]
   if isinstance(value,(list,tuple)):return [plain(v,depth+1) for v in value]
   if isinstance(value,dict):
    if not all(isinstance(k,str) for k in value):raise UnsupportedModel('Nonstring saved parameter keys')
    return {k:plain(v,depth+1) for k,v in value.items()}
   if isinstance(value,type) or callable(value):return dict(python_reference=value.__module__+'.'+value.__qualname__)
   if type(value).__module__.startswith(('torch.distributions.constraints','pyro.distributions.constraints')):return dict(python_type=type(value).__module__+'.'+type(value).__qualname__,attributes=plain(vars(value),depth+1))
   raise UnsupportedModel('Saved parameter object needs an explicit schema: '+type(value).__module__+'.'+type(value).__qualname__)
  genes=[str(v) if isinstance(v,(str,np.str_)) else (_ for _ in ()).throw(UnsupportedModel('Nonstring gene identifier')) for v in model['var_names']]
  if not genes or len(genes)>limits['genes']:raise RuntimeError('Complete saved gene population exceeds capacity')
  fields=registry['field_registries'];setup=registry['setup_args'];x=fields['X'];state_x=x['state_registry']
  batch=fields.get('batch',{}).get('state_registry',{});labels=fields.get('labels',{}).get('state_registry',{});cats=fields.get('extra_categorical_covs',{}).get('state_registry',{});cont=fields.get('extra_continuous_covs',{}).get('state_registry',{})
  def values(array):
   if len(array)>limits['categories']:raise RuntimeError('Complete category registry exceeds capacity')
   return [scalar(v) for v in array]
  categorical=[dict(key=str(k),values=values(cats['mappings'][k])) for k in cats.get('field_keys',[])]
  label_values=values(labels.get('categorical_mapping',[]));unlabeled=scalar(labels['unlabeled_category']) if 'unlabeled_category' in labels else None
  classes=[v for v in label_values if v!=unlabeled] if family=='SCANVI' else []
  init=attrs['init_params_'];merged={}
  module=SysVAE if family=='SysVI' else SCANVAE if family=='SCANVI' else VAE
  for cls in ([SysVAE] if family=='SysVI' else [VAE,module]):
   for key,param in inspect.signature(cls.__init__).parameters.items():
    if param.default is not inspect.Parameter.empty and key not in ['self','kwargs','vae_kwargs']:merged[key]=param.default
  if family=='SysVI':merged.update(prior='vamp',n_prior_components=5,pseudoinputs_data_indices=None)
  merged.update(init.get('non_kwargs',{}))
  for group in init.get('kwargs',{}).values():
   if not isinstance(group,dict):raise UnsupportedModel('Unexpected saved kwargs group')
   merged.update(group)
  architectural_keys=['n_hidden','n_latent','n_layers','dropout_rate','dispersion','gene_likelihood','latent_distribution','log_variational','encode_covariates','deeply_inject_covariates','use_batch_norm','use_layer_norm','use_observed_lib_size','batch_representation','embed_categorical_covariates','out_var_mode','trainable_priors','prior','n_prior_components','pseudoinputs_data_indices','linear_classifier','classifier_parameters']
  architecture={k:plain(merged[k]) for k in architectural_keys if k in merged}
  binary=(target/'tensors.bin').open('xb')
  def tensor(value,path,scope):
   global total_bytes,total_tensors
   if value.layout!=torch.strided:raise UnsupportedModel('Non-dense saved state needs an explicit tensor schema')
   t=value.detach().cpu().contiguous();dtypes={torch.float32:'F32',torch.float64:'F64',torch.int64:'I64',torch.int32:'I32',torch.int16:'I16',torch.int8:'I8',torch.uint8:'U8',torch.bool:'BOOL',torch.float16:'F16',torch.bfloat16:'BF16'}
   if t.dtype not in dtypes:raise UnsupportedModel('Saved tensor precision needs an explicit schema: '+str(t.dtype))
   size=t.numel()*t.element_size();start=binary.tell();total_bytes+=size;total_tensors+=1
   if total_bytes>limits['state_bytes'] or total_tensors>limits['tensors']:raise RuntimeError('Complete saved tensor population exceeds capacity')
   raw=t.view(torch.uint16).numpy() if t.dtype==torch.bfloat16 else t.numpy();view=memoryview(raw).cast('B');h=hashlib.sha256()
   for pos in range(0,len(view),65536):part=view[pos:pos+65536];binary.write(part);h.update(part)
   record=dict(path=path,scope=scope,dtype=dtypes[t.dtype],shape=list(t.shape),elements=t.numel(),offset=start,bytes=size,sha256=h.hexdigest());descriptors.append(record);return dict(kind='tensor',index=len(descriptors)-1)
  def state(value,path,scope,depth=0):
   bound(depth)
   if isinstance(value,torch.Tensor):return tensor(value,path,scope)
   if isinstance(value,dict):
    if not all(isinstance(k,str) for k in value):raise UnsupportedModel('Nonstring state keys')
    return dict(kind='mapping',entries={k:state(v,path+[k],'pyro' if path==['model_state_dict'] and k=='pyro_param_store' else scope,depth+1) for k,v in value.items()})
   if isinstance(value,(list,tuple)):return dict(kind='sequence',sequence_kind='tuple' if isinstance(value,tuple) else 'list',items=[state(v,path+[str(i)],scope,depth+1) for i,v in enumerate(value)])
   if isinstance(value,torch.nn.Module):return dict(kind='module',python_type=type(value).__module__+'.'+type(value).__qualname__,state=state(value.state_dict(),path+['module_state'],'model',depth+1))
   return dict(kind='value',value=plain(value,depth+1))
  state_tree=state(model['model_state_dict'],['model_state_dict'],'model');binary.close()
  if source_hash!=common.sha(source):raise RuntimeError('Reviewed model bytes changed during original loading')
  metadata=dict(schema_version=1,status='completed-original-model-metadata-inspection-only',source_sha256=source_hash,saved_release=release,family='SYSVI' if family=='SysVI' else family,trained=bool(attrs.get('is_trained_',False)),genes=genes,registered_genes=[str(v) for v in state_x.get('column_names',[])],registered_n_vars=int(state_x.get('n_vars',x.get('summary_stats',{}).get('n_vars',-1))),registered_n_cells=int(state_x.get('n_obs',x.get('summary_stats',{}).get('n_cells',-1))),input_data_registry=plain(x['data_registry']),setup=plain(setup),batch=dict(key=setup.get('batch_key'),values=values(batch.get('categorical_mapping',[]))),labels=dict(key=setup.get('labels_key'),values=label_values,unlabeled=unlabeled,classes=classes),categorical=categorical,continuous=[str(v) for v in cont.get('columns',[])],init_params=plain(init),architecture=architecture,state_tree=state_tree,tensors=descriptors,tensor_file_bytes=(target/'tensors.bin').stat().st_size,model_analysis_completed=False)
  encoded=json.dumps(metadata,sort_keys=True,ensure_ascii=False,allow_nan=False).encode('utf-8')
  if len(encoded)>limits['metadata_bytes']:raise RuntimeError('Complete model metadata byte capacity exceeded')
  (target/'model.json').write_bytes(encoded+b'\n');output_records.append(dict(id=entry['id'],source_sha256=source_hash,metadata=entry['id']+'/model.json'))
 common.write_json(out/'inspection.json',dict(schema_version=1,status='completed-reviewed-model-metadata-only',models=output_records,runtime=common.runtime_identity(out),model_analysis_completed=False))
 print('PASS original reviewed-model metadata extraction;independent native semantic/tensor audit required',flush=True)
except BaseException as error:
 unsupported=isinstance(error,(UnsupportedModel,common.UnavailableProfile))
 common.write_json(out/'inspection-failure.json',dict(status='SKIP3-unsupported-model-capability' if unsupported else 'FAIL2-model-inspection-retained',error=repr(error),traceback=traceback.format_exc(),required_action='Retain these diagnostics. Supply an explicitly reviewed matching exact model/runtime and correct the named schema,precision,data orcapacity cause;rerun to a NEW output. No model analysis is completed.'))
 if unsupported:raise SystemExit(3)
 raise
