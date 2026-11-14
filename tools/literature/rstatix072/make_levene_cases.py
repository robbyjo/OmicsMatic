#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate deterministic full populations; no synthetic case replaces author data/settings."""
from pathlib import Path
import argparse,json,random

def cases():
 rows=[]
 def add(name,x,g,expected='pass'):
  for center in ['MEDIAN','MEAN']:
   rows.append(dict(id=name+'-'+center,values=x,groups=g,center=center,missing_policy='OMIT' if any(v=='NaN' for v in x) or None in g else 'FAIL',expected_native=expected))
 random_source=random.Random(20261113)
 for groups in [2,3,4,7]:
  for replicate in range(20):
   x=[];g=[]
   for group in range(groups):
    for j in range(3+random_source.randrange(15)):
     x.append(random_source.randint(-9,9)/4+(1+group/3)*random_source.random());g.append(['α','NA','','10','2','beta','group space'][group])
   add('unequal-'+str(groups)+'-'+str(replicate),x,g)
   if replicate<3:
    add('ties-'+str(groups)+'-'+str(replicate),[round(v) for v in x],g)
 add('singleton',[1,2,4,7,2,5,10],['single','a','a','a','b','b','b'])
 add('missing-rows',[1,'NaN',2,4,7,8,1,4,6,9],['a','a','a','a',None,'a','b','b','b','b'])
 core=[-5,-2,-1,0,2,4,-6,-1,3,8,12,20];g=['a']*6+['b']*6
 add('ordinary',core,g)
 add('shared-offset',[x+1e6 for x in core],g)
 add('large-scale',[x*1e120 for x in core],g)
 add('small-scale',[x*1e-120 for x in core],g)
 add('zero-constant',[0]*8,['a']*4+['b']*4,'refuse')
 add('constant-deviations',[1,1,3,3,5,5,9,9],['a']*4+['b']*4,'refuse')
 add('one-group',[1,2,4,8],['same']*4,'refuse')
 add('all-singletons',[1,2,3],['a','b','c'],'refuse')
 add('infinite',[1,2,'+Inf',4,6,8],['a']*3+['b']*3,'refuse')
 return rows

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False);rows=cases();expected={r['id']:r.pop('expected_native') for r in rows}
 positive=[r for r in rows if expected[r['id']]=='pass']
 for name,value in [('request.json',dict(schema_version=1,cases=rows)),('positive-request.json',dict(schema_version=1,cases=positive)),('EXPECTATIONS.json',expected)]:
  (a.out/name).write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
 print('COMPLETE_LEVENE_CASES',len(rows))
if __name__=='__main__':main()
