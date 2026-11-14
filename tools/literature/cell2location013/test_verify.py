#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refuse changed complete evidence, omitted identities, altered sources and superseded stage graphs."""
from pathlib import Path
import argparse,base64,copy,importlib.util,json,shutil,sys
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8'))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 spec=importlib.util.spec_from_file_location('qualified_original_cell2location013_frozen_verifier',HERE/'verify.py');v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v);data=v.ROOT/v.DATA;archive=data/'objects.zip';index=v.load(data/'INDEX.json');expected_archive=v.pin(archive);expected_index=v.pin(data/'INDEX.json');cases=[]
 def refuse(name,callback):
  try:callback();raise AssertionError('Unqualified changed evidence accepted: '+name)
  except ValueError as error:cases.append(dict(id=name,status='EXPECTED_FAIL2_NO_COMPLETE_RESULT',cause=str(error)))
 changed=out/'changed-evidence.zip';shutil.copyfile(archive,changed);suffix=b'isolated deliberately unqualified evidence suffix'
 with changed.open('ab') as f:f.write(suffix)
 refuse('changed-complete-archive',lambda:v.archive_evidence(changed,index,expected_archive,expected_index))
 save(out/'NEGATIVE_ARCHIVE_DELTA.json',dict(base_archive=expected_archive,append_exact_bytes_base64=base64.b64encode(suffix).decode('ascii'),complete_negative_archive=v.pin(changed),raw_negative_archive_retained_locally=True,lossless_reconstruction=True))
 omitted=copy.deepcopy(index);del omitted['files'][next(iter(omitted['files']))];save(out/'omitted-logical-member.json',omitted);refuse('omitted-complete-logical-member',lambda:v.archive_evidence(archive,omitted,expected_archive,expected_index))
 links=copy.deepcopy(index);assert len(links['links'])==2;del links['links'][next(iter(links['links']))];save(out/'omitted-original-link.json',links);refuse('omitted-original-link-identity',lambda:v.archive_evidence(archive,links,expected_archive,expected_index))
 original=v.pin(HERE/'run.py');source=out/'changed-source';source.mkdir();(source/'run.py').write_bytes((HERE/'run.py').read_bytes()+b'\n# isolated deliberate source mismatch\n');refuse('changed-qualified-controller',lambda:v.require_sources(source,{'run.py':original}))
 # This tests the scientific scope predicate itself, after ordinary archive
 # identity checking: the old recorded graph remains in the lossless store.
 legacy=copy.deepcopy(index);target='cell2location-original-run-7/direct-original/COMPLETE-NUMERICAL-INDEX.json';old='cell2location-original-run-5/direct-original/COMPLETE-NUMERICAL-INDEX.json';legacy['files'][target]=dict(legacy['files'][old]);save(out/'legacy-stage-graph-input.json',legacy)
 old_store=v.EvidenceStore(archive,legacy);refuse('old-global-graph-cannot-prove-independent-stages',lambda:v.stage_comparison(old_store,'cell2location-original-run-7/direct-original','cell2location-original-run-8/direct-original','cell2location-stage-snapshot-validation-1/complete-independent-stages'))
 baseline=v.archive_evidence(archive,index,expected_archive,expected_index)
 if v.pin(archive)!=expected_archive or v.pin(data/'INDEX.json')!=expected_index:raise ValueError('Published original evidence changed during refusal controls')
 result=dict(status='PASS_COMPLETE_ORIGINAL_FROZEN_EVIDENCE_AND_FIVE_REFUSALS',refusal_controls=cases,baseline=baseline,published_evidence_unchanged=True,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
