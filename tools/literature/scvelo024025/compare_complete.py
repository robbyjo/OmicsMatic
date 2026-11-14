#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every retained original scientific stage/array in one fixed complete synthetic example."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,math,re,sys,traceback,xml.etree.ElementTree as ET
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
ACTION='Preserve every original object, partial file, condition, raw report and failed attempt. Missing/forbidden complete prerequisites SKIP3 skip dependent comparison. A scientific-value, typed-identifier, population or integrity mismatch FAIL2 admits no complete result. Correct the actual cause and retry NEW paths; never change saved hashes, repair NaN/Inf or drop failed stages. This checks complete original-versus-adapter synthetic state, not independent statistical validity, opaque pickle/HDF5/SVG byte parity, convergence, author environment or paper reproduction.'
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def pairs(values):
 result={}
 for key,value in values:
  if key in result:raise ValueError('Duplicate JSON key: '+key)
  result[key]=value
 return result
def load(p):
 if p.is_symlink() or not p.is_file():raise OSError('Exact regular original/actual prerequisite unavailable: '+str(p))
 return json.loads(p.read_text(encoding='utf8'),object_pairs_hook=pairs,parse_constant=lambda token:(_ for _ in ()).throw(ValueError('Nonfinite JSON token: '+token)))
def canonical(v):return json.dumps(v,sort_keys=True,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode('utf8')
def member(name):
 p=PurePosixPath(name)
 if not isinstance(name,str) or '\\' in name or p.is_absolute() or p.as_posix()!=name or '..' in p.parts:raise ValueError('Exact safe relative retained filename required')
 return name
def population(root,version,mode,profile):
 if root.is_symlink() or not root.is_dir():raise OSError('Complete original/actual output directory unavailable: '+str(root))
 files=[]
 for path in root.rglob('*'):
  if path.is_symlink():raise ValueError('Linked original/actual output is not a complete regular-file population')
  if path.is_file():files.append(path.relative_to(root).as_posix())
  elif not path.is_dir():raise ValueError('Nonregular original/actual result node is not an admitted scientific file')
 if sorted(files)!=profile['expected_files']:raise ValueError('Missing/extra complete original result files')
 status=load(root/'COMPLETED.json')
 if status['version']!=version or status['mode']!=mode or status['status']!='COMPLETED_ORIGINAL_SCVELO_CANDIDATE_STAGES_NOT_ADAPTER_QUALIFICATION' or any((row['cells'],row['genes'])!=(160,12) for row in status['stages']):raise ValueError('Complete original synthetic population/version/mode differs')
 graph=load(root/'complete-stages/COMPLETE-STAGES.json')
 if graph['schema_version']!=2 or graph['snapshot_scope']!='independent-top-level-stage-values' or graph['snapshot_roots']!=profile['stage_roots'] or graph['nonfinite_values_repaired'] is not False or graph['opaque_values']!=[]:raise ValueError('Complete independent original stage snapshots required')
 arrays=[row for row in graph['records'] if row['kind']=='array'];names=[row['file'] for row in arrays]
 if len(arrays)!=profile['array_count'] or len(set(names))!=len(names) or any(not re.fullmatch(r'arrays/[0-9]{6}\.npy',name) for name in names):raise ValueError('Complete unique original numeric array population differs')
 if {p.relative_to(root/'complete-stages').as_posix() for p in (root/'complete-stages/arrays').rglob('*') if p.is_file()}!=set(names):raise ValueError('Complete scientific array file namespace differs')
 if canonical(graph['nonfinite_arrays'])!=canonical(profile['expected_nonfinite_array_snapshots']):raise ValueError('Original nonfinite loss-history values/population changed or repaired')
 for row in arrays:
  if not isinstance(row['shape'],list) or any(type(n)!=int or n<0 for n in row['shape']):raise ValueError('Exact original numeric shape required')
 figures=status['figure_files']
 if len(figures)!=(3 if mode=='stochastic' else 1) or status['all_figure_files_beneath_output'] is not True:raise ValueError('Complete owned original figure population differs')
 for figure in figures:
  path=root/member(figure['file'])
  if pin(path)!={k:figure[k] for k in ['bytes','sha256']}:raise ValueError('Retained original figure bytes differ')
  xml=ET.fromstring(path.read_bytes())
  if xml.tag!='{http://www.w3.org/2000/svg}svg' or not xml.findall('.//{http://www.w3.org/2000/svg}path'):raise ValueError('Complete nonempty original SVG figure required')
 return graph,arrays,status
def compare(reference,actual,version,mode,supervised=False):
 doc=load(HERE/'example-output-profile.json');selected=doc['profiles'][version][mode]
 left,arrays,left_status=population(reference,version,mode,selected);right,other,right_status=population(actual,version,mode,selected)
 if canonical(left)!=canonical(right):raise ValueError('Full typed original scientific stage graph/identifiers differ')
 if canonical(load(reference/'original-calls.json'))!=canonical(load(actual/'original-calls.json')):raise ValueError('Original scientific calls/options differ')
 for root in [reference,actual]:
  if pin(root/'original-input.h5ad')['sha256']!=doc['input_sha256']:raise ValueError('Complete fixed original input differs')
 for row in arrays:
  if pin(reference/'complete-stages'/row['file'])!=pin(actual/'complete-stages'/row['file']):raise ValueError('Complete original scientific array bytes/dtype/NaN payload differ: '+row['file'])
 if supervised:
  controller=actual.parent.parent;status=load(controller/'analysis-status.json');manifest=load(controller/'MANIFEST.json')
  if status['result_admitted'] is not True or status['exit_code']!=0 or status['original_version']!=version or status['condition_counts_complete'] is not True or status['native_temporary_copy_complete'] is not True:raise ValueError('Complete supervised original output/conditions/temporary retention not admitted')
  for name,wanted in manifest['files'].items():
   path=controller/member(name)
   if path.is_symlink() or pin(path)!=wanted:raise ValueError('Complete supervised manifest differs: '+name)
  native=load(controller/'native-temporary-inventory.json')
  if canonical(native)!=canonical(manifest['native_temporary']) or native['original_root_retained'] is not True:raise ValueError('Complete retained native temporary manifest differs')
  for name,wanted in native['files'].items():
   if wanted['kind']=='file' and pin(controller/'native-temporary'/member(name))!={k:wanted[k] for k in ['bytes','sha256']}:raise ValueError('Complete retained original temporary bytes differ')
 return dict(status='PASS_COMPLETE_ORIGINAL_SCVELO_TYPED_STAGE_GRAPH_AND_ALL_NUMERIC_ARRAY_BYTES',version=version,mode=mode,complete_stages=len(right_status['stages']),stage_snapshots=len(right['snapshot_roots']),graph_records=len(right['records']),arrays=len(arrays),elements=sum(math.prod(row['shape']) for row in arrays),nonfinite_array_snapshots_retained=len(right['nonfinite_arrays']),complete_current_figures=len(right_status['figure_files']),complete_result_files=len(selected['expected_files']),all_npy_bytes_equal=True,typed_identifiers_equal=True,numeric_tolerance=0,supervised_manifest_checked=supervised,reference_delivered_warnings=left_status['delivered_warnings'],actual_stage_delivered_warnings=right_status['delivered_warnings'],opaque_pickle_hdf5_svg_byte_parity_claimed=False,convergence_established=False,paper_reproduced=False,new_tool_credit=0,whole_goal_complete=False)
def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--actual',type=Path,required=True);parser.add_argument('--version',choices=['0.2.4','0.2.5'],required=True);parser.add_argument('--mode',choices=['stochastic','dynamical'],required=True);parser.add_argument('--supervised',action='store_true');parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();owned=False
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW comparison output required')
  for protected in [a.reference.resolve(),a.actual.resolve(),HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Comparison output overlaps protected original/input/source')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=compare(a.reference,a.actual,a.version,a.mode,a.supervised);(out/'COMPLETED.json').write_bytes(canonical(result)+b'\n');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],a.version,a.mode,flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;record=dict(status='SKIP3_COMPLETE_ORIGINAL_COMPARISON_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_COMPLETE_ORIGINAL_SCVELO_COMPARISON_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,new_tool_credit=0)
  if owned:(out/'FAILURE.json').write_bytes(canonical(record)+b'\n');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8')
  print(record['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
