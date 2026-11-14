#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify exact retained-evidence refusals using fresh owned copies, preserving all published files."""
from pathlib import Path
import argparse,copy,importlib.util,json,shutil,sys,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 spec=importlib.util.spec_from_file_location('original_rstatix072_revision2_frozen_verifier',HERE/'verify.py');v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
 proof=v.load(v.DATA/'VALIDATION.json');index=v.load(v.DATA/'MEMBERS.json');archive=v.DATA/'controls.zip'
 with zipfile.ZipFile(v.ROOT/'docs/literature/rstatix072-workflow-development/controls.zip') as z:baseline=z.read('protocols/linux/COMPLETED.json')
 controls=[]
 def refuse(name,callback):
  try:callback();raise AssertionError('Unqualified altered input was accepted: '+name)
  except ValueError as error:controls.append(dict(id=name,status='EXPECTED_FAIL2_NO_RESULT_ADMITTED',cause=str(error)))
 original=v.pin(archive)
 damaged=out/'damaged-evidence.zip';shutil.copyfile(archive,damaged)
 with damaged.open('ab') as f:f.write(b'isolated deliberate changed evidence byte')
 refuse('changed-complete-evidence-bytes',lambda:v.archive_evidence(damaged,proof['archive'],index,baseline))
 omitted=copy.deepcopy(index);omitted.pop(next(iter(omitted)))
 refuse('incomplete-retained-member-population',lambda:v.archive_evidence(archive,proof['archive'],omitted,baseline))
 corrupted=copy.deepcopy(index);name=next(n for n in index if n.endswith('/diagnostics/worker-status.json'));corrupted[name]['sha256']='0'*64
 refuse('changed-raw-worker-report-binding',lambda:v.archive_evidence(archive,proof['archive'],corrupted,baseline))
 isolated=out/'changed-source';isolated.mkdir();(isolated/'run.py').write_bytes((HERE/'run.py').read_bytes()+b'\n# deliberately changed isolated source\n')
 refuse('changed-qualified-source',lambda:v.require_sources(isolated,{'run.py':v.pin(HERE/'run.py')}))
 positive=v.archive_evidence(archive,proof['archive'],index,baseline)
 assert v.pin(archive)==original and len(controls)==4
 result=dict(status='PASS_REVISION2_FROZEN_VERIFIER_BASELINE_AND_FOUR_REFUSALS',refusal_controls=controls,baseline=positive,original_archive_unchanged=True,original_scientific_sources_changed=False,new_tool_credit=0,whole_goal_complete=False)
 (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['status'],flush=True)
if __name__=='__main__':main()
