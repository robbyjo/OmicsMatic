"""Verify the compact COLOC handover bytes; does not rerun R or qualify a workflow."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/coloc-abf-handover'

def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def verify():
    proof=json.loads((FOLDER/'SNAPSHOT.json').read_bytes())
    if proof['status']!='TESTED_NATIVE_COMPONENT_CATALOG_ADMISSION_OPEN':
        raise ValueError('unexpected snapshot status')
    for name,expected in proof['files'].items():
        path=(ROOT/name).resolve()
        if not path.is_relative_to(ROOT.resolve()):raise ValueError('unsafe snapshot path')
        if pin(path)!=expected:raise ValueError('changed snapshot file: '+name)
    for row in proof['jvm_tests']['records']:
        counts=row['counts']
        if row['exit_code']!=0 or int(counts['tests'])!=40 or any(int(counts[k]) for k in ['skipped','failures','errors']):
            raise ValueError('incomplete JVM reference/boundary results')
    if proof['source_roles']!={'positive':27,'direct':24,'FUSION_hosted':2,'host_unspecified':1}:
        raise ValueError('different source-role population')
    if proof['new_family_credit']!=0 or proof['whole_goal_complete']:
        raise ValueError('handover cannot award catalog or whole-goal completion')
    return dict(status='PASS_COMPACT_COLOC_HANDOVER_INTEGRITY_ONLY',files_checked=len(proof['files']),
                tests_reexecuted=False,original_R_reexecuted=False,canonical_metadata_written=False,
                new_family_credit=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True,help='new directory for this readback')
    args=parser.parse_args();owned=False
    try:
        if not __debug__:raise ValueError('run without Python -O')
        args.out.mkdir(parents=True,exist_ok=False);owned=True
        result=verify()
        (args.out/'READBACK.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status'])
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        result=dict(status='SKIP3_HANDOVER_READBACK_SKIPPED' if code==3 else 'FAIL2_NO_HANDOVER_READBACK_ADMITTED',
                    error=repr(error),required_action='Restore exact named prerequisite bytes for SKIP3. Retain FAIL2 errors, correct the cause and retry a NEW output directory. Do not refresh frozen hashes to hide changes.')
        if owned:(args.out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result,file=sys.stderr);raise SystemExit(code)
