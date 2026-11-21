from pathlib import Path
import os
os.environ['OPENBLAS_NUM_THREADS']='1'
import hashlib,json,math,re,struct,zipfile
import numpy as np
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';F=B/'synthetic-inputs-1';O=B/'native-reference-corpus-1';O.mkdir(exist_ok=False)
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def rows(p):return [line.split() for line in p.read_text().splitlines() if line.strip()]
ma=[rows(F/(name+'.ma'))[1:] for name in ['Y','X1','X2']];ids=[r[0] for r in ma[0]];n=len(ids);assert n==10000 and all([r[0] for r in table]==ids for table in ma)
effects=np.column_stack([[float(r[4]) for r in table] for table in ma]);ses=np.column_stack([[float(r[5]) for r in table] for table in ma])
vp=[]
for table in ma:
    values=[]
    for r in table:
        f,b,se,N=[float(r[i]) for i in [3,4,5,7]];h=2*f*(1-f);values.append(h*b*b+h*N*se*se)
    values.sort();vp.append((values[n//2-1]+values[n//2])/2)
def radius(value):return abs(value)*5.00001e-6+16*math.ulp(value)
def matrix(lines,label,count):
    i=lines.index(label)+1;return np.array([[float(x) for x in row.split()] for row in lines[i:i+count]])
sourcepins={};members={};cases=[];eps=np.finfo(float).eps
inputs=bytearray(b'JMTINPUT1'+struct.pack('<ii',n,2))
for i in range(n):inputs+=struct.pack('<6d',effects[i,0],ses[i,0],effects[i,1],ses[i,1],effects[i,2],ses[i,2])
members['inputs.bin']=bytes(inputs)
for host in ['windows','linux']:
    for model in ['single-supplied','multi-supplied','single-GSMR','multi-GSMR','single-supplied-zero']:
        root=B/('original-behavior-controls-1' if model.endswith('-zero') else 'original-1.94.1-1')/host/model
        table=root/'result.mtcojo.cma';log=root/'result.log';sourcepins[str(table)]=pin(table);sourcepins[str(log)]=pin(log)
        data=rows(table);assert len(data)==n+1 and [r[0] for r in data[1:]]==ids
        q=2 if model.startswith('multi') else 1;lines=log.read_text().splitlines();I=matrix(lines,'Intercept:',q+1);G=matrix(lines,'rg:',q+1)
        if model.endswith('-zero'):causal=np.array([0.]);causal_radius=np.zeros(q)
        elif model.endswith('-supplied'):causal=np.array([.35,-.2][:q]);causal_radius=np.zeros(q)
        else:
            found=re.findall(r'^bxy ([^ ]+) ([^ \r\n]+)',log.read_text(),re.M);assert len(found)==q;causal=np.array([float(x[0]) for x in found]);causal_radius=np.full(q,.5000001e-6)
        genetic=np.diag(G)[1:]*np.array(vp[1:q+1]);correlation=np.eye(q)
        for i in range(q):
            for j in range(i):correlation[i,j]=correlation[j,i]=G[i+1,j+1]
        if q==1:joint=causal.copy();joint_radius=float(causal_radius[0])
        else:
            d=np.sqrt(genetic);inverse=np.linalg.inv(correlation);joint=(inverse@(d*causal))/d
            gr=np.array([radius(G[i+1,i+1])*vp[i+1]+abs(G[i+1,i+1])*16*math.ulp(vp[i+1]) for i in range(q)])
            deltaD=max(max(d[i]-math.sqrt(genetic[i]-gr[i]),math.sqrt(genetic[i]+gr[i])-d[i])+16*math.ulp(d[i]) for i in range(q));dnorm=float(d.max());dmin=float(d.min());dinv=1/dmin;deltaDinv=deltaD/(dmin*(dmin-deltaD))
            radR=np.array([[0 if i==j else radius(correlation[i,j]) for j in range(q)] for i in range(q)]);deltaR=float(np.linalg.norm(radR));rinv=float(np.linalg.norm(inverse,2));assert rinv*deltaR<1
            deltaRinv=rinv*rinv*deltaR/(1-rinv*deltaR)
            deltaA=deltaDinv*rinv*dnorm+(dinv+deltaDinv)*deltaRinv*dnorm+(dinv+deltaDinv)*(rinv+deltaRinv)*deltaD
            joint_radius=deltaA*np.linalg.norm(causal)+(dinv+deltaDinv)*(rinv+deltaRinv)*(dnorm+deltaD)*np.linalg.norm(causal_radius)
        joint_radius=float(joint_radius)+4096*eps*(1+float(np.linalg.norm(joint)))
        C=np.eye(q);CR=np.zeros((q,q))
        for i in range(q):
            for j in range(i):C[i,j]=C[j,i]=I[i+1,j+1];CR[i,j]=CR[j,i]=radius(C[i,j])
        intercept=I[0,1:];ir=np.array([radius(x) for x in intercept]);cNorm=float(np.linalg.norm(C,2));crNorm=float(np.linalg.norm(CR));vNorm=float(np.linalg.norm(intercept));drNorm=float(np.linalg.norm(ir));raw=bytearray(b'JMTREF01'+struct.pack('<i',n));maximum_relative_variance_radius=0.
        for i in range(n):
            x=effects[i,1:q+1];sx=ses[i,1:q+1];sy=ses[i,0];u=joint*sx;unorm=float(np.linalg.norm(u));du=float(np.max(sx))*joint_radius
            effect=float(effects[i,0]-x@joint);vx=float(u@C@u);cross=float(sy*u@intercept);variance=sy*sy+vx-2*cross;assert variance>0
            beta_pad=4096*eps*(abs(effects[i,0])+float(np.abs(x)@np.abs(joint))+1e-300);deltaB=float(np.linalg.norm(x))*joint_radius+beta_pad
            deltaV=cNorm*(2*unorm*du+du*du)+crNorm*(unorm+du)**2+2*abs(sy)*(du*vNorm+(unorm+du)*drNorm)+4096*eps*(sy*sy+abs(vx)+2*abs(cross));assert variance>deltaV
            maximum_relative_variance_radius=max(maximum_relative_variance_radius,deltaV/variance)
            error=math.sqrt(variance);probability=math.erfc(abs(effect)/error/math.sqrt(2));reference=[float(v) for v in data[i+1][8:11]]
            betaLow,betaHigh=effect-deltaB,effect+deltaB;seLow,seHigh=math.sqrt(variance-deltaV),math.sqrt(variance+deltaV)
            minAbs=0 if betaLow<=0<=betaHigh else min(abs(betaLow),abs(betaHigh));maxAbs=max(abs(betaLow),abs(betaHigh))
            pLow=math.erfc(maxAbs/seLow/math.sqrt(2));pHigh=math.erfc(minAbs/seHigh/math.sqrt(2))
            lows=[betaLow-radius(reference[0]),seLow-radius(reference[1]),max(0,pLow-radius(reference[2]))];highs=[betaHigh+radius(reference[0]),seHigh+radius(reference[1]),min(1,pHigh+radius(reference[2]))]
            assert all(lo<=value<=hi for lo,value,hi in zip(lows,reference,highs)),(host,model,i,reference,lows,highs)
            raw+=struct.pack('<13d',effect,error,probability,beta_pad,*reference,*lows,*highs)
        key=host+'-'+model;members['cases/'+key+'.bin']=bytes(raw);members['original/'+key+'.mtcojo.cma']=table.read_bytes();members['original/'+key+'.log']=log.read_bytes()
        cases.append(dict(name=key,covariates=q,causal_effects=causal.tolist(),genetic_variances=genetic.tolist(),genetic_correlations=correlation.tolist(),sampling_intercepts=I.tolist(),independent_joint_causal_effects=joint.tolist(),joint_causal_effect_rounding_radius=joint_radius,maximum_relative_variance_rounding_radius=maximum_relative_variance_radius,reference='cases/'+key+'.bin'))
        print('Native oracle bounded',key,n,'rows;gamma radius',format(joint_radius,'.6g'),flush=True)
metadata=dict(schema_version=1,source_tag='v1.94.1',source_commit='6da64e9d8be838d01c34efb974e69c72e861f4c2',variants=n,cases=cases,input_format='JMTINPUT1 then little-endian int32 n,qmax; per row y,seY,bX1,seX1,bX2,seX2 float64',reference_format='JMTREF01 then int32 n; per row point beta,se,p; point-beta floating tolerance; original beta,se,p; lower beta,se,p; upper beta,se,p (13float64)',original_fields=3*n*len(cases),participant_data=False,phenotypic_variance_formula='Median of candidate-row 2*f*(1-f)*b*b+2*f*(1-f)*N*se*se with source operation order',source_derived_Vp=vp,uncertainty_scope='Analytic norm perturbation bounds over available source six-significant/fixed-six-decimal parameter exports plus source table rounding and floating padding; not confidence intervals, added causal-estimation uncertainty, or recovery of unexported internal state',point_oracle='Independent NumPy linear solve, scalar variance formula and Python math.erfc',originals=sourcepins)
members['CORPUS.json']=(json.dumps(metadata,indent=2)+'\n').encode();dest=T/'src/test/resources/org/jlinalg/genetics/mtcojo1941-conditional-originals.zip';dest.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(dest,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for name,raw in sorted(members.items()):
        entry=zipfile.ZipInfo(name,(2026,11,20,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
(O/'CORPUS.json').write_text(json.dumps(dict(resource=pin(dest),original_fields=metadata['original_fields'],cases=len(cases),source_derived_Vp=vp,originals=sourcepins),indent=2)+'\n',encoding='utf-8');print('Saved native source corpus',pin(dest),flush=True)
