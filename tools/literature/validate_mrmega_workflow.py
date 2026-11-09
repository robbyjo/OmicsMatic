# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute and compare every original complete GWAS row, retaining original P fields."""
import argparse,concurrent.futures,csv,json,math,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/mrmega-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def table(path):
    with path.open(newline='',encoding='utf8') as stream:return list(csv.DictReader(stream,delimiter='\t'))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--all-platforms',action='store_true');args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_MRMEGA_COMPLETE_GWAS_WORKFLOW_CONTROLS',implementation_credit=False,whole_goal_complete=False,runs=[])
    try:
        controls=BASE/'workflow-controls-1';reference=BASE/'workflow-original-1'
        control=json.loads((controls/'COMPLETED.json').read_bytes());assert all(pin(controls/n)==v for n,v in control['files'].items())
        original=json.loads((reference/'COMPLETED.json').read_bytes());assert original['status']=='PASS_TWENTY_TWO_COMPLETE_ORIGINAL_MRMEGA_GWAS_FILE_WORKFLOWS'
        cases=json.loads((controls/'cases.json').read_bytes());assert len(cases)==len(original['cases'])==22
        builds={'javac21':'workflow-build-21-4'}
        if args.all_platforms:builds['javac25']='workflow-build-25-3'
        artifacts={}
        for compiler,folder in builds.items():
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_NATIVE_MRMEGA_WORKFLOW_BUILD_ONLY'
            assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']};artifacts[compiler]=artifact
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe']}
        if args.all_platforms:platforms.update(windows25=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],linux21=['wsl','-d',DISTRO,'--exec','/usr/bin/java'])
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [artifacts[compiler],canonical]);reports=[]
            for case,old in zip(cases,original['cases'],strict=True):
                assert case['id']==old['id'];id=case['id'];parent=home/id;parent.mkdir();native=parent/'native'
                command=[*platforms[platform],'-cp',cp,'org.jlinalg.cli.MrMegaWorkflowCli','--settings',convert(controls/case['settings_file']),
                    '--file-list',convert(controls/case['file_lists']['linux' if platform=='linux21' else 'windows']),'--out',convert(native)]
                save(parent/'command.json',command);run=subprocess.run(command,capture_output=True,timeout=120);(parent/'stdout').write_bytes(run.stdout);(parent/'stderr').write_bytes(run.stderr);save(parent/'process.json',dict(exit_code=run.returncode))
                assert run.returncode==0,(id,run.stderr.decode(errors='replace'))
                result=json.loads((native/'run.json').read_bytes());assert result['status']=='PASS_NATIVE_MRMEGA_WITH_WARNINGS'
                assert 'ANALYSIS WARNINGS' in run.stderr.decode() and (native/'WARNINGS.md').is_file()
                source=reference/id;assert pin(source/'original.result')==old['result'] and pin(source/'original.log')==old['program_log']
                actual=table(native/'results.tsv');expected=table(source/'original.result');assert [r['MarkerName'] for r in actual]==[r['MarkerName'] for r in expected]
                assert len(actual)==old['result_rows']==result['variants']
                log=(source/'original.log').read_text();pc_block=log.split('Principal components:',1)[1].strip().split('\n\n',1)[0].splitlines()[1:]
                source_x=[[float(v) for v in row.split()[1:]] for row in pc_block];native_x=[[float(row['axis_'+str(j)]) for j in range(case['settings']['axes'])] for row in table(native/'coordinates.tsv')]
                assert len(source_x)==len(native_x)==result['cohorts']
                p=case['settings']['axes'];signs=[1 if sum(a[j]*b[j] for a,b in zip(source_x,native_x,strict=True))>=0 else -1 for j in range(p)]
                maximum=0;probabilities=[];fitted=0;skipped=0
                def close(a,b,context):
                    nonlocal maximum
                    a=float(a);b=float(b);assert math.isfinite(a) and math.isfinite(b),(id,context,a,b)
                    difference=abs(a-b)/max(1,abs(b));assert difference<=6e-6,(id,context,a,b,difference);maximum=max(maximum,difference)
                for a,b in zip(actual,expected,strict=True):
                    for col in ['MarkerName','Chromosome','Position','EA','NEA','Ncohort','Effects']:assert a[col]==b[col],(id,col,a[col],b[col])
                    for col in ['EAF','Nsample']:close(a[col],b[col],col)
                    if b['Comments']=='SmallCohortCount':
                        skipped+=1;assert a['status']=='SmallCohortCount' and all(a[col]=='NA' for col in ['raw_chisq_association','output_chisq_association','lnBF']);continue
                    assert b['Comments']=='NA' and a['status']=='PASS';fitted+=1
                    for j in range(p+1):close(float(a['beta_'+str(j)])*(1 if j==0 else signs[j-1]),b['beta_'+str(j)],'beta_'+str(j));close(a['se_'+str(j)],b['se_'+str(j)],'se_'+str(j))
                    for col in ['chisq_ancestry_het','chisq_residual_het','lnBF','ndf_association','ndf_ancestry_het','ndf_residual_het']:close(a[col],b[col],col)
                    close(a['output_chisq_association'],b['chisq_association'],'output_chisq_association')
                    for original_col,native_col in [('P-value_association','stable_output_association_P'),('P-value_ancestry_het','stable_ancestry_P'),('P-value_residual_het','stable_residual_P')]:
                        probabilities.append(dict(marker=a['MarkerName'],original_field=original_col,original_raw=b[original_col],native_field=native_col,native_raw=a[native_col],source_and_native_definitions_retained=True))
                lambda_source=[float(v) for v in re.findall(r'^Lambda:([^\n]+)',log,re.M)];lambda_native=table(native/'cohorts.tsv');assert len(lambda_source)==len(lambda_native)==result['cohorts']
                for a,b in zip(lambda_native,lambda_source,strict=True):close(a['lambda'],b,'cohort-lambda')
                if case['settings']['output_gc']:close(result['output_gc_lambda'],float(re.search(r'GCO Lambda:([^\n]+)',log).group(1)),'output-lambda')
                if case['spec'].get('deflation'):assert 0<result['output_gc_lambda']<1
                if case['spec'].get('infer'):
                    chosen=[s.strip() for s in log.split('Selected markers:',1)[1].split('eaf values:',1)[0].splitlines() if s.strip()]
                    assert [r['marker'] for r in table(native/'ancestry-selected.tsv')]==chosen
                save(parent/'original-and-native-probabilities.json',probabilities)
                reports.append(dict(id=id,complete_original_and_native_variant_rows=len(actual),fits=fitted,small_cohort_dispositions=skipped,
                    coordinate_sign_map_for_comparison_only=signs,maximum_scaled_difference_from_six_digit_original=maximum,probability_fields=len(probabilities),
                    native_result=pin(native/'results.tsv'),raw_source_probabilities_retained_separately=True))
                print('PASS complete native/original GWAS '+compiler+'/'+platform+'/'+id,flush=True)
            return dict(compiler=compiler,platform=platform,cases=reports)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,[(c,p) for c in artifacts for p in platforms]))
        assert pin(canonical)==before and all(pin(controls/n)==v for n,v in control['files'].items())
        state.update(status='PASS_NATIVE_COMPLETE_GWAS_FILE_FLOW_AGAINST_TWENTY_TWO_ORIGINAL_FAMILIES',runs=runs,
            complete_workflow_comparisons=22*len(runs),canonical_executable_unchanged=before,full_precision_original_file_flow_comparison=False,
            scope='Original complete six-significant-digit file outputs and lambda logs; source/native probability definitions retained separately, not substituted. Full-precision file preparation, independent stable probability checks and negative contracts still required before credit.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete workflow checks')
    raise SystemExit(main())
