# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create explicit synthetic diploid HiFi reads and a small bounded public workflow request; run no analysis."""
import argparse,hashlib,json,random,re
from pathlib import Path

EDITIONS=['0.14.2-r315','0.15.1-r328','0.16.1-r375','0.19.5-r587','0.19.5-r592','0.22.0-r689']


def path_string(path,style):
    raw=str(path.resolve()).replace('\\','/')
    if style=='wsl':
        match=re.fullmatch(r'([A-Za-z]):/(.*)',raw)
        if match:return '/mnt/'+match[1].lower()+'/'+match[2]
        if raw.startswith('/'):return raw
        raise ValueError('WSL path style needs an absolute drive or Linux path')
    return str(path.resolve())


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--runtime',required=True);p.add_argument('--edition',choices=EDITIONS,required=True);p.add_argument('--path-style',choices=['native','wsl'],default='native');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.parent.is_dir();out.mkdir();rng=random.Random(471911);length=300000;first=''.join(rng.choice('ACGT') for _ in range(length));second=list(first)
    for i in range(100,length,97):
        if i%50000<20000:second[i]=rng.choice([c for c in 'ACGT' if c!=second[i]])
    genomes=[first,''.join(second)];reads=out/'synthetic-hifi.fa';count=0
    with reads.open('x',encoding='ascii',newline='\n') as stream:
        for hap,genome in enumerate(genomes):
            starts=[0,length-12000]+[rng.randrange(length-12000+1) for _ in range(round(length*25/12000))]
            for i,start in enumerate(starts):
                sequence=genome[start:start+12000]
                if i%2:sequence=sequence.translate(str.maketrans('ACGT','TGCA'))[::-1]
                stream.write('>example-hap'+str(hap)+'-read'+str(i)+'\n'+sequence+'\n');count+=1
    pin=dict(bytes=reads.stat().st_size,sha256=hashlib.sha256(reads.read_bytes()).hexdigest());request=dict(schema_version=1,out=path_string(out/'result',a.path_style),runtime=a.runtime,edition=a.edition,inputs=dict(hifi=dict(path=path_string(reads,a.path_style),**pin,kind='reads')),analyses=[dict(id='synthetic-diploid',mode='hifi',reads=['hifi'],threads=1,bloom_bits=0)],limits_profile='bounded-v1');(out/'request.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf8');(out/'PROVENANCE.json').write_text(json.dumps(dict(status='PREPARED_SYNTHETIC_INPUTS_ONLY',seed=471911,haplotypes=2,haplotype_bases=length,read_bases=12000,reads=count,substitution_error_rate=0,graph_design='Alternating20kb divergent and30kb shared blocks',input_pin=pin,analysis_admitted=False,paper_data=False),indent=2)+'\n',encoding='utf8');print('Prepared',count,'synthetic reads only; run HifiasmWorkflowCli with',out/'request.json')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable NEW-path and provenance checks')
    main()
