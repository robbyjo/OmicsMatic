# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal deduplicated current CellChat workflow evidence; preserve catalog/released artifacts."""
import argparse,hashlib,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from cellchat_workflow_evidence import verify

BASE=ROOT/'build/literature-review/cellchat-runtime';PUBLIC=ROOT/'docs/literature/cellchat-workflow-development'
COMPLETE=['typed-controls-7','typed-controls-8','workflow-edges-2','workflow-failures-2','direct-reference-1','direct-comparison-2','public-workflow-example-1','standard-resource-check-1','portable-verification-1','guide-example-check-1','runtime-profile-1','presto-metadata-1','presto-metadata-2','presto-source-intake-1']
SETUPS=['reference-setup-1','presto-reference-setup-1','presto-reference-setup-2']
HISTORY=['original-workflow-controls-1','typed-controls-1','typed-controls-2','typed-controls-3','typed-controls-4','typed-controls-5','typed-controls-6','workflow-edges-1','workflow-failures-1','direct-comparison-1']

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_WORKFLOW_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        assert not (PUBLIC/'VALIDATION.json').exists()
        old=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cellchat-development-1/selection.json').read_bytes());held=old['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items())
        canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==old['canonical']
        status=subprocess.check_output(['git','status','--porcelain=v1','--untracked-files=all','-z'],cwd=ROOT).split(b'\0');sources=[]
        for row in status:
            if not row:continue
            name=row[3:].decode();file=ROOT/name
            own=(name.startswith(('src/main/java/org/jlinalg/cli/CellChatWorkflow','src/test/java/org/jlinalg/cli/CellChatWorkflow','src/main/resources/org/jlinalg/omics/cellchat-workflow/')) or name.startswith('tools/literature/') and 'cellchat' in file.name or name=='docs/literature/CELLCHAT_WORKFLOW_DEVELOPMENT.md')
            if own:assert file.is_file() and not file.is_symlink();sources.append(file)
        sources += [ROOT/'tools/literature'/n for n in ['setup_deseq2_reference.py','setup_chipseeker_reference.py']]
        sources += [ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in ['NextflowFiles','NextflowParameters','NextflowJson','SimpleJson']]
        sourcePins={p.relative_to(ROOT).as_posix():pin(p) for p in sorted(set(sources))}
        catalogPaths=[ROOT/'docs/literature/omics-2023-2026'/n for n in ['papers.json','tool-inventory.json','README.md','SHA256SUMS']]+[ROOT/'tools/literature/reviewed-uses.json'];catalog={p.relative_to(ROOT).as_posix():pin(p) for p in catalogPaths}
        candidate=out/'candidate';candidate.mkdir();artifacts={}
        for name in ['cellchat-workflow-javac21.jar','cellchat-workflow-javac25.jar']:
            shutil.copyfile(PUBLIC/name,candidate/name);artifacts[name]=pin(candidate/name)
        for file in sorted((PUBLIC/'example').rglob('*')):
            if file.is_file():
                relative=file.relative_to(PUBLIC);target=candidate/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target);artifacts[relative.as_posix()]=pin(target)
        index={};origins={}
        def add(virtual,file):
            assert virtual not in index and file.is_file() and not file.is_symlink();value=pin(file);assert value['bytes']<=67108864
            index[virtual]=value;origins.setdefault(value['sha256'],(file,value))
        for folder in COMPLETE:
            if folder=='presto-metadata-1':
                # This early input capture has original raw API files and no
                # completion claim. The later reproducible capture has one.
                assert all((BASE/folder/name).is_file() for name in ['repository.json','tags.json','DESCRIPTION'])
            else:assert (BASE/folder/'COMPLETED.json').is_file(),folder
            for file in sorted((BASE/folder).rglob('*')):
                if not file.is_file():continue
                relative=file.relative_to(BASE/folder)
                if folder=='presto-source-intake-1' and relative.parts[0]=='source':continue
                add('evidence/'+folder+'/'+relative.as_posix(),file)
        for folder in SETUPS:
            for file in sorted((BASE/folder).rglob('*')):
                if not file.is_file():continue
                relative=file.relative_to(BASE/folder)
                if relative.parts[0] in {'R-lib','work-source','downloads','tmp'}:continue
                add('evidence/'+folder+'/'+relative.as_posix(),file)
            # Keep dependency download metadata/URLs, not redistributable copies
            # of the entire preexisting R installation or compiler.
            for file in sorted((BASE/folder/'downloads').glob('*.json')):add('evidence/'+folder+'/downloads/'+file.name,file)
        for folder in HISTORY:
            for file in sorted((BASE/folder).rglob('*')):
                if file.is_file() and (file.suffix in {'.json','.stdout','.stderr'} or file.name.endswith(('stdout','stderr')) or 'source-snapshot' in file.parts):add('history/'+folder+'/'+file.relative_to(BASE/folder).as_posix(),file)
        for folder in ['workflow-seal-1','sealer-before-raw-metadata-fix-1']:
            for file in sorted((BASE/folder).rglob('*')):
                if file.is_file() and 'candidate' not in file.relative_to(BASE/folder).parts:add('history/'+folder+'/'+file.relative_to(BASE/folder).as_posix(),file)
        for folder in sorted(BASE.glob('workflow-build*')):
            if folder.is_dir():
                for file in sorted(folder.rglob('*')):
                    if file.is_file():add('history/'+folder.name+'/'+file.relative_to(folder).as_posix(),file)
        for name in sourcePins:add('source-snapshot/'+name,ROOT/name)
        for file in sorted((PUBLIC/'example').rglob('*')):
            if file.is_file():add('example/'+file.relative_to(PUBLIC/'example').as_posix(),file)
        add('licenses/GPL-3.0.txt',ROOT/'LICENSE');add('licenses/CellChat-upstream-notice.txt',ROOT/'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt')
        cellchat=BASE/'source-intake-1/2.2.0-2025-release/source';sourceLock=json.loads((ROOT/'tools/literature/cellchat-workflow-source-lock.json').read_bytes())['sources']['2.2.0-2025-release']['files']
        for name,wanted in sourceLock.items():assert pin(cellchat/name)==wanted;add('original-source/CellChat2.2.0/'+name,cellchat/name)
        presto=BASE/'presto-source-intake-1/source';prestoMembers=json.loads((BASE/'presto-source-intake-1/original-members.json').read_bytes())
        for name,wanted in prestoMembers.items():assert pin(presto/name)==wanted;add('original-source/presto1.0.0/'+name,presto/name)
        blobs={};shards={};current=None;currentName='';used=0
        try:
            for digest,(file,wanted) in sorted(origins.items()):
                if current is None or used+wanted['bytes']>41943040:
                    if current is not None:current.close();shards[currentName]=pin(candidate/currentName)
                    currentName='evidence-'+str(len(shards)+1).zfill(2)+'.zip';current=zipfile.ZipFile(candidate/currentName,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9);used=0
                info=zipfile.ZipInfo('blobs/'+digest,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
                # Every retained byte is included once, even where failure/tamper
                # copies repeat complete earlier bundles. Paths remain explicit.
                with file.open('rb') as source,current.open(info,'w') as target:shutil.copyfileobj(source,target,1048576)
                assert pin(file)==wanted;blobs[digest]=dict(shard=currentName,bytes=wanted['bytes']);used+=wanted['bytes']
            if current is not None:current.close();current=None;shards[currentName]=pin(candidate/currentName)
        finally:
            if current is not None:current.close()
        assert all(v['bytes']<83886080 for v in shards.values())
        proof=dict(status='PASS_ORIGINAL_CELLCHAT_TYPED_WORKFLOW_DEVELOPMENT_ONLY',schema_version=1,new_catalog_credit=False,whole_goal_complete=False,new_scoped_integrations=93,including_existing_adapters=96,missing=248,source_label='2.2.0-2025-release',CellChat_version='2.2.0',presto_version='1.0.0',R_profile='WindowsR4.6.1',typed_original_fits=16,edge_original_fits=16,separate_direct_package_refits=8,literal_guide_example_fits=8,portable_complete_bundle_checks=30,actual_disposition_controls=17,rehashed_refusals=8,original_centrality_fallbacks_per_baseline_workflow=122,rare_family_positive_pooled_zero_sample_scores=448,constant_family_nonfinite_Wilcoxon_results=315,
            source_pins=sourcePins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,unrelated_files_preserved=18,artifacts=artifacts,shards=shards,files=index,blobs=blobs,raw_metadata_captures_without_completion_claim=['presto-metadata-1'],
            archive_layout='Each virtual file path is indexed by bytes/SHA256; blobs are stored once across numbered ZIP shards. Use cellchat_workflow_evidence.py to verify/materialize NEW paths. Complete current analysis bundles and preferred original source trees are included. Existing R libraries/compiler/dependency binaries and older successful prototype bulk outputs remain outside this public capsule; their saved hashes/metadata and history source snapshots are retained. Original downloaded CellChat ZIP bytes are not included; its complete original extracted tree is included and independently member-pinned. This is integrity/structural evidence, not producer authentication or historical paper reproduction.')
        save(candidate/'VALIDATION.json',proof);verify(candidate,True)
        assert all(pin(ROOT/n)==v for n,v in sourcePins.items()) and all(pin(ROOT/n)==v for n,v in catalog.items()) and all(pin(ROOT/n)==v for n,v in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        for file in sorted(candidate.rglob('*')):
            if not file.is_file():continue
            target=PUBLIC/file.relative_to(candidate);target.parent.mkdir(parents=True,exist_ok=True)
            if target.exists():assert pin(target)==pin(file),'Existing tested artifact differs'
            else:shutil.copyfile(file,target)
        verify(PUBLIC,True)
        state.update(status='PASS_FROZEN_CELLCHAT_ORIGINAL_WORKFLOW_CHECKPOINT',published_files={p.relative_to(ROOT).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file()},source_pins=sourcePins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,unrelated_files_preserved=18,shards=len(shards),unique_blobs=len(blobs),virtual_files=len(index))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence/protected-file guards')
    raise SystemExit(main())
