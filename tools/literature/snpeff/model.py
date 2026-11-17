# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""SnpEff-only transformation of the frozen catalog; preserve every other family."""
import json,re
from collections import Counter
from .primary import EXPECTED,encoded

DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+name for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
GUIDE='docs/literature/SNPEFF_WORKFLOWS.md'
PROGRAM='src/main/java/org/jlinalg/cli/SnpEffWorkflowCli.java'
NOTE=('Original SnpEff 5.2 VCF annotation and selected unmodified 3.1i-r354 source-built TXT annotation, with native Java record/format/diagnostic audits, complete captured runtime/reference/input, independent directory verification and genuine replay. '
      'Selected original/default settings do not establish author binaries, configs or database snapshots. Every ANN allele/transcript and original legacy TXT representation remains available; no most-severe transcript, pathogenicity or burden class is inferred. '
      'Legacy runtime/evidence is restricted to permitted private academic noncommercial use; bundled Flanagan redistribution permissions are separate. '
      'Missing/forbidden prerequisites mean SKIP3 and dependent analysis skipped. FAIL2 admits no complete result, including zero-exit original errors. Preserve diagnostics, resolve the named cause and retry NEW output.')
CURRENT=('250 papers, 446 families, 2693 observations, 1218 reviewed uses and 1475 unreviewed. '
         'One scoped SnpEff upstream family brings the total to 127 new integrations (23 native/104 upstream), 260 missing; 130 including three existing adapters. '
         'Full semantic review and implementation remain open.')
WARNING=('**SKIP3:** skip dependent analysis when the exact permitted runtime/reference is missing or installation is forbidden; supply it and retry NEW output. '
         '**FAIL2:** admit no complete result. Original exit0 is insufficient: retain and inspect `FAILURE.txt`, observed warning counts, `diagnostics.tsv` and `original.stderr`; correct the named cause without repairing bundles or refreshing historical hashes. '
         '**Legacy restrictions:** private academic noncommercial use; do not redistribute legacy runtimes or containing evidence without required permissions. '
         '**Observed human-reference diagnostics:** 1,871 annotation rows retained original transcript/realignment diagnostics; zero ERROR rows. Review transcript warnings before downstream interpretation. '
         'The selected legacy build is not the authors\' original binary, and whole-paper reproduction is not established.')
NOTICE=('<aside class="snpeff-catalog-notice"><h2>SnpEff: original annotation and source-built legacy support</h2><p>'+CURRENT+'</p>'
        '<p>Build current source; released v0.4.0 lacks these commands. Selected 3.1i-r354 is not the author binary. Exact wheat/Ensembl85 settings, transcript-severity rules and downstream biology remain separate. '
        '<a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, reference scope and recovery steps</a>.</p>'
        '<p><strong>SKIP3:</strong> skip analysis needing unavailable/forbidden prerequisites. <strong>FAIL2:</strong> admit no complete result, even after an original exit 0. '
        'Retain diagnostics, resolve the named cause and retry a new output directory. <strong>Legacy software/evidence:</strong> private academic noncommercial use; check redistribution permissions.</p>'
        '<p>The human-reference control retained 1,871 diagnostic annotation rows. Review original transcript warnings before downstream interpretation; no pathogenicity or burden classes are inferred.</p></aside>')
STYLE='<style>.snpeff-catalog-notice{box-sizing:border-box;max-width:100%;margin:1rem 0;padding:1rem;border:2px solid #a56613;border-radius:.5rem;background:#fff8e8;color:#302718;overflow-wrap:anywhere}.snpeff-catalog-notice h2{margin-top:0}.snpeff-catalog-notice p:last-child{margin-bottom:0}</style>'

def registry():
    from snakemake.model import registry as previous
    result=previous();assert len(result)==485
    item=next(row for row in result if row['id']=='snpeff');assert item['current_coverage']=='missing'
    item.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE)
    return result

def transition(before,census):
    papers=json.loads(before[META[0]]);inventory=json.loads(before[META[1]]);summary=json.loads(before[META[2]]);reviews=json.loads(before['tools/literature/reviewed-uses.json'])
    item=next(row for row in inventory if row['id']=='snpeff')
    assert item['implementation_status']=='missing' and item['paper_count']==4 and set(item['papers'])==set(EXPECTED)
    assert len(reviews['reviews'])==1214 and not any(row['tool_id']=='snpeff' for row in reviews['reviews']) and len(census['roles'])==4
    for role in census['roles']:
        paper=next(row for row in papers if row['paper_id']==role['paper_id']);use=next(row for row in paper['tools'] if row['tool_id']=='snpeff')
        assert paper['xml_sha256']==role['xml_sha256'] and use['evidence_paragraph_sha256']==role['anchor_sha256']
        scope=NOTE+' '+role['unresolved']
        use.update(usage_context='variant-consequence-annotation',review_status='usage-summary-reviewed; paper-wide-completeness-review-open',evidence_selection='individually-reviewed-paragraph',use_summary=role['use_summary'],implementation_scope_note=scope,evidence_locator=role['fulltext_url']+' (section: '+role['anchor_section']+')')
        reviews['reviews'].append(dict(pmcid=role['pmcid'],doi=role['doi'],tool_id='snpeff',evidence_paragraph_sha256=role['anchor_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context='variant-consequence-annotation'))
    item.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=NOTE,implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,implementation_scope=NOTE,validation_note='Actual original-engine run/verify/replay controls, full hg38kg reference with 969 independent synthetic variants and published P002 reference compatibility with 67 synthetic SNPs. Genuine Windows/WSL replay, source-specific zero-exit error refusals and complete captured software/reference/input/output; no author biological result.',selected_project_editions=['5.2','3.1i-r354-source'],source_build_required=True,released_binary_contains_workflow_adapter=False,whole_tool_qualified=False,whole_paper_reproduced=False,new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,current_documentation=GUIDE,paper_role_qualification={role['paper_id']:dict(reported_engine_version_known=role['reported_engine_version_known'],selected_profile=role['selected_profile'],selected_runtime_is_author_binary=False,whole_paper_reproduced=False,downstream_policy_not_inferred=True) for role in census['roles']})
    counts=dict(Counter(row['implementation_status'] for row in inventory));assert counts['upstream-adapter-validated']==104 and counts['native-subset-validated']==23 and counts['missing']==260
    summary.update(implementation_status_counts=counts)
    after=dict(before)
    for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),('tools/literature/reviewed-uses.json',reviews)]:
        raw=encoded(value)
        if before[name].count(b'\n')==before[name].count(b'\r\n'):raw=raw.replace(b'\n',b'\r\n')
        after[name]=raw
    page=before['site/literature-review.html'].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    assert match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]]))
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c')
    page=page[:match.start(2)]+payload+page[match.end(2):]
    from snakemake.model import NOTICE as previous
    for name,text in [('site/literature-review.html',page),('site/literature-warnings.html',before['site/literature-warnings.html'].decode())]:
        assert text.count(previous)==1 and text.count('</head>')==1
        after[name]=text.replace(previous,NOTICE+'<details><summary>Previous Snakemake checkpoint (historical)</summary>'+previous+'</details>',1).replace('</head>',STYLE+'</head>',1).encode()
    for name in [META[3],META[4]]:
        heading='# 250-paper omics and GWAS toolbox catalog\n\n## SnpEff: scope, warnings and required actions\n\n' if name==META[3] else '# Current literature warnings and required actions\n\n## SnpEff annotation profiles\n\n'
        block=heading+'**Available from current source:** `snpeff-tools run`, `verify` and genuine `replay`. Released v0.4.0 lacks these commands. Read [commands and scope](../SNPEFF_WORKFLOWS.md).\n\n'+WARNING+'\n\n**Current cohort:** '+CURRENT+'\n\n<details><summary>Checkpoint before SnpEff admission (historical)</summary>\n\n'
        after[name]=(block+before[name].decode()+'\n</details>\n').encode()
    return after

def check(before,after,census):
    assert after==transition(before,census)
    a,b=json.loads(before[META[0]]),json.loads(after[META[0]])
    for old,new in zip(a,b):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [row for row in old['tools'] if row['tool_id']!='snpeff']==[row for row in new['tools'] if row['tool_id']!='snpeff']
    assert [row for row in json.loads(before[META[1]]) if row['id']!='snpeff']==[row for row in json.loads(after[META[1]]) if row['id']!='snpeff']
    assert [row for row in json.loads(before['tools/literature/reviewed-uses.json'])['reviews'] if row['tool_id']!='snpeff']==[row for row in json.loads(after['tools/literature/reviewed-uses.json'])['reviews'] if row['tool_id']!='snpeff']
    assert len(json.loads(after['tools/literature/reviewed-uses.json'])['reviews'])==1218 and sum(len(paper['tools']) for paper in b)==2693
    return dict(papers=250,named_tools=446,observations=2693,reviewed_uses=1218,unreviewed_uses=1475,new_scoped_integrations=127,native_integrations=23,upstream_integrations=104,including_existing_adapters=130,missing=260,registry_definitions=485,reviewed_snpeff_roles=4,observations_added=0,new_scoped_integration_credit=1,native_family_credit_added=0,upstream_family_credit_added=1,whole_goal_complete=False)
