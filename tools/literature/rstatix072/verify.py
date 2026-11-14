#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete retained native Levene/original-provider checkpoint without new analysis."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,traceback,xml.etree.ElementTree as ET,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/rstatix072-development-checkpoint'
ACTION='Required action: retain exact original sources, full control populations, every returned original object/error/warning, native outputs and old failed setup. SKIP3 skips dependent checks/analysis when permitted source, Java/R runtime, provider, download, installation or execution is unavailable. Supply only permitted prerequisites and retry NEW paths. FAIL2 admits no qualification; correct the named source/output contradiction without shrinking populations or changing original algorithms. Native Levene is one prepared-vector method, not all rstatix/R/car functions or any reproduced paper. The original supervised analysis adapter and catalog admission remain unfinished. This command checks saved controls and current bytes; it performs no new analysis or installation. The full250-paper objective stays open.'
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def evidence(rescan=False):
 proof=json.loads((DATA/'VALIDATION.json').read_bytes())
 if proof['status']!='PASS_NATIVE_LEVENE_AND_ORIGINAL_RSTATIX072_PROVIDER_DEVELOPMENT_CHECKPOINT' or proof['new_tool_credit']!=0 or proof['main_catalog_modified'] or proof['whole_goal_complete']:
  raise ValueError('Unsupported development/admission scope')
 for key in ['sources','artifacts','main_catalog_files']:
  for name,wanted in proof[key].items():
   if pin(ROOT/name)!=wanted:raise ValueError('Current checkpoint source/artifact/catalog differs: '+name)
 if pin(DATA/'controls.zip')!=proof['archive'] or pin(DATA/'CONTROLS_LAYOUT.json')!=proof['controls_layout']:raise ValueError('Complete evidence archive/inventory differs')
 files=json.loads((DATA/'CONTROLS_LAYOUT.json').read_bytes())['files']
 with zipfile.ZipFile(DATA/'controls.zip') as archive:
  names=archive.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(files):raise ValueError('Incomplete/duplicate evidence population')
  for name,wanted in files.items():
   if name.startswith('/') or '\\' in name or any(s in ['', '.', '..'] for s in name.split('/')):raise ValueError('Unsafe saved evidence member')
   if rawpin(archive.read(name))!=wanted:raise ValueError('Complete saved evidence member differs: '+name)
  def js(n):return json.loads(archive.read(n))
  for name,wanted in proof['sources'].items():
   if rawpin(archive.read('current-source/'+name))!=wanted:raise ValueError('Current source snapshot differs')
  expected=js('fixtures/EXPECTATIONS.json');request=js('fixtures/request.json')['cases'];reference=js('original/reference-2/complete-original-values.json')
  if (len(expected),len(request),len(reference))!=(206,206,206) or [x['id'] for x in request]!=[x['id'] for x in reference]:raise ValueError('Incomplete original scientific cases')
  summaries={}
  for platform in ['windows21','windows25','linux21']:
   base='native/'+platform;completed=js(base+'/COMPLETED.json')
   if (completed['complete_cases'],completed['complete_computed_cases'],completed['original_scalar_comparisons'],completed['native_refusals'],completed['public_API_checks'],completed['CLI_refusals'])!=(206,196,784,10,29,6):raise ValueError('Incomplete native/public control population')
   if any(c['exit_code']!=c['expected_exit'] for c in completed['calls']):raise ValueError('Native control exit differs')
   rows=js(base+'/mixed-complete/cases.json');positive=js(base+'/positive-complete/cases.json')
   if len(rows)!=206 or len(positive)!=196 or [r['id'] for r in rows]!=[r['id'] for r in request]:raise ValueError('Incomplete native scientific cases')
   compared=0;refused=0;maxF=0;maxP=0
   for actual,original,source in zip(rows,reference,request,strict=True):
    case=actual['id']
    if expected[case]=='refuse':
     if actual['status']!='FAIL2_NATIVE_LEVENE_CASE_UNADMITTED':raise ValueError('Expected numerical/input refusal admitted')
     refused+=1;continue
    if actual['status']!='COMPUTED_NATIVE_LEVENE' or original['status']!='ORIGINAL_RETURNED':raise ValueError('Missing full native/original result')
    for key in ['df1','df2','statistic','p']:
     wanted=original['result'][key]
     if isinstance(wanted,list):wanted=wanted[0]
     difference=abs(actual[key]-wanted);tolerance=2e-9*max(1,abs(wanted)) if key=='statistic' else (2e-10*max(1e-300,abs(wanted))+1e-14 if key=='p' else 0)
     if difference>tolerance:raise ValueError('Complete original scalar differs: '+case+'/'+key)
     compared+=1
     if key=='statistic':maxF=max(maxF,difference)
     if key=='p':maxP=max(maxP,difference)
    omitted=[i for i,(v,g) in enumerate(zip(source['values'],source['groups'],strict=True)) if v=='NaN' or g is None]
    if actual['omitted_rows']!=omitted or actual['kept_rows']!=[i for i in range(len(source['values'])) if i not in omitted]:raise ValueError('Original row identity differs')
   if positive!=[r for r in rows if expected[r['id']]=='pass'] or (compared,refused)!=(784,10):raise ValueError('Complete declared positive/refusal population differs')
   for label,admitted in [('mixed-complete',False),('positive-complete',True)]:
    prefix=base+'/'+label+'/'
    if js(prefix+'analysis-status.json')['result_admitted']!=admitted:raise ValueError('Partial batch admission differs')
    manifest=js(prefix+'MANIFEST.json')
    if set(manifest['files'])!={'request.json','cases.json','WARNINGS.md'}:raise ValueError('Incomplete native manifest')
    for name,wanted in manifest['files'].items():
     if rawpin(archive.read(prefix+name))!=wanted:raise ValueError('Complete native result manifest differs')
   refusals=js(base+'/ORIGINAL_AND_NATIVE_REFUSALS.json')
   if len(refusals)!=10 or [r['id'] for r in refusals]!=[r['id'] for r in reference if expected[r['id']]=='refuse']:raise ValueError('Original/native refused cases omitted')
   for retained in refusals:
    if retained['original']!=next(r for r in reference if r['id']==retained['id']):raise ValueError('Original degeneracy result was modified')
   summaries[platform]=dict(cases=206,computed_cases=196,compared_scalars=compared,native_refusals=refused,maximum_absolute_F_difference=maxF,maximum_absolute_p_difference=maxP)
  profile=json.loads((HERE/'source-profile.json').read_bytes())
  for prefix in ['providers/local-ready','providers/public-download-ready']:
   ready=js(prefix+'/READY.json');readiness=js(prefix+'/readiness.json')
   if ready['status']!='READY_PRIVATE_ORIGINAL_RSTATIX072_NOT_ANALYSIS' or ready['version']!='0.7.2' or sorted(ready['namespace_exports'])!=profile['namespace_exports'] or ready['source_functions_checked']!=339:raise ValueError('Incomplete original provider namespace')
   if len(readiness['source_functions_checked'])!=339 or len(readiness['exports'])!=126:raise ValueError('Incomplete original source/readiness population')
   if rawpin(archive.read(prefix+'/rstatix_0.7.2.tar.gz'))!=profile['archive']:raise ValueError('Installed original source archive differs')
   for name,wanted in profile['source_files'].items():
    if rawpin(archive.read(prefix+'/source/'+name))!=wanted:raise ValueError('Complete original source member differs')
   for name,wanted in ready['installed_library'].items():
    if rawpin(archive.read(prefix+'/library/'+name))!=wanted:raise ValueError('Complete original installed target differs')
  failure=js('providers/failed-export-order/FAILURE.json')
  if failure['status']!='FAIL2_RSTATIX_PROVIDER' or js('providers/failed-export-order/readiness.json')['status']!='READY_ORIGINAL_RSTATIX072_NAMESPACE_NOT_ANALYSIS':raise ValueError('Original failed provider was lost/relabelled')
  for platform,count in [('windows',1),('linux',6)]:
   controls=js('setup/'+platform+'/COMPLETED.json')
   if controls['controls']!=count or any(c['exit_code']!=c['expected_exit'] for c in controls['calls']):raise ValueError('Incomplete provider refusal population')
   for call in controls['calls']:
    if js('setup/'+platform+'/'+call['case']+'/FAILURE.json')['calls']:raise ValueError('A refused prerequisite launched installation')
  census=json.loads((DATA/'CENSUS.json').read_bytes());intake=json.loads((HERE/'paper-use-intake.json').read_bytes())
  named={(r['paper_id'],r['paragraph_index'],r['paragraph_sha256'],r['section']) for r in census['matches']}
  supports={(r['paper_id'],a['paragraph_index'],a['paragraph_sha256'],a['section']) for r in intake['reviews'] for a in r['supporting_evidence']}
  if len(census['census'])!=250 or len(intake['reviews'])!=6 or named!=supports or len(named)!=12:raise ValueError('Incomplete primary roles/supports')
 for line in (DATA/'SHA256SUMS').read_text().splitlines():
  if line.strip():
   sha,name=line.split(maxsplit=1)
   if pin(DATA/name.lstrip('*'))['sha256']!=sha:raise ValueError('Checkpoint record differs: '+name)
 sys.path.insert(0,str(HERE.parent))
 from qupath_catalog.verify import verify
 current=verify(ROOT)
 if (current['papers'],current['named_tools'],current['observations'],current['reviewed_uses'],current['new_scoped_integrations'],current['missing'])!=(250,432,2671,1143,107,266):raise ValueError('Unchanged cumulative catalog differs')
 if rescan:
  from collect_omics_review import paragraphs
  records=[];matches=[]
  for p in json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes()):
   raw=gzip.decompress((ROOT/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')).read_bytes())
   if rawpin(raw)['sha256']!=p['xml_sha256']:raise ValueError('Exact primary XML differs')
   xml=ET.fromstring(raw)
   if p['doi'].lower() not in [e.text.lower() for e in xml.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi' and e.text]:raise ValueError('Primary DOI differs')
   indices=[]
   for s in paragraphs(xml):
    if re.search(r'(?<![A-Za-z0-9_])rstatix(?![A-Za-z0-9_])',s['match_text'],re.I):
     indices.append(s['paragraph_index']);matches.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],xml_sha256=p['xml_sha256'],paragraph_index=s['paragraph_index'],section=s['section'],paragraph_sha256=rawpin(s['text'].encode('utf8'))['sha256']))
   records.append(dict(paper_id=p['paper_id'],pmcid=p['pmcid'],doi=p['doi'],xml_sha256=p['xml_sha256'],named_paragraphs=indices))
  if records!=census['census'] or matches!=census['matches']:raise ValueError('Fresh250-source census differs')
 return dict(status='PASS_COMPLETE_RETAINED_NATIVE_LEVENE_AND_ORIGINAL_RSTATIX_PROVIDER',complete_archive_files=len(files),platforms=summaries,
  original_namespace_functions=339,original_exports=126,provider_refusal_controls=7,reviewed_primary_papers=6,named_primary_anchors=12,
  primary_sources_rescanned=rescan,new_original_analysis_executed=False,main_catalog_modified=False,new_tool_credit=0,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--out',type=Path,required=True);p.add_argument('--rescan-sources',action='store_true');a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=evidence(a.rescan_sources);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_RSTATIX_CHECKPOINT_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_RSTATIX_DEVELOPMENT_CHECKPOINT',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
