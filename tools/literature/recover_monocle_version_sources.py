# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect official DESCRIPTION history for reported untagged versions; never infer a paper commit."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import re
import urllib.request
from setup_deseq2_reference import ROOT,pin,save


def fetch(url,maximum):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-primary-source-audit'}),timeout=60) as response:
        raw=response.read(maximum+1);assert len(raw)<=maximum
        return raw,dict(url=url,resolved_url=response.url,response_headers=dict(response.headers),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir()
    save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),software_installed=False,paper_commit_inference=False));(out/'source-used.py').write_bytes(Path(__file__).read_bytes())
    history={}
    for branch in ['master','develop']:
        for page in range(1,21):
            raw,record=fetch('https://api.github.com/repos/cole-trapnell-lab/monocle3/commits?path=DESCRIPTION&per_page=100&sha='+branch+'&page='+str(page),8388608);(out/(branch+'-DESCRIPTION-history-'+str(page)+'.json')).write_bytes(raw);save(out/(branch+'-response-'+str(page)+'.json'),record)
            rows=json.loads(raw);assert isinstance(rows,list)
            for row in rows:
                item=history.setdefault(row['sha'],dict(commit=row['sha'],date=row['commit']['committer']['date'],message=row['commit']['message'],branches=[]))
                if branch not in item['branches']:item['branches'].append(branch)
            links=record['response_headers'].get('Link',record['response_headers'].get('link',''))
            if 'rel="next"' not in links:break
        else:raise RuntimeError('History exceeds bounded pagination; preserve incomplete search')
    def description(item):
        sha,row=item;assert re.fullmatch('[0-9a-f]{40}',sha);raw,record=fetch('https://raw.githubusercontent.com/cole-trapnell-lab/monocle3/'+sha+'/DESCRIPTION',1048576);folder=out/sha;folder.mkdir();(folder/'DESCRIPTION').write_bytes(raw);save(folder/'RESPONSE.json',record);version=re.search(r'^Version:\s*(\S+)\s*$',raw.decode('utf8'),re.M);assert version;return dict(row,version=version[1],description=pin(folder/'DESCRIPTION'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:records=list(pool.map(description,sorted(history.items())))
    candidates=[r for r in records if r['version'] in ['1.3.3','1.3.7']];save(out/'COMPLETED.json',dict(status='official-DESCRIPTION-version-history-audited',records=records,candidates=candidates,requested_versions=['1.3.3','1.3.7'],source_pin=pin(Path(__file__)),software_installed=False,paper_commit_inference=False,whole_goal_complete=False))
    print(json.dumps(dict(DESCRIPTION_commits=len(records),candidates=candidates)),flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable primary source/version checks')
    main()
