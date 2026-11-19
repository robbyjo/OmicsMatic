# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Forward-only LDAK admission on the current cumulative LDSC catalog."""
import copy,json,re
from collections import Counter
from ldak_workflows.model import NOTE as WORKFLOW_NOTE
from ldak_workflows.primary import EXPECTED,encoded
from ldsc_hsq.catalog import META,DATA,NOTICE as PREVIOUS_NOTICE

GUIDE='docs/literature/LDAK_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/ldak-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/LdakWorkflowCli.java'
POSITIVE={'P090','P109','P120','P171','P238'}
NOTE=(WORKFLOW_NOTE+' Five positive source roles are distinct from the excluded P245 background/prior-work context. '
 'The P238 benchmark does not establish its LDAK edition, model or command. See LDAK_CATALOG_INTEGRATION.md.')
CURRENT=('250 papers, 446 named families, 2,695 paper-tool observations, 1,266 individually reviewed positive uses and 1,429 awaiting individual review. '
 'There are 129 new scoped integrations (24 native Java and 105 upstream), 259 missing families and 132 integrations including three existing adapters. '
 'The frozen 2023-10-23..2026-10-23 cohort is unchanged. The full semantic review and implementation goal remains open.')
ACTION=('SKIP3: dependent analysis is skipped for a missing or forbidden permitted runtime, reference or Linux x86_64 execution environment. Supply the named prerequisite and retry NEW output. '
 'FAIL2: no complete result is admitted, including original errors with exit zero. Read FAILURE.txt, WARNINGS.md, DIAGNOSTICS.tsv and both original consoles; retain inputs and partials, correct the named cause and retry NEW output. '
 'Require exit 0, COMPLETED.properties and successful verification. Original SumHer tutorial substitutions (18 zero statistics and four tagging floors) require explicit allow-recorded or are refused under forbid; they are not silent native repairs. '
 'Keep earlier bundles with matching readers and never refresh old fingerprints. Author binaries, reference snapshots and unreported settings are not inferred. '
 'Legacy SnpEff evidence/software retains its separately recorded private academic noncommercial and redistribution restrictions.')
NOTICE=('<aside id="ldak-catalog-notice" style="box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere"><h2>LDAK: qualified workflows and corrected source roles</h2><p>'+CURRENT+'</p><p>'+NOTE+'</p><p><strong>Required actions:</strong> '+ACTION+'</p><p>P238 is added as an under-specified current-study benchmark; P245 is removed from positive uses and retained as prior-work/context evidence. No paper reproduction is claimed. <a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Source decisions, current commands and recovery steps</a>.</p></aside>')

def require(ok,why):
 if not ok:raise ValueError(why)

def registry():
 from ldsc_hsq.catalog import registry as parent
 rows=parent();require(len(rows)==485,'Cumulative registry population differs');row=next(r for r in rows if r['id']=='ldak');require(row['current_coverage']=='missing','Predecessor LDAK definition differs')
 row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE);return rows

def remaining(role):
 if role['paper_id']=='P238':return ('No LDAK edition, model, command or comparison inputs are established. The main Methods paragraph specifies GCTA REML. The added LDAK benchmark observation does not supply its exact author workflow.')
 return role['unresolved']

def transition(before,census):
 require(set(before)==set(META),'Exactly eight current predecessor metadata files required');require(len(census['roles'])==6 and {r['paper_id'] for r in census['roles']}==set(EXPECTED),'Six reviewed source candidates required')
 papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
 require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2695 and len(reviews['reviews'])==1261,'Current predecessor population differs')
 require(not any(r['tool_id']=='ldak' for r in reviews['reviews']),'Preserve existing LDAK reviews; an additional transition is required')
 item=next(t for t in inventory if t['id']=='ldak');require(item['implementation_status']=='missing' and item['paper_count']==5 and set(item['papers'])==set(EXPECTED)-{'P238'},'Current LDAK predecessor differs')
 qualifications={}
 for role in census['roles']:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[t for t in paper['tools'] if t['tool_id']=='ldak'];require(paper['xml_sha256']==role['xml_sha256'],'Paper identity differs')
  if role['paper_id']=='P245':
   require(len(uses)==1 and role['usage_context']=='background-or-previous-work-not-current-ldak-execution','Excluded context differs');paper['tools']=[t for t in paper['tools'] if t['tool_id']!='ldak'];continue
  require(role['paper_id'] in POSITIVE,'Unreviewed positive source role')
  if role['paper_id']=='P238':require(not uses and role['new_observation'] is not None,'New benchmark observation differs');use=copy.deepcopy(role['new_observation']);paper['tools'].insert(next((i for i,t in enumerate(paper['tools']) if t['tool_id']>'ldak'),len(paper['tools'])),use)
  else:require(len(uses)==1,'Existing positive LDAK observation differs');use=uses[0]
  require(use['evidence_paragraph_sha256']==role['anchor_sha256'],'Primary evidence anchor differs')
  pointers=[dict(paragraph_index=a['paragraph_index'],paragraph_sha256=a['paragraph_sha256'],section=a['section']) for a in role['anchors'] if a['named_tool_mention'] and a['paragraph_index']!=role['anchor_index']]
  context=[dict(paragraph_index=a['paragraph_index'],paragraph_sha256=a['paragraph_sha256'],section=a['section']) for a in role['anchors'] if not a['named_tool_mention']]
  scope=NOTE+' Source-specific boundary: '+remaining(role)
  use.update(usage_context=role['usage_context'],review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-source-role',use_summary=role['use_summary'],implementation_scope_note=scope,version_reported_near_name=role['reported_engine_version'],reviewed_reported_engine_version=role['reported_engine_version'],evidence_locator=role['fulltext_url']+' (section: '+role['anchor_section']+')',supporting_evidence=pointers,reviewed_method_context=context,whole_paper_reproduced=False)
  review=dict(pmcid=role['pmcid'],doi=role['doi'],tool_id='ldak',evidence_paragraph_sha256=role['anchor_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=role['usage_context'],version_reported_near_name=role['reported_engine_version'],supporting_evidence=copy.deepcopy(pointers));reviews['reviews'].append(review)
  qualifications[role['paper_id']]=dict(usage_context=role['usage_context'],reported_engine_version=role['reported_engine_version'],selected_project_profile='5.2',selected_runtime_is_author_binary=False,exact_author_settings_established=False,benchmark_method_under_specified=role['paper_id']=='P238',whole_paper_reproduced=False)
 item.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE,paper_count=5,papers=sorted(POSITIVE),implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,implementation_scope=NOTE,validation_note='Retained original 5.2 qualification: 58 Java tests, 92 workflow/example/refusal controls, 23 complete bundles and 48 exact method-class fingerprints. The current source-build JAR matches all 48 method classes, verifies all 23 closed bundles and genuinely replays both tutorial modes. Native FP64 kinships and declared liability conversion are checked; no author study reproduction.',source_roles_reviewed=6,positive_roles_reviewed=5,excluded_context_roles=1,source_role_coverage=COVERAGE.split('/')[-1],selected_project_editions=['5.2'],source_build_required=True,released_binary_contains_workflow_adapter=False,public_CLI_available=True,whole_tool_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,current_documentation=GUIDE,paper_role_qualification=qualifications)
 counts=dict(Counter(t['implementation_status'] for t in inventory));require(counts['upstream-adapter-validated']==105 and counts['native-subset-validated']==24 and counts['missing']==259 and counts['method-overlap']==46,'Only one upstream LDAK family may be admitted');summary.update(paper_tool_observations=2695,implementation_status_counts=counts);after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),('tools/literature/reviewed-uses.json',reviews)]:
  raw=encoded(value)
  if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
  after[name]=raw
 page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Predecessor embedded tables differ')
 payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
 for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
  require(text.count(PREVIOUS_NOTICE)==1,'Predecessor LDSC admission notice differs');after[name]=text.replace(PREVIOUS_NOTICE,NOTICE+'<details><summary>Previous LDSC catalog checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1).encode()
 for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
  after[name]=('# '+title+'\n\n## LDAK: qualified workflows and corrected source roles\n\n'+CURRENT+'\n\n'+NOTE+'\n\n'+ACTION+'\n\nP238 is added as an under-specified current-study comparator; P245 is excluded from positive uses and retained in the source census as background/prior work. Five positive roles and one negative/context decision are individually reviewed. Read [the source crosswalk and current workflow guide](../LDAK_CATALOG_INTEGRATION.md). No website deployment or release is part of this admission.\n\n<details><summary>Catalog before LDAK admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
 return after

def check(before,after,census):
 require(after==transition(before,census),'Catalog differs from the exact source-reviewed transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
 for a,b in zip(old,new,strict=True):
  require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Paper identities or fixed cohort changed');require([t for t in a['tools'] if t['tool_id']!='ldak']==[t for t in b['tools'] if t['tool_id']!='ldak'],'Unrelated observations/order changed')
 require([t for t in json.loads(before[META[1]]) if t['id']!='ldak']==[t for t in json.loads(after[META[1]]) if t['id']!='ldak'],'Unrelated inventory changed');a=json.loads(before['tools/literature/reviewed-uses.json']);b=json.loads(after['tools/literature/reviewed-uses.json']);require(b['reviews'][:1261]==a['reviews'] and len(b['reviews'])==1266,'Prior reviews/order changed or new reviews inflated');require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Review metadata changed')
 a=json.loads(before[META[2]]);b=json.loads(after[META[2]]);require({k:v for k,v in a.items() if k!='implementation_status_counts'}=={k:v for k,v in b.items() if k!='implementation_status_counts'},'Summary cohort or unrelated fields changed');counts=Counter(t['tool_id'] for p in new for t in p['tools']);require(len(counts)==446 and sum(counts.values())==2695,'Derived observation population differs')
 for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in new if any(t['tool_id']==row['id'] for t in p['tools'])],'Derived inventory membership differs: '+row['id'])
 return dict(papers=250,named_tools=446,registry_definitions=485,observations=2695,reviewed_uses=1266,unreviewed_uses=1429,ldak_candidates_reviewed=6,positive_ldak_roles=5,excluded_context_roles=1,added_positive_observations=['P238'],removed_context_observations=['P245'],new_scoped_integrations=129,native_integrations=24,upstream_integrations=105,including_existing_adapters=132,missing=259,new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,whole_goal_complete=False)
