#!/usr/bin/env python3
"""Run a fully synthetic local IGV example with an already admitted runtime.

No installer/download is invoked. The complete native audit,original images,
portable original sessions and prominent WARNINGS.md are retained in --out.
"""
import argparse,json,subprocess
from pathlib import Path

HERE=Path(__file__).resolve().parent
def recipe():
    data=HERE/'data'
    def track(identity,kind,file):return dict(id=identity,kind=kind,file=str(data/file),height=80,display='expanded',color=[25,90,180],negative_color=[210,40,30])
    tracks=[track('features','bed6','features.bed')]
    for name,kind,file,units,limits in [('signal','bedgraph','signal.bedgraph','synthetic signed units',[-5,6]),('binary','bigwig','ramp.bw','synthetic original float32 values',[-5,105])]:
        row=track(name,kind,file);row.update(units=units,range=limits,window='none');tracks.append(row)
    row=track('segments','seg','segments.seg');row.update(units='already supplied synthetic log2 ratio',range=[-2,3],coordinates='one-based-closed',mode='raw-supplied-units');tracks.append(row)
    row=track('reads','bam','reads.bam');row.update(index=str(data/'reads.bam.bai'),range=[0,20],height=150);tracks.append(row)
    return dict(assembly='Synthetic chr1 length1000;no biological assembly',reference=str(data/'reference.fa'),reference_index='generate',width=1200,height=1000,sequence=True,views=[dict(id='wide',contig='chr1',start=0,end=600),dict(id='bases',contig='chr1',start=80,end=160)],tracks=tracks,reads=dict(minimum_mapq=10,duplicates=False,failed=False,secondary=False,supplementary=False,soft_clips=True,pairs=False,junctions=True,deletions_in_coverage=True,color_by='strand',base_quality_policy='all-bases;quality-shading-only',overlapping_mates='count-each-alignment',downsample=False))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute()
    if out.exists():parser.error('--out must be new;existing outputs are preserved')
    out.parent.mkdir(parents=True,exist_ok=True)
    # Carrier names stay ASCII even when values contain Unicode/space paths.
    import tempfile
    with tempfile.TemporaryDirectory(prefix='jlinalg-igv-example-') as work:
        directory=Path(work)
        if not str(directory).isascii():parser.error('Use an ASCII system TEMP for Windows Java argument carriers')
        views=directory/'views.json';views.write_text(json.dumps(recipe(),ensure_ascii=False),encoding='utf-8');options=directory/'options.json';options.write_text(json.dumps(dict(recipe=str(views),home=str(args.home.absolute()),out=str(out)),ensure_ascii=False),encoding='utf-8');result=subprocess.run([args.java,'-jar',str(args.jar.absolute()),'igv-tools','--options-file',str(options)])
    print('Review warnings and required actions: '+str(out/'WARNINGS.md'));return result.returncode

if __name__=='__main__':raise SystemExit(main())
