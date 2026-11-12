# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Scoped MaltExtract metadata derived from complete, pinned workflow evidence."""
from pathlib import Path
import hashlib,json

def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);n+=len(block)
 return dict(bytes=n,sha256=h.hexdigest())

WORKFLOW='docs/literature/maltextract17-workflow-development'
INTEGRATION='docs/literature/maltextract17-integration'

def qualification(root):
 data=root/WORKFLOW;validation=json.loads((data/'VALIDATION.json').read_bytes())
 if validation['status']!='PASS_ORIGINAL_MALTEXTRACT17_TARGET_SELECTION_EXTRACTION_AND_SUMMARY_WORKFLOW_CONTROLS' or validation['new_tool_credit']!=0:raise ValueError('Unsupported original workflow qualification scope')
 if pin(data/'controls.zip.xz')!=validation['archive']:raise ValueError('Complete workflow evidence archive differs')
 for name,value in validation['sources'].items():
  if pin(root/name)!=value:raise ValueError('Qualified workflow source changed: '+name)
 unit=json.loads((data/'UNIT_TESTS.json').read_bytes());totals=unit['totals']
 if totals!=dict(tests=21,failures=0,errors=0,skipped=0):raise ValueError('Incomplete focused regression qualification')
 replays=json.loads((data/'PORTABLE_REPLAYS.json').read_bytes())
 if replays['status']!='PASS_THREE_COMPLETE_ORIGINAL_WORKFLOW_REPLAYS_WINDOWS21_WINDOWS25_LINUX21' or len(replays['replays'])!=3:raise ValueError('Incomplete portable qualification')
 for row in replays['replays']:
  result=row['replay']
  if row['process']['exit_code']!=0 or result['status']!='PASS_COMPLETE_ORIGINAL_MALTEXTRACT17_WORKFLOW_SAVED_REPLAY' or result['complete_workflow_bundles']!=7 or result['metadata_refusals']!=8 or result['original_descendant_cases']!=76027 or result['original_name_cases']!=16 or sum(x['complete_original_scientific_files'] for x in result['original_scientific_comparisons'])!=516:raise ValueError('Incomplete original-workflow replay population')
 return validation,replays

def metadata(root):
 qualification(root)
 return dict(implementation_status='upstream-adapter-validated',
  implementation_source='src/main/java/org/jlinalg/cli/MaltExtractWorkflowCli.java',
  implementation_scope='Supervised unchanged original MaltExtract1.7 from the GPL HOPS0.35 distribution, with exact Linux OpenJDK8.0.144 runtime, literal target-name lookup, source-specific descendant selection, complete RMA6 snapshots, original target FASTA/BLAST extraction and all original summary/damage/coverage/distribution tables. Explicit source-default, complete-default and complete-def-anc profiles; complete profiles deliberately disable destacking, duplicate removal and downsampling. Native Java audits complete stored classes/counts and complete-profile read multisets. Manual candidate assessment remains a human task. Author version/settings/resources are unreported; the historical91-spirochete and7508-genome screening analyses and whole MALT/HOPS remain separate. Read MALTEXTRACT_INTEGRATION.md.',
  validation_note='Seven complete original productions,516 byte-identical scientific-file comparisons with independent originals,76027 original descendant cases and16 complete-map name cases. All21 focused tests passed without skips/failures. Complete saved replay passed on Windows JDK21/25 and Linux JDK21, including8 rehashed contradiction refusals per platform. Five failed attempts are retained, including original SEVERE output errors with exit0. A complete10001-read RMA showed stock processing stopping at10000 with downSampling? Off; the complete default profile retained10001. Saved verification does not independently recalculate arbitrary damage/coverage cells, repeat original fitting, authenticate a producer or establish biological accuracy.',
  coverage_note='The P002 supplementary DOCX names MaltExtract for target candidate extraction followed by manual summary/damage assessment. A declared1.7 software profile is now qualified for those output-producing operations; unreported author choices are not inferred.',
  project_adapter_available=True,canonical_adapter_available=False,root_dispatch_available=False,
  development_adapter_cli='java -jar maltextract17-workflow-jdk21.jar',
  development_candidate=INTEGRATION+'/maltextract17-workflow-jdk21.jar',
  development_candidate_sha256=pin(root/INTEGRATION/'maltextract17-workflow-jdk21.jar')['sha256'],
  development_build='tools/literature/maltextract17_workflow/build.py',
  source_roles_reviewed=1,source_role_coverage='maltextract17-adapter-role-coverage.json',
  workflow_validation='../maltextract17-workflow-development/VALIDATION.json',
  runtime_profiles=['source-default','complete-default','complete-def-anc'],
  external_analysis_runtime_required=True,whole_paper_reproduced=False,
  biological_accuracy_certified=False,producer_authenticated=False,
  paper_exact_unknown_settings_available=False,whole_tool_qualified=False)

WARNING='''

## MaltExtract: scoped original workflow and required actions

**Scoped target extraction and original summary/damage output are available.**
Use [MALTEXTRACT_INTEGRATION.md](../MALTEXTRACT_INTEGRATION.md) and the separate
development JAR; the canonical/released executable is unchanged. Runtime fitting
requires the pinned HOPS0.35 MaltExtract1.7/Linux OpenJDK8.0.144 provider, complete
RMA6, ncbi.map/ncbi.tre and literal target names. Choose profile and provenance
explicitly. Install/download only where permitted; otherwise SKIP3 the dependent
analysis. FAIL2 admits no result. Retain diagnostics and retry a NEW directory.

**Stock downsampling can be hidden by an Off flag.** The actual10001-read
control examined10000 and destacked to1 retained read while downSampling? was
Off. Skip full-population interpretation of that stock result. If complete
extraction is intended, explicitly choose a complete profile; it changes the
method by disabling sampling, destacking and duplicate removal.

Original NaN/Inf cells remain literal. Ambiguous table interpretation is skipped;
no imputation or padding occurs. SEVERE errors can accompany exit0 and refuse
admission. Review all original error/log files and the damage tables manually.
The damage denominator counts source-length>=20 alignments and differs from the
filter-table alignment count. Saved verification checks consistency and fixed
software-control oracles; arbitrary damage/coverage cells are not independently
recalculated. Checksums do not authenticate a producer or establish pathogen
authenticity/absence. Author settings/resources remain unknown. The paper's
separate bacterial rescreen and whole MALT/HOPS are not admitted by this badge.
'''
