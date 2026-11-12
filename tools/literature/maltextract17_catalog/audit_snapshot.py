#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit exact current scientific/primary files with reviewed historical routing/docs.

The two named shared files are reconstructed from reviewed inverse records.
Every other file is an unchanged physical copy; historical validators are unchanged.
"""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,traceback
from dispatcher import reviewed_root,pin,PATH
from setup_deseq2_reference import ROOT,save
import audit_malt_source_catalog as inherited

README_BEFORE='85b8169c2217b4365ebfc98831637cfe0fb8b908bc88eb21154301bcc4d8928f'
README_AFTER='6ed9a78e743af50714fee07bd525b9bd3d484cafe1e4f4d0ed8233da1ba24fc7'
BLOCK=(b'The [native replacement guide](docs/native-tool-replacements.md) provides\n'
 b'Java-only local enrichment, QC/reporting, SAM/BAM processing, adapter trimming\n'
 b'and HTSeq file counting, with a [complete synthetic example](examples/native-tools/README.md).\n'
 b'Choose each native engine explicitly; existing upstream adapters remain available.\n\n')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);out=p.parse_args().out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime') and not out.exists();out.mkdir(parents=True)
 state=dict(status='FAIL2_PHYSICAL_CURRENT_SOURCE_CATALOG_AUDIT',whole_goal_complete=False)
 try:
  view,review=reviewed_root(ROOT);readme=(ROOT/'README.md').read_bytes()
  if pin(readme)['sha256']!=README_AFTER or readme.count(BLOCK)!=1:raise ValueError('README is outside the exact reviewed native-guide addition')
  restored=readme.replace(BLOCK,b'',1)
  if pin(restored)['sha256']!=README_BEFORE:raise ValueError('Historical README did not reconstruct exactly')
  names={n for n in subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0') if n}
  size=sum((ROOT/n).stat().st_size for n in names)
  if shutil.disk_usage(out).free<size+2*1024**3:raise OSError('Insufficient space for a complete unchanged scientific-source snapshot')
  snapshot=out/'snapshot';snapshot.mkdir();files={}
  for name in sorted(names):
   source=ROOT/name
   if source.is_symlink() or not source.is_file():raise ValueError('Only complete regular source-snapshot files are supported: '+name)
   target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);value=pin(target.read_bytes())
   if pin(source.read_bytes())!=value:raise ValueError('Source changed during snapshot: '+name)
   files[name]=value
  (snapshot/PATH).write_bytes((view/PATH).read_bytes());(snapshot/'README.md').write_bytes(restored)
  shared={PATH:dict(current=files[PATH],reviewed=pin((snapshot/PATH).read_bytes())), 'README.md':dict(current=files['README.md'],reviewed=pin(restored))}
  cache={};papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes())
  for paper in papers:
   name='build/literature-review/extracted/'+paper['pmcid']+'.json';source=ROOT/name;target=snapshot/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);cache[name]=pin(source.read_bytes())
   if pin(target.read_bytes())!=cache[name]:raise ValueError('Complete paper cache changed during capture')
  save(out/'SNAPSHOT.json',dict(files=files,primary_cache=cache,shared_predecessor_views=shared,scientific_source_files_overridden=False,primary_files_overridden=False))
  original_root,original_argv=inherited.ROOT,sys.argv
  try:
   inherited.ROOT=snapshot;sys.argv=[str(Path(inherited.__file__)),'--out',str(snapshot/'build/literature-review/nfcore-runtime/primary-audit')];code=inherited.main()
  finally:inherited.ROOT=original_root;sys.argv=original_argv
  result=json.loads((snapshot/'build/literature-review/nfcore-runtime/primary-audit/COMPLETED.json').read_bytes());save(out/'PRIMARY_AUDIT.json',result)
  for name,value in {**files,**cache}.items():
   if pin((ROOT/name).read_bytes())!=value:raise ValueError('Live source changed during catalog audit: '+name)
   expected=shared[name]['reviewed'] if name in shared else value
   if pin((snapshot/name).read_bytes())!=expected:raise ValueError('Read-only audit changed source-snapshot file: '+name)
  if code:raise ValueError('Unchanged audit refused the explicit snapshot; retain PRIMARY_AUDIT.json')
  state.update(status='PASS250_CURRENT_PRIMARY_AND_SCIENTIFIC_FILES_WITH_REVIEWED_ROUTING_AND_DOCS',primary_audit=result,dispatch_view=review,shared_predecessor_views=shared,source_files=len(files),scientific_source_files_overridden=False,primary_files_overridden=False,paper_wide_semantic_review_open=True,new_tool_credit=0)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc(),required_action='Retain every failed snapshot and diagnostic. Review the named source transition and qualify scientific changes before retrying a NEW audit. Never waive a scientific source hash or silently replace primary evidence.')
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source snapshot checks')
 raise SystemExit(main())
