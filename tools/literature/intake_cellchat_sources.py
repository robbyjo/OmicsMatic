# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture immutable original GPL CellChat trees for the four reported edition families."""
import argparse,hashlib,io,json,stat,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

SELECTED=[('1.0.0','sqjin/CellChat','1a719922cc3a7549011cb59e160a9c94f89ab2a8'),
 ('1.1.3','sqjin/CellChat','fea1c8ecdea0f393d32513cd00b866f8b4457839'),
 ('2.1.2','jinworks/CellChat','a0d3b2d231d46c8787177fffeac908270c253747'),
 ('2.2.0-2025-initial','jinworks/CellChat','af13f5125bccccefd736da31d96378d248d0ce33'),
 ('2.2.0-2025-release','jinworks/CellChat','346fb615aaefa3c1a2830ec2fe3a933c5f723c83'),
 ('2.2.0-2026-description','jinworks/CellChat','7661a6b2687d46f0815c03a1575da0f792cecbca')]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args();base=ROOT/'build/literature-review/cellchat-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);state=dict(status='SKIP3_CELLCHAT_REFERENCE_SOURCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_download,'Source download not allowed; skip dependent reference analysis'
        discovery=base/'edition-discovery-1';ready=json.loads((discovery/'COMPLETED.json').read_bytes());assert ready['status']=='PASS_OFFICIAL_CELLCHAT_IMMUTABLE_VERSION_DECLARATIONS_ONLY'
        assert pin(discovery/'editions.json')==ready['editions'];known=json.loads((discovery/'editions.json').read_bytes());records=[]
        for label,repository,commit in SELECTED:
            version='2.2.0' if label.startswith('2.2.0-') else label;declarations=[r for r in known if r['repository']==repository and r['commit']==commit and r['version']==version];assert declarations
            home=out/label;home.mkdir();url='https://codeload.github.com/'+repository+'/zip/'+commit
            request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-CellChat-source-audit/1.0'})
            with urllib.request.urlopen(request,timeout=90) as response:raw=response.read(268435457);receipt=dict(url=url,resolved_url=response.url,status=response.status)
            assert len(raw)<=268435456,'Archive exceeds256MiB; no truncated source admitted';archive=home/'original.zip';archive.write_bytes(raw);save(home/'download.json',dict(**receipt,**pin(archive)))
            source=home/'source';source.mkdir();members={}
            with zipfile.ZipFile(io.BytesIO(raw)) as z:
                infos=z.infolist();assert len(infos)<=20000 and len(infos)==len({i.filename for i in infos}) and sum(i.file_size for i in infos)<=1073741824
                prefix='CellChat-'+commit+'/'
                for info in infos:
                    name=info.filename;assert name.startswith(prefix) and not stat.S_ISLNK(info.external_attr>>16)
                    if info.is_dir():continue
                    relative=name[len(prefix):];path=PurePosixPath(relative)
                    assert relative and path.as_posix()==relative and not path.is_absolute() and '..' not in path.parts and not any(c in relative for c in '\\:\0\r\n') and info.file_size<=134217728
                    target=source/relative;assert target.resolve().is_relative_to(source);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(info));members[relative]=pin(target)
            description=(source/'DESCRIPTION').read_bytes();assert pin(source/'DESCRIPTION')==declarations[0]['description']
            fields=dict(line.split(':',1) for line in description.decode('utf8').splitlines() if ':' in line and not line.startswith((' ','\t')))
            assert fields['Package'].strip()=='CellChat' and fields['Version'].strip()==version and fields['License'].strip()=='GPL-3'
            save(home/'original-members.json',members);records.append(dict(label=label,repository=repository,commit=commit,declared_version=version,archive=pin(archive),members=pin(home/'original-members.json'),source_files=len(members),original_source_unmodified=True))
            print('Captured original CellChat '+label+' '+commit+' files='+str(len(members)),flush=True)
        state.update(status='PASS_SIX_IMMUTABLE_GPL_CELLCHAT_SOURCE_CANDIDATES_ONLY',candidates=records,original_package_installed=False,original_program_executed=False,
            historical_paper_commits_inferred=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original GPL source candidates captured without installation or scientific execution.')+'\n\nRequired action: skip dependent analysis if a required source/runtime cannot be supplied or installed where permitted. Preserve failed captures and use NEW paths. The several2.2.0 commits are distinct candidates, not proof of the paper source; no source patch, fallback edition or historical identity is admitted.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable immutable source/archive/edition checks')
    raise SystemExit(main())
