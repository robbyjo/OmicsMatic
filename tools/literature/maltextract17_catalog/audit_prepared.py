#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""New audit snapshot with complete cached paper inputs; prior failure stays retained."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,sys,traceback
from dispatcher import pin
from setup_deseq2_reference import ROOT,save
import audit_malt_source_catalog as inherited

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--prepared',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();prepared=a.prepared.resolve();out=a.out.resolve();assert prepared.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 state=dict(status='FAIL2_COMPLETE_PREPARED_SOURCE_CATALOG_AUDIT',whole_goal_complete=False)
 try:
  old=json.loads((prepared/'SNAPSHOT.json').read_bytes());shared=old['shared_predecessor_views'];snapshot=out/'snapshot';snapshot.mkdir();files={}
  names={n for n in subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0') if n}
  for name in sorted(names):
   source=ROOT/name;actual=pin(source.read_bytes());target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True)
   if name in old['files']:
    if actual!=old['files'][name]:raise ValueError('Live source changed since reviewed snapshot: '+name)
    prior=prepared/'snapshot'/name;expected=shared[name]['reviewed'] if name in shared else actual
    if pin(prior.read_bytes())!=expected:raise ValueError('Private immutable snapshot bytes changed: '+name)
    # Links join only retained private source snapshots. Live project files are
    # never linked, and neither snapshot is modified by the read-only audit.
    os.link(prior,target)
   else:shutil.copyfile(source,target)
   files[name]=actual
  papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());cache={}
  for paper in papers:
   name='build/literature-review/extracted/'+paper['pmcid']+'.json';source=ROOT/name;target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);cache[name]=pin(source.read_bytes())
   if pin(target.read_bytes())!=cache[name]:raise ValueError('Complete paper cache changed during capture')
  save(out/'SNAPSHOT.json',dict(files=files,shared_predecessor_views=shared,primary_cache=cache,scientific_source_files_overridden=False,primary_files_overridden=False,prior_failed_snapshot=str(prepared)))
  original_root,original_argv=inherited.ROOT,sys.argv
  try:
   inherited.ROOT=snapshot;sys.argv=[str(Path(inherited.__file__)),'--out',str(snapshot/'build/literature-review/nfcore-runtime/primary-audit')];code=inherited.main()
  finally:inherited.ROOT=original_root;sys.argv=original_argv
  result=json.loads((snapshot/'build/literature-review/nfcore-runtime/primary-audit/COMPLETED.json').read_bytes());save(out/'PRIMARY_AUDIT.json',result)
  for name,value in {**files,**cache}.items():
   if pin((ROOT/name).read_bytes())!=value:raise ValueError('Live source/evidence changed during audit: '+name)
   expected=shared[name]['reviewed'] if name in shared else value
   if pin((snapshot/name).read_bytes())!=expected:raise ValueError('Read-only audit changed snapshot: '+name)
  if code:raise ValueError('Unchanged source audit refused this complete snapshot; retain PRIMARY_AUDIT.json')
  state.update(status='PASS250_CURRENT_PRIMARY_SOURCES_WITH_REVIEWED_DISPATCH_AND_DOCS',primary_audit=result,source_files=len(files),primary_cache_files=len(cache),shared_predecessor_views=shared,scientific_source_files_overridden=False,primary_files_overridden=False,scientific_verifiers_changed=False,new_tool_credit=0,paper_wide_semantic_review_open=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc(),required_action='Retain prior and current snapshots and every diagnostic; review the exact named source/evidence failure before a NEW audit. No source-hash waiver or primary-evidence repair is allowed.')
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable full source/evidence checks')
 raise SystemExit(main())
