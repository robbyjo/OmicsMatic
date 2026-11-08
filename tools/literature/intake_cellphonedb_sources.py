# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture complete published CellPhoneDB sources and official version/database metadata; no installation or implementation credit."""
import argparse,hashlib,io,json,tarfile,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_SOURCE_INTAKE_INCOMPLETE',versions={},implementation_credit=False,whole_goal_complete=False);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    def fetch(url,path):
        assert url.startswith(('https://pypi.org/','https://files.pythonhosted.org/','https://api.github.com/','https://raw.githubusercontent.com/'));request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review','Accept':'application/json' if 'api.github.com' in url or url.endswith('/json') else '*/*'})
        with urllib.request.urlopen(request,timeout=120) as response:raw=response.read(67108865);assert len(raw)<=67108864;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
        return raw
    try:
        release=json.loads(fetch('https://pypi.org/pypi/cellphonedb/json',out/'official-pypi-project.json'));versions=sorted(release['releases']);state['published_versions']=versions;state['paper_reported_5_1_resolved']=any(v in versions for v in ['5.1','5.1.0']);assert release['info']['name'].lower()=='cellphonedb'
        for version in ['5.0.1','5.0.0','2.1.4']:
            home=out/version;home.mkdir();metadata=json.loads(fetch('https://pypi.org/pypi/cellphonedb/'+version+'/json',home/'pypi-metadata.json'));assert metadata['info']['version']==version;distributions=metadata['urls'];candidates=[r for r in distributions if r['packagetype']=='bdist_wheel'] or [r for r in distributions if r['packagetype']=='sdist'];assert len(candidates)==1,'Choose one complete authoritative distribution explicitly';row=candidates[0];raw=fetch(row['url'],home/row['filename']);assert len(raw)==row['size'] and hashlib.sha256(raw).hexdigest()==row['digests']['sha256'];members={}
            def member(name,content):
                path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and str(path)==name and name not in members and len(content)<=16777216;destination=home/'source'/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(content);members[name]=pin(destination)
            if row['filename'].endswith('.whl'):
                with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                    assert len(archive.namelist())==len(set(archive.namelist())) and len(archive.namelist())<10000 and sum(i.file_size for i in archive.infolist())<134217728
                    for entry in archive.infolist():
                        if not entry.is_dir():assert (entry.external_attr>>16)&0o170000!=0o120000;member(entry.filename,archive.read(entry))
            else:
                with tarfile.open(fileobj=io.BytesIO(raw),mode='r:*') as archive:
                    entries=archive.getmembers();assert len(entries)<10000 and sum(r.size for r in entries)<134217728
                    for entry in entries:
                        if entry.isfile():member(entry.name,archive.extractfile(entry).read())
                        else:assert entry.isdir(),'Linked/special source member is not silently followed'
            licenses=[name for name in members if PurePosixPath(name).name.lower().startswith(('license','licence','copying'))];license_body=any(b'Permission is hereby granted, free of charge' in (home/'source'/name).read_bytes() for name in licenses);assert metadata['info'].get('license','').lower().replace(' license','')=='mit' or 'License :: OSI Approved :: MIT License' in metadata['info'].get('classifiers',[]),'Retain actual publisher license declaration'
            if version=='5.0.1':assert license_body,'The native development source must retain its actual original MIT notice'
            state['versions'][version]=dict(distribution=dict(filename=row['filename'],url=row['url'],**pin(home/row['filename'])),members=members,licenses=licenses,license_metadata=metadata['info'].get('license'),original_license_body_present=license_body,license_notice_required_before_public_source_bundling=not license_body,requires_python=metadata['info'].get('requires_python'),requires_dist=metadata['info'].get('requires_dist'),source_runtime_executed=False)
        for owner,repo,label in [('ventolab','CellphoneDB','current-software'),('Teichlab','cellphonedb','legacy-software'),('ventolab','cellphonedb-data','database')]:
            for endpoint,filename in [('/tags',label+'-official-tags.json'),('',label+'-official-repository.json')]:
                url='https://api.github.com/repos/'+owner+'/'+repo+endpoint
                try:fetch(url,out/filename)
                except Exception as failure:
                    record=dict(url=url,error=repr(failure),retrieval_succeeded=False,required_action='Published source distributions remain independently pinned. Do not infer Git tag/commit or database version/license from an unavailable endpoint; retry this same metadata request without restarting any analysis.');state.setdefault('unavailable_repository_metadata',[]).append(record);save(out/(filename+'.UNAVAILABLE.json'),record)
        state.update(status='PASS_EXACT_COMPLETE_PUBLISHED_CELLPHONEDB_SOURCE_DISTRIBUTIONS_ONLY',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},software_installed=False,analysis_executed=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    state['required_action']='Require exact source/version/database identity, actual MIT and database license terms, permitted private runtime installation and complete original/native scientific controls before implementation credit. Keep reported5.1 unresolved unless authoritative evidence resolves it; never map it silently to5.0.1. Preserve failed intakes and retry NEW paths. Database-only and Squidpy/Spyrrow uses are distinct from standalone CellPhoneDB execution. Missing or forbidden dependencies keep dependent analysis skipped.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Source capture only; no software was installed and no analysis ran.')+'\n\n'+state['required_action']+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact published-source checks')
    raise SystemExit(main())
