# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create exact finite prepared-cohort mathematical controls, not paper results."""
import argparse,json,math,random,struct
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    rng=random.Random(2193701);cases=[]
    def add(id,y,w,x,kind):cases.append(dict(id=id,effects=y,weights=w,ancestry=x,kind=kind))
    add('analytic-intercept',[1,2,3,4],[1]*4,[[] for _ in range(4)],'analytic')
    add('zero-statistics',[0]*6,[1,2,3,4,5,6],[[i-2.5] for i in range(6)],'zero-undefined-tests')
    for index in range(48):
        axes=index%4+1;n=axes+3+index%13
        x=[[rng.gauss(0,1) for _ in range(axes)] for _ in range(n)]
        coefficients=[rng.uniform(-1,1) for _ in range(axes+1)]
        w=[math.exp(rng.uniform(-2,2)) for _ in range(n)]
        y=[coefficients[0]+sum(a*b for a,b in zip(row,coefficients[1:]))+rng.gauss(0,.23) for row in x]
        add('well-conditioned-'+str(index),y,w,x,'prepared-full-precision-regression')
    origin=cases[10]
    for name,scale in [('tiny-weights',1e-40),('large-weights',1e40)]:
        add(name,list(origin['effects']),[v*scale for v in origin['weights']],[list(r) for r in origin['ancestry']],'uniform-weight-scale')
    add('large-association',[100+i*.01 for i in range(9)],[100]*9,[[i-4,(i-4)**2] for i in range(9)],'source-tail-approximation-probe')
    origin=cases[7];f32=lambda x:struct.unpack('f',struct.pack('f',x))[0]
    add('explicit-float32-prepared-effects-and-reciprocals',[f32(y) for y in origin['effects']],
        [float(f32(1/f32(1/math.sqrt(w))))**2 for w in origin['weights']],[list(row) for row in origin['ancestry']],
        'explicit-preparation-not-complete-original-file-parser')
    text=[]
    for case in cases:
        text.append(f"{case['id']} {len(case['effects'])} {len(case['ancestry'][0])}")
        for y,w,x in zip(case['effects'],case['weights'],case['ancestry'],strict=True):text.append(' '.join(format(value,'.17g') for value in [y,w,*x]))
    (out/'controls.txt').write_text('\n'.join(text)+'\n',encoding='utf8',newline='\n');save(out/'controls.json',cases)
    save(out/'COMPLETED.json',dict(status='PASS_PREPARED_MRMEGA_MATHEMATICAL_CONTROLS_ONLY',cases=len(cases),files={n:pin(out/n) for n in ['controls.txt','controls.json']},
        source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},analysis_admitted=False,implementation_credit=False,whole_goal_complete=False))
    print('Prepared',len(cases),'complete regression controls; no original/Java execution or paper result admitted')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete control checks')
    main()
