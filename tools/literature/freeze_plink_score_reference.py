"""Freeze actual original PLINK score fields and independently oriented inputs."""
from pathlib import Path
import argparse,gzip,hashlib,io,json,platform

ROOT=Path(__file__).resolve().parents[2]
sha=lambda body:hashlib.sha256(body).hexdigest()

def packed(body):
    buffer=io.BytesIO()
    with gzip.GzipFile(filename='',fileobj=buffer,mode='wb',mtime=0) as stream:stream.write(body)
    return buffer.getvalue()

def table(path):
    lines=path.read_text(encoding='utf-8').splitlines();keys=lines[0].lstrip('#').split()
    return [dict(zip(keys,line.split(),strict=True)) for line in lines[1:] if line.strip()]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--reference',type=Path,required=True);args=parser.parse_args()
    reference=args.reference.resolve();proof=json.loads((reference/'verification.json').read_text(encoding='utf-8'))
    assert proof['status']=='passed-original-frequency-HWE-score-math;adapter-not-implemented'
    assert proof['original_process_calls']==6 and proof['compared_score_sums']==291 and proof['compared_score_averages']==97
    for call in proof['calls']:
        directory=Path(call['command'][call['command'].index('--out')+1]).parent
        for name,digest in call['output_sha256'].items():assert sha((directory/name).read_bytes())==digest,(directory,name)
    data=json.loads((reference/'inputs.json').read_text(encoding='utf-8'));samples=data['samples'];weights=data['weights'];variants=data['variants'];matrix=data['a1_dosages']
    assert len(samples)==97 and len(weights)==16 and len(matrix)==20
    sample_text='fid\tiid\n'+''.join(s['fid']+'\t'+s['iid']+'\n' for s in samples)
    variant_lines=['variant\tweight\tcounted_frequency\t'+'\t'.join(str(i) for i in range(len(samples)))]
    for weight in weights:
        index=weight['variant_index'];values=matrix[index];called=[v for v in values if v is not None];frequency=sum(called)/(2*len(called))
        assert weight['variant']==variants[index]['id']
        if weight['allele']==variants[index]['a2']:values=[None if v is None else 2-v for v in values];frequency=1-frequency
        else:assert weight['allele']==variants[index]['a1']
        variant_lines.append(weight['variant']+'\t'+repr(weight['weight'])+'\t'+repr(frequency)+'\t'+'\t'.join('NA' if v is None else str(v) for v in values))
    rows=['engine\tpolicy\tsample_index\tfid\tiid\toriginal_sum\toriginal_average\toriginal_observed_alleles'];source_hashes={};sums=averages=0
    for engine in ['plink1','plink2']:
        for policy in ['mean','omit']:
            source=reference/(engine+'-score-'+policy)/('result.profile' if engine=='plink1' else 'result.sscore');source_hashes[str(source)]=sha(source.read_bytes());seen=set()
            for row in table(source):
                index=next(i for i,s in enumerate(samples) if (s['fid'],s['iid'])==(row['FID'],row['IID']));assert index not in seen;seen.add(index)
                if engine=='plink1':total=row['SCORESUM'] if policy=='mean' else '';average=row['SCORE'] if policy=='omit' else '';observed=row['CNT']
                else:total=row['WEIGHT_SUM'];average=row['WEIGHT_AVG'];observed=row['ALLELE_CT']
                sums+=bool(total);averages+=bool(average);rows.append('\t'.join([engine,policy,str(index),row['FID'],row['IID'],total,average,observed]))
            assert len(seen)==97
    assert len(rows)==389 and sums==averages==291
    bodies={'samples.tsv.gz':packed(sample_text.encode()),'variants.tsv.gz':packed(('\n'.join(variant_lines)+'\n').encode()),'reference.tsv.gz':packed(('\n'.join(rows)+'\n').encode())}
    destination=ROOT/'src/test/resources/org/jlinalg/genetics/plink-scores';destination.mkdir(parents=True,exist_ok=True)
    metadata=dict(status='frozen-actual-original-score-fields;Java-validation-separate',producer_sha256=sha(Path(__file__).read_bytes()),python=platform.python_version(),reference_campaign_sha256=sha((reference/'verification.json').read_bytes()),reference_input_sha256=sha((reference/'inputs.json').read_bytes()),samples=97,score_variants=16,original_rows=388,original_sum_fields=291,original_average_fields=291,source_table_sha256=source_hashes,resource_sha256={name:sha(body) for name,body in bodies.items()},original_commands=proof['calls'],scope='Actual official original1.9.0/2.0.0-b.1.2 scoring fields,including original2averages not independently checked by the earlier sum-focused campaign. Derived core input is counted-allele oriented from independently generated known genotypes and explicit weights;frequency is the observed founder frequency in that fixture. Mean-imputed denominators differ from reported observed-allele counts. Original1.9sum+no-mean-imputation is unsupported,so that sum field stays blank. No Java result,average field or missing original statistic is invented.')
    bodies['provenance.json']=(json.dumps(metadata,indent=2)+'\n').encode()
    for name,body in bodies.items():
        target=destination/name
        if target.exists():assert target.read_bytes()==body,('Changed frozen reference;select a new explicit revision',target)
        else:target.write_bytes(body)
    print(json.dumps(dict(status=metadata['status'],resources=metadata['resource_sha256'],rows=388,sum_fields=sums,average_fields=averages)),flush=True)

if __name__=='__main__':main()
