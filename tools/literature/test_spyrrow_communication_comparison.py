# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source comparison must reject value/axis/order/state/hash/distance and signed-zero corruption without altering original controls."""
import argparse,copy,json,math
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_spyrrow_communication import compare


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--validation',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/spyrrow-runtime') and not out.exists();out.mkdir(parents=True);truth=ROOT/'build/literature-review/spyrrow-runtime/communication-original-1/original/original-results.json';result=args.validation.resolve()/'windows21/native/results.json';before={str(p.relative_to(ROOT)):pin(p) for p in [truth,result]};expected=json.loads(truth.read_bytes());actual=json.loads(result.read_bytes());checks=[]
    def admit(a):
        assert len(a['cases'])==len(expected['cases'])==21
        counters=dict(numeric=0,exact=0)
        for left,right in zip(a['cases'],expected['cases']):
            for key in ['id','index','columns','values','pairs']:compare(left[key],right[key],right['id']+'/'+key,counters)
        for key in ['hashes','sets','distances']:compare(a[key],expected[key],key,counters)
    admit(actual)
    for name in ['directional-value','pair-axis','aggregate-axis-and-values','nonfinite-to-zero','hash','set-order','distance-ulp','false-empty-family']:
        value=copy.deepcopy(actual)
        if name=='directional-value':value['cases'][0]['values'][0][0]+=1
        elif name=='pair-axis':value['cases'][0]['index'].reverse();value['cases'][0]['values'].reverse()
        elif name=='aggregate-axis-and-values':
            c=value['cases'][0];i,j=len(c['columns'])-1,len(c['columns'])-2;c['columns'][i],c['columns'][j]=c['columns'][j],c['columns'][i]
            for row in c['values']:row[i],row[j]=row[j],row[i]
        elif name=='nonfinite-to-zero':next(c for c in value['cases'] if c['id']=='finite-product-overflow')['values'][0][0]=0.
        elif name=='hash':value['hashes'][1]+=1
        elif name=='set-order':value['sets'][-1][0],value['sets'][-1][1]=value['sets'][-1][1],value['sets'][-1][0]
        elif name=='distance-ulp':value['distances'][0]=math.nextafter(value['distances'][0],math.inf)
        else:next(c for c in value['cases'] if c['id']=='no-surviving-interactions')['values']=[]
        failed=False
        try:admit(value)
        except AssertionError:failed=True
        assert failed,name+' corruption admitted';checks.append(name)
    failed=False
    try:compare(0.,-0.,'signed-zero',dict(numeric=0,exact=0))
    except AssertionError:failed=True
    assert failed;checks.append('signed-zero');assert all(pin(ROOT/n)==want for n,want in before.items());save(out/'COMPLETED.json',dict(status='PASS_NINE_SPYRROW_COMPARISON_CORRUPTION_CONTROLS_ONLY',checks=checks,original_files_unchanged=before,implementation_credit=False,whole_goal_complete=False));print('Rejected all9 comparison corruptions; complete original controls unchanged')


if __name__=='__main__':main()
