"""Translate frozen original-source option fixtures to explicit native prepared-data recipes."""
from pathlib import Path
import hashlib,json,zipfile

METRICS={'Signal2Noise':'SIGNAL_TO_NOISE','tTest':'T_TEST','Diff_of_Classes':'DIFFERENCE','Ratio_of_Classes':'RATIO','log2_Ratio_of_Classes':'LOG2_RATIO'}
WEIGHTS={'classic':'CLASSIC','weighted':'WEIGHTED','weighted_p2':'WEIGHTED_P2','weighted_p1.5':'WEIGHTED_P1_5'}
RANDOM={'no_balance':'NO_BALANCE','balance_class0':'BALANCE_CLASS0','balance_class1':'BALANCE_CLASS1','equalize_and_balance':'EQUALIZE_AND_BALANCE'}

def save(path,value):path.write_text(json.dumps(value,indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n')
def prepare(fixture,out,limits):
    out=Path(out);out.mkdir(exist_ok=False);cases=out/'cases';cases.mkdir();reference=out/'reference';reference.mkdir()
    with zipfile.ZipFile(fixture) as z:
        if z.testzip() is not None:raise ValueError('Corrupt original fixture')
        names=z.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate original fixture members')
        manifest={line.split('  ',1)[1]:line.split('  ',1)[0] for line in z.read('SHA256SUMS').decode().splitlines()}
        if set(names)!={'SHA256SUMS',*manifest} or len(manifest)!=624:raise ValueError('Original fixture population differs')
        for name,wanted in manifest.items():
            if hashlib.sha256(z.read(name)).hexdigest()!=wanted:raise ValueError('Changed original fixture '+name)
        analyses=[];mapping={}
        for name in sorted(manifest):
            rows=[r.split('\t') for r in z.read(name).decode('utf-8').splitlines()]
            if next(r[1] for r in rows if r[0]=='NATIVE_POLICY')!='ADMIT':continue
            c=next(r for r in rows if r[0]=='CONFIG');mode=c[2];edition=c[3];identifier=edition+'-'+Path(name).stem
            if mode not in {'preranked','phenotype','gene_set'}:raise ValueError('Unknown fixture form '+mode)
            data=[r for r in rows if r[0]=='DATA'];prerank=[r for r in rows if r[0]=='PRERANK']
            sets=[dict(name=r[1],members=r[2:]) for r in rows if r[0]=='INPUT_SET']
            prepared=dict(schema_version=1,genes=[r[1] for r in (prerank if mode=='preranked' else data)],gene_sets=sets,
                provenance=dict(organism='Synthetic original-source option fixture; no organism assigned',identifier_namespace='Literal synthetic source-fixture identifiers',expression_units='Explicit prepared float32 source-fixture values',universe_description='All supplied fixture genes, without remapping',gene_set_source='Synthetic original-engine option/boundary fixtures',preparation_note='Frozen original source objects; no author-data or biological reproduction'))
            if mode=='preranked':prepared['ranking_scores']=[float(r[2]) for r in prerank]
            else:
                prepared['expression']=[[float(x) for x in r[2:]] for r in data]
                prepared['samples']=['sample-'+str(i) for i in range(len(data[0])-2)]
                prepared['class0']=[int(x) for x in next(r[1:] for r in rows if r[0]=='GROUP0')]
                prepared['class1']=[int(x) for x in next(r[1:] for r in rows if r[0]=='GROUP1')]
            save(cases/(identifier+'.json'),prepared);(reference/(identifier+'.tsv')).write_bytes(z.read(name))
            form={'preranked':'PRERANKED','phenotype':'EXPRESSION_PHENOTYPE','gene_set':'EXPRESSION_GENE_SET'}[mode]
            a=dict(id=identifier,input='cases/'+identifier+'.json',form=form,edition=edition,normalization='meandiv',permutations=int(c[15]),seed=c[16],minimum_size=int(c[13]),maximum_size=int(c[14]),weight=WEIGHTS[c[12]])
            if mode!='preranked':a['ranking']=dict(metric=METRICS[c[4]],median=c[5]=='true',biased_variance=c[6]=='true',fix_low_variance=c[7]=='true',absolute_sort=c[8]=='true',ascending=c[9]=='true',class_of_interest=int(c[10]))
            if mode=='phenotype':a['randomization']=RANDOM[c[11]]
            analyses.append(a)
        if len(analyses)!=423:raise ValueError('Different admitted fixture population')
        for i in range(0,len(analyses),64):
            index=i//64;batch=analyses[i:i+64];name='batch-%02d'%index
            save(out/(name+'.json'),dict(schema_version=1,mode='RUN',engine='native-broad-gsea-source-core',out=name+'-out',limits=limits,analyses=batch))
            mapping[name]=[a['id'] for a in batch]
        save(out/'BATCHES.json',mapping)
    return mapping
