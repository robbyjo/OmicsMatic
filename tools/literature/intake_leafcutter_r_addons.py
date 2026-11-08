# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact CRAN TailRank source/metadata required by original LeafCutter; no library mutation or analysis."""
import argparse,gzip,hashlib,json,tarfile,traceback,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_LEAFCUTTER_R_ADDON_SOURCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False)
    def fetch(url,path,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-CRAN-source-intake'}),timeout=90) as r:raw=r.read(maximum+1)
        assert len(raw)<=maximum;path.write_bytes(raw);return raw
    try:
        assert a.allow_download,'Required source download forbidden for this invocation';base='https://cloud.r-project.org/src/contrib/';raw=fetch(base+'PACKAGES.gz',out/'PACKAGES.gz',16777216);text=gzip.decompress(raw).decode('utf8');records=[r for r in text.split('\n\n') if r.startswith('Package: TailRank\n')];assert len(records)==1;record={};last=None
        for line in records[0].splitlines():
            if line.startswith(' '):record[last]+=' '+line.strip()
            else:last,value=line.split(':',1);record[last]=value.strip()
        save(out/'TailRank-CRAN-record.json',record);version=record['Version'];assert all(c.isdigit() or c=='.' for c in version);filename='TailRank_'+version+'.tar.gz';archive=out/filename;body=fetch(base+filename,archive,16777216);assert hashlib.md5(body).hexdigest()==record['MD5sum'];members={}
        with tarfile.open(archive,'r:gz') as tar:
            assert len(tar.getmembers())<10000 and sum(m.size for m in tar.getmembers())<67108864
            for m in tar.getmembers():
                name=PurePosixPath(m.name);assert name.parts[0]=='TailRank' and not name.is_absolute() and '..' not in name.parts and (m.isdir() or m.isfile()) and not m.issym() and not m.islnk()
                if m.isfile():target=out/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(tar.extractfile(m).read());members[name.as_posix()]=pin(target)
        state.update(status='PASS_EXACT_CRAN_TAILRANK_SOURCE_ONLY',version=version,archive=dict(url=base+filename,filename=filename,**pin(archive)),published_md5=record['MD5sum'],source_members=members,source_pin=pin(Path(__file__)))
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original source capture only; no LeafCutter model analysis admitted.')+'\n\nRequired action: preserve source/partial downloads and errors; restore exact permitted archive/metadata into NEW paths. Missing or forbidden dependencies keep the affected R analysis skipped; never substitute a version or patch originals to bypass a source check.\n',encoding='utf8',newline='\n');print(state['status']);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official source checks')
    raise SystemExit(main())
