# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact official py-monocle, Cicero-M3 and recovered Monocle3 candidate sources."""
import argparse
import hashlib
import io
import json
from pathlib import Path,PurePosixPath
import re
import tarfile
from recover_monocle_version_sources import fetch
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists();out.mkdir()
    save(out/'STARTED.json',dict(source_pin=pin(Path(__file__)),software_installed=False));(out/'source-used.py').write_bytes(Path(__file__).read_bytes())
    candidates=json.loads((ROOT/'build/literature-review/monocle-runtime/version-history-intake-2/COMPLETED.json').read_bytes())['candidates'];profiles=[]
    for id,repo,ref in [('py-monocle','bioturing-org/py-monocle','HEAD'),('cicero-m3','cole-trapnell-lab/cicero-release','monocle3')]:
        raw,response=fetch('https://api.github.com/repos/'+repo+'/commits/'+ref,4194304);head=json.loads(raw);sha=head['sha'];folder=out/id;folder.mkdir();(folder/'COMMIT.json').write_bytes(raw);save(folder/'COMMIT-RESPONSE.json',response);profiles.append(dict(id=id,repository=repo,commit=sha,selected_ref=ref,paper_commit_known=False))
    profiles.extend(dict(id='monocle3-'+c['version'],repository='cole-trapnell-lab/monocle3',commit=c['commit'],version=c['version'],selected_ref='official-DESCRIPTION-change-commit',paper_commit_known=False) for c in candidates)
    for profile in profiles:
        sha=profile['commit'];assert re.fullmatch('[0-9a-f]{40}',sha);folder=out/profile['id'];folder.mkdir(exist_ok=True);raw,response=fetch('https://codeload.github.com/'+profile['repository']+'/tar.gz/'+sha,134217728);archive=folder/'source.tar.gz';archive.write_bytes(raw);members={};total=0
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as tar:
            for item in tar:
                parts=PurePosixPath(item.name).parts;assert len(parts)>=1 and parts[0]==profile['repository'].split('/')[1]+'-'+sha and '..' not in parts and not PurePosixPath(item.name).is_absolute() and '\\' not in item.name
                if item.isdir():continue
                assert item.isfile() and not item.issym() and not item.islnk() and item.size<=33554432;total+=item.size;assert total<=268435456 and len(members)<10000
                n=PurePosixPath(*parts[1:]).as_posix();target=folder/'source'/n;assert target.resolve().is_relative_to((folder/'source').resolve()) and n not in members;data=tar.extractfile(item).read();assert len(data)==item.size;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);members[n]=dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
        if 'version' in profile:assert '\nVersion: '+profile['version']+'\n' in (folder/'source/DESCRIPTION').read_text(encoding='utf8')
        profile.update(archive=pin(archive),source_members=members,response=response);save(folder/'SOURCE.json',profile);print(profile['id'],sha,len(members),'original files',flush=True)
    save(out/'COMPLETED.json',dict(status='four-original-software-source-candidates-captured',profiles=[{k:v for k,v in r.items() if k not in ['source_members','response']} for r in profiles],source_pin=pin(Path(__file__)),history_proof=pin(ROOT/'build/literature-review/monocle-runtime/version-history-intake-2/COMPLETED.json'),software_installed=False,paper_commits_inferred=False,whole_goal_complete=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/commit checks')
    main()
