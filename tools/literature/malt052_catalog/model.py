# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact one-MALT derived catalog admission; primary records remain unchanged."""
from pathlib import Path
from collections import Counter
import copy,hashlib,json,re
ROOT=Path(__file__).resolve().parents[3]
DATA='docs/literature/omics-2023-2026';INTEGRATION='docs/literature/malt052-workflow-integration';WORKFLOW='docs/literature/malt052-workflow-development'
META=tuple(DATA+'/'+n for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md'])+('site/literature-review.html','site/literature-warnings.html')
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);size+=len(b)
 return dict(bytes=size,sha256=h.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(v):return (json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8')
def qualification(root):
 d=root/WORKFLOW;v=json.loads((d/'VALIDATION.json').read_bytes())
 if v['status']!='PASS_COMPLETE_OWNED_MALT052_BUILD_BLASTN_WORKFLOW_CONTROLS' or v['new_tool_credit']!=0 or v['whole_tool_credit']!=0 or v['whole_goal_complete'] or v['original_accession_mapping_qualified'] or v['historical_database_reconstructed'] or v['producer_authenticated'] or v['original_programs_bundled']:raise ValueError('Unsupported complete MALT workflow qualification scope')
 if pin(d/'controls.zip.xz')!=v['archive']['pin']:raise ValueError('Complete MALT workflow archive differs')
 for n,value in v['sources'].items():
  if pin(root/n)!=value:raise ValueError('Qualified complete MALT workflow source differs: '+n)
 unit=json.loads((d/'UNIT_TESTS.json').read_bytes());pop=json.loads((d/'COMPLETE_POPULATIONS.json').read_bytes());refusals=json.loads((d/'SPECIFIC_REFUSALS.json').read_bytes());policy=json.loads((d/'PREREQUISITES.json').read_bytes());portable=json.loads((d/'PORTABLE_REPLAYS.json').read_bytes());public=json.loads((d/'PUBLIC_REPLAY.json').read_bytes())
 if set(unit['results'])!={'21','25'} or any(r['totals']!=dict(tests=39,failures=0,errors=0,skipped=0) for r in unit['results'].values()):raise ValueError('Incomplete current MALT focused/adjacent tests')
 if pop['status']!='PASS_FOUR_COMPLETE_OWNED_MALT052_BUILD_AND_BLASTN_WORKFLOWS' or [r['case'] for r in pop['results']]!=['A','B','dna5','support59'] or any(not r['scientific_matches_previous_complete_alignment'] or r['source_counters']!=dict(references=3,reference_bases=52244) for r in pop['results']):raise ValueError('Incomplete original MALT build/alignment populations')
 if (sum(r['scientific']['input_queries'] for r in pop['results']),sum(r['scientific']['alignments'] for r in pop['results']))!=(16862,122):raise ValueError('Complete MALT query/alignment population differs')
 if refusals['status']!='PASS_TEN_RESEALED_COMPLETE_MALT052_WORKFLOW_CONTRADICTIONS' or len(refusals['calls'])!=10 or not refusals['specific_causes_checked'] or any(r['exit_code']!=2 for r in refusals['calls']):raise ValueError('Incomplete MALT semantic contradiction checks')
 if policy['status']!='PASS_THREE_MALT052_WORKFLOW_PREREQUISITE_AND_COMPLETE_CAPACITY_CONTROLS' or len(policy['calls'])!=3 or any(r['exit_code']!=r['expected_exit'] for r in policy['calls']) or any(r!=dict(failure=True,complete=False,original_launched=False) for r in policy['proof'].values()):raise ValueError('MALT missing-runtime/capacity controls differ')
 if portable['status']!='PASS_CURRENT_COMPLETE_MALT052_WORKFLOWS_WINDOWS21_WINDOWS25_LINUX21' or (portable['windows21_complete_populations'],portable['windows25_complete_populations'],portable['linux21_complete_populations'])!=(4,1,4) or any(r['exit_code'] for r in portable['calls']):raise ValueError('Incomplete MALT portable saved replay')
 if pin(d/'PUBLIC_REPLAY.json')!=v['saved_replay'] or public['status']!='PASS_COMPLETE_OWNED_MALT052_BUILD_BLASTN_SAVED_REPLAY' or (public['complete_workflows'],public['specific_refusals'],public['restored_members'])!=(5,10,1317) or public['original_fitting_repeated'] or any(r['exit_code']!=r['expected_exit'] for r in public['calls']):raise ValueError('Incomplete complete-archive public MALT replay')
 if v['complete_workflows']!=['A','B','dna5','support59','current-A'] or not v['initial_producer_source_preserved'] or not v['unchanged_scientific_components'] or v['current_source_freshly_produced']!='current-A':raise ValueError('Current versus initial MALT producer scope differs')
 return v,public
def metadata(root):
 qualification(root)
 return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/Malt052WorkflowCli.java',
 implementation_scope='Owned complete DNA FASTA/index-build to original MALT0.5.2 BlastN workflow with explicit native-accession-alternative mapping. Retains unclassified original and separately mapped indexes; captures every reference, mapping and query, enforces identical complete index handoff and checks original SAM/RMA/aligned FASTA geometry and naive/readCount classification. Explicit CUSTOM052/ STANDALONE profiles and original resource/process receipts. CUSTOM052 represents the supplement-reported SemiGlobal/90%/25-alignments/100000-per-reference screen; the main pipeline version is unreported. Read MALT052_INTEGRATION.md. Historical database/query reproduction, original0.5.2 accession-map path, full MALT/HOPS/nf-core equivalence and biological authentication remain separate.',
 validation_note='Five complete original build/alignment productions across four full populations; first four evaluate16862 queries and122 alignments and match previous complete original controls, with three references/52244 bases each. Current source freshly produces A and replays all earlier results. All39 focused/adjacent tests pass on Windows JDK21 and25. Saved verification covers four populations on Windows21/Linux21 and A on Windows25; Linux25 execution is untested. Public archive restores1317 complete logical files and passes five workflows/ten specific resealed refusals. Three prerequisite/capacity controls launch no original analysis. Initial sources and failed attempts are retained. Software controls do not replace the historical databases or establish biological truth.',
 coverage_note='P002 uses MALT in an eager/HOPS screen with a dated2017 reference and separately reports custom MALT0.5.2 index creation/BlastN screening. The declared0.5.2 workflow supplies those DNA build/alignment operations with an explicit native mapping alternative. The original0.5.2 accession-map path remains unqualified. Historical2017/2021 reference populations, complete study queries and130GiB/16-worker execution remain external requirements; that resource request is representable but not claimed executed.',
 project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,development_adapter_cli='java -jar malt052-workflow-jdk21.jar',development_candidate=WORKFLOW+'/malt052-workflow-jdk21.jar',development_candidate_sha256=pin(root/WORKFLOW/'malt052-workflow-jdk21.jar')['sha256'],development_build='tools/literature/malt052_workflow/build.py',source_roles_reviewed=2,source_role_coverage='malt052-adapter-role-coverage.json',workflow_validation='../malt052-workflow-development/VALIDATION.json',runtime_profiles=['CUSTOM052','STANDALONE'],external_analysis_runtime_required=True,whole_paper_reproduced=False,biological_accuracy_certified=False,producer_authenticated=False,paper_exact_unknown_settings_available=False,whole_tool_qualified=False)
WARNING='''

## MALT: complete DNA index-to-alignment workflow and required actions

**Complete original MALT0.5.2 seed building and BlastN screening are available
through a scoped standalone workflow.** Read
[MALT052_INTEGRATION.md](../MALT052_INTEGRATION.md). The canonical/released
executable and root dispatcher are unchanged. Select CUSTOM052 or STANDALONE
explicitly. The supplement reports0.5.2 for the custom screen; the main pipeline
version remains unreported.

**Select native-accession-alternative mapping explicitly.** Original0.5.2's
accession-map path remains unqualified. Preserve its unclassified native/index
and the separate mapped derived/index. The adapter checks complete reference,
mapping, query and original output populations plus the exact full index
handoff; declared capacities refuse rather than substitute a prefix.

**Missing/forbidden runtime, installation or execution: SKIP3 dependent analysis.**
Supply only permitted exact prerequisites and retry a NEW directory, or leave
the dependent analysis skipped. **FAIL2 admits no result:** retain FAILURE.json,
WARNINGS_FAILURE.md and both original stderr logs. Correct the named input,
resource, process or scientific/integrity issue before a NEW run. A completed
index can remain independently usable after later alignment fails.

The historical2017/2021/91-genome databases and complete study queries are not
reconstructed. The130GiB/16-worker request is representable but not claimed
executed. Current controls use three complete mitochondrial references and
cannot replace the paper's database. Empty/insufficient alignments never prove
absence. Saved verification repeats no original fitting and authenticates no
producer. Whole MALT/HOPS/nf-core/paper and biological reproduction remain open.
Older source-component notices are retained as historical qualification scope.
'''
NOTICE='<section id="malt052-workflow-actions"><h2>MALT: complete DNA index-to-alignment workflow and required actions</h2><p><strong>Complete original0.5.2 seed building and BlastN screening are available through a scoped standalone workflow.</strong> Read <code>docs/literature/MALT052_INTEGRATION.md</code>. Select CUSTOM052 or STANDALONE explicitly; the main pipeline version remains unreported.</p><p><strong>Select native-accession-alternative mapping explicitly.</strong> Original0.5.2 accession mapping remains unqualified. Preserve the unclassified original and separately mapped indexes. Complete inputs and the entire identical index handoff are checked; capacities refuse rather than truncate. Missing/forbidden installation/runtime/execution SKIP3 dependent analysis. FAIL2 admits no result: retain diagnostics, correct the named issue and retry a NEW directory. Historical databases and study queries are not reconstructed;130GiB/16-worker execution is not claimed. Empty or insufficient alignments do not prove absence. Saved verification authenticates no producer and establishes no biological truth.</p></section>'
def transition(before,entry):
 if set(before)!=set(META):raise ValueError('Only the six derived catalog metadata paths may have predecessors')
 inventory=json.loads(before[META[0]]);summary=json.loads(before[META[1]])
 if len(inventory)!=405 or len({r['id'] for r in inventory})!=405:raise ValueError('Named tool population differs')
 old=next(r for r in inventory if r['id']=='malt')
 if old['implementation_status']!='missing' or old['papers']!=['P002']:raise ValueError('Unexpected MALT predecessor role/status')
 new=copy.deepcopy(inventory);next(r for r in new if r['id']=='malt').update(entry);counts=dict(Counter(r['implementation_status'] for r in new));expected=dict(summary['implementation_status_counts']);expected['upstream-adapter-validated']+=1;expected['missing']-=1
 if counts!=expected or counts['upstream-adapter-validated']!=83 or counts['missing']!=244:raise ValueError('Admission must change only one MALT count')
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=counts;readme=before[META[2]].decode('utf8')
 substitutions=[('**Current checkpoint: 101 new scoped integrations**','**Current checkpoint: 102 new scoped integrations**'),('subsets and 82 tested upstream adapters; 104 including','subsets and 83 tested upstream adapters; 105 including'),('**245 named tools remain missing.**','**244 named tools remain missing.**')]
 for old,newtext in substitutions:
  if readme.count(old)!=1:raise ValueError('Unexpected predecessor checkpoint text: '+old)
  readme=readme.replace(old,newtext,1)
 marker="Cell Ranger remains excluded at the user's request. The full goal is active."
 if readme.count(marker)!=1:raise ValueError('Missing fixed goal/exclusion boundary')
 readme=readme.replace(marker,marker+'\n\n**MALT complete DNA index building and original0.5.2 BlastN screening are now\navailable** through an owned standalone workflow with an explicit native\naccession-mapping alternative and complete index handoff checks. Read\n[MALT052_INTEGRATION.md](../MALT052_INTEGRATION.md) for exact prerequisites,\nmethod choices and prominent SKIP3/FAIL2 recovery. The main pipeline version\nand historical reference/query populations are not recovered. Historical\nsource-component notices remain retained; no whole-tool or biological credit.',1)
 page=before[META[4]].decode('utf8');m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or 'id="malt052-workflow-actions"' in page:raise ValueError('Unexpected predecessor review page')
 embedded=json.loads(m[2])
 if embedded['tools']!=inventory or embedded['summary']!=summary:raise ValueError('Predecessor rendered catalog differs')
 embedded['tools']=new;embedded['summary']=updated;raw=json.dumps(embedded,ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+raw+page[m.end(2):];page=page.replace('<script id="review-data"',NOTICE+'<script id="review-data"',1)
 warnings=before[META[5]].decode('utf8')
 if warnings.count('</main>')!=1 or 'id="malt052-workflow-actions"' in warnings:raise ValueError('Unexpected predecessor warning page')
 return {META[0]:encoded(new),META[1]:encoded(updated),META[2]:readme.encode('utf8'),META[3]:before[META[3]]+WARNING.encode('utf8'),META[4]:page.encode('utf8'),META[5]:warnings.replace('</main>',NOTICE+'</main>',1).encode('utf8')}
def check_transition(before,after,entry):
 expected=transition(before,entry)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Live catalog is outside the exact one-MALT derived-metadata transition')
 return dict(changed_tool_ids=['malt'],old_new_scoped_integrations=[101,102],old_new_missing=[245,244],primary_files_overridden=False,scientific_sources_overridden=False)
