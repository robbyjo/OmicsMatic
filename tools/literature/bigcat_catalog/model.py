# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit the existing BIGCAT family while preserving both primary paper records."""
from collections import Counter
import copy,json,re
from arrow_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph
from bigcat_catalog.source import MODULE,TOOL,NOTES,reviews,original_reviews
from bigcat_catalog.bridge import EVIDENCE

PROGRAM='tools/literature/scrattchbigcat005_workflows/run.py'
GUIDE='docs/literature/BIGCAT005_CATALOG_INTEGRATION.md'
CURRENT='tools/literature/scrattchbigcat005_workflows/CATALOG_STATUS.md'
PROOF=DATA+'/bigcat-catalog-validation.json'
COVERAGE=DATA+'/bigcat005-role-coverage.json'
COMPATIBILITY=DATA+'/bigcat-reviews-shared-source-compatibility.json'
WARNING='''
## BIGCAT 0.0.5: warnings and required actions

**250 papers, 438 named software families, 2,679 observations and 1,180 individually reviewed uses; 1,499 uses await review.** The 71 Nature, 104 Nature Genetics and 75 AJHG papers, fixed 2023-10-23 through 2026-10-23 source window, paper identities and primary facts are unchanged. Only the implementation coverage notes for the two existing BIGCAT uses change. P058's edition remains unknown; P059 reports 0.0.5. Neither paper establishes its exact Git commit or complete caller environment.

**116 scoped new integrations: 20 native Java subsets and 96 original upstream workflows; 119 including three existing adapters. 263 named families remain missing.** This adds one upstream BIGCAT family credit and zero native family credits. The separately implemented Java marker-doublet helper is a bounded component of this same family. The full semantic review and 250-paper implementation objective remain open. Scoped operation coverage does not establish whole-tool validity or paper reproduction.

The adapter runs eight named original function roles across two explicit source profiles, with original Parquet/FBM/Annoy constructors, centroid cross-validation and constellation assembly. The 2023 snapshot bbfe5277de63c65a5342e43f3e1eeb1015755fc7 and 2022 snapshot 5134c0b0a107a724f7d1f091c23a73ab691b4a66 both report 0.0.5; they are project choices, not recovered author commits. The original 2023 harmonization indexing error is retained. The earlier R 4.1 profile supports tested three-dataset harmonization with explicit caller sets and original warnings, but lacks impute_cross_big. A single profile is not claimed to implement all eight roles successfully. No upstream source or dependency algorithm is patched.

P058's optional at-least-five-SPN selection follows integration at a caller-chosen hierarchy level. It is not a clustering parameter and does not reproduce later reciprocal taxonomy correspondence or the separate fewer-than-three-mapped-cells exclusion. P059 controls use the stated v2/v3 DE settings, source bins of 50,000 cells by 500 genes, fivefold 8,460-marker/534-TF/20-random-534 panels, and sequential coarse/fine FBM imputation partitions. Centroids use all training cells; a separate 240-training-cells-per-class control detects the source summary helper's unrelated 200-cell default. Gene identities, data, folds and unreported caller options are explicit project choices. Manual borderline-cluster review and biological results remain unqualified.

Twenty-five direct original controls produced 19 complete returns and six original failures without results, with 3,198 retained comparison fields. Complete conditions and RNG states remain intact. A nonzero 502-gene by 50,002-cell fixture exercises all four 500-gene/50,000-cell Parquet partitions, with independent reconstruction and mapping-distance checks. Fifteen original PDF pairs contain 18 pages per side with identical rendered pixels. The full 3,335-file logical evidence tree, both public installation records and 12 runtime/policy/recording guards are retained. Two unchanged semantic controls were accepted and 11 changed controls rejected after their integrity manifests were resealed. Six source failures remain failures; matching an error does not create an analysis result.

**Complete original objects and differences are retained.** FBM class-definition identities, cyclic plot environments and temporary Annoy paths can differ between processes. Scientific arrays, complete plot data, derived renders, FBM backing bytes and retained index payloads are checked independently. This does not establish whole live R-object or pointer identity. Original Annoy distances are Euclidean distances between L2-normalized vectors, not silently rewritten cosine scores. Source-produced blank plot pages, NA/NaN/Inf and warnings remain unchanged. Raw/native diagnostic totals are unavailable, never assumed zero; inspect all original conditions and raw consoles.

Use python3 tools/literature/scrattchbigcat005_workflows/run.py with an explicit --runtime provider, trusted --request RDS, --assets and NEW --out paths. This is a Python source entry point, not a bundled Java CLI command. The installer requires an explicit --profile and --allow-install; add --allow-download only where permitted, or supply the exact source cache and complete package cache. Both download-enabled and cache-only installations and actual analyses using their installed runtimes passed. The exact declared recipe-unpatched Arrow C++ build is required. Do not substitute a same-version build or mutate a sealed provider. RDS inputs must be trusted; the adapter is not a sandbox for arbitrary code.

**SKIP3 skips dependent analysis** if exact software, permissions, download, installation or execution requirements are missing or forbidden. **FAIL2 admits no complete result**, including a manifest or final-marker recording failure after R returned. Read WARNINGS.md, FAILURE.json, the original R status, manifests and every raw console. A leftover RDS or successful R return is insufficient; only a final COMPLETED.json admits the recorded operation. Preserve inputs, complete returns/conditions, literal identifiers, NA/NaN/Inf and available partials. Resolve the named prerequisite, input or filesystem cause and retry NEW writable output/workspace paths. No automatic source repair, identifier correction or runtime fallback occurs. See BIGCAT005_CATALOG_INTEGRATION.md for copyable commands and limits.

Other restrictions remain: modified HICAT 1.0.9 and PyArrow remain separate missing families. P250's PyArrow 14.0.2 is a development declaration with no established analysis operation. Mapping retains its prepared-reference-only scope and private noncommercial dependency restrictions. R Arrow's invalidated same-version C++ build50 evidence stays retained and unqualified; its missing-column trace retains the actual caller context. DoubletFinder SCT convergence and reported 3.0 remain unresolved. Cell Ranger/ARC are excluded by user request. Earlier runtime/source/stack/figure limitations remain recorded. All preceding scientific programs, evidence and hashes are unchanged.
'''
NOTICE='<section id="bigcat-required-actions" role="note"><h2>BIGCAT 0.0.5: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+'</section>'
STYLE='\n<style id="bigcat-warning-wrap">#bigcat-required-actions { overflow-wrap: anywhere; min-width: 0; }</style>\n'

def entry(root,old):
    if old['id']!=TOOL or old['implementation_status']!='missing' or old['papers']!=['P058','P059']:
        raise ValueError('Expected exactly the two existing missing BIGCAT uses')
    row=copy.deepcopy(old)
    row.update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,
      coverage_note='Eight original BIGCAT roles across two explicit 0.0.5 source profiles, plus bounded native marker-doublet component; original failures and unknown author settings retained.',
      implementation_status='upstream-adapter-validated',implementation_source=PROGRAM,
      implementation_scope='Original BIGCAT 0.0.5 build_dend, plot_constellation, i_harmonize, iter_clust_big, find_doublet_by_marker, select_N_markers, map_cells_knn_big and impute_cross_big across explicitly selected 2023/R4.2 and 2022/R4.1 profiles. Original Parquet/FBM/Annoy preparation, all-training-cell centroid cross-validation, constellation assembly and explicit post-integration SPN selection. Author commits, P058 edition, unreported settings, manual biological decisions, reciprocal correspondence, live R-object identity and paper reproduction remain unqualified.',
      validation_note='25 direct original controls: 19 complete returns, six original failures, 3198 comparison fields; complete original objects/conditions/RNG retained. 3335 logical evidence files in five payload volumes. Four nonzero Parquet partitions; 15 PDF pairs/18 pages per side have identical rendered pixels. Both public install branches and actual installed-runtime analyses passed; 12 runtime/policy/recording guards; two unchanged and 11 resealed changed semantic controls. Opaque R class/environment/path differences retained; independent scientific payload and render checks do not claim live-object identity. Separate native Java marker component has 753 source cases. SKIP3 skips dependent analysis; FAIL2 admits no complete result, including recording failure.',
      project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
      development_candidate_available=True,development_candidate=PROGRAM,development_candidate_sha256=pin(root/PROGRAM)['sha256'],
      development_candidate_kind='python-source-entrypoint-requires-explicit-Linux-WSL-original-profile',
      development_build='tools/literature/scrattchbigcat005_workflows/install_runtime.py',
      development_adapter_cli='python3 '+PROGRAM+' --runtime /permitted/bigcat-provider --request /trusted/request.rds --assets /local/assets --out /NEW/output',
      selected_upstream_versions=['0.0.5'],selected_source_profiles=['prepublication-2023','pre-regression-2022'],
      selected_source_commits=['bbfe5277de63c65a5342e43f3e1eeb1015755fc7','5134c0b0a107a724f7d1f091c23a73ab691b4a66'],
      selected_R_versions=['4.2.3','4.1.3'],selected_environment_is_author_environment=False,
      author_source_commits_established=False,P058_edition_established=False,source_roles_reviewed=2,
      workflow_validation='../scrattchbigcat005-workflow-validation/QUALIFICATION.json',
      native_component_source='src/main/java/org/jlinalg/singlecell/scrattch/ScrattchBigcat005.java',
      native_component_validation='../scrattchbigcat005-native-development/VALIDATION.json',
      source_role_coverage='bigcat005-role-coverage.json',current_documentation=GUIDE,
      paper_role_coverage={r['paper_id']:r['use_summary']+' '+r['implementation_scope_note'] for r in reviews(root)},
      uncovered_roles=['Actual author commits and caller environments, P058 package edition and unreported arguments',
        'P058 reciprocal taxonomy correspondence and separate fewer-than-three-mapped-cells exclusion',
        'P059 manual borderline-cluster/marker review and biological annotation decisions',
        'Source defaults that fail, missing operations in a selected profile, live R-object and pointer identity',
        'Other source/dependency builds, whole-package validity and biological/paper reproduction'],
      new_scoped_integration_credit=1,native_family_credit_added=0,whole_tool_qualified=False,whole_paper_reproduced=False)
    return row

def transition(root,before):
    if set(before)!=set(META):raise ValueError('Exactly eight complete predecessor metadata inputs required')
    inventory,summary=json.loads(before[META[0]]),json.loads(before[META[1]])
    papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
    if (len(inventory),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(438,250,2679,1180):
        raise ValueError('Complete preceding populations differ')
    indices=[i for i,r in enumerate(inventory) if r['id']==TOOL]
    if len(indices)!=1:raise ValueError('One existing BIGCAT family required')
    inventory[indices[0]]=entry(root,inventory[indices[0]])
    for expected in original_reviews(root):
        paper_id=expected['paper_id']
        old=[r for r in document['reviews'] if r['tool_id']==TOOL and r['paper_id']==paper_id]
        if len(old)!=1 or old[0]!=expected:raise ValueError('Exact preceding BIGCAT primary review required: '+paper_id)
        old[0]['implementation_scope_note']=NOTES[paper_id]
        observation=[r for p in papers if p['paper_id']==paper_id for r in p['tools'] if r['tool_id']==TOOL]
        if len(observation)!=1 or observation[0]['implementation_scope_note']!=expected['implementation_scope_note']:
            raise ValueError('Exact preceding BIGCAT observation required: '+paper_id)
        observation[0]['implementation_scope_note']=NOTES[paper_id]
    updated=copy.deepcopy(summary);updated['implementation_status_counts']=dict(Counter(r['implementation_status'] for r in inventory))
    wanted=copy.deepcopy(summary['implementation_status_counts']);wanted['missing']-=1;wanted['upstream-adapter-validated']+=1
    if updated['implementation_status_counts']!=wanted or (wanted['missing'],wanted['upstream-adapter-validated'],wanted['native-subset-validated'])!=(263,96,20):
        raise ValueError('Exactly one upstream BIGCAT family and no new native family credit required')
    page=before[META[4]].decode();warning=before[META[5]].decode()
    match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):
        raise ValueError('Exact preceding complete embedded catalog required')
    page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inventory,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
    marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    historical='<details><summary>Previous R Arrow checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
    if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]):
        raise ValueError('Exact preceding warning panels and heads required')
    if page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Expected warning insertion points required')
    page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
    warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
    intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [BIGCAT source roles, commands and recovery](../BIGCAT005_CATALOG_INTEGRATION.md). Current cumulative verification: `python tools/literature/bigcat_catalog/catalog.py --verify --rescan-sources --runtime2023 /permitted/bigcat-2023-provider --runtime2022 /permitted/bigcat-2022-provider --arrow-runtime /permitted/arrow-provider --mapping-runtime /permitted/mapping-provider --doubletfinder-runtime /permitted/DoubletFinder-provider --out build/literature-review/monocle-runtime/NEW-bigcat-catalog-check` (add `--wsl-distro NAME` on Windows). The full goal remains open.\n\n'
    paper_bytes=encoded(papers)
    if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):paper_bytes=paper_bytes.replace(b'\n',b'\r\n')
    return {META[0]:encoded(inventory),META[1]:encoded(updated),PAPERS:paper_bytes,REVIEWS:encoded(document),
      META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
      META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',
      META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
    if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Catalog differs from exact one-family/two-note BIGCAT transition')
    oldi,newi=json.loads(before[META[0]]),json.loads(after[META[0]])
    if [r for r in oldi if r['id']!=TOOL]!=[r for r in newi if r['id']!=TOOL]:raise ValueError('An unrelated family changed')
    oldr,newr=json.loads(before[REVIEWS])['reviews'],json.loads(after[REVIEWS])['reviews']
    for old,new in zip(oldr,newr):
        expected=copy.deepcopy(old)
        if old['tool_id']==TOOL:expected['implementation_scope_note']=NOTES[old['paper_id']]
        if new!=expected:raise ValueError('A primary review fact or unrelated review changed')
    for old,new in zip(json.loads(before[PAPERS]),json.loads(after[PAPERS])):
        expected=copy.deepcopy(old)
        if old['paper_id'] in NOTES:
            next(r for r in expected['tools'] if r['tool_id']==TOOL)['implementation_scope_note']=NOTES[old['paper_id']]
        if expected!=new:raise ValueError('Paper identity, primary facts or unrelated coverage changed')
    return dict(papers=250,named_tools=438,observations=2679,reviewed_uses=1180,unreviewed_uses=1499,
      new_scoped_integrations=116,missing=263,unchanged_previous_families=437,unchanged_previous_observations=2677,
      unchanged_previous_reviews=1178,enriched_existing_observations=2,revised_existing_reviews=2,
      added_observations=0,added_reviews=0,new_registry_definitions=0,new_scoped_integration_credit=1,
      native_family_credit_added=0,whole_goal_complete=False)
