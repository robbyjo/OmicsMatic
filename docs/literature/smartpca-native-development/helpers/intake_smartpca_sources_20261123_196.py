from pathlib import Path,PurePosixPath
import hashlib,io,json,tarfile,urllib.request
from handover_inventory_20261122_111 import pin,save
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-1';out=B/'source-intake-1';out.mkdir()
def get(url,limit):
 request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-smartpca-source-intake','Accept':'application/vnd.github+json'})
 with urllib.request.urlopen(request,timeout=60) as response:
  raw=response.read(limit+1);assert len(raw)<=limit
  return raw,dict(url=url,final_url=response.url,status=response.status)
tag_raw,tag_http=get('https://api.github.com/repos/DReichLab/EIG/git/ref/tags/v7.2.1',1048576)
(out/'tag.json').write_bytes(tag_raw);tag=json.loads(tag_raw);obj=tag['object']
assert obj['type']=='commit';commit=obj['sha'];assert commit.startswith('4f7e9f0')
archive,http=get('https://codeload.github.com/DReichLab/EIG/tar.gz/'+commit,100_000_000)
(out/'original-source.tar.gz').write_bytes(archive);source=out/'original-source';source.mkdir();members=[]
with tarfile.open(fileobj=io.BytesIO(archive),mode='r:gz') as tar:
 for item in tar.getmembers():
  parts=PurePosixPath(item.name).parts;assert parts and not PurePosixPath(item.name).is_absolute() and '..' not in parts
  if len(parts)==1:assert item.isdir();continue
  relative=Path(*parts[1:]);path=source/relative
  if item.isdir():path.mkdir(parents=True,exist_ok=True);continue
  assert item.isfile() and item.size<80_000_000,'Unsafe source archive member: '+item.name
  raw=tar.extractfile(item).read();assert len(raw)==item.size
  path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
  members.append(dict(path=relative.as_posix(),mode=oct(item.mode),**pin(path)))
record=dict(status='PINNED_ORIGINAL_EIG_V721_SOURCE_NOT_RUNTIME_QUALIFICATION',tag='v7.2.1',commit=commit,tag_http=tag_http,
 archive_http=http,archive=pin(out/'original-source.tar.gz'),members=members,files=len(members),
 paper_with_exact_reported_package_edition='P041',other_paper_versions_not_inferred=True,runtime_executed=False,new_family_credit=0,new_reviews=0)
save(out/'SOURCE.json',record)
print(json.dumps(dict(commit=commit,files=len(members),archive_bytes=len(archive),license_candidates=[x['path'] for x in members if 'license' in x['path'].lower() or 'copying' in x['path'].lower()]),indent=2))
