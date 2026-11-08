# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original LOFTEE GRCh38, the cited BRaVa pipeline and exact historical source without changing runtimes."""
import argparse,hashlib,io,json,tarfile,urllib.error,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/loftee-runtime'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--allow-download',action='store_true');args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='SKIP3_SOURCE_NOT_PREPARED',analysis_executed=False,implementation_credit=False,whole_goal_complete=False,required_action='Where permitted capture the original source and exact VEP/LOFTEE dependencies in NEW private paths. Preserve source/download errors. Skip LOFTEE analysis until the real plugin runs and its complete output and warnings pass validation. Do not replace GRCh38 with the master branch or silently update the cited resource versions.')
    if not args.allow_download:save(out/'COMPLETED.json',state);print(state['status']);return 3
    receipts=[]
    def get(url,name):
        target=out/name;assert not target.exists();request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1.0','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
        try:
            with urllib.request.urlopen(request,timeout=90) as response:
                raw=response.read(67108865);assert len(raw)<=67108864,'Source response exceeds64MiB';target.write_bytes(raw);record=dict(url=url,final_url=response.url,http_status=response.status,headers=dict(response.headers.items()),path=name,**pin(target))
        except urllib.error.HTTPError as error:
            target.write_bytes(error.read(1048576));record=dict(url=url,http_status=error.code,path=name,**pin(target));receipts.append(record);save(out/'downloads.json',receipts);raise
        receipts.append(record);save(out/'downloads.json',receipts);return raw
    def api(path,name):return json.loads(get('https://api.github.com/repos/'+path,name))
    def source(repo,commit,label):
        raw=get('https://codeload.github.com/'+repo+'/tar.gz/'+commit,label+'.tar.gz');folder=out/label;folder.mkdir();members={}
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
            entries=archive.getmembers();assert 1<=len(entries)<=20000;top={m.name.split('/')[0] for m in entries};assert len(top)==1
            for entry in entries:
                parts=PurePosixPath(entry.name).parts;assert not entry.name.startswith('/') and '..' not in parts and '\\' not in entry.name
                if len(parts)==1:continue
                relative=PurePosixPath(*parts[1:]);target=folder/relative.as_posix();assert target.resolve().is_relative_to(folder.resolve())
                if entry.isdir():target.mkdir(parents=True,exist_ok=True);continue
                assert entry.isfile() and not entry.issym() and not entry.islnk() and 0<=entry.size<=33554432,'Unsafe source member'
                stream=archive.extractfile(entry);data=stream.read();assert len(data)==entry.size;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);members[relative.as_posix()]=dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
        return dict(repository=repo,commit=commit,archive=pin(out/(label+'.tar.gz')),members=members,source_path=folder.relative_to(ROOT).as_posix())
    try:
        tags=api('konradjk/loftee/tags?per_page=100','loftee-tags.json');branch=api('konradjk/loftee/commits/grch38','loftee-grch38-commit.json');fork=api('populationgenomics/loftee_38/commits/HEAD','loftee38-fork-commit.json');pipeline=api('BRaVa-genetics/vep105_loftee/commits/HEAD','brava-current-commit.json');historical=api('BRaVa-genetics/vep105_loftee/commits?until=2026-06-01T00:00:00Z&per_page=1','brava-pre-June2026-commit.json');assert len(historical)==1
        captured={}
        for repo,commit,label in [('konradjk/loftee',branch['sha'],'loftee-grch38'),('populationgenomics/loftee_38',fork['sha'],'loftee38-cited-fork'),('BRaVa-genetics/vep105_loftee',pipeline['sha'],'brava-current'),('BRaVa-genetics/vep105_loftee',historical[0]['sha'],'brava-pre-June2026')]:captured[label]=source(repo,commit,label)
        state.update(status='CAPTURED_ORIGINAL_LOFTEE_AND_CITED_PIPELINE_SOURCES_ONLY',sources=captured,tags=tags,historical_pipeline_selection='Most recent repository commit before2026-06-01; an audit comparison snapshot, not proof of the exact paper execution commit.',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
        print('Captured original LOFTEE and cited pipeline sources; no plugin or variant analysis executed')
    except Exception as error:state.update(status='FAIL2_LOFTEE_SOURCE_INTAKE_INCOMPLETE',error=repr(error));print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('CAPTURED_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/archive checks')
    raise SystemExit(main())
