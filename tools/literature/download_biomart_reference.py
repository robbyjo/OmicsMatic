# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original biomaRt sources/binaries and verify all seven already-reviewed primary roles; grant no tool credit."""
import argparse,concurrent.futures,gzip,hashlib,io,json,tarfile,urllib.error,urllib.request,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs

ARTIFACTS=[('2.68.0','source','https://bioconductor.org/packages/3.23/bioc/src/contrib/biomaRt_2.68.0.tar.gz'),('2.68.0','windows','https://bioconductor.org/packages/3.23/bioc/bin/windows/contrib/4.6/biomaRt_2.68.0.zip'),('2.60.1','source','https://bioconductor.org/packages/3.19/bioc/src/contrib/biomaRt_2.60.1.tar.gz'),('2.60.1','windows','https://bioconductor.org/packages/3.19/bioc/bin/windows/contrib/4.4/biomaRt_2.60.1.zip')]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--reuse-intake',type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/biomart-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();(out/'downloader-at-attempt.py').write_bytes(source.read_bytes());data=ROOT/'docs/literature/omics-2023-2026';current={f.relative_to(ROOT).as_posix():pin(f) for f in [data/'papers.json',data/'tool-inventory.json',data/'summary.json',ROOT/'tools/literature/reviewed-uses.json']};save(out/'STARTED.json',dict(status='RUNNING_ORIGINAL_BIOMART_SOURCE_INTAKE',source_pins={source.relative_to(ROOT).as_posix():pin(source)}));results={}
    def fetch(item):
        version,kind,url=item;home=out/(version+'-'+kind);home.mkdir();state=dict(url=url,version=version,kind=kind,status='SKIP3_REFERENCE_DOWNLOAD_UNAVAILABLE')
        try:
            archive=home/('biomaRt_'+version+('.tar.gz' if kind=='source' else '.zip'))
            if a.reuse_intake:
                prior=a.reuse_intake.resolve()/(version+'-'+kind);receipt=json.loads((prior/'COMPLETED.json').read_bytes());original=prior/archive.name;assert receipt['url']==url and pin(original)==receipt['archive'];raw=original.read_bytes();archive.write_bytes(raw);state.update(final_url=receipt['final_url'],http_status=receipt['http_status'],headers=receipt['headers'],archive=pin(archive),prior_download_receipt=pin(prior/'COMPLETED.json'),archive_download_not_repeated=True)
            else:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-reference-audit/1.0'}),timeout=90) as response:
                    raw=response.read(33554433);assert len(raw)<=33554432;archive.write_bytes(raw);state.update(final_url=response.url,http_status=response.status,headers=dict(response.headers.items()),archive=pin(archive))
            members={}
            if kind=='source':
                with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
                    for entry in archive.getmembers():
                        if entry.isdir():continue
                        assert entry.isfile() and entry.name.startswith('biomaRt/') and entry.size<=16777216;name=entry.name.removeprefix('biomaRt/');target=home/'source'/name;assert target.resolve().is_relative_to(home/'source');value=archive.extractfile(entry).read();assert len(value)==entry.size;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(value);members[name]=pin(target)
                description=(home/'source/DESCRIPTION').read_text(encoding='utf8');assert 'Version: '+version in description.splitlines()
            else:
                with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                    assert len(archive.namelist())==len(set(archive.namelist())) and archive.testzip() is None;description=archive.read('biomaRt/DESCRIPTION').decode('utf8');assert 'Version: '+version in description.splitlines()
            state.update(status='PASS_ORIGINAL_BIOMART_REFERENCE_CAPTURE_ONLY',members=members,description=description)
        except Exception as error:state['error']=repr(error)
        save(home/'COMPLETED.json',state);print(version,kind,state['status'],flush=True);return version+'-'+kind,state
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for name,result in pool.map(fetch,ARTIFACTS):results[name]=result
    papers=json.loads((data/'papers.json').read_bytes());reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'];roles=[]
    for paper in papers:
        rows=[t for t in paper['tools'] if t['tool_id']=='biomart']
        if not rows:continue
        assert len(rows)==1;tool=rows[0];raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();anchor=next(t for t in paragraphs(root) if t['paragraph_index']==tool['evidence_paragraph_index']);assert 'biomaRt' in anchor['text'] and hashlib.sha256(anchor['text'].encode('utf8')).hexdigest()==tool['evidence_paragraph_sha256'];review=next(r for r in reviews if r['pmcid']==paper['pmcid'] and r['tool_id']=='biomart');assert review['use_summary']==tool['use_summary'];roles.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],primary_paragraph=anchor['paragraph_index'],primary_digest=tool['evidence_paragraph_sha256'],review=review))
    assert [r['paper_id'] for r in roles]==['P049','P117','P134','P162','P223','P238','P250'] and len(reviews)==952 and all(pin(ROOT/n)==v for n,v in current.items());save(out/'primary-role-audit.json',dict(status='verified-seven-existing-biomaRt-primary-roles',roles=roles,current_catalog_unchanged=current,exact_historical_package_version_reported_in=['P049'],query_attributes_filters_and_release_not_invented=True));save(out/'COMPLETED.json',dict(status='completed-original-biomaRt-reference-intake-not-implementation',artifacts=results,primary_roles=7,current_catalog_unchanged=current,source_pins={source.relative_to(ROOT).as_posix():pin(source)},implementation_credit=False,whole_goal_complete=False));print('Verified seven existing primary roles; no catalog or implementation-credit changes')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/primary-role checks')
    main()
