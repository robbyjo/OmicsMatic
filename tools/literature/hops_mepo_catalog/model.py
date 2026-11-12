# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Canonical scoped HOPS metadata and exact derived-catalog transition."""
from pathlib import Path
from collections import Counter
import copy,hashlib,json,re
ROOT=Path(__file__).resolve().parents[3]
DATA='docs/literature/omics-2023-2026'
INTEGRATION='docs/literature/hops-mepo-integration'
WORKFLOW='docs/literature/hops-mepo-development'
META=tuple(DATA+'/'+n for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md'])+('site/literature-review.html','site/literature-warnings.html')
def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(v):return (json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8')
def qualification(root):
 d=root/WORKFLOW;v=json.loads((d/'VALIDATION.json').read_bytes())
 if v['status']!='PASS_ORIGINAL_HOPS035_MEPO_COMPLETE_OPERATION_CONTROLS' or v['new_tool_credit']!=0 or v['whole_hops_qualified'] or v['whole_goal_complete']:raise ValueError('Unsupported original HOPS operation qualification')
 if pin(d/'controls.zip.xz')!=v['archive']:raise ValueError('Complete HOPS evidence archive differs')
 for name,value in v['sources'].items():
  if pin(root/name)!=value:raise ValueError('Qualified HOPS source changed: '+name)
 unit=json.loads((d/'UNIT_TESTS.json').read_bytes())
 if set(unit['results'])!={'21','25'} or any(r['totals']!=dict(tests=41,failures=0,errors=0,skipped=0) for r in unit['results'].values()):raise ValueError('Incomplete HOPS native regression')
 public=json.loads((d/'PUBLIC_REPLAY.json').read_bytes());replay=public['complete_replay']
 if pin(d/'PUBLIC_REPLAY.json')!=v['saved_replay'] or public['status']!='PASS_CURRENT_SOURCE_COMPLETE_MEPO_PUBLIC_REPLAY' or replay['status']!='PASS_COMPLETE_ORIGINAL_HOPS_MEPO_SAVED_REPLAY':raise ValueError('Incomplete current-source HOPS replay')
 if (replay['complete_workflows'],replay['specific_refusals'],replay['independent_original_comparisons'],replay['restored_members'])!=(3,10,2,5665) or any(c['exit_code']!=c['expected_exit'] for c in replay['commands']):raise ValueError('HOPS replay population differs')
 comp=json.loads((d/'ORIGINAL_COMPARISONS.json').read_bytes())
 if comp['status']!='PASS_BOTH_ORIGINAL_HOPS_MEPO_PROFILES_FULL_OUTPUT_COMPARISONS' or {r['profile'] for r in comp['results']}!={'source-def-anc','complete-def-anc'} or any(len(r['scientific_files'])!=173 or r['source_statistic_cells']!=280 for r in comp['results']) or len(comp['scientific_files_differing_between_profiles'])!=51:raise ValueError('Incomplete independent HOPS profile comparison')
 return v,public,comp
def metadata(root):
 qualification(root)
 return dict(implementation_status='upstream-adapter-validated',implementation_source='src/main/java/org/jlinalg/cli/HopsMepoCli.java',
  implementation_scope='Owned unchanged HOPS_Main me_po from the declared GPL HOPS0.35 distribution, original MaltExtract1.7 under pinned Java8 and original AMPS under public R4.0.5. Complete classified RMA6/resource capture, literal target lookup, original extraction/summary/damage tables, candidate PDFs, heatmap/TSV/RData and native complete-field checks. Explicit source-def-anc retains original sampling/destacking/deduplication; complete-def-anc deliberately disables them and changes the method. Manual authentication, upstream MALT index/alignment, author settings and historical database reconstruction remain separate. Read HOPS_INTEGRATION.md.',
  validation_note='Three complete original productions; independent stock/complete launcher comparisons match173 scientific files and280 RData fields per profile, with two/four expected PDFs and51 differing scientific files between methods. All41 focused/adjacent tests pass on Windows JDK21 and25. Complete public replay restores5665 logical files, freshly decodes all three workspaces and rejects10 specifically rehashed contradictions. Four dependency/capacity/prefix controls launch no upstream analysis. Original driver exit0 with plotting-child exit1 is retained and unadmitted. Linux JDK21 execution includes the JDK25-built application; Linux JDK25 execution is untested. Saved consistency checks do not authenticate a producer or establish biological truth.',
  coverage_note='P002 reports HOPS assessment of MALT output and candidate profiles; low-read candidates can remain unassessable. The declared tested version/profiles implement those output-producing operations. Author version/settings are unreported and are not inferred. The 2017 database and custom MALT0.5.2 screening remain distinct requirements.',
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
  development_adapter_cli='java -jar hops-mepo-jdk21.jar',development_candidate=WORKFLOW+'/hops-mepo-jdk21.jar',
  development_candidate_sha256=pin(root/WORKFLOW/'hops-mepo-jdk21.jar')['sha256'],development_build='tools/literature/hops_mepo/build.py',
  source_roles_reviewed=1,source_role_coverage='hops-mepo-adapter-role-coverage.json',workflow_validation='../hops-mepo-development/VALIDATION.json',
  runtime_profiles=['source-def-anc','complete-def-anc'],external_analysis_runtime_required=True,
  whole_paper_reproduced=False,biological_accuracy_certified=False,producer_authenticated=False,paper_exact_unknown_settings_available=False,whole_tool_qualified=False)
WARNING='''

## HOPS: original candidate assessment and required actions

**Original HOPS me_po extraction, damage-related candidate plots and workspace
assessment are available through a scoped standalone adapter.** Use
[HOPS_INTEGRATION.md](../HOPS_INTEGRATION.md). The canonical/released executable
and root dispatcher are unchanged. Author version/settings are unknown; the
declared HOPS0.35/MaltExtract1.7/Java8/R4.0.5 profile is a tested choice.

**Select the method explicitly.** source-def-anc preserves original sampling,
destacking and duplicate removal. Stock1.7 can inspect only the first10,000
assigned reads even if downSampling? says Off. complete-def-anc deliberately
disables those operations and changes results; do not switch just to pass a
plot/check. Captured raw inputs and complete populations are never silently
repaired or truncated by adapter capacities. Nonfinite values stay literal.

**Missing/forbidden exact runtime or installation: SKIP3 dependent analysis.**
Install permitted pinned providers in NEW directories or leave it skipped.
**FAIL2: no result admitted.** Retain FAILURE.json, WARNINGS_FAILURE.md and all
process stderr. Fix the named input/capacity/integrity/process problem before a
NEW run. The original driver can return0 after a child returns1; both child
exits, fatal logs and complete outputs are checked. Failed/empty plots and
low-read evidence do not establish absence. Review original profiles manually.

Saved verification freshly decodes RData but repeats no original fitting,
authenticates no producer and establishes no biological accuracy. MALT
index/alignment, dated reference databases, manual authentication and whole
HOPS/MALT/paper reproduction remain separate. Earlier source-only qualification
notices below are historical and do not negate this scoped operation badge.
'''
NOTICE='<section id="hops-mepo-actions"><h2>HOPS: original candidate assessment and required actions</h2><p><strong>Original me_po extraction and candidate plots/workspace are available through a scoped standalone adapter.</strong> Read <code>docs/literature/HOPS_INTEGRATION.md</code>. The canonical executable is unchanged; author version/settings and historical databases are not recovered.</p><p><strong>Choose stock or complete explicitly:</strong> stock retains original sampling/destacking/deduplication, including possible10,000-read prefix sampling despite an Off flag. Complete deliberately changes the method. Missing/forbidden prerequisites SKIP3 dependent analysis. FAIL2 admits no result: retain diagnostics and retry a NEW directory after resolving the named problem. Two child exits and fatal logs are checked despite a potentially zero driver exit. Failed/empty plots and insufficient evidence do not prove absence. Review original profiles manually; saved verification does not authenticate a producer or establish biological truth.</p></section>'
def transition(before,entry):
 if set(before)!=set(META):raise ValueError('Predecessor view may contain only the six exact derived metadata files')
 inventory=json.loads(before[META[0]]);summary=json.loads(before[META[1]])
 if len(inventory)!=405 or len({r['id'] for r in inventory})!=405:raise ValueError('Named tool population differs')
 old=next(r for r in inventory if r['id']=='hops')
 if old['implementation_status']!='missing' or old['papers']!=['P002']:raise ValueError('Unexpected HOPS predecessor role/status')
 new=copy.deepcopy(inventory);next(r for r in new if r['id']=='hops').update(entry)
 counts=dict(Counter(r['implementation_status'] for r in new));expected=dict(summary['implementation_status_counts']);expected['upstream-adapter-validated']+=1;expected['missing']-=1
 if counts!=expected or counts['upstream-adapter-validated']!=82 or counts['missing']!=245:raise ValueError('Admission must change only one HOPS count')
 updated=copy.deepcopy(summary);updated['implementation_status_counts']=counts
 readme=before[META[2]].decode('utf8');oldhead='**Current checkpoint: 100 new scoped integrations**';newhead='**Current checkpoint: 101 new scoped integrations**'
 if readme.count(oldhead)!=1 or readme.count('subsets and 81 tested upstream adapters; 103 including')!=1 or readme.count('**246 named tools remain missing.**')!=1:raise ValueError('Unexpected predecessor checkpoint text')
 readme=readme.replace(oldhead,newhead,1).replace('subsets and 81 tested upstream adapters; 103 including','subsets and 82 tested upstream adapters; 104 including',1).replace('**246 named tools remain missing.**','**245 named tools remain missing.**',1)
 marker="Cell Ranger remains excluded at the user's request. The full goal is active."
 if readme.count(marker)!=1:raise ValueError('Missing fixed goal/exclusion boundary')
 readme=readme.replace(marker,marker+'\n\n**HOPS original extraction and candidate assessment are now available** through\na scoped standalone me_po workflow with explicit stock/complete profiles. Read\n[HOPS_INTEGRATION.md](../HOPS_INTEGRATION.md) for setup, method choices and\nprominent SKIP3/FAIL2 recovery. Historical source-only notices remain retained;\nthe badge does not automate biological authentication, recover author settings\nor qualify MALT alignment/index creation or the historical databases.',1)
 page=before[META[4]].decode('utf8');m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
 if not m or 'id="hops-mepo-actions"' in page:raise ValueError('Unexpected predecessor review page')
 embedded=json.loads(m[2])
 if embedded['tools']!=inventory or embedded['summary']!=summary:raise ValueError('Predecessor rendered catalog differs')
 embedded['tools']=new;embedded['summary']=updated;raw=json.dumps(embedded,ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+raw+page[m.end(2):];page=page.replace('<script id="review-data"',NOTICE+'<script id="review-data"',1)
 warnings=before[META[5]].decode('utf8')
 if warnings.count('</main>')!=1 or 'id="hops-mepo-actions"' in warnings:raise ValueError('Unexpected predecessor warning page')
 return {META[0]:encoded(new),META[1]:encoded(updated),META[2]:readme.encode('utf8'),META[3]:before[META[3]]+WARNING.encode('utf8'),META[4]:page.encode('utf8'),META[5]:warnings.replace('</main>',NOTICE+'</main>',1).encode('utf8')}
def check_transition(before,after,entry):
 expected=transition(before,entry)
 if set(after)!=set(META) or any(after[n]!=expected[n] for n in META):raise ValueError('Live catalog is outside the exact single-HOPS derived-metadata transition')
 return dict(changed_tool_ids=['hops'],old_new_scoped_integrations=[100,101],old_new_missing=[246,245],primary_files_overridden=False,scientific_sources_overridden=False)
