"""Read saved global Scanpy qualification; optionally restore synthetic captures."""
from pathlib import Path, PurePosixPath
import argparse
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile

from scanpy_dispatch_transition import pin, require, verify as retained_enrichr
from scanpy_coverage import build as coverage

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/scanpy-dispatch-checks'
STATUS='PASS_SCANPY_GLOBAL_COMMAND_SAVED_QUALIFICATION'
ACTION=('SKIP3: restore the named exact permitted readable source/evidence; skip its dependent audit until restored. '
        'FAIL2: preserve changed/invalid evidence and diagnostics, correct the cause and retry NEW output. '
        'Do not refresh old hashes. Saved proof is not fresh scientific execution or authentication.')


class Missing(Exception): pass


def name(value):
    p=PurePosixPath(value)
    require(not p.is_absolute() and p.parts and p.as_posix()==value and
            all(part not in ['.','..'] for part in p.parts) and '\\' not in value and ':' not in value,
            'Unsafe evidence path')
    return value


def read(path,cap=32000000):
    try:
        if not path.is_file() or path.is_symlink(): raise Missing('Required regular source/evidence unavailable: '+str(path))
        require(path.stat().st_size<=cap,'Source/evidence capacity exceeded')
        data=path.read_bytes();require(len(data)<=cap,'Source/evidence grew beyond capacity');return data
    except (FileNotFoundError,PermissionError) as error: raise Missing(str(path)) from error


def verify(folder=FOLDER):
    folder=Path(folder);rawq=read(folder/'QUALIFICATION.json');q=json.loads(rawq)
    r=json.loads(read(folder/'READBACK.json'))
    require(pin(rawq)==r['qualification'] and q['status']==STATUS,'Qualification identity/status differs')
    for source,wanted in q['sources'].items():
        require(pin(read(ROOT/name(source)))==wanted,'Current source differs: '+source)
    archive=read(folder/'evidence.zip')
    require(pin(archive)==r['evidence_archive']==q['evidence_archive'],'Evidence archive identity differs')
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        members=z.namelist()
        require(len(members)==len(set(members)) and set(members)==set(q['evidence_payloads']),'ZIP population differs')
        require(sum(info.file_size for info in z.infolist())<=128000000,'ZIP decompression capacity exceeded')
        raw={name(n):z.read(n) for n in members}
    for n,wanted in q['evidence_payloads'].items(): require(pin(raw[n])==wanted,'Changed evidence payload: '+n)
    for host in ['windows21','windows25','linux21']:
        suites=[ET.fromstring(value) for n,value in raw.items() if n.startswith('junit/'+host+'/') and n.endswith('.xml')]
        total={key:sum(int(s.get(key,0)) for s in suites) for key in ['tests','failures','errors','skipped']}
        require(total==dict(tests=62,failures=0,errors=0,skipped=0),'Saved JVM test disposition differs')
    classes=json.loads(raw['producer/class-payloads.json'])
    require(len(classes)==20210 and sum(v['before']==v['after'] for v in classes.values())==20209,
            'Class preservation population differs')
    changed=[n for n,v in classes.items() if v['before']!=v['after']]
    require(changed==['org/jlinalg/cli/JLinAlgCli.class'],'Unexpected changed class')
    for edition in ['before','after']:
        require(pin(raw['producer/'+edition+'/JLinAlgCli.class'])==classes[changed[0]][edition],
                'Saved dispatcher class identity differs')
    comparison=json.loads(raw['original/COMPARISONS.json'])
    require(len(comparison['comparisons'])==52 and all(v['maximum_absolute_difference']==0
            for v in comparison['comparisons']),'Saved original comparisons differ')
    linux=json.loads(raw['linux/COMPLETED.json'])
    require(linux['status']=='PASS_NORMAL_GLOBAL_SCANPY_RUN_VERIFY_REPLAY_AND_REFUSALS' and
            all(p['exit_code']==p['expected'] for p in linux['processes']) and linux['wrong_python_skipped'],
            'Actual global process proof differs')
    for prefix in ['capture','replay-capture']:
        files={n[len(prefix)+1:]:data for n,data in raw.items() if n.startswith(prefix+'/')}
        seal=json.loads(files['capture-files.json'])
        require(set(seal['files'])==set(files)-{'capture-files.json'},'Derived capture population differs')
        for n,wanted in seal['files'].items():require(pin(files[n])['sha256']==wanted,'Derived capture seal differs')
        require(json.loads(files['DERIVATION.json'])['original_capture_modified'] is False,
                'Original capture preservation disposition differs')
    census=json.loads(read(ROOT/'docs/literature/scanpy-preprocessing-checks/CENSUS.json'))
    crosswalk=json.loads(read(ROOT/'docs/literature/scanpy-source-coverage/COVERAGE.json'))
    require(coverage(census)==crosswalk,'Current 22-role source crosswalk differs')
    prior=retained_enrichr()
    return dict(status='PASS_SAVED_SCANPY_GLOBAL_COMMAND_AND_22_ROLE_CROSSWALK',
                sources=len(q['sources']),payloads=len(raw),tests_per_JVM=62,
                original_array_comparisons=52,saved_unchanged_class_payloads=20209,source_roles=22,
                prior_enrichr=prior,fresh_JVM_execution=False,fresh_scientific_models_run=False,
                catalog_integrations=136,reviewed_uses=1462,pending_uses=1241,
                new_family_credit=0,whole_goal_complete=False),raw


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--packet',type=Path,default=FOLDER);parser.add_argument('--restore-capture',action='store_true')
    args=parser.parse_args()
    if args.out.exists():print('FAIL2: preserve evidence; choose NEW output. '+ACTION,file=sys.stderr);return 2
    args.out.mkdir(parents=True)
    try:
        result,raw=verify(args.packet)
        if args.restore_capture:
            for n,data in raw.items():
                if n.startswith(('capture/','replay-capture/')):
                    target=args.out/name(n);target.parent.mkdir(parents=True,exist_ok=True)
                    with target.open('xb') as f:f.write(data)
        result['capture_restored']=args.restore_capture
        (args.out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(Missing,FileNotFoundError,PermissionError)) else 2
        result=dict(status='SKIP3' if code==3 else 'FAIL2',error=str(error),required_action=ACTION,results_admitted=False)
        (args.out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status']+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
