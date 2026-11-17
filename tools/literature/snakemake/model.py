# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pure, Snakemake-only transition of the frozen 250-paper metadata."""
import copy,json,re
from collections import Counter
from .primary import ROOT,ROLES

DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+name for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
GUIDE='docs/literature/SNAKEMAKE_WORKFLOWS.md'
PROGRAM='src/main/java/org/jlinalg/cli/SnakemakeToolsCli.java'
NOTE=('Original Snakemake 9.27.1 local workflow execution, complete captured workflow/data/configuration, declared target/output contracts, independent directory/ZIP verification and genuine replay. '
      'Selected preinstalled, Conda 26.9.4, Apptainer 1.5.5 and combined deployment; explicit installation permission, immutable borrowed caches and owned shadow staging. '
      'Native Java benchmark aggregation sums recorded cpu_time and takes maximum max_rss for explicit pipeline/replicate assignments. '
      'Selected versions do not establish author versions or environments. Cloud provisioning, remote executors, downstream biological tools and whole-paper reproduction remain separate. '
      'Missing/forbidden prerequisites mean SKIP3 and dependent analysis skipped; FAIL2 admits no complete result. Retain diagnostics and retry NEW output after resolving the named cause.')
WARNING=('**SKIP3 / FAIL2:** install only where permitted. Missing/forbidden runtime, provider, cache or filesystem prerequisites skip the dependent analysis; restore the named permitted prerequisite or leave it skipped. '
         'Other failures admit no complete result. Preserve source inputs, raw outputs, original diagnostics, invalidated markers and every failed attempt; correct the discrepancy and retry NEW output. '
         'Never substitute editions, relocate an installed Conda prefix or refresh historical hashes. Verification establishes captured consistency, not authenticity or biological accuracy.')
CURRENT=('250 papers, 446 families, 2693 observations, 1214 reviewed uses and 1479 unreviewed. '
         'Adding one scoped Snakemake upstream family brings the total to 126 new integrations (23 native/103 upstream), 261 missing; 129 including three existing adapters. '
         'P190 is pipeline-code availability, not evidence that its reported analyses used that pipeline. Full semantic review and implementation remain open.')
NOTICE=('<aside class="snakemake-catalog-notice"><h2>Snakemake: selected local workflows and benchmark reports</h2><p>'+CURRENT+'</p>'
        '<p>Build current source; released v0.4.0 lacks the workflow commands. Selected Snakemake 9.27.1 is not an established author version. '
        'Cloud provisioning, downstream biological methods and full-paper reproduction remain separate. '
        '<a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, scope and recovery steps</a>.</p>'
        '<p><strong>SKIP3:</strong> skip dependent analysis when a permitted prerequisite is missing or installation is forbidden. '
        '<strong>FAIL2:</strong> admit no complete result. Preserve all inputs, outputs and failed attempts, resolve the named cause and retry a new output directory.</p></aside>')
STYLE='<style>.snakemake-catalog-notice{box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;border-radius:.5rem;background:#fff8e8;color:#302718;overflow-wrap:anywhere}.snakemake-catalog-notice h2{margin-top:0}.snakemake-catalog-notice p:last-child{margin-bottom:0}</style>'

def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def registry():
    from pops_catalog.model import registry as previous
    result=previous();assert len(result)==485
    item=next(row for row in result if row['id']=='snakemake');assert item['current_coverage']=='missing';item.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE)
    return result
def transition(before,census):
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before['tools/literature/reviewed-uses.json']);old=next(row for row in inventory if row['id']=='snakemake');assert old['implementation_status']=='missing' and old['paper_count']==5
    assert len(reviews['reviews'])==1209 and sum(len(p['tools']) for p in papers)==2692 and len(census['roles'])==6
    reviews['reviews']=[row for row in reviews['reviews'] if row['tool_id']!='snakemake']
    for role in census['roles']:
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);assert paper['pmcid']==role['pmcid'] and paper['xml_sha256']==role['xml_sha256'];context='pipeline-code-availability' if role['paper_id']=='P190' else 'benchmark-or-comparison' if role['paper_id']=='P181' else 'workflow-orchestration'
        scope=NOTE+' '+role['unresolved'];review=dict(pmcid=role['pmcid'],doi=role['doi'],tool_id='snakemake',evidence_paragraph_sha256=role['anchor_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=context)
        reviews['reviews'].append(review);found=[row for row in paper['tools'] if row['tool_id']=='snakemake'];assert len(found)==(0 if role['paper_id']=='P190' else 1)
        item=found[0] if found else dict(tool_id='snakemake',tool='Snakemake',analysis_role='Analysis workflow execution',evidence_section=role['anchor_section'],evidence_paragraph_index=role['anchor_index'],matched_text='snakemake',extraction_score=0,version_reported_near_name=None)
        assert item['version_reported_near_name'] is None
        item.update(usage_context=context,review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-paragraph',evidence_paragraph_sha256=role['anchor_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,evidence_locator=role['fulltext_url']+' (section: '+role['anchor_section']+')')
        if not found:paper['tools'].append(item);paper['tools'].sort(key=lambda row:row['tool_id'])
    old.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE,paper_count=6,papers=sorted(ROLES),implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,implementation_scope=NOTE,validation_note='Actual packaged public commands; selected local original-engine executions, genuine input-independent replay, original-deployer permissions, portable semantic damage controls, terminal-failure invalidation and complete source/data/software/output records. Synthetic controls establish software behavior, not paper biology.',selected_project_edition='9.27.1',source_build_required=True,released_binary_contains_workflow_adapter=False,whole_tool_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,current_documentation=GUIDE,paper_role_qualification={r['paper_id']:dict(engine_execution_established=r['engine_execution_established'],engine_version_established=False,selected_edition_is_author_runtime=False,whole_paper_reproduced=False) for r in census['roles']})
    counts=dict(Counter(row['implementation_status'] for row in inventory));assert counts['upstream-adapter-validated']==103 and counts['native-subset-validated']==23 and counts['missing']==261
    summary.update(paper_tool_observations=2693,implementation_status_counts=counts)
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),('tools/literature/reviewed-uses.json',reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);assert match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]]));payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    from pops_catalog.model import NOTICE as previous
    for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
        assert text.count(previous)==1 and text.count('</head>')==1;text=text.replace(previous,NOTICE+'<details><summary>Previous PoPS checkpoint (historical)</summary>'+previous+'</details>',1).replace('</head>',STYLE+'</head>',1);after[name]=text.encode()
    for name in [META[3],META[4]]:
        heading='# 250-paper omics and GWAS toolbox catalog\n\n## Snakemake: scope, warnings and required actions\n\n' if name==META[3] else '# Current literature warnings and required actions\n\n## Snakemake: scoped local workflows\n\n'
        block=heading+'**Available from current source:** original local `snakemake-tools run`, `verify` and `replay`, plus the separate native `benchmark` component. Released v0.4.0 lacks the new workflow commands. Read [commands and scope](../SNAKEMAKE_WORKFLOWS.md).\n\n'+WARNING+'\n\n**Current cohort:** '+CURRENT+'\n\n<details><summary>Checkpoint before Snakemake admission (historical)</summary>\n\n'
        after[name]=(block+before[name].decode()+'\n</details>\n').encode()
    return after
def check(before,after,census):
    assert after==transition(before,census)
    a,b=json.loads(before[META[0]]),json.loads(after[META[0]])
    for old,new in zip(a,b):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [r for r in old['tools'] if r['tool_id']!='snakemake']==[r for r in new['tools'] if r['tool_id']!='snakemake']
    assert [r for r in json.loads(before[META[1]]) if r['id']!='snakemake']==[r for r in json.loads(after[META[1]]) if r['id']!='snakemake']
    assert [r for r in json.loads(before['tools/literature/reviewed-uses.json'])['reviews'] if r['tool_id']!='snakemake']==[r for r in json.loads(after['tools/literature/reviewed-uses.json'])['reviews'] if r['tool_id']!='snakemake']
    assert len(json.loads(after['tools/literature/reviewed-uses.json'])['reviews'])==1214 and sum(len(p['tools']) for p in b)==2693
    return dict(papers=250,named_tools=446,observations=2693,reviewed_uses=1214,unreviewed_uses=1479,new_scoped_integrations=126,native_integrations=23,upstream_integrations=103,including_existing_adapters=129,missing=261,registry_definitions=485,reviewed_snakemake_roles=6,added_observations=['P190'],new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,whole_goal_complete=False)
