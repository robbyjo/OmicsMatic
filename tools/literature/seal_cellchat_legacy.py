# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original legacy CellChat evidence with lossless blob deduplication."""
import argparse,json,shutil,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_legacy_evidence import verify,fingerprint
BASE=ROOT/'build/literature-review/cellchat-runtime';PUBLIC=ROOT/'docs/literature/cellchat-legacy-development'
COMPLETE=['legacy-typed-controls-7/analysis','legacy-typed-controls-8/analysis','legacy-projected-mouse-controls-2/analysis','legacy-prefilter-boundary-1/analysis','legacy-public-example-1']
PORTABLE=['legacy-portable-'+n+'-1' for n in ['win21-j21','win25-j21','win21-j25','win25-j25','linux21-j21','linux21-j25']]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);state=dict(status='FAIL2_LEGACY_EVIDENCE_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        previous=json.loads((ROOT/'docs/literature/cellchat-projection-development/VALIDATION.json').read_bytes());catalog=previous['catalog_unchanged'];canonical=previous['canonical_executable_unchanged'];assert all(pin(ROOT/n)==v for n,v in catalog.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        names=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['CellChatLegacyRequest','CellChatLegacyTables','CellChatLegacyAudit','CellChatLegacyCli','SimpleJson','NextflowParameters','NextflowFiles','NextflowJson']]
        names+=['src/main/java/org/jlinalg/omics/'+n+'.java' for n in ['CellChatProjection','CellChatNetworkSimilarity']]+['src/test/java/org/jlinalg/cli/CellChatLegacyAuditProbe.java','docs/literature/CELLCHAT_LEGACY_WORKFLOW_DEVELOPMENT.md']
        names+=['tools/literature/'+n for n in ['cellchat_legacy_workflow.R','cellchat-legacy-source-lock.json','export_cellchat_legacy_runtime.py','export_cellchat_legacy_ppi.R','build_cellchat_legacy.py','reference_cellchat_legacy.R','test_cellchat_legacy_workflow.py','check_cellchat_legacy_portable.py','audit_cellchat_legacy_paper.py','verify_cellchat_legacy_evidence.py','seal_cellchat_legacy.py','setup_deseq2_reference.py','setup_chipseeker_reference.py']]
        names+=['src/main/resources/org/jlinalg/omics/cellchat-legacy/'+n for n in ['worker.R','SOURCE-LOCK.json']]
        pins={n:pin(ROOT/n) for n in names};files={}
        def add(name,file):assert name not in files;files[name]=file
        for name in names:add('source/'+name,ROOT/name)
        add('LICENSES/GPL-3.0.txt',ROOT/'LICENSE');add('upstream/CELLCHAT_UPSTREAM_NOTICE.txt',ROOT/'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt')
        original=BASE/'source-intake-1/1.1.3';members=json.loads((original/'original-members.json').read_bytes());assert all(pin(original/'source'/n)==v for n,v in members.items())
        for file in sorted((original/'source').rglob('*')):
            if file.is_file():add('upstream/1.1.3/source/'+file.relative_to(original/'source').as_posix(),file)
        for n in ['original-members.json','download.json']:add('upstream/1.1.3/'+n,original/n)
        for directory in sorted(BASE.glob('legacy-*')):
            if not directory.is_dir() or directory.name.startswith('legacy-seal-'):continue
            for file in sorted(directory.rglob('*')):
                if file.is_file() and not (directory.name=='legacy-ppi-export-1' and file.name=='PPI.human.tsv'):add('evidence/'+directory.name+'/'+file.relative_to(directory).as_posix(),file)
        for name in ['legacy-public-example-controller.stdout','legacy-public-example-controller.stderr']:add('evidence/'+name,BASE/name)
        # Metadata/hashes are redistributable facts; the unlicensed author-code
        # downloads stay private and are not included in this evidence capsule.
        author=BASE/'p085-author-code-intake-1'
        for file in sorted(author.glob('*.json')):add('author-code-metadata/'+file.name,file)
        addendum=BASE/'legacy-p085-review-1/role-addendum.json';target=PUBLIC/'P085-source-addendum.json';assert not target.exists();shutil.copyfile(addendum,target)
        jars={}
        for compiler,label in [('javac21','legacy-build21-7'),('javac25','legacy-build25-3')]:
            report=json.loads((BASE/label/'COMPLETED.json').read_bytes());assert report['status']=='PASS_ISOLATED_CELLCHAT_LEGACY_WORKFLOW_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in report['source_pins'].items());source=BASE/label/'cellchat-legacy-development.jar';target=PUBLIC/('cellchat-legacy-'+compiler+'.jar');assert not target.exists();shutil.copyfile(source,target);jars[target.name]=pin(target)
        logical={};blobs={};archives={};z=None;number=0;logicalBytes=0
        try:
            for name,file in sorted(files.items()):
                assert file.is_file() and not file.is_symlink();raw=file.read_bytes();p=fingerprint(raw);logical[name]=p;logicalBytes+=len(raw);digest=p['sha256']
                if digest in blobs:continue
                if z is None or z.fp.tell()>28000000:
                    if z is not None:z.close();archives[archive.name]=pin(archive)
                    number+=1;archive=PUBLIC/f'controls-{number:02d}.zip';assert not archive.exists();z=zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)
                info=zipfile.ZipInfo('blobs/'+digest,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw);blobs[digest]=dict(archive=archive.name,**p)
        finally:
            if z is not None:z.close();archives[archive.name]=pin(archive)
        assert all(p['bytes']<90000000 for p in archives.values())
        examples={p.relative_to(PUBLIC).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file() and (p.parent.name=='example' or p.name=='P085-source-addendum.json')}
        proof=dict(status='PASS_ORIGINAL_CELLCHAT_1_1_3_CONDITION_WORKFLOW_DEVELOPMENT_ONLY',new_catalog_credit=False,whole_goal_complete=False,new_scoped_integrations=93,including_existing_adapters=96,missing=248,source_pins=pins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,development_jars=jars,example_and_addendum_files=examples,archives=archives,blobs=blobs,members=logical,logical_bytes=logicalBytes,portable_suites=PORTABLE,complete_bundles=COMPLETE,private_exclusions=['Previously retained R libraries/compiler','The487MB independent human-PPI dense decode; original RDA/source, decoder and exact decode hashes are retained','Unlicensed author-code contents; immutable URLs/blob/SHA metadata and verified facts are retained'],historical_paper_reproduced=False,all_cellchat_roles_completed=False)
        assert not (PUBLIC/'VALIDATION.json').exists();save(PUBLIC/'VALIDATION.json',proof);verify(PUBLIC,True)
        state.update(status='PASS_FROZEN_ORIGINAL_CELLCHAT_LEGACY_WORKFLOW_CHECKPOINT',source_pins=pins,published_files={p.relative_to(ROOT).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file()},catalog_unchanged=catalog,canonical_executable_unchanged=canonical,logical_members=len(logical),unique_blobs=len(blobs),archive_bytes=sum(p['bytes'] for p in archives.values()))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen source/evidence gates')
    raise SystemExit(main())
