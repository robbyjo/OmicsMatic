# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare all stored frequencies, participating effects/weights and fits at full precision."""
import argparse,json,math,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,pin,save
from validate_mrmega_workflow import BASE,table


def vector(text):return [float(v) for v in text.split(',')] if text else []


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_COMPLETE_FILE_PREPARATION_AND_STABLE_PROBABILITIES',implementation_credit=False,whole_goal_complete=False)
    try:
        reference=BASE/'preparation-original-1';original=json.loads((reference/'COMPLETED.json').read_bytes())
        assert original['status']=='PASS_TWENTY_TWO_FULL_PRECISION_ORIGINAL_FILE_PREPARATION_AND_REGRESSION_FAMILIES'
        validation=BASE/'workflow-validation-4';validated=json.loads((validation/'COMPLETED.json').read_bytes())
        assert validated['status']=='PASS_NATIVE_COMPLETE_GWAS_FILE_FLOW_AGAINST_TWENTY_TWO_ORIGINAL_FAMILIES' and validated['complete_workflow_comparisons']==132
        controls=BASE/'workflow-controls-1';cases=json.loads((controls/'cases.json').read_bytes());reports=[];probabilities=[];qrows=['id\tq\tdf'];counter=0
        protected={p.relative_to(ROOT).as_posix():pin(p) for p in [reference/'COMPLETED.json',validation/'COMPLETED.json',ROOT/'build/cli/omicsmatic-0.3.7.jar']}
        for run in validated['runs']:
            for case,expected in zip(cases,original['cases'],strict=True):
                id=case['id'];assert id==expected['id'];source=reference/id
                assert all(pin(source/n)==v for n,v in expected['outputs'].items())
                native=validation/(run['compiler']+'-'+run['platform'])/id/'native';p=case['settings']['axes'];k=p+1
                source_x=[[float(v) for v in row.split('\t')] for row in (source/'coordinates.tsv').read_text().splitlines()]
                native_x=[[float(row['axis_'+str(j)]) for j in range(p)] for row in table(native/'coordinates.tsv')]
                assert len(source_x)==len(native_x)==8
                signs=[1 if sum(a[j]*b[j] for a,b in zip(source_x,native_x,strict=True))>=0 else -1 for j in range(p)]
                differences={}
                def close(name,a,b,tol=2e-10):
                    assert len(a)==len(b);maximum=0
                    for x,y in zip(a,b,strict=True):
                        assert math.isfinite(x) and math.isfinite(y);diff=abs(x-y)/max(1,abs(y));assert diff<=tol,(id,name,x,y,diff);maximum=max(maximum,diff)
                    differences[name]=max(differences.get(name,0),maximum)
                close('all_coordinates',[v for row in native_x for v in row],[source_x[i][j]*signs[j] for i in range(8) for j in range(p)])
                close('all_cohort_lambdas',[float(r['lambda']) for r in table(native/'cohorts.tsv')],[float(r['lambda']) for r in table(source/'lambdas.tsv')],2e-13)
                close('output_lambda',[json.loads((native/'run.json').read_bytes())['output_gc_lambda']],[float((source/'output-lambda.txt').read_text())],2e-12)
                prepared=table(native/'prepared.tsv');by={}
                for row in prepared:by.setdefault(row['marker'],[]).append(row)
                used={}
                for row in table(native/'used.tsv'):used.setdefault(row['marker'],[]).append(row)
                fitrows={row['MarkerName']:row for row in table(native/'results.tsv')};cv={}
                for row in table(native/'covariance.tsv'):cv.setdefault(row['marker'],[]).append(float(row['covariance']))
                source_prepared=table(source/'prepared.tsv');assert [r['marker'] for r in source_prepared]==list(by)==list(fitrows)
                for old in source_prepared:
                    marker=old['marker'];current=by[marker];assert [int(r['cohort']) for r in current]==list(range(8))
                    assert [float(r['stored_EAF']) for r in current]==vector(old['frequencies'])
                    if marker in used:
                        indices=[i for i,v in enumerate(vector(old['frequencies'])) if v!=-1]
                        assert [int(r['cohort']) for r in used[marker]]==indices
                        assert [float(r['effect']) for r in used[marker]]==vector(old['effects'])
                        assert [float(r['weight']) for r in used[marker]]==vector(old['weights'])
                original_fits=table(source/'fits.tsv');assert [r['marker'] for r in original_fits]==list(used)==list(cv)
                coefficient_signs=[1,*signs]
                for old in original_fits:
                    marker=old['marker'];row=fitrows[marker]
                    close('all_coefficients',[float(row['beta_'+str(j)]) for j in range(k)],[v*s for v,s in zip(vector(old['coefficients']),coefficient_signs,strict=True)])
                    close('all_standard_errors',[float(row['se_'+str(j)]) for j in range(k)],vector(old['errors']))
                    close('all_covariance',cv[marker],[v*coefficient_signs[i//k]*coefficient_signs[i%k] for i,v in enumerate(vector(old['covariance']))])
                    close('all_test_statistics_and_lnBF',[float(row[c]) for c in ['raw_chisq_association','chisq_ancestry_het','chisq_residual_het','lnBF']],
                        [float(old[c]) for c in ['raw_association','ancestry','residual','lnBF']])
                    for qcol,dfcol,pcol in [('output_chisq_association','ndf_association','stable_output_association_P'),('chisq_ancestry_het','ndf_ancestry_het','stable_ancestry_P'),('chisq_residual_het','ndf_residual_het','stable_residual_P')]:
                        counter+=1;label=str(counter);q=float(row[qcol]);df=int(row[dfcol]);probabilities.append(dict(id=label,compiler=run['compiler'],platform=run['platform'],case=id,marker=marker,field=pcol,q=q,df=df,native=float(row[pcol])))
                        qrows.append(label+'\t'+format(q,'.17g')+'\t'+str(df))
                reports.append(dict(compiler=run['compiler'],platform=run['platform'],id=id,prepared_variants=len(source_prepared),full_precision_regressions=len(original_fits),
                    stored_frequencies_and_used_effects_weights_match_exactly=True,coordinate_sign_map_for_comparison_only=signs,maximum_scaled_differences=differences))
        qinput=out/'r-input.tsv';qinput.write_text('\n'.join(qrows)+'\n',encoding='utf8');script=out/'chi-square-reference.R'
        script.write_text('''a <- commandArgs(trailingOnly=TRUE)
d <- read.delim(a[1],check.names=FALSE)
o <- data.frame(id=d$id,probability=pchisq(d$q,d$df,lower.tail=FALSE))
options(digits=17)
write.table(o,file=a[2],sep="\\t",quote=FALSE,row.names=FALSE,na="NaN")
cat(R.version.string,"\\n")
''',encoding='utf8')
        assert RSCRIPT.is_file(),'R reference unavailable: skip dependent probability validation and grant no complete workflow credit'
        routput=out/'r-probabilities.tsv';command=[str(RSCRIPT),'--vanilla',str(script),str(qinput),str(routput)];save(out/'r-command.json',command)
        run=subprocess.run(command,capture_output=True,timeout=120);(out/'r.stdout').write_bytes(run.stdout);(out/'r.stderr').write_bytes(run.stderr);save(out/'r-process.json',dict(exit_code=run.returncode));assert run.returncode==0
        rows=table(routput);assert len(rows)==len(probabilities);maximum=0;undefined=[]
        for current,old in zip(probabilities,rows,strict=True):
            assert current['id']==old['id'];expected=float(old['probability']);observed=current['native']
            if current['q']<=0 or current['df']<=0:
                assert math.isnan(observed);undefined.append(dict(**current,r_probability=expected));continue
            assert math.isfinite(observed) and 0<=observed<=1 and abs(observed-expected)<=3e-11*max(expected,1e-300),(current,expected)
            maximum=max(maximum,abs(observed-expected)/max(expected,1e-300))
        save(out/'native-probabilities.json',probabilities);save(out/'undefined-boundaries.json',undefined)
        assert all(pin(ROOT/n)==v for n,v in protected.items())
        state.update(status='PASS_ALL_132_FULL_PRECISION_NATIVE_FILE_PREPARATIONS_REGRESSIONS_AND_SEPARATE_R_PROBABILITIES',comparisons=reports,
            complete_workflow_comparisons=len(reports),stable_R_probability_fields=len(probabilities),maximum_R_probability_relative_difference=maximum,
            protected_inputs_unchanged=protected,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},
            catalog_credit=False,scope='Exact all-marker float32 frequencies and participating effects/weights from original parser/marker APIs. Full original coefficient/SE/covariance/statistic/lnBF values with comparison-only signs. Complete original CLI outputs remain separate. Stable native survival probabilities have independent actual base-R references.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete full-precision file/fit checks')
    raise SystemExit(main())
