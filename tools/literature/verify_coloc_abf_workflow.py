"""Read back frozen native ABF workflow qualification; no R/JVM execution."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/coloc-abf-workflow-checks'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(out,restore):
    proof=json.loads((FOLDER/'QUALIFICATION.json').read_bytes())
    if proof['status']!='PASS_SCOPED_NATIVE_ABF_WORKFLOW_QUALIFICATION':raise ValueError('unexpected qualification status')
    for name,expected in proof['files'].items():
        path=(ROOT/name).resolve()
        if not path.is_relative_to(ROOT.resolve()):raise ValueError('unsafe live source path')
        if pin(path.read_bytes())!=expected:raise ValueError('changed source/evidence: '+name)
    tests=proof['qualification']['tests']
    if {r['host'] for r in tests}!={'windows21','windows25','linux21'}:raise ValueError('incomplete JVM population')
    for row in tests:
        if row['exit_code']!=0 or int(row['counts']['tests'])!=62 or any(int(row['counts'][k]) for k in ['skipped','failures','errors']):raise ValueError('incomplete reference/control tests')
    if proof['new_family_credit']!=0 or proof['whole_goal_complete']:raise ValueError('component proof cannot admit the catalog or whole objective')
    restored=0
    with zipfile.ZipFile(FOLDER/'evidence.zip') as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)) or set(names)!=set(proof['archive_members']):raise ValueError('different evidence population')
        total=0
        for name in names:
            relative=PurePosixPath(name)
            if relative.is_absolute() or '..' in relative.parts or '\\' in name:raise ValueError('unsafe archive member')
            expected=proof['archive_members'][name]
            if archive.getinfo(name).file_size!=expected['bytes'] or expected['bytes']>16_000_000:raise ValueError('changed/oversized member')
            total+=expected['bytes']
            if total>64_000_000:raise ValueError('complete archive exceeds capacity')
            raw=archive.read(name)
            if pin(raw)!=expected:raise ValueError('changed archive member: '+name)
            if restore and name.startswith('bundles/'):
                target=out.joinpath(*relative.parts);target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(raw)
                restored+=1
    return dict(status='PASS_FROZEN_NATIVE_ABF_WORKFLOW_QUALIFICATION_INTEGRITY',files_checked=len(proof['files']),
                archive_members=len(proof['archive_members']),restored_bundle_files=restored,
                JVM_tests_reexecuted=False,original_R_reexecuted=False,catalog_metadata_written=False,
                new_family_credit=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,type=Path,help='NEW readback directory')
    parser.add_argument('--restore-bundles',action='store_true',help='also restore exact synthetic bundles under the new readback directory')
    args=parser.parse_args();owned=False
    try:
        if not __debug__:raise ValueError('run without Python -O')
        args.out.mkdir(parents=True,exist_ok=False);owned=True
        result=verify(args.out,args.restore_bundles)
        (args.out/'READBACK.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(result['status'])
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        result=dict(status='SKIP3_QUALIFICATION_READBACK_SKIPPED' if code==3 else 'FAIL2_NO_QUALIFICATION_READBACK_ADMITTED',error=repr(error),
                    required_action='Restore the exact named source/evidence prerequisites for SKIP3. Retain changed bytes, partials and errors for FAIL2, correct the cause and retry a NEW output. Never refresh historical hashes to conceal a change.')
        if owned:(args.out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result,file=sys.stderr);raise SystemExit(code)
