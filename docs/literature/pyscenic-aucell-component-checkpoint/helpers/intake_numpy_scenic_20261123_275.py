from pathlib import Path
import hashlib,json,urllib.request
T=Path('D:/projects/JLinAlg/build/fu-1');D=T/'build/scenic-1/numpy-source-intake-1';D.mkdir()
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-numpy-source-intake'}),timeout=50) as f:return f.read()
meta_raw=get('https://api.github.com/repos/numpy/numpy/commits/v1.23.5');commit=json.loads(meta_raw)['sha'];(D/'COMMIT.json').write_bytes(meta_raw);files={}
for name in ('LICENSE.txt','numpy/random/mtrand.pyx','numpy/random/src/mt19937/mt19937.c','numpy/random/src/mt19937/mt19937.h','numpy/random/src/legacy/legacy-distributions.c','numpy/random/src/distributions/distributions.c'):
 raw=get('https://raw.githubusercontent.com/numpy/numpy/'+commit+'/'+name);target=D/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);files[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
(D/'SOURCE.json').write_text(json.dumps(dict(repo='numpy/numpy',tag='v1.23.5',commit=commit,files=files,scientific_source_modified=False),indent=2)+'\n')
print(json.dumps(dict(numpy_commit=commit,files=len(files))))
