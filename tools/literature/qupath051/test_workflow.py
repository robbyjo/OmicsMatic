#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the public Java adapter and compare all portable original detection exports."""
from pathlib import Path
import argparse,copy,json,os,shutil,subprocess,time,traceback
from provider import pin,save
HERE=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--provider',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--complete-protocol-controls',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 for name in ['java_bin','jar','provider','reference']:setattr(a,name,getattr(a,name).resolve())
 try:
  for protected in [HERE,a.java_bin.resolve(),a.jar.resolve(),a.provider.resolve(),a.reference.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Control output overlaps protected source/runtime/reference')
  out.mkdir(parents=True,exist_ok=False);owned=True;actual=out/'actual';actual.mkdir();requests=out/'requests';requests.mkdir();inputs=out/'protocol-inputs';inputs.mkdir()
  sources={n:pin(HERE/n) for n in ['OriginalControl.groovy','ProtocolControl.groovy','test_workflow.py']};(out/'source-snapshot').mkdir()
  for n in sources:shutil.copyfile(HERE/n,out/'source-snapshot'/n)
  before=dict(sources=sources,jar=pin(a.jar),reference_files={p.name:pin(p) for p in (a.reference/'results').iterdir()});save(out/'BEFORE.json',before)
  java=a.java_bin/('java.exe' if os.name=='nt' else 'java')
  def run(label,request,expected):
   path=requests/(label+'.json');save(path,request);command=[str(java),'-jar',str(a.jar),'--request',str(path)];start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.live.json'),dict(command=command,pid=child.pid,started_unix=start));code=child.wait(timeout=1200)
   row=dict(label=label,command=command,exit_code=code,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row);print(label,'exit',code,flush=True)
   if code!=expected:raise ValueError('Actual public original-adapter exit differs at '+label)
   target=Path(request['out'])
   if target.exists() and (target/'analysis-status.json').exists():
    status=json.loads((target/'analysis-status.json').read_bytes());assert status['result_admitted']==(expected==0)
    manifest=json.loads((target/'MANIFEST.json').read_bytes());excluded=set(manifest['excluded_final_records']);assert excluded=={'MANIFEST.json','analysis-status.json'}
    present={p.relative_to(target).as_posix() for p in target.rglob('*') if p.is_file()};assert present-set(manifest['files'])==excluded
    for name,record in manifest['files'].items():
     path=target/name
     if record['kind']=='file':assert pin(path)=={k:record[k] for k in ['bytes','sha256']}
     else:
      assert record['kind']=='symlink' and path.is_symlink() and str(path.readlink())==record['target'] and path.resolve().is_relative_to(target)
      if expected==0:
       cache=target/'owned-home/.javacpp/cache';assert path.is_relative_to(cache) and path.resolve().is_relative_to(cache)
   return target
  original=a.reference/'results';wanted={p.name:dict(minimum_bytes=0) for p in original.iterdir()};assert len(wanted)==25
  request=dict(schema_version=1,version='0.5.1',mode='SCRIPT',script=str(HERE/'OriginalControl.groovy'),provider_directory=str(a.provider),
   inputs={p.name:str(p) for p in (a.reference/'inputs').iterdir()},required_classes=['qupath.imagej.detect.cells.PositiveCellDetection','qupath.lib.images.ImageData','com.google.gson.Gson'],
   expected_outputs=wanted,input_provenance='Complete original independent synthetic six-image control inputs; no author data or settings recovered',analysis_population='All six synthetic jobs, including true zero detections;72 original detections total',
   expected_return='QUPATH051_COMPLETE_SYNTHETIC_CELL_POPULATION',execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(actual/'complete-cells'))
  target=run('complete-cells',request,0);compared=[]
  for name in sorted(wanted):
   if name.endswith('.qpdata'):continue
   if (target/'results'/name).read_bytes()!=(original/name).read_bytes():raise ValueError('Complete original portable export differs: '+name+'; preserve full files and all source/model differences')
   compared.append(name)
  assert len(compared)==13
  summary=json.loads((target/'results/complete-summary.json').read_bytes());assert [r['detections'] for r in summary]==[16,0,8,16,16,16]
  assert (target/'results/all-16.measurements.tsv').read_text(encoding='utf8').find('α')>=0
  protocol=[]
  if a.complete_protocol_controls:
   config=inputs/'control.json';save(config,dict(mode='success'))
   base=dict(request,script=str(HERE/'ProtocolControl.groovy'),inputs={'control.json':str(config)},required_classes=['qupath.lib.images.ImageData'],
    expected_outputs={'complete.txt':dict(minimum_bytes=1)},input_provenance='Explicit synthetic protocol controls',analysis_population='One complete original script',expected_return='QUPATH051_PROTOCOL_COMPLETE')
   modes=[('warning-visible','warning',0),('logged-error-with-zero-exit','logged-error',2),('original-exception','throw',2),
     ('wrong-return','wrong-return',2),('extra-output','extra-output',2),('mutated-captured-input','mutate-captured-input',2)]
   if os.name!='nt':modes += [('workspace-link-refusal','workspace-link',2),('cache-escape-link-refusal','unsafe-cache-link',2),('internal-native-cache-link','internal-cache-link',0),('absolute-internal-native-cache-link','internal-cache-absolute-link',0)]
   for label,mode,expected in modes:
    source=inputs/(label+'.json');save(source,dict(mode=mode));q=dict(base,inputs={'control.json':str(source)},out=str(actual/label));dest=run(label,q,expected);protocol.append(label)
    if label=='warning-visible':
     status=json.loads((dest/'analysis-status.json').read_bytes());assert status['original_warning_headers']==1 and status['warning_review_required'];assert 'original headers=1' in (out/(label+'.stderr')).read_text()
    if label=='logged-error-with-zero-exit':assert json.loads((dest/'process-exit.json').read_bytes())['exit_code']==0 and json.loads((dest/'analysis-status.json').read_bytes())['original_error_headers']==1
    if label=='mutated-captured-input':assert pin(source)!=pin(dest/'inputs/control.json')
   controls=[('missing-output',dict(expected_outputs={'missing.txt':dict(minimum_bytes=1)}),2),('execution-skip',dict(execution_policy='FORBID'),3),
    ('trusted-code-skip',dict(trusted_code_policy='FORBID'),3),('missing-provider',dict(provider_directory=str(out/'absent-provider')),3),
    ('missing-class',dict(required_classes=['not.installed.OriginalExtension']),3),('wrong-version',dict(version='0.6.0'),2),
    ('unknown-request-field',dict(unknown_analysis_flag=True),2),('missing-provenance',dict(input_provenance=''),2),
    ('source-output-overlap',dict(out=str(a.provider/'refused-analysis-child')),2)]
   for label,changes,expected in controls:
    q=dict(base,out=str(actual/label));q.update(changes);dest=run(label,q,expected);protocol.append(label)
    if label in ['execution-skip','trusted-code-skip','missing-provider','missing-class']:assert not (dest/'process-live.json').exists()
   assert not (a.provider/'refused-analysis-child').exists()
  if pin(a.jar)!=before['jar'] or any(pin(HERE/n)!=v for n,v in sources.items()) or any(pin(a.reference/'results'/n)!=v for n,v in before['reference_files'].items()):raise ValueError('Protected source/artifact/reference changed')
  result=dict(status='PASS_ORIGINAL_QUPATH051_COMPLETE_PORTABLE_CELL_EXPORTS_AND_PUBLIC_ADMISSION_CONTROLS',complete_image_jobs=6,complete_detections=72,
   complete_original_portable_files_compared=13,raw_and_fixture_labelled_qpdata_files_retained=12,opaque_qpdata_byte_parity_claimed=False,protocol_controls=len(protocol),
   calls=calls,compared_files=compared,sources=sources,new_tool_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_QUPATH_CONTROL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_QUPATH_PUBLIC_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False)
  if owned:save(out/'FAILURE.json',v)
  print(v['status'],repr(e),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
