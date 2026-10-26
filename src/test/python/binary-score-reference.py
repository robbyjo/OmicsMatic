"""Independent SciPy convex-objective references for native binary risk scores.

No JLinAlg calls. Ridge uses analytic-gradient BFGS; lasso/elastic net uses
nonnegative positive/negative coefficient splitting with L-BFGS-B bounds.
All objectives include separately weighted covariate ridge, unpenalized
intercept and population-variance feature/covariate standardization.
"""
from pathlib import Path
import csv,json,hashlib,sys
import numpy as np
import scipy
from scipy import optimize,special

root=Path(__file__).resolve().parents[1]/'resources/binary-score';root.mkdir(exist_ok=True)
rng=np.random.default_rng(10731);n=180
x=rng.normal(size=(n,3));x[:,1]=.4*x[:,0]+.7*x[:,1];x[:,2]=2
cov=np.column_stack([np.ones(n),rng.normal(size=n),np.arange(n)%2])
eta=-.25+.55*cov[:,1]-.3*cov[:,2]+1.1*x[:,0]-.7*x[:,1]
y=(rng.random(n)<special.expit(eta)).astype(int);x[[2,11,34,92],0]=np.nan
with (root/'input.tsv').open('w',newline='',encoding='utf-8') as f:
 w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(['y','x0','x1','x2','intercept','c0','c1','partition'])
 for i in range(n):w.writerow([y[i],*['NA' if np.isnan(v) else repr(float(v)) for v in x[i]],*map(float,cov[i]),'training' if i<80 else 'tuning' if i<130 else 'evaluation'])

def solve(response,design,l1,l2):
 n,p=design.shape
 def objective(b):return np.mean(np.logaddexp(0,design@b)-response*(design@b))+np.dot(l1,np.abs(b))+.5*np.dot(l2,b*b)
 def smooth_gradient(b):return design.T@(special.expit(design@b)-response)/n+l2*b
 initial=np.zeros(p);initial[0]=special.logit(response.mean())
 if np.all(l1==0):r=optimize.minimize(objective,initial,jac=smooth_gradient,method='BFGS',options={'gtol':1e-11,'maxiter':20000});b=r.x
 else:
  def unpack(z):return z[:p]-z[p:]
  def fun(z):
   b=unpack(z);return np.mean(np.logaddexp(0,design@b)-response*(design@b))+.5*np.dot(l2,b*b)+np.dot(l1,z[:p]+z[p:])
  def jac(z):g=smooth_gradient(unpack(z));return np.r_[g+l1,-g+l1]
  z=np.r_[np.maximum(initial,0),np.maximum(-initial,0)]
  r=optimize.minimize(fun,z,jac=jac,bounds=[(0,None)]*(2*p),method='L-BFGS-B',options={'gtol':1e-12,'ftol':1e-15,'maxiter':20000,'maxls':100});b=unpack(r.x)
 g=smooth_gradient(b);kkt=np.max(np.where(abs(b)<1e-7,np.maximum(0,abs(g)-l1),abs(g+np.sign(b)*l1)))
 assert kkt<3e-7,(r.message,kkt)
 return b,float(objective(b)),float(kkt)

def fit(rows,lam,alpha):
 features=x[rows];response=y[rows];c=cov[rows];means=np.nanmean(features,axis=0);filled=np.where(np.isnan(features),means,features);scales=np.sqrt(np.mean((filled-means)**2,axis=0));scales[scales==0]=1
 cm=c.mean(axis=0);cm[0]=0;cs=c.std(axis=0);cs[0]=1;cs[cs==0]=1
 cstd=(c-cm)/cs;design=np.c_[cstd,(filled-means)/scales];q=c.shape[1]
 l1=np.r_[np.zeros(q),np.full(features.shape[1],lam*alpha)];l2=np.r_[0,np.full(q-1,.3),np.full(features.shape[1],lam*(1-alpha))]
 b,obj,kkt=solve(response,design,l1,l2);base,bobj,bkkt=solve(response,cstd,np.zeros(q),l2[:q])
 cw=b[:q]/cs;cw[0]-=np.dot(cm[1:],cw[1:]);bw=base/cs;bw[0]-=np.dot(cm[1:],bw[1:]);weights=b[q:]/scales;intercept=-np.dot(means,weights)
 return {'weights':weights.tolist(),'means':means.tolist(),'scales':scales.tolist(),'score_intercept':float(intercept),'covariate_weights':cw.tolist(),'baseline_weights':bw.tolist(),'objective':obj,'baseline_objective':bobj,'kkt':kkt,'baseline_kkt':bkkt}
cases=[]
for alpha in [0,.5,1]:
 for lam in [.2,.02]:cases.append({'alpha':alpha,'lambda':lam,**fit(np.arange(80),lam,alpha)})
(root/'reference.json').write_text(json.dumps({'baseline_lambda':.3,'training_rows':80,'cases':cases},indent=2)+'\n',encoding='utf-8')
(root/'PROVENANCE.json').write_text(json.dumps({'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'python':sys.version,'numpy':np.__version__,'scipy':scipy.__version__,'methods':['BFGS analytic gradient','L-BFGS-B nonnegative coefficient splitting'],'seed':10731,'scope':'Independent convex point/objective/KKT references; not clinical or population calibration'},indent=2)+'\n',encoding='utf-8')
print('Generated six independent binomial elastic-net reference fits')
