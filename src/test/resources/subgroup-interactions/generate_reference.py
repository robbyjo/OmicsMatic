"""Independent synthetic interaction references; not biological evidence.

Run with Python/numpy/scipy/statsmodels. Uses unscaled raw model columns and
general optimizers, independently of JLinAlg's centered design and solvers.
"""
from pathlib import Path
import csv, json
import numpy as np
import scipy
from scipy.special import expit
from scipy.stats import t, norm, chi2
from scipy.optimize import minimize
import statsmodels.api as sm
import statsmodels

root=Path(__file__).resolve().parent
n=96;i=np.arange(n);g=(i%2).astype(float);c=35+(i%19)*.7+np.sin(i*.31)
x=np.sin(i*1.37)+.3*np.cos(i*.17)+.04*(c-40)+.2*g
offset=.12*np.sin(i*.21)
design=np.column_stack([np.ones(n),g,c,c*g,x,c*x,x*g])
continuous=design@np.array([2.,.7,.12,-.05,-1.1,.04,1.2])+offset+(np.sin(i*2.1)+.3*np.cos(i*.71))*(.15+.5*g)
probability=expit(design@np.array([-.8,.3,.01,.004,-1.3,.025,1.15])+offset)
binary=np.random.default_rng(20261024).binomial(1,probability).astype(float)

def table(name,rows):
    with (root/name).open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(rows[0]),delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(rows)

table('data.tsv',[dict(id=f'S{k}',group=int(g[k]),age=c[k],feature=x[k],offset=offset[k],continuous=continuous[k],binary=int(binary[k])) for k in range(n)])
expected=[]
def row(method,beta,cov,p,stat,df,crit):
    reference=np.array([0,0,0,0,1,c.mean(),0.]);comparison=reference+np.eye(7)[6]
    se=np.sqrt(cov[6,6]);b=beta[6]
    expected.append(dict(method=method,beta=b,se=se,ci_lower=b-crit*se,ci_upper=b+crit*se,p_value=p,statistic=stat,df=df,reference_beta=reference@beta,reference_se=np.sqrt(reference@cov@reference),comparison_beta=comparison@beta,comparison_se=np.sqrt(comparison@cov@comparison)))

lm=sm.OLS(continuous-offset,design).fit()
for method,fit in [('ols-classical',lm),('ols-hc3',lm.get_robustcov_results(cov_type='HC3',use_t=True))]:
    stat=fit.params[6]/np.sqrt(fit.cov_params()[6,6]);row(method,fit.params,fit.cov_params(),2*t.sf(abs(stat),lm.df_resid),stat,lm.df_resid,t.ppf(.975,lm.df_resid))
glm=sm.GLM(binary,design,family=sm.families.Binomial(),offset=offset).fit(maxiter=1000,tol=1e-12)
reduced=sm.GLM(binary,design[:,:-1],family=sm.families.Binomial(),offset=offset).fit(maxiter=1000,tol=1e-12)
lr=2*(glm.llf-reduced.llf);row('logit-lrt',glm.params,glm.cov_params(),chi2.sf(lr,1),lr,1,norm.ppf(.975))

def objective(beta):
    eta=offset+design@beta;p=expit(eta);w=p*(1-p);information=design.T@(w[:,None]*design)
    sign,det=np.linalg.slogdet(information)
    if sign<=0:return 1e100,np.zeros(7)
    inverse=np.linalg.inv(information);h=w*np.sum((design@inverse)*design,axis=1)
    ll=np.sum(binary*eta-np.logaddexp(0,eta))+.5*det
    score=design.T@(binary-p+h*(.5-p))
    return -ll,-score

full=minimize(objective,np.zeros(7),jac=True,method='BFGS',options={'gtol':1e-8,'maxiter':10000})
def constrained(free):
    beta=np.r_[free,0.];value,gradient=objective(beta);return value,gradient[:-1]
null=minimize(constrained,full.x[:-1],jac=True,method='BFGS',options={'gtol':1e-8,'maxiter':10000})
assert np.max(np.abs(objective(full.x)[1]))<1e-5 and np.max(np.abs(constrained(null.x)[1]))<1e-5
p=expit(offset+design@full.x);cov=np.linalg.inv(design.T@((p*(1-p))[:,None]*design))
lr=2*(null.fun-full.fun);row('firth-lrt',full.x,cov,chi2.sf(lr,1),lr,1,norm.ppf(.975))
table('expected.tsv',expected)
(root/'software.json').write_text(json.dumps({'numpy':np.__version__,'scipy':scipy.__version__,'statsmodels':statsmodels.__version__,'full_firth_max_abs_score':float(np.max(np.abs(objective(full.x)[1]))),'constrained_firth_max_abs_score':float(np.max(np.abs(constrained(null.x)[1]))),'all_two_way_nuisance_terms':True,'reference_slopes':'At the observed complete-case mean of age; no three-way term'},indent=2)+'\n',encoding='utf-8')
print(json.dumps({'rows':n,'cases_reference':int(binary[g==0].sum()),'cases_comparison':int(binary[g==1].sum()),'models':len(expected)}))
