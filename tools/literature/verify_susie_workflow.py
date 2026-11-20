"""Read the saved SuSiE handover packet, without R, Git, network or refitting.

Exit 0: pinned evidence and saved dispositions agree; 3: named prerequisite
missing/forbidden; 2: changed or inconsistent evidence. Hashes are integrity,
not authentication. No scientific model is executed or independently verified.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER=Path('docs/literature/susie-workflow-checks')


def require(condition,message):
    if not condition:raise ValueError(message)


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    p=PurePosixPath(name)
    require(name and not p.is_absolute() and '..' not in p.parts and '\\' not in name and ':' not in name,
            'Unsafe evidence path: '+name)
    return p


def verify(root=ROOT):
    folder=root/FOLDER
    proof=json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(proof['status']=='QUALIFIED_SCOPED_SUSIE_ADAPTER_CATALOG_ADMISSION_OPEN','Unexpected qualification status')
    for name,wanted in proof['source_files'].items():
        safe(name);require(pin((root/name).read_bytes())==wanted,'Changed source or fixture: '+name)
    raw=(folder/'evidence.zip').read_bytes()
    require(pin(raw)==proof['packet'],'Changed evidence.zip')
    with zipfile.ZipFile(folder/'evidence.zip') as z:
        names=z.namelist();require(len(names)==len(set(names)),'Duplicate ZIP members')
        require(sum(i.file_size for i in z.infolist())<=128*1024*1024,'Evidence exceeds saved size limit')
        index=json.loads(z.read('INDEX.json'));pins=index['blob_pins']
        require(set(names)=={'INDEX.json'}|{'blobs/'+sha for sha in pins},'Different ZIP inventory')
        for sha,wanted in pins.items():
            require(len(sha)==64 and all(c in '0123456789abcdef' for c in sha),'Invalid blob name')
            require(pin(z.read('blobs/'+sha))==wanted and wanted['sha256']==sha,'Changed blob: '+sha)
        def blob(wanted):
            require(pins.get(wanted['sha256'])==wanted,'Unpinned reference')
            return z.read('blobs/'+wanted['sha256'])
        def receipt(name):return json.loads(blob(index['receipts'][name]))
        cases=index['cases'];require(len(cases)==41 and len({c['name'] for c in cases})==41,'Different case population')
        require({c['version'] for c in cases}=={'0.11.92','0.12.35','0.14.2'},'Different edition population')
        require(sum(c['project_exit']==0 for c in cases)==38 and sum(c['project_exit']==2 for c in cases)==3,'Different model dispositions')
        comparison=receipt('original-model-comparison-2/COMPLETED.json')
        require(comparison['cases']==41 and not comparison['scientific_source_overlay'],'Different comparison scope')
        for case in cases:
            row=comparison['models'][case['name']]
            require(row['passed'] and not row['differences'] and row['maximum_numeric_absolute_error']==0,'Saved original comparison failed')
            require(case['original_converged']==(case['project_exit']==0),'Wrong nonconvergence disposition')
            for name,wanted in case['capture'].items():safe(name);blob(wanted)
            for name,wanted in case['inputs'].items():safe(name);blob(wanted)
            blob(case['original_model']);blob(case['original_status'])
        final=receipt('java-tests-2/COMPLETED.json')['results']
        first=receipt('java-tests-1/RESULTS.json')
        require(set(final)==set(first)=={'windows21','windows25','linux21'},'Different JVM population')
        for name,row in final.items():
            require(row['exit_code']==0 and row['totals']==dict(tests=73,errors=0,failures=0,skipped=0),'Saved final JVM checks failed: '+name)
            require(first[name]['totals']==dict(tests=73,errors=0,failures=1,skipped=0),'First reporting failure was not retained')
        for name,wanted in index['receipts'].items():safe(name);blob(wanted)
        census=json.loads((folder/'CENSUS.json').read_bytes())
        require(census['positive_roles']==20 and census['new_observations']==['P073','P203'] and census['false_positive_observations_to_remove']==['P235'],'Different source census')
        require(not census['canonical_metadata_written'] and not census['new_family_credit'],'False catalog credit')
        require(blob(index['receipts']['source-census-2/CENSUS.json'])==(folder/'CENSUS.json').read_bytes()==blob(index['receipts']['source-census-linux-1/CENSUS.json']),'Different host census')
        require(proof['admitted_totals']==dict(papers=250,observations=2703,reviewed=1483,pending=1220,new_scoped_integrations=137,native=25,upstream=112),'Different admitted totals')
        return dict(status='PASS_SAVED_SUSIE_HANDOVER_EVIDENCE',cases=41,converged=38,nonconverged_refused=3,
                    tests_per_jvm=73,jvms=3,source_roles=20,catalog_admission_open=True,
                    scientific_models_executed=False,independent_model_verification=False,authentication=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT)
    args=parser.parse_args()
    try:
        require(__debug__ and sys.version_info>=(3,11),'Use Python 3.11+ without -O')
        print(json.dumps(verify(args.root),indent=2));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        print(('SKIP3: ' if code==3 else 'FAIL2: ')+str(error)+
              '\nREQUIRED ACTION: restore the named exact permitted file, or retain the changed evidence and investigate. No model result is admitted. Do not refresh old hashes to hide changes.',file=sys.stderr)
        return code


if __name__=='__main__':raise SystemExit(main())
