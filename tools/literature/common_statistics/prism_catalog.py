#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append P215's source-reviewed unversioned Prism use to the cumulative catalog.

One paper/tool observation has five source paragraphs, not five observations.
Historical definitions, observations, scientific code and evidence stay intact.
"""
import argparse
import copy
import gzip
import hashlib
import json
import re
import subprocess
import sys
import traceback
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from snpeff.model import META,DATA,NOTICE as PREVIOUS_NOTICE

BASE='f76e262494152572d3514dc7845c7c460e74c2ae'
EVIDENCE='docs/literature/prism-source-catalog'
PROOF=DATA+'/prism-source-catalog-validation.json'
COMPATIBILITY=DATA+'/prism-source-reviews-shared-source-compatibility.json'
GUIDE='docs/literature/PRISM_SOURCE_CATALOG.md'
BARE_PATTERN=r'(?<![A-Za-z0-9_])Prism(?![A-Za-z0-9_])'
CURRENT='250 papers, 446 named families, 2,694 paper-tool observations, 1,219 individually reviewed uses and 1,475 awaiting individual review. There are 127 admitted scoped integrations (23 native Java and 104 upstream), 260 missing families, and 130 integrations including three existing adapters. Full semantic review and implementation remain open.'
SCOPE='The five P215 Prism paragraphs support one new paper-tool observation, one individual review and four supporting pointers. The source specifies two-sided unpaired t-tests of replicate-average measurements and IHC comparisons; broader genomic paragraphs list statistical software without assigning specific operations. The word latest does not recover a numeric Prism edition. Variance assumptions, complete contrast/outcome families, historical settings and author data remain unresolved. Native Java statistical components do not establish a complete Prism replacement or paper reproduction. No implementation badge is added.'
ACTION='SKIP3: skip dependent analysis when a required permitted source, runtime, license, download, installation or execution is unavailable/forbidden. Supply the named permitted prerequisite and retry NEW output. FAIL2: admit no complete result on source, input, numerical, capacity or integrity failure. Preserve complete inputs, warnings, logs, partials and historical evidence; resolve the named cause and retry NEW output. Never repair identifiers, remove failed observations, substitute author versions or refresh historical fingerprints to force success.'
NOTICE='<aside id="prism-source-catalog-notice" style="box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>Prism: corrected source coverage and remaining models</h2><p>'+CURRENT+'</p><p>'+SCOPE+'</p><p><strong>Required actions:</strong> '+ACTION+'</p><p>Current source builds contain separate native Tukey-Kramer and independent-sample Dunn commands. Exact author models remain explicit. <a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Source review, method guides and recovery</a>.</p></aside>'
OWN=['tools/literature/common_statistics/prism_catalog.py','tools/literature/common_statistics/review_sources.py','tools/literature/common_statistics/test_prism_catalog.py']
PREVIOUS_STALE_MANIFEST={'papers.json','README.md','summary.json','tool-inventory.json','WARNINGS.md'}

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(Path(path).read_bytes())
def require(condition,reason):
 if not condition:raise ValueError(reason)
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)
def registry():
 from snpeff.model import registry as previous
 rows=previous();require(len(rows)==485,'Unexpected predecessor registry population')
 targets=[row for row in rows if row['id']=='graphpad_prism'];require(len(targets)==1,'Ambiguous Prism registry definition')
 row=targets[0];row['pattern']='(?:'+row['pattern']+'|'+BARE_PATTERN+')'
 return rows
def baseline():
 before={name:git('show',BASE+':'+name) for name in META}
 previous=json.loads(git('show',BASE+':'+DATA+'/snpeff-catalog-validation.json'))
 require({n:rawpin(v) for n,v in before.items()}==previous['after'],'Actual Git predecessor differs from its retained metadata admission')
 return before

def manifest_audit():
 data=ROOT/DATA;raw=(data/'SHA256SUMS').read_bytes();names=[];mismatches=[]
 legacy={line.split('  ',1)[1] for line in git('show',BASE+':'+DATA+'/SHA256SUMS').decode().splitlines() if '/' in line.split('  ',1)[1]}
 for line in raw.decode().splitlines():
  digest,name=line.split('  ',1);require((Path(name).name==name or name in legacy) and (data/name).resolve().is_relative_to(ROOT/'docs/literature') and re.fullmatch('[a-f0-9]{64}',digest),'Invalid catalog checksum member')
  names.append(name)
  if not (data/name).is_file() or pin(data/name)['sha256']!=digest:mismatches.append(name)
 require(len(names)==len(set(names)),'Duplicate catalog checksum entry')
 expected={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}|legacy
 return dict(manifest=rawpin(raw),entries=len(names),mismatches=sorted(mismatches),missing_entries=sorted(expected-set(names)),extra_entries=sorted(set(names)-expected))

def refresh_current_manifest():
 data=ROOT/DATA;old=(data/'SHA256SUMS').read_text().splitlines();files={p.name:pin(p)['sha256'] for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
 for line in old:
  _,name=line.split('  ',1)
  if name not in files:
   require(name.startswith('../') and (data/name).resolve().is_relative_to(ROOT/'docs/literature'),'Invalid preserved external literature checksum member');files[name]=pin(data/name)['sha256']
 order=[line.split('  ',1)[1] for line in old];require(len(order)==len(set(order)) and set(order)<=set(files),'Catalog checksum inventory lost a recorded member')
 order+=sorted(set(files)-set(order));(data/'SHA256SUMS').write_bytes(''.join(files[name]+'  '+name+'\n' for name in order).encode())

def primary(cache,papers):
 verify_fixed_cohort(ROOT,ROOT/DATA,papers)
 rows=registry();definition=next(row for row in rows if row['id']=='graphpad_prism');matched=[];identities=[];selected=[]
 for paper in papers:
  raw=gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes());require(rawpin(raw)['sha256']==paper['xml_sha256'],'Frozen primary XML differs: '+paper['paper_id'])
  identities.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256']))
  for row in paragraphs(ET.fromstring(raw)):
   match=re.search(definition['pattern'],row['match_text'],re.I)
   if match:
    pointer=dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],paragraph_index=row['paragraph_index'],paragraph_sha256=rawpin(row['text'].encode())['sha256'],section=row['section'],matched_text=match[0]);matched.append(pointer)
    if paper['paper_id']=='P215':selected.append(pointer)
 existing={p['paper_id'] for p in papers for t in p['tools'] if t['tool_id']=='graphpad_prism'};detected={p['paper_id'] for p in matched}
 require(len(existing)==46 and detected==existing|{'P215'},'Unexpected changed Prism paper-family population')
 require([p['paragraph_index'] for p in selected]==[11,12,20,25,26] and all(p['matched_text'].lower()=='prism' for p in selected),'P215 source population or spelling differs')
 paper=next(p for p in papers if p['paper_id']=='P215');require(paper['pmcid']=='PMC11480806' and paper['doi']=='10.1016/j.ajhg.2024.07.022','P215 identity differs')
 anchor=next(p for p in selected if p['paragraph_index']==26)
 supporting=[{k:p[k] for k in ['paragraph_index','paragraph_sha256','section']} for p in selected if p['paragraph_index']!=26]
 summary='Used Prism for two-sided unpaired t-tests of biological-replicate average measurements in Superplot quantification and IHC comparisons. RNA-seq and ChIP/ATAC paragraphs also list Prism among statistical software without identifying its separate operations.'
 scope='The source says latest without a numeric Prism edition. Equal-variance versus Welch assumptions, exact author inputs/settings and complete outcome/contrast families are unreported. Replicate-average measurements, rather than each captured image as an independent replicate, are specified. Separate native Java tests require an explicitly justified model; no automatic model selection, historical Prism parity, whole-paper reproduction or new family badge.'
 observation=dict(tool_id='graphpad_prism',tool='GraphPad Prism',analysis_role='Statistical analysis of replicate-average measurements',evidence_section=anchor['section'],evidence_paragraph_index=26,matched_text='Prism',extraction_score=0,usage_context='two-sided-unpaired-tests-of-replicate-means; broader-software-lists',review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=None,version_reporting_note='The paper says latest; no numeric Prism edition is established.',evidence_selection='individually-reviewed-unversioned-name-source-extension',evidence_paragraph_sha256=anchor['paragraph_sha256'],use_summary=summary,evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')',implementation_scope_note=scope,supporting_evidence=supporting)
 review={k:copy.deepcopy(observation[k]) for k in ['tool_id','evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']};review.update(pmcid=paper['pmcid'],doi=paper['doi'])
 raw=gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes());validate_supporting_evidence(paragraphs(ET.fromstring(raw)),[review],{'graphpad_prism':definition})
 return dict(status='PASS250_PRIMARY_HASHES_AND_P215_PRISM_SOURCE_CORRECTION',primary_sources=identities,prism_named_papers=47,prism_paragraph_pointers=matched,new_paper_tool_observations=1,individually_reviewed_uses_added=1,new_supporting_pointers=4,new_observation=observation,new_review=review,whole_goal_complete=False)

def transition(before,census):
 require(set(before)==set(META),'Exactly eight predecessor metadata files required')
 papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
 require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2693 and len(reviews['reviews'])==1218,'Unexpected predecessor catalog population')
 require(census['status']=='PASS250_PRIMARY_HASHES_AND_P215_PRISM_SOURCE_CORRECTION' and census['prism_named_papers']==47,'Source correction census differs')
 paper=next(p for p in papers if p['paper_id']=='P215');require(not any(t['tool_id']=='graphpad_prism' for t in paper['tools']),'P215 Prism is already present')
 require(not any(v['pmcid']==paper['pmcid'] and v['tool_id']=='graphpad_prism' for v in reviews['reviews']),'P215 Prism review already present')
 paper['tools'].append(copy.deepcopy(census['new_observation']));reviews['reviews'].append(copy.deepcopy(census['new_review']))
 row=next(t for t in inventory if t['id']=='graphpad_prism');require(row['paper_count']==46 and row['implementation_status']=='missing' and row['source_roles_reviewed']==2,'Prism predecessor status differs')
 row.update(pattern=next(t['pattern'] for t in registry() if t['id']=='graphpad_prism'),paper_count=47,papers=sorted(row['papers']+['P215']),source_roles_reviewed=3,
  coverage_note='Source coverage includes the individually reviewed bare Prism mention in P215. Separate native Tukey-Kramer/Dunn components exist; remaining models, historical settings and whole Prism workflows are not established.',
  implementation_scope='Prism family remains missing: separate current-source native Tukey-Kramer and independent Dunn components do not supply all cited statistical models, historical defaults, GUI workflows or author analyses. P215 specifies two-sided unpaired tests on replicate-average measurements but no numeric Prism edition or variance assumption. Read PRISM_SOURCE_CATALOG.md.',
  validation_note='All 250 frozen primary XML hashes checked; 47 Prism paper-family uses detected. One reviewed observation and four supporting P215 pointers are appended; every earlier observation/review and unrelated family are preserved. This is a source correction, not a new scientific-engine or vendor-runtime qualification.')
 updated=copy.deepcopy(summary);updated['paper_tool_observations']=2694
 after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],updated),('tools/literature/reviewed-uses.json',reviews)]:
  raw=encoded(value)
  if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
  after[name]=raw
 page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=summary),'Predecessor embedded catalog differs')
 payload=json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
 for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
  require(text.count(PREVIOUS_NOTICE)==1,'Predecessor notice differs')
  after[name]=text.replace(PREVIOUS_NOTICE,NOTICE+'<details><summary>Previous SnpEff catalog checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1).encode()
 for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
  block='# '+title+'\n\n## Prism source correction: counts, scope and required actions\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n'+ACTION+'\n\nRead [the source correction and current method guides](../PRISM_SOURCE_CATALOG.md). No website deployment or release is performed by this catalog correction.\n\n<details><summary>Catalog before the P215 source correction (historical)</summary>\n\n'
  after[name]=(block+before[name].decode()+'\n</details>\n').encode()
 return after

def check(before,after,census):
 require(after==transition(before,census),'Catalog differs from the exact source-only append')
 a,b=json.loads(before[META[0]]),json.loads(after[META[0]])
 for old,new in zip(a,b,strict=True):
  require({k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'},'Paper identity/metadata changed')
  require(new['tools'][:len(old['tools'])]==old['tools'],'Previous observations or order changed')
  require(len(new['tools'])==len(old['tools'])+(old['paper_id']=='P215'),'Unrelated observation appended')
 old_reviews=json.loads(before['tools/literature/reviewed-uses.json']);new_reviews=json.loads(after['tools/literature/reviewed-uses.json']);require(new_reviews['reviews'][:-1]==old_reviews['reviews'],'Previous reviews changed')
 require({k:v for k,v in new_reviews.items() if k!='reviews'}=={k:v for k,v in old_reviews.items() if k!='reviews'},'Review-document metadata changed')
 old_inventory=json.loads(before[META[1]]);new_inventory=json.loads(after[META[1]])
 require([t for t in old_inventory if t['id']!='graphpad_prism']==[t for t in new_inventory if t['id']!='graphpad_prism'],'Unrelated inventory family changed')
 prior=json.loads(before[META[2]]);current=json.loads(after[META[2]]);require({k:v for k,v in prior.items() if k!='paper_tool_observations'}=={k:v for k,v in current.items() if k!='paper_tool_observations'},'Window, goal or implementation counts changed')
 return dict(papers=250,named_tools=446,observations=2694,reviewed_uses=1219,unreviewed_uses=1475,prism_papers=47,registry_definitions=485,observations_added=1,reviews_added=1,supporting_pointers_added=4,new_scoped_integrations=127,native_integrations=23,upstream_integrations=104,missing=260,new_integration_credit=0,whole_goal_complete=False)

def verify(cache,out):
 proof=json.loads((ROOT/PROOF).read_bytes());require(proof['status']=='PASS_P215_PRISM_SOURCE_CATALOG_CORRECTION','Missing source-correction admission')
 require(proof['source_code']=={name:pin(ROOT/name) for name in OWN},'Correction verifier source changed; use its retained source checkpoint')
 with zipfile.ZipFile(ROOT/EVIDENCE/'catalog-before.zip') as z:
  require(len(z.namelist())==8 and set(z.namelist())==set(META),'Incomplete predecessor archive');before={name:z.read(name) for name in META}
 require(before==baseline(),'Archived predecessor differs from the actual historical Git commit')
 for name,identity in proof['evidence'].items():require(pin(ROOT/name)==identity,'Evidence changed: '+name)
 census=primary(cache,json.loads(before[META[0]]));after={name:(ROOT/name).read_bytes() for name in META};result=check(before,after,census)
 require(proof['before']=={n:rawpin(v) for n,v in before.items()} and proof['after']=={n:rawpin(v) for n,v in after.items()},'Recorded catalog pins differ')
 require(census==json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes()),'Source recheck differs')
 current_manifest=manifest_audit();require(not current_manifest['mismatches'] and not current_manifest['missing_entries'] and not current_manifest['extra_entries'],'Current catalog checksum inventory differs or is incomplete')
 (out/'COMPLETED.json').write_bytes(encoded(dict(status=proof['status'],**result,source_xml_hashes_checked=250)))
 return result

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--plan',action='store_true');mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true');p.add_argument('--primary-cache',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None;before=after=None;started=False
 try:
  require(__debug__,'Never disable catalog/source assertions');target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Use a NEW workspace build evidence directory');target.mkdir(parents=True,exist_ok=False);out=target
  if a.verify:result=verify(a.primary_cache,out)
  else:
   before=baseline();require(all((ROOT/n).read_bytes()==v for n,v in before.items()),'Canonical metadata differs from the verified predecessor; preserve and review it')
   previous_manifest=manifest_audit();require((ROOT/DATA/'SHA256SUMS').read_bytes()==git('show',BASE+':'+DATA+'/SHA256SUMS'),'Predecessor current-manifest bytes changed');require(set(previous_manifest['mismatches'])==PREVIOUS_STALE_MANIFEST and len(previous_manifest['missing_entries'])==71 and not previous_manifest['extra_entries'],'Unexpected predecessor checksum discrepancies; preserve and review')
   for name in previous_manifest['missing_entries']:require((ROOT/DATA/name).read_bytes()==git('show',BASE+':'+DATA+'/'+name),'Previously unlisted metadata differs from its actual historical Git bytes: '+name)
   census=primary(a.primary_cache,json.loads(before[META[0]]));after=transition(before,census);result=check(before,after,census);(out/'PLAN.json').write_bytes(encoded(result))
   if a.apply:
    require(not (ROOT/EVIDENCE).exists() and not (ROOT/PROOF).exists() and not (ROOT/COMPATIBILITY).exists(),'Retain existing correction evidence; do not overwrite')
    evidence=ROOT/EVIDENCE;evidence.mkdir();(evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name,raw in before.items():z.writestr(name,raw)
    (evidence/'CENSUS.json').write_bytes(encoded(census));(evidence/'REGISTRY.json').write_bytes(encoded(registry()));(evidence/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/DATA/'SHA256SUMS').read_bytes())
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    name='tools/literature/reviewed-uses.json';change=delta(name,before[name],after[name]);require(reverse_delta(after[name],change)==before[name],'Review inverse differs')
    journal=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='graphpad_prism',scope='One reviewed source-only append; no scientific source, result or historical verifier is projected or modified.',paths=[change]);(ROOT/COMPATIBILITY).write_bytes(encoded(journal))
    (evidence/'WARNINGS.md').write_text('# Source-only correction and required actions\n\n'+SCOPE+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    with zipfile.ZipFile(evidence/'verifier-source.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name in OWN:z.writestr(name,(ROOT/name).read_bytes())
    require(all((ROOT/n).read_bytes()==v for n,v in before.items()),'Metadata changed before append')
    started=True
    for name,raw in after.items():(ROOT/name).write_bytes(raw)
    proof=dict(status='PASS_P215_PRISM_SOURCE_CATALOG_CORRECTION',**result,base_commit=BASE,source_code={n:pin(ROOT/n) for n in OWN},before={n:rawpin(v) for n,v in before.items()},after={n:rawpin(v) for n,v in after.items()},evidence={path.relative_to(ROOT).as_posix():pin(path) for path in evidence.rglob('*') if path.is_file()},predecessor_current_manifest=previous_manifest,current_manifest_regenerated=True,scientific_engines_changed=False,earlier_sources_or_verifiers_overlaid=False,historical_fingerprints_refreshed=False,website_deployed=False)
    proof['evidence'][COMPATIBILITY]=pin(ROOT/COMPATIBILITY);(ROOT/PROOF).write_bytes(encoded(proof))
    refresh_current_manifest()
    require(all((ROOT/n).read_bytes()==v for n,v in after.items()),'Canonical metadata changed after append')
    (out/'COMPLETED.json').write_bytes(encoded(dict(status=proof['status'],**result)))
   else:(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_REVIEWABLE_P215_PRISM_SOURCE_CORRECTION_PLAN',**result)))
  print(json.dumps(result),flush=True);return 0
 except Exception:
  detail=traceback.format_exc();restored=[]
  if started:
   for name,raw in before.items():
    if (ROOT/name).read_bytes()==after[name]:(ROOT/name).write_bytes(raw);restored.append(name)
  if out is not None:(out/'FAILURE.json').write_bytes(encoded(dict(status='FAIL2_NO_CATALOG_ADMISSION',detail=detail,restored_metadata=restored,required_action=ACTION)))
  print('FAIL2: no catalog admission; preserve diagnostics and retry NEW output after resolving the named cause.\n'+detail,file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
