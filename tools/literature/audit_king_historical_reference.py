# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check every original historical KING kinship row against independently counted complete BED calls."""
import argparse
import csv
import json
import math
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE


def numeric(raw):
    return float(raw.replace('-nan','nan'))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[]
    state=dict(status='FAIL2_HISTORICAL_KING_COMPLETE_NATIVE_CENSUS',new_catalog_credit=False,whole_goal_complete=False)
    try:
        def census(path,official):
            with path.open(encoding='utf8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
            result={}
            for r in rows:
                ids=[(r['FID1'],r['IID1']),(r['FID2'],r['IID2'])];key=tuple(sorted(ids))
                n=int(r['NSNP' if official else 'N_SNP']);h=int(r['HETHET' if official else 'HET_HET']);z=int(r['IBS0'])
                a=int(r['HET1_HOM2']);b=int(r['HET2_HOM1']);coefficient=float.fromhex(r['COEFFICIENT_HEX' if official else 'NATIVE_BETWEEN_MIN_HET_KINSHIP_HEX'])
                assert key not in result;result[key]=dict(n=n,h=h,z=z,a=a,b=b,k=coefficient)
            return result
        tables={'official':census(BASE/'king-platform-pilot-1/native-official-counts.tsv',True),
                'own':census(BASE/'king-own-controls-1/windows-kinship-0.bundle/native-pair-counts.tsv',False)}
        for profile in ['2.2.7-windows','2.2.7-linux','2.0-linux','2.0-ubuntu']:
            for label in ['official','own']:
                home=BASE/'king-historical-probe-1'/profile/(label+'-kinship');data=tables[label];seen=set();maxdiff=0;zero=0
                for suffix in ['kin','kin0']:
                    rows=[r.split() for r in (home/('original.'+suffix)).read_text().splitlines()]
                    if rows[0][0] in ['FID','FID1']:rows=rows[1:]
                    for r in rows:
                        within=suffix=='kin';ids=[(r[0],r[1]),(r[0],r[2])] if within else [(r[0],r[1]),(r[2],r[3])]
                        key=tuple(sorted(ids));assert key not in seen;seen.add(key);c=data[key]
                        i=3 if within else 4;h=6 if within else 5;z=7 if within else 6;k=8 if within else 7
                        assert int(r[i])==c['n']
                        for column,value,tolerance in [(h,c['h']/c['n'],.0005 if within and profile.startswith('2.0-') else .00005),
                                                       (z,c['z']/c['n'],.00005)]:
                            assert abs(numeric(r[column])-value)<=tolerance+1e-12,(profile,label,key,column,r[column],value)
                        expected=(c['h']-2*c['z'])/(2*c['h']+c['a']+c['b']) if within else (0 if c['h']+min(c['a'],c['b'])==0 else c['k'])
                        if not within and c['h']+min(c['a'],c['b'])==0:zero+=1
                        difference=abs(numeric(r[k])-expected);assert difference<=.00005+1e-12,(profile,label,key,r[k],expected)
                        maxdiff=max(maxdiff,difference)
                assert seen==set(data),(profile,label,'Complete pair population differs')
                records.append(dict(profile=profile,input=label,complete_pairs=len(seen),source_zero_unassessable_pairs=zero,
                                    maximum_coefficient_rounding_difference=maxdiff,complete_count_population_equal=True,
                                    lower_precision_within_HetHet=profile.startswith('2.0-'),
                                    original_table_pins={f.name:pin(f) for f in home.glob('original.kin*')}))
        state.update(status='PASS_HISTORICAL_KING20_227_COMPLETE_NATIVE_COUNTS_AND_KINSHIP',records=records,
                     complete_original_pairs_per_profile=54946,complete_own_pairs_per_profile=120,producer=pin(Path(__file__)))
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],len(records),state.get('error','')[:2000],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/native population checks')
    raise SystemExit(main())
