#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete frozen native grid comparisons without installing software or fitting models."""
from pathlib import Path
import argparse,hashlib,io,json,math,struct,sys,traceback,zipfile
ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/velociraptor-grid120-native-development'
ACTION='Native grid arithmetic is a scoped finite two-dimensional Java component, not RNA-velocity fitting or latent time. The separately missing R wrapper receives zero family credit here. Keep all rows and original values; unsupported domains require a qualified permitted original route or skipped dependent analysis. Review computed NaN/Inf and the retained original R namespace stack-imbalance diagnostic; its native cause is unresolved and raw-console diagnostic totals are unavailable, never inferred to be zero. This verifier installs nothing, fits no model and changes no saved hashes. Preserve all source, original RDS, binary inputs/outputs, diagnostics and partial evidence after failure; correct the named cause and retry NEW paths. The full250-paper objective remains open.'

def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return identity(path.read_bytes())
def unique(pairs):
 result={}
 for key,value in pairs:
  if key in result:raise ValueError('Duplicate evidence JSON key: '+key)
  result[key]=value
 return result
def decoded(raw):return json.loads(raw,object_pairs_hook=unique,parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Nonfinite evidence JSON token: '+value)))
def archive_members(archive,expected):
 names=archive.namelist()
 if len(names)!=len(set(names)) or set(names)!=set(expected):raise ValueError('Complete evidence archive population differs')
 for name,wanted in expected.items():
  if identity(archive.read(name))!=wanted:raise ValueError('Original evidence archive member changed: '+name)

def verify(root=ROOT):
 data=root/'docs/literature/velociraptor-grid120-native-development'
 manifest=decoded((data/'VERIFICATION_MANIFEST.json').read_bytes())
 if manifest['schema_version']!=1 or manifest['new_family_implementation_credit'] or manifest['whole_goal_complete']:raise ValueError('Unsupported native component verification scope')
 for name,wanted in manifest['sources'].items():
  if pin(root/name)!=wanted:raise ValueError('Qualified native source changed: '+name)
 for name,wanted in manifest['proof_files'].items():
  if pin(data/name)!=wanted:raise ValueError('Frozen native proof changed: '+name)
 proof=decoded((data/'VALIDATION.json').read_bytes())
 if proof['status']!='PASS_SCOPED_NATIVE_JAVA_GRID31_FULL_ORIGINAL_R_CASES_PROJECT_AND_PACKAGED_JAR' or proof['new_family_implementation_credit'] or proof['whole_R_wrapper_adapter_qualified'] or proof['whole_goal_complete']:raise ValueError('Native component scope differs')
 if proof['sources']!={name:manifest['sources'][name] for name in proof['sources']}:raise ValueError('Executed-source population/pins differ')
 if pin(data/'SCOPE.json')!=proof['scope'] or pin(data/'EVIDENCE_MEMBERS.json')!=proof['evidence_members']:raise ValueError('Exact scope/member binding differs')
 source_fixture=root/'src/test/resources/velociraptor-grid120-reference.zip'
 if pin(source_fixture)!=proof['fixture'] or pin(data/'validation-evidence.zip')!=proof['evidence']:raise ValueError('Complete original/native archive changed')
 members=decoded((data/'EVIDENCE_MEMBERS.json').read_bytes())['members']
 finite=nonfinite=integers=fields=0;maximum_error=0.0
 with zipfile.ZipFile(source_fixture) as original,zipfile.ZipFile(data/'validation-evidence.zip') as native:
  expected=decoded(original.read('MEMBERS.json'))['members'];archive_members(original,{**expected,'MEMBERS.json':identity(original.read('MEMBERS.json'))});archive_members(native,members)
  index=decoded(original.read('binary/INDEX.json'))
  if index['case_count']!=31 or len(index['cases'])!=31 or index['byte_order']!='little' or index['matrix_order']!='row-major':raise ValueError('Complete original grid case population/order differs')
  names={'start','end','vectors','limits','delta','categories','groups','group_labels','populations'}
  for case in index['cases']:
   prefix='native-comparison/results/'+case['directory']+'/'
   actual_result=decoded(native.read(prefix+'RESULT.json'))
   wanted=dict(status='COMPLETE_NATIVE_GRID',cells=case['cells'],bins=case['fields']['start']['rows'],resolution=case['resolution'],scaled=case['scale'],requires_nonfinite_review=case['requires_nonfinite_review'])
   if actual_result!=wanted:raise ValueError('Native complete population/review flag differs: '+case['id'])
   for name in sorted(names):
    field=case['fields'][name];left=original.read('binary/'+field['path']);right=native.read(prefix+name+'.bin');count=field['rows']*field['columns'];fields+=1
    if len(left)!=len(right) or len(left)!=field['bytes']:raise ValueError('Native field is incomplete: '+case['id']+'/'+name)
    if field['kind']=='int32':
     if len(left)!=count*4 or left!=right:raise ValueError('Original categories/group labels/order differ: '+case['id']+'/'+name)
     integers+=count
    elif field['kind']=='float64':
     if len(left)!=count*8:raise ValueError('Incomplete original FP64 population')
     for position,(expected_value,actual_value) in enumerate(zip(struct.unpack('<'+'d'*count,left),struct.unpack('<'+'d'*count,right))):
      if math.isnan(expected_value):
       if not math.isnan(actual_value):raise ValueError('Original computed NaN changed')
       nonfinite+=1
      elif math.isinf(expected_value):
       if expected_value!=actual_value:raise ValueError('Original infinity sign changed')
       nonfinite+=1
      else:
       if not math.isfinite(actual_value) or not math.isclose(expected_value,actual_value,rel_tol=1e-12,abs_tol=1e-12):raise ValueError('Original finite value differs: '+case['id']+'/'+name+'/'+str(position))
       maximum_error=max(maximum_error,abs(expected_value-actual_value));finite+=1
    else:raise ValueError('Unknown original numeric representation')
  if (fields,integers,finite,nonfinite)!=(279,14547,5736,48):raise ValueError('Full original/native value population changed')
  math_proof=decoded(native.read('native-comparison/COMPLETED.json'));project=decoded(native.read('project-check/COMPLETED.json'));packaged=decoded(native.read('packaged-check/COMPLETED.json'));repeat=decoded(native.read('original-namespace-diagnostic/COMPLETED.json'))
  if math_proof!=proof['native_math'] or project!=proof['project'] or packaged!=proof['packaged'] or repeat!=proof['original_repetition']:raise ValueError('Full retained terminal proof differs')
  if math_proof['native_control_checks']!=31 or project['tests']!=3 or project['failures'] or project['errors'] or packaged['native_control_checks']!=31 or repeat['original_value_cases']!=31:raise ValueError('Incomplete guards/project/packaged/repeated original controls')
  jarname=Path(project['library_JAR']['path'].replace(chr(92),'/')).name
  if identity(native.read('library-JAR/'+jarname))!={key:project['library_JAR'][key] for key in ['bytes','sha256']}:raise ValueError('Actual packaged library JAR differs')
  with zipfile.ZipFile(io.BytesIO(native.read('library-JAR/'+jarname))) as jar:
   if 'org/jlinalg/singlecell/VelociraptorGrid120.class' not in jar.namelist() or jar.read('META-INF/LICENSE-velociraptor120.txt')!=(root/'src/main/resources/META-INF/LICENSE-velociraptor120.txt').read_bytes():raise ValueError('Native class/original notice is not packaged')
  diagnostics=decoded(original.read('binary-export/ORIGINAL-DIAGNOSTICS-REVIEW.json'))
  if not diagnostics['warning_review_required'] or diagnostics['raw_console_diagnostic_counts_complete'] or not diagnostics['known_stack_imbalance_lines'] or 'stack imbalance' not in original.read('original-reference/process.stderr').decode():raise ValueError('Known original R diagnostic was removed or zeroed')
  observations=repeat['raw_stack_diagnostic_observations']
  if not observations or any(row['most_recent_stage']!='before-original-namespace-load' for row in observations):raise ValueError('Original namespace-load diagnostic localization differs')
 return dict(status=proof['status'],complete_original_R_cases=31,complete_arithmetic_fields=fields,integer_values=integers,finite_values=finite,nonfinite_classes_preserved=nonfinite,maximum_absolute_error=maximum_error,native_guard_checks=31,project_tests=3,actual_packaged_JAR_verified=True,original_R_namespace_warning_review_required=True,original_native_C_cause_resolved=False,raw_console_diagnostic_counts_complete=False,new_family_implementation_credit=0,whole_R_wrapper_adapter_qualified=False,whole_goal_complete=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.absolute();owned=False
 try:
  if out!=out.resolve() or not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW canonical monocle-runtime verification output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=verify()
  (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');(out/'WARNINGS.md').write_text('# Original R namespace diagnostic: review required\n\n'+ACTION+'\n',encoding='utf-8');print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
  result=dict(status='SKIP3_NATIVE_GRID_EVIDENCE_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_GRID_EVIDENCE_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:
   try:(out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf-8')
   except OSError as recording:print('FAIL2_NATIVE_GRID_RECORDING_ERROR',repr(recording),'Retain complete console and resolve the named filesystem requirement. '+ACTION,flush=True);return 2
  print(result['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
