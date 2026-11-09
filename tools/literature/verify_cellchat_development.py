# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify frozen CellChat component evidence; no R refit or scientific execution."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin

def bytepin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(home,check_current=True):
    proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_CELLCHAT_PREPARED_RNA_AND_DATABASE_DEVELOPMENT_ONLY'
    assert proof['new_catalog_credit'] is False and proof['whole_goal_complete'] is False and proof['full_workflow_available'] is False
    assert (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(93,96,248)
    if check_current:
        for name,wanted in proof['source_pins'].items():assert pin(ROOT/name)==wanted,(name,'current source changed')
        for name,wanted in proof['catalog_unchanged'].items():assert pin(ROOT/name)==wanted
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    for name,wanted in proof['development_jars'].items():assert pin(home/name)==wanted
    assert pin(home/'controls.zip')==proof['archive']
    with zipfile.ZipFile(home/'controls.zip') as archive:
        infos=archive.infolist();assert len(infos)<=10000 and len(infos)==len({i.filename for i in infos}) and sum(i.file_size for i in infos)<=536870912
        assert set(archive.namelist())==set(proof['members'])
        for info in infos:
            path=PurePosixPath(info.filename)
            assert not info.is_dir() and path.as_posix()==info.filename and not path.is_absolute() and '..' not in path.parts and '\\' not in info.filename and ':' not in info.filename and info.file_size<=33554432
            assert bytepin(archive.read(info))==proof['members'][info.filename]
        def document(name):return json.loads(archive.read(name))
        def table(name):return list(csv.DictReader(io.StringIO(archive.read(name).decode('utf8')),delimiter='\t'))
        prefix='evidence/'
        control=prefix+'controls-1/';cases=document(control+'cases.json');assert len(cases)==150 and len({c['id'] for c in cases})==150
        reference=document(prefix+'reference-1/COMPLETED.json');assert reference['status']=='PASS_SIX_UNCHANGED_CELLCHAT_RNA_FUNCTION_REFERENCES_ONLY' and len(reference['references'])==6
        native=document(prefix+'native-validation-2/COMPLETED.json');assert native['status']=='PASS_NATIVE_CELLCHAT_OBSERVED_RNA_SCORES_ONLY' and native['permutation_p_values_source_equivalent'] and len(native['runs'])==6
        values=0;maximum=0
        for run in native['runs']:
            runroot=prefix+'native-validation-2/'+run['compiler']+'-'+run['platform']+'/'
            assert run['cases']==150 and run['permutation_pvalue_mismatches']==0 and run['contracts']==20
            for case in cases:
                left=table(runroot+case['label']+'/results/'+case['id']+'/metrics.tsv')
                right=table(prefix+'reference-1/'+case['label']+'/'+case['id']+'/metrics.tsv')
                assert len(left)==len(right)
                for x,y in zip(left,right,strict=True):
                    assert all(x[k]==y[k] for k in ['interaction','source','target'])
                    a=float(x['probability']);b=float(y['probability']);assert math.isfinite(a) and math.isfinite(b)
                    error=abs(a-b)/max(1,abs(b));assert error<=2e-12;maximum=max(maximum,error);values+=1
                    nb=case['nboot'];pa=float(x['pvalue']);pb=float(y['pvalue'])
                    assert abs(pa-round(pa*nb)/nb)<1e-13 and abs(pb-round(pb*nb)/nb)<1e-13 and round(pa*nb)==round(pb*nb)
        assert values==69984 and maximum==proof['maximum_scaled_score_difference']
        db=document(prefix+'database-validation-1/COMPLETED.json');assert db['status']=='PASS16_ORIGINAL_DATABASE_EXPORTS_FIVE_DISPOSITIONS_TWO_TAMPER_REFUSALS_ONLY' and len(db['exports'])==16 and len(db['dispositions'])==5 and db['tamper_refusals']==2
        for export in db['exports']:
            path=prefix+'database-validation-1/'+export['label']+'-'+export['species']+'/'
            receipt=document(path+'COMPLETED.json');assert bytepin(archive.read(path+'COMPLETED.json'))==export['receipt']
            assert receipt['status']=='completed-cellchat-database-export-only' and receipt['resource_export_complete'] and not receipt['analysis_admitted']
            assert bytepin(archive.read(path+'WARNINGS.md'))==receipt['warnings']
            for name,wanted in receipt['output_files'].items():assert bytepin(archive.read(path+name))==wanted
        audit=document(prefix+'primary-audit-1/COMPLETED.json');assert audit['papers_scanned']==250 and audit['observed_source_roles']==6 and audit['new_review_drafts']==6 and audit['pending_unobserved_mentions']==0 and audit['catalog_unchanged']
        assert len(document(prefix+'primary-audit-1/role-drafts.json')['reviews'])==6
        old=document(prefix+'native-validation-1/COMPLETED.json');assert old['status']=='FAIL2_CELLCHAT_NATIVE_COMPONENT_CONTROLS' and not old['implementation_credit']
        checks=document(prefix+'prepublication-checks-1/COMPLETED.json');assert checks['status']=='PASS_COPYABLE_JAVA_EXAMPLE_MAFFT_AND_CURRENT_CISTARGET_CATALOG'
        lock=document('source-snapshot/tools/literature/cellchat-source-lock.json')
        for label,source in lock['sources'].items():
            for name,wanted in source['files'].items():assert bytepin(archive.read('original-source/'+label+'/'+name))==wanted
    return proof

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',type=Path,default=ROOT/'docs/literature/cellchat-development');args=parser.parse_args();verify(args.home)
    print('Verified CellChat prepared component:900 comparisons/16 database exports/six unapplied paper roles;93 integrations/248 missing; full workflow unfinished')
