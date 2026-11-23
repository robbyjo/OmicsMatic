"""Compare a known synthetic original-R witness with its native diagnostic result.

This is a reference comparator, not a verifier for arbitrary scientific captures.
It does not import the native implementation or the production capture verifier.
"""
from pathlib import Path
import argparse,json,math

def require(condition,message):
    if not condition:raise ValueError(message)

def field(record,key,default=None):
    require(record['kind']=='list','Original typed list required')
    return record['values'][record['names'].index(key)] if key in record['names'] else default

def scalar(record,default=None):return record['values'][0] if record is not None else default
def numeric(value):
    if isinstance(value,dict):
        token=value['special']
        if token=='Inf':return math.inf
        if token=='-Inf':return -math.inf
        require(token=='NaN' or token.startswith('NA_'),'Unknown original special value')
        return math.nan
    return float(value)

def cell(value):
    if value['kind']=='FINITE':
        result=float(value['value']);require(math.isfinite(result),'Nonfinite payload labelled finite');return result
    require(value['kind']=='NONFINITE','Unknown native cell kind')
    return {'Inf':math.inf,'-Inf':-math.inf,'NaN':math.nan}[value['literal']]

def close(expected,actual,label):
    if math.isnan(expected):require(math.isnan(actual),label+' must preserve undefined inference');return
    if math.isinf(expected):require(expected==actual,label+' changed infinity');return
    require(math.isfinite(actual),label+' must remain finite')
    if expected==0 or actual==0:require(expected==actual,label+' changed numeric zero');return
    require(abs(expected-actual)<=3e-11*abs(expected)+32*max(math.ulp(expected),math.ulp(actual)),label+' differs from original R')

def columns(frame):
    require(frame['kind']=='data.frame','Original data frame required')
    return {x['name']:x['data']['values'] for x in frame['columns']}

def probability(expected,actual,n,b,outlier=False):
    raw=scalar(expected) if isinstance(expected,dict) and 'values' in expected else expected
    require(actual['distributions']==b,'Distribution count changed')
    if isinstance(raw,dict):
        require(actual['kind']=='UNREPORTABLE' and not actual['inference_admitted'],'Undefined source probability was admitted');return
    bound=isinstance(raw,str) and raw.startswith('<')
    if bound:
        require(actual['kind']=='STRICT_UPPER_BOUND' and actual['exceedances']==0,'Strict source probability bound was changed')
        close(float(raw[1:]),cell(actual['reported_bound']),'Source displayed bound')
        close(n/b if outlier else 1/b,cell(actual['resolution']),'Source bound resolution')
        require(cell(actual['internal_source_value'])==0,'Bound must retain the underlying zero count')
        require(actual['inference_admitted'] is True,'A finite source bound was incorrectly invalidated');return
    expected=float(raw);close(expected,cell(actual['internal_source_value']),'Source probability')
    count=actual['exceedances'];require(type(count) is int and count>=0,'Invalid native exceedance count')
    evaluated=min(1,count/b*n) if outlier else count/b
    close(evaluated,cell(actual['internal_source_value']),'Count/value consistency')
    valid=math.isfinite(expected) and 0<=expected<=1
    require(actual['inference_admitted'] is valid and actual['kind']==('EMPIRICAL' if valid else 'UNREPORTABLE'),'Probability admission changed')

def compare(record,native):
    require(scalar(field(record,'returned')) is True,'Original call failed: no finite reference claim')
    name=scalar(field(record,'case'));arguments=field(record,'arguments');source=field(record,'input');input_columns=columns(source)
    result=field(record,'result');tests=field(result,'MR-PRESSO results');main=columns(field(result,'Main MR results'))
    exposures=field(arguments,'BetaExposure')['values'];errors=field(arguments,'SdExposure')['values'];k=len(exposures)
    selected=[scalar(field(arguments,'BetaOutcome')),*exposures,scalar(field(arguments,'SdOutcome')),*errors]
    ordinals=[i for i in range(len(source['row_names'])) if all(not math.isnan(numeric(input_columns[key][i])) for key in selected)]
    labels=[source['row_names'][i] for i in ordinals];n=len(labels);b=int(scalar(field(arguments,'NbDistribution'),1000))
    require(native['retained_input_ordinals']==ordinals and native['retained_row_ids']==labels,'Complete-case mapping or literal IDs changed')
    require(native['exposure_names']==exposures,'Exposure axes changed')
    require(native['rng_after']==field(record,'rng_after')['values'],'Original RNG consumption changed')
    if field(arguments,'seed') is None:require(native['rng_before']==field(record,'rng_before')['values'],'Continued state was reseeded')
    require(native['original_R_executed'] is False and native['whole_tool_qualified'] is False and native['whole_paper_reproduced'] is False,'Native scope changed')
    global_test=field(tests,'Global Test');perfect=name=='essentially-perfect-raw';zero=name=='zero-corrected-inference';discrepancies=[]
    if perfect:
        discrepancies.append(dict(field='global_rss',original=scalar(field(global_test,'RSSobs')),native=cell(native['global_rss']),claim='No relative equality claim for roundoff-scale perfect-fit RSS'))
    else:close(numeric(scalar(field(global_test,'RSSobs'))),cell(native['global_rss']),name+' global RSS')
    probability(field(global_test,'Pvalue'),native['global_p_value'],n,b)
    require(main['Exposure']==exposures+exposures and main['MR Analysis']==['Raw']*k+['Outlier-corrected']*k,'Original coefficient-frame labels changed')
    for offset,key in ((0,'raw_mr'),(k,'corrected_mr')):
        fit=native[key];conditional=all(isinstance(main['Causal Estimate'][offset+j],dict) and main['Causal Estimate'][offset+j]['special'].startswith('NA_') for j in range(k))
        if conditional:
            require(fit['status']=='SOURCE_NOT_ESTIMATED' and fit['inference_admitted'] is False,'Conditional corrected NA was replaced');continue
        require(fit['rank']==k,'Original coefficient rank changed')
        expected_admission=not (perfect or zero and key=='corrected_mr')
        require(fit['inference_admitted'] is expected_admission,'Inference admission contradicts known source witness')
        if perfect:require(fit['essentially_perfect'] is True,'Perfect fit must be explicitly guarded')
        for source_key,native_key in [('Causal Estimate','beta'),('Sd','standard_error'),('T-stat','statistic'),('P-value','p_value')]:
            require(len(fit[native_key])==k,'Coefficient axis length changed')
            for j in range(k):
                expected=numeric(main[source_key][offset+j]);actual=cell(fit[native_key][j]);label=name+' '+key+' '+native_key
                if perfect and native_key!='beta' or zero and key=='raw_mr' and native_key in ('beta','statistic'):
                    discrepancies.append(dict(field=key+'/'+native_key,original=main[source_key][offset+j],native=fit[native_key][j],claim='No relative source-inference equivalence claim in this degenerate witness'))
                else:close(expected,actual,label)
    outlier=field(tests,'Outlier Test');require(native['outlier_test_performed'] is (outlier is not None),'Original outlier branch changed')
    if outlier is None:require(native['outliers']==[],'Unexpected outlier table')
    else:
        table=columns(outlier);require(outlier['row_names']==labels and len(native['outliers'])==n,'Outlier row identities changed')
        for i,row in enumerate(native['outliers']):
            require(row['row_id']==labels[i] and row['retained_ordinal']==i+1,'Outlier label/ordinal changed')
            close(numeric(table['RSSobs'][i]),cell(row['squared_residual']),name+' per-IV residual')
            probability(table['Pvalue'][i],row['p_value'],n,b,True)
    distortion=field(tests,'Distortion Test');require(native['distortion_branch_performed'] is (distortion is not None),'Original distortion branch changed')
    if distortion is None:require(native['distortion'] is None,'Unexpected distortion test')
    else:
        indices=field(distortion,'Outliers Indices');found=native['distortion'];computed=indices['kind']!='character'
        if not computed:
            expected='ALL_INSTRUMENTS_OUTLIERS' if indices['values'][0].startswith('All') else 'NO_SIGNIFICANT_OUTLIERS'
            require(found['status']==expected and found['inference_admitted'] is False,'Conditional distortion status changed')
        else:
            require(found['status']=='CALCULATED' and found['outlier_ordinals']==indices['values'],'Distortion outlier selection changed')
            expected=field(distortion,'Distortion Coefficient')['values'];require(len(found['coefficient_percent'])==len(expected),'Distortion axis changed')
            for j,raw in enumerate(expected):
                if zero:
                    require(not math.isfinite(cell(found['coefficient_percent'][j])),'Undefined zero-denominator distortion was replaced')
                    discrepancies.append(dict(field='distortion/coefficient_percent',original=raw,native=found['coefficient_percent'][j],claim='Undefined distortion retained; cancellation-dependent sign is not claimed equivalent'))
                else:close(numeric(raw),cell(found['coefficient_percent'][j]),name+' distortion coefficient')
            probability(field(distortion,'Pvalue'),found['p_value'],n,b)
            expected_admission=not zero and 0<=numeric(scalar(field(distortion,'Pvalue')))<=1 if not isinstance(scalar(field(distortion,'Pvalue')),str) else not zero
            require(found['inference_admitted'] is expected_admission,'Distortion admission changed')
    unreportable=perfect or zero or name.startswith('balanced-mvmr-distortion-')
    require(native['contains_unreportable_fields'] is unreportable,'Unreportable field population changed')
    require(native['results_admitted'] is (not unreportable or native['allow_unreportable_fields']),'Policy changed overall admission')
    return dict(case=name,status='MATCH_WITH_EXPLICIT_DEGENERATE_LIMITS' if discrepancies else 'MATCH',
        source_package_patched=False,exact_rng_after=True,source_numeric_discrepancies=discrepancies,
        full_workflow_qualified=False,whole_paper_reproduced=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--original',type=Path,required=True);parser.add_argument('--native-result',type=Path,required=True);args=parser.parse_args()
    try:print(json.dumps(compare(json.loads(args.original.read_bytes()),json.loads(args.native_result.read_bytes())),indent=2))
    except (OSError,ValueError,TypeError,KeyError) as error:parser.exit(2,'FAIL2: original/native reference comparison refused: '+str(error)+'\n')

if __name__=='__main__':main()
