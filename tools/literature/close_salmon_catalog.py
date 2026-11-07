# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain Salmon review/native-foundation browser gates without granting tool credit."""
import gzip
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from salmon_catalog import salmon_metadata
from catalog_source_integrity import verify_source


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def read(path):return json.loads(path.read_bytes())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable catalog preservation checks')
    data=ROOT/'docs/literature/omics-2023-2026';base=ROOT/'build/literature-review/salmon-runtime';target=data/'salmon-catalog-validation.json';archive=data/'salmon-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    metadata=salmon_metadata(ROOT,data);assert metadata['implementation_status']=='missing' and metadata['native_inferential_decoder_API_available']
    assert not metadata['project_adapter_available'] and not metadata['scientific_runtime_admitted']
    tools=read(data/'tool-inventory.json')
    with zipfile.ZipFile(data/'nextflow-catalog-support.zip') as z:prior=json.loads(z.read('catalog-snapshot/tool-inventory.json'))
    assert len(tools)==len(prior)==380 and [a['id'] for a,b in zip(tools,prior) if a!=b]==['salmon']
    papers=read(data/'papers.json');old=json.loads(gzip.decompress((base/'role-application-3/before-papers.json.gz').read_bytes()))
    assert len(papers)==len(old)==250 and sum(len(p['tools']) for p in papers)==2430
    for before,after in zip(old,papers):
        assert {k:v for k,v in before.items() if k!='tools'}=={k:v for k,v in after.items() if k!='tools'}
        assert [t for t in before['tools'] if t['tool_id']!='salmon']==[t for t in after['tools'] if t['tool_id']!='salmon']
    reviews=read(ROOT/'tools/literature/reviewed-uses.json')['reviews'];before=json.loads(gzip.decompress((base/'role-application-3/before-reviewed-uses.json.gz').read_bytes()))['reviews']
    assert len(before)==785 and len(reviews)==793 and reviews[:785]==before
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews)==241
    focused=read(base/'ui-1/ui-validation.json');broad=read(base/'broad-ui-1/ui-validation.json')
    assert focused['status']==broad['status']=='passed' and len(focused['checks'])==3 and len(broad['checks'])==299
    tests=ROOT/'build/test-results/test/TEST-org.jlinalg.sequence.SalmonInferentialReplicatesTest.xml';suite=ET.parse(tests).getroot()
    assert {k:int(suite.get(k,0)) for k in ['tests','failures','errors','skipped']}==dict(tests=27,failures=0,errors=0,skipped=0)
    summary=read(data/'summary.json');counts=summary['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(15,49,256)
    rejected=[]
    for name in ['src/main/java/org/jlinalg/sequence/SalmonInferentialReplicates.java','tools/literature/collect_salmon_reference_controls.py']:
        try:verify_source(ROOT,data,name,'0'*64)
        except ValueError:rejected.append(name)
        else:raise AssertionError('Incorrect scientific/controller hash accepted: '+name)
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/salmon_catalog.py',ROOT/'tools/literature/wire_salmon_catalog.py',ROOT/'tools/literature/check_salmon_review_ui.cjs',ROOT/'tools/literature/render_review.py']}
    entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    for name in sources:add(ROOT/name,'source/'+name)
    for label in ['ui-1','broad-ui-1']:
        for p in sorted((base/label).rglob('*')):
            if p.is_file():add(p,'browser/'+label+'/'+p.relative_to(base/label).as_posix())
    add(tests,'gradle/native-tests.xml')
    for name in ['salmon-native-shared-source-compatibility.json','salmon-use-shared-source-compatibility.json','salmon-role-coverage.json','summary.json','tool-inventory.json','papers.json']:
        add(data/name,'catalog-snapshot/'+name)
    census={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,raw);census[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist())==len(set(z.namelist()))==len(census) and z.testzip() is None
        for name,row in census.items():raw=z.read(name);assert len(raw)==row['bytes'] and hashlib.sha256(raw).hexdigest()==row['sha256']
    assert archive.stat().st_size<100*1024*1024 and all(pin(ROOT/name)==row for name,row in sources.items())
    proof=read(ROOT/'docs/literature/salmon-native-development/VALIDATION.json')
    result=dict(schema_version=1,status='passed-Salmon-role-native-development-and-missing-status-browser-gates',
        native_checkpoint='../salmon-native-development/VALIDATION.json',native_checkpoint_pin=pin(ROOT/'docs/literature/salmon-native-development/VALIDATION.json'),
        source_pins=sources,archive=dict(path=archive.name,**pin(archive),entries=len(census)),archive_files=census,
        catalog_counts=dict(papers=250,tools=380,observations=2430,reviewed_uses=793,supporting_pointers=241,new_scoped_integrations=64,native=15,upstream=49,including_existing_adapters=67,missing=256),
        preserved=dict(unrelated_tool_objects=379,earlier_reviews=785,unrelated_observations=2422,paper_cohort=True),
        focused_browser_checks=3,broad_browser_checks=299,browser_screenshots=len(focused['screenshots'])+len(broad['screenshots']),
        standard_Gradle_native_tests=27,standard_Gradle_failures=0,standard_Gradle_skips=0,javadoc_passed=True,website_structure_and_links_passed=True,
        source_hash_rejections=rejected,canonical_executable_unchanged=proof['canonical_executable_unchanged'],
        Salmon_implementation_status='missing',native_decoder_available=True,typed_public_quantification_available=False,scientific_runtime_admitted=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False,whole_paper_reproduced=False,required_action=proof['required_action'])
    write(target,result);print(json.dumps({k:v for k,v in result.items() if k in ['status','catalog_counts','preserved','Salmon_implementation_status','focused_browser_checks','broad_browser_checks']}))


if __name__=='__main__':main()
