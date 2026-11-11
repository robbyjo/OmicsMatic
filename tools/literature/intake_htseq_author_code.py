# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect pinned author repositories for custom HTSeq counting, without executing their code."""
import argparse
import hashlib
import json
import re
import urllib.parse
import urllib.request
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/htseq-runtime'
AUTHORS={'P099':'JunyueCaoLab/EasySci','P121':'owensnick/ZNF808Genomics.jl'}


def pin(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    papers={p['paper_id']:p for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())}
    receipts=[]
    total=0
    def save(path,value):
        path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
    def fetch(url,target,limit):
        nonlocal total
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-primary-source-audit/1.0','Accept':'application/vnd.github+json' if 'api.github.com' in url else 'text/plain'})
        with urllib.request.urlopen(request,timeout=45) as response:
            raw=response.read(limit+1)
            assert len(raw)<=limit
            total+=len(raw)
            assert total<=67108864
            receipt=dict(url=url,final_url=response.geturl(),**pin(raw))
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(raw)
        receipts.append(receipt)
        save(out/'DOWNLOAD-PROGRESS.json',receipts)
        return raw
    results=[]
    for pid,repository in AUTHORS.items():
        folder=out/pid
        folder.mkdir()
        metadata=json.loads(fetch('https://api.github.com/repos/'+repository,folder/'repository.json',1048576))
        assert metadata['full_name'].lower()==repository.lower()
        publication_date=papers[pid]['first_publication_date']
        selected=[]
        for label,suffix in [('current',''),('last-before-publication','&until='+publication_date+'T23:59:59Z')]:
            commits=json.loads(fetch('https://api.github.com/repos/'+repository+'/commits?per_page=1'+suffix,folder/(label+'-commit.json'),1048576))
            if not commits:
                selected.append(dict(reference=label,available=False,paper_exact_checkout_known=False))
                continue
            commit=commits[0]
            sha=commit['sha']
            assert re.fullmatch('[0-9a-f]{40}',sha)
            tree=json.loads(fetch('https://api.github.com/repos/'+repository+'/git/trees/'+sha+'?recursive=1',folder/(label+'-tree.json'),8388608))
            assert tree.get('truncated') is False and len(tree['tree'])<=20000
            candidates=[row for row in tree['tree'] if row['type']=='blob' and (row['path'].endswith(('.py','.sh','.jl'))
                or '/' not in row['path'] and row['path'].lower().startswith(('readme','license','copying')))]
            assert len(candidates)<=250
            files=[]
            mentions=[]
            for row in candidates:
                name=PurePosixPath(row['path'])
                assert not name.is_absolute() and '..' not in name.parts and '\\' not in row['path']
                if row['size']>2097152:
                    files.append(dict(path=row['path'],skipped='Over2MiB bounded textual-source intake',bytes=row['size']))
                    continue
                target=folder/label/'source'/Path(*name.parts)
                raw=fetch('https://raw.githubusercontent.com/'+repository+'/'+sha+'/'+urllib.parse.quote(row['path'],safe='/'),target,2097152)
                assert len(raw)==row['size']
                assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==row['sha']
                files.append(dict(path=row['path'],git_blob_sha1=row['sha'],**pin(raw)))
                text=raw.decode('utf8')
                matches=[i for i,line in enumerate(text.splitlines(),1) if re.search(r'HT[-_ ]?Seq|GenomicArrayOfSets|SAM_Reader|BAM_Reader',line,re.I)]
                if matches:
                    mentions.append(dict(path=row['path'],line_numbers=matches))
            selected.append(dict(reference=label,available=True,commit=sha,commit_date=commit['commit']['committer']['date'],
                paper_exact_checkout_known=False,files=files,HTSeq_mentions=mentions))
        results.append(dict(paper_id=pid,doi=papers[pid]['doi'],repository='https://github.com/'+repository,
            publication_date=publication_date,references=selected,
            scope='Public author sources inspected at explicit reference commits. Last-before-publication is a reference, not proof of the actual paper checkout. No author code executed or counted as an implemented pipeline.'))
    save(out/'COMPLETED.json',dict(schema_version=1,status='inspected-pinned-author-HTSeq-code-references',
        sources=results,total_downloaded_bytes=total,original_author_pipelines_executed=False,implementation_credit=False))
    print(json.dumps(dict(status='inspected-author-code-without-execution',repositories=len(results),
        references=[dict(paper_id=r['paper_id'],references=[dict(reference=p['reference'],available=p['available'],mentions=p.get('HTSeq_mentions',[])) for p in r['references']]) for r in results])))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable author-source hash checks')
    main()
