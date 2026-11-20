"""Verify saved native dispersion-HVG proof; optionally restore the Java library."""
from pathlib import Path, PurePosixPath
import argparse
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile

from verify_scanpy_pearson import read, pin, unpack, Missing

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/scanpy-hvg-checks'
STATUS='PASS_NATIVE_SCANPY_DISPERSION_HVG_SAVED_QUALIFICATION'
ACTION=('SKIP3: restore the exact named permitted readable source/evidence dependency and retry NEW output; '
        'skip the dependent audit until restored. FAIL2: preserve changed/invalid evidence and diagnostics, '
        'correct the cause and retry NEW output. Do not refresh old hashes. Saved proof is not a fresh '
        'scientific rerun, biological validation or authentication against coordinated replacement.')


def require(condition,message):
    if not condition:raise ValueError(message)


def name(value):
    p=PurePosixPath(value)
    require(not p.is_absolute() and p.parts and p.as_posix()==value and ':' not in value and '\\' not in value
            and all(part not in ['.','..'] for part in p.parts),'Unsafe source/evidence member')
    return value


def verify(folder=FOLDER):
    folder=Path(folder);rawq=read(folder/'QUALIFICATION.json');q=json.loads(rawq);r=json.loads(read(folder/'READBACK.json'))
    require(pin(rawq)==r['qualification'] and q['status']==STATUS,'Qualification identity/status differs')
    for n,wanted in q['sources'].items():require(pin(read(ROOT/name(n)))==wanted,'Current source differs: '+n)
    archive=read(folder/'evidence.zip')
    require(pin(archive)==q['evidence_archive']==r['evidence_archive'],'Evidence archive identity differs')
    for n in q['evidence_payloads']:name(n)
    raw=unpack(archive,q['evidence_payloads'],cap=128000000)
    component=raw['component/omicsmatic-scanpy-hvg-development.jar']
    require(pin(component)==q['component'],'Java component identity differs')
    for n in q['component_payloads']:name(n)
    unpack(component,q['component_payloads'])
    require(raw['jdeps/stdout'].strip()==b'java.base','Native component acquired an external module dependency')
    for producer in ['normal','component']:
        for host in ['windows21','windows25','linux21']:
            suites=[ET.fromstring(value) for n,value in raw.items() if n.startswith('junit/'+producer+'/'+host+'/') and n.endswith('.xml')]
            total={k:sum(int(s.get(k,0)) for s in suites) for k in ['tests','failures','errors','skipped']}
            require(total==dict(tests=2574,failures=0,errors=0,skipped=0),'Saved JVM disposition differs')
    producer=json.loads(raw['producer/PRODUCER.json'])
    require(producer['normal_candidate']==q['normal_candidate'] and producer['component']==q['component']
            and producer['existing_class_payloads_unchanged']==20210
            and producer['all_preceding_member_payloads_unchanged'] and producer['new_native_classes']==12,
            'Normal build/component preservation scope differs')
    fixture=read(ROOT/name(q['reference_fixture']['path']))
    require(pin(fixture)==q['reference_fixture']['pin'],'Original reference fixture differs')
    with zipfile.ZipFile(io.BytesIO(fixture)) as z:
        require(set(z.namelist())=={'INPUTS.json','1.9.3.json','1.13.0.json','PROVENANCE.json'}
                and len(z.namelist())==4,'Original fixture population differs')
        require(sum(i.file_size for i in z.infolist())<128000000,'Reference decompression capacity exceeded')
        inputs=json.loads(z.read('INPUTS.json'));ids=[c['id'] for c in inputs['cases']]
        require(len(ids)==len(set(ids))==1280 and len(inputs['matrices'])==17,'Original input population differs')
        counts={}
        for version in ['1.9.3','1.13.0']:
            c=json.loads(z.read(version+'.json'))
            require(c['scanpy_version']==version and not c['source_patched']
                    and [item['id'] for item in c['cases']]==ids,'Original case identity/source scope differs')
            counts[version]={status:sum(item['status']==status for item in c['cases'])
                             for status in ['original-returned','original-error']}
            require(sum(counts[version].values())==1280,'Unknown original case disposition')
            if version=='1.13.0':require(c['source_batch_accessor']=='obs.batch','Current original accessor binding differs')
        require(counts=={'1.9.3':{'original-returned':778,'original-error':502},
                         '1.13.0':{'original-returned':854,'original-error':426}},'Original outcome population differs')
    for version,entries in [('1.9.3',25252),('1.13.0',19048)]:
        before=json.loads(raw['runtime/'+version+'-before.json']);after=json.loads(raw['runtime/'+version+'-after.json'])
        require(before==after and len(before)==entries,'Original reference runtime changed')
    return dict(status='PASS_SAVED_NATIVE_DISPERSION_HVG_PROOF',sources=len(q['sources']),payloads=len(raw),
                tests_per_JVM_per_producer=2574,original_cases=2560,admission_controls=14,
                original_outcomes=counts,reference_runtime_entries_unchanged=44300,
                fresh_JVM_execution=False,fresh_scientific_models_run=False,new_family_credit=0,
                whole_tool_qualified=False,whole_goal_complete=False),component


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--packet',type=Path,default=FOLDER);parser.add_argument('--restore-component',action='store_true')
    args=parser.parse_args()
    if args.out.exists():print('FAIL2: preserve evidence and choose NEW output. '+ACTION,file=sys.stderr);return 2
    args.out.mkdir(parents=True)
    try:
        result,component=verify(args.packet)
        if args.restore_component:
            target=args.out/'component';target.mkdir()
            with (target/'omicsmatic-scanpy-hvg-development.jar').open('xb') as f:f.write(component)
        result['component_restored']=args.restore_component
        (args.out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status']);return 0
    except Exception as error:
        code=3 if isinstance(error,(Missing,FileNotFoundError,PermissionError)) else 2
        result=dict(status='SKIP3' if code==3 else 'FAIL2',error=str(error),required_action=ACTION,results_admitted=False)
        (args.out/'FAILURE.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(result['status']+': '+str(error)+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
