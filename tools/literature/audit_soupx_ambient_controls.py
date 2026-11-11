# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent arithmetic audit of complete saved original ambient profiles, masks and GLM objects."""
import math


def counts(matrix):
    genes=matrix['genes'];cells=matrix['cells'];assert len(genes)==len(set(genes)) and len(cells)==len(set(cells));values={};totals=[0]*len(cells)
    for e in matrix['entries']:
        k=e['gene'],e['cell'];x=float(e['value']);assert 0<=k[0]<len(genes) and 0<=k[1]<len(cells) and k not in values and x>=0 and math.isfinite(x) and x.is_integer();values[k]=int(x);totals[k[1]]+=int(x)
    return values,totals


def close(expected,actual):
    expected=float(expected);actual=float(actual)
    if math.isnan(expected):assert math.isnan(actual)
    elif not math.isfinite(expected) or expected==0:assert expected==actual
    else:assert abs(expected-actual)<=max(64*math.ulp(expected),3e-12*abs(expected)),(expected,actual)


def poisson_survival(observed,mean):
    if observed==0:return 1.0
    if mean==0:return 0.0
    assert 0<mean<600,'This fixture-only independent series requires bounded positive mean'
    if observed<=mean:
        term=math.exp(-mean);terms=[term]
        for k in range(1,observed):term*=mean/k;terms.append(term)
        return max(0,1-math.fsum(terms))
    term=math.exp(-mean+observed*math.log(mean)-math.lgamma(observed+1));terms=[term]
    for k in range(observed+1,10000):
        term*=mean/k;terms.append(term)
        if term<=math.fsum(terms)*1e-16:break
    else:raise AssertionError('Independent tail series did not converge')
    return min(1,math.fsum(terms))


def audit(inputs,controls,diagnosis):
    raw=inputs['raw'];filtered=inputs['filtered'];assert raw['genes']==filtered['genes'];rv,rt=counts(raw);fv,ft=counts(filtered);ng=len(raw['genes']);nc=len(filtered['cells']);gene=raw['genes'].index('CD7');labels=[inputs['clusters'][c] for c in filtered['cells']];profiles={};profile_rows=0;mask_cells=0;fits=0;corrections=0
    for row in controls['cases']:
        if not row['id'].startswith('profile-'):continue
        lower,upper=row['settings']['soup_range'];selected={i for i,n in enumerate(rt) if lower<n<upper};total=sum(rt[i] for i in selected);observed=[0]*ng
        for (g,c),x in rv.items():
            if c in selected:observed[g]+=x
        expected=[v/total if total else math.nan for v in observed];assert row['returned'] and row['gene_ids']==raw['genes'] and len(row['soup_profile'])==ng
        for original,n,p in zip(row['soup_profile'],observed,expected):close(n,original['counts']);close(p,original['est']);profile_rows+=1
        profiles[lower,upper]=expected
    profile=profiles[0,100]
    for row in controls['cases']:
        if row['id'].startswith('manual-') and row['returned']:
            if row['id']=='manual-supplied-mask':accepted=[c!=labels[0] for c in labels]
            else:
                p=[poisson_survival(fv.get((gene,c),0),ft[c]*profile[gene]) for c in range(nc)];order=sorted(range(nc),key=p.__getitem__);q=[0]*nc;minimum=1
                for at in reversed(range(nc)):minimum=min(minimum,nc/(at+1)*p[order[at]]);q[order[at]]=minimum
                grouping=filtered['cells'] if row['id'].endswith('cell-only') else labels;group_min={v:min(q[i] for i,c in enumerate(grouping) if c==v) for v in grouping};accepted=[group_min[c]>=.05 for c in grouping]
            assert row['nonexpressing_cells']==filtered['cells'] and row['nonexpressing_groups']==['CD7'] and row['nonexpressing']==[[v] for v in accepted];mask_cells+=nc;fit=diagnosis['cases'][row['id']];n=sum(fv.get((gene,c),0) for c,ok in enumerate(accepted) if ok);exposure=math.fsum(ft[c]*profile[gene] for c,ok in enumerate(accepted) if ok);close(n,fit['observed']);close(exposure,fit['exposure']);close(n/exposure,fit['analytical_rho']);assert fit['original_converged'] and fit['original_object_not_modified'];original=float(fit['original_rho']);assert abs(original-n/exposure)>1e-10*original
            for record in row['channel_metadata']:close(original,record['rho'])
            y=list(map(float,fit['complete_y']));offset=list(map(float,fit['complete_log_exposure']));assert sum(y)==n and len(y)==len(offset)==sum(accepted);close(exposure,math.fsum(math.exp(v) for v in offset));assert abs(original-float(fit['analytical_rho'])-float(fit['original_minus_analytical']))<=128*math.ulp(original);fits+=1
        if row['id'].startswith('fixed-') and 'corrected' in row:
            matrix=row['corrected'];assert matrix['genes']==filtered['genes'] and matrix['cells']==filtered['cells'];seen=set()
            for e in matrix['entries']:
                k=e['gene'],e['cell'];assert k not in seen;seen.add(k);value=float(e['value']);assert math.isfinite(value) and 0<=value<=fv.get(k,0)
                if row['settings']['round_to_integer']:assert value.is_integer()
            corrections+=1
    assert (profile_rows,mask_cells,fits,corrections)==(904,186,3,12)
    by_id={r['id']:r for r in controls['cases']};assert not by_id['manual-no-accepted-cells']['returned'];assert by_id['constructor-gene-order-mismatch']['returned']==(controls['version']=='1.3.0');assert by_id['fixed-rho-0.9']['returned']==(controls['version']=='1.3.0');assert by_id['profile-no-selected-droplets']['returned']
    first=by_id['first-cluster-FALSE-source-branch']['matrix'];cell=by_id['fixed-cell-only-subtraction-FALSE']['corrected'];assert first==cell
    return dict(profile_gene_rows=profile_rows,manual_mask_decisions=mask_cells,analytical_original_Poisson_comparisons=fits,original_corrected_matrices_checked=corrections,reference_cases=len(controls['cases']))
