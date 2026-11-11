# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pack complete original LOFTEE controls with independent Python transport ledgers; no upstream runtime redistributon."""
import argparse,hashlib,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--probe',action='append',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/loftee-runtime') and not out.exists();out.mkdir(parents=True);files={};cases=[]
    for given in a.probe:
        home=given.resolve();proof=json.loads((home/'COMPLETED.json').read_bytes());assert proof['status'].startswith('passed-original-VEP105-LOFTEE-') and len(proof['cases'])==2
        for case in proof['cases']:
            name=home.name+'-'+case['case'];assert name not in cases;cases.append(name);base=home/case['case']
            for file in (home/'inputs').iterdir():
                if file.is_file():files[name+'/inputs/'+file.name]=file.read_bytes()
            for file in base.iterdir():
                if file.is_file():files[name+'/original/'+file.name]=file.read_bytes()
            for suffix in ['.stdout','.stderr','.process.json']:files[name+'/original/vep'+suffix]=(home/(case['case']+suffix)).read_bytes()
            files[name+'/PROBE-COMPLETED.json']=(home/'COMPLETED.json').read_bytes();files[name+'/CASE.json']=(json.dumps(case,indent=2)+'\n').encode();headers=None;annotations=['row\tannotation\tallele_number\tchromosome\tposition\tid\tref\talt\tgene\tfeature\tconsequence\tconfidence\tfilters\tflags\tinfo\traw_CSQ'];variants=['row\tchromosome\tposition\tid\tref\talts\tannotations\ttargets\tHC\tLC\tOS'];count=0
            for line in (base/'original.vcf').read_text(encoding='utf8').splitlines():
                if line.startswith('##INFO=<ID=CSQ,'):headers=line.split('Format: ',1)[1].rsplit('"',1)[0].split('|')
                elif not line.startswith('#'):
                    count+=1;row=line.split('\t');alts=row[4].split(',');raw=next(x[4:] for x in row[7].split(';') if x.startswith('CSQ='));values=[dict(zip(headers,x.split('|'))) for x in raw.split(',')];hc=lc=os=targets=0
                    for index,(text,v) in enumerate(zip(raw.split(','),values),1):
                        number=int(v['ALLELE_NUM']);assert 1<=number<=len(alts);annotations.append('\t'.join(map(str,[count,index,number,*row[:4],alts[number-1],v['Gene'],v['Feature'],v['Consequence'],v['LoF'],v['LoF_filter'],v['LoF_flags'],v['LoF_info'],text])));hc+=v['LoF']=='HC';lc+=v['LoF']=='LC';os+=v['LoF']=='OS';targets+=v['BIOTYPE']=='protein_coding' and v['Feature_type']=='Transcript' and bool(set(v['Consequence'].split('&'))&{'stop_gained','frameshift_variant','splice_donor_variant','splice_acceptor_variant'})
                    variants.append('\t'.join(map(str,[count,*row[:5],len(values),targets,hc,lc,os])))
            assert count==case['summary']['variants']==15
            for label,lines in [('annotations',annotations),('variants',variants)]:
                raw=('\n'.join(lines)+'\n').encode();assert raw==(base/('native-'+label+'.tsv')).read_bytes(),'Independent Python/source ledger differs from Java ledger';files[name+'/expected-'+label+'.tsv']=raw
    assert len(cases)==4;fingerprint=lambda raw:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());index=dict(schema_version=1,status='complete-original-VEP105-LOFTEE-synthetic-transports',cases=cases,files={n:fingerprint(v) for n,v in sorted(files.items())},independent_python_source_ledgers_match_java=True,original_program_or_libraries_redistributed=False,LOFTEE_annotation_admitted=False,implementation_credit=False);files['INDEX.json']=(json.dumps(index,indent=2)+'\n').encode();target=ROOT/'src/test/resources/org/jlinalg/genetics/loftee-original/controls-v1.zip';assert not target.exists();target.parent.mkdir(parents=True,exist_ok=True);(target.parent/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n')
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(files.items()):z.writestr(name,raw)
    save(out/'COMPLETED.json',dict(status='packed-four-complete-original-LOFTEE-output-audit-cases',fixture=dict(path=target.relative_to(ROOT).as_posix(),**pin(target)),original_variants=60,original_annotations=320,cases=4,independent_source_ledgers=8,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},LOFTEE_annotation_admitted=False,implementation_credit=False));print('Packed4 complete original cases,60 variants,320 annotations;8 independent source ledgers match Java')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original family/source checks')
    main()
