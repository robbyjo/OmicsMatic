"""Synthetic mixed-population/duplicate/family hard calls and independent NumPy references."""
from pathlib import Path
import csv,hashlib,json
import numpy as np
root=Path(__file__).resolve().parent;rng=np.random.default_rng(20261024);n=64;m=3072
ids=[f'S{i:02}' for i in range(n)];base=rng.uniform(.1,.45,m);population=(np.arange(n)>=32).astype(float)
frequencies=np.clip(base[:,None]+population[None,:]*rng.uniform(-.08,.30,m)[:,None],.05,.85)
genotypes=rng.binomial(2,frequencies).astype(float);genotypes[:,1]=genotypes[:,0]
genotypes[:,2]=rng.binomial(1,genotypes[:,0]/2)+rng.binomial(1,genotypes[:,3]/2)
genotypes[1]=genotypes[0];genotypes[2]=2-genotypes[0];genotypes[:10,4]=np.nan
markers=[dict(variant_id=f'v{j:04}',chromosome=str(1+j%22),position=1000*(j+1),ref='A',alt='G') for j in range(m)]
markers[1]['chromosome']=markers[2]['chromosome']='1'
def table(name,data):
    with (root/name).open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(data[0]),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(data)
table('hard-calls.tsv',[{**marker,**{id_value:'NA' if np.isnan(genotypes[j,i]) else int(genotypes[j,i]) for i,id_value in enumerate(ids)}} for j,marker in enumerate(markers)])
priority=lambda marker:hashlib.sha256(f"17\n{marker['chromosome']}\n{marker['position']}\nA\nG\n{marker['variant_id']}".encode()).hexdigest()
eligible=[j for j in range(m) if np.mean(~np.isnan(genotypes[j]))>=.98 and .05<=np.nanmean(genotypes[j])/2<=.95]
sampled=sorted(eligible,key=lambda j:(priority(markers[j]),markers[j]['variant_id']))[:2048]
sampled.sort(key=lambda j:(int(markers[j]['chromosome']),markers[j]['position'],markers[j]['variant_id']))
panel=[];dispositions=[]
for j in sampled:
    partner=None;value=None
    for previous in reversed(panel):
        if markers[j]['chromosome']!=markers[previous]['chromosome'] or markers[j]['position']-markers[previous]['position']>500000:break
        complete=~np.isnan(genotypes[j])&~np.isnan(genotypes[previous]);r2=np.corrcoef(genotypes[j,complete],genotypes[previous,complete])[0,1]**2
        if r2>.2:partner=previous;value=r2;break
    if partner is None:panel.append(j)
    dispositions.append(dict(variant_id=markers[j]['variant_id'],disposition='retained' if partner is None else 'ld_pruned',ld_partner='' if partner is None else markers[partner]['variant_id'],r_squared='' if value is None else value))
table('expected-panel.tsv',dispositions)
g=genotypes[panel];pairs=[];related=set()
for i in range(n):
    for j in range(i+1,n):
        complete=~np.isnan(g[:,i])&~np.isnan(g[:,j]);a=g[complete,i];b=g[complete,j];hi=int(np.sum(a==1));hj=int(np.sum(b==1));hh=int(np.sum((a==1)&(b==1)));opposite=int(np.sum(abs(a-b)==2));identical=int(np.sum(a==b));shared=int(np.sum(complete))
        within=(hh-2*opposite)/(hi+hj);between=.5-(hi+hj-2*hh+4*opposite)/(4*min(hi,hj));flag=between>2**-3.5
        if flag:related.add((i,j))
        pairs.append(dict(sample_a=ids[i],sample_b=ids[j],shared_markers=shared,heterozygotes_a=hi,heterozygotes_b=hj,both_heterozygous=hh,opposite_homozygous=opposite,identical_calls=identical,king_equation_9=within,king_between_equation_11=between,above_registered_cutoff=str(flag).lower()))
table('expected-pairs.tsv',pairs)
call_rate=np.mean(~np.isnan(g),axis=0);anchors=[]
for i in sorted(range(n),key=lambda i:(-call_rate[i],ids[i])):
    if not any(tuple(sorted((i,j))) in related for j in anchors):anchors.append(i)
anchors.sort();mean=np.nanmean(g[:,anchors],axis=1);scale=np.sqrt(mean*(1-mean/2));valid=scale>1e-6
z=(g[valid]-mean[valid,None])/scale[valid,None];z=np.nan_to_num(z,nan=0);basis=z[:,anchors];gram=basis.T@basis/basis.shape[0]
values,vectors=np.linalg.eigh(gram);order=np.argsort(values)[::-1];values=values[order];vectors=vectors[:,order];weights=basis@vectors[:,:2]/(basis.shape[0]*values[:2]);scores=z.T@weights
for k in range(2):
    sign=-1 if weights[np.argmax(abs(weights[:,k])),k]<0 else 1;weights[:,k]*=sign;scores[:,k]*=sign
table('expected-pcs.tsv',[dict(sample_id=ids[i],GPC1=scores[i,0],GPC2=scores[i,1],pca_basis=str(i in anchors).lower()) for i in range(n)])
table('expected-weights.tsv',[dict(variant_id=markers[panel[j]]['variant_id'],basis_dosage_mean=mean[j],basis_scale=scale[j],GPC1=weights[k,0],GPC2=weights[k,1]) for k,j in enumerate(np.flatnonzero(valid))])
table('phenotype.tsv',[dict(id=id_value,age=35+i*.3,y=1.2*genotypes[20,i]+.04*(35+i*.3)+.4*population[i]+np.sin(i*1.3),patient_note='PRIVATE_PARTICIPANT_SENTINEL') for i,id_value in enumerate(ids)])
(root/'software.json').write_text(json.dumps({'numpy':np.__version__,'seed':20261024,'samples':n,'source_markers':m,'hash_sampled':len(sampled),'ld_panel':len(panel),'related_pairs':len(related),'anchors':len(anchors),'eigenvalues':values[:2].tolist(),'fractions':(values[:2]/sum(np.maximum(0,values))).tolist()},indent=2)+'\n',encoding='utf-8')
assert len(panel)>=1000 and len(related)>=3 and len(anchors)>=58
print((root/'software.json').read_text())
