# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recover the reported2.23.2 source from an official release ancestry without inferring the paper binary."""
import argparse,gzip,hashlib,io,json,re,subprocess,tarfile,traceback
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--repo',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();repo=a.repo.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_RSUBREAD_REPORTED_SOURCE_RECOVERY',analysis_admitted=False,implementation_credit=False,paper_binary_identified=False,whole_goal_complete=False)
    def git(*args):return subprocess.check_output(['git','-c','core.autocrlf=false','-c','core.eol=lf','-C',str(repo),*args],stderr=subprocess.PIPE,timeout=180)
    try:
        origin=git('remote','get-url','origin').decode().strip();assert origin=='https://git.bioconductor.org/packages/Rsubread';head=git('rev-parse','HEAD').decode().strip();remote=git('ls-remote','origin','refs/heads/RELEASE_3_22');(out/'official-release-ref.tsv').write_bytes(remote);assert remote.decode().split()==[head,'refs/heads/RELEASE_3_22'];commits=git('rev-list','--reverse','HEAD').decode().splitlines();records=[]
        for commit in commits:
            raw=git('show',commit+':DESCRIPTION');version=re.search(rb'^Version:\s*(\S+)\s*$',raw,re.M).group(1).decode();metadata=git('show','-s','--format=%H%n%P%n%aI%n%cI%n%s',commit).decode().splitlines();records.append(dict(commit=commit,parents=metadata[1].split(),author_date=metadata[2],commit_date=metadata[3],subject=metadata[4],version=version,description=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())))
        save(out/'official-ancestry-versions.json',records);matches=[r for r in records if r['version']=='2.23.2'];assert matches,'Reported source version not recovered; do not substitute current release';selected=matches[-1];commit=selected['commit'];raw=git('archive','--format=tar','--prefix=Rsubread/',commit);assert len(raw)<=67108864;archive=out/('Rsubread-2.23.2-'+commit+'.tar.gz');archive.write_bytes(gzip.compress(raw,mtime=0));root=out/'source';root.mkdir();members={}
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:') as tar:
            for member in tar.getmembers():
                path=PurePosixPath(member.name);assert not path.is_absolute() and '..' not in path.parts
                if member.isdir():continue
                assert member.isfile() and path.parts[0]=='Rsubread' and len(path.parts)>1;name=PurePosixPath(*path.parts[1:]).as_posix();assert name not in members and '\\' not in name and ':' not in name;content=tar.extractfile(member).read();assert len(content)==member.size;file=root/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(content);members[name]=pin(file)
        description=(root/'DESCRIPTION').read_text(encoding='utf8');assert 'Version: 2.23.2\n' in description and 'License: GPL (>=3)' in description and members['DESCRIPTION']==selected['description'];assert 'R/featureCounts.R' in members and 'src/readSummary.c' in members;record=dict(repository=origin,official_release_ref='RELEASE_3_22',official_release_commit=head,version='2.23.2',selected_commit=commit,selection='Latest matching commit in the captured official release ancestry. A version label does not identify a paper source/binary or reproduce R4.4.1.',matching_commits=matches,archive=dict(path=archive.relative_to(ROOT).as_posix(),**pin(archive)),members=members,license_declaration='DESCRIPTION: GPL (>=3)',namespace_patched=False,paper_binary_identified=False);save(out/'SOURCE.json',record);state.update(status='passed-official-ancestry-Rsubread2232-source-recovered-only',source=record,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable official ancestry/source/version checks')
    raise SystemExit(main())
