#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read back every frozen SingleR record and qualified source; repeat no analysis."""
from pathlib import Path
import argparse,importlib.util,sys,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/singler_workflows'))
from Runtime import ACTION,load,pin,save,check_output
spec=importlib.util.spec_from_file_location('singler_logical_evidence',ROOT/'tools/literature/scrattchbigcat005_workflows/Evidence.py')
evidence=importlib.util.module_from_spec(spec);spec.loader.exec_module(evidence)
DATA=ROOT/'docs/literature/singler-workflow-validation'

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--evidence',type=Path,default=DATA);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.absolute();owned=False
    try:
        out=check_output(out,[HERE,a.evidence,ROOT/'tools/literature/singler_workflows']);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nRecord integrity is separate from fresh original scientific replay.\n')
        proof=load(a.evidence/'QUALIFICATION.json')
        if proof['status']!='PASS_SCOPED_FIVE_ORIGINAL_SINGLER_RELEASES_AND_COMPLETE_RECORDING':raise ValueError('Unsupported SingleR qualification')
        for name,identity in proof['source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Qualified source differs: '+name)
        index=evidence.extract(a.evidence/'complete-validation-records.zip',out/'records',proof)
        for name,identity in proof['source_files'].items():
            if pin(out/'records/qualified-project-sources'/name)!=identity:raise ValueError('Complete frozen source differs: '+name)
        result=dict(status='PASS_COMPLETE_FROZEN_SINGLER_RECORD_INTEGRITY_NOT_NEW_SCIENTIFIC_COMPUTATION',
          logical_files=len(index['files']),unique_payloads=len(index['blobs']),qualified_source_files=len(proof['source_files']),
          all_archive_members_checked=True,scientific_calls_repeated=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
    except BaseException as e:
        code=3 if isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2
        label='SKIP3_SINGLER_RECORDS_UNAVAILABLE' if code==3 else 'FAIL2_SINGLER_RECORDS_UNADMITTED'
        print(label,repr(e),ACTION,flush=True)
        if owned:
            try:save(out/'FAILURE.json',dict(status=label,cause=repr(e),traceback=traceback.format_exc(),required_action=ACTION))
            except BaseException as recording:print('FAIL2_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
