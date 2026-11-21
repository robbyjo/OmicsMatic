from pathlib import Path
import hashlib,json,urllib.request
R=Path('D:/projects/JLinAlg');I=R/'build/twosamplemr-clump-intake-20261121-1';I.mkdir()
commit='ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6';files={}
def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-clumping-intake'}),timeout=60) as response:return response.read()
for name in ['DESCRIPTION','LICENSE','LICENSE.md','R/ld_clump.R']:
    url='https://raw.githubusercontent.com/MRCIEU/ieugwasr/'+commit+'/'+name;raw=fetch(url);p=I/'ieugwasr'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw);files[name]=dict(url=url,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
(I/'UPSTREAM.json').write_text(json.dumps(dict(package='ieugwasr',version='1.2.1',commit=commit,files=files,original_namespace_installed_at='D:/projects/JLinAlg/build/twosamplemr-runtime-intake-20261121-1/library/ieugwasr',license='MIT; original notices retained',new_catalog_credit=0),indent=2)+'\n',encoding='utf-8',newline='\n')
print('Pinned exact ieugwasr1.2.1 source and MIT notices',files['R/ld_clump.R'],flush=True)
