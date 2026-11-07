# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain official UCSC format sources and independent tools for native bigWig validation only."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/bigwig-native').resolve()) and not out.exists();out.mkdir(parents=True);files={}
    def fetch(name,url,maximum):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-native-bigWig-source-audit'})
        with urllib.request.urlopen(request,timeout=60) as response:raw=response.read(maximum+1);final=response.geturl();headers=dict(response.headers)
        assert len(raw)<=maximum;target=out/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);files[name]=dict(url=url,final_url=final,headers=headers,**pin(target));(out/'PROGRESS.json').write_text(json.dumps(files,indent=2)+'\n');return raw
    commit=json.loads(fetch('kent-commit.json','https://api.github.com/repos/ucscGenomeBrowser/kent/commits/master',1048576))['sha'];assert len(commit)==40
    for name in ['src/utils/bedGraphToBigWig/bedGraphToBigWig.c','src/lib/bbiWrite.c','src/lib/bwgCreate.c','src/lib/bPlusTree.c','src/lib/cirTree.c','src/inc/bwgInternal.h','src/inc/bbiFile.h','src/lib/sqlNum.c','src/lib/linefile.c','LICENSE']:
        fetch('kent/'+name,'https://raw.githubusercontent.com/ucscGenomeBrowser/kent/'+commit+'/'+name,4194304)
    for name in ['bigWigToBedGraph','bigWigInfo']:fetch('bin/'+name,'https://hgdownload.soe.ucsc.edu/admin/exe/linux.x86_64/'+name,30000000)
    converter=ROOT/'build/literature-review/homer-runtime/ucsc-bigwig-source-intake-1/bedGraphToBigWig';assert pin(converter)==dict(bytes=12989000,sha256='fe9ed8ed858731d22d9a3cefa99d192f51bff1496693599b2f8054fa350adb48');(out/'bin/bedGraphToBigWig').write_bytes(converter.read_bytes());files['bin/bedGraphToBigWig']=dict(reused_exact_official_reference=True,source=converter.relative_to(ROOT).as_posix(),**pin(converter))
    (out/'COMPLETED.json').write_text(json.dumps(dict(status='downloaded-official-reference-tools-and-pinned-format-sources-not-project-integration',kent_commit=commit,files=files,analysis_run=False,implementation_credit=False),indent=2)+'\n');print(json.dumps(dict(status='downloaded',kent_commit=commit,files=len(files))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source-intake checks')
    main()
