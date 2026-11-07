# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original current and reported historical SoupX releases; download only, no installation."""
import argparse,gzip,hashlib,io,json,tarfile,urllib.request
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists();out.mkdir(parents=True)
    source_pin=pin(Path(__file__).resolve());receipts=[]
    def fetch(url,target,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1'}),timeout=45) as response:raw=response.read(maximum+1)
        assert len(raw)<=maximum;target.write_bytes(raw);return raw
    try:
        packages=fetch('https://cran.r-project.org/src/contrib/PACKAGES.gz',out/'PACKAGES.gz',16<<20)
        record=next(row for row in gzip.decompress(packages).decode('utf8').split('\n\n') if row.startswith('Package: SoupX\n'));fields=dict(line.split(': ',1) for line in record.splitlines() if ': ' in line and not line.startswith(' '));assert fields['Version']=='1.6.2','Current CRAN version changed; preserve and review exact edition explicitly'
        for version in ['1.6.2','1.3.0']:
            folder=out/version;folder.mkdir();url='https://cran.r-project.org/src/contrib/'+('Archive/SoupX/' if version=='1.3.0' else '')+'SoupX_'+version+'.tar.gz';archive=folder/('SoupX_'+version+'.tar.gz');raw=fetch(url,archive,32<<20)
            if version=='1.6.2':assert hashlib.md5(raw).hexdigest()==fields['MD5sum']
            files={}
            with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as source:
                for member in source.getmembers():
                    name=PurePosixPath(member.name);assert not name.is_absolute() and '..' not in name.parts and name.parts[0]=='SoupX' and (member.isdir() or member.isfile())
                    if member.isfile():
                        relative=PurePosixPath(*name.parts[1:]).as_posix();assert relative not in files and member.size<=32<<20;value=source.extractfile(member).read();target=folder/'source'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(value);files[relative]=pin(target)
            description=(folder/'source/DESCRIPTION').read_text(encoding='utf8');assert 'Version: '+version+'\n' in description
            md5=folder/'source/MD5';members_checked=0
            if md5.exists():
                for line in md5.read_text(encoding='utf8').splitlines():
                    digest,relative=line.split(' ',1);relative=relative.strip().strip('*').strip('"');assert relative in files and hashlib.md5((folder/'source'/relative).read_bytes()).hexdigest()==digest;members_checked+=1
            receipt=dict(version=version,source_url=url,archive=pin(archive),source_members=files,repository_MD5=fields['MD5sum'] if version=='1.6.2' else None,internal_MD5_members_checked=members_checked,license=next(line.split(': ',1)[1] for line in description.splitlines() if line.startswith('License: ')),paper_commit_proven=False,installation_performed=False,analysis_admitted=False);save(folder/'SOURCE.json',receipt);receipts.append(dict(version=version,receipt=pin(folder/'SOURCE.json')));print('Captured original SoupX'+version+':'+str(len(files))+' source members',flush=True)
        assert pin(Path(__file__).resolve())==source_pin;save(out/'COMPLETED.json',dict(status='captured-two-official-SoupX-releases-not-installation',releases=receipts,source_pin=source_pin,index=pin(out/'PACKAGES.gz'),installation_performed=False,analysis_admitted=False))
    except Exception as error:
        save(out/'SKIP3.json',dict(status='SKIP3_ORIGINAL_SOURCE_UNAVAILABLE',detail=str(error),required_action='Preserve this attempt. Obtain the exact official source through permitted access, verify archive/member identities and retry NEW paths. Skip dependent source-bound analysis until its selected release is available; never substitute releases silently.',analysis_admitted=False));raise


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source checks')
    main()
