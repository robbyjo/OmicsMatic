# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete synthetic GWAS file families for native/original MR-MEGA flow."""
import argparse,gzip,json,math,random
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    specs=[('qt-even',{}),('qt-odd',dict(odd=True)),('qt-input-gc',dict(gc=True)),('qt-both-gc',dict(gc=True,gco=True)),
        ('qt-output-deflation',dict(gco=True,deflation=True)),('qt-inferred-1',dict(infer=1)),('qt-inferred-4',dict(infer=4)),
        ('binary-lower-ci',dict(binary=True)),('binary-both-gc',dict(binary=True,gc=True,gco=True)),
        ('no-alleles-source-flip',dict(noalleles=True)),('swapped-alleles',dict(swap=True)),('duplicate-overwrite',dict(duplicate=True)),
        ('allele-mismatch-N-retained',dict(mismatch=True)),('coordinate-conflicts',dict(conflict=True)),('partial-cohorts',dict(partial=True)),
        ('numeric-filter',dict(filter=True)),('absent-filter-column',dict(filter=True,absent=True)),('custom-column-aliases',dict(alias=True)),
        ('last-matching-column',dict(last=True)),('gzip-cohort-and-list',dict(gzip=True)),('filtered-invalid-numbers',dict(filter=True,invalid_filtered=True)),
        ('literal-N-allele',dict(literal_n=True))]
    cases=[]
    for number,(id,spec) in enumerate(specs):
        home=out/id;home.mkdir();rng=random.Random(918276+number);n=8;axes=spec.get('infer',2);m=33 if spec.get('odd') else 32
        settings=dict(schema_version=1,trait='binary' if spec.get('binary') else 'quantitative',axes=axes,ancestry='inferred' if spec.get('infer') else 'precalculated',
            no_alleles=bool(spec.get('noalleles')),input_gc=bool(spec.get('gc')),output_gc=bool(spec.get('gco')),standard_names=not spec.get('alias',False),aliases={},filters=[])
        columns=['MARKERNAME','CHROMOSOME','POSITION','EA','NEA','EAF','N']+(['OR','OR_95L','OR_95U'] if spec.get('binary') else ['BETA','SE'])
        if spec.get('noalleles'):columns=[c for c in columns if c not in ['EA','NEA']]
        if spec.get('filter'):columns.append('INFO');settings['filters']=[dict(column='INFO',operator='>',value=.7)]
        names={col:('ALT_'+col if spec.get('alias') else col) for col in columns}
        if spec.get('alias'):settings['aliases']={col:[name] for col,name in names.items()}
        files=[];pc=[[(c-3.5)*.09,((c-3.5)**2-5.25)*.013] for c in range(n)]
        for c in range(n):
            current=[v for v in columns if not (spec.get('absent') and c==3 and v=='INFO')]
            header=[names[col] for col in current]
            if spec.get('last'):header.insert(0,'BETA')
            lines=['\t'.join(header)]
            for row in range(m):
                if spec.get('partial') and ((row==3 and c>=4) or (row==5 and c==0)):continue
                beta=.17*math.sin(row*.37)+.08*pc[c][0]+.03*pc[c][1]+rng.uniform(-.04,.04)
                se=.09+rng.uniform(0,.09)
                if spec.get('deflation'):beta*=.025
                ea,nea='A','G';frequency=rng.uniform(.03,.97)
                if spec.get('literal_n') and row==4:ea='N' if c==0 else 'T';nea='G'
                if spec.get('swap') and c%2:ea,nea=nea,ea;frequency=1-frequency;beta=-beta
                if spec.get('mismatch') and row==4 and c==1:nea='C'
                chromosome=1+row%3;position=(1+row//3)*1000000+137
                if spec.get('conflict') and row==4 and c==1:chromosome=5;position+=17
                values=dict(MARKERNAME='variant-'+str(row),CHROMOSOME=chromosome,POSITION=position,EA=ea,NEA=nea,EAF=frequency,N=900+c*13,BETA=beta,SE=se,
                    OR=math.exp(beta),OR_95L=math.exp(beta-1.96*se),OR_95U=math.exp(beta+3.731*se),INFO=.5 if row%7==0 else .95)
                if spec.get('invalid_filtered') and row%7==0:values['BETA']='NA';values['SE']='NA'
                def line(v):
                    fields=[format(v[col],'.17g') if isinstance(v[col],float) else str(v[col]) for col in current]
                    if spec.get('last'):fields.insert(0,'91.234')
                    return '\t'.join(fields)
                lines.append(line(values))
                if spec.get('duplicate') and row==4:
                    values=dict(values,BETA=beta*.4+.01,SE=se*1.2,EAF=frequency*.8,N=700+c*11);lines.append(line(values))
            path=home/('cohort-'+str(c)+('.tsvgz' if spec.get('gzip') else '.tsv'));raw=('\n'.join(lines)+'\n').encode()
            path.write_bytes(gzip.compress(raw,mtime=0) if spec.get('gzip') else raw);files.append(path)
        lists={}
        for platform in ['windows','linux']:
            convert=(lambda p:str(p.resolve())) if platform=='windows' else linux
            lines=[convert(path)+(' '+' '.join(format(v,'.17g') for v in pc[c]) if not spec.get('infer') else '') for c,path in enumerate(files)]
            path=home/('cohorts-'+platform+('.in.gz' if spec.get('gzip') else '.in'));raw=('\n'.join(lines)+'\n').encode()
            path.write_bytes(gzip.compress(raw,mtime=0) if spec.get('gzip') else raw);lists[platform]=path.relative_to(out).as_posix()
        save(home/'settings.json',settings)
        cases.append(dict(id=id,settings=settings,file_lists=lists,settings_file=(home/'settings.json').relative_to(out).as_posix(),cohort_files=[p.relative_to(out).as_posix() for p in files],spec=spec))
    save(out/'cases.json',cases);pins={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()}
    save(out/'COMPLETED.json',dict(status='PASS_SYNTHETIC_COMPLETE_GWAS_WORKFLOW_CONTROLS_ONLY',cases=len(cases),files=pins,
        source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False))
    print('Created',len(cases),'complete GWAS workflow control families; no result credit')


if __name__=='__main__':main()
