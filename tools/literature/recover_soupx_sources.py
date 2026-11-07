# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recover official version-label candidates when the paper's historical release is absent from CRAN."""
import argparse,concurrent.futures,hashlib,io,json,re,tarfile,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=args.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    source_pin=pin(Path(__file__).resolve());(out/'recovery-used.py').write_bytes(Path(__file__).read_bytes())
    current=base/'source-intake-1/1.6.2';receipt=json.loads((current/'SOURCE.json').read_bytes());assert all(pin(current/'source'/n)==v for n,v in receipt['source_members'].items())
    def fetch(url,target,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1','Accept':'application/vnd.github+json'}),timeout=45) as response:raw=response.read(maximum+1)
        assert len(raw)<=maximum;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);return raw
    tags=json.loads(fetch('https://api.github.com/repos/constantAmateur/SoupX/tags?per_page=100',out/'tags-page-1.json',8<<20));commits=[]
    for page in range(1,20):
        rows=json.loads(fetch('https://api.github.com/repos/constantAmateur/SoupX/commits?path=DESCRIPTION&sha=master&per_page=100&page='+str(page),out/('description-history-'+str(page)+'.json'),16<<20));assert isinstance(rows,list)
        if not rows:break
        commits.extend(rows)
        if len(rows)<100:break
    else:raise RuntimeError('History pagination incomplete')
    unique={r['sha']:r for r in commits};versions={}
    def description(item):
        commit,row=item;raw=fetch('https://raw.githubusercontent.com/constantAmateur/SoupX/'+commit+'/DESCRIPTION',out/'descriptions'/(commit+'.txt'),1<<20);match=re.search(rb'^Version: ([^\r\n]+)',raw,re.M);assert match;return commit,match[1].decode('ascii'),row['commit']['committer']['date']
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:history=list(pool.map(description,unique.items()))
    candidates=[dict(commit=commit,version=version,date=date) for commit,version,date in history if version=='1.3.0'];assert candidates,'No explicit1.3.0 source candidate; preserve and review other official branches before claiming absence'
    # A DESCRIPTION-changing commit identifies a candidate, not all intervening
    # commits with the same version label and not the paper's actual code.
    selected=candidates[0];commit=selected['commit'];folder=out/'1.3.0';folder.mkdir();url='https://codeload.github.com/constantAmateur/SoupX/tar.gz/'+commit;raw=fetch(url,folder/'source.tar.gz',32<<20);members={}
    with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
        for member in archive.getmembers():
            p=PurePosixPath(member.name);assert not p.is_absolute() and '..' not in p.parts and p.parts[0]=='SoupX-'+commit and (member.isdir() or member.isfile())
            if member.isfile():
                name=PurePosixPath(*p.parts[1:]).as_posix();assert name not in members and member.size<=32<<20;target=folder/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.extractfile(member).read());members[name]=pin(target)
    assert 'Version: 1.3.0\n' in (folder/'source/DESCRIPTION').read_text(encoding='utf8')
    save(folder/'SOURCE.json',dict(version='1.3.0',source_url=url,commit=commit,source_selection='latest-master-DESCRIPTION-change-to-version-label',archive=pin(folder/'source.tar.gz'),source_members=members,paper_commit_proven=False,all_same_label_commits_enumerated=False,installation_performed=False,analysis_admitted=False))
    assert pin(Path(__file__).resolve())==source_pin
    save(out/'COMPLETED.json',dict(status='recovered-official-SoupX-version-label-candidate',source_pin=source_pin,current_CRAN_source=dict(path=current.relative_to(ROOT).as_posix(),receipt=pin(current/'SOURCE.json')),description_history_unique_commits=len(unique),description_history=history,tags=tags,candidates=candidates,selected=selected,historical_source=pin(folder/'SOURCE.json'),paper_commit_proven=False,analysis_admitted=False));print('Recovered1.3.0 candidate',commit,'from',len(unique),'DESCRIPTION commits;',len(members),'source files',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/version checks')
    main()
