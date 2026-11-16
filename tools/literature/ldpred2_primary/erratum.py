#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Correct one P074 disease summary, retaining exact predecessor metadata."""
from pathlib import Path
import argparse,copy,gzip,hashlib,json,re,sys,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3]
EVIDENCE='docs/literature/p074-prs-disease-erratum';GUIDE='docs/literature/P074_PRS_DISEASE_ERRATUM.md'
OLD='ankylosing-spondylitis';NEW='aortic-stenosis';PMCID='PMC12807872'
STATUS='PASS_P074_AORTIC_STENOSIS_METADATA_ONLY_ERRATUM'
NOTICE=('P074 studies aortic stenosis. The prior PRS-CS summary incorrectly expanded AS as ankylosing spondylitis; one review and matching observation are corrected. '
        'Historical metadata and the unadmitted LDpred2 draft are retained. Estimators, numerical results, version labels, LD/tuning designs and all tool/review counts are unchanged. '
        'This correction adds zero implementation credit. Later LDpred2 source/catalog admission and the full250-paper implementation goal remain open.')
HTML='<section id="p074-disease-erratum" role="note"><h2>P074 disease-label correction</h2><p>'+NOTICE+'</p><p>Source: <a href="https://doi.org/10.1038/s41588-025-02417-6">the primary aortic-stenosis article</a>. Guide: <code>'+GUIDE+'</code>.</p></section>'
def encoded(x):return (json.dumps(x,ensure_ascii=False,indent=2)+'\n').encode()
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(p):return rawpin(p.read_bytes())
def save(p,obj):
    with p.open('xb') as f:f.write(encoded(obj))
def expected_before():return json.loads((ROOT/'docs/literature/ldpred2-native-development/STATUS.json').read_bytes())['catalog_metadata_pins']
def primary_check(papers):
    paper=next(p for p in papers if p['paper_id']=='P074');assert paper['pmcid']==PMCID
    raw=gzip.decompress((ROOT/'build/literature-review/xml'/(PMCID+'.xml.gz')).read_bytes());assert rawpin(raw)['sha256']==paper['xml_sha256']
    article=ET.fromstring(raw);title=''.join(article.find('.//article-title').itertext());abstract=' '.join(''.join(article.find('.//abstract').itertext()).split())
    assert 'aortic stenosis' in title.lower() and 'Aortic stenosis (AS)' in abstract
    return dict(paper_id='P074',pmcid=PMCID,doi=paper['doi'],title=title,xml_sha256=paper['xml_sha256'],abstract_defines_AS_as_aortic_stenosis=True)
def transition(before):
    papers=json.loads(before['docs/literature/omics-2023-2026/papers.json']);reviews=json.loads(before['tools/literature/reviewed-uses.json']);primary_check(papers)
    p=next(p for p in papers if p['paper_id']=='P074');observation=next(t for t in p['tools'] if t['tool_id']=='prscs');review=next(r for r in reviews['reviews'] if r['pmcid']==PMCID and r['tool_id']=='prscs')
    assert observation['use_summary']==review['use_summary'] and review['use_summary'].count(OLD)==1
    for row in [observation,review]:row['use_summary']=row['use_summary'].replace(OLD,NEW)
    after=dict(before)
    for name,value in [('docs/literature/omics-2023-2026/papers.json',papers),('tools/literature/reviewed-uses.json',reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    name='site/literature-review.html';page=before[name].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);assert match
    payload=json.loads(match[2]);assert payload==dict(papers=json.loads(before['docs/literature/omics-2023-2026/papers.json']),tools=json.loads(before['docs/literature/omics-2023-2026/tool-inventory.json']),summary=json.loads(before['docs/literature/omics-2023-2026/summary.json']))
    payload['papers']=papers;render=json.dumps(payload,ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+render+page[match.end(2):]
    anchor='<section id="sambamba-required-actions" role="note">';assert page.count(anchor)==1;after[name]=page.replace(anchor,HTML+anchor,1).encode()
    name='site/literature-warnings.html';page=before[name].decode();assert page.count(anchor)==1;after[name]=page.replace(anchor,HTML+anchor,1).encode()
    for name in ['docs/literature/omics-2023-2026/README.md','docs/literature/omics-2023-2026/WARNINGS.md']:
        intro=('# P074 disease-label erratum\n\n'+NOTICE+'\n\nRead [the source correction](../P074_PRS_DISEASE_ERRATUM.md). Current metadata-only check: `python -B tools/literature/ldpred2_primary/erratum.py --verify --out build/NEW-p074-check`. Historical cumulative verifiers below retain their original scope.\n\n<details><summary>Catalog checkpoint before this disease-only correction (historical)</summary>\n\n').encode();after[name]=intro+before[name]+b'\n</details>\n'
    return after
def check(before,after):
    assert after==transition(before),'Unexpected metadata change'
    oldpapers=json.loads(before['docs/literature/omics-2023-2026/papers.json']);newpapers=json.loads(after['docs/literature/omics-2023-2026/papers.json'])
    for a,b in zip(oldpapers,newpapers):
        expected=copy.deepcopy(a)
        if a['paper_id']=='P074':
            row=next(t for t in expected['tools'] if t['tool_id']=='prscs');row['use_summary']=row['use_summary'].replace(OLD,NEW)
        assert expected==b
    a=json.loads(before['tools/literature/reviewed-uses.json']);b=json.loads(after['tools/literature/reviewed-uses.json']);expected=copy.deepcopy(a)
    row=next(t for t in expected['reviews'] if t['pmcid']==PMCID and t['tool_id']=='prscs');row['use_summary']=row['use_summary'].replace(OLD,NEW);assert expected==b
    assert len(newpapers)==250 and len(b['reviews'])==1199 and sum(len(p['tools']) for p in newpapers)==2691
    return dict(papers=250,observations=2691,reviewed_uses=1199,named_tools=446,scoped_integrations=123,missing=264,new_catalog_credit=0,whole_goal_complete=False)
def apply(out):
    folder=ROOT/EVIDENCE;assert not folder.exists()
    before={n:(ROOT/n).read_bytes() for n in expected_before()};assert {n:rawpin(raw) for n,raw in before.items()}==expected_before()
    parent=ROOT/'build/ldpred2/catalog-predecessor-driver-1';assert json.loads((parent/'PROCESS.json').read_bytes())['exit_code']==0
    assert json.loads((ROOT/'build/ldpred2/command-qualification-1/COMPLETED.json').read_bytes())['all_numerical_and_worker_class_bytes_unchanged']
    after=transition(before);result=check(before,after);folder.mkdir();(folder/'.gitattributes').write_bytes(b'* -text\n')
    with zipfile.ZipFile(folder/'catalog-before.zip','x',zipfile.ZIP_DEFLATED) as z:
        for n,raw in before.items():z.writestr(n,raw)
    save(folder/'PRIMARY.json',primary_check(json.loads(before['docs/literature/omics-2023-2026/papers.json'])))
    for n in ['PROCESS.json','stdout','stderr']:shutil_copy=folder/('predecessor-'+n);shutil_copy.write_bytes((parent/n).read_bytes())
    save(out/'PREPARED.json',dict(before={n:rawpin(raw) for n,raw in before.items()},after={n:rawpin(raw) for n,raw in after.items()}))
    assert all((ROOT/n).read_bytes()==raw for n,raw in before.items()),'Concurrent metadata change'
    for n,raw in after.items():
        if raw!=before[n]:(ROOT/n).write_bytes(raw)
    sources=['tools/literature/ldpred2_primary/erratum.py','tools/literature/ldpred2_primary/source.py',GUIDE]
    proof=dict(status=STATUS,**result,before={n:rawpin(raw) for n,raw in before.items()},after={n:pin(ROOT/n) for n in after},sources={n:pin(ROOT/n) for n in sources},evidence={p.relative_to(folder).as_posix():pin(p) for p in folder.iterdir()},metadata_only=True,historical_verifiers_modified=False,current_development_source_cumulatively_requalified=False,exact_clean_predecessor_verified=True)
    save(folder/'STATUS.json',proof);return proof
def verify():
    folder=ROOT/EVIDENCE;proof=json.loads((folder/'STATUS.json').read_bytes());assert proof['status']==STATUS
    for n,v in proof['sources'].items():assert pin(ROOT/n)==v,n
    for n,v in proof['evidence'].items():assert pin(folder/n)==v,n
    with zipfile.ZipFile(folder/'catalog-before.zip') as z:
        assert set(z.namelist())==set(expected_before());before={n:z.read(n) for n in z.namelist()}
    assert {n:rawpin(raw) for n,raw in before.items()}==expected_before()==proof['before']
    after={n:(ROOT/n).read_bytes() for n in before};assert {n:rawpin(raw) for n,raw in after.items()}==proof['after'];result=check(before,after)
    return dict(status=STATUS,**result,metadata_only=True,current_development_source_cumulatively_requalified=False,exact_primary_source_checked=True,historical_verifiers_modified=False)
def main():
    p=argparse.ArgumentParser(description=__doc__);m=p.add_mutually_exclusive_group(required=True);m.add_argument('--apply',action='store_true');m.add_argument('--verify',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False)
    try:result=apply(out) if a.apply else verify();save(out/'COMPLETED.json',result);print(result['status']);return 0
    except Exception as error:save(out/'FAILURE.json',dict(cause=repr(error),required_action='Preserve exact predecessor metadata and failed outputs. Resolve the named cause before retrying NEW output. No estimator or source-hash substitution is admitted.'));print('FAIL2_P074_ERRATUM_UNVERIFIED',repr(error));return 2
if __name__=='__main__':raise SystemExit(main())
