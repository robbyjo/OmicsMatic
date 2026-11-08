# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged scientific gates and the exact archived980 metadata gate, alongside current988-role checks."""
import argparse,contextlib,gzip,importlib,io,json,os,sys
from pathlib import Path
from types import SimpleNamespace
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from palantir_catalog import current_catalog_review_context,verify_review_extension

class ArchivedFile:
 def __init__(self,raw):self.raw=raw
 def read_bytes(self):return self.raw
 def read_text(self,encoding='utf-8'):return self.raw.decode(encoding)
 def open(self,mode):assert mode=='rb';return io.BytesIO(self.raw)
 def stat(self):return SimpleNamespace(st_size=len(self.raw))

class ArchivedCatalogView:
 """Only the two exact historical metadata inputs are virtual; scientific paths resolve to the real tree."""
 def __init__(self,root,overrides,relative=''):self.root=root;self.overrides=overrides;self.relative=relative
 def __fspath__(self):return os.fspath(self.root/self.relative)
 def __str__(self):return os.fspath(self)
 def __truediv__(self,name):
  relative=(Path(self.relative)/name).as_posix()
  if relative in self.overrides:return ArchivedFile(self.overrides[relative])
  if any(n.startswith(relative+'/') for n in self.overrides):return ArchivedCatalogView(self.root,self.overrides,relative)
  return self.root/relative
 def glob(self,pattern):return (self.root/self.relative).glob(pattern)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/palantir-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';names=['soupx_markers_evidence','soupx_ambient_core_evidence','soupx_workflow_evidence','dndscv_workflow_evidence','smr_statistics_evidence','smr_workflow_evidence','loftee_audit_evidence','loftee_workflow_evidence','verify_cellphonedb_review_context','biomart_workflow_evidence','hail_sample_qc_evidence','hail_workflow_evidence','leafcutter_clusters_evidence','leafcutter_phenotypes_evidence','leafcutter_models_evidence','leafcutter_workflow_evidence','cellphonedb_statistics_evidence','cellphonedb_preparation_evidence','validate_cellphonedb_integration_evidence','cellphonedb_hash_seed_evidence','spyrrow_communication_evidence','palantir_multiscale_evidence','palantir_workflow_evidence'];modules={n:importlib.import_module(n) for n in names};checks={};overrides={'tools/literature/reviewed-uses.json':gzip.decompress((data/'palantir-prior-reviews.json.gz').read_bytes()),'docs/literature/omics-2023-2026/papers.json':gzip.decompress((data/'palantir-prior-papers.json.gz').read_bytes())};assert len(json.loads(overrides['tools/literature/reviewed-uses.json'])['reviews'])==980
 with current_catalog_review_context():
  verify_review_extension(ROOT,data,json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])
  for name,module in modules.items():
   captured=io.StringIO();saved_root=None
   if name=='verify_cellphonedb_review_context':saved_root=module.ROOT;module.ROOT=ArchivedCatalogView(ROOT,overrides)
   try:
    with contextlib.redirect_stdout(captured):result=module.verify()
   finally:
    if saved_root is not None:module.ROOT=saved_root
    (out/(name+'.stdout')).write_text(captured.getvalue(),encoding='utf8',newline='\n')
   checks[name]=dict(status=result['status'],input_scope='exact archived980-role metadata inputs; all live scientific source paths strict' if saved_root is not None else 'current live scientific sources with exact review-extension context');print(name+': passed',flush=True)
 files=[Path(__file__).resolve(),ROOT/'tools/literature/palantir_catalog.py',*[ROOT/'tools/literature'/(n+'.py') for n in names]];save(out/'COMPLETED.json',dict(status='passed-twenty-three-frozen-and-additive-Palantir-gates',checks=checks,current_reviews=988,prior_metadata_reviews=980,metadata_snapshot_overrides={n:dict(bytes=len(raw),sha256=__import__('hashlib').sha256(raw).hexdigest()) for n,raw in overrides.items()},source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in files},scientific_verifiers_changed=False,whole_goal_complete=False));print('Passed23 unchanged/additive gates; archived metadata input scope is explicit')
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable scientific source gates')
 main()
