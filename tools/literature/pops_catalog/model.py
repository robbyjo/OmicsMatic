# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reviewable PoPS-only catalog transition; no mutation or integration credit on import."""
import copy,json,re
from collections import Counter
from .primary import ROOT,PATTERN,census

DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+name for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
PROGRAM='src/main/java/org/jlinalg/cli/PopsWorkflowCli.java'
GUIDE='docs/literature/POPS_CATALOG_INTEGRATION.md'
NOTE=('Native Java PoPS 0.2 ridge/OLS with complete prepared-input workflows; explicit unchanged original PoPS 0.1 selection/chromosome prediction and 0.2 ridge/OLS/LassoCV/munging adapters. '
      'Selected source is not the author commit or environment. Original execution requires a separately installed/adopted Linux x86-64 runtime; no silent edition/method fallback. '
      'All 22 autosomes, full official 18383-gene/508-feature examples, actual source convergence refusal, complete captured-input replay and retained failure controls are covered. '
      'Explicit ConvergenceWarning means FAIL2 even with finite coefficients; missing permitted prerequisites mean SKIP3 and dependent analysis skipped. '
      'Original 0.1 single-selected-feature failure and empty-selection control-only results are preserved. Munging/selection alone are not fitted models. '
      'Six source roles distinguish three reported computations from benchmarking and published-score reuse. Score-release provenance, author parameters, LD/reference preparation, downstream ranks/enrichment and whole-paper reproduction remain separate and unqualified. '
      'Build current source; released v0.4.0 lacks the commands. This is one scoped native family credit with supplemental original adapters, not multiple tool credits or whole-tool equivalence.')
WARNING=('## PoPS: scope, warnings and required actions\n\n'
         '**Available:** native Java PoPS 0.2 ridge/OLS (`pops-tools`) and explicit original 0.1/0.2 workflows (`pops-original-tools`). Build current source; released v0.4.0 lacks these commands. Read [scope and commands](../POPS_CATALOG_INTEGRATION.md).\n\n'
         '**SKIP3 / FAIL2:** install dependencies only where permitted. Missing/forbidden prerequisites skip dependent analysis; supply the named permitted setup or leave it skipped. Other failures admit no complete result. Retain raw reports, inputs and diagnostics; correct the named cause and retry NEW output. Never silently substitute versions/methods, thin data, repair nonfinite values or refresh old fingerprints.\n\n'
         '**Scientific limitations:** source convergence warnings reject finite fits. Original 0.1 single-feature failure is retained; empty selection may give a control-only model. Munging/selection are preparation, not fitted models. Published-score reuse in P220/P236 and benchmarking in P153 do not establish new PoPS fits or software versions. Unreported author settings must remain unresolved.\n\n'
         '**Current cohort:** 250 papers, 446 families, 2692 observations, 1209 reviewed uses and 1483 unreviewed. One scoped native PoPS family gives 125 integrations (23 native/102 upstream), 262 missing; 128 including three existing adapters. The mixed-case P236 observation is added and P239 membrane-lipid POPS is excluded. Whole-paper semantic completeness and the full implementation goal remain open.\n\n')
NOTICE='<section id="pops-required-actions" role="note"><h2>PoPS: scope and required actions</h2><p>'+NOTE+'</p><p>Three papers report computation; three use scores for benchmarking or published-result comparison. Author-specific reproduction remains unqualified. The full 250-paper implementation goal remains open.</p></section>'
STYLE='\n<style id="pops-warning-wrap">#pops-required-actions{overflow-wrap:anywhere;min-width:0}#pops-required-actions pre{white-space:pre-wrap;overflow-wrap:anywhere}</style>\n'
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def registry():
    rows=copy.deepcopy(json.loads((ROOT/'docs/literature/ldpred2-catalog-integration/REGISTRY.json').read_bytes()));assert len(rows)==485
    item=next(r for r in rows if r['id']=='pops');item.update(pattern=PATTERN,current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=NOTE)
    return rows
def transition(before,primary=None):
    assert set(before)==set(META);primary=census() if primary is None else primary
    inventory=json.loads(before[DATA+'/tool-inventory.json']);papers=json.loads(before[DATA+'/papers.json']);summary=json.loads(before[DATA+'/summary.json']);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
    assert len(inventory)==446 and len(papers)==250 and len(reviews['reviews'])==1203 and sum(len(p['tools']) for p in papers)==2691
    assert not any(r['tool_id']=='pops' for r in reviews['reviews'])
    item=next(r for r in inventory if r['id']=='pops');assert item['implementation_status']=='missing' and item['paper_count']==5
    item.update(pattern=PATTERN,current_coverage='native-subset-validated',implementation_status='native-subset-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=NOTE,implementation_scope=NOTE,paper_count=6,papers=primary['software_papers'],native_methods=['ridge','linreg'],native_edition='0.2',native_lasso_available=False,native_01_available=False,original_editions=['0.1','0.2'],original_methods=['0.1-select','0.1-predict','0.1-select-predict','0.2-ridge','0.2-linreg','0.2-lasso','0.2-munge'],root_dispatch_available=True,project_adapter_available=True,canonical_adapter_available=True,source_build_required=True,released_binary_contains_adapter=False,whole_tool_qualified=False,whole_paper_reproduced=False,author_environment_reproduced=False,new_scoped_integration_credit=1,native_analytical_credit_added=1,upstream_family_credit_added=0,current_documentation=GUIDE,source_roles_reviewed=6,validation_note='Native 219 Windows/Linux controls and complete tutorial parity; original 270 Windows regressions/32 Linux contracts, 17 successful original profile controls plus two expected failures, all 22 legacy chromosomes, full official fitting/munging, 42 admitted task bundles and 13 genuine replay families. Real 35-warning finite source fit is rejected. No biological or author-setting reproduction claimed.',paper_role_qualification={r['paper_id']:dict(reported_version=r['version_reported_near_name'],fresh_estimation_reported=r['fresh_estimation_reported'],usage_context=r['usage_context'],author_options_established=False,whole_paper_reproduced=False) for r in primary['reviews']})
    for review in primary['reviews']:
        paper=next(p for p in papers if p['paper_id']==review['paper_id']);observations=[t for t in paper['tools'] if t['tool_id']=='pops'];assert len(observations)==(0 if paper['paper_id']=='P236' else 1)
        if observations:paper['tools'][paper['tools'].index(observations[0])]=copy.deepcopy(primary['observations'][paper['paper_id']])
        else:paper['tools'].append(copy.deepcopy(primary['observations'][paper['paper_id']]));paper['tools'].sort(key=lambda r:r['tool_id'])
        reviews['reviews'].append(copy.deepcopy(review))
    counts=dict(Counter(r['implementation_status'] for r in inventory));assert counts['native-subset-validated']==23 and counts['upstream-adapter-validated']==102 and counts['missing']==262
    summary.update(paper_tool_observations=2692,implementation_status_counts=counts);assert len(reviews['reviews'])==1209 and sum(len(p['tools']) for p in papers)==2692
    after=dict(before)
    for name,value in [(DATA+'/tool-inventory.json',inventory),(DATA+'/papers.json',papers),(DATA+'/summary.json',summary),('tools/literature/reviewed-uses.json',reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);assert match
    assert json.loads(match[2])==dict(papers=json.loads(before[DATA+'/papers.json']),tools=json.loads(before[DATA+'/tool-inventory.json']),summary=json.loads(before[DATA+'/summary.json']))
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    from ldpred2_catalog.model import NOTICE as previous
    for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
        assert text.count(previous)==1 and text.count('</head>')==1;text=text.replace(previous,NOTICE+'<details><summary>Previous LDpred2 checkpoint (historical)</summary>'+previous+'</details>',1).replace('</head>',STYLE+'</head>',1);after[name]=text.encode()
    for name in [DATA+'/README.md',DATA+'/WARNINGS.md']:
        after[name]=('# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'<details><summary>Checkpoint before PoPS admission (historical)</summary>\n\n').encode()+before[name]+b'\n</details>\n'
    return after
def check(before,after,primary=None):
    assert after==transition(before,primary)
    a,b=json.loads(before[DATA+'/tool-inventory.json']),json.loads(after[DATA+'/tool-inventory.json']);assert [r for r in a if r['id']!='pops']==[r for r in b if r['id']!='pops']
    a,b=json.loads(before[DATA+'/papers.json']),json.loads(after[DATA+'/papers.json'])
    for old,new in zip(a,b):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [r for r in old['tools'] if r['tool_id']!='pops']==[r for r in new['tools'] if r['tool_id']!='pops']
    assert [r for r in json.loads(before['tools/literature/reviewed-uses.json'])['reviews'] if r['tool_id']!='pops']==[r for r in json.loads(after['tools/literature/reviewed-uses.json'])['reviews'] if r['tool_id']!='pops']
    return dict(papers=250,named_tools=446,observations=2692,reviewed_uses=1209,unreviewed_uses=1483,new_scoped_integrations=125,native_integrations=23,upstream_integrations=102,including_existing_adapters=128,missing=262,registry_definitions=485,added_reviews=6,added_observations=1,changed_primary_locator_papers=['P090'],rejected_lipid_name_papers=['P239'],new_scoped_integration_credit=1,native_family_credit_added=1,upstream_family_credit_added=0,whole_goal_complete=False)
