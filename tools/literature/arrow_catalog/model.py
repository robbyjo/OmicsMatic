# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one existing R Arrow family without changing primary paper facts."""
from collections import Counter
import copy,json,re
from scrattchmapping_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph
from arrow_catalog.source import MODULE,TOOL,NOTES,reviews,original_reviews
from arrow_catalog.bridge import EVIDENCE

PROGRAM='tools/literature/arrow1201/run.py'
GUIDE='docs/literature/ARROW1201_CATALOG_INTEGRATION.md'
CURRENT='tools/literature/arrow1201/CATALOG_STATUS.md'
PROOF=DATA+'/arrow-catalog-validation.json'
COVERAGE=DATA+'/arrow1201-role-coverage.json'
COMPATIBILITY=DATA+'/arrow-reviews-shared-source-compatibility.json'
WARNING='''
## R Arrow 12.0.1: warnings and required actions

**250 papers, 438 named software families, 2,679 observations and 1,180 individually reviewed uses; 1,499 uses await review.** The 71 Nature, 104 Nature Genetics and 75 AJHG papers, fixed 2023-10-23 through 2026-10-23 window and all primary facts remain unchanged. Only implementation coverage for P059's existing R Arrow use changes. P059 reports 12.0.1, Parquet expression chunks of 50,000 cells by 500 genes and pairwise cluster DEG queries. Its exact Arrow calls, storage schema, memory-map settings and query options remain unreported.

**115 scoped new integrations: 20 native Java subsets and 95 original upstream workflows; 118 including three existing adapters. 264 named families remain missing.** This adds one upstream R Arrow family credit and no native family credit. The full semantic review and 250-paper implementation objective remain open. This is selected Parquet workflow coverage, not the whole package or a reproduced paper.

The selected original R Arrow 12.0.1 workflow reads caller-declared local Parquet chunks into complete R data frames, or uses open_dataset with explicit lazy filter/select/arrange steps and collect. Thirteen direct original controls produced ten complete returns and three original failures, including a real 50,000-by-500 tabular fixture. Complete returns and RNG match; numeric payload checks include signed zero, NA/NaN and integer64 precision. The wide fixture is a project layout, not the authors' unknown sparse-matrix encoding. Sparse-matrix conversion, unknown paper settings, other interfaces and biological/paper reproduction are not qualified.

**Same version does not imply the same source build.** The required libarrow 12.0.1 h32ed672_2_cpu recipe declares no source patches. The earlier h0de2578_50_cpu candidate declares four backports and is blocked before R executes. Its old proof and frozen archive are retained with an overriding INVALIDATION.json; do not promote them or refresh their hashes. The official Apache source archive was independently checked (6,889 regular files), as were all 1,619 CRAN R source files and 13 loaded I/O/query functions. C++ binaries were not independently rebuilt. The reported C++ Git ID identifies a conda-forge packaging commit, not the upstream Arrow tag. The locked R 4.2.3/dplyr 1.1.4 profile is a project comparison environment, not an author environment.

**Complete original errors are retained.** Twelve controls have identical complete conditions. The missing-column error differs only in its complete trace field because bare-call and adapter caller contexts differ. All other fields, attributes, field order and error class/message/call agree. No condition field is removed or coerced to manufacture equality. Live Dataset/Table C++ pointers and live execution-context identity are not persisted. Raw/native diagnostic totals are unavailable, never assumed zero; inspect every raw console and original condition, including on success. The selected build lacks LZO; LZO-dependent input requires a separately permitted and qualified runtime.

Use python3 tools/literature/arrow1201/run.py --runtime /permitted/arrow-provider --request /trusted/request.rds --assets /local/parquet-assets --out /NEW/output. This is a Python source entry point, not a bundled Java CLI command. Use the documented installer with --allow-install and, only where permitted, --allow-download; cache-only installation requires the exact source archive and complete package cache. Both installer branches passed, along with five actual run/recording policy tests and a real UID-65534 permission-denial test. Two unchanged saved controls were accepted and twelve tampered controls rejected. Analysis never downloads, installs, repairs identifiers, substitutes a different edition or silently collects an unsupported Dataset query.

**SKIP3 skips dependent analysis** when required software, permission, installation, download or execution is missing or forbidden. **FAIL2 admits no complete result**, including recording failure after R returns. Inspect the exit code, WARNINGS.md, FAILED.json, FAILURE.json or FINALIZATION_ERROR.json and the raw consoles. Success requires exit 0, COMPLETED.json, the complete original-file manifest and no failure marker. A leftover RDS alone is not success. Retain original inputs, complete returns/conditions, literal IDs, NA/NaN/Inf and all available partials. Resolve the named prerequisite or filesystem cause and retry NEW writable paths. See ARROW1201_CATALOG_INTEGRATION.md and arrow1201/CATALOG_STATUS.md for current coverage and copyable recovery commands; the byte-preserved standalone README records the earlier pre-admission checkpoint.

Other restrictions remain: HICAT, BIGCAT and PyArrow remain separate missing families. PyArrow 14.0.2 is a P250 development declaration with no established analysis operation. Mapping retains its prepared-reference-only limits and noncommercial dependency redistribution restrictions. DoubletFinder SCT convergence remains unqualified despite source parity (3,706 original warnings, including 12 iteration-limit warnings), and reported 3.0 remains unresolved. Cell Ranger/ARC are excluded by user request. All prior scientific verifiers, evidence and hashes remain unchanged. Earlier runtime, source, stack-diagnostic and irrecoverable-figure limitations remain recorded.
'''
NOTICE='<section id="arrow-required-actions" role="note"><h2>R Arrow 12.0.1: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+'</section>'
STYLE='\n<style id="arrow-warning-wrap">#arrow-required-actions { overflow-wrap: anywhere; min-width: 0; }</style>\n'

def entry(root,old):
    if old['id']!=TOOL or old['implementation_status']!='missing' or old['papers']!=['P059']:
        raise ValueError('Expected one separately missing primary R Arrow use')
    row=copy.deepcopy(old)
    row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,
        coverage_note='Selected original R Arrow 12.0.1 chunk reads and lazy Parquet Dataset queries; author calls/schema/options remain unreported.',
        implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,
        implementation_scope='Original R Arrow 12.0.1 local Parquet chunk reads and lazy Dataset filter/select/arrange/collect using caller-declared assets and options. Required recipe-unpatched C++ h32ed672_2_cpu; same-version patched builds blocked. Complete collected R data frames and conditions retained. Author sparse-matrix encoding/conversion, exact calls/mmap/query options, other interfaces, live pointers and biological/paper reproduction are not qualified.',
        validation_note='13 direct original controls: 10 complete returns and 3 original failures, including a 50,000-by-500 project fixture. Complete return/RNG identity and numeric payload checks preserve signed zero, NA/NaN, integer64, classes, attributes and order. 12 complete condition controls identical; missing-column differs only in complete trace (actual caller context retained); class/message/call and all other fields agree. 984-member frozen evidence; online/cache-only installs; five real policy/recording tests and UID-65534 permission test. Two unchanged and twelve tampered comparator controls. SKIP3 skips dependent analysis; FAIL2 admits no complete result, including recording failure.',
        project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
        development_candidate_available=True,development_candidate=PROGRAM,
        development_candidate_sha256=pin(root/PROGRAM)['sha256'],
        development_candidate_kind='python-source-entrypoint-requires-exact-recipe-unpatched-Linux-WSL-R-provider',
        development_build='tools/literature/arrow1201/install_runtime.py',
        development_adapter_cli='python3 '+PROGRAM+' --runtime /permitted/arrow-provider --request /trusted/request.rds --assets /local/parquet-assets --out /NEW/output',
        selected_upstream_versions=['12.0.1'],selected_CPP_build='h32ed672_2_cpu',selected_R_version='4.2.3',
        selected_environment_is_author_environment=False,source_roles_reviewed=1,
        workflow_validation='../arrow1201-pristine-workflow-validation/QUALIFICATION.json',
        source_role_coverage='arrow1201-role-coverage.json',current_documentation=GUIDE,
        paper_role_coverage={r['paper_id']:r['use_summary']+' '+r['implementation_scope_note'] for r in reviews(root)},
        uncovered_roles=['The author storage schema, sparse-matrix encoding/conversion and exact Arrow/memory-map/query options',
            'Other editions/builds/interfaces, remote/cloud sources and live Dataset/Table pointer persistence',
            'Live execution-context identity and universal raw/native diagnostic totals',
            'Whole-package, biological validity and paper reproduction'],
        new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False)
    return row

def transition(root,before):
    if set(before)!=set(META):raise ValueError('Exactly eight complete predecessor metadata inputs required')
    inventory,summary=json.loads(before[META[0]]),json.loads(before[META[1]])
    papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(438,250,2679,1180):
        raise ValueError('Complete preceding populations differ')
    index=next(i for i,r in enumerate(inventory) if r['id']==TOOL)
    inventory[index]=entry(root,inventory[index])
    expected=original_reviews(root)[0]
    old=[r for r in document['reviews'] if r['tool_id']==TOOL and r['paper_id']=='P059']
    if len(old)!=1 or old[0]!=expected:raise ValueError('Exact preceding primary Arrow review required')
    old[0]['implementation_scope_note']=NOTES['P059']
    observation=[r for p in papers if p['paper_id']=='P059' for r in p['tools'] if r['tool_id']==TOOL]
    if len(observation)!=1 or observation[0]['implementation_scope_note']!=expected['implementation_scope_note']:
        raise ValueError('Exact preceding Arrow observation coverage required')
    observation[0]['implementation_scope_note']=NOTES['P059']
    updated=copy.deepcopy(summary)
    updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory))
    wanted=copy.deepcopy(summary['implementation_status_counts']);wanted['missing']-=1;wanted['upstream-adapter-validated']+=1
    if updated['implementation_status_counts']!=wanted or (wanted['missing'],wanted['upstream-adapter-validated'],wanted['native-subset-validated'])!=(264,95,20):
        raise ValueError('Exactly one upstream R Arrow family credit required')
    page=before[META[4]].decode()
    match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):
        raise ValueError('Exact preceding complete embedded catalog required')
    page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
    warning=before[META[5]].decode();marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    historical='<details><summary>Previous scrattch.mapping checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
    if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]):
        raise ValueError('Exact preceding warning panels/document heads required')
    if page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Expected insertion points required')
    page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
    warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
    intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [R Arrow source roles, commands and recovery](../ARROW1201_CATALOG_INTEGRATION.md). Current cumulative verification: `python tools/literature/arrow_catalog/catalog.py --verify --rescan-sources --runtime /permitted/arrow-provider --prior-runtime /permitted/mapping-provider --doubletfinder-runtime /permitted/DoubletFinder-provider --out build/literature-review/monocle-runtime/NEW-arrow-catalog-check` (add `--wsl-distro NAME` on Windows). The full goal remains open.\n\n'
    paper_bytes=encoded(papers)
    if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_bytes=paper_bytes.replace(b'\n',b'\r\n')
    return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:paper_bytes,REVIEWS:encoded(document),
        META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
        META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',
        META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
    if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Catalog differs from exact one-family Arrow coverage transition')
    oldi,newi=json.loads(before[META[0]]),json.loads(after[META[0]])
    if [r for r in oldi if r['id']!=TOOL]!=[r for r in newi if r['id']!=TOOL]:raise ValueError('An unrelated family changed')
    oldr,newr=json.loads(before[REVIEWS])['reviews'],json.loads(after[REVIEWS])['reviews']
    if [r for r in oldr if r['tool_id']!=TOOL]!=[r for r in newr if r['tool_id']!=TOOL]:raise ValueError('An unrelated review changed')
    for old,new in zip(json.loads(before[PAPERS]),json.loads(after[PAPERS])):
        expected=copy.deepcopy(old)
        if old['paper_id']=='P059':next(r for r in expected['tools'] if r['tool_id']==TOOL)['implementation_scope_note']=NOTES['P059']
        if expected!=new:raise ValueError('Paper identity, primary facts or unrelated coverage changed')
    return dict(papers=250,named_tools=438,observations=2679,reviewed_uses=1180,unreviewed_uses=1499,
        new_scoped_integrations=115,missing=264,unchanged_previous_families=437,unchanged_previous_observations=2678,
        unchanged_previous_reviews=1179,enriched_existing_observations=1,revised_existing_reviews=1,
        added_observations=0,added_reviews=0,new_registry_definitions=0,new_scoped_integration_credit=1,
        native_family_credit_added=0,whole_goal_complete=False)
