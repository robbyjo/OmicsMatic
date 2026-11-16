# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One scoped original-runtime integration and three source-specific role reviews."""
from collections import Counter
import copy,html,json,re
from sambamba_catalog.source import ROOT,DATA,PAPERS,REVIEWS,KNOWN,reviews
from catalog_source_transition_v2.source import META,pin,rawpin
from tpmcalculator_catalog.model import NOTICE as PRIOR_NOTICE

MODULE='tools/literature/sambamba_catalog';GUIDE='docs/literature/SAMBAMBA_CATALOG_INTEGRATION.md';EVIDENCE='docs/literature/sambamba-catalog-integration'
PROOF=DATA+'/sambamba-catalog-validation.json';COMPATIBILITY=DATA+'/sambamba-reviews-shared-source-compatibility.json'
INVENTORY=DATA+'/tool-inventory.json';SUMMARY=DATA+'/summary.json';README=DATA+'/README.md';WARNINGS=DATA+'/WARNINGS.md'
PROGRAM='src/main/java/org/jlinalg/cli/SambambaStreamingWorkflowCli.java';DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
NOTE=('Build current source for original Sambamba 0.6.6/0.8.2 Linux marking/removal with all 20 request fields, complete captured BAM auditing and genuine original runtime replay. '
      'Source-compatible operation controls do not recover author flags, resource settings, binary builds or whole-paper environments. '
      'P082 lists 0.6.5; its operation is unresolved and that edition has no validated adapter profile. Never substitute 0.6.6. '
      'Original 0.6.6 score-overflow runs remain unaccepted; affected original index metadata must not be repaired or promoted silently. '
      'Select a separate audited native index or a separately qualified edition/tool for a new analysis, or skip the affected analysis. '
      'BAM/BAI qualification is per exact pair; arbitrary downstream consumers and biological validity remain unqualified.')
WARNING='''## Sambamba: current scope, warnings and required actions

This source catalog contains 123 new scoped integrations (21 native, 102 upstream), 126 including existing adapters, with 264 missing families. The fixed 250-paper corpus now has 446 families, 2691 observations and 1199 reviewed uses; 1492 uses and paper-wide semantic completeness remain open. One missed declaration is added; no unrelated review or scientific output is replaced.

**Build current source.** The published v0.4.0 assets are frozen and do not contain this new command. Run `./gradlew executableJar` from this source checkout, then use `java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --help`. Supply the exact permitted Linux runtime and an explicit request; use genuine `--verify` replay before accepting a run. A metadata readback is not fresh scientific computation or genuine runtime replay.

**Unsupported declared edition:** P082 lists 0.6.5; its operation is unresolved. Do not substitute 0.6.6. Skip that paper-specific analysis until its operation and a matching profile are validated.

**Original 0.6.6 failures:** retain score-overflow failures and inconsistent original index metadata. No affected original analysis/index is accepted. Rebuild a separate index with native Java and audit the exact pair; explicitly select it downstream. For overflowing reads, use a separately qualified permitted edition/tool for a new analysis or skip. Do not trim reads, change qualities or silently replace a sidecar.

**SKIP3 / FAIL2:** a missing or forbidden prerequisite skips dependent analysis until the named permitted setup is supplied. Any analysis, verification or recording failure admits no complete result. Preserve inputs, failures and raw consoles; correct the named cause and retry NEW output paths. Native Windows skips the original Linux analysis profile. All preceding exclusions, licenses and source restrictions remain applicable, including the user's Cell Ranger/ARC exclusion. Whole-tool, author-environment, biological and whole-paper validity remain unqualified; the full 250-paper objective remains active.
'''
def paragraph(text):
    text=html.escape(text);text=re.sub(r'\*\*(.*?)\*\*',r'<strong>\1</strong>',text)
    return re.sub(r'`([^`]+)`',r'<code>\1</code>',text)
NOTICE='<section id="sambamba-required-actions" role="note"><h2>Sambamba: scope and required actions</h2>'+''.join('<p>'+paragraph(part)+'</p>' for part in WARNING.strip().split('\n\n')[1:])+'<p>Guide: <code>docs/literature/SAMBAMBA_CATALOG_INTEGRATION.md</code>. Preserve all original failure records.</p></section>'
STYLE='\n<style id="sambamba-warning-wrap">#sambamba-required-actions{overflow-wrap:anywhere;min-width:0}#sambamba-required-actions pre{white-space:pre-wrap;overflow-wrap:anywhere}</style>\n'
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def qualified_entry(root,old):
    row=copy.deepcopy(old);row.update(role='Alignment processing and duplicate marking/removal',current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',
        existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note='Exact original0.6.6/0.8.2 mark/remove profiles; reported0.6.5 remains unsupported and operation unresolved.',implementation_scope=NOTE,
        paper_count=3,papers=['P082','P110','P198'],validation_note='Complete original-runtime/replay controls, four-million-record64-MiB runs, adversarial failures and51 BAM/index pairs with30070 independent mapped queries. Original metadata/score failures retained; no independent duplicate classifier or biological validation.',
        project_adapter_available=True,canonical_adapter_available=True,root_dispatch_available=True,source_build_required=True,released_binary_contains_adapter=False,
        development_candidate_available=True,development_candidate=PROGRAM,development_candidate_kind='Java-wrapper-of-exact-original-Linux-runtime-with-genuine-replay',development_candidate_sha256=pin(root/PROGRAM)['sha256'],
        development_build='./gradlew executableJar',development_adapter_cli='java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --request /trusted/request.properties --out /NEW/output',
        supported_original_editions=['0.6.6','0.8.2'],unsupported_reported_editions=['0.6.5'],reported_versions_by_paper={key:role['version'] for key,role in KNOWN.items()},
        paper_role_coverage={key:role['summary']+(' Unsupported declared edition; operation unresolved. Skip dependent analysis; do not substitute 0.6.6.' if key=='P082' else ' Exact author flags, executable build and whole-paper environment remain unestablished.') for key,role in KNOWN.items()},
        paper_role_qualification={key:dict(use_summary=role['summary'],operation_established=key!='P082',reported_edition_supported=role['supported'],whole_paper_reproduced=False) for key,role in KNOWN.items()},
        selected_environment_is_author_environment=False,exact_author_binary_build_established=False,author_options_established=False,native_analytical_credit_added=0,new_scoped_integration_credit=1,source_roles_reviewed=3,
        workflow_validation='../sambamba-streaming-workflow-development/VALIDATION.json',index_validation='../bai-query-qualification/QUALIFICATION.json',current_documentation=GUIDE,whole_tool_qualified=False,whole_paper_reproduced=False)
    return row
def transition(root,before):
    if set(before)!=set(META):raise ValueError('Eight complete predecessor metadata files required')
    inventory=json.loads(before[INVENTORY]);papers=json.loads(before[PAPERS]);summary=json.loads(before[SUMMARY]);document=json.loads(before[REVIEWS])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(446,250,2690,1197):raise ValueError('Exact complete predecessor population required')
    old=next(row for row in inventory if row['id']=='sambamba')
    if old['implementation_status']!='missing' or old['papers']!=['P110','P198']:raise ValueError('Exact missing Sambamba predecessor required')
    inventory[inventory.index(old)]=qualified_entry(root,old);current_reviews=reviews(root)
    existing=[row for row in document['reviews'] if row['tool_id']=='sambamba']
    if len(existing)!=1 or existing[0]['pmcid']!=KNOWN['P110']['pmcid']:raise ValueError('Exact original P110-only review required')
    for review in current_reviews:
        paper_id=review['paper_id'];role=KNOWN[paper_id];paper=next(p for p in papers if p['paper_id']==paper_id)
        selected=[row for row in paper['tools'] if row['tool_id']=='sambamba']
        if paper_id=='P082':
            if selected:raise ValueError('The missed P082 declaration must not overwrite an existing observation')
            anchor=next(row for row in paper['tools'] if row['tool_id']=='deseq2')
            if anchor['evidence_paragraph_sha256']!=role['evidence_paragraph_sha256'] or anchor['evidence_paragraph_index']!=64:raise ValueError('Exact already-located Code availability paragraph required')
            observation=dict(tool_id='sambamba',tool='Sambamba',analysis_role=role['role'],matched_text='Sambamba',extraction_score=0)
        else:
            if len(selected)!=1 or selected[0]['evidence_paragraph_sha256']!=role['evidence_paragraph_sha256']:raise ValueError('Exact original method observation required')
            observation=copy.deepcopy(selected[0])
        scope=NOTE if role['supported'] else 'Declared0.6.5 is unsupported; operation unresolved. No paper-specific analysis or edition substitution is admitted.'
        review['implementation_scope_note']=scope
        observation.update(analysis_role=role['role'],evidence_section=role['section'],evidence_paragraph_index=role['index'],usage_context=role['context'],
            evidence_selection='individually-reviewed-primary-paragraph',evidence_paragraph_sha256=role['evidence_paragraph_sha256'],evidence_locator=paper['fulltext_url']+' (section: '+role['section']+')',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open',version_reported_near_name=role['version'],use_summary=role['summary'],implementation_scope_note=scope,
            reported_edition_has_validated_project_profile=role['supported'],author_operation_established=paper_id!='P082',whole_paper_reproduced=False)
        if selected:paper['tools'][paper['tools'].index(selected[0])]=observation
        else:paper['tools'].append(observation)
        previous=[row for row in document['reviews'] if row['tool_id']=='sambamba' and row['pmcid']==review['pmcid']]
        if previous:
            updated=copy.deepcopy(previous[0]);updated.update(review);document['reviews'][document['reviews'].index(previous[0])]=updated
        else:document['reviews'].append(review)
    updated_summary=copy.deepcopy(summary);updated_summary.update(distinct_named_tools=len(inventory),paper_tool_observations=sum(len(p['tools']) for p in papers),implementation_status_counts=dict(Counter(row['implementation_status'] for row in inventory)))
    counts=updated_summary['implementation_status_counts']
    if (updated_summary['paper_tool_observations'],len(document['reviews']),counts['missing'],counts['upstream-adapter-validated'],counts['native-subset-validated'])!=(2691,1199,264,102,21):raise ValueError('Exact scoped populations differ')
    page,warning=before['site/literature-review.html'].decode(),before['site/literature-warnings.html'].decode()
    match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[INVENTORY]),summary=summary):raise ValueError('Complete embedded predecessor catalog differs')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=updated_summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    historical='<details><summary>Previous TPMCalculator checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>';marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]) or page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Exact preceding warning panels required')
    page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
    warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
    intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [Sambamba commands, scope and recovery](../SAMBAMBA_CATALOG_INTEGRATION.md). Current cumulative verifier: `python -B tools/literature/sambamba_catalog/catalog.py --verify --parent-checkout /trusted/clean-1a9ff82 --baseline-checkout /trusted/clean-357c9301 --out build/NEW-catalog-check`. The original gates stay unchanged; use the separately reviewed source transition and the required retained primary cache. Add `--rescan-sources` for the full frozen primary corpus.\n\n'
    paper_bytes=encoded(papers)
    if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_bytes=paper_bytes.replace(b'\n',b'\r\n')
    return {INVENTORY:encoded(inventory),SUMMARY:encoded(updated_summary),PAPERS:paper_bytes,REVIEWS:encoded(document),
        README:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n').encode()+before[README]+b'\n</details>\n',
        WARNINGS:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[WARNINGS]+b'\n</details>\n',
        'site/literature-review.html':page.encode(),'site/literature-warnings.html':warning.encode()}
def check_transition(root,before,after):
    if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Exact scoped Sambamba and source-role transition differs')
    old_inv,new_inv=json.loads(before[INVENTORY]),json.loads(after[INVENTORY])
    if [row for row in old_inv if row['id']!='sambamba']!=[row for row in new_inv if row['id']!='sambamba']:raise ValueError('Unrelated tool definition changed')
    old_papers,new_papers=json.loads(before[PAPERS]),json.loads(after[PAPERS])
    for old,new in zip(old_papers,new_papers):
        if {key:value for key,value in old.items() if key!='tools'}!={key:value for key,value in new.items() if key!='tools'}:raise ValueError('Unrelated paper metadata changed')
        if [row for row in old['tools'] if row['tool_id']!='sambamba']!=[row for row in new['tools'] if row['tool_id']!='sambamba']:raise ValueError('Unrelated paper observation changed')
    old_reviews=json.loads(before[REVIEWS])['reviews'];new_reviews=json.loads(after[REVIEWS])['reviews']
    if [row for row in old_reviews if row['tool_id']!='sambamba']!=[row for row in new_reviews if row['tool_id']!='sambamba']:raise ValueError('Unrelated review changed')
    return dict(papers=250,named_tools=446,observations=2691,reviewed_uses=1199,unreviewed_uses=1492,new_scoped_integrations=123,native_integrations=21,upstream_integrations=102,
        including_existing_adapters=126,missing=264,registry_definitions=485,added_observations=1,added_reviews=2,updated_reviews=1,new_registry_definitions=0,
        new_scoped_integration_credit=1,upstream_family_credit_added=1,native_family_credit_added=0,P082_operation_qualified=False,P082_declared_edition_supported=False,whole_goal_complete=False)
