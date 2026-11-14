#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check exact original distributions, primary identities and candidate namespaces; fit no model."""
from pathlib import Path
import argparse,gzip,hashlib,json,os,subprocess,sys,traceback,xml.etree.ElementTree as ET,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION='This is a source/namespace audit, not a qualified analysis provider or an implementation admission. Preserve complete console and all failed attempts. Missing/forbidden prerequisites SKIP3 skip dependent analysis; integrity mismatches FAIL2 admit no complete result. Resolve the exact named source/runtime requirement and retry NEW paths. The printed0.24 edition and R velociraptor wrapper remain separate unresolved requirements.'
def encoded(v):return (json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8')
def pin(p):
 raw=p.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
PROBE='''from pathlib import Path
import hashlib,importlib.metadata,json,sys
def distributions():
 return sorted([dict(name=d.metadata['Name'],version=d.version,distribution_directory=str(d._path),root=str(d.locate_file(''))) for d in importlib.metadata.distributions()],key=lambda x:(x['name'],x['version'],x['distribution_directory']))
before=distributions()
import scvelo
package=Path(scvelo.__file__).resolve().parent
files={}
for p in package.rglob('*'):
 if p.is_file() and '__pycache__' not in p.parts:
  raw=p.read_bytes();files[p.relative_to(package.parent).as_posix()]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
result=dict(version=scvelo.__version__,python=sys.version,executable=sys.executable,namespace=str(package),original_package_files=files,distributions_before_import=before,distributions_after_import=distributions())
Path(sys.argv[1]).write_text(json.dumps(result,indent=2),encoding='utf8')
'''
def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--archives',type=Path,required=True);parser.add_argument('--python024',type=Path,required=True);parser.add_argument('--python025',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();owned=False
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True
  profile=json.loads((HERE/'source-profile.json').read_bytes());census=json.loads((HERE/'source-census.json').read_bytes());wrapper=json.loads((HERE/'wrapper-source-census.json').read_bytes());papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
  if len(papers)!=250 or len(census)!=250 or len(wrapper)!=250:raise ValueError('Complete fixed250-source census required')
  sys.path.insert(0,str(HERE.parent));from collect_omics_review import paragraphs
  import re
  for paper,row,other in zip(papers,census,wrapper):
   for item in [row,other]:
    if any(item[k]!=paper[k] for k in ['paper_id','pmcid','doi','xml_sha256']):raise ValueError('Primary identity differs')
   raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
   if hashlib.sha256(raw).hexdigest()!=paper['xml_sha256']:raise ValueError('Exact primary XML differs')
   xml=ET.fromstring(raw);ps=paragraphs(xml)
   if paper['doi'].lower() not in [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi' and e.text]:raise ValueError('Primary DOI differs')
   for name,item in [('scvelo',row),('velociraptor',other)]:
    found=[dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=hashlib.sha256(p['text'].encode()).hexdigest()) for p in ps if re.search(r'(?<![A-Za-z0-9_])'+name+r'(?![A-Za-z0-9_])',p['match_text'],re.I)]
    if found!=item['named_anchors']:raise ValueError('Complete named primary paragraph population differs: '+paper['paper_id']+' '+name)
  probe=out/'namespace-probe.py';probe.write_text(PROBE,encoding='utf8',newline='\n');versions=[]
  env={k:v for k,v in os.environ.items() if k not in ['PYTHONPATH','PYTHONHOME','LD_PRELOAD','LD_LIBRARY_PATH']}
  env.update(PYTHONNOUSERSITE='1',PYTHONDONTWRITEBYTECODE='1',MPLCONFIGDIR=str(out/'matplotlib-cache'),NUMBA_CACHE_DIR=str(out/'numba-cache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  for selected,python in zip(profile['profiles'],[a.python024,a.python025]):
   version=selected['version'];wheel=None
   for item in selected['source_downloads']:
    path=a.archives/version/item['filename']
    if pin(path)!={k:item[k] for k in ['bytes','sha256']}:raise ValueError('Exact original distribution differs: '+str(path))
    if item['packagetype']=='bdist_wheel':wheel=path
   with zipfile.ZipFile(wheel) as z:
    actual={n:dict(bytes=len(z.read(n)),sha256=hashlib.sha256(z.read(n)).hexdigest()) for n in z.namelist() if n.startswith('scvelo/') and not n.endswith('/')}
   if actual!=selected['original_package_files']:raise ValueError('Complete original source namespace differs')
   result=out/('namespace-'+version+'.json')
   with (out/('namespace-'+version+'.stdout')).open('xb') as so,(out/('namespace-'+version+'.stderr')).open('xb') as se:
    child=subprocess.run([str(python),'-B',str(probe),str(result)],stdout=so,stderr=se,env=env,cwd=out)
   if child.returncode:raise OSError('Candidate import failed for'+version+'; inspect complete captured console')
   state=json.loads(result.read_bytes())
   if state['version']!=version or state['original_package_files']!=selected['original_package_files']:raise ValueError('Installed original source namespace/version differs')
   versions.append(dict(version=version,original_files=len(actual),namespace=state['namespace'],before_metadata_records=len(state['distributions_before_import']),after_metadata_records=len(state['distributions_after_import'])))
  summary=dict(status='PASS_ORIGINAL_SCVELO_DISTRIBUTIONS_COMPLETE_NAMESPACES_AND_PRIMARY_CENSUS_ONLY',primary_sources=250,scvelo_papers=5,scvelo_named_paragraphs=6,new_wrapper_papers=['P228'],versions=versions,analysis_executed=False,provider_qualified=False,new_tool_credit=0,new_catalog_review_credit=0,whole_goal_complete=False)
  (out/'COMPLETED.json').write_bytes(encoded(summary));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print(summary['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
  record=dict(status='SKIP3_SCVELO_SOURCE_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_SCVELO_SOURCE_NAMESPACE_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,analysis_executed=False,new_tool_credit=0)
  if owned:(out/'FAILURE.json').write_bytes(encoded(record));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8')
  print(record['status'],repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
