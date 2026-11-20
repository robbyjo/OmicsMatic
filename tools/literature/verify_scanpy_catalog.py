"""Read saved Scanpy admission proof; no private cache, original-owner process or scientific refit."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/scanpy-catalog-checks'
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
from scanpy_catalog.model import META,DATA,check
from scanpy_catalog.catalog import PROOF,STATUS,source_pins
from scanpy_catalog.coverage import require
from catalog_source_integrity import reverse_delta

ACTION=('SKIP3: restore the exact named permitted source/evidence; otherwise skip the dependent audit. '
        'FAIL2: retain changed evidence and diagnostics, correct the cause and retry NEW output. '
        'Never refresh saved hashes. This checks saved integrity, not fresh models, original-owner execution, '
        'biological validity or a signature against coordinated replacement.')


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def saved_manifest():
    """Audit the same manifest using its pinned predecessor list, without a Git lookup."""
    data=ROOT/DATA
    previous=(ROOT/'docs/literature/scanpy-catalog-admission/PREVIOUS-SHA256SUMS').read_text().splitlines()
    legacy={line.split('  ',1)[1] for line in previous if '/' in line.split('  ',1)[1]}
    raw=(data/'SHA256SUMS').read_bytes();names=[]
    for line in raw.decode().splitlines():
        digest,name=line.split('  ',1)
        require((Path(name).name==name or name in legacy) and (data/name).resolve().is_relative_to(ROOT/'docs/literature')
                and re.fullmatch('[a-f0-9]{64}',digest),'Invalid checksum member')
        require(hashlib.sha256((data/name).read_bytes()).hexdigest()==digest,'Changed manifest member: '+name)
        names.append(name)
    expected={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}|legacy
    require(len(names)==len(set(names))==815 and set(names)==expected,'Manifest population differs')
    return dict(entries=len(names),manifest=pin(raw))


def verify(folder=FOLDER):
    folder=Path(folder)
    require((folder/'QUALIFICATION.json').stat().st_size<=4194304,'Qualification capacity exceeded')
    q=json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(q['schema_version']==2 and q['status']=='PASS_SCANPY_CUMULATIVE_ADMISSION_SAVED_CHECKS','Different qualification')
    for name,wanted in q['sources'].items():
        require(not Path(name).is_absolute() and '..' not in Path(name).parts,'Unsafe source path')
        require(pin((ROOT/name).read_bytes())==wanted,'Changed source: '+name)
    require((folder/'evidence.zip').stat().st_size<=134217728,'Archive file capacity exceeded')
    raw=(folder/'evidence.zip').read_bytes();require(pin(raw)==q['archive'],'Changed archive')
    for name,wanted in q['retained_first_attempt'].items():
        require(name.startswith('attempt-1/') and '..' not in Path(name).parts,'Unsafe retained-attempt path')
        require(pin((folder/name).read_bytes())==wanted,'Changed retained first attempt: '+name)
    payload={}
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=z.namelist();require(len(names)==len(set(names)) and set(names)==set(q['payloads']),'Different archive members')
        require(sum(n.file_size for n in z.infolist())<=134217728,'Archive capacity exceeded')
        for name,wanted in q['payloads'].items():
            require(not name.startswith(('/', '\\')) and ':' not in name and '\\' not in name and '..' not in Path(name).parts,'Unsafe member')
            payload[name]=z.read(name);require(pin(payload[name])==wanted,'Changed payload: '+name)
    proof=json.loads((ROOT/PROOF).read_bytes())
    require(pin((ROOT/PROOF).read_bytes())==q['admission_proof'] and proof['status']==STATUS
            and proof['source_code']==source_pins(),'Changed admission proof or owner')
    for field in ['evidence','foundation_sources']:
        for name,wanted in proof[field].items():require(pin((ROOT/name).read_bytes())==wanted,'Changed retained admission file: '+name)
    with zipfile.ZipFile(ROOT/'docs/literature/scanpy-catalog-admission/catalog-before.zip') as z:
        require(len(z.namelist())==8 and set(z.namelist())==set(META),'Different before archive')
        before={n:z.read(n) for n in META}
    after={n:(ROOT/n).read_bytes() for n in META}
    require(proof['before']=={n:pin(v) for n,v in before.items()} and proof['after']=={n:pin(v) for n,v in after.items()},'Before/after identities differ')
    census=json.loads((ROOT/'docs/literature/scanpy-preprocessing-checks/CENSUS.json').read_bytes())
    result=check(before,after,census,proof['component_qualification'])
    require(all(proof.get(k)==v for k,v in result.items()),'Recorded result differs')
    compat=json.loads((ROOT/DATA/'scanpy-catalog-shared-source-compatibility.json').read_bytes())
    require(len(compat['paths'])==1 and compat['paths'][0]['path']==META[5]
            and reverse_delta(after[META[5]],compat['paths'][0])==before[META[5]],'Exact review inverse differs')
    for label in ['windows','linux']:
        text=payload['tests-2/'+label+'.stderr'].decode('utf-8')
        require(re.search(r'Ran 25 tests in ',text) is not None and text.rstrip().endswith('OK'),'Different passing test disposition')
    require(b'Ran 0 tests' in payload['tests-1/linux.stderr'] and b'not a git repository' in payload['tests-1/linux.stderr'],
            'Original Linux Git-context failure not retained')
    controls=json.loads(payload['controls/COMPLETED.json'])
    require(len(controls['cases'])==6 and all(r['exit_code']==r['expected'] for r in controls['cases'])
            and controls['canonical_metadata_preserved'] is True,'Refusal controls differ')
    for name in ['apply','verify']:
        r=json.loads(payload['formal/'+name+'-COMPLETED.json'])
        require(r['status']==STATUS and r['reviewed_uses']==1483 and r['current_manifest']['entries']==815,
                'Actual '+name+' disposition differs')
    require(json.loads(payload['ui/COMPLETED.json'])['status']=='PASS_LOCAL_SCANPY_CATALOG_DESKTOP_MOBILE_FILTERS_AND_WARNINGS',
            'Local UI disposition differs')
    require(json.loads(payload['checkout/CHECKOUT.json'])['total_worktrees']==23,'Checkout disposition differs')
    manifest=saved_manifest();require(manifest['entries']==815,'Current manifest population differs')
    return dict(status='PASS_SAVED_SCANPY_CATALOG_ADMISSION_PROOF',**result,manifest_entries=815,
        tests_per_host=25,hosts=['Windows','Linux'],refusal_controls=6,sources_checked=len(q['sources']),
        evidence_payloads_checked=len(payload),fresh_original_owners_executed=False,
        fresh_scientific_models_run=False,required_action=ACTION)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--packet',type=Path,default=FOLDER);args=parser.parse_args();owned=False
    try:
        require(__debug__ and sys.version_info>=(3,11),'Require Python 3.11+ without -O')
        require(not args.out.exists(),'Preserve existing evidence and choose NEW output')
        args.out.mkdir(parents=True);owned=True;result=verify(args.packet)
        (args.out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        result=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),required_action=ACTION,complete_result=False)
        if owned:(args.out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status']+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
