# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture explicit R4.2-compatible official Biobase/oompaData sources for TailRank; no version substitution or model execution."""
import argparse,gzip,hashlib,json,tarfile,traceback,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

def dcf(text):
    result={};last=None
    for line in text.splitlines():
        if not line:continue
        if line.startswith((' ','\t')):result[last]+=' '+line.strip()
        else:last,value=line.split(':',1);result[last]=value.strip()
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_LEAFCUTTER_COMPATIBILITY_DEPENDENCY_SOURCE_UNAVAILABLE',packages={},implementation_credit=False,whole_goal_complete=False)
    def fetch(url,path,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-official-R-compatibility-source'}),timeout=90) as r:raw=r.read(maximum+1);final=r.url
        assert len(raw)<=maximum;path.write_bytes(raw);return raw,final
    try:
        assert a.allow_download,'Required source download forbidden';base='https://bioconductor.org/packages/3.16/bioc/src/contrib/';raw,_=fetch(base+'PACKAGES.gz',out/'Bioconductor316-PACKAGES.gz',16777216);entries=[r for r in gzip.decompress(raw).decode('utf8').split('\n\n') if r.startswith('Package: Biobase\n')];assert len(entries)==1;record=dcf(entries[0]);assert record['Version']=='2.58.0';save(out/'Biobase-record.json',record);targets=[('Biobase','2.58.0',base+'Biobase_2.58.0.tar.gz',record['MD5sum']),('oompaData','3.1.3','https://cloud.r-project.org/src/contrib/Archive/oompaData/oompaData_3.1.3.tar.gz',None)]
        for name,version,url,md5 in targets:
            home=out/name;home.mkdir();archive=home/(name+'_'+version+'.tar.gz');body,final=fetch(url,archive,33554432)
            if md5:assert hashlib.md5(body).hexdigest()==md5
            members={}
            with tarfile.open(archive,'r:gz') as tar:
                assert len(tar.getmembers())<20000 and sum(m.size for m in tar.getmembers())<134217728
                for m in tar.getmembers():
                    path=PurePosixPath(m.name);assert path.parts[0]==name and not path.is_absolute() and '..' not in path.parts and (m.isdir() or m.isfile()) and not m.issym() and not m.islnk()
                    if m.isfile():destination=home/'source'/path;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(tar.extractfile(m).read());members[path.as_posix()]=pin(destination)
            description=dcf((home/'source'/name/'DESCRIPTION').read_text(encoding='utf8'));assert description['Package']==name and description['Version']==version
            if name=='oompaData':assert 'R (>= 3.0)' in description['Depends']
            state['packages'][name]=dict(version=version,archive=dict(url=url,final_url=final,filename=archive.name,**pin(archive)),published_md5=md5,description=description,source_members=members)
        state.update(status='PASS_EXACT_R42_COMPATIBLE_BIOBASE_OOMPADATA_SOURCES_ONLY',source_pin=pin(Path(__file__)),reason='Current oompaData3.1.5 requires R>=4.4; the explicit official archived3.1.3 source supports R>=3.0. Original TailRank does not pin an oompaData version. Biobase2.58.0 matches the explicit Bioconductor3.16/R4.2 dependency profile. No original package source or requirement is edited.')
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Explicit compatible official sources captured; no model workflow admitted.')+'\n\nRequired action: preserve original source, archives and errors; use the named permitted compatible versions in a NEW private library. Missing/forbidden dependencies keep the affected R analysis skipped. Do not alter DESCRIPTION requirements, replace a source hash or imply historical paper-runtime identity.\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source checks')
    raise SystemExit(main())
