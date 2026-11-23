from pathlib import Path
import hashlib,json,urllib.request,zipfile,io
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/scenic-1/source-intake-1';B.mkdir()
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-scenic-source-intake'}),timeout=90) as f:return f.read()
def save(p,x):p.write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def pin(x):return dict(bytes=len(x),sha256=hashlib.sha256(x).hexdigest())
records=[]
for repo,ref,package in [('aertslab/pySCENIC','0.12.1','pyscenic'),('aertslab/ctxcore','v0.2.0','ctxcore')]:
 dest=B/package;dest.mkdir();url='https://api.github.com/repos/'+repo+'/commits/'+ref
 try:meta_raw=get(url)
 except urllib.error.HTTPError as error:
  if error.code!=422:raise
  ref=ref[1:] if ref.startswith('v') else 'v'+ref;url='https://api.github.com/repos/'+repo+'/commits/'+ref;meta_raw=get(url)
 meta=json.loads(meta_raw);commit=meta['sha'];(dest/'COMMIT.json').write_bytes(meta_raw)
 archive_url='https://codeload.github.com/'+repo+'/zip/'+commit;raw=get(archive_url);(dest/'original-source.zip').write_bytes(raw)
 source=dest/'original-source';source.mkdir();files={}
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  for item in z.infolist():
   if item.is_dir():continue
   name='/'.join(item.filename.split('/')[1:]);assert name and '..' not in Path(name).parts and not Path(name).is_absolute();data=z.read(item);path=source/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);files[name]=pin(data)
 pypi_raw=get('https://pypi.org/pypi/'+package+('/0.12.1' if package=='pyscenic' else '/0.2.0')+'/json');(dest/'PYPI.json').write_bytes(pypi_raw);pypi=json.loads(pypi_raw)
 licenses=[n for n in files if 'license' in n.lower() or 'licence' in n.lower() or 'copying' in n.lower()]
 record=dict(package=package,repo=repo,ref=ref,commit=commit,archive_url=archive_url,archive=pin(raw),files=files,license_files=licenses,pypi_version=pypi['info']['version'],pypi_license=pypi['info'].get('license'),scientific_source_modified=False)
 save(dest/'SOURCE.json',record);records.append(record)
 print(json.dumps(dict(package=package,commit=commit,source_files=len(files),licenses=licenses,pypi_license=record['pypi_license']),ensure_ascii=False),flush=True)
save(B/'COMPLETED.json',dict(status='SOURCE_INTAKE_ONLY_NOT_RUNTIME_OR_SCIENTIFIC_QUALIFICATION',sources=records,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
