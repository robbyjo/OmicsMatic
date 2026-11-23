from pathlib import Path
import hashlib,json,shutil,urllib.request,urllib.error
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-1';D=T/'src/main/resources/org/jlinalg/omics/pyscenic-ce41b61';D.mkdir(parents=True)
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
sources=[]
for package,repo,ref,files in [('pyscenic','aertslab/pySCENIC','0.12.1',['src/pyscenic/aucell.py','LICENSE.txt']),('ctxcore','aertslab/ctxcore','v0.2.0',['src/ctxcore/recovery.py','src/ctxcore/genesig.py','LICENSE.txt'])]:
 meta=json.loads((B/'source-intake-1'/package/'SOURCE.json').read_bytes());record=dict(package=package,repo=repo,tag=ref,commit=meta['commit'],files={})
 for name in files:
  src=B/'source-intake-1'/package/'original-source'/name;dest=D/package/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest);record['files'][dest.relative_to(D).as_posix()]=pin(dest)
 sources.append(record)
numpy=B/'numpy-source-intake-1';meta=json.loads((numpy/'SOURCE.json').read_bytes());record=dict(package='numpy',repo='numpy/numpy',tag='v1.23.5',commit=meta['commit'],files={})
extra=[]
for name in ('numpy/random/LICENSE.md','numpy/random/src/mt19937/LICENSE.md'):
 url='https://raw.githubusercontent.com/numpy/numpy/'+meta['commit']+'/'+name
 try:
  with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-numpy-license-intake'}),timeout=40) as f:raw=f.read()
 except urllib.error.HTTPError as error:
  if error.code==404:continue
  raise
 dest=numpy/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw);extra.append(name)
for name in [*meta['files'],*extra]:
 src=numpy/name;dest=D/'numpy'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest);record['files'][dest.relative_to(D).as_posix()]=pin(dest)
sources.append(record)
java=['src/main/java/org/jlinalg/enrichment/PyScenicAucell.java','src/main/java/org/jlinalg/enrichment/CisTargetRecovery.java']
state=dict(profile='pyscenic-0.12.1-ctxcore-0.2.0-numpy-1.23.5-aucell-v1',sources=sources,implementation_source={n:pin(T/n) for n in java},
 source_scoring='ctxcore0.2.0 enrichment4cells/aucs; existing immutable CisTargetRecovery.ctxcoreDirectAuc; source seeded column shuffle and pandas1.5.3 rank/layout behavior',
 scope='Native prepared/expression AUCell component only. No coexpression fit, motif pruning, full SCENIC/SCENIC+, source CLI capture/replay or paper reproduction claim.',new_family_credit=0,new_reviews=0,whole_goal_complete=False)
(D/'SOURCE.json').write_text(json.dumps(state,indent=2)+'\n',encoding='utf-8')
(D/'NOTICE.txt').write_text('Native Java profile GPL-3.0-or-later. Translated pySCENIC0.12.1/ctxcore0.2.0 behavior and original GPL notices/sources are retained in this directory. NumPy1.23.5 source/seed/shuffle behavior and original BSD/random licenses are retained under numpy/. Existing deterministic-tie signature-score AUCell remains separate. Original source bytes are unchanged; native API implements a bounded source-specific component, not a whole-tool or biological certification.\n',encoding='utf-8')
print(json.dumps(dict(profile=state['profile'],source_resources=sum(len(r['files']) for r in sources),extra_numpy_notices=extra,new_family_credit=0)))
