#!/usr/bin/env python3
"""Write two tiny synthetic SAM recipes against the separately pinned whole RNA references."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--runtime-home',type=Path,required=True)
    args=parser.parse_args()
    out=args.out.absolute()
    if out.exists():raise SystemExit('Output exists; use a NEW directory')
    repository=Path(__file__).resolve().parents[2]
    raw=(repository/'tools/literature/homer-rna-lock.json').read_bytes()
    lock=json.loads(raw)
    out.mkdir(parents=True)
    inputs={}
    for genome in ['hg38','mm10']:
        directory=out/genome;directory.mkdir()
        rows=['replicate_id\tsam_path\n']
        for s,replicate in enumerate(lock['health']['genomes'][genome]['replicates']):
            text=replicate['sam'];body=text.encode('utf8')
            if hashlib.sha256(body).hexdigest()!=replicate['sam_sha256']:raise ValueError('Synthetic fixture pin differs')
            path=directory/('replicate-'+str(s+1)+'.sam');path.write_bytes(body)
            rows.append('technical-'+str(s+1)+'\t'+path.name+'\n');inputs[str(path.relative_to(out))]=hashlib.sha256(body).hexdigest()
        manifest=directory/'replicates.tsv';manifest.write_bytes(''.join(rows).encode())
        options=dict(home=str(args.runtime_home.absolute()),profile=lock['profile'],out=str(out/(genome+'-result')),
            **{'sam-list':str(manifest),'genome':genome,'reference-provenance':'Synthetic technical fixture; selected original RefSeq v7.0 resource, not paper or biological data',
               'mapping':'unique','minimum-mapq':'30','mates':'both','flip-strands':'false','strand-specific-paired':'false','relative-strand':'both',
               'condensation':'copy-families','normalization':'both','reference-irregularities':'retain-original','short-exon-parts':'retain-original',
               'duplicate-alignments':'error','supplementary-alignments':'error','unpaired-alignments':'retain-upstream','improper-pairs':'retain-upstream','identical-replicates':'error',
               'maximum-records':'1000','maximum-chromosome-tags':'10000','maximum-ledger-keys':'1000','timeout':'3600'})
        (out/(genome+'-options.json')).write_text(json.dumps(options,indent=2)+'\n',encoding='utf8')
    (out/'inputs.json').write_text(json.dumps(dict(kind='synthetic-technical-fixture',lock_sha256=hashlib.sha256(raw).hexdigest(),input_sha256=inputs,scope='Tiny alignment populations; commands still audit the COMPLETE original selected RefSeq resource. No alignment, differential expression, biological inference or exact paper reproduction.'),indent=2)+'\n',encoding='utf8')
    print('Synthetic RNA recipes written: '+str(out))


if __name__=='__main__':main()
