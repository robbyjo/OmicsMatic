# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact primary amendment: only eight metadata paths receive historical views."""
import json,sys,zipfile
from pathlib import Path
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from batchelor120_catalog.model import DATA,EVIDENCE,META,PAPERS,REVIEWS,pin,rawpin
from run_mrmega_catalog_checks import ArchivedCatalogView

def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only the eight reviewed metadata paths may have predecessors; scientific paths cannot be overlaid')
 return ArchivedCatalogView(root,before)

def load_before(root):
 proof=json.loads((root/DATA/'malt052-adapter-validation.json').read_bytes());archive=root/EVIDENCE/'catalog-before.zip'
 with zipfile.ZipFile(archive) as z:
  if len(z.namelist())!=len(META) or set(z.namelist())!=set(META):raise ValueError('Unexpected predecessor archive members')
  before={n:z.read(n) for n in META}
 expected=dict(proof['after_files']);expected.update({n:proof['protected'][n] for n in [PAPERS,REVIEWS]})
 if set(expected)!=set(META) or any(rawpin(before[n])!=expected[n] for n in META):raise ValueError('Archived predecessor differs from the admitted MALT102 state')
 return before

def prior_admission(root,before):
 from malt052_catalog.verify import verify
 result=verify(metadata_view(root,before))
 if result['new_scoped_integrations']!=102 or result['missing']!=244 or result['observations']!=2479 or result['reviewed_uses']!=1052:raise ValueError('Preserved predecessor admission failed')
 return result
