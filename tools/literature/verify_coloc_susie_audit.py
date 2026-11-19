"""Read back scoped native SuSiE source-audit/fix evidence; no R/JVM execution."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/coloc-susie-audit-checks'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify():
    proof=json.loads((FOLDER/'QUALIFICATION.json').read_bytes())
    if proof['status']!='PASS_SCOPED_NATIVE_SUSIE_SOURCE_AUDIT_AND_FIXES':raise ValueError('unexpected qualification status')
    for name,expected in proof['files'].items():
        path=(ROOT/name).resolve()
        if not path.is_relative_to(ROOT.resolve()):raise ValueError('unsafe source path')
        if pin(path.read_bytes())!=expected:raise ValueError('changed source/evidence: '+name)
    q=proof['qualification']
    if {row['host'] for row in q['tests']}!={'windows21','windows25','linux21'}:raise ValueError('incomplete JVM population')
    for row in q['tests']:
        if row['exit_code']!=0 or int(row['counts']['tests'])!=50 or any(int(row['counts'][k]) for k in ['skipped','failures','errors']):raise ValueError('incomplete source/boundary/regression checks')
    profiles=q['source_profiles']
    if profiles!={'total':28,'complete_source_matching':12,'weighted_regional_only':4,'stable_H3_difference':1,'single_variant_source_invalid_simplex':1,'explicit_null_refusals':4,'upstream_no_admissible_posterior_other_profiles':5,'both_refuse_zero_weight':1}:raise ValueError('different source coverage classification')
    if not 2<q['original_single_variant_hypothesis_sum']<2.001:raise ValueError('invalid raw source result was relabeled')
    if proof['new_family_credit']!=0 or proof['whole_goal_complete']:raise ValueError('component audit cannot admit the catalog or whole objective')
    total=0
    with zipfile.ZipFile(FOLDER/'evidence.zip') as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)) or set(names)!=set(proof['archive_members']):raise ValueError('different evidence population')
        for name in names:
            relative=PurePosixPath(name);expected=proof['archive_members'][name]
            if relative.is_absolute() or '..' in relative.parts or '\\' in name:raise ValueError('unsafe evidence member')
            total+=expected['bytes']
            if total>64_000_000 or expected['bytes']>16_000_000 or archive.getinfo(name).file_size!=expected['bytes']:raise ValueError('changed/oversized evidence')
            if pin(archive.read(name))!=expected:raise ValueError('changed member: '+name)
    return dict(status='PASS_FROZEN_NATIVE_SUSIE_AUDIT_FIXES_INTEGRITY',files_checked=len(proof['files']),archive_members=len(proof['archive_members']),
                complete_source_matching_profiles=12,total_source_profiles=28,literal_whole_package_parity=False,
                R_reexecuted=False,JVM_tests_reexecuted=False,catalog_metadata_written=False,new_family_credit=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path,help='NEW readback directory');args=parser.parse_args();owned=False
    try:
        if not __debug__:raise ValueError('run without Python -O')
        args.out.mkdir(parents=True,exist_ok=False);owned=True;result=verify()
        (args.out/'READBACK.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(result['status'])
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        result=dict(status='SKIP3_SUSIE_AUDIT_READBACK_SKIPPED' if code==3 else 'FAIL2_NO_SUSIE_AUDIT_READBACK_ADMITTED',error=repr(error),
                    required_action='Restore the exact named source/evidence prerequisite for SKIP3. Retain changed bytes, partials and errors for FAIL2, correct the cause and retry a NEW output. Never refresh old hashes or relabel native variants/invalid original outputs as source parity.')
        if owned:(args.out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result,file=sys.stderr);raise SystemExit(code)
