# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze only own SNPRelate native evidence; verify staged complete references before publication."""
import argparse
import json
import shutil
import subprocess
import sys
import traceback
import zipfile
from pathlib import Path
from verify_snprelate_evidence import ROOT,BASE,HOME,pin,digest,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE) and not HOME.exists();out.mkdir(parents=True)
    stage=out/'staging';stage.mkdir();canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    state=dict(status='FAIL2_SNPRELATE_PUBLIC_EVIDENCE_SEAL',new_catalog_credit=False,whole_goal_complete=False)
    try:
        source_names=['src/main/java/org/jlinalg/genetics/'+n+'.java' for n in ['KingRobustKinship','SnprelateKingRobust','SnprelateVariantQc']]
        source_names+=['src/test/java/org/jlinalg/genetics/'+n+'.java' for n in ['KingRobustKinshipTest','SnprelateProfilesTest','SnprelateReferenceProbe']]
        source_names+=['tools/literature/'+n+'.py' for n in ['audit_snprelate_genesis_roles','curate_snprelate_genesis_roles','collect_snprelate_edges',
            'collect_snprelate_king_reference','validate_snprelate_native','test_snprelate_contracts','verify_snprelate_evidence','seal_snprelate_evidence',
            'setup_snprelate_reference','setup_deseq2_reference','probe_king_runtime']]
        source_names+=['src/main/resources/org/jlinalg/omics/snprelate/king_reference.R','examples/native-king/SnprelateProfileExample.java',
            'docs/literature/NATIVE_SNPRELATE_PROFILES.md','docs/literature/omics-2023-2026/snprelate-genesis-primary-roles.json']
        pins={name:pin(ROOT/name) for name in source_names};payload={'own-source/'+name:(ROOT/name).read_bytes() for name in pins}
        builds={c:BASE/('native-reference'+str(c)+'-final1') for c in [21,25]};artifacts={};summaries={}
        for compiler,folder in builds.items():
            done=json.loads((folder/'COMPLETED.json').read_bytes());assert done['status'].startswith('PASS_NATIVE_SNPRELATE146_COMPLETE_')
            assert all(pin(ROOT/name)==value for name,value in done['source_pins'].items())
            summaries['native'+str(compiler)]=folder/'COMPLETED.json'
            for target,source in [('snprelate-native-javac'+str(compiler)+'.jar','snprelate-native-api.jar'),('snprelate-probe-javac'+str(compiler)+'.jar','snprelate-native-reference-probe.jar')]:
                assert pin(folder/source)==done['artifacts'][source];shutil.copyfile(folder/source,stage/target);artifacts[target]=pin(stage/target)
        contracts=BASE/'native-contracts-1';done=json.loads((contracts/'COMPLETED.json').read_bytes())
        assert done['status']=='PASS_NATIVE_SNPRELATE96_JUNIT_EXECUTIONS_AND4_INDEPENDENT_API_CONSUMERS'
        assert all(pin(ROOT/name)==value for name,value in done['source_pins'].items())
        for compiler in [21,25]:
            path=next(Path(name) for name in done['api_pins'] if 'native-reference'+str(compiler)+'-' in name)
            assert done['api_pins'][str(path)]==pin(builds[compiler]/'snprelate-native-api.jar')
            consumer=contracts/('javac'+str(compiler))/'consumer/SnprelateProfileExample.class'
            payload['native-consumers/'+str(compiler)+'/SnprelateProfileExample.class']=consumer.read_bytes()
        summaries.update(contracts=contracts/'COMPLETED.json',source_intake=BASE/'source-intake-1/receipt.json',
            primary_census=BASE/'primary-intake-1/COMPLETED.json',edge_collection=BASE/'own-edge-controls-3/COMPLETED.json')
        payload.update({'summaries/'+key+'.json':path.read_bytes() for key,path in summaries.items()})
        references=[]
        folders=[BASE/'original-king-controls-6']+[BASE/'own-edge-controls-3'/(kind+'-'+transport)
            for kind in ['mixed','zero-heterozygosity','all-missing'] for transport in ['bed-import','row-index']]
        for folder in folders:
            done=json.loads((folder/'COMPLETED.json').read_bytes());assert done['status']=='PASS_ORIGINAL_SNPRELATE146_KING_MATRIX_REFERENCE_COLLECTION_ONLY'
            assert done['preservation_checks']['all_unchanged'] and done['worker_pin']==pins['src/main/resources/org/jlinalg/omics/snprelate/king_reference.R']
            assert done['producer']==pins['tools/literature/collect_snprelate_king_reference.py']
            assert not done['project_analysis_admitted'] and not done['new_catalog_credit']
            for name,value in done['complete_original_files'].items():assert pin(folder/name)==value
            # These are the project-generated cohorts, never original author example data.
            if folder.name=='original-king-controls-6':
                original=ROOT/'build/literature-review/plink-relatedness-runtime/king-own-controls-1/own-inputs/cohort'
            else:original=BASE/'own-edge-controls-3/own-inputs'/folder.name.rsplit('-',2)[0]
            for ext,value in done['input_pins'].items():assert pin(original.with_suffix('.'+ext))==value==pin(folder/'inputs'/('cohort.'+ext))
            name=folder.name;references.append(dict(name=name,cases=len(done['cases']),input_pins=done['input_pins']))
            selected=[folder/'COMPLETED.json',folder/'stdout',folder/'stderr',folder/'worker.R',folder/'WARNINGS.md',folder/'process.json']
            selected+=[f for tree in ['inputs','original-results'] for f in sorted((folder/tree).rglob('*')) if f.is_file()]
            for file in selected:payload['references/'+name+'/'+file.relative_to(folder).as_posix()]=file.read_bytes()
        assert sum(r['cases'] for r in references)==128
        with zipfile.ZipFile(stage/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(payload.items()):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type,item.external_attr=zipfile.ZIP_DEFLATED,0o100644<<16;z.writestr(item,raw)
        proof=dict(status='PASS_FROZEN_NATIVE_SNPRELATE146_SCALAR_QC_REFERENCE_EVIDENCE',schema_version=1,
            source_pins=pins,artifacts=artifacts,archive=pin(stage/'controls.zip'),members={name:digest(raw) for name,raw in sorted(payload.items())},
            references=references,original_declared_cases=128,original_empty_selection_errors=10,original_empty_selection_matrix_returns=10,
            full_native_case_checks=512,complete_matrix_cell_checks=59776,test_methods=32,new_test_methods=23,test_executions=96,
            independent_api_consumers=4,source_archive= json.loads((BASE/'source-intake-1/receipt.json').read_bytes()),
            original_software_author_data_or_article_prose_bundled=False,project_analysis_admitted=False,new_catalog_credit=False,
            whole_goal_complete=False,canonical_executable_unchanged=before,
            scope='Native KING-robust scalar and source-layout variant-QC components plus own reference evidence. No GDS adapter admission, LD pruning, GRM, GENESIS, paper reproduction, catalog credit or whole-goal claim.')
        save(stage/'VALIDATION.json',proof)
        command=[sys.executable,str(ROOT/'tools/literature/verify_snprelate_evidence.py'),'--home',str(stage),'--out',str(out/'verified-staging'),
            '--java','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe']
        result=subprocess.run(command,capture_output=True,timeout=300)
        (out/'verify.stdout').write_bytes(result.stdout);(out/'verify.stderr').write_bytes(result.stderr)
        assert result.returncode==0,(result.returncode,result.stdout[-2500:],result.stderr[-2500:])
        assert pin(canonical)==before and all(pin(ROOT/name)==value for name,value in pins.items())
        HOME.mkdir()
        for file in stage.iterdir():shutil.copyfile(file,HOME/file.name)
        assert pin(HOME/'controls.zip')==proof['archive'] and all(pin(HOME/name)==value for name,value in artifacts.items())
        state.update(status='PASS_VERIFIED_PUBLIC_NATIVE_SNPRELATE_SCALAR_QC_EVIDENCE',source_pins=pins,archive=proof['archive'],
            published_files={f.relative_to(ROOT).as_posix():pin(f) for f in HOME.iterdir()},canonical_executable_unchanged=before)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3000],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable staged verification or original-data/software exclusion')
    raise SystemExit(main())
