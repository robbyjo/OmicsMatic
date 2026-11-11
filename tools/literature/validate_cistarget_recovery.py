# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete native recovery populations with unchanged original references."""
import argparse,concurrent.futures,csv,itertools,json,math,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/cistarget-runtime'
DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def table(path):
    with path.open(encoding='utf8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))


def numeric(a,b,context,tolerance=2e-12):
    a=float(a);b=float(b)
    if math.isnan(a) or math.isnan(b):assert math.isnan(a) and math.isnan(b),(context,a,b);return 0
    assert math.isfinite(a) and math.isfinite(b),(context,a,b)
    error=abs(a-b)/max(1,abs(b));assert error<=tolerance,(context,a,b,error);return error


def matrix(actual,expected):
    maximum=0;cells=0;rows=0
    with actual.open(encoding='utf8') as a,expected.open(encoding='utf8') as b:
        for row,(x,y) in enumerate(itertools.zip_longest(a,b)):
            assert x is not None and y is not None,(actual,'matrix row count')
            left=x.strip().split('\t');right=y.strip().split('\t');assert len(left)==len(right),(actual,row,'matrix width')
            for col,(u,v) in enumerate(zip(left,right,strict=True)):
                maximum=max(maximum,numeric(u,v,(actual,row,col)));cells+=1
            rows+=1
    return dict(rows=rows,cells=cells,maximum_scaled_difference=maximum)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--fixtures',choices=['controls-1','pairwise-controls-1'],default='controls-1');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CISTARGET_NATIVE_RECOVERY_CONTROLS',implementation_credit=False,whole_goal_complete=False,runs=[])
    try:
        original=BASE/('reference-controls-1' if a.fixtures=='controls-1' else 'pairwise-reference-controls-1');reference=json.loads((original/'COMPLETED.json').read_bytes())
        assert reference['status']==('PASS_64_ORIGINAL_CISTARGET_PREPARED_RECOVERY_AND_LEADING_EDGE_FAMILIES_ONLY' if a.fixtures=='controls-1' else 'PASS48_ORIGINAL_CTXCORE_NUMPY_REDUCTION_BOUNDARY_FAMILIES_ONLY')
        assert all(pin(ROOT/n)==v for n,v in reference['original_source_pins_unchanged'].items())
        fixtures=BASE/a.fixtures;fixture_receipt=json.loads((fixtures/'COMPLETED.json').read_bytes())
        assert all(pin(fixtures/n)==v for n,v in fixture_receipt['files'].items())
        cases=json.loads((fixtures/'cases.json').read_bytes());assert len(cases)==(64 if a.fixtures=='controls-1' else 48)
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';artifacts={};protected=[canonical,Path(__file__).resolve(),original/'COMPLETED.json',fixtures/'controls.txt',fixtures/'cases.json']
        for compiler,folder in [('javac21','recovery-build21-3'),('javac25','recovery-build25-3')]:
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_JAVA17_CISTARGET_RECOVERY_COMPONENT_BUILD_ONLY'
            assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items())
            artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']}
            artifacts[compiler]=artifact;protected.append(artifact)
        for f in original.rglob('*'):
            if f.is_file():protected.append(f)
        before={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
        state['protected_inputs_before']=before
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def run(home,command):
            home.mkdir();save(home/'command.json',command)
            with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:
                process=subprocess.run(command,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=900)
            save(home/'process.json',dict(exit_code=process.returncode));assert process.returncode==0,(home,'Read retained stdout/stderr')
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda path:str(path.resolve())
            command=[*platforms[platform],'-Xmx768m','-cp',convert(artifacts[compiler]),'org.jlinalg.enrichment.CisTargetRecoveryProbe']
            run(home/'contracts',command+['--contracts']);native=home/'native';run(home/'values',command+[convert(fixtures/'controls.txt'),convert(native)])
            report=[]
            for case in cases:
                prefix=native/case['id'];expected=original/('ctxcore' if case['profile']=='CTXCORE_0_2_0' else 'rcistarget')/case['id']
                actual=table(prefix/'metrics.tsv');wanted=table(expected/'metrics.tsv');assert len(actual)==len(wanted)==case['motifs']
                metric_error=0
                for x,y in zip(actual,wanted,strict=True):
                    assert x['motif']==y['motif'] and x['leading_rank']==y['leading_rank'],(case['id'],'exact motif/peak',x,y)
                    assert x['status']==('UNDEFINED_NES' if math.isnan(float(y['nes'])) else 'DEFINED_NES'),(case['id'],'NES status',x,y)
                    for field in y:
                        if field not in ['motif','leading_rank']:metric_error=max(metric_error,numeric(x[field],y[field],(case['id'],x['motif'],field)))
                left=table(prefix/'parameters.tsv');right=table(expected/'parameters.tsv');assert len(left)==len(right)==1
                for field in right[0]:numeric(left[0][field],right[0][field],(case['id'],field))
                left=table(prefix/'background.tsv');right=table(expected/'background.tsv');assert len(left)==len(right)
                background_error=0
                for row,(x,y) in enumerate(zip(left,right,strict=True)):
                    assert float(x['rank'])==float(y['rank']),(case['id'],'background rank',row)
                    for field in y:
                        if field!='rank':background_error=max(background_error,numeric(x[field],y[field],(case['id'],row,field)))
                curves=matrix(prefix/'curves.tsv',expected/'curves.tsv');assert curves['rows']==case['motifs']
                left=table(prefix/'targets.tsv');right=table(expected/'targets.tsv')
                # Originals retain NumPy rank order or R lexical feature order. Only
                # compare typed membership after explicitly mapping to caller order.
                expected_members=sorted([(int(r['motif']),int(r['feature']),float(r['rank']),float(r['weight'])) for r in right])
                actual_members=[(int(r['motif']),int(r['feature']),float(r['rank']),float(r['weight'])) for r in left]
                assert actual_members==expected_members,(case['id'],'leading membership',actual_members,expected_members)
                report.append(dict(id=case['id'],motifs=case['motifs'],recovery_points=curves['cells']//curves['rows'],
                    complete_curves=curves,maximum_metric_scaled_difference=metric_error,maximum_background_scaled_difference=background_error,
                    leading_ranks_match_exactly=True,all_leading_memberships_match=True,leading_members=len(left),
                    original_target_display_order_retained_separately=True,source_zero_background_NaNs_preserved=True))
            print('PASS'+str(len(cases))+' complete recovery families '+compiler+'/'+platform,flush=True)
            return dict(compiler=compiler,platform=platform,cases=report,complete_cases=len(cases))
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,[(c,p) for c in artifacts for p in platforms]))
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_BOTH_CISTARGET_RECOVERY_BUILDS_ON_THREE_JVMS_WITH_ALL_ORIGINAL_VALUES_AND_TARGETS_ONLY',runs=results,
            complete_recovery_comparisons=len(cases)*6,contract_suites=6,protected_inputs_unchanged=before,
            full_database_file_workflow_available=False,scope='Both explicit original numerical profiles, all64 families on6 build/JVM combinations. Exact peak coordinates and leading-target membership; no rank-base repair, motif subsetting or undefined-NES replacement. Python public prepared-ranking reference and original R function boundaries do not establish full database, annotation or SCENIC workflows.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source comparison checks')
    raise SystemExit(main())
