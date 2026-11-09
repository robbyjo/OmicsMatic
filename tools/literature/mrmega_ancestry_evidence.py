# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check archived ancestry populations and numerical relationships; execute nothing."""
import argparse,json,math,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

HOME=ROOT/'docs/literature/mrmega-ancestry-development'
BASE='build/literature-review/mrmega-runtime/'
STATUS='PASS_NATIVE_PREPARED_FREQUENCY_MRMEGA_ANCESTRY_COMPONENT_ONLY'


def verify(archive_only=False,home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']==STATUS and proof['native_prepared_ancestry_available']
    assert not any(proof[k] for k in ['catalog_credit','full_MRMEGA_workflow_available','whole_paper_reproduced','whole_goal_complete'])
    assert (proof['source_cases'],proof['complete_ancestry_comparisons'],proof['coupled_regression_comparisons'],proof['contract_suites'])==(12,72,72,6)
    assert pin(home/'controls.zip')==proof['archive']
    assert all(pin(home/n)==v for n,v in proof['artifacts'].items())
    assert all(pin(ROOT/n)==v for n,v in proof['frozen_parent_pins'].items())
    if not archive_only:
        assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    files=unpack(home/'controls.zip',proof)
    for n,v in proof['source_pins'].items():assert byte_pin(files['current-source/'+n])==v
    def document(n):return json.loads(files[BASE+n])
    def matrix(n):return [[float(v) for v in row.split('\t')] for row in files[n].decode().splitlines()]
    def values(n):return {row.split('\t')[0]:[float(v) for v in row.split('\t')[1].split(',')] for row in files[n].decode().splitlines()}
    def close(a,b,tol=2e-10):
        assert len(a)==len(b)
        assert all(math.isfinite(x) and math.isfinite(y) and abs(x-y)<=tol*max(1,abs(y)) for x,y in zip(a,b,strict=True))
    def successful(prefix,owned=False):
        process=json.loads(files[prefix+'/process.json']);assert process['exit_code']==0
        if owned:assert 'owned_live_after_cleanup' in process and not process['owned_live_after_cleanup']
    controls=document('ancestry-controls-1/COMPLETED.json')
    assert controls['complete_cases']==12 and controls['status']=='PASS_TWELVE_SYNTHETIC_MRMEGA_ANCESTRY_CONTROL_FAMILIES_ONLY'
    assert all(byte_pin(files[BASE+'ancestry-controls-1/'+n])==v for n,v in controls['files'].items())
    cases=document('ancestry-controls-1/cases.json');assert len(cases)==12
    parent=ROOT/'docs/literature/mrmega-regression-development'
    for compiler,folder in [('javac21','ancestry-build-21-1'),('javac25','ancestry-build-25-1')]:
        built=document(folder+'/COMPLETED.json');name='mrmega-ancestry-'+compiler+'.jar'
        assert built['status']=='PASS_ISOLATED_MRMEGA_ANCESTRY_BUILD_WITH_UNCHANGED_REGRESSION_BYTES_ONLY'
        assert built['all_inherited_entries_unchanged'] and not built['complete_file_workflow_available']
        assert byte_pin(files[built['artifact']['path']])==pin(home/name)
        for path,value in built['source_pins'].items():
            assert byte_pin(files[BASE+folder+'/source-snapshot/'+path])==byte_pin(files['current-source/'+path])==value
        assert json.loads(files[BASE+folder+'/compile.json'])['exit_code']==0
        with zipfile.ZipFile(parent/('mrmega-regression-'+compiler+'.jar')) as old,zipfile.ZipFile(home/name) as new:
            assert all(new.read(n)==old.read(n) for n in old.namelist())
            for n in new.namelist():
                assert n=='META-INF/LICENSE' or n.startswith(('org/jlinalg/meta/MrMegaRegression','org/jlinalg/meta/MrMegaAncestry')) and n.endswith('.class')
                if n.endswith('.class'):assert int.from_bytes(new.read(n)[6:8],'big')==61
    original=document('ancestry-original-1/COMPLETED.json')
    assert original['status']=='PASS_TWELVE_ORIGINAL_MARKER_POPULATIONS_MDS_AND_COUPLED_REGRESSIONS_ONLY' and original['complete_cases']==12
    parent_files=unpack(parent/'controls.zip',json.loads((parent/'VALIDATION.json').read_bytes()))
    assert json.loads(parent_files[BASE+'reference-build-1/ORIGINAL-SOURCE-FILES.json'])==original['original_sources_unchanged']
    assert len(original['original_sources_unchanged'])==100
    successful(BASE+'ancestry-original-1/compile',True);successful(BASE+'ancestry-original-1/component-run',True)
    validation=document('ancestry-validation-1/COMPLETED.json')
    assert validation['status']=='PASS_BOTH_NATIVE_MRMEGA_ANCESTRY_BUILDS_ON_THREE_JVMS_WITH_ORIGINAL_SELECTION_MDS_AND_REGRESSION_ONLY'
    assert not validation['genotype_file_parsing_and_complete_GWAS_workflow_available']
    assert (validation['complete_ancestry_comparisons'],validation['coupled_regression_comparisons'],validation['contract_suites'])==(72,72,6)
    assert {(r['compiler'],r['platform']) for r in validation['runs']}=={(c,p) for c in ['javac21','javac25'] for p in ['windows21','windows25','linux21']}
    for case,expected in zip(cases,original['cases'],strict=True):
        assert case['id']==expected['id'];id=case['id'];n=case['cohorts'];p=case['axes']
        prefix=BASE+'ancestry-original-1/'+id
        successful(prefix+'/whole-program-mds',True)
        log=files[prefix+'/original.log'];assert byte_pin(log)==expected['program_log']
        chosen=[s.strip() for s in log.decode().split('Selected markers:',1)[1].split('eaf values:',1)[0].splitlines() if s.strip()]
        assert chosen==expected['selected_ids'] and len(chosen)==len(set(chosen))
        source_file=BASE+'ancestry-original-1/full-precision-values/'+id+'.tsv'
        assert byte_pin(files[source_file])==expected['full_precision_values'];source=values(source_file)
        assert len(source['coordinates'])==n*p and expected['original_full_metaregression_not_executed']
        close(source['coordinates'],[v for row in expected['original_log_coordinates_six_significant_digits'] for v in row],5e-6)
        for run in validation['runs']:
            prefix=BASE+'ancestry-validation-1/'+run['compiler']+'-'+run['platform']
            successful(prefix+'/contracts');successful(prefix+'/values')
            assert b'PASS_' in files[prefix+'/contracts/stdout']
            native=prefix+'/native/'+id
            selected=[s.split('\t') for s in files[native+'/selected.tsv'].decode().splitlines()]
            assert [r[1] for r in selected]==chosen and [int(r[0]) for r in selected]==expected['selected_source_ordinals']
            assert matrix(native+'/frequencies.tsv')==expected['stored_frequency_matrix']
            dispositions=[s.split('\t') for s in files[native+'/dispositions.tsv'].decode().splitlines()]
            assert [r[1] for r in dispositions]==[r['id'] for r in case['variants']]
            receipt=dict(s.split('=',1) for s in files[native+'/receipt.txt'].decode().splitlines()[1:])
            assert int(receipt['eligible'])==expected['eligible_variants'] and int(receipt['selected'])==len(chosen)
            assert 'column300 aliases' in files[native+'/warnings.txt'].decode()
            coordinates=matrix(native+'/coordinates.tsv');distances=matrix(native+'/distances.tsv')
            assert len(coordinates)==len(distances)==n and all(len(row)==p for row in coordinates) and all(len(row)==n for row in distances)
            close([v for row in distances for v in row],source['distances'],1e-13)
            reference=[source['coordinates'][i*p:(i+1)*p] for i in range(n)]
            signs=[1 if sum(coordinates[i][j]*reference[i][j] for i in range(n))>=0 else -1 for j in range(p)]
            close([v for row in coordinates for v in row],[reference[i][j]*signs[j] for i in range(n) for j in range(p)])
            close([sum(coordinates[i][a]*coordinates[j][a] for a in range(p)) for i in range(n) for j in range(n)],
                [sum(reference[i][a]*reference[j][a] for a in range(p)) for i in range(n) for j in range(n)])
            model=values(native+'/model.tsv');sg=[1,*signs];k=p+1
            close(model['coefficients'],[v*s for v,s in zip(source['coefficients'],sg,strict=True)])
            close(model['errors'],source['errors'])
            close(model['covariance'],[source['covariance'][i*k+j]*sg[i]*sg[j] for i in range(k) for j in range(k)])
            close(model['statistics'],source['statistics'])
    assert not any(n.endswith('/original-ancestry-probe') or n.startswith(BASE+'reference-build-1/source/') or n.endswith('/MR-MEGA_v0.2.zip') for n in files)
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen ancestry checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');args=parser.parse_args()
    proof,_=verify(args.archive_only);print(proof['status']+';72 archived ancestry/model comparisons; full workflow remains unfinished')
