"""Read/restore the saved native Scanpy component; no JVM or upstream execution."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
import scanpy_source_roles as source_roles

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT/'docs/literature/scanpy-preprocessing-checks'
COMPONENT = 'omicsmatic-scanpy-preprocessing-development.jar'
STATUS = 'PASS_SCANPY_SOURCE_CENSUS_AND_NATIVE_PREPROCESSING_COMPONENT'


def pin(raw): return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def require(value,message):
    if not value: raise ValueError(message)


def safe(name,directory=False):
    require(isinstance(name,str) and '\\' not in name and ':' not in name,'Unsafe member')
    value=name[:-1] if directory and name.endswith('/') else name
    p=PurePosixPath(value)
    require(p.parts and not p.is_absolute() and all(v not in ['.','..'] for v in p.parts)
            and p.as_posix()==value,'Unsafe member')
    return name


def archive(raw,expected):
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=z.namelist()
        require(len(names)==len(set(names)) and set(names)==set(expected),'Archive population differs')
        require(z.testzip() is None,'Archive CRC failed')
        result={}
        for n in names:
            safe(n,directory=z.getinfo(n).is_dir());b=z.read(n)
            require(pin(b)==expected[n],'Changed archive payload: '+n);result[n]=b
    return result


def verify(folder=FOLDER,primary_cache=None):
    folder=Path(folder);readback=json.loads((folder/'READBACK.json').read_bytes())
    for n,w in readback['artifacts'].items():
        require('/' not in n and '\\' not in n and n not in ['.','..'],'Unsafe artifact')
        require(pin((folder/n).read_bytes())==w,'Changed packet artifact: '+n)
    q=json.loads((folder/'QUALIFICATION.json').read_bytes())
    require(q['status']==STATUS and q['catalog_admitted'] is False and q['new_family_credit']==0
            and q['whole_goal_complete'] is False,'Wrong qualification disposition')
    for n,w in q['sources'].items():require(pin((ROOT/safe(n)).read_bytes())==w,'Changed source: '+n)
    payload=archive((folder/'evidence.zip').read_bytes(),q['evidence_payloads'])
    build=json.loads(payload['package/COMPLETED.json'])
    require(build['status']=='PASS_SCANPY_NATIVE_418_ORIGINAL_CASES_THREE_JVMS_AND_ISOLATED_PACKAGE'
            and build['existing_class_payloads_unchanged']==20182 and len(build['added_class_payloads'])==13
            and build['original_reference_cases']==418 and build['production_fallback_classpath'] is False,
            'Wrong source/package scope')
    for n,w in build['sources'].items():require(q['sources'].get(n)==w,'Qualified build source differs: '+n)
    component=payload['component/'+COMPONENT]
    require(pin(component)==build['component']==q['component'],'Component identity differs')
    entries=archive(component,build['component_payloads'])
    for n,w in {**build['added_class_payloads'],**build['shared_container_class_payloads']}.items():
        require(pin(entries[n])==w,'Changed component class: '+n)
    require(any('GPL' in n for n in entries) and any('seurat' in n.lower() and 'license' in n.lower() for n in entries)
            and any('fast_array_utils' in n and 'LICENSE' in n for n in entries),'Missing retained notices')
    totals={}
    for host in ['windows21','windows25','linux21']:
        total={k:0 for k in ['tests','failures','errors','skipped']}
        for n,b in payload.items():
            if n.startswith('package/reports-'+host+'/') and n.endswith('.xml'):
                node=ET.fromstring(b)
                for k in total:total[k]+=int(node.get(k,'0'))
        require(total==dict(tests=426,failures=0,errors=0,skipped=0),'Wrong test disposition: '+host)
        totals[host]=total
    fixture_raw=(ROOT/'src/test/resources/org/jlinalg/singlecell/scanpy-preprocessing/reference-20261119.zip').read_bytes()
    fixture_record=json.loads(payload['fixture/COMPLETED.json'])
    fixture=archive(fixture_raw,fixture_record['payloads'])
    for v,errors in [('1.9.3',10),('1.13.0',0)]:
        r=json.loads(fixture[v+'/REFERENCE.json']);require(r['version']==v and len(r['cases'])==201
            and r['source_errors']==errors,'Original case population differs')
        require(all(c.get('actual_evaluated_dtype')=='float64' for c in r['cases'] if c['operation']=='normalize_total'),
                'Float64 normalization declaration differs')
        require(all(c.get('error_class')=='ZeroDivisionError' and c['operation']=='scale' and c['shape'][0]==1
                    for c in r['cases'] if c['status']=='ERROR'),'Original source errors were reclassified')
    require(len(json.loads(fixture['CONTRACT-EXPECTATIONS.json']))==402,'Contract population differs')
    ranges=archive((ROOT/'src/test/resources/org/jlinalg/singlecell/scanpy-preprocessing/range-boundaries-20261119.zip').read_bytes(),
                   json.loads(payload['fixture/RANGE-QUALIFICATION.json'])['payloads'])
    require(len(json.loads(ranges['CONTRACT-EXPECTATIONS.json']))==16,'Wrong range-contract population')
    for v in ['1.9.3','1.13.0']:
        r=json.loads(ranges[v+'/REFERENCE.json'])
        require(len(r['cases'])==8 and r['source_errors']==0
                and all(c['actual_evaluated_dtype']=='float64' for c in r['cases']), 'Wrong original range cases')
    census=json.loads((folder/'CENSUS.json').read_bytes())
    require(census['status']=='PASS250_IDENTITIES_22_CURATED_SCANPY_ROLES_SOURCE_REVIEW_ONLY'
            and census['base_commit']==source_roles.BASE and len(census['primary_files'])==250
            and census['named_paragraphs']==53 and len(census['roles'])==22
            and census['canonical_metadata_written'] is False,'Wrong saved source census')
    require(census==json.loads(payload['census/windows/CENSUS.json'])==json.loads(payload['census/linux/CENSUS.json']),
            'Cross-host source census differs')
    require({r['paper_id'] for r in census['roles']}==set(source_roles.DECISIONS),'Wrong role identities')
    require([r['paper_id'] for r in census['roles'] if r['existing_review_preserved']]==['P061']
            and census['potential_new_review_records']==21 and census['observations_added']==[],
            'Wrong review preservation/credit')
    require(len({r['paper_id'] for r in census['primary_files']})==250
            and len({r['pmcid'] for r in census['primary_files']})==250,'Duplicate paper identity')
    for r in census['roles']:
        anchors,role,host,editions,operations,summary,caution=source_roles.DECISIONS[r['paper_id']]
        require(r['role']==role and r['declared_host']==host and r['reported_operations']==operations
                and r['use_summary']==summary and r['required_caution']==caution
                and [a['paragraph_index'] for a in r['anchors']]==anchors
                and [(e['paragraph_index'],e['literal'],e['source_context']) for e in r['reported_editions']]==editions,
                'Changed curated source decision: '+r['paper_id'])
        require(not r['author_runtime_executed'] and not r['exact_author_settings_established']
                and not r['whole_paper_reproduced'],'Wrong author-execution claim')
    old_before=json.loads(payload['reference/old-before.json']);old_after=json.loads(payload['reference/old-after.json'])
    require(old_before==old_after and len(old_before)==25252,'Historical runtime changed')
    previous=json.loads(payload['previous-catalog/COMPLETED.json'])
    require(previous['status']=='PASS_PREVIOUS_ENRICHR_CUMULATIVE_CATALOG_AFTER_SCANPY_ADDITIONS'
            and previous['result']['new_scoped_integrations']==136
            and previous['result']['reviewed_uses']==1462
            and previous['result']['current_manifest']['entries']==812,'Previous catalog disposition differs')
    if primary_cache is not None:require(source_roles.audit(primary_cache)==census,'Fresh primary census differs')
    return dict(status=STATUS,source_files_checked=len(q['sources']),evidence_payloads_checked=len(payload),
                component_payloads_checked=len(entries),tests=totals,original_reference_cases=418,
                source_XML_identities=250,source_roles=22,named_paragraphs=53,
                source_rescan_performed=primary_cache is not None,fresh_JVM_execution=False,
                fresh_upstream_execution=False,component=q['component'],catalog_admitted=False,
                new_family_credit=0,whole_goal_complete=False),component


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--packet',type=Path,default=FOLDER);p.add_argument('--restore-component',action='store_true')
    p.add_argument('--rescan',action='store_true');p.add_argument('--primary-cache',type=Path);a=p.parse_args();owned=False
    try:
        require(not a.out.exists(),'Choose a NEW output directory; preserve existing evidence')
        require(a.rescan==(a.primary_cache is not None),'Supply --rescan and --primary-cache together')
        a.out.mkdir(parents=True);owned=True;report,component=verify(a.packet,a.primary_cache if a.rescan else None)
        if a.restore_component:
            target=a.out/'component';target.mkdir();(target/COMPONENT).write_bytes(component)
            report['restored_component']='component/'+COMPONENT
        (a.out/'COMPLETED.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        print(STATUS+': saved evidence only; general workflow and catalog admission remain open');return 0
    except Exception as e:
        code=3 if isinstance(e,(FileNotFoundError,PermissionError,source_roles.MissingSource)) else 2
        action='Restore the exact named permitted dependency/evidence or retain the skipped audit. Preserve diagnostics and retry NEW output; do not refresh old hashes.'
        failure=dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(e),required_action=action,whole_goal_complete=False)
        if owned:(a.out/'FAILURE.json').write_text(json.dumps(failure,indent=2)+'\n',encoding='utf-8')
        print(failure['status']+': '+str(e)+'\n'+action,file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
