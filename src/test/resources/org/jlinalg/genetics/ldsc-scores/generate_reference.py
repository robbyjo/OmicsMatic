"""Execute unchanged original BED filtering/normalization/chunked LD-score code."""
import argparse,hashlib,importlib,json,math,sys,types,traceback,warnings,zipfile
from pathlib import Path
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def plain(x):
 import numpy as np
 if isinstance(x,np.ndarray):return plain(x.tolist())
 if isinstance(x,np.generic):return plain(x.item())
 if isinstance(x,(list,tuple)):return [plain(v) for v in x]
 if isinstance(x,dict):return {k:plain(v) for k,v in x.items()}
 return x
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
 import numpy as np,pandas,scipy,bitarray
 package=types.ModuleType('ldscore');package.__path__=[str(a.source/'ldscore')];sys.modules['ldscore']=package;ld=importlib.import_module('ldscore.ldscore');ps=importlib.import_module('ldscore.parse')
 guards={n:pin(a.source/n) for n in ['ldscore/ldscore.py','ldscore/parse.py','ldsc.py','ldscore/__init__.py','setup.py','LICENSE']};rng=np.random.RandomState(19870726);cases=[];errors=[];payload={};fields=0;config=['id\tinput_variants\tsamples\tannotations\tannotation_mode\twindow\tchunk\tmaf\tsample_ordinals\tvariant_ids']
 for index in range(37):
  try:
   ident='L%03d'%index;large=index==36;m=4099 if large else 27+index%13*4;n=512 if large else 20+index%7*3;chunk=50 if large else [1,2,3,7,50,200][index%6];mode=3 if large else index//6
   coords=np.cumsum(rng.randint(0,5,m)).astype(float)/8
   if mode==0:window=0.
   elif mode==1:coords=np.arange(m,dtype=float);window=5.
   elif mode==2:window=float(coords[min(8,m-1)]-coords[0])
   elif mode==3:coords=np.cumsum(np.where(np.arange(m)%11==0,100.,1.));window=10.
   elif mode==4:window=float(coords[-1]-coords[0])+1
   else:coords=(np.arange(m)//3).astype(float);window=1.
   maf=0. if large else (index//6%3)*.05;amode=1 if large else (index//2+index//6)%3;k=3 if large else 1 if amode==0 else 2+index%4
   raw=rng.binomial(2,rng.uniform(.12,.88,m),size=(n,m)).T.astype(float);raw[rng.uniform(size=raw.shape)<.04]=9
   raw[0]=0;raw[1]=2;raw[2]=1;raw[3]=9;raw[4]=1;raw[4,::3]=9;raw[5]=2;raw[5,0]=0;raw[6]=0;raw[6,0]=2
   keep_samples=None if large or index%4!=1 else list(range(n-1,-1,-2));selected_samples=list(range(n)) if keep_samples is None else keep_samples
   keep_snps=None if large or index%5!=1 else [j for j in range(m) if j%3!=1];selected_variants=list(range(m)) if keep_snps is None else keep_snps
   ann=None if amode==0 else (rng.uniform(size=(m,k))<rng.uniform(.15,.9,k)).astype(float) if amode==1 else rng.normal(.5,.7,(m,k))
   if ann is not None and amode==1:ann[:,0]=1;ann[:,-1]=0 if index%7==0 else ann[:,-1]
   folder=a.out/ident;folder.mkdir();prefix=folder/'reference';bed=bytearray(b'\x6c\x1b\x01');stride=(n+3)//4
   for row in raw:
    packed=bytearray(stride)
    for sample,value in enumerate(row):packed[sample//4]|={0.:0,1.:2,2.:3,9.:1}[float(value)]<<(2*(sample%4))
    bed.extend(packed)
   prefix.with_suffix('.bed').write_bytes(bed);prefix.with_suffix('.bim').write_text(''.join('1 rs%d %.17g %d A G\n'%(j,coords[j],100000+j*10) for j in range(m)),encoding='utf8');prefix.with_suffix('.fam').write_text(''.join('f i%d 0 0 0 -9\n'%j for j in range(n)),encoding='utf8')
   snps=ps.PlinkBIMFile(str(prefix)+'.bim');source=ld.PlinkBEDFile(str(prefix)+'.bed',n,snps,keep_snps=keep_snps,keep_indivs=keep_samples,mafMin=maf)
   kept=list(map(int,source.kept_snps));selected_index={j:i for i,j in enumerate(selected_variants)};actual_coords=np.array(snps.df.CM)[kept];lefts=ld.getBlockLefts(actual_coords,window);annotation=None if ann is None else ann[kept];chunks=[];normalization=[]
   def observe(frame,event,arg):
    if Path(frame.f_code.co_filename).name=='ldscore.py' and event=='line':
     local=frame.f_locals
     if frame.f_code.co_name=='__corSumVarBlocks__' and frame.f_lineno==187:chunks.append(dict(start=0,end=int(local['b']),left=0))
     if frame.f_code.co_name=='__corSumVarBlocks__' and frame.f_lineno==225:chunks.append(dict(start=int(local['l_B']),end=int(local['l_B']+local['c']),left=int(local['l_A'])))
     if frame.f_code.co_name=='nextSNPs' and frame.f_lineno==410:normalization.append(dict(index=int(local['self']._currentSNP+local['j']),mean=float(local['avg']),sd=float(local['denom'])))
    return observe
   sys.settrace(observe)
   try:scores=source.ldScoreVarBlocks(lefts,chunk,annot=annotation)
   finally:sys.settrace(None)
   assert len(normalization)==len(kept) and len(chunks)>0
   annotation=np.ones((len(kept),1)) if ann is None else ann[kept];common=source.maf>.05;binary=amode!=2
   expected=dict(retainedInputIndices=[selected_index[j] for j in kept],nominalWindowLefts=lefts.astype(int),ldScores=scores,annotationCounts=annotation.sum(axis=0),commonAnnotationCounts=annotation[common].sum(axis=0),commonVariants=int(common.sum()),chunks=chunks)
   if binary:expected.update(annotationOverlaps=annotation.T@annotation,commonAnnotationOverlaps=annotation[common].T@annotation[common])
   normal_by_global={kept[v['index']]:v for v in normalization}
   for local,global_index in enumerate(selected_variants):
    calls=raw[global_index,selected_samples];called=calls!=9;freq=None if not called.any() else float(calls[called].sum()/(2*called.sum()));af=None if freq is None else min(freq,1-freq);observed=dict(called=int(called.sum()),missing=int((~called).sum()),allele2Frequency=freq,maf=af,imputedMean=None if freq is None else float(calls[called].mean()),retained=global_index in kept,genotypeSha256=hashlib.sha256(bytes(3 if v==9 else int(v) for v in calls)).hexdigest())
    if global_index in kept:
     j=kept.index(global_index);assert freq==source.freq[j];observed['populationStandardDeviation']=normal_by_global[global_index]['sd'];assert observed['imputedMean']==normal_by_global[global_index]['mean']
    else:observed['populationStandardDeviation']=None
    expected.setdefault('variantAudit',[]).append(observed)
   input_coords=np.array(snps.df.CM)[selected_variants];input_ann=np.ones((len(selected_variants),1)) if ann is None else ann[selected_variants];rows=['coordinate\t'+'\t'.join('A%d'%j for j in range(k))]+['\t'.join(map(str,[input_coords[i],*input_ann[i]])) for i in range(len(selected_variants))];payload[ident+'/annotations.tsv']='\n'.join(rows)+'\n'
   for ext in ['.bed','.bim','.fam']:payload[ident+'/reference'+ext]=prefix.with_suffix(ext).read_bytes()
   config.append('\t'.join([ident,str(len(selected_variants)),str(len(selected_samples)),str(k),'binary' if binary else 'continuous',str(window),str(chunk),str(maf),','.join(map(str,selected_samples)),','.join('rs%d'%j for j in selected_variants)]));lines=['field\tvalue']
   def flatten(key,value):
    nonlocal fields
    value=plain(value)
    if isinstance(value,dict):
     for name,v in value.items():flatten(key+'.'+name,v)
    elif isinstance(value,list):
     for j,v in enumerate(value):flatten(key+'.'+str(j),v)
    else:lines.append(key+'\t'+('NA' if value is None else str(value).lower() if isinstance(value,bool) else str(value)));fields+=1
   for key,value in expected.items():flatten(key,value)
   payload[ident+'/expected.tsv']='\n'.join(lines)+'\n';case=dict(id=ident,large=large,raw_samples=n,raw_variants=m,selected_samples=selected_samples,selected_variants=selected_variants,window=window,chunk=chunk,maf=maf,binary=binary,expected=expected);json.dumps(plain(case),allow_nan=False);cases.append(case)
  except Exception as e:sys.settrace(None);errors.append(dict(index=index,error=repr(e),detail=traceback.format_exc()))
 assert all(pin(a.source/n)==v for n,v in guards.items());result=dict(status='PASS_UNCHANGED_ORIGINAL_LDSCORE_REFERENCE' if not errors else 'FAIL_INCOMPLETE_LDSCORE_REFERENCE',models=len(cases),fields=fields,source_guards=guards,versions=dict(python=sys.version,bitarray=bitarray.__version__,numpy=np.__version__,scipy=scipy.__version__,pandas=pandas.__version__),original_functions_replaced=False,trace_only=True,cases=cases,errors=errors)
 (a.out/'reference.json').write_text(json.dumps(plain(result),indent=2,allow_nan=False)+'\n',encoding='utf8')
 if errors:print(json.dumps(errors,indent=2),flush=True);raise SystemExit(2)
 payload['cases.tsv']='\n'.join(config)+'\n';payload['provenance.json']=json.dumps(dict(source_guards=guards,seed=19870726,models=len(cases),fields=fields,versions=result['versions'],trace_only=True,original_functions_replaced=False),indent=2)+'\n'
 with zipfile.ZipFile(a.out/'reference-fixtures.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,value in payload.items():z.writestr(name,value.encode() if isinstance(value,str) else value)
 print('PASS',len(cases),'original models',fields,'fields',flush=True)
if __name__=='__main__':main()
