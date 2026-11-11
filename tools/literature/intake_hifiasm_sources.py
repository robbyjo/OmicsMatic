# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture official paper-relevant hifiasm tags and original yak source without changing the catalog."""
import argparse,concurrent.futures,hashlib,io,json,re,sys,tarfile,urllib.request,traceback
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

VERSIONS=['0.14.2','0.15.1','0.16.1','0.19.5','0.22.0']


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_SOURCE_INTAKE',versions={},analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def fetch(url,maximum):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-intake','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
        with urllib.request.urlopen(request,timeout=120) as response:raw=response.read(maximum+1);assert len(raw)<=maximum;return raw,dict(requested_url=url,final_url=response.url,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    try:
        tags={}
        for label,repo in [('hifiasm','chhylp123/hifiasm')]:
            raw,receipt=fetch('https://api.github.com/repos/'+repo+'/tags?per_page=100',4194304);(out/(label+'-tags.json')).write_bytes(raw);save(out/(label+'-tags.download.json'),receipt);tags[label]=json.loads(raw)
        def capture(item):
            label,repo,version=item;matches=[t for t in tags[label] if t['name'] in [version,'v'+version]];assert len(matches)==1,(label,version,[r['name'] for r in tags[label]]);tag=matches[0];commit=tag['commit']['sha'];assert re.fullmatch('[0-9a-f]{40}',commit);home=out/label/version;home.mkdir(parents=True);url='https://codeload.github.com/'+repo+'/tar.gz/'+commit;raw,receipt=fetch(url,33554432);archive=home/(label+'-'+commit+'.tar.gz');archive.write_bytes(raw);members={};source=home/'source';source.mkdir();version_tokens=[]
            with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
                seen=set();roots=set()
                for member in tar.getmembers():
                    path=PurePosixPath(member.name);assert not path.is_absolute() and '..' not in path.parts and len(path.parts)>=1;roots.add(path.parts[0])
                    if member.isdir():continue
                    assert member.isfile() and not member.issym() and not member.islnk() and len(path.parts)>1 and member.size<=33554432;relative=PurePosixPath(*path.parts[1:]).as_posix();assert relative not in seen and '\\' not in relative and ':' not in relative;seen.add(relative);content=tar.extractfile(member).read();assert len(content)==member.size;file=source/relative;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(content);members[relative]=pin(file)
                    if file.suffix in ['.h','.cpp','.c']:version_tokens.extend((relative,line) for line in content.decode('utf8',errors='replace').splitlines() if re.search(r'(?:HA_VERSION|HIFIASM_VERSION|YAKS_VERSION|#define\s+VERSION)',line))
                assert len(roots)==1
            license_files=[n for n in members if n.lower() in ['license','license.txt','copying']];assert license_files and any(b'MIT' in (source/n).read_bytes() or b'Permission is hereby granted' in (source/n).read_bytes() for n in license_files);record=dict(repository='https://github.com/'+repo,requested_version=version,tag=tag['name'],commit=commit,archive=dict(filename=archive.name,**pin(archive)),download=receipt,members=members,version_tokens=version_tokens,license_files=license_files,namespace_patched=False);save(home/'SOURCE.json',record);print(label,version,version_tokens,flush=True);return label+'/'+version,record
        items=[('hifiasm','chhylp123/hifiasm',v) for v in VERSIONS]
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for name,record in pool.map(capture,items):state['versions'][name]=record
        state.update(status='passed-official-five-edition-hifiasm-source-intake-only',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},unresolved_paper_version='P068 reports0.19.5-r592; the official0.19.5 tag revision is retained literally rather than inferred equivalent. P164 reports dirty Yak; a clean official tag is not claimed to reproduce it.')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official-source intake checks')
    raise SystemExit(main())
