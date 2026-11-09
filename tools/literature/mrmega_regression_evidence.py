# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check frozen mathematical-kernel evidence without running MR-MEGA/Java/R."""
import argparse,json,math,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from validate_mrmega_regression import rows

HOME=ROOT/'docs/literature/mrmega-regression-development';BASE='build/literature-review/mrmega-runtime/'
STATUS='PASS_NATIVE_PREPARED_COHORT_MRMEGA_REGRESSION_COMPONENT_ONLY'


def verify(archive_only=False,home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']==STATUS
    assert proof['native_regression_kernel_available']
    assert not any(proof[key] for key in ['implementation_credit','full_MRMEGA_workflow_available','catalog_credit','whole_paper_reproduced','whole_goal_complete'])
    assert (proof['source_control_cases'],proof['complete_regression_comparisons'],proof['contract_suites'])==(54,324,6)
    assert pin(home/'controls.zip')==proof['archive'] and all(pin(home/name)==value for name,value in proof['artifacts'].items())
    if not archive_only:
        assert all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    files=unpack(home/'controls.zip',proof)
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    def document(name):return json.loads(files[BASE+name])
    for folder,name in [('kernel-build-21-1','mrmega-regression-javac21.jar'),('kernel-build-25-1','mrmega-regression-javac25.jar')]:
        built=document(folder+'/COMPLETED.json');assert built['status']=='PASS_ISOLATED_JAVA17_MRMEGA_REGRESSION_BUILD_ONLY'
        assert byte_pin(files[built['artifact']['path']])==pin(home/name)
        assert built['canonical_executable_unchanged']==proof['canonical_executable_unchanged']
        for path,value in built['source_pins'].items():assert byte_pin(files[BASE+folder+'/source-snapshot/'+path])==byte_pin(files['current-source/'+path])==value
        with zipfile.ZipFile(home/name) as archive:
            for path in archive.namelist():
                assert path=='META-INF/LICENSE' or path.startswith('org/jlinalg/meta/MrMegaRegression') and path.endswith('.class')
                if path.endswith('.class'):assert int.from_bytes(archive.read(path)[6:8],'big')==61
    original=document('kernel-original-1/COMPLETED.json');assert original['status']=='PASS_FIFTY_FOUR_ORIGINAL_MRMEGA_REGRESSION_KERNEL_CONTROLS_ONLY'
    assert byte_pin(files[BASE+'kernel-original-1/original-kernel.tsv'])==original['original_kernel_output'] and len(original['original_sources_unchanged'])==100
    setup=document('reference-build-1/COMPLETED.json');assert setup['status']=='PASS_PRIVATE_UNMODIFIED_ORIGINAL_MRMEGA_REFERENCE_BUILD_ONLY'
    assert setup['original_sources_unchanged'] and setup['original_archive']['sha256']=='e91eb5bdba0a398619e6cd98c351ea72d94e09d6f5a1d25bb7da8a66da938ffe'
    assert document('reference-build-1/ORIGINAL-SOURCE-FILES.json')==original['original_sources_unchanged']
    assert byte_pin(files[BASE+'reference-build-1/ORIGINAL-SOURCE-FILES.json'])==setup['original_source_files']
    for folder in ['reference-build-1/compile','reference-build-1/version','kernel-original-1/compile','kernel-original-1/run']:
        process=document(folder+'/process.json');assert process['exit_code']==0 and 'owned_live_after_cleanup' in process and not process['owned_live_after_cleanup']
    validated=document('kernel-validation-1/COMPLETED.json')
    assert validated['status']=='PASS_BOTH_MRMEGA_KERNEL_BUILDS_ON_THREE_JVMS_WITH_ORIGINAL_WLS_AND_SEPARATE_R_TAILS_ONLY'
    assert validated['complete_regression_comparisons']==324 and len(validated['cases'])==6 and not validated['full_MRMEGA_file_workflow_available']
    assert {(r['compiler'],r['platform']) for r in validated['cases']}=={(c,p) for c in ['javac21','javac25'] for p in ['windows21','windows25','linux21']}
    def parsed(raw):
        output=[]
        for line in raw.decode().splitlines():
            values=line.split('\t');assert len(values)==21
            output.append((values[:3],[[float(x) for x in s.split(',')] for s in values[3:6]],[float(x) for x in values[6:]]))
        return output
    source=parsed(files[BASE+'kernel-original-1/original-kernel.tsv']);assert len(source)==54
    r_rows=[line.split('\t') for line in files[BASE+'kernel-validation-1/r-chi-square.tsv'].decode().splitlines()[1:]];assert len(r_rows)==54
    for run in validated['cases']:
        prefix=run['path']+'/';native_raw=files[prefix+'native-kernel.tsv'];assert byte_pin(native_raw)==run['native_output']
        assert b'PASS_MRMEGA_ANALYTIC_INPUT_IMMUTABILITY_AND_ELEVEN_REFUSAL_CONTRACTS_ONLY' in files[prefix+'contracts/stdout']
        assert json.loads(files[prefix+'contracts/process.json'])['exit_code']==json.loads(files[prefix+'complete-values/process.json'])['exit_code']==0
        native=parsed(native_raw);assert len(native)==54
        for current,old,r_expected in zip(native,source,r_rows,strict=True):
            assert current[0]==old[0] and current[0][0]==r_expected[0];k=int(current[0][2])+1
            assert [len(v) for v in current[1]]==[len(v) for v in old[1]]==[k,k,k*k]
            first=[x for v in current[1] for x in v]+current[2][:11]+current[2][14:]
            second=[x for v in old[1] for x in v]+old[2][:11]+old[2][14:]
            assert all(math.isfinite(a) and math.isfinite(b) and abs(a-b)<=2e-10*max(1,abs(b)) for a,b in zip(first,second,strict=True))
            for index in range(3):
                p=current[2][11+index];reference=float(r_expected[1+index]);q=current[2][5+index];df=current[2][8+index]
                if q<=0 or df<=0:assert math.isnan(p)
                else:assert math.isfinite(p) and 0<=p<=1 and abs(p-reference)<=3e-11*max(reference,1e-300)
    differences=document('kernel-validation-1/original-probability-discrepancies.json')
    assert len(differences)==7 and sum(row['original_raw']=='-inf' for row in differences)==3
    assert len(document('kernel-validation-1/undefined-test-boundaries.json'))==4
    audit=document('primary-roles-1/COMPLETED.json');assert audit['source_papers']==250 and audit['observations']==2465 and audit['roles']==3 and not audit['catalog_changed']
    drafts=document('primary-roles-1/role-drafts.json');assert {r['paper_id'] for r in drafts['reviews']}=={'P072','P105','P162'}
    assert sum(len(r['review'].get('supporting_evidence',[])) for r in drafts['reviews'])==2
    assert not any(name.startswith(BASE+'reference-build-1/source/') or name.endswith('/MR-MEGA_v0.2.zip') or name.endswith('/mrmega-regression-reference') for name in files)
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen mathematical-kernel checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--archive-only',action='store_true');a=p.parse_args()
    proof,files=verify(a.archive_only)
    print(proof['status']+';324 original-regression comparisons; separate stable R probabilities; no full-tool/catalog credit')
