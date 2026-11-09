# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resolve reported r592 from official commit history without treating it as the r587 release tag."""
import argparse,concurrent.futures,hashlib,io,json,re,tarfile,traceback,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_R592_RESOLUTION',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def fetch(url,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-intake'}),timeout=120) as response:raw=response.read(maximum+1);assert len(raw)<=maximum;return raw,dict(requested_url=url,final_url=response.url,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    try:
        raw,receipt=fetch('https://api.github.com/repos/chhylp123/hifiasm/compare/0.19.5...0.19.6',4194304);(out/'compare.json').write_bytes(raw);save(out/'compare.download.json',receipt);compare=json.loads(raw);assert compare['total_commits']==len(compare['commits']) and 0<len(compare['commits'])<100
        def header(c):
            sha=c['sha'];assert re.fullmatch('[0-9a-f]{40}',sha);raw,receipt=fetch('https://raw.githubusercontent.com/chhylp123/hifiasm/'+sha+'/CommandLines.h',1048576);home=out/'headers'/sha;home.mkdir(parents=True);(home/'CommandLines.h').write_bytes(raw);save(home/'download.json',receipt);match=re.search(rb'#define HA_VERSION "([^"]+)"',raw);assert match;return dict(commit=sha,version=match.group(1).decode('ascii'),message=c['commit']['message'],header=pin(home/'CommandLines.h'))
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:headers=list(pool.map(header,compare['commits']))
        save(out/'observed-versions.json',headers);matches=[r for r in headers if r['version']=='0.19.5-r592'];assert matches,'Exact r592 not found; preserve uncertainty rather than substitute r587';selected=matches[-1];sha=selected['commit'];raw,receipt=fetch('https://codeload.github.com/chhylp123/hifiasm/tar.gz/'+sha,33554432);archive=out/('hifiasm-'+sha+'.tar.gz');archive.write_bytes(raw);source=out/'source';source.mkdir();members={}
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
            for member in tar.getmembers():
                path=PurePosixPath(member.name);assert not path.is_absolute() and '..' not in path.parts
                if member.isdir():continue
                assert member.isfile() and len(path.parts)>1;name=PurePosixPath(*path.parts[1:]).as_posix();assert name not in members and '\\' not in name and ':' not in name;file=source/name;file.parent.mkdir(parents=True,exist_ok=True);content=tar.extractfile(member).read();assert len(content)==member.size;file.write_bytes(content);members[name]=pin(file)
        assert pin(source/'CommandLines.h')==selected['header'] and b'Permission is hereby granted' in (source/'LICENSE').read_bytes();record=dict(repository='https://github.com/chhylp123/hifiasm',requested_version='0.19.5-r592',commit=sha,matching_commits=matches,commit_selection='Last matching commit in official compare order; exact header version alone does not identify the paper binary',archive=dict(filename=archive.name,**pin(archive)),download=receipt,members=members,license_files=['LICENSE'],namespace_patched=False);save(out/'SOURCE.json',record);state.update(status='passed-official-r592-commit-source-resolved-not-paper-binary-authentication',source=record,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},paper_binary_identified=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact revision checks')
    raise SystemExit(main())
