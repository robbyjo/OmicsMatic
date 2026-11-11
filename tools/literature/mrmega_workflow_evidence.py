# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check frozen complete GWAS file-flow evidence without executing analysis software."""
import argparse,csv,io,json,math,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

HOME=ROOT/'docs/literature/mrmega-workflow-development';BASE='build/literature-review/mrmega-runtime/'
STATUS='PASS_NATIVE_MRMEGA_COMPLETE_GWAS_WORKFLOW_DEVELOPMENT'


def verify(archive_only=False,home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']==STATUS
    assert proof['standalone_native_file_workflow_available'] and not any(proof[k] for k in ['main_dispatcher_registered','catalog_credit','whole_paper_reproduced','whole_goal_complete'])
    assert (proof['original_families'],proof['complete_workflow_comparisons'],proof['full_precision_fit_comparisons'],proof['stable_R_probability_fields'],proof['real_CLI_refusals'],proof['analytic_odd_median_comparisons'])==(22,132,4134,12402,144,6)
    assert pin(home/'controls.zip')==proof['archive'] and all(pin(home/n)==v for n,v in proof['artifacts'].items())
    assert all(pin(ROOT/n)==v for n,v in proof['frozen_parent_pins'].items())
    if not archive_only:
        assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    files=unpack(home/'controls.zip',proof)
    for n,v in proof['source_pins'].items():assert byte_pin(files['current-source/'+n])==v
    def doc(n):return json.loads(files[BASE+n])
    def table(n):return list(csv.DictReader(io.StringIO(files[BASE+n].decode('utf8')),delimiter='\t'))
    def vec(s):return [float(v) for v in s.split(',')] if s else []
    def close(a,b,tol=2e-10):
        assert len(a)==len(b)
        assert all(math.isfinite(x) and math.isfinite(y) and abs(x-y)<=tol*max(1,abs(y)) for x,y in zip(a,b,strict=True))
    def process(prefix,owned=False):
        result=doc(prefix+'/process.json');assert result['exit_code']==0
        if owned:assert 'owned_live_after_cleanup' in result and not result['owned_live_after_cleanup']
    controls=doc('workflow-controls-1/COMPLETED.json');assert controls['cases']==22
    assert all(byte_pin(files[BASE+'workflow-controls-1/'+n])==v for n,v in controls['files'].items())
    cases=doc('workflow-controls-1/cases.json');original=doc('workflow-original-1/COMPLETED.json');prepared=doc('preparation-original-1/COMPLETED.json')
    assert original['status']=='PASS_TWENTY_TWO_COMPLETE_ORIGINAL_MRMEGA_GWAS_FILE_WORKFLOWS'
    assert prepared['status']=='PASS_TWENTY_TWO_FULL_PRECISION_ORIGINAL_FILE_PREPARATION_AND_REGRESSION_FAMILIES'
    assert len(original['cases'])==len(prepared['cases'])==len(cases)==22
    assert original['original_sources_unchanged']==prepared['original_sources_unchanged'] and len(original['original_sources_unchanged'])==100
    parent=ROOT/'docs/literature/mrmega-ancestry-development';parent_files=unpack(parent/'controls.zip',json.loads((parent/'VALIDATION.json').read_bytes()))
    assert json.loads(parent_files[BASE+'ancestry-original-1/COMPLETED.json'])['original_sources_unchanged']==original['original_sources_unchanged']
    for c,folder in [('javac21','workflow-build-21-4'),('javac25','workflow-build-25-3')]:
        built=doc(folder+'/COMPLETED.json');assert built['status']=='PASS_NATIVE_MRMEGA_WORKFLOW_BUILD_ONLY' and built['all_inherited_entries_unchanged']
        artifact=home/('mrmega-workflow-'+c+'.jar');assert byte_pin(files[built['artifact']['path']])==pin(artifact)
        assert doc(folder+'/compile.json')['exit_code']==0
        for n,v in built['source_pins'].items():assert byte_pin(files[BASE+folder+'/source-snapshot/'+n])==byte_pin(files['current-source/'+n])==v
        with zipfile.ZipFile(parent/('mrmega-ancestry-'+c+'.jar')) as old,zipfile.ZipFile(artifact) as new:
            assert all(new.read(n)==old.read(n) for n in old.namelist())
            for n in new.namelist():
                assert n=='META-INF/LICENSE' or n.startswith(('org/jlinalg/meta/MrMega','org/jlinalg/cli/MrMegaWorkflowCli')) and n.endswith('.class')
                if n.endswith('.class'):assert int.from_bytes(new.read(n)[6:8],'big')==61
    for p in ['preparation-original-1/compile-original-entry','preparation-original-1/compile-probe']:process(p,True)
    validation=doc('workflow-validation-4/COMPLETED.json');precision=doc('preparation-validation-2/COMPLETED.json')
    assert validation['status']=='PASS_NATIVE_COMPLETE_GWAS_FILE_FLOW_AGAINST_TWENTY_TWO_ORIGINAL_FAMILIES' and validation['complete_workflow_comparisons']==132
    assert precision['status']=='PASS_ALL_132_FULL_PRECISION_NATIVE_FILE_PREPARATIONS_REGRESSIONS_AND_SEPARATE_R_PROBABILITIES'
    assert {(r['compiler'],r['platform']) for r in validation['runs']}=={(c,p) for c in ['javac21','javac25'] for p in ['windows21','windows25','linux21']}
    probabilities=doc('preparation-validation-2/native-probabilities.json');r_rows=table('preparation-validation-2/r-probabilities.tsv');r_input=table('preparation-validation-2/r-input.tsv')
    assert doc('preparation-validation-2/r-process.json')['exit_code']==0 and len(probabilities)==len(r_rows)==len(r_input)==12402
    count=0;fits=0
    for run in validation['runs']:
        for case,old,source_case in zip(cases,original['cases'],prepared['cases'],strict=True):
            id=case['id'];assert old['id']==source_case['id']==id
            assert all(byte_pin(files[BASE+'preparation-original-1/'+id+'/'+n])==v for n,v in source_case['outputs'].items())
            assert byte_pin(files[BASE+'workflow-original-1/'+id+'/original.result'])==old['result'] and byte_pin(files[BASE+'workflow-original-1/'+id+'/original.log'])==old['program_log']
            process('workflow-original-1/'+id+'/complete-program',True);process('preparation-original-1/'+id+'/original-apis',True)
            prefix='workflow-validation-4/'+run['compiler']+'-'+run['platform']+'/'+id;process(prefix)
            native=prefix+'/native/';source='preparation-original-1/'+id+'/';p=case['settings']['axes'];k=p+1
            result=table(native+'results.tsv');original_result=table('workflow-original-1/'+id+'/original.result')
            assert [r['MarkerName'] for r in result]==[r['MarkerName'] for r in original_result]
            actual={r['MarkerName']:r for r in result};freq={};used={};cv={}
            for row in table(native+'prepared.tsv'):freq.setdefault(row['marker'],[]).append(float(row['stored_EAF']))
            for row in table(native+'used.tsv'):used.setdefault(row['marker'],[]).append(row)
            for row in table(native+'covariance.tsv'):cv.setdefault(row['marker'],[]).append(float(row['covariance']))
            original_prep=table(source+'prepared.tsv');assert [r['marker'] for r in original_prep]==list(actual)==list(freq)
            x=[[float(r['axis_'+str(j)]) for j in range(p)] for r in table(native+'coordinates.tsv')]
            sx=[[float(v) for v in line.split('\t')] for line in files[BASE+source+'coordinates.tsv'].decode().splitlines()]
            assert len(x)==len(sx)==8;signs=[1 if sum(a[j]*b[j] for a,b in zip(x,sx,strict=True))>=0 else -1 for j in range(p)];sg=[1,*signs]
            close([v for r in x for v in r],[sx[i][j]*signs[j] for i in range(8) for j in range(p)])
            close([float(r['lambda']) for r in table(native+'cohorts.tsv')],[float(r['lambda']) for r in table(source+'lambdas.tsv')],2e-13)
            close([doc(native+'run.json')['output_gc_lambda']],[float(files[BASE+source+'output-lambda.txt'])],2e-12)
            assert doc(native+'run.json')['status']=='PASS_NATIVE_MRMEGA_WITH_WARNINGS' and b'ANALYSIS WARNINGS' in files[BASE+native+'WARNINGS.md']
            for row in original_prep:
                marker=row['marker'];assert freq[marker]==vec(row['frequencies'])
                if marker in used:
                    assert [int(r['cohort']) for r in used[marker]]==[i for i,v in enumerate(freq[marker]) if v!=-1]
                    assert [float(r['effect']) for r in used[marker]]==vec(row['effects']) and [float(r['weight']) for r in used[marker]]==vec(row['weights'])
            full_fits=table(source+'fits.tsv');assert [r['marker'] for r in full_fits]==list(used)==list(cv)
            for row,previous in zip(result,original_result,strict=True):
                for n in ['MarkerName','Chromosome','Position','EA','NEA','Ncohort','Effects']:assert row[n]==previous[n]
                close([float(row[n]) for n in ['EAF','Nsample']],[float(previous[n]) for n in ['EAF','Nsample']],6e-6)
                if previous['Comments']=='SmallCohortCount':assert row['status']=='SmallCohortCount' and row['lnBF']=='NA'
                else:
                    assert row['status']=='PASS' and previous['Comments']=='NA'
                    close([float(row[n]) for n in ['output_chisq_association','chisq_ancestry_het','chisq_residual_het','lnBF']],
                        [float(previous[n]) for n in ['chisq_association','chisq_ancestry_het','chisq_residual_het','lnBF']],6e-6)
            for old_fit in full_fits:
                marker=old_fit['marker'];row=actual[marker];fits+=1
                close([float(row['beta_'+str(j)]) for j in range(k)],[v*s for v,s in zip(vec(old_fit['coefficients']),sg,strict=True)])
                close([float(row['se_'+str(j)]) for j in range(k)],vec(old_fit['errors']))
                close(cv[marker],[v*sg[i//k]*sg[i%k] for i,v in enumerate(vec(old_fit['covariance']))])
                close([float(row[n]) for n in ['raw_chisq_association','chisq_ancestry_het','chisq_residual_het','lnBF']],[float(old_fit[n]) for n in ['raw_association','ancestry','residual','lnBF']])
                for qcol,dfcol,pcol in [('output_chisq_association','ndf_association','stable_output_association_P'),('chisq_ancestry_het','ndf_ancestry_het','stable_ancestry_P'),('chisq_residual_het','ndf_residual_het','stable_residual_P')]:
                    current=probabilities[count];expected=r_rows[count];r_source=r_input[count];count+=1;q=float(row[qcol]);df=int(row[dfcol]);prob=float(row[pcol]);r=float(expected['probability'])
                    assert (current['compiler'],current['platform'],current['case'],current['marker'],current['field'],current['id'])==(run['compiler'],run['platform'],id,marker,pcol,expected['id'])
                    assert current['q']==q and current['df']==df
                    assert r_source['id']==expected['id'] and float(r_source['q'])==q and int(r_source['df'])==df
                    if q<=0 or df<=0:assert math.isnan(prob) and math.isnan(current['native'])
                    else:assert prob==current['native'] and math.isfinite(prob) and 0<=prob<=1 and abs(prob-r)<=3e-11*max(r,1e-300)
    assert (fits,count)==(4134,12402)
    contracts=doc('workflow-contracts-3/COMPLETED.json')
    assert contracts['status']=='PASS_TWENTY_FOUR_REAL_CLI_REFUSALS_AND_ANALYTIC_ODD_MEDIAN_ON_BOTH_BUILDS_THREE_JVMS'
    assert (contracts['refusal_comparisons'],contracts['analytic_odd_median_comparisons'])==(144,6)
    assert all(byte_pin(files[n])==v for n,v in contracts['input_pins'].items())
    assert {(r['compiler'],r['platform']) for r in contracts['runs']}=={(c,p) for c in ['javac21','javac25'] for p in ['windows21','windows25','linux21']}
    for run in contracts['runs']:
        assert len(run['contracts'])==25
        for case in run['contracts']:
            prefix='workflow-contracts-3/'+run['compiler']+'-'+run['platform']+'/'+case['id']+'/';code=doc(prefix+'process.json')['exit_code']
            assert code==case['exit_code']==(0 if case['id']=='analytic-odd-median' else 2)
            if code==2:
                assert BASE+prefix+'native/results.tsv' not in files and b'FAILED' in files[BASE+prefix+'stderr']
                if BASE+prefix+'native/WARNINGS.md' in files:assert b'Required action:' in files[BASE+prefix+'native/WARNINGS.md'] and doc(prefix+'native/run.json')['status'].startswith('FAIL2_')
            else:
                assert all(float(r['lambda'])==4/.4549364 for r in table(prefix+'native/cohorts.tsv'))
                rows=table(prefix+'native/results.tsv');assert len(rows)==5
                for i,row in enumerate(rows):assert abs(float(row['beta_0'])-i-1)<=2e-15*(i+1) and float(row['raw_chisq_association'])==8*(i+1)**2
    assert not any(n.endswith(('.o','/original-preparation-probe','/MR-MEGA_v0.2.zip')) or n.startswith(BASE+'reference-build-1/source/') for n in files)
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen full-flow numerical checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');args=parser.parse_args()
    proof,_=verify(args.archive_only);print(proof['status']+';132 full file flows/4134 fits/12402 R probabilities; catalog registration pending')
