# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Cumulative METAL admission with exact source-role and unrelated-record retention."""
import copy,json,re
from collections import Counter
from pathlib import Path
from ldak_catalog.model import META,DATA,NOTICE as PREVIOUS_NOTICE
from metal_catalog.primary import PREVIEW,ROOT,require
from metal_workflows.reviews import REVIEWS

GUIDE='docs/literature/METAL_CATALOG_INTEGRATION.md'
COVERAGE=DATA+'/metal-source-role-coverage.json'
PROGRAM='src/main/java/org/jlinalg/cli/MetalWorkflowCli.java'
NOTE=('Typed captured original METAL 2020-05-05 workflows: declared STDERR or SAMPLESIZE scheme, '
 'allelic or gene units, per-study and post-meta genomic control, overlap, heterogeneity and pooled frequencies. '
 'The 24 positive roles retain individual source settings and unresolved details. Seven explicit 2011 edition reports '
 'are not historical-runtime reproductions; a shared 2020 label does not establish the author binary or command. '
 'Independent native inverse-variance and signed-Z mathematics remain separately scoped. No whole-tool parity or paper reproduction is claimed.')
CURRENT=('250 papers, 446 named families, 2,695 paper-tool observations, 1,290 individually reviewed positive uses and 1,405 awaiting individual review. '
 'There are 130 new scoped integrations (24 native Java and 106 upstream), 259 missing definitions, 45 method-overlap definitions and '
 '133 integrations including three existing adapters. The frozen 2023-10-23..2026-10-23 cohort is unchanged. '
 'The full semantic review and implementation goal remains open.')
ACTION=('SKIP3: dependent analysis is skipped for a missing or forbidden permitted runtime, reference or platform. '
 'Supply the named prerequisite and retry NEW output, or leave that analysis skipped with its cause and recovery action. '
 'FAIL2: no complete result is admitted, including original errors with exit zero. Keep inputs, partials and diagnostics, '
 'correct the stated cause and retry NEW output. Require the workflow completion marker and successful verification. '
 'Never silently change identifiers, drop contributors, repair NaN/Inf or floor P values. '
 'Original METAL source, archives, binaries, notices and author examples remain private because redistribution is restricted. '
 'Public evidence views are not standalone admitted execution bundles. Keep complete private stores with their matching frozen reader; '
 'do not refresh old fingerprints. The public 0.4.0 release predates this source-build adapter.')
NOTICE=('<aside id="metal-catalog-notice" style="box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>METAL: qualified workflows and 24 source-specific roles</h2><p>'+CURRENT+'</p><p>'+NOTE+'</p><p><strong>Required actions:</strong> '+ACTION+'</p>'
 '<p>P076 is benchmark data preparation, P114 is gene-level CNV meta-analysis and P212 includes pooled-frequency support. '
 'P123 uses RE2C for overlap correction. Unknown schemes and author settings remain unknown. '
 '<a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Source crosswalk, commands and recovery steps</a>.</p></aside>')


def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()


def registry():
 from ldak_catalog.model import registry as parent
 rows=parent();require(len(rows)==485,'Cumulative registry population differs')
 row=next(t for t in rows if t['id']=='metal');require(row['current_coverage']=='method-overlap','METAL predecessor definition differs')
 row.update(role='Association and gene-level meta-analysis; pooled-frequency support',current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE)
 return rows


def validate_census(census,papers):
 require(census['status']=='PASS_ALL250_PRIMARY_IDENTITIES_24_INDIVIDUAL_METAL_ROLES_AND47_NAMED_PARAGRAPHS','Primary census status differs')
 require(census['papers_scanned']==250 and census['positive_roles']==24 and census['named_paragraphs']==47 and census['observations_added']==[] and census['observations_removed']==[] and census['whole_goal_complete'] is False,'Primary census scope differs')
 require(len(census['roles'])==24 and [r['paper_id'] for r in census['roles']]==[p['paper_id'] for p in papers if p['paper_id'] in REVIEWS],'Exactly the ordered 24 source roles are required')
 require(len(census['primary_files'])==250,'All primary identities required')
 for p,r in zip(papers,census['primary_files'],strict=True):
  require(all(r[k]==p[k] for k in ['paper_id','pmcid','doi','xml_sha256']) and isinstance(r['xml_bytes'],int) and r['xml_bytes']>0,'Primary identity differs: '+p['paper_id'])
 preview=json.loads((ROOT/PREVIEW).read_bytes());frozen={r['paper_id']:r for r in preview['reviews']}
 require(set(frozen)==set(REVIEWS),'Frozen role population differs')
 named=census['named_body_hits'];require(len(named)==24 and [r['paper_id'] for r in named]==[r['paper_id'] for r in census['roles']] and sum(len(r['paragraphs']) for r in named)==47,'Named paragraph population differs')
 for role in census['roles']:
  id=role['paper_id'];p=next(p for p in papers if p['paper_id']==id);uses=[t for t in p['tools'] if t['tool_id']=='metal'];require(len(uses)==1,'Require one existing METAL observation: '+id)
  old=uses[0];indices,edition,kind,summary,caution=REVIEWS[id];record=frozen[id];anchors=record['selected_primary_evidence'];first=anchors[0]
  require([r['paragraph_index'] for r in anchors]==indices and (record['role'],record['use_summary'],record['edition_literal'],record['required_caution'])==(kind,summary,edition,caution),'Source decisions differ from frozen reviewed preview: '+id)
  expected=dict(paper_id=id,pmcid=p['pmcid'],doi=p['doi'],fulltext_url=p['fulltext_url'],xml_sha256=p['xml_sha256'],role=kind,use_summary=summary,reported_edition_literal=edition,unresolved=caution,anchors=anchors,anchor_index=first['paragraph_index'],anchor_section=first['section'],anchor_sha256=first['paragraph_sha256'],previous_anchor_index=old['evidence_paragraph_index'],previous_anchor_sha256=old['evidence_paragraph_sha256'],positive_use=True,usage_context='current-study-comparative-benchmark-data-preparation' if id=='P076' else 'current-study-allele-frequency-support' if id=='P212' else 'current-study-analysis',selected_project_profile='2020-05-05',reported_historical_edition_not_reproduced=edition is not None and '2011' in edition,exact_author_settings_established=False,whole_paper_reproduced=False)
  require(role==expected,'Individual source role, edition, settings or anchor changed: '+id)
  hits=next(n['paragraphs'] for n in named if n['paper_id']==id)
  require(all(any(all(hit[k]==anchor[k] for k in ['paragraph_index','section','paragraph_sha256']) for hit in hits) for anchor in anchors),'Selected evidence absent from named census: '+id)


def transition(before,census):
 require(set(before)==set(META),'Exactly eight predecessor metadata files required')
 papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
 require(len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2695 and len(reviews['reviews'])==1266,'Current predecessor population differs')
 require(not any(r['tool_id']=='metal' for r in reviews['reviews']),'METAL reviews already exist; preserve them and use a separate transition')
 validate_census(census,papers);ids=[r['paper_id'] for r in census['roles']];item=next(t for t in inventory if t['id']=='metal')
 require(item['implementation_status']=='method-overlap' and item['paper_count']==24 and item['papers']==ids,'METAL predecessor inventory differs')
 qualifications={}
 for role in census['roles']:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);use=next(t for t in paper['tools'] if t['tool_id']=='metal');pointers=copy.deepcopy(role['anchors'][1:]);scope=NOTE+' Source-specific boundary: '+role['unresolved']
  analysis_role={'P076':'Comparative benchmark GWAS summary preparation','P114':'Gene-level CNV meta-analysis','P203':'eQTL meta-analysis','P212':'Pooled allele-frequency support in meta-analysis'}.get(role['paper_id'],'Association meta-analysis')
  use.update(analysis_role=analysis_role,evidence_section=role['anchor_section'],evidence_paragraph_index=role['anchor_index'],evidence_paragraph_sha256=role['anchor_sha256'],evidence_locator=role['fulltext_url']+' (section: '+role['anchor_section']+')',usage_context=role['usage_context'],review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-source-role',use_summary=role['use_summary'],implementation_scope_note=scope,version_reported_near_name=role['reported_edition_literal'],reviewed_reported_engine_version=role['reported_edition_literal'],supporting_evidence=pointers,selected_project_profile='2020-05-05',reported_historical_edition_not_reproduced=role['reported_historical_edition_not_reproduced'],exact_author_settings_established=False,whole_paper_reproduced=False)
  reviews['reviews'].append(dict(pmcid=role['pmcid'],doi=role['doi'],tool_id='metal',evidence_paragraph_sha256=role['anchor_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=role['usage_context'],version_reported_near_name=role['reported_edition_literal'],supporting_evidence=copy.deepcopy(pointers)))
  qualifications[role['paper_id']]={k:copy.deepcopy(role[k]) for k in ['role','usage_context','reported_edition_literal','selected_project_profile','reported_historical_edition_not_reproduced','exact_author_settings_established','whole_paper_reproduced']}
 definition=next(t for t in registry() if t['id']=='metal')
 item.update(role=definition['role'],current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE,implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,implementation_scope=NOTE,validation_note='Original 2020-05-05 qualification: 17 packaged tests on each of Windows and Linux; 13 successful runtime profiles, 28 complete bundles and 37 admitted original stages. Current-source audit rechecks public payloads and complete private closure with the matching frozen Java reader. No exact author workflow or whole-paper reproduction.',source_roles_reviewed=24,positive_roles_reviewed=24,excluded_context_roles=0,source_role_coverage=COVERAGE.split('/')[-1],selected_project_editions=['2020-05-05'],source_build_required=True,released_binary_contains_workflow_adapter=False,public_CLI_available=True,whole_tool_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,current_documentation=GUIDE,paper_role_qualification=qualifications)
 counts=dict(Counter(t['implementation_status'] for t in inventory));require(counts['upstream-adapter-validated']==106 and counts['native-subset-validated']==24 and counts['missing']==259 and counts['method-overlap']==45,'Only one upstream family may be admitted');summary['implementation_status_counts']=counts;after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),('tools/literature/reviewed-uses.json',reviews)]:
  raw=encoded(value)
  if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
  after[name]=raw
 page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 require(match is not None and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Predecessor embedded data differs')
 payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
 for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
  require(text.count(PREVIOUS_NOTICE)==1,'Predecessor LDAK notice differs');after[name]=text.replace(PREVIOUS_NOTICE,NOTICE+'<details><summary>Previous LDAK catalog checkpoint (historical)</summary>'+PREVIOUS_NOTICE+'</details>',1).encode()
 for name,title in [(META[3],'250-paper omics and GWAS toolbox catalog'),(META[4],'Current literature warnings and required actions')]:
  after[name]=('# '+title+'\n\n## METAL: qualified workflows and individually reviewed roles\n\n'+CURRENT+'\n\n'+NOTE+'\n\n'+ACTION+'\n\nRead [the METAL source crosswalk and workflow guide](../METAL_CATALOG_INTEGRATION.md). Twenty-four positive uses are individually reviewed; unreported schemes and author settings remain unknown. No observation is added or removed. No deployment or release is part of this admission.\n\n<details><summary>Catalog before METAL admission (historical)</summary>\n\n'+before[name].decode()+'\n</details>\n').encode()
 return after


def check(before,after,census):
 require(after==transition(before,census),'Catalog differs from the exact source-reviewed METAL transition')
 old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
 for a,b in zip(old,new,strict=True):
  require({k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'},'Paper identity or fixed cohort changed')
  require([t for t in a['tools'] if t['tool_id']!='metal']==[t for t in b['tools'] if t['tool_id']!='metal'],'Unrelated observation or order changed')
 require([t for t in json.loads(before[META[1]]) if t['id']!='metal']==[t for t in json.loads(after[META[1]]) if t['id']!='metal'],'Unrelated inventory changed')
 a,b=json.loads(before['tools/literature/reviewed-uses.json']),json.loads(after['tools/literature/reviewed-uses.json']);require(b['reviews'][:1266]==a['reviews'] and len(b['reviews'])==1290,'Prior reviews/order changed or new credit inflated');require({k:v for k,v in a.items() if k!='reviews'}=={k:v for k,v in b.items() if k!='reviews'},'Review metadata changed')
 a,b=json.loads(before[META[2]]),json.loads(after[META[2]]);require({k:v for k,v in a.items() if k!='implementation_status_counts'}=={k:v for k,v in b.items() if k!='implementation_status_counts'},'Summary cohort or unrelated fields changed')
 counts=Counter(t['tool_id'] for p in new for t in p['tools']);require(len(counts)==446 and sum(counts.values())==2695,'Derived observation population differs')
 for row in json.loads(after[META[1]]):require(row['paper_count']==counts[row['id']] and row['papers']==[p['paper_id'] for p in new if any(t['tool_id']==row['id'] for t in p['tools'])],'Derived inventory membership differs: '+row['id'])
 return dict(papers=250,named_tools=446,registry_definitions=485,observations=2695,reviewed_uses=1290,unreviewed_uses=1405,positive_metal_roles=24,historical_2011_editions_not_reproduced=7,observations_added=[],observations_removed=[],new_scoped_integrations=130,native_integrations=24,upstream_integrations=106,including_existing_adapters=133,missing=259,method_overlap=45,new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,whole_goal_complete=False)
