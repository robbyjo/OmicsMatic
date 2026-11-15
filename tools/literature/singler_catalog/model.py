# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""A deterministic seven-review transition; preserve every unrelated catalog fact."""
from collections import Counter
import copy,json,re
from hicat109_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph
from singler_catalog.source import MODULE,PROGRAM,NOTE,definitions,reviews

GUIDE='docs/literature/SINGLER_CATALOG_INTEGRATION.md'
EVIDENCE='docs/literature/singler-catalog-integration'
PROOF=DATA+'/singler-catalog-validation.json'
COMPATIBILITY=DATA+'/singler-reviews-shared-source-compatibility.json'
WARNING='''
## SingleR: warnings and required actions

**250 papers, 441 named software families, 2,682 observations and 1,187 reviewed uses; 1,495 uses await review.** The fixed 2023-10-23 through 2026-10-23 cohort remains 71 Nature, 104 Nature Genetics and 75 AJHG papers. The full semantic review and implementation goal remains open.

**118 scoped new integrations: 20 native Java subsets and 98 original upstream workflows; 121 including three existing adapters. 264 named families remain missing.** SingleR adds one upstream family across five explicitly selected releases. Four existing paper uses receive source-specific readings. Three newly identified P051 families, celldex, SPOTlight and the BD Rhapsody analysis pipeline, remain missing and receive no implementation credit. Similar company names, an ordinary verb, instrument-only descriptions and ImmGen biological references add no software credit.

**P233 literally reports SingleR 2.41; its identity is unresolved.** Requesting 2.41 produces SKIP3 and skips dependent analysis. Obtain original source/version clarification; do not normalize it to 2.4.1 or substitute another release. Elsewhere the papers report different SingleR releases and references for different analyses. Use each source-specific reading; do not borrow settings across contexts. Project comparison environments are not recovered author environments.

**SKIP3 skips dependent analysis** for missing or forbidden exact software, reference inputs, Linux execution, installation or downloads. Read the named requirement, supply that permitted requirement and retry NEW paths. The installer requires explicit permission flags; analysis installs and downloads nothing. Supply original query/reference objects, labels and intended caller settings. A skip is not an annotation or empty biological result.

**FAIL2 admits no complete result**, including original source errors, native crashes and recording failure after an original return. Preserve originals and partials. Read WARNINGS.md, FAILURE.json, original R-status.json and all raw consoles. Resolve the stated input/filesystem cause and retry NEW paths. Only a final public-runner COMPLETED.json admits the recorded analysis; a leftover RDS or successful R exit is insufficient. Original warnings, IDs, NA/NaN/Inf, conditions and RNG remain intact.

The qualified synthetic comparisons include 250 original controls: 232 complete returns and 18 original errors. Original errors remain errors, including unsupported arguments, label/gene mismatches and the tested custom-assay failure. Read the release-specific warning table before analysis. The first 1.2.4 dependency binary crashed even on finite row medians and remains unqualified; the selected profile rebuilds the same unchanged dependency release. Saved training pointers are records, not reusable live indexes. Whole-tool validity, biological accuracy and paper reproduction remain unqualified. The integration guide gives commands, tested scope, complete records and recovery steps. Cell Ranger/ARC remain excluded by user request; earlier source/license/runtime restrictions remain in their guides and historical notices.
'''
QUICKSTART='''<details id="singler-runtime-guide"><summary>SingleR commands and recovery guide</summary>
<p>Use a permitted Linux/WSL environment, an existing permitted Pixi executable and NEW paths. Installation needs explicit flags. For offline installation, omit --allow-download and supply both complete --cache-dir and exact --source-cache inputs. If a requirement is forbidden or unavailable, skip dependent analysis.</p>
<pre>python3 -B tools/literature/singler_workflows/install_runtime.py --edition 2.8.0 --manager /permitted/pixi --allow-install --allow-download --cache-dir /permitted/cache --out /private/NEW-install</pre>
<p>Use the exact selected runtime to create a trusted request RDS containing original query/reference expression objects, their labels and intended RNG/caller settings. The repository guide provides the complete R constructor. Analysis installs and downloads nothing.</p>
<pre>python3 -B tools/literature/singler_workflows/run.py --edition 2.8.0 --runtime /private/NEW-install-provider --request /trusted/request.rds --out /results/NEW-annotation</pre>
<p>Read original/complete-original-return.rds, complete conditions/RNG and raw consoles. Only final COMPLETED.json admits the public analysis. For SKIP3 supply the named exact permitted requirement; for FAIL2 resolve the stated input/filesystem cause. Preserve all original/partial evidence and retry NEW paths. Literal 2.41 requires source clarification; another release does not resolve it.</p>
<p>For full commands, release-specific source readings, tested scope, original warning/error boundaries and fresh-replay instructions, open repository document <code>docs/literature/SINGLER_CATALOG_INTEGRATION.md</code>. Synthetic controls establish no whole-tool validity or paper reproduction.</p>
</details>'''
NOTICE='<section id="singler-required-actions" role="note"><h2>SingleR: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+QUICKSTART+'</section>'
STYLE='\n<style id="singler-warning-wrap">#singler-required-actions { overflow-wrap: anywhere; min-width: 0; } #singler-required-actions pre { white-space: pre-wrap; overflow-wrap: anywhere; }</style>\n'

def entry(root,old):
    if old['id']!='singler' or old['implementation_status']!='missing' or old['papers']!=['P051','P082','P192','P233']:raise ValueError('Exactly four existing missing SingleR observations required')
    row=copy.deepcopy(old)
    row.update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
      coverage_note='Complete original annotation in five explicit source releases; literal 2.41 unresolved; source-specific references and original errors retained.',
      implementation_scope=NOTE,validation_note='250 paired original controls/232 returns/18 original errors; whole returns, conditions and RNG. 17 Linux public guards,3 Windows prerequisite guards,both actual installs and whole installed analyses; frozen readback and fresh replay. Synthetic scope only.',
      project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
      development_candidate_available=True,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],
      development_candidate_kind='python-source-entrypoint-original-Linux-WSL-source-built-R-releases',development_build='tools/literature/singler_workflows/install_runtime.py',
      development_adapter_cli='python3 '+PROGRAM+' --edition 2.8.0 --runtime /permitted/provider --request /trusted/request.rds --out /NEW/output',
      selected_upstream_versions=['1.2.4','1.8.1','2.2.0','2.6.0','2.8.0'],selected_R_versions=['4.0.5','4.1.3','4.3.3','4.3.3','4.4.3'],
      selected_environment_is_author_environment=False,author_reference_snapshots_established=False,literal_2_41_resolved=False,
      workflow_validation='../singler-workflow-validation/QUALIFICATION.json',current_documentation=GUIDE,source_roles_reviewed=4,
      paper_role_coverage={r['paper_id']:r['use_summary']+' '+NOTE for r in reviews(root) if r['tool_id']=='singler'},
      uncovered_roles=['Literal 2.41 release identity','Actual author reference objects, label vectors, caller settings and environments','Original failed inputs admit no result','Whole-tool validity, biological decisions and paper reproduction'],
      new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False)
    return row

def transition(root,before):
    if set(before)!=set(META):raise ValueError('Exactly eight complete predecessor metadata inputs required')
    inventory,summary=json.loads(before[META[0]]),json.loads(before[META[1]])
    papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(438,250,2679,1180):raise ValueError('Complete preceding populations differ')
    rows=reviews(root);newdefs=definitions(root);newids={d['id'] for d in newdefs}
    if any(r['tool_id']=='singler' or r['tool_id'] in newids for r in document['reviews']):raise ValueError('Duplicate source review credit')
    for i,row in enumerate(inventory):
        if row['id']=='singler':inventory[i]=entry(root,row)
    if newids.intersection(r['id'] for r in inventory):raise ValueError('New families already exist')
    for definition in newdefs:
        inventory.append(dict(definition,paper_count=1,papers=['P051'],implementation_status='missing',implementation_source=None,
          implementation_scope=definition['coverage_note'],validation_note='Source-reviewed use only; no runtime analysis or implementation credit.'))
    document['reviews']+=copy.deepcopy(rows)
    for review in rows:
        paper=next(p for p in papers if p['paper_id']==review['paper_id'])
        found=[t for t in paper['tools'] if t['tool_id']==review['tool_id']]
        if review['tool_id']=='singler':
            if len(found)!=1:raise ValueError('One existing annotation observation required')
            observation=found[0]
            if observation['evidence_paragraph_sha256']!=review['evidence_paragraph_sha256']:raise ValueError('Existing primary anchor differs')
        else:
            if found:raise ValueError('Duplicate new observation')
            definition=next(d for d in newdefs if d['id']==review['tool_id']);anchor=review['supporting_evidence'][0]
            observation=dict(tool_id=definition['id'],tool=definition['name'],analysis_role=definition['role'],evidence_section=anchor['section'],
              evidence_paragraph_index=anchor['paragraph_index'],matched_text=definition['name'],extraction_score=15,
              usage_context='analysis-method-description',evidence_selection='individually-reviewed-primary-method-anchor',evidence_paragraph_sha256=anchor['paragraph_sha256'],
              evidence_locator=paper['fulltext_url']+' (section: '+anchor['section']+')')
            paper['tools'].append(observation)
        for key in ['use_summary','implementation_scope_note','supporting_evidence','version_reported_near_name']:
            observation[key]=copy.deepcopy(review[key])
        observation['review_status']='usage-summary-reviewed; paper-wide-completeness-review-open'
    updated=copy.deepcopy(summary)
    updated.update(distinct_named_tools=len(inventory),paper_tool_observations=sum(len(p['tools']) for p in papers),implementation_status_counts=dict(Counter(r['implementation_status'] for r in inventory)))
    counts=updated['implementation_status_counts']
    if (counts['missing'],counts['upstream-adapter-validated'],counts['native-subset-validated'])!=(264,98,20):raise ValueError('One upstream credit/three missing families required')
    page,warning=before[META[4]].decode(),before[META[5]].decode()
    match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Complete preceding embedded catalog differs')
    page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
    marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    historical='<details><summary>Previous HICAT checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
    if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]):raise ValueError('Exact preceding warning panel required')
    if page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Expected warning insertion points required')
    page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
    warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
    intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [SingleR commands, source roles and recovery](../SINGLER_CATALOG_INTEGRATION.md). Current cumulative entry point: `python tools/literature/singler_catalog/catalog.py --verify` with a NEW output path. Fresh scientific replay is documented separately. The full goal remains open.\n\n'
    paperbytes=encoded(papers)
    if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paperbytes=paperbytes.replace(b'\n',b'\r\n')
    return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:paperbytes,REVIEWS:encoded(document),
      META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
      META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
    if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Catalog differs from exact source-bound SingleR transition')
    papers=json.loads(after[PAPERS]);inventory=json.loads(after[META[0]]);document=json.loads(after[REVIEWS])
    return dict(papers=len(papers),named_tools=len(inventory),observations=sum(len(p['tools']) for p in papers),reviewed_uses=len(document['reviews']),
      unreviewed_uses=sum(len(p['tools']) for p in papers)-len(document['reviews']),new_scoped_integrations=118,missing=264,
      added_reviews=7,added_observations=3,enriched_existing_observations=4,new_registry_definitions=3,
      new_scoped_integration_credit=1,native_family_credit_added=0,whole_goal_complete=False)
