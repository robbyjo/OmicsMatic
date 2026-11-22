from pathlib import Path
import hashlib,json,re,urllib.request,zipfile,io,gzip
T=Path('D:/projects/JLinAlg/build/gp-1');B=T/'build/fusion-1/binary-intake-1';B.mkdir()
def fetch(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-FUSION-private-tool-intake'}),timeout=60) as r:return r.read()
def save(n,raw):p=B/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw);return {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
html=fetch('https://www.cog-genomics.org/plink/1.9/').decode();links=re.findall(r'href=[\"\']([^\"\']+)',html);candidates=[x for x in links if 'plink_linux_x86_64_' in x and x.endswith('.zip')];assert candidates;candidates=sorted(set(candidates));url=candidates[-1];url='https:'+url if url.startswith('//') else url;raw=fetch(url);plinkzip=save('plink.zip',raw)
with zipfile.ZipFile(io.BytesIO(raw)) as z:save('plink',z.read('plink'));save('plink-LICENSE',z.read('LICENSE') if 'LICENSE' in z.namelist() else z.read('LICENSE.txt'))
release=json.loads(fetch('https://api.github.com/repos/genetics-statistics/GEMMA/releases/latest'));save('GEMMA-RELEASE.json',(json.dumps(release,indent=2)+'\n').encode());assets=[a for a in release['assets'] if 'linux' in a['name'].lower()];print('GEMMA assets',[(a['name'],a['browser_download_url']) for a in assets]);assert assets
asset=assets[0];raw=fetch(asset['browser_download_url']);save(asset['name'],raw)
if asset['name'].endswith('.gz'):save('gemma',gzip.decompress(raw))
else:save('gemma',raw)
revision='9346c1222bffbb34499fa7a8e23c1b701b55cb05';tree=json.loads((T/'build/fusion-1/upstream-intake-1/TREE.json').read_bytes());meta={r['path']:r for r in tree['tree']}
for name in ['gcta_nr_robust','glist-hg19']:
 raw=fetch('https://raw.githubusercontent.com/gusevlab/fusion_twas/'+revision+'/'+name);assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==meta[name]['sha'];save(name,raw)
result={'PLINK_URL':url,'PLINK_archive':plinkzip,'GEMMA_release':release['tag_name'],'GEMMA_asset':asset['browser_download_url'],'FUSION_revision':revision,'files':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in B.iterdir() if p.is_file()},'public_redistribution_requested':False};save('INTAKE.json',(json.dumps(result,indent=2)+'\n').encode());print(json.dumps({'status':'PRIVATE_ORIGINAL_BINARY_INTAKE','plink_url':url,'gemma_release':release['tag_name']}))
