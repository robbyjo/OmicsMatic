# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate native ancestry populations, geometry and coupled original regressions."""
import argparse,concurrent.futures,json,math,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/mrmega-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def matrix(path):return [[float(v) for v in line.split('\t')] for line in path.read_text().splitlines()]
def values(path):return {line.split('\t')[0]:[float(v) for v in line.split('\t')[1].split(',')] for line in path.read_text().splitlines()}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_ANCESTRY_COMPONENT_CONTROLS',implementation_credit=False,whole_goal_complete=False,runs=[])
    try:
        original=BASE/'ancestry-original-1';reference=json.loads((original/'COMPLETED.json').read_bytes())
        assert reference['status']=='PASS_TWELVE_ORIGINAL_MARKER_POPULATIONS_MDS_AND_COUPLED_REGRESSIONS_ONLY' and len(reference['cases'])==12
        fixtures=BASE/'ancestry-controls-1';fixture_receipt=json.loads((fixtures/'COMPLETED.json').read_bytes())
        assert all(pin(fixtures/name)==value for name,value in fixture_receipt['files'].items())
        cases=json.loads((fixtures/'cases.json').read_bytes());assert [r['id'] for r in cases]==[r['id'] for r in reference['cases']]
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';artifacts={};protected=[canonical,Path(__file__).resolve(),original/'COMPLETED.json',fixtures/'prepared-controls.txt']
        for compiler,folder in [('javac21','ancestry-build-21-1'),('javac25','ancestry-build-25-1')]:
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_MRMEGA_ANCESTRY_BUILD_WITH_UNCHANGED_REGRESSION_BYTES_ONLY'
            assert all(pin(ROOT/name)==value for name,value in built['source_pins'].items())
            artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']}
            artifacts[compiler]=artifact;protected.append(artifact)
        before={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def run(home,command):
            home.mkdir();save(home/'command.json',command);process=subprocess.run(command,capture_output=True,timeout=180)
            (home/'stdout').write_bytes(process.stdout);(home/'stderr').write_bytes(process.stderr);save(home/'process.json',dict(exit_code=process.returncode))
            assert process.returncode==0,(home,process.stderr.decode(errors='replace'))
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda path:str(path.resolve())
            cp=(':' if platform=='linux21' else ';').join(convert(path) for path in [artifacts[compiler],canonical])
            command=[*platforms[platform],'-cp',cp,'org.jlinalg.meta.MrMegaAncestryReferenceProbe']
            run(home/'contracts',command+['--contracts']);native=home/'native';run(home/'values',command+[convert(fixtures/'prepared-controls.txt'),convert(native)])
            report=[]
            for case,expected in zip(cases,reference['cases'],strict=True):
                prefix=native/case['id'];n=case['cohorts'];p=case['axes'];k=p+1
                selected=[line.split('\t') for line in (prefix/'selected.tsv').read_text().splitlines()]
                assert [row[1] for row in selected]==expected['selected_ids'] and [int(row[0]) for row in selected]==expected['selected_source_ordinals']
                assert matrix(prefix/'frequencies.tsv')==expected['stored_frequency_matrix']
                receipt=dict(line.split('=',1) for line in (prefix/'receipt.txt').read_text().splitlines()[1:])
                assert int(receipt['eligible'])==expected['eligible_variants'] and int(receipt['selected'])==len(selected)
                dispositions=[line.split('\t') for line in (prefix/'dispositions.tsv').read_text().splitlines()]
                assert len(dispositions)==len(case['variants']) and [row[1] for row in dispositions]==[row['id'] for row in case['variants']]
                by={row[1]:row[2] for row in dispositions}
                assert by['first-ordinal-zero']=='SOURCE_ZERO_INDEX_SENTINEL'
                assert all(by[id]=='OUTSIDE_SOURCE_FLOAT32_MAF_BOUNDS' for id in ['float32-lower-boundary','float32-upper-boundary','minor-frequency-rejected'])
                assert by['missing-in-last-cohort']=='NOT_PRESENT_IN_ALL_COHORTS'
                assert 'column300 aliases' in (prefix/'warnings.txt').read_text()
                actual_source=original/'full-precision-values'/(case['id']+'.tsv');assert pin(actual_source)==expected['full_precision_values']
                source=values(actual_source);coordinates=matrix(prefix/'coordinates.tsv');distances=matrix(prefix/'distances.tsv')
                source_coordinates=[source['coordinates'][i*p:(i+1)*p] for i in range(n)]
                assert len(coordinates)==len(distances)==n and all(len(row)==p for row in coordinates) and all(len(row)==n for row in distances)
                errors={}
                def compare(name,actual,wanted,tolerance=2e-10):
                    assert len(actual)==len(wanted);maximum=0
                    for x,y in zip(actual,wanted,strict=True):
                        assert math.isfinite(x) and math.isfinite(y);difference=abs(x-y)/max(1,abs(y));assert difference<=tolerance,(case['id'],name,x,y,difference)
                        maximum=max(maximum,difference)
                    errors[name]=maximum
                compare('complete_squared_distances',[v for row in distances for v in row],source['distances'],1e-13)
                signs=[1 if sum(coordinates[i][axis]*source_coordinates[i][axis] for i in range(n))>=0 else -1 for axis in range(p)]
                compare('all_coordinate_values_with_explicit_sign_mapping',[v for row in coordinates for v in row],
                    [source_coordinates[i][axis]*signs[axis] for i in range(n) for axis in range(p)])
                compare('complete_projected_cohort_gram',[sum(coordinates[i][axis]*coordinates[j][axis] for axis in range(p)) for i in range(n) for j in range(n)],
                    [sum(source_coordinates[i][axis]*source_coordinates[j][axis] for axis in range(p)) for i in range(n) for j in range(n)])
                model=values(prefix/'model.tsv');coefficient_signs=[1,*signs]
                compare('all_model_coefficients_with_explicit_sign_mapping',model['coefficients'],[value*sign for value,sign in zip(source['coefficients'],coefficient_signs,strict=True)])
                compare('all_model_errors',model['errors'],source['errors'])
                compare('all_model_covariance_with_explicit_sign_mapping',model['covariance'],[source['covariance'][i*k+j]*coefficient_signs[i]*coefficient_signs[j] for i in range(k) for j in range(k)])
                compare('all_coupled_regression_statistics',model['statistics'],source['statistics'])
                report.append(dict(id=case['id'],selected_markers=len(selected),all_source_selected_ids_ordinals_and_float32_frequencies_match=True,
                    all_variants_have_dispositions=True,cohort_order_preserved=True,coordinate_sign_mapping_for_comparison_only=signs,
                    raw_source_and_native_values_retained_without_relabeling=True,maximum_scaled_differences=errors))
            print('PASS complete ancestry/model populations '+compiler+'/'+platform,flush=True)
            return dict(compiler=compiler,platform=platform,complete_cases=12,cases=report,source_population=pin(native/'ancestry-0/selected.tsv'))
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,[(c,p) for c in artifacts for p in platforms]))
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_BOTH_NATIVE_MRMEGA_ANCESTRY_BUILDS_ON_THREE_JVMS_WITH_ORIGINAL_SELECTION_MDS_AND_REGRESSION_ONLY',
            runs=results,complete_ancestry_comparisons=72,coupled_regression_comparisons=72,contract_suites=6,protected_inputs_unchanged=before,
            genotype_file_parsing_and_complete_GWAS_workflow_available=False,
            scope='Exact original selected populations and stored float32 frequencies. Full original public MDS/distance/regression function comparisons, with explicit coordinate-sign mappings only for comparisons; original outputs are unchanged. Native prepared variants are not a complete Java GWAS file reader, allele harmonizer or GC/file workflow.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete marker, geometry or model comparisons')
    raise SystemExit(main())
