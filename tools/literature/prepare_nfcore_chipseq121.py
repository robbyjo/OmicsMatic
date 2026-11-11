# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare exact source/data bindings and typed nf-core1.2.1 requests; no workflow execution or installation."""
import argparse,hashlib,json,os,shutil,sys,traceback
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
class Number(str):pass
def unique_object(pairs):
 result={}
 for key,value in pairs:
  if key in result:raise ValueError('Duplicate JSON key: '+key)
  result[key]=value
 return result
def encode(value):
 if isinstance(value,Number):return str(value)
 if isinstance(value,dict):return '{'+','.join(json.dumps(k,ensure_ascii=False)+':'+encode(v) for k,v in value.items())+'}'
 if isinstance(value,list):return '['+','.join(encode(v) for v in value)+']'
 return json.dumps(value,ensure_ascii=False,allow_nan=False)
def save(path,value):path.write_text(encode(value)+'\n',encoding='utf8',newline='\n')
def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):n+=len(b);h.update(b)
 return dict(bytes=n,sha256=h.hexdigest())
def absolute(value):
 p=Path(value);assert p.is_absolute() and str(p)==str(p.resolve()),'Require normalized canonical absolute paths';return p
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--workflow',required=True,type=Path);p.add_argument('--data-root',required=True,type=Path);p.add_argument('--design',required=True,type=Path);p.add_argument('--fasta',required=True,type=Path);p.add_argument('--gtf',required=True,type=Path);p.add_argument('--macs-genome-size',required=True);p.add_argument('--container-home',required=True,type=Path);p.add_argument('--image',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--run-root',required=True,type=Path);p.add_argument('--max-cpus',type=int,required=True);p.add_argument('--executor-memory-bytes',type=int,required=True);p.add_argument('--max-memory',required=True);p.add_argument('--max-time',required=True);p.add_argument('--parameters',type=Path);p.add_argument('--limits',type=Path);p.add_argument('--resource',action='append',default=[])
 a=p.parse_args()
 if sys.platform!='linux':print('SKIP3 analysis preparation requires Linux path semantics; run this preparer with Linux Python inside WSL. Original data and IDs are unchanged.',file=sys.stderr);return 3
 out=absolute(a.out);assert not out.exists() and out.parent.is_dir();out.mkdir();state=dict(status='FAIL2_PREPARATION_NO_RESULT',scientific_analysis_executed=False)
 try:
  run=absolute(a.run_root);assert not run.exists() and not run.is_relative_to(out) and not out.is_relative_to(run);base=absolute(a.data_root);workflow=absolute(a.workflow);assert base.is_dir() and workflow.is_dir();assert 1<=a.max_cpus<=1024
  materialized=out/'materialized';materialized.mkdir();installed=materialized/'workflow';installed.mkdir();lock_path=ROOT/'src/main/resources/org/jlinalg/omics/nfcore/nfcore-source-lock.json';lock=json.loads(lock_path.read_bytes())['pipelines']['chipseq-1.2.1'];originals={}
  for name,identity in lock['files'].items():
   source=workflow/name;assert source.is_file() and pin(source)==identity,('Original source differs',name);dest=installed/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest);assert pin(dest)==identity;originals[str(source)]=identity
  data=materialized/'data';data.mkdir();bindings={};copied={}
  def stage(path):
   path=absolute(path);assert path.is_relative_to(base) and path.is_file(),('Input must be a complete regular file under explicit data root',path)
   if path in copied:return copied[path]
   identity=pin(path);relative=path.relative_to(base);dest=data/relative;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest);assert pin(dest)==identity and pin(path)==identity;copied[path]=dest;originals[str(path)]=identity;return dest
  def captured(path):return str(run/'engine/workspace/resources/r0/materialized'/path.relative_to(materialized))
  design=absolute(a.design);assert design.is_file() and design.is_relative_to(base);raw=design.read_bytes();(materialized/'original-design.csv').write_bytes(raw);originals[str(design)]=pin(design)
  lines=raw.decode('utf8').splitlines();assert lines and lines[0]=='group,replicate,fastq_1,fastq_2,antibody,control','Require exact original ChIP-seq1.2.1 design schema';derived=[lines[0]];path_rows=[]
  for line in lines[1:]:
   if not line or line.startswith('#'):derived.append(line);continue
   fields=line.split(',');assert len(fields)==6,'Original checker requires six literal comma-separated cells';changed=list(fields);pair=[]
   for i in [2,3]:
    if not fields[i]:continue
    source=Path(fields[i]);source=source if source.is_absolute() else base/source;source=source.resolve();dest=stage(source);changed[i]=captured(dest);pair.append(source)
   assert pair and (len(pair)!=2 or not os.path.samefile(pair[0],pair[1])),'Original checked mates cannot refer to one physical source file'
   assert [fields[i] for i in [0,1,4,5]]==[changed[i] for i in [0,1,4,5]];derived.append(','.join(changed));path_rows.append(dict(original=fields,derived=changed,relative_read_base=str(base)))
  design_out=materialized/'design.csv';design_out.write_text('\n'.join(derived)+'\n',encoding='utf8',newline='\n');fasta=stage(a.fasta);gtf=stage(a.gtf)
  external=materialized/'external-configs';external.mkdir();(external/'nfcore_custom.config').write_text('// No institutional overrides selected; original scientific defaults remain in force.\n',encoding='utf8',newline='\n')
  resources={name:dict(path=str(path)) for name,path in dict(bundle=materialized,design=design_out,fasta=fasta,gtf=gtf,external_configs=external).items()}
  for item in a.resource:
   name,path=item.split('=',1);assert name not in resources and name!='workflow';source=absolute(Path(path));assert source.is_relative_to(base)
   if source.is_file():dest=stage(source)
   else:
    assert source.is_dir();dest=data/source.relative_to(base);dest.mkdir(parents=True,exist_ok=True)
    for member in source.rglob('*'):
     assert not member.is_symlink(),'Explicit directory resources must have stable unlinked members'
     if member.is_file():stage(member)
   resources[name]=dict(path=str(dest))
  parameters=json.loads(a.parameters.read_text(encoding='utf8'),parse_float=Number,object_pairs_hook=unique_object) if a.parameters else {};assert isinstance(parameters,dict)
  schema=json.loads((installed/'nextflow_schema.json').read_bytes());known={key for definition in schema['definitions'].values() for key in definition.get('properties',{})};assert not set(parameters)-known,('Unknown original parameters',sorted(set(parameters)-known))
  if a.parameters:
   original=absolute(a.parameters);originals[str(original)]=pin(original);shutil.copyfile(original,out/'original-parameters.json')
  for flag in ['single_end','narrow_peak']:
   if flag in parameters:assert isinstance(parameters[flag],bool),('Require original typed boolean',flag)
  reserved={'input','fasta','gtf','custom_config_base','outdir','tracedir','macs_gsize','max_cpus','max_memory','max_time'};assert not reserved.intersection(parameters),'Use the explicit protected path/genome/execution options'
  try:gsize=json.loads(a.macs_genome_size,parse_float=Number)
  except json.JSONDecodeError:gsize=a.macs_genome_size
  parameters.update(input={'$input':'design'},fasta={'$input':'fasta'},gtf={'$input':'gtf'},custom_config_base={'$input':'external_configs'},outdir={'$output':''},tracedir={'$output':'pipeline_info'},macs_gsize=gsize,max_cpus=a.max_cpus,max_memory=a.max_memory,max_time=a.max_time)
  image=absolute(a.image);home=absolute(a.container_home);quote=lambda x:"'"+str(x).replace('\\','\\\\').replace("'","\\'")+"'"
  overlay="process.executor = 'local'\nprocess.container = "+quote(image)+"\nexecutor.cpus = "+str(a.max_cpus)+"\nexecutor.memory = '"+str(a.executor_memory_bytes)+" B'\nsingularity.cacheDir = "+quote(run/'engine/workspace/execution/container-cache')+"\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\n"
  (installed/'runtime').mkdir();(installed/'runtime/host.config').write_text(overlay,encoding='utf8',newline='\n')
  limits=dict(wall_seconds=7200,cpu_seconds=7200,address_space_bytes=68719476736,workspace_bytes=274877906944,processes=512,threads=16384,sampled_rss_bytes=34359738368,java_heap_mib=2048,java_cpus=a.max_cpus,poll_millis=500,metadata_bytes=67108864)
  if a.limits:
   original=absolute(a.limits);originals[str(original)]=pin(original);shutil.copyfile(original,out/'original-limits.json');limits.update(json.loads(original.read_text(encoding='utf8'),object_pairs_hook=unique_object))
  recipe=dict(schema_version=1,engine_profile='linux-x86_64-nextflow-21.04.1',workflow=dict(path=str(installed),entrypoint='main.nf',configs=['nextflow.config','runtime/host.config'],profiles=['singularity']),resources=resources,parameters=parameters,required_tools=[dict(name='singularity',path=str(home/'bin/singularity'))],outputs=[dict(path=name,kind='directory',allow_empty=False) for name in ['pipeline_info','fastqc','trim_galore','bwa','genome','multiqc','igv']],require_tasks=True,limits=limits)
  recipe_path=out/'engine-recipe.json';save(recipe_path,recipe);request=dict(schema_version=1,pipeline_profile='nfcore-chipseq-1.2.1',engine_recipe=str(recipe_path),container_home=str(home),image=str(image),peak_type='narrowPeak' if parameters.get('narrow_peak',False) else 'broadPeak',read_mode='single' if parameters.get('single_end',False) else 'paired',minimum_replicates=parameters.get('min_reps_consensus',1),executor_cpus=a.max_cpus,executor_memory_bytes=a.executor_memory_bytes);save(out/'request.json',request);save(out/'path-bindings.json',path_rows)
  assert all(pin(Path(name))==identity for name,identity in originals.items());state.update(status='PASS_PREPARED_SOURCE_DATA_BINDINGS_ONLY',request=pin(out/'request.json'),recipe=pin(recipe_path),original_files_unchanged=originals,source_version='1.2.1',author_engine_inferred=False,scientific_ID_cells_changed=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 state['required_action']='Preparation only. Run the original source workflow through the packaged adapter with its exact managed Nextflow runtime/current host report and pinned image. Require a completed result and successful verify. Preserve originals and failed preparations; resolve named causes and use NEW paths for changed inputs or requests.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Path cells were explicitly bound; original non-path design cells and complete input bytes remain retained.')+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original source, input or identity preservation checks')
 raise SystemExit(main())
