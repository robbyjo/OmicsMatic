"""Offline VCF/BCF/BGEN score acceptance using independent raw-byte encoders.

run NEW_DIRECTORY --jar JAR; verify DIRECTORY --jar THE_PINNED_JAR.
Keep genetic-risk-score-workflow.py alongside this file. All data are synthetic.
Requires NumPy, SciPy, PyYAML and zstandard. No network or model request occurs.
"""
import argparse
import csv
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import zlib
import numpy as np
from scipy.stats import t
import yaml
import zstandard

SUPPORT=Path(__file__).with_name("genetic-risk-score-workflow.py")
spec=importlib.util.spec_from_file_location("genetic_score_fixture_support",SUPPORT);base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
CASES=("vcf-gt","vcf-ds","vcf-gzip","bcf21","bcf22-bgzf","bgen0","bgen1","bgen2","bgen-phased","bgen-external","mixed","imputation","bgen-imputation","unknown-declared","unknown-held","late-duplicate","missing-model","haploid","multiallelic","shared-cells","sidecar-conflict","all-supplied","related-only")
HOLDS={"unknown-held","late-duplicate","missing-model","haploid","multiallelic","shared-cells","sidecar-conflict"}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_text(json.dumps(v,indent=2)+"\n",encoding="utf-8")
def load(p):return json.loads(p.read_text(encoding="utf-8"))
def typed_string(v):
    v=v.encode();n=len(v)
    return (bytes([(n<<4)|7]) if n<15 else bytes([0xf7,0x11,n]) if n<128 else bytes([0xf7,0x12])+struct.pack("<h",n))+v
def bgzf(raw):
    result=bytearray()
    for i in range(0,len(raw),32000):
        part=raw[i:i+32000];c=zlib.compressobj(6,zlib.DEFLATED,-15);body=c.compress(part)+c.flush();size=len(body)+26
        result.extend(bytes.fromhex("1f8b08040000000000ff060042430200")+struct.pack("<H",size-1)+body+struct.pack("<II",zlib.crc32(part),len(part)))
    return bytes(result)+bytes.fromhex("1f8b08040000000000ff0600424302001b0003000000000000000000")
def alleles(j):
    ea,oa=("A","C") if j%2==0 else ("T","G")
    return (ea,oa) if j%3==0 else (oa,ea)
def alt_dosage(effect,j):return 2-effect if j%3==0 else effect
def vcf(path,dose,ds=True,unknown=False,haploid=False,multiallelic=False,duplicate=False,missing=False):
    n,m=dose.shape;order=list(reversed(range(n)));header=['##fileformat=VCFv4.2','##contig=<ID=1,length=1000000>','##contig=<ID=2,length=1000000>','##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">','##FORMAT=<ID=DS,Number=1,Type=Float,Description="ALT dosage">','#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t'+'\t'.join(f"genome-I{i}" for i in order)]
    records=[]
    for j in range(m):
        ref,alt=alleles(j);values=[]
        for i in order:
            d=alt_dosage(dose[i,j],j);gt="./." if np.isnan(d) else "0/0" if d<.5 else "0/1" if d<1.5 else "1/1"
            if unknown:gt="."
            if haploid and j==0:gt="1"
            values.append(gt+(":"+("." if np.isnan(d) else str(d)) if ds else ""))
        records.append('\t'.join(map(str,[1+j%2,999999 if missing and j==0 else 100+j,f"file-alias-{j}",ref,alt+",G" if multiallelic and j==0 else alt,'.','.','.',"GT:DS" if ds else "GT",*values])))
    if duplicate:records.append(records[0].replace("file-alias-0","later-alias"))
    raw=('\n'.join(header+records)+'\n').encode()
    path.write_bytes(gzip.compress(raw,mtime=0) if path.name.endswith('.gz') else raw)
def bcf(path,dose,version,compressed):
    n,m=dose.shape;order=list(reversed(range(n)));header='##fileformat=VCFv4.2\n##FILTER=<ID=PASS,Description="All filters passed",IDX=0>\n##contig=<ID=1,length=1000000,IDX=0>\n##contig=<ID=2,length=1000000,IDX=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype",IDX=1>\n##FORMAT=<ID=DS,Number=1,Type=Float,Description="ALT dosage",IDX=2>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t'+'\t'.join(f"genome-I{i}" for i in order)+'\n\0';text=header.encode();out=bytearray(b'BCF'+bytes([2,version])+struct.pack('<I',len(text))+text)
    for j in range(m):
        ref,alt=alleles(j);values=[alt_dosage(dose[i,j],j) for i in order]
        fixed=struct.pack('<iiifII',j%2,99+j,1,float('nan'),2<<16,(2<<24)|n)
        shared=fixed+typed_string(f"bcf-alias-{j}")+typed_string(ref)+typed_string(alt)+bytes([0x11,0])
        gt=bytearray([0x11,1,0x21]);ds=bytearray([0x11,2,0x15])
        for d in values:
            gt.extend([0,0] if np.isnan(d) else [2,2] if d<.5 else [2,4] if d<1.5 else [4,4]);ds.extend(struct.pack('<I',0x7f800001) if np.isnan(d) else struct.pack('<f',d))
        individual=gt+ds;out.extend(struct.pack('<II',len(shared),len(individual))+shared+individual)
    path.write_bytes(bgzf(out) if compressed else out)
def bgen(path,dose,compression=1,embedded=True,phased=False):
    n,m=dose.shape;order=list(reversed(range(n)));ids=[f"genome-I{i}".encode() for i in order];sample=struct.pack('<I',n)+b''.join(struct.pack('<H',len(x))+x for x in ids);sample=struct.pack('<I',len(sample)+4)+sample if embedded else b''
    out=bytearray(struct.pack('<IIII',20+len(sample),20,m,n)+b'bgen'+struct.pack('<I',8|compression|(0x80000000 if embedded else 0))+sample)
    for j in range(m):
        ref,alt=alleles(j)
        for text in (f"bgen-alias-{j}",f"bgen-rsid-{j}",str(1+j%2)):
            raw=text.encode();out.extend(struct.pack('<H',len(raw))+raw)
        out.extend(struct.pack('<IH',100+j,2))
        for text in (ref,alt):raw=text.encode();out.extend(struct.pack('<I',len(raw))+raw)
        probabilities=bytearray();ploidies=[]
        for i in order:
            original=alt_dosage(dose[i,j],j);ploidies.append(0x82 if np.isnan(original) else 2)
            if np.isnan(original):probabilities.extend([0,0]);continue
            original=int(original)
            pair=((224,240),(200,50),(10,25))[original] if phased else ((224,20),(31,190),(10,30))[original]
            probabilities.extend(pair);actual=2-sum(pair)/255 if phased else (pair[1]+2*(255-sum(pair)))/255;dose[i,j]=2-actual if j%3==0 else actual
        raw=struct.pack('<IHBB',n,2,2,2)+bytes(ploidies)+bytes([int(phased),8])+probabilities
        if compression==0:out.extend(struct.pack('<I',len(raw))+raw)
        else:
            packed=zlib.compress(raw) if compression==1 else zstandard.ZstdCompressor().compress(raw);out.extend(struct.pack('<II',len(packed)+4,len(raw))+packed)
    path.write_bytes(out)

def generate(root,case):
    p=base.generate(root,'variant-all-supplied-seed' if case=='all-supplied' else case)
    if p.name!=case:p=p.rename(root/case)
    study=load(p/'study.json');policy=study['hypotheses'][0]['analysis']['risk_score'];g=policy['genotypes'];source_rows=base.rows(p/'participants.tsv');o={k:v.copy() for k,v in np.load(p/'oracle.npz').items()};dose=o['dose'];n,m=dose.shape[1:]
    if case=='mixed':
        extra=np.random.default_rng(18571).binomial(2,.3,(1,n,m)).astype(float);extra[:,:,-1]=0;dose=np.concatenate([dose,extra]);o={k:np.concatenate([v,v[1:2].copy()]) if v.ndim==2 and v.shape==(2,n) else v for k,v in o.items()};extra_score=(extra[0]@o['beta']-1)/2;o['y'][2]=1+extra_score+.01*o['age'][2]+.15*np.sin(np.arange(n)*.7+2);o['related'][2]=.6*extra_score+.25*np.cos(np.arange(n)*.9+2)
        source_rows += [dict(r,sample_id=r['sample_id'].replace('private-B-','private-C-'),cohort='C',y=str(o['y'][2,i]),z=str(o['related'][2,i])) for i,r in enumerate(source_rows[n:])]

    if case in {'imputation','bgen-imputation'}:dose[0,0,:3]=np.nan;g.update(missing='reference_mean',frequency_provenance='Independent synthetic EA frequencies',minimum_observed_fraction=.8)
    sources=[]
    for k,name in enumerate('ABC'[:len(dose)]):
        if case=='mixed' and k==0:sources.append({'id':name,'bfile':name,'genome_build':'GRCh38'});continue
        kind='bgen1' if case in {'bgen-external','sidecar-conflict','bgen-imputation'} or case=='mixed' and k==2 else case
        if kind.startswith('bgen'):
            file=f'{name}.bgen';bgen(p/file,dose[k],0 if kind=='bgen0' else 2 if kind=='bgen2' else 1,case!='bgen-external',case=='bgen-phased')
        elif kind.startswith('bcf'):
            dose[k]=np.where(np.isnan(dose[k]),dose[k],.25+.75*dose[k]);file=f'{name}.bcf';bcf(p/file,dose[k],1 if case=='bcf21' else 2,case=='bcf22-bgzf')
        else:
            if case!='vcf-gt':dose[k]=np.where(np.isnan(dose[k]),dose[k],.25+.75*dose[k])
            file=f'{name}.vcf'+('.gz' if case=='vcf-gzip' else '');vcf(p/file,dose[k],case!='vcf-gt',case.startswith('unknown'),case=='haploid',case=='multiallelic',case=='late-duplicate',case=='missing-model')
        source={'id':name,'file':file,'genome_build':'GRCh38'}
        if case=='unknown-declared':source['diploid_dosage_declaration']='Synthetic autosomal diploid DS; called GT intentionally withheld'
        if case in {'bgen-external','sidecar-conflict'}:
            sidecar=f'{name}.sample';(p/sidecar).write_text('ID_1 ID_2 missing\n0 0 0\n'+''.join(f'F genome-I{i} 0\n' for i in reversed(range(n))),encoding='utf-8');source['samples']=sidecar
            if case=='sidecar-conflict':(p/sidecar).write_text((p/sidecar).read_text().replace('genome-I0 ','WRONG-ID '),encoding='utf-8')
        sources.append(source)
        for r in source_rows[k*n:(k+1)*n]:r['iid']='genome-I'+r['sample_id'].split('-')[-1]
    g['sources']=sources
    if case=='shared-cells':g['maximum_cells']=n*m+1
    o['dose']=dose;o['score']=(np.where(np.isnan(dose),2*o['frequency'],dose)@o['beta']-1)/2;o['prediction']=1+o['score']+.01*o['age'];np.savez(p/'oracle.npz',**o)
    supplied=[]
    if case in {'all-supplied','related-only'}:
        origin,_=base.one(root/'vcf-ds','risk_score_evaluation');shutil.copyfile(origin/'risk-score-evaluation.tsv',p/'provided-evaluation.tsv');supplied.append({'id':'provided-evaluation','kind':'risk_score_evaluation','files':{'results':'provided-evaluation.tsv'},'method':{'name':'Completed synthetic VCF score evaluation'},'scope':{'population':'registered_evaluation_complete_cases'}});study['hypotheses'][0]['analysis']['goals']=['risk_score_evaluation','risk_score_related_phenotypes']
        if case=='all-supplied':
            shutil.copyfile(origin/'related-phenotypes.tsv',p/'provided-related.tsv');supplied.append({'id':'provided-related','kind':'risk_score_related_phenotypes','files':{'results':'provided-related.tsv'},'method':{'name':'Completed synthetic related family'},'scope':{'population':'registered_evaluation_complete_cases'},'family':{'id':'related','coverage':'complete','tested_count':1,'adjustment':'BH'}})
            for s in sources:(p/s['file']).unlink()
        else:
            for r in source_rows:r.pop('y')
    study['hypotheses'][0]['analysis']['supplied_results']=supplied;write(p/'study.json',study);base.table(p/'participants.tsv',list(source_rows[0]),[list(r.values()) for r in source_rows]);write(p/'input-sha256.json',{f.name:sha(f) for f in p.iterdir() if f.is_file() and f.name!='input-sha256.json'});return p

def verify(p,case,jar):
    for name,digest in load(p/'input-sha256.json').items():assert sha(p/name)==digest,(case,name)
    base.cli(jar,'verify','--project',p);status,_=base.operations(p);assert status['state']==('waiting_for_input' if case in HOLDS else 'completed'),(case,status)
    folder,manifest=base.one(p,'risk_score_evaluation')
    if case in HOLDS:assert not (folder/'participant-scores.tsv').exists();assert not (folder/'risk-score-evaluation.tsv').exists();return 0
    if case=='all-supplied':assert manifest['details']['analysis_executed_by_jlinalg'] is False;assert all(m['details'].get('analysis_executed_by_jlinalg') is False for _,m in base.operations(p)[1] if m['operation_type'] in {'risk_score_evaluation','risk_score_related_phenotypes'});return 0
    if case=='related-only':assert manifest['details']['analysis_executed_by_jlinalg'] is False;folder,manifest=base.one(p,'risk_score_related_phenotypes');assert not (folder/'risk-score-evaluation.tsv').exists()
    o=np.load(p/'oracle.npz');actual=base.rows(folder/'participant-scores.tsv');assert len(actual)==o['score'].size;checks=0
    for i,r in enumerate(actual):
        assert r['sample_id']==f"private-{'ABC'[i//48]}-{i%48}"
        for col,key in [('score','score'),('prediction','prediction'),('baseline','baseline')]:np.testing.assert_allclose(float(r[col]),o[key].flat[i],rtol=2e-10,atol=2e-12);checks+=1
    report=yaml.safe_load((folder/'genetic-score-application.yaml').read_text(encoding='utf-8'));assert report['model_coverage']=='complete_in_every_registered_source';assert report['decoded_genotype_cells']==o['dose'].size;assert report['assumed_diploid_dosage_cells']==(o['dose'].size if case=='unknown-declared' else 0);assert manifest['details']['model_refitted'] is False
    if case!='related-only':
        metrics={r['metric']:float(r['estimate']) for r in base.rows(folder/'risk-score-evaluation.tsv') if r['subgroup_column']=='all'};y=o['y'].ravel();pred=o['prediction'].ravel();b=o['baseline'].ravel()
        for key,value in {'rmse':np.sqrt(np.mean((y-pred)**2)),'baseline_rmse':np.sqrt(np.mean((y-b)**2)),'incremental_predictive_r2':1-np.sum((y-pred)**2)/np.sum((y-b)**2)}.items():np.testing.assert_allclose(metrics[key],value,rtol=3e-10,atol=2e-12);checks+=1
    z=o['related'].ravel();x=np.column_stack([np.ones(len(z)),o['age'].ravel(),o['score'].ravel()]);coef=np.linalg.lstsq(x,z,rcond=None)[0];res=z-x@coef;se=np.sqrt(np.linalg.inv(x.T@x)[-1,-1]*(res@res)/(len(z)-3));prob=2*t.sf(abs(coef[-1]/se),len(z)-3);r=base.rows(folder/'related-phenotypes.tsv')[0]
    for key,value in [('beta',coef[-1]),('se',se),('p_value',prob),('adjusted_p',prob)]:np.testing.assert_allclose(float(r[key]),value,rtol=3e-8,atol=2e-12);checks+=1
    return checks

def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['run','verify']);parser.add_argument('directory',type=Path);parser.add_argument('--jar',type=Path,required=True);a=parser.parse_args();root=a.directory.resolve();jar=a.jar.resolve()
    if a.action=='run':
        root.mkdir();(root/'software').mkdir();pinned=root/'software'/f'jlinalg-{sha(jar)}.jar';shutil.copyfile(jar,pinned);jar=pinned;shutil.copyfile(Path(__file__),root/'workflow-verifier-source.py');shutil.copyfile(SUPPORT,root/'software'/SUPPORT.name);write(root/'execution-software.json',{'jar_sha256':sha(jar),'jar':str(jar.relative_to(root)),'verifier_sha256':sha(Path(__file__)),'support_sha256':sha(SUPPORT)})
        for case in CASES:
            p=generate(root,case);text=base.cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture');(p/'execution.log').write_text(text,encoding='utf-8');print(case,base.operations(p)[0]['state'],flush=True)
        p=root/'vcf-ds';original,_=base.one(p,'risk_score_evaluation');hashes={f.name:sha(f) for f in original.iterdir() if f.is_file()};base.cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture');assert all(m['reused_from_cache'] for _,m in base.operations(p)[1])
        raw=(p/'A.vcf').read_bytes();(root/'cache-original-A.vcf').write_bytes(raw);lines=raw.decode().splitlines();header=next(line for line in lines if line.startswith('#CHROM')).split('\t');column=header.index('genome-I0');index=next(i for i,line in enumerate(lines) if line.startswith('1\t100\t'));fields=lines[index].split('\t');gt,ds=fields[column].rsplit(':',1);before=float(ds);after=1.5 if before!=1.5 else .5;fields[column]=gt+':'+str(after);lines[index]='\t'.join(fields);mutated=('\n'.join(lines)+'\n').encode();(p/'A.vcf').write_bytes(mutated);(root/'cache-mutated-A.vcf').write_bytes(mutated)
        base.cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture');folder,manifest=base.one(p,'risk_score_evaluation');assert manifest.get('reused_from_cache',False) is False;o=np.load(p/'oracle.npz');expected=o['score'][0,0]-(after-before)*o['beta'][0]/2;observed=float(base.rows(folder/'participant-scores.tsv')[0]['score']);np.testing.assert_allclose(observed,expected,rtol=2e-10,atol=2e-12);assert hashes=={f.name:sha(f) for f in original.iterdir() if f.is_file()}
        (p/'A.vcf').write_bytes(raw);base.cli(jar,'auto',p/'study.json','--offline','--actor-type','external','--actor-id','synthetic-protocol-fixture');assert all(m['reused_from_cache'] for _,m in base.operations(p)[1]);write(root/'cache-replay.json',{'all_operations_reused':True,'genotype_mutation_recomputed':True,'original_files_unchanged':len(hashes),'expected_score':float(expected),'observed_score':observed,'mutation_operation':str(folder.relative_to(root)),'original_source_sha256':sha(root/'cache-original-A.vcf'),'mutated_source_sha256':sha(root/'cache-mutated-A.vcf')})
    pin=load(root/'execution-software.json');assert pin['jar_sha256']==sha(jar) and pin['verifier_sha256']==sha(Path(__file__))==sha(root/'workflow-verifier-source.py') and pin['support_sha256']==sha(SUPPORT)==sha(root/'software'/SUPPORT.name)
    cache=load(root/'cache-replay.json');assert cache['all_operations_reused'] and cache['genotype_mutation_recomputed'];assert cache['original_source_sha256']==sha(root/'cache-original-A.vcf') and cache['mutated_source_sha256']==sha(root/'cache-mutated-A.vcf')
    original=(root/'cache-original-A.vcf').read_text(encoding='utf-8').splitlines();mutated=(root/'cache-mutated-A.vcf').read_text(encoding='utf-8').splitlines();column=next(line for line in original if line.startswith('#CHROM')).split('\t').index('genome-I0');a=float(next(line for line in original if line.startswith('1\t100\t')).split('\t')[column].rsplit(':',1)[1]);b=float(next(line for line in mutated if line.startswith('1\t100\t')).split('\t')[column].rsplit(':',1)[1]);o=np.load(root/'vcf-ds/oracle.npz');expected=o['score'][0,0]-(b-a)*o['beta'][0]/2;actual=float(base.rows(root/cache['mutation_operation']/'participant-scores.tsv')[0]['score']);np.testing.assert_allclose(actual,expected,rtol=2e-10,atol=2e-12)
    checks=sum(verify(root/case,case,jar) for case in CASES);write(root/'verification.json',{'status':'passed','cases':len(CASES),'completed':len(CASES)-len(HOLDS),'deliberate_holds':len(HOLDS),'independent_numeric_checks':checks,'jar_sha256':sha(jar),'independent_encoders':['VCF GT/DS with reversed sample headers','BCF2.1/2.2 packed GT/float DS and BGZF framing','BGEN2 unphased/phased 8-bit probabilities with raw/zlib/zstd framing'],'workflow_network_requests':0,'workflow_LLM_requests':0});print(f'Verified {len(CASES)} cases with {checks} independent numerical checks')
if __name__=='__main__':main()
