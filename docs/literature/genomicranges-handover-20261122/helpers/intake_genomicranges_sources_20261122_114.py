from pathlib import Path,PurePosixPath
import argparse,concurrent.futures,hashlib,json,tarfile,urllib.error,urllib.request
from prepare_genomicranges_20261122_114 import B
from handover_inventory_20261122_111 import pin,save
parser=argparse.ArgumentParser();parser.add_argument('--attempt',type=int,default=1);parser.add_argument('--modern',action='store_true');args=parser.parse_args();D=B/f'upstream-intake-{args.attempt}';D.mkdir();specs=[('1.64.0','3.23')] if args.modern else [('1.44.0','3.13'),('1.58.0','3.20'),('1.60.0','3.21')]
def intake(spec):
 version,release=spec;folder=D/('GenomicRanges'+version);folder.mkdir();url=f'https://bioconductor.org/packages/{release}/bioc/src/contrib/GenomicRanges_{version}.tar.gz';save(folder/'STARTED.json',{'url':url,'expected_version':version,'Bioconductor_release':release,'scope':'Official exact source intake;no installation or model execution. Browser viewer could not open historical package pages.','maximum_download_bytes':67108864})
 try:
  request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-intake/1.0','Accept':'application/gzip'})
  with urllib.request.urlopen(request,timeout=120) as response:raw=response.read(67108865);code=response.status;resolved=response.url;headers=dict(response.headers)
  path=folder/'download.tar.gz';path.write_bytes(raw);assert len(raw)<=67108864 and raw[:2]==b'\x1f\x8b'
  with tarfile.open(path,'r:gz') as archive:
   members=archive.getmembers();assert len(members)<=10000 and len({x.name for x in members})==len(members) and sum(x.size for x in members)<=536870912;description=archive.extractfile('GenomicRanges/DESCRIPTION').read().decode('utf-8');fields={}
   for line in description.splitlines():
    if line and not line[0].isspace() and ':' in line:key,value=line.split(':',1);fields[key]=value.strip()
   assert fields['Package']=='GenomicRanges' and fields['Version']==version
   source=folder/'source';source.mkdir();pins={}
   for member in members:
    name=PurePosixPath(member.name);assert not name.is_absolute() and '..' not in name.parts and all('\\' not in part and ':' not in part for part in name.parts);assert member.isdir() or member.isfile();assert member.size<=67108864
    target=source.joinpath(*name.parts)
    if member.isdir():target.mkdir(parents=True,exist_ok=True)
    else:
     target.parent.mkdir(parents=True,exist_ok=True);data=archive.extractfile(member).read();assert len(data)==member.size
     with target.open('xb') as stream:stream.write(data)
     pins[member.name]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
  report={'status':'OFFICIAL_EXACT_SOURCE_RETRIEVED','url':url,'resolved_url':resolved,'http_status':code,'response_headers':headers,'version':version,'release':release,'description':fields,'archive':pin(path),'files':pins,'installed':False,'new_catalog_credit':0};save(folder/'RESULT.json',report);print(version,'PASS_OFFICIAL_EXACT_SOURCE',fields.get('License'),pin(path)['sha256'],flush=True);return report
 except Exception as error:
  report={'status':'SOURCE_INTAKE_FAILED_NO_ANALYSIS','version':version,'url':url,'error':repr(error),'installed':False,'new_catalog_credit':0};save(folder/'FAILURE.json',report);print(version,report['status'],str(error),flush=True);return report
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:rows=list(pool.map(intake,specs))
save(D/'RESULT.json',{'results':[{k:r.get(k) for k in ['status','version','url','archive','error']} for r in rows],'new_catalog_credit':0,'whole_goal_complete':False})
