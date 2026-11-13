#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify every retained evidence file, rebuild offline, and replay all packaged native comparisons."""
from pathlib import Path
import argparse,hashlib,json,subprocess,sys,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/wesanderson037-workflow-development'
ACTION='Required action: preserve exact original source, full vectors/attributes, original failures, earlier mismatches and qualification evidence. --allow-build and --allow-analysis explicitly permit an offline native rebuild and complete saved-reference comparisons. Without either permission, SKIP3 verifies retained evidence then skips dependent build/analysis. Native colors need no R installation. Original R plotting requires the permitted documented private R4.5.3 provider; otherwise skip that dependent analysis. FAIL2 admits no qualification; correct the named source, artifact or complete-output cause and retry NEW paths. This is scalar color-generation qualification, not R-language coercion/graphics equivalence, paper reproduction or whole-goal completion.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def evidence(out):
 v=json.loads((DATA/'VALIDATION.json').read_bytes());profile=json.loads((HERE/'source-profile.json').read_bytes());artifacts=json.loads((DATA/'ARTIFACTS.json').read_bytes());layout=json.loads((DATA/'CONTROLS_LAYOUT.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_WESANDERSON037_AND_NATIVE_PACKAGED_PALETTES' or v['new_tool_credit']!=0 or v['whole_goal_complete'] or v['native_R_graphics_ported'] or v['paper_palette_inferred']:raise ValueError('Unqualified component or unsupported scope')
 for name,wanted in v['sources'].items():
  if pin(ROOT/name)!=wanted:raise ValueError('Qualified component source differs: '+name)
 for name,wanted in artifacts.items():
  if pin(ROOT/name)!=wanted:raise ValueError('Published development artifact differs: '+name)
 if pin(DATA/'controls.zip')!=v['controls_archive'] or pin(DATA/'CONTROLS_LAYOUT.json')!=v['controls_layout']:raise ValueError('Retained complete evidence inventory differs')
 extracted=out/'retained';extracted.mkdir()
 with zipfile.ZipFile(DATA/'controls.zip') as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(layout['files']):raise ValueError('Complete retained evidence population differs')
  for name,wanted in layout['files'].items():
   entry=z.getinfo(name)
   if name.startswith('/') or '\\' in name or any(x in ['','.','..'] for x in name.split('/')) or entry.is_dir() or (entry.external_attr>>16)&0o170000==0o120000 or entry.file_size!=wanted['bytes']:raise ValueError('Unsafe or inconsistent retained evidence member')
   raw=z.read(entry)
   if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Complete retained evidence bytes differ: '+name)
   target=extracted/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
 def read(name):return (extracted/name).read_bytes()
 if pin(extracted/'upstream/wesanderson_0.3.7.tar.gz')!=profile['source_archive']:raise ValueError('Complete original package source differs')
 reference=extracted/'original-reference-6108'
 if (reference/'case-count.txt').read_text().strip()!='6108' or len((reference/'complete-original-cases.tsv').read_text(encoding='utf8').splitlines())!=6108:raise ValueError('Incomplete original reference population')
 for name in ['complete-original-cases.tsv','complete-original-palettes.tsv','complete-original-cases.rds','complete-original-palettes.rds']:
  if read('public-original/'+name)!=read('original-reference-6108/'+name):raise ValueError('Fresh public original-provider result differs from independent original packet: '+name)
 for platform in ['windows21','windows25','linux21']:
  build=json.loads(read(platform+'/build/BUILD.json'));tests=json.loads(read(platform+'/packaged/COMPLETED.json'));direct=json.loads(read(platform+'/direct/COMPLETED.json'))
  if build['status']!='READY_NATIVE_WESANDERSON037_JAVA_LIBRARY' or build['compiled_target']!=17 or build['original_R_graphics_ported'] or build['artifact']!=pin(extracted/(platform+'/build/wesanderson037-native-development.jar')):raise ValueError('Retained native build differs')
  for name,wanted in build['source_pins'].items():
   if pin(ROOT/name)!=wanted or pin(extracted/(platform+'/build/source-snapshot/'+name))!=wanted:raise ValueError('Current/frozen build source differs: '+name)
  expected=(6108,24,72,62,10,1490,7,2,2,10,63)
  keys=['original_API_cases','complete_original_palette_entries','original_CLI_cases','original_CLI_successes','original_CLI_failures','complete_CLI_colors_compared','resealed_computational_or_provenance_refusals','protected_output_refusals','missing_extra_file_refusals','invalid_requests','complete_saved_bundle_replays']
  if tests['status']!='PASS_PACKAGED_WESANDERSON037_COMPLETE_API_CLI_AND_REFUSALS' or tuple(tests[k] for k in keys)!=expected or any(r['exit_code']!=r['expected_exit'] for r in tests['calls']):raise ValueError('Incomplete actual packaged control population')
  if b'cases=6108 complete_colors=224445 original_failures=3397 palettes=24' not in read(platform+'/packaged/complete-packaged-api.stdout'):raise ValueError('Full original returned color/failure population differs')
  if direct['status']!='PASS_COMPLETE_ORIGINAL_WESANDERSON037_NATIVE' or direct['original_case_population']!=6108 or any(r['exit_code'] for r in direct['calls']):raise ValueError('Incomplete direct native comparison')
  artifact='docs/literature/wesanderson037-workflow-development/wesanderson037-native-'+platform+'-development.jar'
  if artifacts[artifact]!=build['artifact']:raise ValueError('Published artifact is not the actual qualified artifact')
 setup=json.loads(read('setup/COMPLETED.json'));provider=json.loads(read('public-provider/READY.json'))
 if setup['status']!='PASS_WESANDERSON037_PRIVATE_ORIGINAL_SETUP_AND_REFUSALS' or (setup['actual_private_original_installs'],setup['actual_official_source_downloads'],setup['setup_refusals'])!=(2,1,9) or any(r['exit_code']!=r['expected_exit'] for r in setup['calls']) or setup['paper_analysis_executed'] or setup['existing_runtime_modified']:raise ValueError('Incomplete permitted original setup/refusal controls')
 if provider['status']!='READY_PRIVATE_ORIGINAL_WESANDERSON037_NO_ANALYSIS' or provider['original_source_modified'] or provider['paper_analysis_executed'] or provider['source_archive']!=profile['source_archive']:raise ValueError('Original private provider/source qualification differs')
 if len(list((extracted/'public-original/original-plots').glob('*.pdf')))!=24:raise ValueError('Original full plotting population missing')
 failed=json.loads(read('historical/failed-native-6012/FAILURE.json'))
 if failed['status']!='FAIL2_WESANDERSON037_NATIVE_CONTROLS' or b'604' not in read('historical/failed-native-6012/complete-native.stderr'):raise ValueError('Historical numerical discrepancy was lost or relabelled')
 failedBefore=json.loads(read('historical/failed-native-6012/BEFORE.json'));nativeKey=next(n for n in failedBefore if n.replace('\\','/').endswith('/src/main/java/org/jlinalg/plot/WesAnderson037.java'))
 if pin(extracted/'historical/failed-native-6012/unqualified-source.java')!=failedBefore[nativeKey]:raise ValueError('Historical failed source bytes differ from the actual failed attempt')
 return v,reference

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--allow-build',action='store_true');p.add_argument('--allow-analysis',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  for protected in [HERE,DATA,a.java_bin.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source, evidence or compiler')
  out.mkdir(parents=True,exist_ok=False);owned=True;v,reference=evidence(out)
  if not a.allow_build or not a.allow_analysis:raise OSError('Native build/analysis permission absent; retained complete evidence verified but dependent work skipped')
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=1200);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode==3:raise OSError('Required native prerequisite unavailable at '+label)
   if r.returncode:raise ValueError('Actual full offline comparison failed: '+label)
  run('build',[sys.executable,HERE/'build.py','--java-bin',a.java_bin,'--out',out/'build']);run('packaged',[sys.executable,HERE/'test_packaged.py','--java-bin',a.java_bin,'--jar',out/'build/wesanderson037-native-development.jar','--reference',reference,'--out',out/'packaged'])
  if any(pin(ROOT/name)!=wanted for name,wanted in v['sources'].items()):raise ValueError('Qualified source changed during replay')
  result=dict(status='PASS_OFFLINE_WESANDERSON037_SOURCE_REBUILD_AND_COMPLETE_PACKAGED_NATIVE_CONTROLS',calls=calls,complete_original_API_cases=6108,complete_original_returned_colors=224445,original_failures_retained=3397,original_CLI_cases=72,complete_CLI_colors_compared=1490,complete_saved_bundle_replays=63,original_R_analysis_repeated=False,original_R_graphics_ported=False,paper_palette_inferred=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_NATIVE_REPLAY_PREREQUISITE_OR_POLICY_UNAVAILABLE' if code==3 else 'FAIL2_WESANDERSON037_OFFLINE_REPLAY',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
