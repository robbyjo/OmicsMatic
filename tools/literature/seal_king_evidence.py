# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Publish own KING2.3.2 adapter/evidence; exclude original programs, author source and author example data."""
import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/plink-relatedness-runtime'
HOME=ROOT/'docs/literature/king232-development'


def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def save(path,value):
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);assert not HOME.exists();HOME.mkdir()
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    names=['KingRequest','KingAudit','KingToolsCli','NextflowFiles','NextflowParameters','NextflowJson','SimpleJson']
    sources=[ROOT/'src/main/java/org/jlinalg/cli'/(name+'.java') for name in names]
    sources += [ROOT/'src/main/java/org/jlinalg/genetics/KingRobustKinship.java',
                ROOT/'src/main/resources/org/jlinalg/omics/king/setup_king_runtime.py',ROOT/'docs/literature/KING_INTEGRATION.md']
    sources += [ROOT/'tools/literature'/name for name in ['build_king.py','collect_king_workflows.py','collect_king_synthetic.py',
                'probe_king_runtime.py','validate_king_adapter.py','test_king_contracts.py','audit_king_reference_parity.py',
                'intake_king_historical.py','seal_king_evidence.py','verify_king_evidence.py']]
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    summaries={}
    for name in ['king-build21-4','king-build25-1','king-adapter-gates-4','king-adapter-gates-25-1',
                 'king-own-controls-1','king-contracts-1','king-parity-1','king-workflows-2','king-historical-intake-1']:
        path=BASE/name/'COMPLETED.json';value=json.loads(path.read_bytes());assert value['status'].startswith('PASS_'),name
        summaries[name]=value
    for name in ['king-build21-4','king-build25-1']:
        assert all(pin(ROOT/p)==value for p,value in summaries[name]['source_pins'].items())
    artifacts={}
    for label,build in [('javac21','king-build21-4'),('javac25','king-build25-1')]:
        target=HOME/('king232-'+label+'.jar');shutil.copyfile(BASE/build/'king-development.jar',target);artifacts[target.name]=pin(target)
    own=BASE/'king-own-controls-1';bundles=sorted(p for p in own.glob('*.bundle') if p.is_dir());assert len(bundles)==13
    payload={}
    for source in sources:payload['own-source/'+source.relative_to(ROOT).as_posix()]=source.read_bytes()
    for name,value in summaries.items():payload['summaries/'+name+'.json']=(json.dumps(value,indent=2)+'\n').encode()
    for path in sorted((own/'own-inputs').iterdir()):payload['synthetic/own-inputs/'+path.name]=path.read_bytes()
    own_pins={p.suffix[1:]:pin(p) for p in (own/'own-inputs').iterdir()}
    for bundle in bundles:
        assert json.loads((bundle/'COMPLETED.json').read_bytes())['result_admitted']
        for ext,value in own_pins.items():assert pin(bundle/'inputs'/('cohort.'+ext))==value
        for path in sorted(bundle.rglob('*')):
            if path.is_file():payload['synthetic/'+bundle.name+'/'+path.relative_to(bundle).as_posix()]=path.read_bytes()
    archive=HOME/'controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(payload.items()):
            item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type,item.external_attr=zipfile.ZIP_DEFLATED,0o100644<<16;z.writestr(item,raw)
    proof=dict(status='PASS_FROZEN_KING232_ORIGINAL_EXECUTION_AND_NATIVE_TRANSPORT',schema_version=1,
               new_catalog_credit=False,whole_goal_complete=False,source_pins=pins,artifacts=artifacts,archive=pin(archive),
               members={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for name,raw in sorted(payload.items())},
               synthetic_bundles=[b.name for b in bundles],complete_original_example_pairs=54946,
               original_example_ibd_coordinate_records=8932,original_example_ibd_summary_pairs=4240,
               final_compiler_execution_cases=44,admitted_final_compiler_cases=34,expected_final_skips=6,expected_final_failures=4,
               portable_compiler_verifications=88,synthetic_admitted_cases=13,synthetic_portable_verifications=26,
               resealed_mutation_cases=12,request_and_setup_failure_cases=12,raw_reference_comparisons=30,
               original_programs_or_author_source_or_author_example_data_bundled=False,
               historical_king20_227_intake_only=True,root_cli_and_catalog_admission_complete=False,
               canonical_executable_unchanged=before,
               scope='Exact original research-use2.3.2 standalone bounded adapter. Native count/coordinate/partition transport verification; no independent segment-placement/selection refit, historical or R/PLINK2 hosting equivalence, paper reproduction, production-cohort-scale or whole-goal claim.')
    save(HOME/'VALIDATION.json',proof)
    assert pin(canonical)==before and all(pin(ROOT/name)==value for name,value in pins.items())
    save(out/'COMPLETED.json',dict(status='PASS_PUBLIC_KING232_OWN_CODE_AND_SYNTHETIC_EVIDENCE_SEAL',proof=pin(HOME/'VALIDATION.json'),
                                 archive=proof['archive'],new_catalog_credit=False,whole_goal_complete=False))
    print('PASS_PUBLIC_KING232_OWN_CODE_AND_SYNTHETIC_EVIDENCE_SEAL',proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen-source and original-software exclusion checks')
    main()
