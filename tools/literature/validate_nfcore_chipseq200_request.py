# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual v2 schema, complete source/read bindings and explicit typed request refusals; no analysis."""
import argparse,copy,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
from prepare_nfcore_chipseq121 import Number,encode,unique_object
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--prepared-request',required=True);p.add_argument('--engine-root',required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_TYPED_CHIPSEQ200_REQUEST_CONTROLS',scientific_analysis_executed=False,new_tool_credit=0)
 try:
  def read_linux(path):
   r=subprocess.run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/cat',path],capture_output=True,timeout=30);assert r.returncode==0,r.stderr;return r.stdout
  raw=read_linux(a.prepared_request);request=json.loads(raw);recipe_raw=read_linux(request['engine_recipe']);recipe=json.loads(recipe_raw,parse_float=Number,object_pairs_hook=unique_object);(out/'original-request.json').write_bytes(raw);(out/'original-recipe.json').write_bytes(recipe_raw);helper=ROOT/'tools/literature/java/NfcoreChipseq200RequestControl.java';before=pin(helper);checks=[]
  cases=[('unchanged',0,''),('valid_numeric_enum',0,''),('unknown_parameter',2,'Unknown original'),('literal_external_input',2,'declared $input'),('unbound_input_alias',2,'Undeclared input'),('input_traversal',2,'Unsafe'),('string_boolean',2,'typed scalar'),('fractional_integer',2,'typed scalar'),('invalid_numeric_enum',2,'Unknown original parameter choice'),('unsupported_igenome',3,'outside this explicit local profile'),('notification_without_contract',3,'outside this explicit local profile'),('skip_original_branch',3,'complete selected original'),('floating_conda',3,'floating Conda solve'),('unknown_publication_mode',2,'Unknown original parameter choice'),('linked_publication',3,'publish_dir_mode=copy'),('peak_mode_mismatch',2,'narrow_peak selection'),('consensus_minimum_mismatch',2,'source replicate minimum'),('wrong_engine',3,'exact Nextflow22.10.6'),('other_pipeline_profile',3,'family/version'),('literal_output_path',2,'output/trace paths'),('executor_CPU_mismatch',2,'Runtime-only overlay'),('executor_memory_mismatch',2,'Runtime-only overlay'),('external_samplesheet_read',2,'outside the complete declared future capture'),('scientific_ID_rewrite',2,'would change scientific identifier'),('missing_captured_read',2,''),('duplicate_physical_mates',2,'identical physical input identity')]
  for compiler in ['21','25']:
   jar=BASE/f'chipseq200-workflow-build{compiler}-2/nfcore-chipseq200.jar';artifact=pin(jar);java=JAVA if compiler=='21' else JAVA25;classes=out/('helper'+compiler);classes.mkdir();command=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(command,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for name,expected,fragment in cases:
    folder=out/(compiler+'-'+name);folder.mkdir();q=copy.deepcopy(request);v=copy.deepcopy(recipe);params=v['parameters']
    if name=='valid_numeric_enum':params['read_length']=50
    elif name=='unknown_parameter':params['macs_gsie']=Number('1.2e7')
    elif name=='literal_external_input':params['blacklist']='/outside/capture/blacklist.bed'
    elif name=='unbound_input_alias':params['blacklist']={'$input':'UNDECLARED'}
    elif name=='input_traversal':params['blacklist']={'$input':'bundle','path':'../outside.bed'}
    elif name=='string_boolean':params['keep_dups']='false'
    elif name=='fractional_integer':params['fingerprint_bins']=Number('500000.5')
    elif name=='invalid_numeric_enum':params['read_length']=51
    elif name=='unsupported_igenome':params['genome']='GRCh38'
    elif name=='notification_without_contract':params['email']='unrequested@example.org'
    elif name=='skip_original_branch':params['skip_preseq']=True
    elif name=='floating_conda':params['enable_conda']=True
    elif name=='unknown_publication_mode':params['publish_dir_mode']='invented'
    elif name=='linked_publication':params['publish_dir_mode']='symlink'
    elif name=='peak_mode_mismatch':q['peak_type']='narrowPeak'
    elif name=='consensus_minimum_mismatch':q['minimum_replicates']=2
    elif name=='wrong_engine':v['engine_profile']='linux-x86_64-nextflow-24.04.4'
    elif name=='other_pipeline_profile':q['pipeline_profile']='nfcore-chipseq-1.2.1'
    elif name=='literal_output_path':params['outdir']='/outside/output'
    elif name=='executor_CPU_mismatch':q['executor_cpus']=5
    elif name=='executor_memory_mismatch':q['executor_memory_bytes']+=1
    elif name in ['external_samplesheet_read','scientific_ID_rewrite','missing_captured_read','duplicate_physical_mates']:
     clone=folder/'materialized';clone.mkdir();code='from pathlib import Path;import json,shutil; v=json.loads('+repr(json.dumps(v,default=str))+'); dest=Path('+repr(linux(clone))+'); old=Path(v["resources"]["bundle"]["path"]); shutil.copytree(v["workflow"]["path"],dest/"workflow"); print("COPIED")'
     # JSON floats use the source-preserving writer below; this preparation code only copies source files.
     r=subprocess.run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/python3','-c',code],capture_output=True,timeout=60);assert r.returncode==0,r.stderr;v['workflow']['path']=linux(clone/'workflow')
     for key in ['design','fasta','gtf']:
      original=v['resources'][key]['path'];target=clone/('input-'+key+Path(original).suffix);target.write_bytes(read_linux(original));v['resources'][key]['path']=linux(target)
     external=clone/'external';external.mkdir();(external/'nfcore_custom.config').write_bytes(read_linux(v['resources']['external_configs']['path']+'/nfcore_custom.config'));v['resources']['external_configs']['path']=linux(external);v['resources']['bundle']['path']=linux(clone);design=clone/('input-design'+Path(recipe['resources']['design']['path']).suffix);lines=design.read_text(encoding='utf8').splitlines();selected_row=min(range(1,len(lines)),key=lambda i:lines[i].split(',')[0]);f=lines[selected_row].split(',')
     if name=='external_samplesheet_read':f[1]='/unbound/original.fastq.gz'
     elif name=='scientific_ID_rewrite':f[0]='"'+f[0]+'"'
     elif name=='missing_captured_read':f[1]=a.engine_root+'/workspace/resources/r0/materialized/MISSING.fastq.gz'
     else:
      prefix=a.engine_root+'/workspace/resources/r0/materialized/';assert f[1].startswith(prefix);relative=f[1][len(prefix):];target=clone/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(read_linux(recipe['resources']['bundle']['path']+'/'+relative));f[2]=f[1]
     lines[selected_row]=','.join(f);design.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
    recipe_path=folder/'engine-recipe.json';recipe_path.write_text(encode(v)+'\n',encoding='utf8',newline='\n');q['engine_recipe']=linux(recipe_path);save(folder/'request.json',q);command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.NfcoreChipseq200RequestControl',linux(folder/'request.json'),a.engine_root];r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(folder/'stdout').write_bytes(r.stdout);(folder/'stderr').write_bytes(r.stderr);save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==expected and (not fragment or fragment in r.stderr.decode('utf8')),(name,expected,r.returncode,r.stderr);checks.append(dict(compiler=compiler,case=name,exit_code=expected,passed=True));print('PASS',compiler,name,flush=True)
   assert pin(jar)==artifact
  assert read_linux(a.prepared_request)==raw and read_linux(request['engine_recipe'])==recipe_raw and pin(helper)==before;state.update(status='PASS_COMPLETE_TYPED_CHIPSEQ200_REQUEST_ADMISSION_CONTROLS_ONLY',checks=checks,helper=before,original_request_and_recipe_unchanged=True,source_workflow_not_modified=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/request assertions')
 raise SystemExit(main())
