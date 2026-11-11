# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture the exact official CellPhoneDB database v5.0.0 Git tree and published ZIP for private reference inspection; no inferred MIT grant or tool credit."""
import argparse,hashlib,io,json,time,traceback,urllib.error,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-download',action='store_true');a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_CELLPHONEDB_DATABASE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,requests=[])
    def fetch(url,path,maximum=67108864):
        assert url.startswith(('https://api.github.com/repos/ventolab/cellphonedb-data','https://raw.githubusercontent.com/ventolab/cellphonedb-data/'))
        for attempt in range(1,3):
            record=dict(url=url,attempt=attempt,started_at=time.time(),destination=path.relative_to(out).as_posix())
            try:
                request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'})
                with urllib.request.urlopen(request,timeout=45) as response:raw=response.read(maximum+1);assert len(raw)<=maximum;record.update(status=response.status,final_url=response.url)
                assert not path.exists();path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);record.update(pin=pin(path));state['requests'].append(record);save(out/'request-receipts.json',state['requests']);return raw
            except Exception as error:
                record['error']=repr(error);state['requests'].append(record);save(out/'request-receipts.json',state['requests'])
                if attempt==2:raise
    try:
        assert a.allow_download,'Explicit download permission required';base='https://api.github.com/repos/ventolab/cellphonedb-data';repository=json.loads(fetch(base,out/'official-repository.json'));tag=json.loads(fetch(base+'/git/ref/tags/v5.0.0',out/'official-tag.json'));revision=tag['object']['sha'];kind=tag['object']['type'];assert len(revision)==40 and kind in ['commit','tag']
        if kind=='tag':annotated=json.loads(fetch(base+'/git/tags/'+revision,out/'official-annotated-tag.json'));assert annotated['object']['type']=='commit';revision=annotated['object']['sha']
        commit=json.loads(fetch(base+'/git/commits/'+revision,out/'official-commit.json'));assert commit['sha']==revision;pending=[('',commit['tree']['sha'])];members={}
        while pending:
            prefix,oid=pending.pop();tree=json.loads(fetch(base+'/git/trees/'+oid,out/'trees'/(oid+'.json')));assert tree['sha']==oid and tree['truncated'] is False
            for entry in tree['tree']:
                name=prefix+entry['path'];path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and str(path)==name
                if entry['type']=='tree':pending.append((name+'/',entry['sha']));continue
                assert entry['type']=='blob' and entry['mode'] in ['100644','100755'] and entry['size']<67108864 and name not in members;assert len(members)<250
                raw=fetch('https://raw.githubusercontent.com/ventolab/cellphonedb-data/'+revision+'/'+name,out/'source'/name);blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest();assert len(raw)==entry['size'] and blob==entry['sha'];members[name]=dict(**pin(out/'source'/name),git_blob_sha1=blob)
        assert 'README.md' in members and 'cellphonedb.zip' in members;database=out/'source/cellphonedb.zip';tables={}
        with zipfile.ZipFile(database) as archive:
            names=archive.namelist();assert len(names)==len(set(names))<100 and sum(i.file_size for i in archive.infolist())<67108864
            for info in archive.infolist():
                assert not info.is_dir() and info.filename.endswith('.csv') and PurePosixPath(info.filename).name==info.filename and (info.external_attr>>16)&0o170000!=0o120000;raw=archive.read(info);target=out/'database-tables'/info.filename;target.parent.mkdir(exist_ok=True);target.write_bytes(raw);tables[info.filename]=pin(target)
        readme=(out/'source/README.md').read_text(encoding='utf8');licenses=[n for n in members if PurePosixPath(n).name.lower().startswith(('license','licence','copying','copyright'))];assert 'The database can be used' in readme and 'CellphoneDB tool' in readme
        state.update(status='PASS_EXACT_OFFICIAL_CELLPHONEDB_DATABASE_CAPTURE_ONLY',revision=revision,tag='v5.0.0',repository_license_metadata=repository.get('license'),license_files=licenses,README_use_invitation_recorded=True,separate_database_redistribution_grant_confirmed=False,MIT_software_grant_inferred_for_database=False,public_database_bundling_admitted=False,source_members=members,database=dict(path=database.relative_to(ROOT).as_posix(),**pin(database)),tables=tables,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    state['required_action']='Exact database capture/private inspection only, not an installation, analysis, full-tool implementation, biological result or general redistribution-license determination. Keep the software MIT grant separate from database terms. Preserve the official README use invitation and actual license metadata; do not relicense or package the curated database as project-owned data. Retain all failed GET receipts and retry NEW intake paths if required metadata/source identities cannot be resolved. Missing/forbidden required input keeps dependent analysis skipped.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Database captured for private inspection; no analysis or public redistribution admitted.')+'\n\n'+state['required_action']+'\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official tree/database identity checks')
    raise SystemExit(main())
