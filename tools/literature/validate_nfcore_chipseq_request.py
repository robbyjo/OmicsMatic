# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check real pinned source request admission and deliberate request discrepancies without analysis."""
import argparse,copy,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
from prepare_nfcore_chipseq121 import Number,encode,unique_object

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);p.add_argument('--prepared-request',required=True);p.add_argument('--engine-root',required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_TYPED_REQUEST_CONTROLS',scientific_analysis_executed=False,new_tool_credit=0)
 try:
  def read_linux(path):
   r=subprocess.run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/cat',path],capture_output=True,timeout=30);assert r.returncode==0,r.stderr;return r.stdout
  raw=read_linux(a.prepared_request);request=json.loads(raw);recipe_raw=read_linux(request['engine_recipe']);recipe=json.loads(recipe_raw,parse_float=Number,object_pairs_hook=unique_object);(out/'original-request.json').write_bytes(raw);(out/'original-recipe.json').write_bytes(recipe_raw);before={c:pin(Path(getattr(a,'jar'+c)).resolve()) for c in ['21','25']};helper=ROOT/'tools/literature/java/NfcoreChipseqRequestControl.java';helper_pin=pin(helper)
  cases=[('unchanged',0,''),('unknown_parameter',2,'Unknown original'),('literal_external_input',2,'declared $input'),('unbound_input_alias',2,'Undeclared input'),('input_traversal',2,'Unsafe'),('string_boolean',2,'typed scalar'),('fractional_integer',2,'typed scalar'),('unsupported_igenome',3,'outside this explicit local profile'),('notification_without_contract',3,'outside this explicit local profile'),('skip_original_branch',3,'complete selected original'),('unknown_publication_mode',2,'Unknown original parameter choice'),('linked_publication',3,'publish_dir_mode=copy'),('peak_mode_mismatch',2,'narrow_peak selection'),('consensus_minimum_mismatch',2,'source replicate minimum'),('wrong_engine',3,'exact Nextflow21.04.1'),('other_pipeline_profile',3,'family/version'),('literal_output_path',2,'output/trace paths')];checks=[]
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();java=JAVA if compiler=='21' else JAVA25;classes=out/('helper'+compiler);classes.mkdir();command=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(command,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for name,expected,fragment in cases:
    folder=out/(compiler+'-'+name);folder.mkdir();q=copy.deepcopy(request);v=copy.deepcopy(recipe);params=v['parameters']
    if name=='unknown_parameter':params['macs_gsie']='hs'
    elif name=='literal_external_input':params['blacklist']='/outside/capture/blacklist.bed'
    elif name=='unbound_input_alias':params['blacklist']={'$input':'UNDECLARED'}
    elif name=='input_traversal':params['blacklist']={'$input':'bundle','path':'../outside.bed'}
    elif name=='string_boolean':params['keep_dups']='false'
    elif name=='fractional_integer':params['fingerprint_bins']=Number('500000.5')
    elif name=='unsupported_igenome':params['genome']='GRCh38'
    elif name=='notification_without_contract':params['email']='unrequested@example.org'
    elif name=='skip_original_branch':params['skip_preseq']=True
    elif name=='unknown_publication_mode':params['publish_dir_mode']='invented'
    elif name=='linked_publication':params['publish_dir_mode']='symlink'
    elif name=='peak_mode_mismatch':q['peak_type']='narrowPeak'
    elif name=='consensus_minimum_mismatch':q['minimum_replicates']=2
    elif name=='wrong_engine':v['engine_profile']='linux-x86_64-nextflow-24.04.4'
    elif name=='other_pipeline_profile':q['pipeline_profile']='nfcore-chipseq-2.0.0'
    elif name=='literal_output_path':params['outdir']='/outside/output'
    recipe_path=folder/'engine-recipe.json';recipe_path.write_text(encode(v)+'\n',encoding='utf8',newline='\n');q['engine_recipe']=linux(recipe_path);save(folder/'request.json',q);command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx256m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.NfcoreChipseqRequestControl',linux(folder/'request.json'),a.engine_root]
    r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=90);(folder/'stdout').write_bytes(r.stdout);(folder/'stderr').write_bytes(r.stderr);save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==expected and (not fragment or fragment in r.stderr.decode('utf8')),(name,expected,r.returncode,r.stderr);checks.append(dict(compiler=compiler,case=name,exit_code=expected,passed=True))
  assert read_linux(a.prepared_request)==raw and read_linux(request['engine_recipe'])==recipe_raw and pin(helper)==helper_pin and all(pin(Path(getattr(a,'jar'+c)).resolve())==v for c,v in before.items());state.update(status='PASS_TYPED_REQUEST_ADMISSION_CONTROLS_ONLY',checks=checks,artifacts=before,helper=helper_pin,original_request_and_recipe_unchanged=True,source_workflow_not_modified=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/request assertions')
 raise SystemExit(main())
