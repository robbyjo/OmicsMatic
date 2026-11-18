#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-bound LDSC role correction and one bounded native-family admission.

Preserves the actual Git predecessor, every unrelated observation/review/family,
the frozen 250-paper identities, scientific engines and historical evidence.
"""
import argparse,copy,gzip,hashlib,json,re,subprocess,sys,traceback,xml.etree.ElementTree as ET,zipfile
from collections import Counter
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.dont_write_bytecode=True
sys.path.insert(0,str(ROOT/'tools/literature'))
from collect_omics_review import paragraphs,validate_supporting_evidence
from catalog_fixed_cohort import verify_fixed_cohort
from common_statistics.prism_catalog import META,DATA,NOTICE as PREVIOUS_NOTICE,manifest_audit,refresh_current_manifest
from ldsc_hsq.roles import ROLES,EXCLUDED

BASE='118f202c848bdfe2cf61e07a5f27cbd37912935b'
EVIDENCE='docs/literature/ldsc-catalog'
PROOF=DATA+'/ldsc-catalog-validation.json'
COVERAGE=DATA+'/ldsc-source-role-coverage.json'
COMPATIBILITY=DATA+'/ldsc-catalog-shared-source-compatibility.json'
GUIDE='docs/literature/LDSC_CATALOG_INTEGRATION.md'
PROGRAM='src/main/java/org/jlinalg/cli/LdscToolsCli.java'
PATTERN=r'(?<![A-Za-z0-9_])(?:s[- ]?LDSC|LDSC|LD[- ]?score\s+regression|linkage[- ]disequilibrium[ -]+score[ -]+regression)(?![A-Za-z0-9_])'
NOTE=('Native Java prepared unpartitioned observed-scale Hsq and two-trait RG/Gencov through ldsc-tools h2/rg, selected CBIIT 3.0.2 numerical-core behavior. '
 'Explicit intercepts, one/two-step masks, sample sizes/reference M, fixed-stage-weight and ratio jackknives, full row/block/weight diagnostics and no-result statuses are qualified. '
 'This is one scoped native family, not complete LDSC software or 42 reproduced papers. '
 'Munging, allele/reference preparation, LD-score generation, liability conversion, stratification, multivariate/wrapper-specific behavior and author historical settings remain separate. '
 'The 42 reviewed roles distinguish direct analyses, Genomic SEM/gsMap/CELLECT methods and two reused-resource roles. '
 'No original binary is inferred from a method mention. Build current source; released 0.4.0 lacks these commands.')
CURRENT=('250 papers, 446 named families, 2,695 paper-tool observations, 1,261 individually reviewed uses and 1,434 awaiting individual review. '
 'There are 128 admitted new scoped integrations (24 native Java, 104 upstream), 260 missing families and 131 integrations including three existing adapters. '
 'The frozen 2023-10-23..2026-10-23 cohort/window is unchanged; full semantic review and implementation remain open.')
ACTION=('SKIP3: skip dependent analysis for unavailable/forbidden input or prerequisite; supply the named permitted resource and retry NEW output. '
 'FAIL2: admit no complete result on invalid input, source, numerical, capacity or integrity checks. For RG, preserve component fits and distinct NA/NaN/raw diagnostics when correlation is unreportable. '
 'Read FAILURE.txt, WARNINGS.md and model.json; correct the named model/reference/population issue and retry NEW output. '
 'Never drop failed SNPs/blocks, force positive heritability, replace NA/NaN or clamp final estimates to obtain success. '
 'Explicit retain-raw changes the source range presentation only; it cannot repair invalid uncertainty. No Python installation is needed for the native workflows.')
NOTICE=('<aside id="ldsc-catalog-notice" style="box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>LDSC: native unpartitioned fits and corrected source roles</h2><p>'+CURRENT+'</p><p>'+NOTE+'</p><p><strong>Required actions:</strong> '+ACTION+'</p><p>P019/P092 are added; P151 is removed as comparison-only. P177 remains excluded as discussion. P220 reuses estimates and P245 reuses annotations; neither is a study LDSC execution. <a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Reviewed roles, commands, limits and recovery</a>.</p></aside>')
OWN=['tools/literature/ldsc_hsq/catalog.py','tools/literature/ldsc_hsq/roles.py','tools/literature/ldsc_hsq/test_catalog.py']

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(Path(path).read_bytes())
def require(condition,reason):
 if not condition:raise ValueError(reason)
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)
def registry():
 from common_statistics.prism_catalog import registry as previous
 rows=previous();require(len(rows)==485,'Predecessor registry population differs');row=next(r for r in rows if r['id']=='ldsc');require(row['current_coverage']=='method-overlap','Predecessor LDSC definition differs')
 row.update(pattern=PATTERN,current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=NOTE);return rows
def baseline():
 before={name:git('show',BASE+':'+name) for name in META}
 old=json.loads(git('show',BASE+':'+DATA+'/prism-source-catalog-validation.json'))
 require({n:rawpin(v) for n,v in before.items()}==old['after'],'Actual predecessor differs from the retained cumulative Prism admission')
 return before
def native_qualification():
 path='docs/literature/ldsc-rg-development/VALIDATION.json';raw=git('show',BASE+':'+path);require((ROOT/path).read_bytes()==raw,'Native qualification record changed')
 proof=json.loads(raw);require(proof['status']=='PASS_SCOPED_NATIVE_LDSC_RG_GENCOV_COMPONENT' and proof['profile']=='native-ldsc-rg-cbiit302-v1','Missing selected native qualification')
 require(proof['original_core_models']==72 and proof['original_core_reference_fields']==54202 and proof['earlier_distinct_hsq_models_rechecked']==72,'Native reference population differs')
 expected=dict(tests=21,errors=0,failures=0,skipped=0);require(proof['windows_junit']==proof['linux_same_junit']==expected,'Native Windows/Linux checks differ')
 package=proof['packaged_validation'];require(len(package['processes'])==43 and all(p['exit_code']==p['expected'] for p in package['processes']) and package['byte_identical_rg_payloads']==133 and package['byte_identical_prior_hsq_files']==96,'Actual packaged qualification differs')
 require(proof['all_archive_payloads_read_back'] and proof['indexed_payloads']==1041,'Incomplete retained scientific controls')
 archive='docs/literature/ldsc-rg-development/checks.zip';require(pin(ROOT/archive)==proof['controls_archive'],'Native scientific archive changed')
 sources={}
 for name,expected_pin in proof['source_guards'].items():
  require(rawpin(git('show',BASE+':'+name))==expected_pin,'Native original Git source binding differs: '+name)
  if name.startswith('src/'):
   require(pin(ROOT/name)==expected_pin,'Qualified scientific source/fixture changed: '+name);sources[name]=expected_pin
 # Bind shared parsing/capture helpers to the same tested Git snapshot as well.
 # Historical metadata guards above are verified against BASE, never refreshed.
 for leaf in ['NextflowFiles.java','NextflowParameters.java','NextflowJson.java']:
  name='src/main/java/org/jlinalg/cli/'+leaf;expected_pin=rawpin(git('show',BASE+':'+name))
  require(pin(ROOT/name)==expected_pin,'Qualified shared CLI dependency changed: '+name);sources[name]=expected_pin
 return dict(status='PASS_SAVED_NATIVE_CORE_QUALIFICATION_AND_CURRENT_SCIENTIFIC_SOURCE_BINDINGS',qualification={path:rawpin(raw)},scientific_archive={archive:proof['controls_archive']},scientific_sources=sources,original_core_models=72,earlier_hsq_models=72,windows_tests=expected,linux_same_tests=expected,scientific_runtime_reexecuted=False,original_core_only=True,whole_tool_qualified=False,whole_paper_reproduced=False)
def primary(cache,papers):
 verify_fixed_cohort(ROOT,ROOT/DATA,papers);require(len(ROLES)==42 and set(EXCLUDED)=={'P151','P177'},'Reviewed decision population differs')
 definition=next(r for r in registry() if r['id']=='ldsc');identities=[];matches=[];roles=[];excluded=[];old_ids={p['paper_id'] for p in papers for t in p['tools'] if t['tool_id']=='ldsc'}
 for paper in papers:
  raw=gzip.decompress((Path(cache)/(paper['pmcid']+'.xml.gz')).read_bytes());require(rawpin(raw)['sha256']==paper['xml_sha256'],'Frozen primary XML differs: '+paper['paper_id'])
  identities.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256']));rows=paragraphs(ET.fromstring(raw));by_index={p['paragraph_index']:p for p in rows};found=[]
  for row in rows:
   match=re.search(PATTERN,row['match_text'],re.I)
   if match:found.append(dict(paragraph_index=row['paragraph_index'],paragraph_sha256=rawpin(row['text'].encode())['sha256'],section=row['section'],matched_text=match[0]))
  if found:matches.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],source_pointers=found))
  def pointer(index):
   require(index in by_index,'Missing reviewed source paragraph');row=by_index[index];require(re.search(PATTERN,row['match_text'],re.I),'Reviewed anchor does not name/link the method: '+paper['paper_id']+' '+str(index))
   return dict(paragraph_index=index,paragraph_sha256=rawpin(row['text'].encode())['sha256'],section=row['section'])
  if paper['paper_id'] in EXCLUDED:
   indices,classification,summary=EXCLUDED[paper['paper_id']];excluded.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],classification=classification,use_summary=summary,source_pointers=[pointer(i) for i in indices],positive_record_retained=False));continue
  if paper['paper_id'] not in ROLES:continue
  indices,profiles,version,attribution,summary,remaining=ROLES[paper['paper_id']];old=next((t for t in paper['tools'] if t['tool_id']=='ldsc'),None);indices=list(indices)
  if old and old['evidence_paragraph_index'] not in indices:indices.append(old['evidence_paragraph_index'])
  primary_pointer=pointer(indices[0]);supporting=[pointer(i) for i in indices[1:]];match=re.search(PATTERN,by_index[indices[0]]['match_text'],re.I)
  context={'direct':'reported-direct-ldscore-analysis','wrapper':'reported-wrapper-specific-ldscore-method','reused-estimates':'reused-LDSC-derived-estimates-not-study-execution','annotation-resource':'reused-LDSC-annotations-not-study-execution'}[attribution]
  scope=NOTE+' Source-specific boundary: '+remaining
  use=dict(tool_id='ldsc',tool='LDSC',analysis_role='Reviewed LD-score method or explicitly reused resource',evidence_section=primary_pointer['section'],evidence_paragraph_index=primary_pointer['paragraph_index'],matched_text=match[0],extraction_score=old.get('extraction_score',0) if old else 0,usage_context=context,review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=version,evidence_selection='individually-reviewed-source-role',evidence_paragraph_sha256=primary_pointer['paragraph_sha256'],use_summary=summary,evidence_locator=paper['fulltext_url']+' (section: '+primary_pointer['section']+')',implementation_scope_note=scope,source_attribution=attribution,native_unpartitioned_profiles_applicable=profiles,whole_paper_reproduced=False,supporting_evidence=supporting)
  if version is not None:use['version_reporting_note']='Reported LDSC edition/source provenance; annotation and wrapper versions are separate. '+('Literal 1.0.11 is retained; no correction to 1.0.1 is made.' if paper['paper_id']=='P115' else 'Current selected native 3.0.2 core qualification does not establish this historical edition.')
  review={k:copy.deepcopy(use[k]) for k in ['tool_id','evidence_paragraph_sha256','use_summary','implementation_scope_note','usage_context','version_reported_near_name','supporting_evidence']};review.update(pmcid=paper['pmcid'],doi=paper['doi'])
  validate_supporting_evidence(rows,[review],{'ldsc':definition});roles.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],source_attribution=attribution,native_unpartitioned_profiles_applicable=profiles,reported_ldsc_edition=version,new_observation=use,new_review=review,source_pointers=[primary_pointer]+supporting,whole_paper_reproduced=False))
 require(len(identities)==250 and len(roles)==42 and len(excluded)==2,'Reviewed source population incomplete')
 require({x['paper_id'] for x in matches}==set(ROLES)|set(EXCLUDED),'Unreviewed broad-name candidate found; do not auto-admit')
 require(old_ids==set(ROLES)-{'P019','P092'}|{'P151'},'Predecessor LDSC population differs')
 return dict(status='PASS250_PRIMARY_IDENTITIES_AND44_REVIEWED_LDSC_CANDIDATES',primary_sources=identities,raw_name_candidates=matches,roles=roles,excluded=excluded,added_papers=['P019','P092'],removed_positive_papers=['P151'],positive_roles=42,reused_resource_roles=['P220','P245'],native_qualification=native_qualification(),whole_goal_complete=False)
def validate_census(census):
 require(census['status']=='PASS250_PRIMARY_IDENTITIES_AND44_REVIEWED_LDSC_CANDIDATES' and len(census['primary_sources'])==250 and len(census['roles'])==42 and {r['paper_id'] for r in census['roles']}==set(ROLES),'Incomplete source census')
 for role in census['roles']:
  indices,profiles,version,attribution,summary,remaining=ROLES[role['paper_id']];use=role['new_observation'];require(role['native_unpartitioned_profiles_applicable']==use['native_unpartitioned_profiles_applicable']==profiles and role['reported_ldsc_edition']==use['version_reported_near_name']==version and role['source_attribution']==use['source_attribution']==attribution and use['use_summary']==summary and use['implementation_scope_note']==NOTE+' Source-specific boundary: '+remaining,'Curated role/edition/profile changed')
  require(use['evidence_paragraph_index']==indices[0] and role['new_review']['evidence_paragraph_sha256']==use['evidence_paragraph_sha256'] and role['new_review']['use_summary']==summary and role['new_review']['implementation_scope_note']==use['implementation_scope_note'],'Reviewed anchor/summary binding changed')
 require({r['paper_id'] for r in census['excluded']}==set(EXCLUDED),'Negative decisions differ')
 require(census['native_qualification']==native_qualification(),'Scientific qualification/source binding differs')
def transition(before,census):
 require(set(before)==set(META),'Exactly eight predecessor metadata files required');validate_census(census)
 papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
 require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2694 and len(reviews['reviews'])==1219 and not any(r['tool_id']=='ldsc' for r in reviews['reviews']),'Predecessor catalog population differs')
 roles={r['paper_id']:r for r in census['roles']}
 for paper in papers:
  if paper['paper_id']=='P151':require(sum(t['tool_id']=='ldsc' for t in paper['tools'])==1,'Comparison-only predecessor record differs');paper['tools']=[t for t in paper['tools'] if t['tool_id']!='ldsc']
  elif paper['paper_id'] in roles:
   role=roles[paper['paper_id']];existing=[i for i,t in enumerate(paper['tools']) if t['tool_id']=='ldsc'];require(len(existing)==(paper['paper_id'] not in ['P019','P092']),'LDSC observation count differs')
   if existing:paper['tools'][existing[0]]=copy.deepcopy(role['new_observation'])
   else:paper['tools'].append(copy.deepcopy(role['new_observation']))
   reviews['reviews'].append(copy.deepcopy(role['new_review']))
 row=next(t for t in inventory if t['id']=='ldsc');require(row['implementation_status']=='method-overlap' and row['paper_count']==41,'LDSC predecessor status differs')
 row.update(pattern=PATTERN,paper_count=42,papers=sorted(ROLES),current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=NOTE,implementation_status='native-subset-validated',implementation_source=PROGRAM,implementation_scope=NOTE,validation_note='Selected unchanged-core reference: 72 unpartitioned Hsq models plus 72 cross-trait RG models; 21 focused tests on Windows and Linux; 43 packaged controls, 133 identical RG payloads and 96 prior-Hsq output comparisons. Original NA/NaN/range outcomes retained; 42 source roles separately reviewed. No historical author data or whole-tool parity.',source_roles_reviewed=42,source_role_coverage=Path(COVERAGE).name,source_build_required=True,released_binary_contains_workflow_adapter=False,public_CLI_available=True,native_profiles=['native-ldsc-hsq-cbiit302-v1','native-ldsc-rg-cbiit302-v1'],whole_tool_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1,native_family_credit_added=1,upstream_family_credit_added=0,current_documentation=GUIDE,paper_role_qualification={r['paper_id']:dict(source_attribution=r['source_attribution'],reported_ldsc_edition=r['reported_ldsc_edition'],native_unpartitioned_profiles_applicable=r['native_unpartitioned_profiles_applicable'],whole_paper_reproduced=False) for r in census['roles']})
 counts=dict(Counter(t['implementation_status'] for t in inventory));require(counts['native-subset-validated']==24 and counts['upstream-adapter-validated']==104 and counts['method-overlap']==46 and counts['missing']==260,'Only one native family may be admitted')
 summary.update(paper_tool_observations=2695,implementation_status_counts=counts);after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),('tools/literature/reviewed-uses.json',reviews)]:
  raw=encoded(value)
  if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
  after[name]=raw
 page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Predecessor embedded tables differ')
 payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
 for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
  require(text.count(PREVIOUS_NOTICE)==1,'Predecessor notice differs');after[name]=text.replace(PREVIOUS_NOTICE,NOTICE+'<details><summary>Previous Prism source-correction checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1).encode()
 for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
  after[name]=('# '+title+'\n\n## LDSC: reviewed roles and native unpartitioned scope\n\n'+CURRENT+'\n\n'+NOTE+'\n\n'+ACTION+'\n\nP019/P092 are added, P151 comparison-only is removed and P177 remains excluded. P220/P245 are resource reuse, not study execution. Read [the source crosswalk and current commands](../LDSC_CATALOG_INTEGRATION.md). No website deployment or release occurs in this metadata admission.\n\n<details><summary>Catalog before LDSC correction/admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
 return after
def check(before,after,census):
 require(after==transition(before,census),'Catalog differs from the exact reviewed transition');a,b=json.loads(before[META[0]]),json.loads(after[META[0]])
 for old,new in zip(a,b,strict=True):
  require({k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'},'Paper identity/window metadata changed')
  require([t for t in old['tools'] if t['tool_id']!='ldsc']==[t for t in new['tools'] if t['tool_id']!='ldsc'],'Unrelated observation/order changed')
 require([t for t in json.loads(before[META[1]]) if t['id']!='ldsc']==[t for t in json.loads(after[META[1]]) if t['id']!='ldsc'],'Unrelated inventory changed')
 old=json.loads(before['tools/literature/reviewed-uses.json']);new=json.loads(after['tools/literature/reviewed-uses.json']);require(new['reviews'][:1219]==old['reviews'] and len(new['reviews'])==1261,'Prior review/order changed or reviewed count inflated');require({k:v for k,v in old.items() if k!='reviews'}=={k:v for k,v in new.items() if k!='reviews'},'Review document metadata changed')
 old=json.loads(before[META[2]]);new=json.loads(after[META[2]]);require({k:v for k,v in old.items() if k not in ['paper_tool_observations','implementation_status_counts']}=={k:v for k,v in new.items() if k not in ['paper_tool_observations','implementation_status_counts']},'Window, goal or unrelated summary changed')
 counts=Counter(t['tool_id'] for p in b for t in p['tools']);inventory=json.loads(after[META[1]]);require(len(counts)==446 and sum(counts.values())==2695,'Derived observation population differs')
 for row in inventory:require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in b if any(t['tool_id']==row['id'] for t in p['tools'])],'Inventory frequency/IDs differ: '+row['id'])
 return dict(papers=250,named_tools=446,registry_definitions=485,observations=2695,reviewed_uses=1261,unreviewed_uses=1434,ldsc_roles=42,ldsc_candidates_reviewed=44,positive_observations_added=2,comparison_observations_removed=1,reviews_added=42,new_scoped_integrations=128,native_integrations=24,upstream_integrations=104,including_existing_adapters=131,missing=260,new_scoped_integration_credit=1,native_family_credit_added=1,whole_goal_complete=False)
def verify(cache,out):
 proof=json.loads((ROOT/PROOF).read_bytes());require(proof['status']=='PASS_LDSC_REVIEWED_SOURCE_AND_SCOPED_NATIVE_ADMISSION','Missing current admission');require(proof['source_code']=={n:pin(ROOT/n) for n in OWN},'Catalog verifier source changed; use its retained snapshot')
 with zipfile.ZipFile(ROOT/EVIDENCE/'catalog-before.zip') as z:require(len(z.namelist())==8 and set(z.namelist())==set(META),'Incomplete predecessor archive');before={n:z.read(n) for n in META}
 require(before==baseline(),'Archived predecessor differs from actual Git')
 for name,value in proof['evidence'].items():require(pin(ROOT/name)==value,'Admission evidence changed: '+name)
 census=primary(cache,json.loads(before[META[0]]));require(census==json.loads((ROOT/COVERAGE).read_bytes()),'Source recheck differs');after={n:(ROOT/n).read_bytes() for n in META};result=check(before,after,census)
 require(proof['before']=={n:rawpin(v) for n,v in before.items()} and proof['after']=={n:rawpin(v) for n,v in after.items()},'Recorded before/after pins differ')
 audit=manifest_audit();require(not audit['mismatches'] and not audit['missing_entries'] and not audit['extra_entries'],'Current catalog manifest differs/incomplete')
 (out/'COMPLETED.json').write_bytes(encoded(dict(status=proof['status'],**result,source_xml_hashes_checked=250,current_manifest=audit)));return result
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=p.add_mutually_exclusive_group(required=True);mode.add_argument('--plan',action='store_true');mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true');p.add_argument('--primary-cache',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=None;before=after=None;started=False
 try:
  require(__debug__,'Never disable source/catalog assertions');target=a.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Use NEW workspace build evidence output');target.mkdir(parents=True,exist_ok=False);out=target
  if a.verify:result=verify(a.primary_cache,out)
  else:
   before=baseline();require(all((ROOT/n).read_bytes()==v for n,v in before.items()),'Canonical metadata differs from the exact predecessor; preserve and review it');old_manifest=manifest_audit();require(not old_manifest['mismatches'] and not old_manifest['missing_entries'] and not old_manifest['extra_entries'],'Predecessor current manifest differs/incomplete')
   census=primary(a.primary_cache,json.loads(before[META[0]]));after=transition(before,census);result=check(before,after,census);(out/'PLAN.json').write_bytes(encoded(result));(out/'SOURCE_CENSUS.json').write_bytes(encoded(census))
   if a.apply:
    require(not (ROOT/EVIDENCE).exists() and all(not (ROOT/n).exists() for n in [PROOF,COVERAGE,COMPATIBILITY]),'Preserve existing admission evidence; never overwrite');evidence=ROOT/EVIDENCE;evidence.mkdir();(evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name,raw in before.items():z.writestr(name,raw)
    (evidence/'REGISTRY.json').write_bytes(encoded(registry()));(evidence/'PREVIOUS-SHA256SUMS').write_bytes((ROOT/DATA/'SHA256SUMS').read_bytes());(ROOT/COVERAGE).write_bytes(encoded(census))
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    name='tools/literature/reviewed-uses.json';change=delta(name,before[name],after[name]);require(reverse_delta(after[name],change)==before[name],'Review inverse differs');(ROOT/COMPATIBILITY).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='ldsc',scope='42 reviewed role appends; no scientific source/result overlay or historical-fingerprint refresh.',paths=[change])))
    (evidence/'WARNINGS.md').write_text('# Scoped native admission and required actions\n\n'+NOTE+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    with zipfile.ZipFile(evidence/'verifier-source.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
     for name in OWN:z.writestr(name,(ROOT/name).read_bytes())
    require(all((ROOT/n).read_bytes()==v for n,v in before.items()),'Metadata changed before admission');started=True
    for name,raw in after.items():(ROOT/name).write_bytes(raw)
    proof=dict(status='PASS_LDSC_REVIEWED_SOURCE_AND_SCOPED_NATIVE_ADMISSION',**result,base_commit=BASE,source_code={n:pin(ROOT/n) for n in OWN},before={n:rawpin(v) for n,v in before.items()},after={n:rawpin(v) for n,v in after.items()},evidence={p.relative_to(ROOT).as_posix():pin(p) for p in evidence.rglob('*') if p.is_file()},predecessor_current_manifest=old_manifest,scientific_engines_changed=False,historical_fingerprints_refreshed=False,earlier_sources_or_verifiers_overlaid=False,website_deployed=False)
    for name in [COVERAGE,COMPATIBILITY]:proof['evidence'][name]=pin(ROOT/name)
    (ROOT/PROOF).write_bytes(encoded(proof));refresh_current_manifest();require(all((ROOT/n).read_bytes()==v for n,v in after.items()),'Metadata changed after admission')
    audit=manifest_audit();require(not audit['mismatches'] and not audit['missing_entries'] and not audit['extra_entries'],'Current manifest recheck failed');(out/'COMPLETED.json').write_bytes(encoded(dict(status=proof['status'],**result,current_manifest=audit)))
   else:(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_REVIEWABLE_LDSC_SOURCE_AND_NATIVE_SCOPE_PLAN',**result)))
  print(json.dumps(result),flush=True);return 0
 except Exception:
  detail=traceback.format_exc();restored=[]
  if started:
   for name,raw in before.items():
    if (ROOT/name).read_bytes()==after[name]:(ROOT/name).write_bytes(raw);restored.append(name)
  code=3 if isinstance(sys.exception(),(FileNotFoundError,PermissionError)) else 2
  if out is not None:(out/'FAILURE.json').write_bytes(encoded(dict(status='SKIP3_DEPENDENT_ADMISSION_SKIPPED' if code==3 else 'FAIL2_NO_CATALOG_ADMISSION',detail=detail,restored_metadata=restored,required_action=ACTION)))
  print(('SKIP3: dependent admission skipped. ' if code==3 else 'FAIL2: no catalog admission. ')+detail+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
