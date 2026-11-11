# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a typed original ChIP-seq2.0.0 workflow request and complete captured inputs; no installation or analysis."""
import argparse,json,os,shutil,sys,traceback
from pathlib import Path
from prepare_nfcore_chipseq121 import ROOT,Number,encode,save,pin,absolute,unique_object

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['workflow','data-root','design','fasta','gtf','container-home','image-set','out','run-root']:p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--macs-genome-size',required=True);p.add_argument('--max-cpus',type=int,required=True);p.add_argument('--executor-memory-bytes',type=int,required=True);p.add_argument('--max-memory',required=True);p.add_argument('--max-time',required=True);p.add_argument('--resource',action='append',default=[]);p.add_argument('--parameters',type=Path);p.add_argument('--limits',type=Path);a=p.parse_args()
 if sys.platform!='linux':print('SKIP3: source/data preparation requires Linux path semantics, including WSL. Analysis skipped.',file=sys.stderr);return 3
 out=absolute(a.out);assert not out.exists() and out.parent.is_dir();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_PREPARATION_NO_RESULT',scientific_analysis_executed=False,new_tool_credit=0)
 try:
  run=absolute(a.run_root);base=absolute(a.data_root);workflow=absolute(a.workflow);home=absolute(a.container_home);assert not run.exists() and not run.is_relative_to(out) and not out.is_relative_to(run) and base.is_dir();materialized=out/'materialized';materialized.mkdir();installed=materialized/'workflow';installed.mkdir();lock=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/nfcore/nfcore-source-lock.json').read_bytes())['pipelines']['chipseq-2.0.0'];guards={}
  for name,expected in lock['files'].items():
   source=workflow/name;assert pin(source)==expected,('Original source differs',name);dest=installed/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest);assert pin(dest)==expected;guards[str(source)]=expected
  data=materialized/'data';data.mkdir();copied={}
  def stage(path):
   path=absolute(path);assert path.is_relative_to(base) and path.is_file(),('Complete input outside explicit data root',path)
   if path in copied:return copied[path]
   v=pin(path);dest=data/path.relative_to(base);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest);assert pin(dest)==v and pin(path)==v;copied[path]=dest;guards[str(path)]=v;return dest
  def captured(path):return str(run/'engine/workspace/resources/r0/materialized'/path.relative_to(materialized))
  design=absolute(a.design);assert design.is_relative_to(base);raw=design.read_bytes();guards[str(design)]=pin(design);(materialized/'original-samplesheet.csv').write_bytes(raw);lines=raw.decode('utf8').splitlines();assert lines and lines[0]=='sample,fastq_1,fastq_2,antibody,control','Require exact original 2.0 samplesheet header';derived=[lines[0]];bindings=[]
  for line in lines[1:]:
   fields=line.split(',');assert len(fields)==5 and all(x==x.strip().strip('"') for x in fields),'Source-trimmed/quoted cells need an explicit normalization contract; original IDs are not silently repaired';assert fields[0] and all(' ' not in fields[i] for i in [0,3,4]),'Source whitespace-to-underscore ID changes are not silently applied';changed=list(fields);pair=[]
   for i in [1,2]:
    if not fields[i]:continue
    path=Path(fields[i]);path=(path if path.is_absolute() else base/path).resolve();dest=stage(path);changed[i]=captured(dest);pair.append(path)
   assert pair and (len(pair)!=2 or not os.path.samefile(*pair)),'Original mates must have distinct physical file identity';assert [fields[i] for i in [0,3,4]]==[changed[i] for i in [0,3,4]];derived.append(','.join(changed));bindings.append(dict(original=fields,derived=changed,relative_read_base=str(base)))
  bound=materialized/'samplesheet.csv';bound.write_text('\n'.join(derived)+'\n',encoding='utf8',newline='\n');fasta=stage(a.fasta);gtf=stage(a.gtf);external=materialized/'external-configs';external.mkdir();(external/'nfcore_custom.config').write_text('// No institutional overrides selected; original scientific defaults remain in force.\n',encoding='utf8',newline='\n')
  assert 1<=a.max_cpus<=1024 and 16777216<=a.executor_memory_bytes<=1099511627776
  resources={name:dict(path=str(path)) for name,path in dict(bundle=materialized,design=bound,fasta=fasta,gtf=gtf,external_configs=external).items()}
  for item in a.resource:
   name,path=item.split('=',1);assert name not in resources and name!='workflow';original=absolute(Path(path));assert original.is_relative_to(base)
   if original.is_file():dest=stage(original)
   else:
    assert original.is_dir();dest=data/original.relative_to(base);dest.mkdir(parents=True,exist_ok=True)
    for member in original.rglob('*'):
     assert not member.is_symlink(),'Explicit directory resources must have stable unlinked members'
     if member.is_dir():(data/member.relative_to(base)).mkdir(parents=True,exist_ok=True)
     elif member.is_file():stage(member)
     else:raise RuntimeError('Nonregular explicit resource member: '+str(member))
   resources[name]=dict(path=str(dest))
  image_file=absolute(a.image_set);declaration=json.loads(image_file.read_bytes());guards[str(image_file)]=pin(image_file);assert set(declaration)=={'schema_version','images'} and declaration['schema_version']==1;profile_path=ROOT/'src/main/resources/org/jlinalg/omics/nfcore-chipseq200/images.json';profile=json.loads(profile_path.read_bytes());assert set(profile['images'])==set(declaration['images']);shutil.copyfile(image_file,out/'image-set.json');quote=lambda value:"'"+str(value).replace('\\','\\\\').replace("'","\\'")+"'";overlay="process.executor = 'local'\nexecutor.cpus = "+str(a.max_cpus)+"\nexecutor.memory = '"+str(a.executor_memory_bytes)+" B'\nsingularity.cacheDir = "+quote(run/'engine/workspace/execution/container-cache')+"\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nprocess {\n"
  for uri,row in profile['images'].items():
   assert pin(absolute(Path(declaration['images'][uri])))==row['pin'],('Complete source image differs',uri)
   guards[str(absolute(Path(declaration['images'][uri])))]=row['pin']
   for name in row['process_names']:overlay+='  withName: '+quote(name)+' { container = '+quote(declaration['images'][uri])+' }\n'
  overlay+='}\n';(installed/'runtime').mkdir();(installed/'runtime/host.config').write_text(overlay,encoding='utf8',newline='\n');params=json.loads(a.parameters.read_text(encoding='utf8'),parse_float=Number,object_pairs_hook=unique_object) if a.parameters else {};assert isinstance(params,dict);schema=json.loads((installed/'nextflow_schema.json').read_bytes());known={n for d in schema['definitions'].values() for n in d.get('properties',{})};assert not set(params)-known,('Unknown original parameters',sorted(set(params)-known));reserved={'input','fasta','gtf','custom_config_base','outdir','tracedir','macs_gsize','max_cpus','max_memory','max_time'};assert not reserved.intersection(params)
  if a.parameters:original=absolute(a.parameters);guards[str(original)]=pin(original);shutil.copyfile(original,out/'original-parameters.json')
  try:gsize=json.loads(a.macs_genome_size,parse_float=Number)
  except json.JSONDecodeError:gsize=a.macs_genome_size
  params.update(input={'$input':'design'},fasta={'$input':'fasta'},gtf={'$input':'gtf'},custom_config_base={'$input':'external_configs'},outdir={'$output':''},tracedir={'$output':'pipeline_info'},macs_gsize=gsize,max_cpus=a.max_cpus,max_memory=a.max_memory,max_time=a.max_time)
  limits=dict(wall_seconds=14400,cpu_seconds=14400,address_space_bytes=68719476736,workspace_bytes=274877906944,processes=512,threads=16384,sampled_rss_bytes=34359738368,java_heap_mib=2048,java_cpus=a.max_cpus,poll_millis=500,metadata_bytes=67108864)
  if a.limits:original=absolute(a.limits);guards[str(original)]=pin(original);shutil.copyfile(original,out/'original-limits.json');limits.update(json.loads(original.read_text(encoding='utf8'),object_pairs_hook=unique_object))
  recipe=dict(schema_version=1,engine_profile='linux-x86_64-nextflow-22.10.6',workflow=dict(path=str(installed),entrypoint='main.nf',configs=['nextflow.config','runtime/host.config'],profiles=['singularity']),resources=resources,parameters=params,required_tools=[dict(name='singularity',path=str(home/'bin/singularity'))],outputs=[dict(path=n,kind='directory',allow_empty=False) for n in ['pipeline_info','fastqc','trimgalore',params.get('aligner','bwa'),'genome','multiqc','igv']],require_tasks=True,limits=limits);save(out/'engine-recipe.json',recipe);request=dict(schema_version=1,pipeline_profile='nfcore-chipseq-2.0.0',engine_recipe=str(out/'engine-recipe.json'),container_home=str(home),image_set=str(out/'image-set.json'),peak_type='narrowPeak' if params.get('narrow_peak',False) else 'broadPeak',minimum_replicates=params.get('min_reps_consensus',1),executor_cpus=a.max_cpus,executor_memory_bytes=a.executor_memory_bytes);save(out/'request.json',request);save(out/'path-bindings.json',bindings);assert all(pin(Path(n))==v for n,v in guards.items());state.update(status='PASS_CHIPSEQ200_SOURCE_DATA_PREPARATION_ONLY',original_files_unchanged=guards,recipe=pin(out/'engine-recipe.json'),images=pin(out/'image-set.json'),source_files=203,source_images=33,typed_request=pin(out/'request.json'),author_engine_inferred=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 state['required_action']='Preparation only; original source execution, complete task/publication checks and version-specific scientific output contracts remain required. Install/download exact prerequisites only where permitted; otherwise skip dependent analysis. Preserve originals and all failed preparations; resolve named causes and use NEW paths. Hashes establish integrity, not author history or biological validity.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Complete original non-path cells and input byte identities retained.')+'\n\n'+state['required_action']+'\n',encoding='utf8');print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/input/runtime preparation checks')
 raise SystemExit(main())
