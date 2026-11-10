# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original CellChat2.2.0 strength evidence with lossless blob deduplication."""
import argparse,json,shutil,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_strength_evidence import verify,fingerprint
BASE=ROOT/'build/literature-review/cellchat-runtime';PUBLIC=ROOT/'docs/literature/cellchat-strength-development'
COMPLETE=[f'strength-controls-3x4-javac{co}-2/analysis' for co in [21,25]]+['strength-edge-'+n+'-1/analysis' for n in ['rare-zero','missing-group','missing-feature','asymptotic-zero','missing-pooled-feature']]
PORTABLE=['strength-portable-'+vm+'-j'+str(co)+'-1' for co in [21,25] for vm in ['win21','win25','linux21']]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True);state=dict(status='FAIL2_STRENGTH_EVIDENCE_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        previous=json.loads((ROOT/'docs/literature/cellchat-legacy-development/VALIDATION.json').read_bytes());catalog=previous['catalog_unchanged'];canonical=previous['canonical_executable_unchanged'];assert all(pin(ROOT/n)==v for n,v in catalog.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        names=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['CellChatStrengthRequest','CellChatStrengthTables','CellChatStrengthAudit','CellChatStrengthCli','CellChatStrengthComparison','SimpleJson','NextflowParameters','NextflowFiles','NextflowJson']]
        names+=['src/main/java/org/jlinalg/omics/CellChatSignalingStrengths.java','src/test/java/org/jlinalg/cli/CellChatStrengthProbe.java','docs/literature/CELLCHAT_STRENGTH_DEVELOPMENT.md']
        names+=['tools/literature/'+n for n in ['cellchat_strength_workflow.R','cellchat_strength_comparison.R.inc','cellchat-strength-source-lock.json','build_cellchat_strength.py','reference_cellchat_strength_workflow.R','reference_cellchat_strength_exports.R','reference_cellchat_strength_prepared.R','test_cellchat_strength.py','replay_cellchat_strength_refusals.py','check_cellchat_strength_portable.py','audit_cellchat_strength_paper.py','verify_cellchat_strength_evidence.py','seal_cellchat_strength.py','export_cellchat_runtime_profile.py','setup_deseq2_reference.py','setup_chipseeker_reference.py']]
        names+=['src/main/resources/org/jlinalg/omics/cellchat-strength/'+n for n in ['worker.R','SOURCE-LOCK.json']];pins={n:pin(ROOT/n) for n in names};files={}
        def add(name,file):assert name not in files;files[name]=file
        for name in names:add('source/'+name,ROOT/name)
        add('LICENSES/GPL-3.0.txt',ROOT/'LICENSE');add('upstream/CELLCHAT_UPSTREAM_NOTICE.txt',ROOT/'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt')
        label='2.2.0-2025-release';original=BASE/'source-intake-1'/label;members=json.loads((original/'original-members.json').read_bytes());assert all(pin(original/'source'/n)==v for n,v in members.items())
        for file in sorted((original/'source').rglob('*')):
            if file.is_file():add('upstream/'+label+'/source/'+file.relative_to(original/'source').as_posix(),file)
        for n in ['original-members.json','download.json']:add('upstream/'+label+'/'+n,original/n)
        for label in ['reference-setup-1','presto-reference-setup-2','runtime-profile-1']:add('setup/'+label+'/COMPLETED.json',BASE/label/'COMPLETED.json')
        for directory in sorted(BASE.glob('strength-*')):
            if not directory.is_dir() or directory.name.startswith(('strength-seal-','strength-materialized-')):continue
            for file in sorted(directory.rglob('*')):
                if file.is_file():add('evidence/'+directory.name+'/'+file.relative_to(directory).as_posix(),file)
        for file in sorted(BASE.glob('strength-portable-*.stdout')):add('evidence/'+file.name,file)
        for file in sorted(BASE.glob('strength-portable-*.stderr')):add('evidence/'+file.name,file)
        target=PUBLIC/'P145-source-addendum.json';assert not target.exists();shutil.copyfile(BASE/'strength-paper-review-2/paper-addendum.json',target)
        jars={}
        for compiler,label in [('javac21','strength-build21-3'),('javac25','strength-build25-3')]:
            report=json.loads((BASE/label/'COMPLETED.json').read_bytes());assert report['status']=='PASS_ISOLATED_CELLCHAT_STRENGTH_WORKFLOW_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in report['source_pins'].items());source=BASE/label/'cellchat-strength-development.jar';target=PUBLIC/('cellchat-strength-'+compiler+'.jar');assert not target.exists();shutil.copyfile(source,target);jars[target.name]=pin(target)
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
        examples={p.relative_to(PUBLIC).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file() and ('example' in p.relative_to(PUBLIC).parts or p.name=='P145-source-addendum.json')}
        proof=dict(status='PASS_ORIGINAL_CELLCHAT_STRENGTH_WORKFLOW_DEVELOPMENT_ONLY',new_catalog_credit=False,whole_goal_complete=False,new_scoped_integrations=93,including_existing_adapters=96,missing=248,source_pins=pins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,development_jars=jars,example_and_addendum_files=examples,archives=archives,blobs=blobs,members=logical,logical_bytes=logicalBytes,portable_suites=PORTABLE,complete_bundles=COMPLETE,private_exclusions=['Installed R libraries and compiler; setup inventories are retained','Primary article text; source hashes and factual paper-role notes are retained'],historical_paper_reproduced=False,all_cellchat_roles_completed=False)
        assert not(PUBLIC/'VALIDATION.json').exists();save(PUBLIC/'VALIDATION.json',proof);verify(PUBLIC,True)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in catalog.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        state.update(status='PASS_FROZEN_ORIGINAL_CELLCHAT_STRENGTH_CHECKPOINT',source_pins=pins,published_files={p.relative_to(ROOT).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file()},catalog_unchanged=catalog,canonical_executable_unchanged=canonical,logical_members=len(logical),unique_blobs=len(blobs),archive_bytes=sum(value['bytes'] for value in archives.values()))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen source/evidence checks')
    raise SystemExit(main())
