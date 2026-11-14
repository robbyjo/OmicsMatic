#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the additional frozen-verifier tests and exact negative ZIP inputs represented by lossless deltas."""
from pathlib import Path
import argparse,base64,hashlib,io,json,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/ggpubr-workflow-development'
ACTION='Required action: retain the unchanged base scientific checkpoint, all archive parts, the additional verifier evidence, complete inventories and exact negative-archive deltas. Missing evidence SKIP3 skips dependent verification; restore it and retry NEW output. FAIL2 admits no auxiliary qualification. Preserve diagnostics and resolve the named contradiction without changing original scientific source, dropping a case or refreshing the base hashes to force success. The delta representation preserves every byte of each actually tested negative ZIP; private materialized copies remain retained. This command runs no new original analysis.'

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(path.read_bytes())
def read_archive(record,key,parts_key,stem):
 parts=record.get(parts_key,{})
 if parts:
  if list(parts)!=[stem+'.part'+str(i).zfill(3) for i in range(1,len(parts)+1)]:raise ValueError('Missing or reordered evidence archive parts')
  raw=[]
  for name,wanted in parts.items():
   piece=(DATA/name).read_bytes()
   if rawpin(piece)!=wanted:raise ValueError('Evidence archive part differs: '+name)
   raw.append(piece)
  payload=b''.join(raw)
 else:payload=(DATA/stem).read_bytes()
 if rawpin(payload)!=record[key]:raise ValueError('Complete evidence archive differs')
 return payload

def evidence():
 base=json.loads((DATA/'VALIDATION.json').read_bytes());record=json.loads((DATA/'VERIFIER_BOUNDARIES.json').read_bytes())
 if record['status']!='PASS_FOUR_FROZEN_GGPUBR_VERIFIER_REFUSALS_WITHOUT_SCIENTIFIC_SOURCE_CHANGES' or record['controls']!=4 or record['baseline_controls']!=1:raise ValueError('Incomplete frozen-verifier control population')
 if record['base_validation']!=pin(DATA/'VALIDATION.json') or record['base_scientific_archive']!=base['archive']:raise ValueError('Frozen scientific baseline changed')
 if record['scientific_sources_changed'] or record['scientific_sources_overridden'] or record['verifier_source_changed'] or record['source_or_result_hashes_refreshed_in_original_checkpoint']:raise ValueError('Scientific source/hash overlays are forbidden')
 if len(record['calls'])!=5 or any(c['exit_code']!=c['expected_exit'] for c in record['calls']):raise ValueError('Actual frozen-verifier exits differ')
 if pin(DATA/'VERIFIER_BOUNDARIES_LAYOUT.json')!=record['additional_layout']:raise ValueError('Additional evidence inventory differs')
 layout=json.loads((DATA/'VERIFIER_BOUNDARIES_LAYOUT.json').read_bytes());base_raw=read_archive(base,'archive','archive_parts','controls.zip')
 raw=read_archive(record,'additional_archive','additional_archive_parts','verifier-boundary-controls.zip')
 reconstructed=[]
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(layout['files']) or len(names)!=record['additional_members']:raise ValueError('Incomplete additional verifier archive population')
  for name,wanted in layout['files'].items():
   if rawpin(z.read(name))!=wanted:raise ValueError('Additional verifier member differs: '+name)
  for case,row in record['negative_archive_deltas'].items():
   encoded=z.read(row['archive_member'])
   if rawpin(encoded)!=row['delta']:raise ValueError('Exact negative ZIP delta differs')
   delta=json.loads(encoded)
   if delta['format']!='exact-byte-base-slices-and-literals-v1' or delta['base_archive']!=base['archive'] or delta['target_archive']!=row['target_archive']:raise ValueError('Incorrect delta base or target identity')
   pieces=[]
   for operation in delta['operations']:
    if set(operation)=={'copy'}:
     start,length=operation['copy']
     if type(start)!=int or type(length)!=int or not 0<=start<=len(base_raw) or not 0<=length<=len(base_raw)-start:raise ValueError('Invalid lossless delta copy interval')
     pieces.append(base_raw[start:start+length])
    elif set(operation)=={'literal_base64'}:pieces.append(base64.b64decode(operation['literal_base64'],validate=True))
    else:raise ValueError('Unknown lossless delta operation')
   target=b''.join(pieces)
   if rawpin(target)!=row['target_archive']:raise ValueError('Complete actually tested negative ZIP bytes were not retained')
   parts=row['target_archive_parts'];offset=0
   for name,wanted in parts.items():
    piece=target[offset:offset+wanted['bytes']]
    if rawpin(piece)!=wanted:raise ValueError('Reconstructed negative ZIP part differs')
    offset+=wanted['bytes']
   if parts and offset!=len(target):raise ValueError('Reconstructed negative ZIP parts are incomplete')
   fixture=json.loads(z.read('controls/'+case+'/VALIDATION.json'))
   if fixture['archive']!=row['target_archive'] or fixture.get('archive_parts',{})!=parts:raise ValueError('Reconstructed negative input differs from actual verifier request')
   with zipfile.ZipFile(io.BytesIO(target)) as negative:
    wanted=json.loads(z.read('controls/'+case+'/CONTROLS_LAYOUT.json'))['files'];present=set(negative.namelist())
    if case=='missing-original-plot':
     missing=set(wanted)-present
     if len(missing)!=1 or present-set(wanted) or not next(iter(missing)).endswith('.svg'):raise ValueError('Missing-original-plot refusal was weakened')
    elif case=='forged-failed-layer-admission':
     prefix='protocols/current/protocols-linux-0.4.0/original-statistical-layer-failure/'
     state=json.loads(negative.read(prefix+'analysis-status.json'));worker=json.loads(negative.read(prefix+'diagnostics/worker-status.json'))
     if not state['result_admitted'] or state['exit_code']!=0 or state['failed_layer_warnings']<1 or worker['exit_code']!=2:raise ValueError('Forged-admission contradiction was erased')
    else:raise ValueError('Unexpected negative archive control')
   reconstructed.append(dict(case=case,archive=row['target_archive'],exact_bytes_reconstructed=True,all_parts_verified=True))
   del pieces,target
 result=dict(status='PASS_FROZEN_GGPUBR_VERIFIER_REFUSALS_AND_EXACT_LOSSLESS_NEGATIVE_ARCHIVES',refusal_controls=4,baseline_controls=1,additional_archive_members=len(names),
  reconstructed_negative_archives=reconstructed,base_validation=pin(DATA/'VALIDATION.json'),base_scientific_archive=base['archive'],boundary_record=pin(DATA/'VERIFIER_BOUNDARIES.json'),
  auxiliary_source=dict(path=Path(__file__).resolve().relative_to(ROOT).as_posix(),**pin(Path(__file__).resolve())),original_scientific_verifier_modified=False,new_original_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
 return result

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(exist_ok=False);owned=True;result=evidence();(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_GGPUBR_AUXILIARY_EVIDENCE_UNAVAILABLE' if code==3 else 'FAIL2_GGPUBR_AUXILIARY_VERIFICATION',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:(out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
