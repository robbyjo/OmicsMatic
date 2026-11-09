# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze completed native projection controls into a new, independently checked checkpoint."""
import argparse,json,shutil,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_projection import verify,bytepin
BASE=ROOT/'build/literature-review/cellchat-runtime';PUBLIC=ROOT/'docs/literature/cellchat-projection-development'
SUITES=['projection-controls-win21-j21-2','projection-controls-win25-j21-1','projection-controls-win21-j25-1','projection-controls-win25-j25-1','projection-controls-linux21-j21-1','projection-controls-linux21-j25-1']

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_PROJECTION_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        prior=json.loads((ROOT/'docs/literature/cellchat-workflow-development/VALIDATION.json').read_bytes());catalog=prior['catalog_unchanged'];canonical=prior['canonical_executable_unchanged']
        assert all(pin(ROOT/n)==v for n,v in catalog.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        source_names=['src/main/java/org/jlinalg/omics/CellChatProjection.java','src/main/java/org/jlinalg/cli/CellChatProjectionCli.java','src/test/java/org/jlinalg/cli/CellChatProjectionProbe.java','docs/literature/CELLCHAT_PROJECTION_DEVELOPMENT.md']
        source_names+=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['SimpleJson','NextflowFiles','NextflowJson','NextflowParameters']]
        source_names+=['tools/literature/'+n for n in ['setup_cellchat_editions.py','install_cellchat_editions.R','build_cellchat_projection.py','collect_cellchat_projection.py','reference_cellchat_projection.R','test_cellchat_projection.py','verify_cellchat_projection.py','seal_cellchat_projection.py','setup_deseq2_reference.py','setup_chipseeker_reference.py']]
        source_pins={n:pin(ROOT/n) for n in source_names};members={};files={}
        def add(name,file):assert name not in files;files[name]=file
        for n in source_names:add('source/'+n,ROOT/n)
        add('LICENSES/GPL-3.0.txt',ROOT/'LICENSE');add('upstream/CELLCHAT_UPSTREAM_NOTICE.txt',ROOT/'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt')
        original=BASE/'source-intake-1/1.1.3';upstream=json.loads((original/'original-members.json').read_bytes());assert all(pin(original/'source'/n)==v for n,v in upstream.items())
        for file in sorted((original/'source/R').glob('*.R')):add('upstream/1.1.3/source/R/'+file.name,file)
        for n in ['DESCRIPTION','NAMESPACE']:add('upstream/1.1.3/source/'+n,original/'source'/n)
        for n in ['original-members.json','download.json']:add('upstream/1.1.3/'+n,original/n)
        directories=sorted(p for p in BASE.glob('projection-*') if p.is_dir() and not p.name.startswith('projection-seal-'))
        for directory in directories:
            for file in sorted(directory.rglob('*')):
                if file.is_file():add('evidence/'+directory.name+'/'+file.relative_to(directory).as_posix(),file)
        for label in ['edition-1.1.3-setup-1','edition-forbidden-control-1']:
            directory=BASE/label
            for file in sorted(directory.rglob('*')):
                rel=file.relative_to(directory)
                if file.is_file() and not set(rel.parts)&{'R-lib','work-source','downloads','tmp'}:add('evidence/'+label+'/'+rel.as_posix(),file)
        for file in sorted((ROOT/'build/cellchat-projection-example-1').iterdir()):add('evidence/public-example/'+file.name,file)
        jars={}
        for compiler,directory in [('javac21','projection-build21-3'),('javac25','projection-build25-2')]:
            build=json.loads((BASE/directory/'COMPLETED.json').read_bytes());assert build['status']=='PASS_ISOLATED_CELLCHAT_PROJECTION_BUILD_ONLY';assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
            file=BASE/directory/'cellchat-projection-development.jar';target=PUBLIC/('cellchat-projection-'+compiler+'.jar');assert not target.exists();shutil.copyfile(file,target);jars[target.name]=pin(target)
        reports=[json.loads((BASE/n/'COMPLETED.json').read_bytes()) for n in SUITES];assert all(r['status']=='PASS_NATIVE_CELLCHAT_PROJECTION_CONTROLS_ONLY' and r['protocols']==126 for r in reports)
        maximum=max(r['maximum_scaled_error'] for r in reports);archive=PUBLIC/'controls.zip';assert not archive.exists() and not (PUBLIC/'VALIDATION.json').exists()
        print('Freezing',len(files),'complete source/evidence files',flush=True)
        with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,file in sorted(files.items()):
                assert not file.is_symlink();raw=file.read_bytes();members[name]=bytepin(raw);info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
        assert archive.stat().st_size<90000000
        proof=dict(status='PASS_NATIVE_CELLCHAT_PPI_PROJECTION_DEVELOPMENT_ONLY',new_catalog_credit=False,whole_goal_complete=False,new_scoped_integrations=93,including_existing_adapters=96,missing=248,source_pins=source_pins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,development_jars=jars,archive=pin(archive),members=members,native_suites=SUITES,original_synthetic_families=50,original_numerical_comparisons=300,maximum_scaled_error=maximum,protocols_per_compiler_jvm=126,expected_analysis_refusals_per_suite=19,offline_tamper_refusals_per_suite=6,original_source_candidate='fea1c8ecdea0f393d32513cd00b866f8b4457839',reference_R='Windows R4.6.1',historical_paper_environment=False,full_cellchat_paper_roles_completed=False)
        save(PUBLIC/'VALIDATION.json',proof);verify(PUBLIC,True)
        published={p.relative_to(ROOT).as_posix():pin(p) for p in PUBLIC.rglob('*') if p.is_file()}
        assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and all(pin(ROOT/n)==v for n,v in catalog.items())
        state.update(status='PASS_FROZEN_CELLCHAT_NATIVE_PROJECTION_CHECKPOINT',source_pins=source_pins,published_files=published,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,archive=pin(archive),members=len(members))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence preservation gates')
    raise SystemExit(main())
