# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Require unchanged complete source/independent-stage evidence before loading the frozen component gate."""
from pathlib import Path
import importlib.util,json,os
from cell2location_catalog.model import pin,WORKFLOW
def qualification(root):
 data=root/WORKFLOW;proof=json.loads((data/'VALIDATION.json').read_bytes())
 for name,wanted in proof['sources'].items():
  if pin(root/name)!=wanted:raise ValueError('Qualified original cell2location source/evidence differs: '+name)
 path=Path(os.fspath(root/'tools/literature/cell2location013/verify.py')).resolve();spec=importlib.util.spec_from_file_location('frozen_original_cell2location013_complete_workflow',path);v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v);result=v.evidence(root)
 if (result['original_result_files'],result['public_original_comparison']['complete_original_arrays'],result['public_original_comparison']['array_elements'],result['workflow_controls']['linux_workflow_controls'],result['comparator_refusals'],result['auxiliary']['frozen_verifier_refusals'])!=(684,647,65212,41,10,5):raise ValueError('Incomplete original source workflow/independent-stage/control qualification')
 if result['new_tool_credit'] or result['whole_goal_complete'] or result['paper_reproduced']:raise ValueError('Component qualification cannot confer catalog credit or paper reproduction')
 return result
