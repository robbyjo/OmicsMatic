# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete portable v2 results with both builds/three JVMs and deliberate integrity/scope faults."""
import argparse,copy,json,os,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--result',type=Path,required=True);a=p.parse_args();out=a.out.resolve();source=a.result.resolve();assert out.is_relative_to(BASE) and not out.exists() and not source.is_relative_to(out);out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_PORTABLE_BUNDLE_CONTROLS',new_tool_credit=0)
 try:
  before={n:pin(source/n) for n in ['manifest.json','COMPLETED.json','workflow.json','adapter.jar']};jars={c:BASE/f'chipseq200-workflow-build{c}-2/nfcore-chipseq200.jar' for c in ['21','25']};artifacts={c:pin(j) for c,j in jars.items()};checks=[]
  def invoke(label,path,compiler='21',runtime='windows21',expected=0):
   jar=jars[compiler]
   if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-XX:ActiveProcessorCount=4','-jar',linux(jar),'--verify',linux(path)]
   else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-XX:ActiveProcessorCount=4','-jar',str(jar),'--verify',str(path)]
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
   save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==expected,(label,r.returncode,(out/(label+'.stderr')).read_text(encoding='utf8')[-3000:]);checks.append(dict(case=label,compiler=compiler,runtime=runtime,expected_exit_code=expected,passed=True));print('PASS',label,flush=True)
  def linked(src,dst):os.link(src,dst);return dst
  def clone(destination):assert destination.is_relative_to(out) and not destination.exists();shutil.copytree(source,destination,copy_function=linked)
  def write(path,raw):
   assert path.resolve().is_relative_to(out) and not path.is_symlink();temp=path.with_name(path.name+'.control-new');assert not temp.exists();temp.write_bytes(raw);os.replace(temp,path)
  def put(path,value):write(path,(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False)+'\n').encode('utf8'))
  def load(path):return json.loads(path.read_bytes())
  def rehash(root,changed):
   manifest=load(root/'manifest.json')
   for name in changed:assert name in manifest['files'];manifest['files'][name]=pin(root/name)
   put(root/'manifest.json',manifest)
   for name in ['CANDIDATE.json','COMPLETED.json']:marker=load(root/name);marker['manifest']=pin(root/'manifest.json');put(root/name,marker)
  for compiler in jars:
   for runtime in ['windows21','windows25','linux21']:invoke('complete-'+compiler+'-'+runtime,source,compiler,runtime)
  run=out/'selected-run';(run/'results').mkdir(parents=True);clone(run/'results/000001');save(run/'latest.json',dict(attempt='000001',status='COMPLETED'))
  for compiler in jars:invoke('completed-latest-'+compiler,run,compiler)
  cases=['failed-latest','missing-marker','extra-member','raw-changed-audit','rehashed-engine-profile','rehashed-biological-scope','rehashed-missing-component','rehashed-component-engine','rehashed-peak-mode','rehashed-consensus-minimum','rehashed-executor-capacity','rehashed-image-binding','rehashed-QC-label','rehashed-QC-transform-tail','rehashed-QC-process-cleanup']
  for mode in cases:
   if mode=='failed-latest':put(run/'latest.json',dict(attempt='000002',status='FAIL2'));invoke(mode,run,expected=2);continue
   target=out/mode;clone(target);changed=[]
   if mode=='missing-marker':
    # Rename only the owned derivative hardlink. Original source bytes/entry remain intact.
    marker=target/'COMPLETED.json';assert marker.resolve().is_relative_to(out);marker.rename(target/'RETAINED_COMPLETION.control');invoke(mode,target,expected=2);continue
   if mode=='extra-member':(target/'UNEXPECTED.control').write_bytes(b'owned extra member\n');invoke(mode,target,expected=2);continue
   if mode=='raw-changed-audit':write(target/'audits/complexity/audit.json',b'{}\n');invoke(mode,target,expected=2);continue
   report=load(target/'workflow.json')
   if mode=='rehashed-engine-profile':report['engine_profile']='linux-x86_64-nextflow-24.04.4';put(target/'workflow.json',report);changed.append('workflow.json')
   elif mode=='rehashed-biological-scope':report['biological_accuracy_certified']=True;put(target/'workflow.json',report);changed.append('workflow.json')
   elif mode=='rehashed-missing-component':report['source_components']=[x for x in report['source_components'] if x['component']!='complexity'];put(target/'workflow.json',report);changed.append('workflow.json')
   elif mode=='rehashed-component-engine':
    name='audits/complexity/audit.json';audit=load(target/name);audit['original_engine_manifest']['sha256']='0'*64;put(target/name,audit);changed.append(name);name2='audits/complexity/COMPONENT_AUDIT_COMPLETED.json';completion=load(target/name2);completion['audit']=pin(target/name);put(target/name2,completion);changed.append(name2)
    for row in report['source_components']:
     if row['component']=='complexity':row['audit']=pin(target/name)
    put(target/'workflow.json',report);changed.append('workflow.json')
   elif mode in ['rehashed-peak-mode','rehashed-consensus-minimum','rehashed-executor-capacity']:
    request=load(target/'request.json')
    if mode=='rehashed-peak-mode':request['peak_type']='narrowPeak'
    elif mode=='rehashed-consensus-minimum':request['minimum_replicates']+=1
    else:request['executor_cpus']+=1
    put(target/'request.json',request);changed.append('request.json')
   elif mode=='rehashed-image-binding':images=load(target/'image-set.json');uri=next(iter(images['images']));images['images'][uri]='/deliberately/another/image.sif';put(target/'image-set.json',images);changed.append('image-set.json');report['source_image_declaration']=pin(target/'image-set.json');put(target/'workflow.json',report);changed.append('workflow.json')
   elif mode in ['rehashed-QC-label','rehashed-QC-transform-tail','rehashed-QC-process-cleanup']:
    base='audits/saved-QC/original-qc-runtime/antibody-1/'
    if mode=='rehashed-QC-process-cleanup':name=base+'process/process.json';ledger=load(target/name);ledger['owned_live_after_cleanup']=[999999999];put(target/name,ledger)
    else:
     name=base+'worker/'+('samples.tsv' if mode=='rehashed-QC-label' else 'stored-transform.tsv');lines=(target/name).read_text(encoding='utf8').splitlines();row=lines[-1].split('\t')
     if mode=='rehashed-QC-label':row[0]='DELIBERATELY_CHANGED_SAMPLE'
     else:row[-1]=format(float(row[-1])+1,'.17g')
     lines[-1]='\t'.join(row);write(target/name,('\n'.join(lines)+'\n').encode('utf8'))
    changed.append(name)
   rehash(target,changed);invoke(mode,target,expected=2)
  invoke('original-after-all-owned-faults',source);assert {n:pin(source/n) for n in before}==before and all(pin(jars[c])==v for c,v in artifacts.items());state.update(status='PASS_COMPLETE_CHIPSEQ200_PORTABLE_RESULT_CONTROLS_ONLY',checks=checks,successful_verifications=9,deliberate_refusals=len(cases),original_payload_unchanged=before,artifacts=artifacts,full_nfcore_family_or_biological_admission=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete portable result assertions')
 raise SystemExit(main())
