# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""One LDpred2 family admission, five reviewed roles and no LDpred1 credit."""
import copy,json,re
from collections import Counter
from .lineage import ROOT,DATA,META,GUIDE,encoded
from ldpred2_primary.source import KNOWN,census
PROGRAM='src/main/java/org/jlinalg/cli/LdPred2WorkflowCli.java'
NOTE=('Native Java inf and complete serial grid; exact original bigsnpr 1.12.2 / selected 1.12.4 inf/grid/auto adapters with captured-input recomputation. '
      'Full CSC and aligned variant/allele/build/population/effective-N declarations are required; no matching, LD repair or sample-size conversion. '
      'Native inf/grid and selected original 1.12.4 auto have 1.1 million variant synthetic controls. Original auto uses 3 chains, 500 burn-in / 200 retained iterations and default MLE. '
      'Finite source outputs are not MCMC convergence: causal-fraction estimates differ and no chain pooling/selection is automatic. '
      'Source divergence, old failed input validation and metadata-only disease correction remain retained. Linux prerequisites are explicit; SKIP3 skips unavailable analysis, FAIL2 admits no complete collection. '
      'LDpred1, author environment, biological LD, prediction/tuning and full-paper reproduction are not qualified. P128/P205 method choices and other unreported author parameters must not be inferred.')
WARNING=('## LDpred2: scope and required actions\n\n'
 '**Native and original methods:** native Java inf/grid plus original bigsnpr1.12.2/selected1.12.4 auto are available through `ldpred2-tools`. LDpred1 is unsupported. Build current source; frozen v0.4.0 downloads lack the command.\n\n'
 '**Finite is not converged:** the large original-auto control retained3finite chains with materially different causal-fraction estimates. Inspect paths and establish stability/selection before interpreting or combining weights. No automatic pooling or biological validation is supplied.\n\n'
 '**Paper settings:** P074 uses actual estimated heritability times0.7/1/1.4; never infer actual h2=1.4. P109 reports1.12.4 but its author commit is unknown. P125 uses auto with UKB European LD; synthetic pair LD is not that resource. P128/P205 inf/grid/auto choices remain unresolved: skip paper-specific execution until resolved rather than choosing project defaults.\n\n'
 '**SKIP3 / FAIL2:** unavailable or forbidden prerequisites skip dependent analysis. Install only where allowed, otherwise leave it skipped. Other analysis, verification or recording failures admit no complete result. Retain raw inputs, failed runs and original objects; correct the named cause and retry NEW output. Never repair source nonfinite effects or refresh old fingerprints.\n\n'
 '**Current cohort:**250papers,446families,2691observations,1203reviewed uses and1488unreviewed. This adds one scoped native family:124integrations (22native/102upstream),263missing. Five LDpred2 source roles are reviewed; four reviews are new and one is updated. Whole-paper semantic completeness and the full implementation objective remain open.\n\n')
NOTICE='<section id="ldpred2-required-actions" role="note"><h2>LDpred2: scope and required actions</h2><p>'+NOTE+'</p><p>Inspect sampler paths before interpreting or pooling finite chains. Read <code>'+GUIDE+'</code> and <code>docs/literature/LDPRED2_AUTO_SCALE.md</code>. The full250-paper goal remains open.</p></section>'
STYLE='\n<style id="ldpred2-warning-wrap">#ldpred2-required-actions,#p074-disease-erratum{overflow-wrap:anywhere;min-width:0}#ldpred2-required-actions pre,#p074-disease-erratum pre{white-space:pre-wrap;overflow-wrap:anywhere}</style>\n'
def registry():
    rows=copy.deepcopy(json.loads((ROOT/'docs/literature/sambamba-catalog-integration/REGISTRY.json').read_bytes()));assert len(rows)==485
    row=next(r for r in rows if r['id']=='ldpred');row.update(name='LDpred2',role='LD-aware Bayesian PRS effect estimation',current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=NOTE)
    return rows
def transition(before):
    assert set(before)==set(META)
    inventory=json.loads(before[DATA+'/tool-inventory.json']);papers=json.loads(before[DATA+'/papers.json']);summary=json.loads(before[DATA+'/summary.json']);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
    assert len(inventory)==446 and len(papers)==250 and len(reviews['reviews'])==1199 and sum(len(p['tools']) for p in papers)==2691
    item=next(r for r in inventory if r['id']=='ldpred');assert item['implementation_status']=='missing' and set(item['papers'])==set(KNOWN)
    item.update(name='LDpred2',current_coverage='native-subset-validated',implementation_status='native-subset-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=NOTE,implementation_scope=NOTE,
        native_methods=['inf','grid'],upstream_methods=['inf','grid','auto'],native_auto_available=False,LDpred1_supported=False,root_dispatch_available=True,project_adapter_available=True,canonical_adapter_available=True,source_build_required=True,released_binary_contains_adapter=False,
        supported_original_editions=['1.12.2','1.12.4'],selected_1124_is_author_commit=False,author_environment_reproduced=False,whole_tool_qualified=False,whole_paper_reproduced=False,development_adapter_cli='java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --request /trusted/request.properties --out /NEW/output',
        native_sources=['src/main/java/org/jlinalg/genetics/'+n+'.java' for n in ['LdCorrelation','LdPred2','LdPred2Random','LdPred2Seeds']],current_documentation=GUIDE,validation_note='22numerical/workflow tests plus3dispatcher regressions;49native original numerical models;48original small-workflow models; full native1.1-million-variant controls and selected original1.12.4 auto3default700-iteration chains with complete-object comparison and genuine replay. Eight original small-source rejections retained. No chain convergence or biological validation claimed.',
        source_roles_reviewed=5,new_scoped_integration_credit=1,native_analytical_credit_added=1,upstream_family_credit_added=0,paper_role_qualification={pid:dict(method=r['method'],reported_version=r['version'],method_choice_established=not r['method'].startswith('unresolved'),author_parameters_established=False,whole_paper_reproduced=False) for pid,r in KNOWN.items()})
    current=census()['reviews'];assert len([r for r in reviews['reviews'] if r['tool_id']=='ldpred'])==1
    for review in current:
        paper=next(p for p in papers if p['paper_id']==review['paper_id']);observation=next(r for r in paper['tools'] if r['tool_id']=='ldpred');assert observation['evidence_paragraph_sha256']==review['evidence_paragraph_sha256']
        observation.update(tool='LDpred2',analysis_role='LD-aware Bayesian PRS effect estimation',use_summary=review['use_summary'],implementation_scope_note=review['implementation_scope_note'],usage_context=review['usage_context'],version_reported_near_name=review['version_reported_near_name'],method_reported=review['method_reported'],review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-primary-paragraph',supporting_evidence=review['supporting_evidence'],whole_paper_reproduced=False)
        old=next((r for r in reviews['reviews'] if r['tool_id']=='ldpred' and r['pmcid']==review['pmcid']),None)
        if old is None:reviews['reviews'].append(copy.deepcopy(review))
        else:old.update(review)
    counts=dict(Counter(r['implementation_status'] for r in inventory));assert counts['native-subset-validated']==22 and counts['upstream-adapter-validated']==102 and counts['missing']==263
    summary.update(implementation_status_counts=counts);assert len(reviews['reviews'])==1203
    after=dict(before)
    for name,value in [(DATA+'/tool-inventory.json',inventory),(DATA+'/papers.json',papers),(DATA+'/summary.json',summary),('tools/literature/reviewed-uses.json',reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);assert match
    assert json.loads(match[2])==dict(papers=json.loads(before[DATA+'/papers.json']),tools=json.loads(before[DATA+'/tool-inventory.json']),summary=json.loads(before[DATA+'/summary.json']))
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
    from sambamba_catalog.model import NOTICE as previous_notice
    for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
        assert text.count(previous_notice)==1
        text=text.replace(previous_notice,'<details><summary>Previous Sambamba checkpoint (historical)</summary>'+previous_notice+'</details>',1)
        anchor='<section id="p074-disease-erratum" role="note">';assert text.count(anchor)==1 and text.count('</head>')==1;text=text.replace(anchor,NOTICE+anchor,1).replace('</head>',STYLE+'</head>',1);after[name]=text.encode()
    for name in [DATA+'/README.md',DATA+'/WARNINGS.md']:
        intro=('# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'Read [LDpred2 scope and commands](../LDPRED2_CATALOG_INTEGRATION.md). Current cumulative verifier: `python -B tools/literature/ldpred2_catalog/catalog.py --verify --catalog-parent-checkout /trusted/clean-070ddea4 --previous-parent-checkout /trusted/clean-1a9ff82 --baseline-checkout /trusted/clean-357c9301 --out build/NEW-check`. Add `--rescan-sources` for every frozen primary XML. Old verifiers retain their original source and metadata scope.\n\n<details><summary>Checkpoint before LDpred2 admission (historical)</summary>\n\n').encode();after[name]=intro+before[name]+b'\n</details>\n'
    return after
def check(before,after):
    assert after==transition(before)
    a,b=json.loads(before[DATA+'/tool-inventory.json']),json.loads(after[DATA+'/tool-inventory.json']);assert [r for r in a if r['id']!='ldpred']==[r for r in b if r['id']!='ldpred']
    a,b=json.loads(before[DATA+'/papers.json']),json.loads(after[DATA+'/papers.json'])
    for old,new in zip(a,b):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [r for r in old['tools'] if r['tool_id']!='ldpred']==[r for r in new['tools'] if r['tool_id']!='ldpred']
    a,b=json.loads(before['tools/literature/reviewed-uses.json'])['reviews'],json.loads(after['tools/literature/reviewed-uses.json'])['reviews'];assert [r for r in a if r['tool_id']!='ldpred']==[r for r in b if r['tool_id']!='ldpred']
    return dict(papers=250,named_tools=446,observations=2691,reviewed_uses=1203,unreviewed_uses=1488,new_scoped_integrations=124,native_integrations=22,upstream_integrations=102,including_existing_adapters=127,missing=263,registry_definitions=485,added_reviews=4,updated_reviews=1,new_scoped_integration_credit=1,native_family_credit_added=1,upstream_family_credit_added=0,LDpred1_qualified=False,whole_goal_complete=False)
