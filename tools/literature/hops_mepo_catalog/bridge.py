# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""An exact six-file metadata predecessor; scientific and primary paths stay live."""
from pathlib import Path
import json,sys,zipfile
TOOLS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS/'maltextract17_catalog'));sys.path.insert(0,str(TOOLS))
from hops_mepo_catalog.model import ROOT,DATA,INTEGRATION,META,pin,rawpin,metadata,check_transition
from run_mrmega_catalog_checks import ArchivedCatalogView
from maltextract17_catalog import verify as prior

def metadata_view(root,before):
 if set(before)!=set(META):raise ValueError('Only the six derived catalog metadata paths may have predecessors')
 return ArchivedCatalogView(root,before)
def protected(root,expected):
 for name,value in expected.items():
  if pin(root/name)!=value:raise ValueError('Protected source/evidence changed: '+name)
def predecessor(root=ROOT):
 proof=json.loads((root/DATA/'hops-mepo-adapter-validation.json').read_bytes())
 archive=root/INTEGRATION/'catalog-before.zip'
 if pin(archive)!=proof['before_archive']:raise ValueError('Exact HOPS predecessor archive differs')
 with zipfile.ZipFile(archive) as z:
  before={name:z.read(name) for name in META}
 old=json.loads((root/DATA/'maltextract17-adapter-validation.json').read_bytes())
 if set(old['after_files'])!=set(META):raise ValueError('Unexpected prior admission metadata paths')
 if any(rawpin(before[n])!=old['after_files'][n] for n in META):raise ValueError('HOPS predecessor does not match the exact prior admission')
 protected(root,proof['protected']);current={n:(root/n).read_bytes() for n in META}
 transition=check_transition(before,current,metadata(root))
 return metadata_view(root,before),dict(paths=list(META),before={n:rawpin(v) for n,v in before.items()},transition=transition,primary_files_overridden=False,scientific_sources_overridden=False)
def prior_admission(root,before):
 result=prior.verify(metadata_view(root,before))
 if result['status']!='PASS_ONE_SCOPED_MALTEXTRACT_ADMISSION_WITH_PRESERVED_PRIMARY_EVIDENCE' or result['new_scoped_integrations']!=100 or result['missing']!=246:raise ValueError('Unchanged prior scientific/catalog admission did not pass')
 return result
