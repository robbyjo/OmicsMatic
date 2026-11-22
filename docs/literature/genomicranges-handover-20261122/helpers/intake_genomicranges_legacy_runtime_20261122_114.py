from pathlib import PurePosixPath
import hashlib,json,urllib.error,urllib.request,zipfile
from prepare_genomicranges_20261122_114 import B
from handover_inventory_20261122_111 import pin,save
D=B/'legacy-r410-intake-1';D.mkdir();attempts=[]
urls=['https://cran.r-project.org/bin/windows/base/old/4.1.0/R-4.1.0-win.exe','https://cran-archive.r-project.org/bin/windows/base/old/4.1.0/R-4.1.0-win.exe']
for i,url in enumerate(urls,1):
 request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-GenomicRanges-reference-intake/1.0'})
 try:
  with urllib.request.urlopen(request,timeout=120) as response:raw=response.read(134217729);code=response.status;resolved=response.url;headers=dict(response.headers)
 except urllib.error.HTTPError as error:
  raw=error.read();(D/f'HTTP-FAILURE-{i}.body').write_bytes(raw);attempts.append({'url':url,'http_status':error.code,'response_bytes':len(raw)});continue
 except Exception as error:attempts.append({'url':url,'error':repr(error)});continue
 assert len(raw)<=134217728 and raw.startswith(b'MZ');path=D/'R-4.1.0-win.exe';path.write_bytes(raw);attempts.append({'url':url,'resolved_url':resolved,'http_status':code,'headers':headers,'pin':pin(path)});break
save(D/'R-INTAKE.json',{'attempts':attempts,'runtime_installer_executed':False,'system_registry_or_existing_runtime_modified':False})
if not (D/'R-4.1.0-win.exe').exists():
 print(json.dumps({'status':'LEGACY_RUNTIME_NOT_RETRIEVED','attempts':attempts}),flush=True);raise SystemExit(3)
toolurl='https://constexpr.org/innoextract/files/innoextract-1.9/innoextract-1.9-windows.zip'
with urllib.request.urlopen(toolurl,timeout=90) as response:raw=response.read(2097153)
assert len(raw)<=2097152 and hashlib.sha256(raw).hexdigest()=='6989342c9b026a00a72a38f23b62a8e6a22cc5de69805cf47d68ac2fec993065';archive=D/'innoextract-1.9-windows.zip';archive.write_bytes(raw);out=D/'innoextract';out.mkdir()
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 for info in z.infolist():
  name=PurePosixPath(info.filename);assert not name.is_absolute() and '..' not in name.parts and all(':' not in x and '\\' not in x for x in name.parts);assert info.file_size<=16777216
  target=out.joinpath(*name.parts)
  if info.is_dir():target.mkdir(parents=True,exist_ok=True)
  else:
   target.parent.mkdir(parents=True,exist_ok=True)
   with target.open('xb') as stream:stream.write(z.read(info))
save(D/'RESULT.json',{'status':'LEGACY_R_AND_PORTABLE_EXTRACTOR_DOWNLOADED_NOT_EXECUTED','R_installer':pin(D/'R-4.1.0-win.exe'),'extractor':{'url':toolurl,'archive':pin(archive),'published_SHA256_source':'https://constexpr.org/innoextract/files/innoextract-1.9/SHA256SUMS','published_digest_match':True},'R_installer_executed':False,'system_or_existing_qualified_runtimes_modified':False,'new_catalog_credit':0})
print(json.dumps({'status':'LEGACY_R_AND_PORTABLE_EXTRACTOR_READY','R_installer':pin(D/'R-4.1.0-win.exe'),'extractor_archive':pin(archive)}),flush=True)
