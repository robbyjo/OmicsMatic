# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read every retained workflow family and independently recompute fitted objectives, effects, tails, filters and PCA equations; execute no R or optimizer."""
import argparse,gzip,json,math,traceback
from pathlib import Path
from audit_leafcutter_pca import audit as audit_pca
from validate_leafcutter_phenotypes import compare
from validate_leafcutter_models import independent_posterior
from signac_scan_evidence import byte_pin,strict_json


def obj(path):return strict_json(path.read_bytes())
def lines(path):
    raw=path.read_bytes();return (gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw).decode('utf8').splitlines()
def matrix(path,ratio=False):
    text=lines(path);header=text[0].split();samples=header[1:] if ratio else header;rows=[]
    if ratio:assert header[0]=='chrom'
    for line in text[1:]:
        fields=line.split();assert len(fields)==len(samples)+1
        values=[tuple(map(int,v.split('/'))) if ratio else int(v) for v in fields[1:]];rows.append((fields[0],values))
    assert len(set(samples))==len(samples) and len({r[0] for r in rows})==len(rows);return samples,rows
def close(a,b,absolute=2e-11):assert math.isfinite(a) and math.isfinite(b) and math.isclose(a,b,rel_tol=2e-10,abs_tol=absolute),(a,b)
def clusters(path):
    result=[]
    for ordinal,line in enumerate(lines(path),1):
        f=line.split();result.append(dict(ordinal=ordinal,axis=f[0].split(':'),introns=[dict(zip(['start','end','reads'],map(int,v.split(':')))) for v in f[1:]]))
    return result
def effects(par):
    k=len(par['conc']);beta=[[scale*(v-1/k) for v in row] for row,scale in zip(par['beta_raw'],par['beta_scale'])]
    def proportions(eta):
        v=[math.exp(x) for x in eta];s=sum(v);w=[x/s*c for x,c in zip(v,par['conc'])];total=sum(w);return [x/total for x in w]
    first=proportions(beta[0]);second=proportions([a+b for a,b in zip(beta[0],beta[1])]);return [dict(logef=b,baseline=a,perturbed=c,deltapsi=c-a) for a,b,c in zip(first,beta[1],second)]
def table(path,records):
    text=lines(path);header=text[0].split('\t');assert len(set(header))==len(header) and len(text)==len(records)+1
    for line,row in zip(text[1:],records):
        fields=line.split('\t');assert len(fields)==len(header) and set(header)==set(row)-{'_row'}
        if '_row' in row:assert isinstance(row['_row'],str)
        for key,value in zip(header,fields):
            wanted=row[key]
            if isinstance(wanted,(int,float)) and not isinstance(wanted,bool):close(float(value),wanted)
            else:assert value==wanted,(key,value,wanted)


def audit(analysis):
    status=obj(analysis/'analysis-status.json');assert status['status']=='PASS_ORIGINAL_LEAFCUTTER_WORKFLOWS' and status['result_admitted'] is True and status['atomic_batch'] is True
    manifest=obj(analysis/'manifest.json');assert byte_pin((analysis/'manifest.json').read_bytes())['sha256']==status['manifest_sha256']
    for name,want in manifest['files'].items():assert byte_pin((analysis/name).read_bytes())==want,name
    for name in ['original.process.json','java-process.json']:assert obj(analysis/'worker'/name)['owned_live_after_cleanup']==[]
    request=obj(analysis/'request.original.json');worker=obj(analysis/'worker/COMPLETED.json');assert worker['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_WORKFLOWS_ONLY' and worker['namespace_patched'] is False and worker['analysis_installed_software'] is False
    summaries=obj(analysis/'native-audit.json');assert len(worker['cases'])==len(request['analyses'])==len(summaries['cases'])==8 and summaries['independent_optimizer_refitting'] is False
    checks=dict(complete_cases=8,complete_manifest_files=len(manifest['files']),native_audit_operations=summaries['audit_operations'],retained_model_objectives=0,successful_clusters=0,filtered_cluster_dispositions=0,effect_introns=0,original_table_cells=0,exact_cluster_count_cells=0,PCA_cases=0,PCA_equations=0,source_undefined_variance_ratios=0,randomized_PCA_cases=0,variance_ratio_equations=0,floating_values=0,exact_values=0,maximum_absolute_error=0.0)
    for row in obj(analysis/'input-capture.json'):assert byte_pin((analysis/'inputs'/(row['id']+'.original')).read_bytes())=={k:row[k] for k in ['bytes','sha256']}
    for case,summary in zip(request['analyses'],summaries['cases']):
        home=analysis/'worker'/case['id'];assert summary['id']==case['id'] and summary['kind']==case['kind'] and obj(home/'COMPLETED.json')['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_CASE_ONLY'
        if case['kind'] in ['cluster','pipeline']:
            native=obj(home/'native-clustering.json');assert native['pooled']==clusters(home/'original/leaf_pooled') and native['refined']==clusters(home/'original/leaf_refined');samples,ratios=matrix(home/'original/leaf_perind.counts.gz',True);ns,numerators=matrix(home/'original/leaf_perind_numers.counts.gz');assert samples==ns==native['samples'] and len(native['rows'])==len(ratios)==len(numerators)
            for row,(identifier,pairs),(other,values) in zip(native['rows'],ratios,numerators):
                assert row['id']==identifier==other and row['numerators']==[v[0] for v in pairs]==values and row['denominators']==[v[1] for v in pairs];checks['exact_cluster_count_cells']+=2*len(pairs)
            physical=[(sample['sample_id'],lines(analysis/'inputs'/(case['id']+'-junction-'+str(i)+'.original'))) for i,sample in enumerate(case['samples'])];original_lines=[(i,j,line) for i,(_,values) in enumerate(physical) for j,line in enumerate(values)];decisions=native['input_dispositions'];assert len(original_lines)==len(decisions)
            for (i,j,line),decision in zip(original_lines,decisions):assert (decision['sample_ordinal'],decision['row_ordinal'],decision['original_line'])==(i,j,line)
        if case['kind']=='phenotype' or 'phenotype' in case:
            source=obj(home/'phenotype/source-report.json');native=obj(home/'phenotype/native-phenotype.json');errors=[];compare({k:source[k] for k in native},native,case['id'],errors,checks);assert errors==[],errors
            for key,value in audit_pca(source,source['PCA']).items():checks[key]+=value
            samples,rows=matrix(home/'phenotype/input.counts.gz',True);assert samples==source['samples'] and [dict(id=n,counts=[list(v) for v in vs]) for n,vs in rows]==source['original_rows']
        if case['kind']=='differential' or 'differential' in case:
            ds=home/'differential';source=obj(ds/'source-report.json');native=obj(ds/'native-model-audit.json');assert source['status']=='PASS_COMPLETE_ORIGINAL_DIFFERENTIAL_PROGRAM_ONLY' and source['original_namespace_patched'] is False and source['numeric_export_significant_digits']==17 and len(source['namespace_functions_checked'])==24 and native['independent_refitting'] is False
            # Both binary RDS families are retained, alongside complete typed fits.
            for filename in ['original-results.rds','original-input-state.rds']:assert gzip.decompress((ds/filename).read_bytes()).startswith(b'X\n')
            counts_name='counts.txt.gz' if case['kind']=='pipeline' or str(case['counts']).endswith('.gz') else 'counts.txt';samples,counts=matrix(ds/counts_name);groups=[line.split() for line in lines(ds/'groups.txt')];selected=[r[0] for r in groups];assert source['samples']==selected and source['introns']==[r[0] for r in counts] and source['counts']==[[values[samples.index(s)] for s in selected] for _,values in counts];selection=obj(ds/'sample-selection.json');assert selection['original_count_samples']==samples and selection['selected_samples']==selected and selection['omitted_samples']==[s for s in samples if s not in selected]
            options=case['differential'];codes=source['numeric_groups'];assert set(codes)=={0,1} and len(codes)==len(selected);by={}
            for index,name in enumerate(source['introns']):f=name.split(':');by.setdefault(f[0]+':'+f[3],[]).append(index)
            raw_table={r['cluster']:r for r in source['raw_cluster_table']};raw_effects={r['intron']:r for r in source['raw_effects']};assert list(raw_table)==list(source['results'])==[r['cluster'] for r in native['clusters']] and set(by)==set(raw_table)
            all_effects=[]
            for cluster,res in source['results'].items():
                columns=by[cluster];k=len(columns);y=[[source['counts'][j][i] for j in columns] for i in range(len(selected))];totals=list(map(sum,y));use=[i for i,t in enumerate(totals) if t>0];high=[i for i in use if totals[i]>=options['minimum_coverage']];introns=[j for j in range(k) if sum(y[i][j]>0 for i in use)>=options['minimum_samples_per_intron']];hg=[sum(codes[i]==g for i in high) for g in [0,1]]
                reason=('Too many introns in cluster' if k>options['maximum_cluster_size'] else '<=1 junction in cluster' if k<=1 else '<=1 sample with coverage>0' if len(use)<=1 else '<=1 sample with coverage>min_coverage' if len(high)<=1 else '<2 introns used in >=min_samples_per_intron samples' if len(introns)<2 else 'Not enough valid samples' if min(hg)<options['minimum_samples_per_group'] else 'Success');assert res['status']==raw_table[cluster]['status']==reason
                ledger=next(r for r in native['clusters'] if r['cluster']==cluster);assert ledger['status']==reason and ledger['covered_samples']==len(use) and ledger['coverage_eligible_groups']==hg and ledger['supported_introns']==len(introns)
                if reason!='Success':assert all(raw_table[cluster][key]=='NA' for key in ['loglr','df','p','p.adjust']);checks['filtered_cluster_dispositions']+=1;continue
                checks['successful_clusters']+=1;assert res['counts']==[[y[i][j] for j in introns] for i in use] and res['samples']==[selected[i] for i in use] and res['introns']==[source['introns'][columns[j]] for j in introns] and res['sample_mask']==[t>0 for t in totals] and res['intron_mask']==[j in introns for j in range(k)]
                conf=source['confounders'];varied=[] if conf is None else [j for j in range(len(conf[0])) if len({conf[i][j] for i in use})>1];null=[[1]+[conf[i][j] for j in varied] for i in use];full=[[1,codes[i]]+[conf[i][j] for j in varied] for i in use];assert res['full']==full and res['null']==null
                fit=res['fit']
                for label,design in [('null',null),('full',full)]:
                    retained=fit['fit_'+label];assert retained['return_code']==0;value=independent_posterior(dict(parameters=retained['par'],priors=dict(shape=1.0001,rate=1e-4,outlier_a=1.01,outlier_b=100),robust=False,counts=res['counts'],design=design));close(value,retained['value'],2e-8);close(ledger['native_'+label+'_posterior'],retained['value'],2e-8);checks['retained_model_objectives']+=1
                difference=fit['fit_full']['value']-fit['fit_null']['value'];df=(len(full[0])-len(null[0]))*(len(introns)-1);assert df in [1,2] and df==fit['df']==raw_table[cluster]['df']==ledger['df'];p=1.0 if difference<=0 else math.erfc(math.sqrt(difference)) if df==1 else math.exp(-difference)
                for row,key in [(fit,'loglr'),(raw_table[cluster],'loglr'),(ledger,'log_ratio')]:close(row[key],difference)
                for value in [fit['lrtp'],raw_table[cluster]['p'],ledger['p']]:close(value,p)
                for name,expected in zip(res['introns'],effects(fit['fit_full']['par'])):
                    all_effects.append(name)
                    for key,value in expected.items():close(value,raw_effects[name][key])
                    checks['effect_introns']+=1
            assert all_effects==list(raw_effects);successful=sorted([r for r in raw_table.values() if r['status']=='Success'],key=lambda r:r['p'],reverse=True);bound=1.0
            for rank,row in zip(range(len(successful),0,-1),successful):bound=min(bound,len(successful)/rank*row['p']);close(bound,row['p.adjust'])
            # R data-frame row names retain raw IDs when add_chr changes the
            # reported intron column. They are metadata, not extra TSV columns.
            for raw_key,reported_key,id_key in [('raw_effects','reported_effects','intron'),('raw_cluster_table','reported_cluster_table','cluster')]:
                before=[r[id_key] for r in source[raw_key]];after=before if not before or 'chr' in before[0] else ['chr'+v for v in before];assert [r[id_key] for r in source[reported_key]]==after
                for raw,row in zip(source[raw_key],source[reported_key]):
                    if '_row' in row:assert row['_row']==raw[id_key]
            for filename,key in [('original_cluster_significance.txt','reported_cluster_table'),('original_effect_sizes.txt','reported_effects')]:table(ds/filename,source[key]);checks['original_table_cells']+=sum(len(set(r)-{'_row'}) for r in source[key])
    assert (checks['retained_model_objectives'],checks['successful_clusters'],checks['filtered_cluster_dispositions'],checks['effect_introns'])==(16,8,14,18)
    return checks


def main():
    from setup_deseq2_reference import ROOT,pin,save
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--analysis',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir();state=dict(status='FAIL2_RETAINED_LEAFCUTTER_WORKFLOW_OUTPUT_AUDIT_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);sources={str(p.relative_to(ROOT).as_posix()):pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/audit_leafcutter_pca.py',ROOT/'tools/literature/validate_leafcutter_phenotypes.py',ROOT/'tools/literature/validate_leafcutter_models.py',ROOT/'tools/literature/signac_scan_evidence.py']};(out/'auditor-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        checks=audit(a.analysis);assert all(pin(ROOT/name)==want for name,want in sources.items());state.update(status='PASS_COMPLETE_RETAINED_LEAFCUTTER_WORKFLOW_OUTPUT_AND_EQUATIONS_ONLY',checks=checks,source_pins=sources,analysis_manifest=pin(a.analysis/'manifest.json'))
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete retained workflow checks')
    raise SystemExit(main())
