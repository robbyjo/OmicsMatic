# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal an additive CellChat component checkpoint without altering the catalog or released JAR."""
import argparse,json,shutil,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from verify_cellchat_development import verify,bytepin

BASE=ROOT/'build/literature-review/cellchat-runtime'
FOLDERS=['source-metadata-1','edition-discovery-1','primary-audit-1','dependency-probe-1','controls-1','reference-1','build21-1','build21-2','build25-1','native-validation-1','native-validation-2','database-validation-1','export-first-1','prepublication-checks-1']

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_CHECKPOINT_SEAL',whole_goal_complete=False,new_catalog_credit=False)
    try:
        public=ROOT/'docs/literature/cellchat-development';assert not public.exists()
        held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cistarget-catalog-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items())
        canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical['sha256']=='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
        sources=[p for p in (ROOT/'tools/literature').glob('*cellchat*') if p.is_file() and p.suffix in {'.py','.R','.json'}]
        sources += [ROOT/'src/main/java/org/jlinalg/omics/CellChatRna.java',ROOT/'src/test/java/org/jlinalg/omics/CellChatRnaProbe.java',ROOT/'src/test/java/org/jlinalg/omics/CellChatRnaContracts.java',ROOT/'docs/literature/CELLCHAT_DEVELOPMENT.md',ROOT/'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt',ROOT/'tools/literature/setup_deseq2_reference.py']
        sources=sorted(set(sources));pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
        protected=[ROOT/'docs/literature/omics-2023-2026'/n for n in ['papers.json','tool-inventory.json','README.md','SHA256SUMS']]+[ROOT/'tools/literature/reviewed-uses.json']
        catalog={p.relative_to(ROOT).as_posix():pin(p) for p in protected}
        native=json.loads((BASE/'native-validation-2/COMPLETED.json').read_bytes());assert native['status']=='PASS_NATIVE_CELLCHAT_OBSERVED_RNA_SCORES_ONLY' and native['permutation_p_values_source_equivalent']
        db=json.loads((BASE/'database-validation-1/COMPLETED.json').read_bytes());assert db['status']=='PASS16_ORIGINAL_DATABASE_EXPORTS_FIVE_DISPOSITIONS_TWO_TAMPER_REFUSALS_ONLY'
        assert json.loads((BASE/'prepublication-checks-1/COMPLETED.json').read_bytes())['guide']==pin(ROOT/'docs/literature/CELLCHAT_DEVELOPMENT.md')
        candidate=out/'candidate';candidate.mkdir();jars={}
        for compiler,folder in [('javac21','build21-2'),('javac25','build25-1')]:
            report=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in report['source_pins'].items())
            source=ROOT/report['artifact']['path'];name='cellchat-rna-'+compiler+'.jar';shutil.copyfile(source,candidate/name);jars[name]=pin(candidate/name)
        archive=candidate/'controls.zip';members={}
        with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            def add(name,raw):
                assert name not in members and len(raw)<=33554432
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
                z.writestr(info,raw);members[name]=bytepin(raw)
            for folder in FOLDERS:
                assert (BASE/folder).is_dir()
                for file in sorted((BASE/folder).rglob('*')):
                    if file.is_file():assert not file.is_symlink();add('evidence/'+folder+'/'+file.relative_to(BASE/folder).as_posix(),file.read_bytes())
            for file in sources:add('source-snapshot/'+file.relative_to(ROOT).as_posix(),file.read_bytes())
            lock=json.loads((ROOT/'tools/literature/cellchat-source-lock.json').read_bytes())
            for label,source in lock['sources'].items():
                for name,wanted in source['files'].items():
                    file=BASE/'source-intake-1'/label/'source'/name;assert pin(file)==wanted;add('original-source/'+label+'/'+name,file.read_bytes())
            add('LICENSES/GPL-3.0.txt',(ROOT/'LICENSE').read_bytes())
        assert archive.stat().st_size<83886080 and sum(v['bytes'] for v in members.values())<=536870912
        proof=dict(status='PASS_CELLCHAT_PREPARED_RNA_AND_DATABASE_DEVELOPMENT_ONLY',new_catalog_credit=False,whole_goal_complete=False,full_workflow_available=False,new_scoped_integrations=93,including_existing_adapters=96,missing=248,prepared_source_families=150,build_runtime_comparisons=900,observed_score_cells=69984,maximum_scaled_score_difference=max(r['maximum_scaled_probability_difference'] for r in native['runs']),permutation_count_mismatches_in_controls=0,original_database_exports=16,source_roles_drafted_not_applied=6,prior_failed_attempts_retained=True,original_R_runtime='Windows R4.6.1; source-function boundary only, no CellChat package installation',native_platforms=['WindowsJava21','WindowsJava25','LinuxJava21'],source_pins=pins,catalog_unchanged=catalog,canonical_executable_unchanged=canonical,unrelated_files_preserved=18,development_jars=jars,archive=pin(archive),members=members)
        save(candidate/'VALIDATION.json',proof);verify(candidate)
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in catalog.items()) and all(pin(ROOT/n)==v for n,v in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        public.mkdir()
        for file in sorted(candidate.iterdir()):shutil.copyfile(file,public/file.name)
        verify(public);state.update(status='PASS_FROZEN_ADDITIVE_CELLCHAT_DEVELOPMENT_CHECKPOINT',published_files={p.relative_to(ROOT).as_posix():pin(p) for p in public.iterdir()},source_pins=pins,canonical_executable_unchanged=canonical,unrelated_files_preserved=18,catalog_unchanged=catalog)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable immutable source/evidence checks')
    raise SystemExit(main())
