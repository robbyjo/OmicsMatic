# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit one native workflow and two exact primary-source role reviews."""
from collections import Counter
import copy, json, re
from melodi_catalog.model import ROOT,META,PAPERS,REVIEWS,DATA,NOTICE as PRIOR_NOTICE,encoded,pin,rawpin,paragraph
from tpmcalculator_catalog.source import DEFINITION,MISSING_NOTE,audit

MODULE='tools/literature/tpmcalculator_catalog'
PROGRAM='src/main/java/org/jlinalg/cli/TpmCalculatorCli.java'
GUIDE='docs/literature/TPMCALCULATOR_CATALOG_INTEGRATION.md'
EVIDENCE='docs/literature/tpmcalculator-catalog-integration'
WORKFLOW='docs/literature/tpmcalculator-workflow-development'
PROOF=DATA+'/tpmcalculator-catalog-validation.json'
COMPATIBILITY=DATA+'/tpmcalculator-reviews-shared-source-compatibility.json'
NOTE=('Project availability: native Java GTF/BAM read assignment and TPM workflow using the explicit original0.0.4 GNU64 comparison profile; original executable prints0.0.3. '
 'The author version, options, exact GTF and output selection are unknown. Project controls are not recovered author settings. '
 'All original counting/model quirks, missing/present-zero state, nonfinite values, float bridge TPM and six-digit tables are retained. '
 'Complete input capture, all32 state fields, raw IEEE bits, conditions, implementation/dependency identities and worker streams are recorded. '
 'Saved verification freshly recomputes captured GTF/BAM with the matching recorded implementation; it does not need original file locations. '
 'SKIP3 skips unavailable/forbidden prerequisites; FAIL2 admits no complete result, including recording failure after a native return. '
 'Only final COMPLETED.json with operation run and result_admitted true admits an analysis. No original quantification program or installer is invoked. '
 'HTSJDK5.0.0 transports BAM using explicitly selected JDK decompression. No whole-tool, biological-accuracy or paper-reproduction claim is made.')
WARNING='''
## Native TPMCalculator: warnings and required actions

**250 papers, 446 named families, 2,690 observations and 1,197 reviewed uses; 1,493 uses await review.** The fixed 2023-10-23 through 2026-10-23 corpus remains 71 Nature, 104 Nature Genetics and 75 AJHG papers. The full semantic review and implementation goal remains open.

**122 scoped new integrations: 21 native Java subsets and 101 upstream workflows; 125 including three existing adapters. 265 named families remain missing.** TPMCalculator adds one native family. Newly catalogued iDEP remains missing, so the missing-family total is unchanged. P050's two source-specific reviews distinguish transcript quantification/normalization with TPMCalculator from DESeq2 differential expression within the iDEP.96 web interface. iDEP receives no runtime or implementation credit.

**P050 does not report the TPMCalculator version, options, exact GTF or output selection.** The explicit project comparison uses tag0.0.4, whose original executable prints0.0.3; no author edition is inferred. The tag0.0.6 quantification-source equivalence check is not an executed0.0.6 binary comparison. Preserve the literal iDEP.96 without normalizing it to a source release or DESeq2 version. Its exact edition, license, deployed snapshot and runtime remain unqualified. DESeq2 defaults in that interface do not establish TPMCalculator options. Strand-specific library preparation is not a TPMCalculator strand setting.

**Native counting retains the original source behavior.** Both mates count separately; no strand filtering is inferred. Literal -AS gene exclusions, unusual CIGAR/block assignments, bounded gene scans, partial splice updates, GNU gene order, repeated-transcript behavior, unsigned coordinates and float bridge TPM remain observable. Nonfinite values are retained. Source tables use six significant digits and the declared -nan token; complete raw IEEE state and semantic NaN classification remain separate. Inspect WARNINGS.md, WARNINGS.json and products/CONDITIONS.json before interpretation. Do not silently repair identifiers, annotations or NaN/Inf.

**SKIP3 skips dependent analysis** for a named missing/forbidden input, exact Java/dependency implementation or output prerequisite. Supply that permitted requirement and retry NEW output paths. If access or installation is forbidden, leave the dependent analysis skipped. Analysis downloads and installs nothing; the worker is Java and invokes no original counting program. Use Java17+ and the exact HTSJDK5.0.0 transport supplied in the self-contained application or declared dependency JAR. The library-only JAR requires that dependency on its classpath.

**FAIL2 admits no complete result**, including malformed inputs, runtime/capacity failures, changed inputs, replay mismatch and recording failure after native computation returns. Preserve originals and partials. Read FAILURE.json, failure-trace.txt, WARNINGS.md and complete worker/stdout.bin and worker/stderr.bin when present. Resolve the named cause and retry NEW paths. Only final COMPLETED.json with operation run and result_admitted true admits an analysis; worker/RETURN.json and partial tables do not. A verification marker records a replay, not another analysis. Existing outputs are rejected and preserved.

The qualified synthetic scope includes141 focused tests,64 original table cases and full captured-input replays per host, nine public failure controls per host, five cross-host replays each direction, and50,049 original numeric-formatting references. Windows Java25 and Linux Java21 were actually exercised; the self-contained JAR also passed five saved-bundle replays on each host. Verification freshly recomputes complete captured GTF/BAM files with the recorded implementation and compares semantic state/table bytes. Hash integrity is not source authentication or biological validity. Heap, wall time, workspace and metadata limits are explicit operating capacities, not OS total-memory/disk quotas. Cell Ranger/ARC remain excluded; earlier source/license restrictions and failed/historical records remain retained. Whole-tool validity, biological accuracy and paper reproduction remain unqualified.
'''
QUICKSTART='''<details id="tpmcalculator-runtime-guide"><summary>Native TPMCalculator commands and recovery</summary>
<p>Build the self-contained application with <code>./gradlew executableJar</code>. Use an explicit request containing GTF, ordered BAM samples, native profile, counting/output options and adequate operating limits. These are caller choices; the paper's choices remain unknown. Repository guides: <code>docs/literature/TPMCALCULATOR_NATIVE_WORKFLOW.md</code> and <code>docs/literature/TPMCALCULATOR_CATALOG_INTEGRATION.md</code>.</p>
<pre>java -cp build/cli/omicsmatic-0.3.7.jar org.jlinalg.cli.TpmCalculatorCli run --request /trusted/request.json --out /results/NEW-analysis</pre>
<p>Read all warnings and conditions. A missing permitted prerequisite skips analysis; failures admit no complete result. Preserve partials and retry new output paths. Do not consume a worker return as an admitted analysis.</p>
<pre>java -cp build/cli/omicsmatic-0.3.7.jar org.jlinalg.cli.TpmCalculatorCli verify --bundle /results/SAVED-analysis --out /validation/NEW-replay</pre>
<p>Verification recomputes the saved GTF/BAM bytes with the matching recorded implementation. It needs neither original input locations nor the original TPMCalculator runtime. It does not reproduce the paper.</p></details>'''
NOTICE='<section id="tpmcalculator-required-actions" role="note"><h2>Native TPMCalculator: warnings and required actions</h2>'+''.join('<p>'+paragraph(p)+'</p>' for p in WARNING.strip().split('\n\n')[1:])+QUICKSTART+'</section>'
STYLE='\n<style id="tpmcalculator-warning-wrap">#tpmcalculator-required-actions { overflow-wrap: anywhere; min-width: 0; } #tpmcalculator-required-actions pre { white-space: pre-wrap; overflow-wrap: anywhere; }</style>\n'

def qualified_entry(root, old):
    row=copy.deepcopy(old)
    row.update(current_coverage='native-subset-validated',implementation_status='native-subset-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
        coverage_note='Native source-defined GTF/BAM read counting and TPM workflow; project0.0.4 GNU64 comparison, author edition/options unreported.',implementation_scope=NOTE,
        validation_note='141 focused tests;64 actual original table cases and complete-input replays on each Windows/Linux host; nine public failure controls per host; cross-host and self-contained-JAR replays. Synthetic source comparisons, not biological validation.',
        project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_candidate_available=True,
        development_candidate=PROGRAM,development_candidate_kind='standalone-Java-main-native-analysis',development_candidate_sha256=pin(root/PROGRAM)['sha256'],
        development_build='./gradlew executableJar',development_adapter_cli='java -cp build/cli/omicsmatic-0.3.7.jar org.jlinalg.cli.TpmCalculatorCli run --request /trusted/request.json --out /NEW/output',
        selected_environment_is_author_environment=False,author_version_established=False,author_options_established=False,
        native_analytical_credit_added=1,new_scoped_integration_credit=1,source_roles_reviewed=1,
        selected_original_tag='0.0.4',original_printed_version='0.0.3',original006_binary_executed=False,
        workflow_validation='../tpmcalculator-workflow-development/QUALIFICATION.json',current_documentation=GUIDE,
        whole_tool_qualified=False,whole_paper_reproduced=False)
    return row

def transition(root,before):
    if set(before)!=set(META):raise ValueError('Eight exact predecessor metadata files required')
    inv,summary=json.loads(before[META[0]]),json.loads(before[META[1]])
    papers,document=json.loads(before[PAPERS]),json.loads(before[REVIEWS])
    if (len(inv),len(papers),sum(len(p['tools']) for p in papers),len(document['reviews']))!=(445,250,2689,1195):raise ValueError('Exact complete MELODI predecessor required')
    old=next(r for r in inv if r['id']=='tpmcalculator')
    if old['implementation_status']!='missing' or old['papers']!=['P050'] or any(r['id']=='idep' for r in inv):raise ValueError('Exact missing TPMCalculator / omitted iDEP predecessor required')
    if any(r['tool_id'] in {'tpmcalculator','idep'} for r in document['reviews']):raise ValueError('New reviews must not overwrite previously reviewed uses')
    inv[inv.index(old)]=qualified_entry(root,old)
    inv.append(dict(copy.deepcopy(DEFINITION),paper_count=1,papers=['P050'],implementation_status='missing',implementation_source=None,
        implementation_scope=MISSING_NOTE,validation_note='All250 primary sources searched; exact iDEP.96 interface use reviewed. No runtime result or integration credit.',
        source_roles_reviewed=1,new_scoped_integration_credit=0,whole_tool_qualified=False,whole_paper_reproduced=False))
    reviews=copy.deepcopy(audit(root)['reviews'])
    paper=next(p for p in papers if p['paper_id']=='P050')
    existing=[t for p in papers for t in p['tools'] if t['tool_id']=='tpmcalculator']
    if len(existing)!=1 or existing[0]['evidence_paragraph_index']!=45 or existing[0] not in paper['tools']:raise ValueError('Exactly the old P050 TPMCalculator use required')
    for review in reviews:
        review['implementation_scope_note']=NOTE if review['tool_id']=='tpmcalculator' else MISSING_NOTE
        observation=copy.deepcopy(existing[0]) if review['tool_id']=='tpmcalculator' else dict(tool_id='idep',tool='iDEP',analysis_role=DEFINITION['role'],matched_text='iDEP.96',extraction_score=15)
        observation.update(evidence_section=review['supporting_evidence'][0]['section'],evidence_paragraph_index=45,
            usage_context=review['usage_context'],evidence_selection='individually-reviewed-primary-source-identity-and-role',
            evidence_paragraph_sha256=review['evidence_paragraph_sha256'],evidence_locator=paper['fulltext_url']+' (section: '+review['supporting_evidence'][0]['section']+')',
            review_status='usage-summary-reviewed; paper-wide-completeness-review-open')
        for key in ['version_reported_near_name','use_summary','implementation_scope_note','supporting_evidence']:observation[key]=copy.deepcopy(review[key])
        if review['tool_id']=='tpmcalculator':paper['tools'][paper['tools'].index(existing[0])]=observation
        else:paper['tools'].append(observation)
    document['reviews']+=reviews
    updated=copy.deepcopy(summary)
    updated.update(distinct_named_tools=len(inv),paper_tool_observations=sum(len(p['tools']) for p in papers),implementation_status_counts=dict(Counter(r['implementation_status'] for r in inv)))
    counts=updated['implementation_status_counts']
    if (len(inv),updated['paper_tool_observations'],len(document['reviews']),counts['missing'],counts['upstream-adapter-validated'],counts['native-subset-validated'])!=(446,2690,1197,265,101,21):raise ValueError('Exact admitted/source-specific populations differ')
    page,warning=before[META[4]].decode(),before[META[5]].decode()
    match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    if not match or json.loads(match[2])!=dict(papers=json.loads(before[PAPERS]),tools=json.loads(before[META[0]]),summary=summary):raise ValueError('Complete predecessor embedded catalog differs')
    page=page[:match.start(2)]+json.dumps(dict(papers=papers,tools=inv,summary=updated),ensure_ascii=False).replace('<','\\u003c')+page[match.end(2):]
    historical='<details><summary>Previous MELODI checkpoint (historical)</summary>'+PRIOR_NOTICE+'</details>'
    marker='<p><a href="literature-review.html">Return to the 250-paper catalog</a></p>'
    if any(text.count(PRIOR_NOTICE)!=1 or text.count('</head>')!=1 for text in [page,warning]) or page.count('<p id="overview"></p>')!=1 or warning.count(marker)!=1:raise ValueError('Exact predecessor warning panels required')
    page=page.replace(PRIOR_NOTICE,historical,1).replace('<p id="overview"></p>',NOTICE+'<p id="overview"></p>',1).replace('</head>',STYLE+'</head>',1)
    warning=warning.replace(PRIOR_NOTICE,historical,1).replace(marker,marker+NOTICE,1).replace('</head>',STYLE+'</head>',1)
    intro='# 250-paper omics and GWAS toolbox catalog\n\n'+WARNING+'\nRead [native TPMCalculator commands and scope](../TPMCALCULATOR_CATALOG_INTEGRATION.md). Current cumulative entry point: `python tools/literature/tpmcalculator_catalog/catalog.py --verify --out build/NEW-check`; add `--rescan-sources` to check all250 primary XML files. Saved evidence checks do not repeat native scientific calls or establish biological validity. The full goal remains open.\n\n'
    pb=encoded(papers)
    if before[PAPERS].count(b'\n')==before[PAPERS].count(b'\r\n'):pb=pb.replace(b'\n',b'\r\n')
    return {META[0]:encoded(inv),META[1]:encoded(updated),PAPERS:pb,REVIEWS:encoded(document),
        META[2]:(intro+'<details><summary>Previous checkpoints (historical)</summary>\n\n'+before[META[2]].decode()+'\n</details>\n').encode(),
        META[3]:(WARNING+'\n<details><summary>Previous warnings (historical)</summary>\n\n').encode()+before[META[3]]+b'\n</details>\n',META[4]:page.encode(),META[5]:warning.encode()}

def check_transition(root,before,after):
    if set(after)!=set(META) or after!=transition(root,before):raise ValueError('Catalog differs from exact native TPMCalculator and iDEP source transition')
    return dict(papers=250,named_tools=446,observations=2690,reviewed_uses=1197,unreviewed_uses=1493,
        new_scoped_integrations=122,native_integrations=21,upstream_integrations=101,including_existing_adapters=125,missing=265,
        added_observations=1,added_reviews=2,new_registry_definitions=1,new_scoped_integration_credit=1,native_family_credit_added=1,whole_goal_complete=False)
