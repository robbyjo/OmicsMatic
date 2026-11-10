# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original CellChat1.0.0/2.1.2 inference evidence with lossless blob deduplication."""
import argparse,json,shutil,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_inference_evidence import verify,fingerprint
BASE=ROOT/'build/literature-review/cellchat-runtime';PUBLIC=ROOT/'docs/literature/cellchat-inference-development'
COMPLETE=[f'inference-controls-{label}-javac{co}-{n}/analysis' for label,n in [('1.0.0',3),('2.1.2',2)] for co in [21,25]]+['inference-filter-boundary-2/'+tag+'/analysis' for tag in ['unfiltered','filtered']]
PORTABLE=['inference-portable-'+name+'-1' for name in ['win21-j21','win25-j25']]+['inference-portable-'+name+'-2' for name in ['linux21-j21','linux21-j25']]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);state=dict(status='FAIL2_INFERENCE_EVIDENCE_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        previous=json.loads((ROOT/'docs/literature/cellchat-legacy-development/VALIDATION.json').read_bytes());catalog=previous['catalog_unchanged'];canonical=previous['canonical_executable_unchanged'];assert all(pin(ROOT/n)==v for n,v in catalog.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        names=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['CellChatInferenceRequest','CellChatInferenceTables','CellChatInferenceAudit','CellChatInferenceCli','CellChatInferencePpi','SimpleJson','NextflowParameters','NextflowFiles','NextflowJson']]
        names+=['src/main/java/org/jlinalg/omics/CellChatProjection.java','src/test/java/org/jlinalg/cli/CellChatInferenceProbe.java','docs/literature/CELLCHAT_INFERENCE_DEVELOPMENT.md']
        names+=['tools/literature/'+n for n in ['cellchat_inference_workflow.R','cellchat-inference-source-lock.json','export_cellchat_inference_runtime.py','export_cellchat_ppi_binary.R','build_cellchat_inference.py','reference_cellchat_inference.R','reference_cellchat_inference_exports.R','test_cellchat_inference.py','check_cellchat_inference_portable.py','audit_cellchat_inference_papers.py','verify_cellchat_inference_evidence.py','seal_cellchat_inference.py','setup_cellchat_editions.py','install_cellchat_editions.R','setup_deseq2_reference.py','setup_chipseeker_reference.py']]
        names+=['src/main/resources/org/jlinalg/omics/cellchat-inference/'+n for n in ['worker.R','SOURCE-LOCK.json']];pins={n:pin(ROOT/n) for n in names};files={}
        def add(name,file):assert name not in files;files[name]=file
        for name in names:add('source/'+name,ROOT/name)
        add('LICENSES/GPL-3.0.txt',ROOT/'LICENSE');add('upstream/CELLCHAT_UPSTREAM_NOTICE.txt',ROOT/'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt')
        for label in ['1.0.0','2.1.2']:
            original=BASE/'source-intake-1'/label;members=json.loads((original/'original-members.json').read_bytes());assert all(pin(original/'source'/n)==v for n,v in members.items())
            for file in sorted((original/'source').rglob('*')):
                if file.is_file():add('upstream/'+label+'/source/'+file.relative_to(original/'source').as_posix(),file)
            for n in ['original-members.json','download.json']:add('upstream/'+label+'/'+n,original/n)
            # Receipts and inventories only; private installed libraries/compiler
            # are not distributed. Original source is above, unchanged.
            setup=BASE/f'edition-{label}-setup-1';report=json.loads((setup/'COMPLETED.json').read_bytes());assert report['status']=='INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY' and report['preexisting_libraries_unchanged']
            for n in ['COMPLETED.json','STARTED.json','LIBRARIES-BEFORE.json','LIBRARIES-AFTER.json','installer.stdout','installer.stderr','process.json','R-RESULT.json']:add('setup/'+label+'/'+n,setup/n)
        for directory in sorted(BASE.glob('inference-*')):
            if not directory.is_dir() or directory.name.startswith(('inference-seal-','inference-materialized-')):continue
            for file in sorted(directory.rglob('*')):
                if file.is_file():add('evidence/'+directory.name+'/'+file.relative_to(directory).as_posix(),file)
        for file in sorted(BASE.glob('inference-portable-*.stdout')):add('evidence/'+file.name,file)
        for file in sorted(BASE.glob('inference-portable-*.stderr')):add('evidence/'+file.name,file)
        for file in sorted((BASE/'p100-author-intake-1').glob('*.json')):add('author-code-metadata/'+file.name,file)
        target=PUBLIC/'paper-source-addendum.json';assert not target.exists();shutil.copyfile(BASE/'inference-paper-review-1/paper-addendum.json',target)
        jars={}
        for compiler,label in [('javac21','inference-build21-8'),('javac25','inference-build25-6')]:
            report=json.loads((BASE/label/'COMPLETED.json').read_bytes());assert report['status']=='PASS_ISOLATED_CELLCHAT_INFERENCE_WORKFLOW_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in report['source_pins'].items());source=BASE/label/'cellchat-inference-development.jar';target=PUBLIC/('cellchat-inference-'+compiler+'.jar');assert not target.exists();shutil.copyfile(source,target);jars[target.name]=pin(target)
        logical={};blobs={};archives={};z=None;number=0;logicalBytes=0
        try:
            for name,file in sorted(files.items()):
                assert file.is_file() and not file.is_symlink();raw=file.read_bytes();value=fingerprint(raw);logical[name]=value;logicalBytes+=len(raw);digest=value['sha256']
                if digest in blobs:continue
                if z is None or z.fp.tell()>28000000:
                    if z is not None:z.close();archives[archive.name]=pin(archive)
                    number+=1;archive=PUBLIC/f'controls-{number:02d}.zip';assert not archive.exists();z=zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)
                info=zipfile.ZipInfo('blobs/'+digest,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw);blobs[digest]=dict(archive=archive.name,**value)
        finally:
            if z is not None:z.close();archives[archive.name]=pin(archive)
        assert all(value['bytes']<90000000 for value in archives.values())
        examples={p.relative_to(PUBLIC).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file() and ('examples' in p.relative_to(PUBLIC).parts or p.name=='paper-source-addendum.json')}
        proof=dict(status='PASS_ORIGINAL_CELLCHAT_1_0_0_AND_2_1_2_INFERENCE_DEVELOPMENT_ONLY',new_catalog_credit=False,whole_goal_complete=False,new_scoped_integrations=93,including_existing_adapters=96,missing=248,source_pins=pins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,development_jars=jars,example_and_addendum_files=examples,archives=archives,blobs=blobs,members=logical,logical_bytes=logicalBytes,portable_suites=PORTABLE,complete_bundles=COMPLETE,private_exclusions=['Installed R libraries and compiler; setup inventories are retained','Unlicensed author-code contents; immutable metadata and verified facts are retained'],historical_paper_reproduced=False,all_cellchat_roles_completed=False)
        assert not(PUBLIC/'VALIDATION.json').exists();save(PUBLIC/'VALIDATION.json',proof);verify(PUBLIC,True)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in catalog.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        state.update(status='PASS_FROZEN_ORIGINAL_CELLCHAT_INFERENCE_CHECKPOINT',source_pins=pins,published_files={p.relative_to(ROOT).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file()},catalog_unchanged=catalog,canonical_executable_unchanged=canonical,logical_members=len(logical),unique_blobs=len(blobs),archive_bytes=sum(value['bytes'] for value in archives.values()))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen source/evidence checks')
    raise SystemExit(main())
