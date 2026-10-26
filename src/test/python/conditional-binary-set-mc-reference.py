"""Independent fixed-pilot statistics, main-tail counts and CP intervals.

The frozen raw score draws come from the Java sampler, whose probabilities are
checked separately against exhaustive independent individual-label references.
No native statistic, P value or interval is used as an oracle.
"""
import hashlib,importlib.util,json,pathlib
import numpy as np
from scipy.stats import beta

HERE=pathlib.Path(__file__).resolve().parent
ROOT=HERE.parent/'resources'/'conditional-binary-set'
spec=importlib.util.spec_from_file_location('reference',HERE/'conditional-binary-set-reference.py')
ref=importlib.util.module_from_spec(spec);spec.loader.exec_module(ref)
raw=ROOT/'mc-draws.json';draws=json.loads(raw.read_text(encoding='utf-8'))
case=next(c for c in json.loads((ROOT/'reference.json').read_text(encoding='utf-8'))['cases'] if c['name']==draws['case'])
pilot=np.array(draws['pilot']);main=np.array(draws['main']);observed=np.array(draws['observed']);n=len(main)
stats=ref.evaluate_statistics(pilot,np.full(len(pilot),1/len(pilot)),np.array(case['maf']),np.array(case['mac']),case['weights'],case['threshold'],np.vstack([observed,main]),True)
tests=[]
for i,name in enumerate(ref.TESTS):
    h=int((stats[1:,i]>=stats[0,i]).sum());p=(h+1)/(n+1)
    tests.append(dict(test=name,statistic=float(stats[0,i]),hits=h,p=p,lower95=0 if h==0 else float(beta.ppf(.025,h,n-h+1)),upper95=1 if h==n else float(beta.ppf(.975,h+1,n-h))))
out=dict(seed=draws['seed'],pilot_draws=len(pilot),draws=n,tests=tests,raw_draws_sha256=hashlib.sha256(raw.read_bytes()).hexdigest(),generator_sha256=hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),definition_sha256=hashlib.sha256((HERE/'conditional-binary-set-reference.py').read_bytes()).hexdigest(),scope=__doc__.strip())
(ROOT/'mc-reference.json').write_text(json.dumps(out,indent=2,allow_nan=False)+'\n',encoding='utf-8')
print(json.dumps(dict(tests=len(tests),hits=[t['hits'] for t in tests])))
