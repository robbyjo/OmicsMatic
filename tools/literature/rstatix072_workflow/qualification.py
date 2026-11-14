# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay the unchanged native/provider checkpoint with only eight preserved catalog inputs."""
from pathlib import Path
import importlib.util,json,os,zipfile
from qupath_catalog.model import META
from run_mrmega_catalog_checks import ArchivedCatalogView

NATIVE='docs/literature/rstatix072-development-checkpoint'
def pin(path):
 import hashlib
 raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only the exact eight catalog metadata paths may be overlaid; scientific overlays are forbidden')
 return ArchivedCatalogView(root,before)
def load_before(root,data):
 proof=json.loads((root/NATIVE/'VALIDATION.json').read_bytes())
 with zipfile.ZipFile(data/'catalog-before.zip') as archive:
  names=archive.namelist()
  if len(names)!=8 or set(names)!=set(META):raise ValueError('Exact eight-path catalog predecessor required')
  before={n:archive.read(n) for n in META}
 import hashlib
 if any(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=proof['main_catalog_files'][n] for n,raw in before.items()):
  raise ValueError('Preserved native/provider checkpoint catalog differs')
 return before
def qualification(root,data):
 proof=json.loads((root/NATIVE/'VALIDATION.json').read_bytes())
 # Check live scientific bytes before importing the frozen verifier. The view
 # below changes only eight historical catalog inputs, never scientific code,
 # inputs, results, source profiles, evidence archives or verifier definitions.
 for n,wanted in proof['sources'].items():
  if pin(root/n)!=wanted:raise ValueError('Qualified native/provider scientific source differs: '+n)
 before=load_before(root,data)
 file=Path(os.fspath(root/'tools/literature/rstatix072/verify.py')).resolve()
 spec=importlib.util.spec_from_file_location('retained_rstatix072_native_provider',file);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 module.ROOT=metadata_view(root,before)
 result=module.evidence(False)
 if result['complete_archive_files']!=3852 or result['original_namespace_functions']!=339 or result['original_exports']!=126 or result['new_tool_credit']!=0:
  raise ValueError('Incomplete original native/provider checkpoint')
 return dict(original_native_provider=result,scientific_sources_overridden=False,scientific_verifiers_changed=False,
  preserved_catalog_inputs=8,original_analysis_executed=False)
