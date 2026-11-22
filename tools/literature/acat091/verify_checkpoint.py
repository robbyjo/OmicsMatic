"""Read the frozen ACAT checkpoint; this does not execute R or rerun tests."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[3]
FOLDER=ROOT/'docs/literature/acat091-development'

def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def require(condition,message):
    if not condition: raise ValueError(message)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,help='Optional exact qualified local JAR to hash; not executed')
    args=parser.parse_args()
    q=json.loads((FOLDER/'QUALIFICATION.json').read_bytes())
    require(q['status']=='PASS_NATIVE_ACAT091_COMPONENT_CHECKPOINT','Wrong checkpoint status')
    require(q['whole_goal_complete'] is False and q['new_catalog_credit']==0,'Unexpected goal or credit claim')
    require(q['normal_build']['tests']==500 and q['normal_build']['failures']==0,'Incomplete normal tests')
    require(q['packaged']['tests_each_JVM']==500,'Incomplete packaged tests')
    require(set(q['packaged']['hosts'])=={'windows21','windows25','linux21'},'Wrong qualified hosts')
    require(all(v['exit_code']==0 for v in q['packaged']['hosts'].values()),'Failed packaged host')
    for group in ['public_inputs','canonical_catalog_files','supplementary_files']:
        for name,wanted in q[group].items():
            path=ROOT/name
            require(not path.is_symlink() and path.is_file(),f'Missing regular checkpoint file: {name}')
            require(pin(path)==wanted,f'Changed frozen checkpoint file: {name}; use the exact checkpoint checkout')
    archive=FOLDER/'validation.zip'
    require(pin(archive)==q['validation_archive'],'Changed validation archive')
    with zipfile.ZipFile(archive) as source:
        require(len(source.namelist())==len(set(source.namelist())),'Duplicate archive names')
        require(set(source.namelist())==set(q['validation_members']),'Different validation population')
        for name,wanted in q['validation_members'].items():
            require(not name.startswith('/') and '..' not in Path(name).parts,'Unsafe archive member')
            raw=source.read(name)
            require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==wanted,f'Changed validation member: {name}')
    if args.jar: require(pin(args.jar)==q['producer'],'Different local JAR; retain it and supply the exact qualified producer')
    print('PASS frozen ACAT component: 500 saved tests per qualified JVM; source/catalog/archive bytes match.')
    print('Saved qualification readback only; no new R execution, model fit or test-suite execution.')
    print('146 scoped families; 1639 reviewed /1083 pending uses; zero new family credit; full goal unfinished.')
    return 0

if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError,zipfile.BadZipFile) as error:
        raise SystemExit('FAIL2 - CHECKPOINT NOT ADMITTED: '+str(error)+'; preserve evidence and use the exact checkpoint files.')
