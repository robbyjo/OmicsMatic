# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact predecessor metadata; all original scientific files stay live."""
import json,zipfile
from run_mrmega_catalog_checks import ArchivedCatalogView
from p040_catalog.model import META,DATA,rawpin
EVIDENCE='docs/literature/imagej-catalog-integration'
def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only the eight catalog metadata paths may be overlaid; scientific overlays are forbidden')
 return ArchivedCatalogView(root,before)
def load_before(root):
 expected=json.loads((root/DATA/'p040-catalog-validation.json').read_bytes())['after_files']
 with zipfile.ZipFile(root/EVIDENCE/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Exact eight-path predecessor required')
  before={n:z.read(n) for n in META}
 if any(rawpin(before[n])!=expected[n] for n in META):raise ValueError('Predecessor differs from admitted429-tool catalog')
 return before
def prior_admission(root,before):
 from p040_catalog.verify import verify
 result=verify(metadata_view(root,before))
 if (result['papers'],result['named_tools'],result['observations'],result['reviewed_uses'],result['new_scoped_integrations'],result['missing'])!=(250,429,2668,1079,103,267):raise ValueError('Preserved predecessor admission differs')
 return result
