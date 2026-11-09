# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete selected-rank populations for both explicit original profiles."""
import argparse,json,random
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    rng=random.Random(923061);cases=[];lines=[]
    for profile in ['CTXCORE_0_2_0','RCISTARGET_1_29_0_ICISTARGET']:
        for number in range(32):
            total=[17,31,64,123,1001,10000,1000000,40][number%8];m=[3,7,17,64][number%4];n=min(total,5+number%13)
            bound=min(total-1,max(8,int(total*.15)));count=max(1,int(total*.05));fraction=count/total
            if number%8==7:fraction=.0625 # total40 =>2.5 rounds to2
            if number%8==0:fraction=1/total
            auc=fraction if profile=='CTXCORE_0_2_0' else fraction*total
            weights=[1.0]*n
            if profile=='CTXCORE_0_2_0' and number%3:weights=[rng.uniform(.1,3) if i%5 else 0 for i in range(n)]
            if profile=='CTXCORE_0_2_0' and number==20:weights=[-1.0,3.0,*[1.0]*(n-2)]
            ranks=[]
            for row in range(m):
                values=[rng.randrange(total) for i in range(n)]
                if row==0:values=[i%max(1,int(total*.05)) for i in range(n)]
                if number%6==1:values[0]=0;values[1]=1;values[2]=count;values[3]=max(0,count-1);values[4]=bound
                if number%11==0:values=[total-1]*n # all-zero AUC/flat background
                if profile=='RCISTARGET_1_29_0_ICISTARGET' and number%7==2:values[-1]='Inf'
                ranks.append(values)
            id=('ctx-' if profile=='CTXCORE_0_2_0' else 'r-')+str(number)
            case=dict(id=id,profile=profile,total_features=total,motifs=m,selected_features=n,auc_parameter=auc,recovery_bound=bound,weights=weights,rankings=ranks)
            cases.append(case);lines.append(' '.join(map(str,[id,profile,total,m,n,format(auc,'.17g'),bound])));lines.append(' '.join(format(w,'.17g') for w in weights))
            lines.extend(' '.join(str(v) for v in row) for row in ranks)
    (out/'controls.txt').write_text('\n'.join(lines)+'\n',encoding='utf8');save(out/'cases.json',cases)
    save(out/'COMPLETED.json',dict(status='PASS_64_COMPLETE_CISTARGET_RANK_CONTROL_FAMILIES_ONLY',cases=64,
        files={p.name:pin(p) for p in out.iterdir() if p.is_file()},source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},
        implementation_credit=False,whole_goal_complete=False));print('Created64 complete rank/recovery families; no implementation credit')


if __name__=='__main__':main()
