"""Exercise the packaged native scorer against known mathematics and real PLINKs."""
from pathlib import Path
import argparse,datetime as dt,hashlib,json,math,os,random,shutil,subprocess,sys,time,traceback

ROOT=Path(__file__).resolve().parents[2]
ENGINES={'plink1':'efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b',
         'plink2':'f498c754dd97b0010ae907fb92561f9cf5e2bca6fa15e0c527375077985f8105'}
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()

def table(path):
    lines=path.read_text(encoding='utf-8').splitlines();header=lines[0].lstrip('#').split()
    return [dict(zip(header,line.split(),strict=True)) for line in lines[1:] if line.strip()]

def native_table(path):
    lines=path.read_text(encoding='utf-8').splitlines();header=lines[0].split('\t')
    return [dict(zip(header,line.split('\t'),strict=True)) for line in lines[1:] if line]

def identities(rows,fid,iid):
    indexed={}
    for row in rows:
        key=(row[fid],row[iid]);assert key not in indexed,('Duplicate composite sample identity',key)
        indexed[key]=row
    return indexed

def displayed(literal,value,absolute_bound,label):
    reference=float(literal);assert math.isfinite(reference) and math.isfinite(value),(label,literal,value)
    quantum=0 if reference==0 else 10**(math.floor(math.log10(abs(reference)))-5)
    assert abs(reference-value)<=quantum/2+32*math.ulp(absolute_bound),(label,reference,value)

def fixture(directory,n,v,external,missing,seed,all_missing_sample=False):
    directory.mkdir();rng=random.Random(seed);calls=[];variants=[];weights=[];frequencies=[]
    for j in range(v):
        a1,a2=[('C','A'),('G','T'),('A','G'),('T','C')][j%4];p=.1+.7*(j%7)/6
        row=[int(rng.random()<p)+int(rng.random()<p) for _ in range(n)]
        if missing:
            for i in range(n):
                if rng.random()<.04:row[i]=None
        if all_missing_sample:row[0]=None
        assert any(x is not None for x in row)
        p1=[0,.13,.5,.87,1][j%5] if external else sum(x for x in row if x is not None)/(2*sum(x is not None for x in row))
        effect=a2 if j%2 else a1;frequency=1-p1 if j%2 else p1;weight=((j%9)-4)/16
        variants.append(dict(id='rs'+str(10000+j),a1=a1,a2=a2,chromosome=1+j*22//v,position=1000+j*1000));calls.append(row)
        weights.append(dict(id=variants[-1]['id'],ea=effect,oa=a1 if j%2 else a2,weight=weight,frequency=frequency));frequencies.append(p1)
    samples=[('F'+str(i//17).zfill(3),'S'+str(i%17).zfill(3)) for i in range(n)]
    prefix=directory/'known';data=bytearray([0x6c,0x1b,1]);encoding={2:0,1:2,0:3,None:1}
    for row in calls:
        for start in range(0,n,4):data.append(sum(encoding[value]<<(2*i) for i,value in enumerate(row[start:start+4])))
    prefix.with_suffix('.bed').write_bytes(data)
    prefix.with_suffix('.fam').write_text(''.join(f'{f} {i} 0 0 {1+s%2} -9\n' for s,(f,i) in enumerate(samples)),encoding='utf-8')
    prefix.with_suffix('.bim').write_text(''.join(f"{a['chromosome']} {a['id']} 0 {a['position']} {a['a1']} {a['a2']}\n" for a in variants),encoding='utf-8')
    model=directory/'native-weights.tsv';model.write_text('variant\tea\toa\tweight\tea_frequency\n'+''.join(f"{a['id']}\t{a['ea']}\t{a['oa']}\t{a['weight']}\t{a['frequency']!r}\n" for a in weights),encoding='utf-8')
    original=directory/'original-weights.tsv';original.write_text('ID A1 WEIGHT\n'+''.join(f"{a['id']} {a['ea']} {a['weight']}\n" for a in weights),encoding='utf-8')
    freq=directory/'external.frq';freq.write_text('CHR SNP A1 A2 MAF NCHROBS\n'+''.join(f"{a['chromosome']} {a['id']} {a['a1']} {a['a2']} {p!r} 20000\n" for a,p in zip(variants,frequencies,strict=True)),encoding='utf-8')
    snapshot=dict(samples=samples,variants=variants,weights=weights,a1_genotypes=calls,external_frequencies=external)
    (directory/'inputs.json').write_text(json.dumps(snapshot,indent=2)+'\n',encoding='utf-8')
    return dict(prefix=prefix,model=model,original=original,freq=freq,**snapshot)

def expected(data,mode):
    result={};bound=sum(2*abs(w['weight']) for w in data['weights'])
    for i,key in enumerate(data['samples']):
        terms=[];observed=0
        for j,weight in enumerate(data['weights']):
            value=data['a1_genotypes'][j][i]
            if value is None:
                if mode=='omit':continue
                value=2*weight['frequency']
            else:
                observed+=1
                if weight['ea']==data['variants'][j]['a2']:value=2-value
            terms.append(value*weight['weight'])
        denominator=2*(observed if mode=='omit' else len(data['weights']))
        total=math.fsum(terms) if denominator else None
        result[tuple(key)]=dict(total=total,average=total/denominator if denominator else None,observed=2*observed,denominator=denominator,imputed=len(data['weights'])-observed if mode=='mean-impute' else 0,bound=bound)
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--java',required=True);parser.add_argument('--additional-java',action='append',default=[]);parser.add_argument('--plink1',type=Path,required=True);parser.add_argument('--plink2',type=Path,required=True);parser.add_argument('--work',type=Path,required=True);args=parser.parse_args()
    args.jar=args.jar.resolve();args.work=args.work.resolve();args.work.mkdir(parents=True,exist_ok=False)
    shutil.copy2(__file__,args.work/'producer.py')
    record=dict(status='running',jar_sha256=sha(args.jar),producer_sha256=sha(__file__),java_calls=[],original_calls=[],profiles=[],controls=[],scope='Actual packaged nativeJava,independent known-call mathematics and authenticated original1.9.0/2.0.0-b.1.2 references. Reportable sums/averages and all composite identities/counts are checked;original all-missing omission fields remain raw while native marks them unreportable. No full PLINK/GLM/PCA or historical-paper parity.')
    started=time.monotonic();environment=os.environ.copy();environment['PATH']=str(Path(os.environ.get('SystemRoot','C:/Windows'))/'System32')
    def run(command,directory,kind,wanted=0):
        directory.mkdir(parents=True,exist_ok=False);start=time.monotonic();result=subprocess.run(list(map(str,command)),cwd=ROOT,env=environment,capture_output=True,timeout=180)
        (directory/'stdout.txt').write_bytes(result.stdout);(directory/'stderr.txt').write_bytes(result.stderr)
        entry=dict(command=list(map(str,command)),exit_code=result.returncode,expected_exit=wanted,seconds=time.monotonic()-start,stdout_sha256=sha(directory/'stdout.txt'),stderr_sha256=sha(directory/'stderr.txt'));record[kind].append(entry)
        assert result.returncode==wanted,(command,result.returncode,wanted,result.stderr.decode('utf-8',errors='replace')[-1800:]);return entry
    def java(data,mode,label,extra=(),wanted=0,jvm=None):
        directory=args.work/label;out=directory/'output';command=[jvm or args.java,'-jar',str(args.jar),'genotype-score','--bfile',str(data['prefix']),'--weights',str(data['model']),'--missing',mode,'--genome-build','unspecified','--out',str(out),*extra]
        run(command,directory,'java_calls',wanted);return out
    try:
        for name in ENGINES:
            path=getattr(args,name).resolve()
            if not path.is_file() or sha(path)!=ENGINES[name]:
                (args.work/'WARNINGS.md').write_text('# ORIGINAL REFERENCE VALIDATION SKIPPED\n\nMissing or changed '+name+' reference. Supply the exact permitted official reference binary and rerun with --'+name+' PATH. Native genotype-score itself needs only Java;this complete fresh-original campaign is skipped until its reference dependency is available.\n',encoding='utf-8')
                record.update(status='skipped-reference-dependency-unavailable',missing=name);return 3
        models=[]
        for index,(name,n,v,external,missing,all_missing) in enumerate([('complete',13,8,False,False,False),('external',65,24,True,True,True),('wide',257,128,False,True,False)]):
            data=fixture(args.work/('input-'+name),n,v,external,missing,20261026+index,all_missing);models.append(data)
            for mode in ['mean-impute','omit']:
                out=java(data,mode,name+'-'+mode);rows=identities(native_table(out/'results.tsv'),'fid','iid');truth=expected(data,mode);assert set(rows)==set(truth)
                for key,target in truth.items():
                    row=rows[key];assert int(row['observed_alleles'])==target['observed'] and int(row['denominator_alleles'])==target['denominator'] and int(row['imputed_variants'])==target['imputed']
                    if target['total'] is None:assert row['score_sum']==row['score_average']=='' and row['status']=='NO_OBSERVED_DOSAGES'
                    else:
                        assert abs(float(row['score_sum'])-target['total'])<=32*math.ulp(target['bound'])
                        assert abs(float(row['score_average'])-target['average'])<=32*math.ulp(target['bound']/target['denominator'])
                comparison=dict(profile=name,missing=mode,samples=n,variants=v,original_fields=0,project_unreportable_samples=sum(t['total'] is None for t in truth.values()))
                for engine in ['plink1','plink2']:
                    directory=args.work/(name+'-'+mode+'-'+engine);directory.mkdir();prefix=directory/'result';command=[str(getattr(args,engine).resolve()),'--bfile',str(data['prefix']),'--threads','1','--memory','128' if engine=='plink1' else '640']
                    if engine=='plink1':command+=['--keep-allele-order']
                    # Use the same explicit known frequencies as the native model.
                    # Current PLINK2 deliberately refuses to estimate frequencies
                    # from the 13-sample complete control; keep that small case.
                    command+=['--read-freq',str(data['freq'])]
                    command+=['--score',str(data['original']),'1','2','3']
                    command+=(['header','sum'] if mode=='mean-impute' else ['header','no-mean-imputation']) if engine=='plink1' else ['header-read','cols=+scoresums']+(['no-mean-imputation'] if mode=='omit' else [])
                    command+=['--out',str(prefix)]
                    logdir=directory/'process';run(command,logdir,'original_calls');source=prefix.with_suffix('.profile' if engine=='plink1' else '.sscore');originals=identities(table(source),'FID','IID');assert set(originals)==set(rows)
                    for key,target in truth.items():
                        raw=originals[key];assert int(raw['CNT' if engine=='plink1' else 'ALLELE_CT'])==target['observed']
                        if target['total'] is None:continue
                        if engine=='plink1':fields=[(raw['SCORESUM'],target['total'],target['bound'])] if mode=='mean-impute' else [(raw['SCORE'],target['average'],target['bound']/target['denominator'])]
                        else:fields=[(raw['WEIGHT_SUM'],target['total'],target['bound']),(raw['WEIGHT_AVG'],target['average'],target['bound']/target['denominator'])]
                        for literal,value,bound in fields:displayed(literal,value,bound,(name,mode,engine,key));comparison['original_fields']+=1
                    comparison.setdefault('raw_original_tables',{})[engine]=dict(path=str(source),sha256=sha(source))
                record['profiles'].append(comparison)
        complete=models[0];java(complete,'error','complete-error-policy');record['controls'].append('complete-error-policy-accepted')
        java(models[1],'error','missing-error-policy',wanted=2);record['controls'].append('missing-error-policy-fails')
        for label,extra in [('cells',['--maximum-scored-cells','1']),('samples',['--maximum-samples','1']),('variants',['--maximum-scored-variants','1'])]:
            out=java(complete,'omit','limit-'+label,extra,wanted=2);assert not (out/'results.tsv').exists() and (out/'WARNINGS.md').is_file();record['controls'].append('bounded-'+label)
        before=sha(args.work/'complete-omit/output/results.tsv');directory=args.work/'existing-output-attempt'
        run([args.java,'-jar',args.jar,'genotype-score','--bfile',complete['prefix'],'--weights',complete['model'],'--missing','omit','--genome-build','unspecified','--out',args.work/'complete-omit/output'],directory,'java_calls',2)
        assert sha(args.work/'complete-omit/output/results.tsv')==before;record['controls'].append('existing-output-unchanged')
        for i,jvm in enumerate([args.java,*args.additional_java]):
            directory=args.work/('Unicode检查-'+str(i));directory.mkdir();copied={}
            for suffix in ['.bed','.bim','.fam']:shutil.copy2(Path(str(complete['prefix'])+suffix),directory/('输入'+suffix))
            shutil.copy2(complete['model'],directory/'权重.tsv');out=directory/'结果';recipe=args.work/('recipe-'+str(i)+'.json')
            recipe.write_text(json.dumps(dict(bfile=str(directory/'输入'),weights=str(directory/'权重.tsv'),missing='omit',**{'genome-build':'unspecified'},out=str(out)),ensure_ascii=False),encoding='utf-8')
            run([jvm,'-jar',args.jar,'genotype-score','--options-file',recipe],directory/'process','java_calls');assert len(native_table(out/'results.tsv'))==13
            record['controls'].append('Unicode-recipe-Java-'+str(i))
        def cloned(label):
            directory=args.work/('input-control-'+label);directory.mkdir()
            for suffix in ['.bed','.bim','.fam']:
                shutil.copy2(Path(str(complete['prefix'])+suffix),directory/('known'+suffix))
            shutil.copy2(complete['model'],directory/'native-weights.tsv')
            return dict(complete,prefix=directory/'known',model=directory/'native-weights.tsv')
        def replace(path,transform):
            path.write_text(transform(path.read_text(encoding='utf-8')),encoding='utf-8')
        def rejected(label,mutate,mode='omit',cause=None,partial=None):
            data=cloned(label);mutate(data)
            inputs=[Path(str(data['prefix'])+s) for s in ['.bed','.bim','.fam']]+[data['model']]
            before={str(p):sha(p) for p in inputs}
            out=java(data,mode,'rejected-'+label,wanted=2)
            assert not any((out/name).exists() for name in ['results.tsv','variant-metadata.tsv','sample-metadata.tsv']),label
            assert json.loads((out/'analysis-status.json').read_text())['status']=='failed',label
            warning=(out/'WARNINGS.md').read_text(encoding='utf-8')
            assert warning.startswith('# GENOTYPE SCORING FAILED') and 'Required action:' in warning,label
            if cause:assert cause.lower() in warning.lower(),(label,warning)
            if partial:assert (out/'partial'/partial).is_file(),label
            assert before=={str(p):sha(p) for p in inputs},label
            record['controls'].append('rejected-with-recovery-'+label)
        rejected('duplicate-weight',lambda d:replace(d['model'],lambda s:s+s.splitlines()[1]+'\n'),cause='Duplicate weight')
        rejected('missing-weight-variant',lambda d:replace(d['model'],lambda s:s.replace('rs10000','absent-variant')),cause='absent-variant')
        rejected('allele-mismatch',lambda d:replace(d['model'],lambda s:s.replace('rs10007\tC\tT','rs10007\tA\tT')),cause='allele-pair mismatch',partial='variant-metadata.tsv')
        rejected('allele-case',lambda d:replace(d['model'],lambda s:s.replace('rs10000\tC\tA','rs10000\tc\tA')),cause='allele-pair mismatch')
        rejected('missing-frequencies',lambda d:replace(d['model'],lambda s:'\n'.join('\t'.join(line.split('\t')[:-1]) for line in s.splitlines())+'\n'),mode='mean-impute',cause='ea_frequency')
        rejected('invalid-frequency',lambda d:replace(d['model'],lambda s:s.replace(s.splitlines()[1],s.splitlines()[1].rsplit('\t',1)[0]+'\t1.01')),cause='frequency')
        rejected('nonfinite-weight',lambda d:replace(d['model'],lambda s:s.replace('-0.25','NaN',1)),cause='Nonfinite weight')
        rejected('duplicate-composite-sample',lambda d:replace(Path(str(d['prefix'])+'.fam'),lambda s:s+s.splitlines()[0]+'\n'),cause='Duplicate')
        rejected('duplicate-selected-BIM',lambda d:replace(Path(str(d['prefix'])+'.bim'),lambda s:s+s.splitlines()[0]+'\n'),cause='Duplicate')
        rejected('sex-chromosome',lambda d:replace(Path(str(d['prefix'])+'.bim'),lambda s:'X'+s[1:]),cause='outside the diploid human autosomal')
        def padding(data):
            path=Path(str(data['prefix'])+'.bed');body=bytearray(path.read_bytes());body[6]|=0x80;path.write_bytes(body)
        rejected('nonzero-padding',padding,cause='padding')
        rejected('truncated-BED',lambda d:Path(str(d['prefix'])+'.bed').write_bytes(Path(str(d['prefix'])+'.bed').read_bytes()[:-1]),cause='size')
        def all_missing(data):
            Path(str(data['prefix'])+'.bed').write_bytes(bytes([0x6c,0x1b,1])+bytes([0x55,0x55,0x55,1])*8)
        rejected('all-missing-omission',all_missing,cause='No sample has a reportable score',partial='results.tsv')
        reversed_model=cloned('reversed-weights');replace(reversed_model['model'],lambda s:'\n'.join([s.splitlines()[0],*reversed(s.splitlines()[1:])])+'\n')
        out=java(reversed_model,'omit','reversed-weights');assert (out/'results.tsv').read_bytes()==(args.work/'complete-omit/output/results.tsv').read_bytes();record['controls'].append('model-row-order-independent')
        csv_model=cloned('CSV');csv_model['model']=csv_model['model'].with_suffix('.csv')
        csv_model['model'].write_text('\n'.join(','.join('"'+cell+'"' for cell in line.split('\t')) for line in complete['model'].read_text().splitlines())+'\n',encoding='utf-8')
        out=java(csv_model,'omit','quoted-CSV');assert (out/'results.tsv').read_bytes()==(args.work/'complete-omit/output/results.tsv').read_bytes();record['controls'].append('quoted-CSV-weights')
        assert sha(args.jar)==record['jar_sha256'];record.update(status='passed',reference_workflows=len(record['profiles']),compared_original_fields=sum(p['original_fields'] for p in record['profiles']),compared_native_rows=sum(p['samples'] for p in record['profiles']),actual_original_calls=len(record['original_calls']),packaged_java_calls=len(record['java_calls']),control_checks=len(record['controls']))
        return 0
    except BaseException as error:record.update(status='failed-retained',error=repr(error));raise
    finally:
        record.update(seconds=time.monotonic()-started,recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat());(args.work/'verification.json').write_text(json.dumps(record,indent=2,ensure_ascii=False)+'\n',encoding='utf-8');print(json.dumps({k:record[k] for k in ['status','reference_workflows','compared_original_fields','actual_original_calls','packaged_java_calls','control_checks','error','seconds'] if k in record}),flush=True)

if __name__=='__main__':raise SystemExit(main())
