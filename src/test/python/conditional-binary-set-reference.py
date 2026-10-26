"""Independent exhaustive individual-label reference for conditional set statistics.

Uses direct combinations and Bernoulli odds, not genotype-pattern compression.
Internal mid-tail ranks define adaptive statistics; their full conditional
upper tails, never the raw mid-P/Cauchy values, are the primary test P values.
"""
import hashlib,itertools,json,math,pathlib,struct
import numpy as np
import scipy
from scipy.optimize import brentq
from scipy.special import expit,logsumexp

ROOT=pathlib.Path(__file__).resolve().parents[1]/'resources'/'conditional-binary-set'
RHO=[0,.01,.04,.09,.16,.25,.5,1]
TESTS=['burden','skat','skat-o','acat-v','acat-o']
def canonical(x):
    if x==0:return 0.
    bits=struct.unpack('>Q',struct.pack('>d',abs(float(x))))[0]
    return math.copysign(struct.unpack('>d',struct.pack('>Q',(bits+2048)&~4095))[0],x)
def cauchy(p,w=None):
    if len(p)==0:return 0.
    w=np.ones(len(p)) if w is None else np.array(w,dtype=float)
    if not w.max()>0:return 0.
    w=w/w.sum();terms=[]
    for q,a in zip(p,w):
        q=max(np.finfo(float).tiny,min(np.nextafter(1.,0.),q))
        terms.append(a*math.tan(math.pi*(.5-q)))
    return canonical(math.fsum(terms))
class Cdf:
    def __init__(self,values,prob,sampled=False):self.values=np.array(values);self.prob=np.array(prob);self.sampled=sampled
    def mid(self,value):
        value=canonical(value);q=self.prob[self.values>value].sum()+self.prob[self.values==value].sum()/2
        if self.sampled:q=(q*len(self.values)+.5)/(len(self.values)+1)
        return max(np.finfo(float).tiny,min(np.nextafter(1.,0.),q))
    def upper(self,value):return self.prob[self.values>=canonical(value)].sum()
def quadratic(u,w):
    v=u*w
    return canonical(math.fsum(v)**2),canonical(math.fsum(v*v))
def evaluate_statistics(u,prob,maf,mac,weights,threshold,evaluate,sampled=False):
    def cdf(values,prob):return Cdf(values,prob,sampled)
    primary=[np.array(weights[k],dtype=float)/max(weights[k]) for k in ['burden','kernel','acat']]
    b25=(1-maf)**24;b25/=b25.max();equal=np.ones(len(maf));high=np.where(mac>threshold)[0];low=np.where(mac<=threshold)[0]
    variant={j:cdf([canonical(z[j]**2) for z in u],prob) for j in high}
    def shape(w,scheme):
        q=np.array([quadratic(z,w) for z in u]);bc=cdf(q[:,0],prob);kc=cdf(q[:,1],prob);pw=w*w*maf*(1-maf)
        cc=None;cw=0
        if len(low):
            cc=cdf([canonical(math.fsum(z[low]*w[low])**2) for z in u],prob);f=maf[low].mean()
            cw=pw[low].mean() if scheme=='custom' else (1 if scheme=='beta_1_1' else (1-f)**24/((1-maf)**24).max())**2*f*(1-f)
        def acat(z):
            ps=[variant[j].mid(canonical(z[j]**2)) for j in high];ww=list(pw[high])
            if len(low):ps.append(cc.mid(canonical(math.fsum(z[low]*w[low])**2)));ww.append(cw)
            return cauchy(ps,ww)
        av=np.array([acat(z) for z in u]);return q,bc,kc,acat,cdf(av,prob)
    a=shape(b25,'beta_1_25');b=shape(equal,'beta_1_1');main=shape(primary[1],'custom');av=shape(primary[2],weights['acat_scheme']);rho=[cdf([canonical((1-r)*q[1]+r*q[0]) for q in main[0]],prob) for r in RHO]
    statistics=[]
    for z in evaluate:
        q=quadratic(z,primary[1]);minimum=min(c.mid(canonical((1-r)*q[1]+r*q[0])) for c,r in zip(rho,RHO));aq=quadratic(z,b25);bq=quadratic(z,equal)
        rank=[a[2].mid(aq[1]),b[2].mid(bq[1]),a[1].mid(aq[0]),b[1].mid(bq[0]),a[4].mid(a[3](z)),b[4].mid(b[3](z))]
        statistics.append([quadratic(z,primary[0])[0],q[1],canonical(-minimum),av[3](z),cauchy(rank)])
    return np.array(statistics)

def reference(y,g,strata,offset,weights,threshold):
    groups=[];mean=np.zeros(len(y))
    for level in sorted(set(strata)):
        idx=np.where(strata==level)[0];k=int(y[idx].sum());groups.append([tuple(idx[list(c)]) for c in itertools.combinations(range(len(idx)),k)])
        alpha=brentq(lambda a:expit(a+offset[idx]).sum()-k,-1000,1000,xtol=1e-14)
        mean[idx]=expit(alpha+offset[idx])
    labels=[]
    for selected in itertools.product(*groups):
        z=np.zeros(len(y))
        for group in selected:z[list(group)]=1
        labels.append(z)
    labels=np.array(labels);log=labels@offset;prob=np.exp(log-logsumexp(log));u=(labels-mean)@g
    maf=g.mean(axis=0)/2;mac=g.sum(axis=0)
    statistics=evaluate_statistics(u,prob,maf,mac,weights,threshold,u)
    statistics=np.array(statistics);pvalues=np.array([[prob[statistics[:,t]>=row[t]].sum() for t in range(5)] for row in statistics]);observed=np.where(np.all(labels==y,axis=1))[0].item()
    for alpha in [.001,.01,.025,.05,.1,.25,.5,.9]:assert np.all((prob[:,None]*(pvalues<=alpha+1e-14)).sum(axis=0)<=alpha+1e-12)
    return {'null_means':mean.tolist(),'states':len(labels),'observed_statistics':statistics[observed].tolist(),'observed_p':pvalues[observed].tolist(),'all_labels':labels.astype(int).tolist(),'all_statistics':statistics.tolist(),'all_p':pvalues.tolist(),'probabilities':prob.tolist(),'type_one_error_checked':True}
def main():
    ROOT.mkdir(parents=True,exist_ok=True)
    g=np.array([[1,0,1,0],[0,1,0,1],[0,0,1,0],[0,0,1,0],[0,0,0,1],[0,0,0,0],[0,0,1,0],[0,0,0,1],[0,0,0,0],[0,0,0,1],[0,0,0,0],[0,0,0,0]],dtype=float)
    y=np.array([1,0,1,0,0,0,0,1,0,0,1,0],dtype=float);strata=np.array([0]*6+[1]*6);offset=np.array([-.7,.2,1,-.3,.7,0,.1,-.5,.8,0,.4,-.2]);maf=g.mean(axis=0)/2;b25=((1-maf)**24).tolist()
    standard={'burden':[1]*4,'kernel':b25,'acat':b25,'acat_scheme':'beta_1_25'};cases=[]
    for name,off,st,weights,threshold in [('central',np.zeros(12),strata,standard,2),('known-offset',offset,strata,standard,2),('no-collapse',offset,strata,standard,0),('native-default',np.zeros(12),strata,{'burden':[1]*4,'kernel':[25*(1-max(2/12,f))**24 for f in maf],'acat':b25,'acat_scheme':'beta_1_25'},2),('custom',offset,strata,{'burden':[.1,2,1,.7],'kernel':[1,1,.4,3],'acat':[1,2,3,4],'acat_scheme':'custom'},2)]:
        expected=reference(y,g,st,off,weights,threshold);cases.append({'name':name,'y':y.tolist(),'genotype':g.tolist(),'strata':st.tolist(),'offset':off.tolist(),'maf':maf.tolist(),'mac':g.sum(axis=0).tolist(),'weights':weights,'threshold':threshold,'expected':expected})
    data={'tests':TESTS,'rho':RHO,'cases':cases,'scope':'Independent synthetic finite-null definition/type-I control; no SKAT-package or Gaussian asymptotic parity claim'}
    (ROOT/'reference.json').write_text(json.dumps(data,indent=2,allow_nan=False)+'\n',encoding='utf-8');provenance={'numpy':np.__version__,'scipy':scipy.__version__,'generator_sha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),'reference_sha256':hashlib.sha256((ROOT/'reference.json').read_bytes()).hexdigest(),'enumeration':'Individual Bernoulli label combinations weighted by known offsets, conditioned on each stratum case total; no genotype compression used'};(ROOT/'PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8');print(json.dumps({'cases':len(cases),'individual_null_states':[c['expected']['states'] for c in cases],'all_five_tests_calibrated':True}))
if __name__=='__main__':main()
