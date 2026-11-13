#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare all pixels/metadata of complete original-release and source-built/native correction workflows."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,time,traceback
HERE=Path(__file__).resolve().parent
ACTION='Required action: provide permitted JDK21/25, exact --provider, published original release --reference-plugin, standalone --jar and NEW output. These controls execute complete synthetic image stacks through all three original methods and the explicit native histogram engine. Missing/forbidden runtime prerequisites SKIP3 skip dependent analysis. Retain every original/adapter image, fit log, failure and receipt; resolve FAIL2 without patching original behavior. P026 plugin version/method/settings remain unreported; no paper reproduction or biological validation is claimed.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--provider',type=Path,required=True);p.add_argument('--reference-plugin',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;jdk=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';java=jdk/('java'+suffix);jar=a.jar.resolve();provider=a.provider.resolve();reference=a.reference_plugin.resolve();before={str(n):pin(n) for n in [jar,reference,HERE/'test_workflow.py',HERE/'OriginalWorkflowControl.java']};env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  def run(label,args,expected=0,needle=None):
   command=list(map(str,args));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    proc=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    try:code=proc.wait(timeout=600)
    except subprocess.TimeoutExpired:proc.kill();proc.wait();raise ValueError('Control observation timeout; retained diagnostics: '+label)
   row=dict(label=label,command=command,pid=proc.pid,exit_code=code,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row);raw=(out/(label+'.stdout')).read_bytes()+(out/(label+'.stderr')).read_bytes()
   if code==3 and expected==0:raise OSError('Required original runtime unavailable at '+label)
   if code!=expected or needle and needle.encode() not in raw:raise ValueError('Complete control differs: '+label)
   return raw
  libs=[provider/'lib'/n for n in ['ij-1.53t.jar','scijava-common-2.89.0.jar','parsington-3.0.0.jar','eventbus-1.4.jar']];classes=out/'original-classes';classes.mkdir();cp=os.pathsep.join(map(str,[*libs,reference]));run('original-compile',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','','-cp',cp,'-d',classes,HERE/'OriginalWorkflowControl.java']);cp=str(classes)+os.pathsep+cp;run('original-inputs',[java,'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',cp,'OriginalWorkflowControl','generate',out/'fixtures']);requests=out/'requests';requests.mkdir();comparisons={};base=None
  cases=[]
  for fixture in ['byte2d','ushort2d','byte3d','ushort3d']:
   for method in ['SIMPLE_RATIO','EXPONENTIAL_FIT','HISTOGRAM_MATCHING']:cases.append((fixture+'-'+method.lower(),fixture,method,'IMAGE_ROI',0,0))
  cases += [('external-roi','byte2d','SIMPLE_RATIO',str(out/'fixtures/selected.roi'),0,0),('external-roi-unnamed','byte2d','SIMPLE_RATIO',str(out/'fixtures/region-original-017.roi'),0,0),('roi-none','ushort2d','SIMPLE_RATIO','NONE',0,0),('linear-stack','linear-stack','SIMPLE_RATIO','IMAGE_ROI',0,0),('channels-linear','channels-linear','HISTOGRAM_MATCHING','IMAGE_ROI',0,0),('zero-ratio','zero','SIMPLE_RATIO','IMAGE_ROI',0,2),('increasing-exp','increasing','EXPONENTIAL_FIT','IMAGE_ROI',2,2),('channels-volume','channels-volume','HISTOGRAM_MATCHING','IMAGE_ROI',0,2),('two-point-exp','two-point','EXPONENTIAL_FIT','IMAGE_ROI',0,0)]
  for label,fixture,method,roi,originalExit,adapterExit in cases:
   original=out/('original-'+label);original.mkdir();image=out/'fixtures'/(fixture+'.tif');baseline=7 if fixture.startswith('byte') else 75 if fixture.startswith('ushort') else 0;run('original-'+label,[java,'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',cp,'OriginalWorkflowControl','run',image,original,method,str(baseline),roi],expected=originalExit)
   req=dict(schema_version=1,version='2.1.0',imagej_version='1.53t',mode='RUN',image=str(image),roi_mode=roi if roi in ['IMAGE_ROI','NONE'] else 'ROI_FILE',provider_root=str(provider),out=str(out/label),method=method,engine='ORIGINAL',purpose='SEGMENTATION' if method=='HISTOGRAM_MATCHING' else 'INTENSITY_ANALYSIS',input_units='raw unsigned pixel intensities; original spatial/time calibration retained',input_provenance='Complete independently generated synthetic original Java TIFF; every included C/Z/T plane',axes_policy='ORIGINAL_SOURCE',execution_policy='ALLOW',timeout_seconds=240,heap_megabytes=1024,maximum_decoded_pixels=10000000)
   if method=='SIMPLE_RATIO':req['baseline']=baseline
   if req['roi_mode']=='ROI_FILE':req['roi_file']=roi
   request=requests/(label+'.json');save(request,req);raw=run('adapter-'+label,[java,'-jar',jar,'--request',request],expected=adapterExit,needle='PASS_COMPLETE_' if adapterExit==0 else 'FAIL2')
   if b'Required action:' not in raw:raise ValueError('Prominent recovery guidance missing')
   status=json.loads((out/label/'analysis-status.json').read_bytes())
   if status['result_admitted']!=(adapterExit==0):raise ValueError('Original failure/unreportable result admission differs')
   if method=='EXPONENTIAL_FIT':
    worker=json.loads((out/label/'worker-status.json').read_bytes());model=worker['actual_original_exponential_model']
    if model['second_fit_performed'] or model['reportable_fit']!=(adapterExit==0):raise ValueError('Actual original fitted-model admission differs')
    if originalExit==0:
     printed=(out/('original-'+label+'.stdout')).read_text(encoding='utf8').strip().splitlines()[-1].split(',');actual=[r['value'] for r in model['parameters_including_residual_sum'][:3]]
     if printed!=actual or not model['fitter_returned'] or len(model['x'])!=len(model['y']) or len(model['x'])!=len(model['residuals']) or len(model['derived_correction_factors'])!=len(model['x']):raise ValueError('Complete actual original fitted-model diagnostics differ')
    elif label=='increasing-exp' and 'not decaying' not in (out/label/'WARNINGS.md').read_text(encoding='utf8'):raise ValueError('Actual original nondecay error missing from prominent recovery warning')
   filename='corrected.tif' if originalExit==0 else 'unadmitted-original-image.tif';wanted=original/filename;actual=out/label/filename;equal=wanted.read_bytes()==actual.read_bytes();comparisons[label]=dict(method=method,original_exit=originalExit,adapter_exit=adapterExit,complete_original_image=pin(wanted),complete_adapter_image=pin(actual),complete_file_byte_identical=equal)
   if not equal:raise ValueError('Complete original TIFF bytes differ: '+label)
   if method=='HISTOGRAM_MATCHING':
    native=copy.deepcopy(req);native['engine']='JAVA_NATIVE_HISTOGRAM210';native['out']=str(out/(label+'-native'));nativeRequest=requests/(label+'-native.json');save(nativeRequest,native);run('native-'+label,[java,'-jar',jar,'--request',nativeRequest],expected=adapterExit,needle='PASS_COMPLETE_' if adapterExit==0 else 'FAIL2');nativeImage=out/(label+'-native')/filename;nativeStatus=json.loads((out/(label+'-native')/'analysis-status.json').read_bytes());same=wanted.read_bytes()==nativeImage.read_bytes();comparisons[label]['complete_native_image']=pin(nativeImage);comparisons[label]['complete_native_file_byte_identical']=same
    if not same or nativeStatus['result_admitted']!=(adapterExit==0):raise ValueError('Complete native histogram correction differs: '+label)
   if label=='byte2d-simple_ratio':base=req
  def bad(label,mutate,expected=2):
   req=copy.deepcopy(base);req['out']=str(out/label);mutate(req);request=requests/(label+'.json');save(request,req);raw=run(label,[java,'-jar',jar,'--request',request],expected=expected,needle='SKIP3' if expected==3 else 'FAIL2')
   if b'Required action:' not in raw:raise ValueError('Refusal recovery guidance absent')
   target=out/label
   if (target/'analysis-status.json').exists():
    status=json.loads((target/'analysis-status.json').read_bytes())
    if status['result_admitted'] or status['exit_code']!=expected:raise ValueError('Refusal admitted partial result')
  bad('execution-forbid',lambda r:r.update(execution_policy='FORBID'),3);bad('missing-provider',lambda r:r.update(provider_root=str(out/'absent-provider')),3);bad('missing-image',lambda r:r.update(image=str(out/'absent.tif')),3);bad('missing-roi',lambda r:r.update(roi_mode='ROI_FILE',roi_file=str(out/'absent.roi')),3);bad('wrong-version',lambda r:r.update(version='2.0.3'));bad('wrong-core',lambda r:r.update(imagej_version='1.54t'));bad('unknown-field',lambda r:r.update(silent_repair=True));bad('missing-baseline',lambda r:r.pop('baseline'));bad('unknown-method',lambda r:r.update(method='AUTOMATIC'));bad('native-wrong-method',lambda r:r.update(engine='JAVA_NATIVE_HISTOGRAM210'));bad('histogram-quantification',lambda r:(r.update(method='HISTOGRAM_MATCHING'),r.pop('baseline')));bad('image-capacity',lambda r:r.update(maximum_decoded_pixels=1));bad('unsupported-float',lambda r:r.update(image=str(out/'fixtures/float.tif')));bad('unsupported-single',lambda r:r.update(image=str(out/'fixtures/single.tif')));bad('existing-output',lambda r:r.update(out=str(out/'byte2d-simple_ratio')));bad('input-overlap',lambda r:r.update(out=str(out/'fixtures')))
  nativeBudget=copy.deepcopy(base);nativeBudget.update(method='HISTOGRAM_MATCHING',engine='JAVA_NATIVE_HISTOGRAM210',purpose='SEGMENTATION',maximum_native_comparisons_per_map=1);nativeBudget.pop('baseline');bad('native-budget',lambda r:(r.clear(),r.update(nativeBudget),r.update(out=str(out/'native-budget'))))
  bad('combined-input-capacity',lambda r:r.update(roi_mode='ROI_FILE',roi_file=str(out/'fixtures/selected.roi'),maximum_input_bytes=(out/'fixtures/byte2d.tif').stat().st_size));bad('workspace-capacity',lambda r:r.update(maximum_output_bytes=1));bad('file-capacity',lambda r:r.update(maximum_files=32))
  donors=out/'donors';donors.mkdir();missing=donors/'missing-source';missing.mkdir();shutil.copyfile(provider/'READY.json',missing/'READY.json');bad('missing-source',lambda r:r.update(provider_root=str(missing)),3)
  corrupt=donors/'corrupt-source';corrupt.mkdir();shutil.copyfile(provider/'READY.json',corrupt/'READY.json');shutil.copytree(provider/'source',corrupt/'source');target=corrupt/'source/src/main/java/histogram2/Util.java';target.write_bytes(target.read_bytes()+b'\n// Deliberately corrupt refusal fixture; not original source\n');bad('corrupt-source',lambda r:r.update(provider_root=str(corrupt)))
  corruptRuntime=donors/'corrupt-runtime';corruptRuntime.mkdir();shutil.copyfile(provider/'READY.json',corruptRuntime/'READY.json');shutil.copytree(provider/'source',corruptRuntime/'source');shutil.copytree(provider/'lib',corruptRuntime/'lib');target=corruptRuntime/'lib/ij-1.53t.jar';target.write_bytes(target.read_bytes()+b'\0');bad('corrupt-runtime',lambda r:r.update(provider_root=str(corruptRuntime)))
  def timeout(r):r.update(image=str(out/'fixtures/ushort2d.tif'),method='HISTOGRAM_MATCHING',purpose='SEGMENTATION',timeout_seconds=1);r.pop('baseline')
  bad('worker-timeout',timeout)
  live=json.loads((out/'worker-timeout/process-live.json').read_bytes());exitRecord=json.loads((out/'worker-timeout/process-exit.json').read_bytes())
  if live['pid']!=exitRecord['pid']:raise ValueError('Missing complete timeout terminal process receipt')
  if os.name=='nt':
   observed=subprocess.run(['tasklist','/FI','PID eq '+str(live['pid']),'/FO','CSV','/NH'],capture_output=True,timeout=30)
   if ('"'+str(live['pid'])+'"').encode() in observed.stdout:raise ValueError('Timed-out worker is still live')
  elif Path('/proc',str(live['pid'])).exists():raise ValueError('Timed-out worker is still live')
  if any(pin(Path(n))!=w for n,w in before.items()):raise ValueError('Test source/runtime changed during controls')
  result=dict(status='PASS_COMPLETE_ORIGINAL_CORRECTBLEACH210_AND_NATIVE_IMAGE_WORKFLOWS',calls=calls,complete_original_cases=comparisons,source_image_cases=len(cases),complete_native_histogram_cases=sum('complete_native_image' in r for r in comparisons.values()),request_refusals=24,original_nondecay_failure_preserved=True,original_nonfinite_ratio_unadmitted=True,original_two_point_converged_fit_preserved=True,actual_original_exponential_models_retained_without_refitting=True,original_channel_product_return_unadmitted=True,external_roi_filename_identity_preserved=True,timeout_terminal_exit_verified=True,original_source_patched=False,paper_method_inferred=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_CORRECTBLEACH210_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
