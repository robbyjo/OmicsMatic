# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare original SciPy1.2.1 MTAG tails with the native port, including dense underflow-boundary and subnormal controls."""
import argparse
import csv
import json
import math
import os
import random
import re
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE,linux

PROGRAM = r'''from __future__ import print_function
import sys,csv,math
import numpy as np
import scipy
from scipy.stats import norm
assert scipy.__version__=='1.2.1'
with open(sys.argv[1],'rb') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
z=np.array([float.fromhex(row['z_hex']) for row in rows],dtype=np.float64)
p=2*norm.cdf(-np.abs(z))
with open(sys.argv[2],'wb') as stream:
    writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(['row','z_hex','p_hex'])
    for r,v in zip(rows,p):writer.writerow([r['row'],r['z_hex'],float(v).hex()])
print('Original SciPy1.2.1 evaluated %d tails' % len(rows))
'''


def rows(path):
    with path.open(encoding='utf8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build21',type=Path,required=True);p.add_argument('--build25',type=Path,required=True);p.add_argument('--runtime-profile',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_MTAG_SCIPY121_TAIL',new_catalog_credit=False,full_adapter_available=False,nonzero_relative_tolerance=2e-12)
    (out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        source=BASE/'cephes-source-intake-1';intake=json.loads((source/'intake.json').read_bytes())
        for name in ['scipy/special/cephes/ndtr.c','scipy/special/cephes/const.c','scipy/special/cephes/polevl.h','LICENSE.txt']:
            assert pin(source/name)=={k:intake['files'][name][k] for k in ['bytes','sha256']}
        original_c=(source/'scipy/special/cephes/ndtr.c').read_text();java=(ROOT/'src/main/java/org/jlinalg/genetics/MtagScipy121NormalTail.java').read_text()
        # Independently bind every coefficient to the exact official C source.
        for label in ['P','Q','R','S','T','U']:
            c=re.search(r'static double '+label+r'\[\] = \{(.*?)\};',original_c,re.S).group(1)
            c=re.sub(r'/\*.*?\*/','',c,flags=re.S);expected=[float(x.strip()) for x in c.split(',') if x.strip()]
            actual=re.search(r'double\[\] '+label+r'=\{(.*?)\};',java,re.S).group(1)
            assert expected==[float(x.strip()) for x in actual.split(',') if x.strip()],label
        protected={str(ROOT/'build/cli/omicsmatic-0.3.7.jar'):pin(ROOT/'build/cli/omicsmatic-0.3.7.jar'),str(a.runtime_profile.resolve()):pin(a.runtime_profile),str(Path(__file__).resolve()):pin(Path(__file__))}
        values=[0.,-0.,math.ulp(0.),-math.ulp(0.),1e-300,-1e-300,1e300,-1e300,float.fromhex('0x1.fffffffffffffp+1023')]
        values += [x/1000 for x in range(-40000,40001)]
        rng=random.Random(20261110);values += [rng.uniform(-40,40) for _ in range(20000)]
        for center in [1.,math.sqrt(2),8*math.sqrt(2),math.sqrt(2*7.09782712893383996732E2),37.519379347,37.6,38.]:
            left=right=center
            values += [center,-center]
            for i in range(1024):
                left=math.nextafter(left,-math.inf);right=math.nextafter(right,math.inf);values.extend([left,-left,right,-right])
        values += [float('nan'),float('inf'),float('-inf')]
        source_input=out/'tails.tsv'
        with source_input.open('x',encoding='ascii',newline='') as stream:
            w=csv.writer(stream,delimiter='\t',lineterminator='\n');w.writerow(['row','z_hex'])
            for i,x in enumerate(values):w.writerow([i+1,'NaN' if math.isnan(x) else 'Infinity' if x==math.inf else '-Infinity' if x==-math.inf else x.hex()])
        worker=out/'original_tail.py';worker.write_text(PROGRAM,encoding='utf8',newline='\n')
        profile=json.loads(a.runtime_profile.read_bytes());assert profile['version']=='1.0.8'
        prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--']
        def run(label,command):
            r=subprocess.run(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,capture_output=True,timeout=300)
            (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(label,r.stderr[-3000:])
        run('original',prefix+['/usr/bin/env','-i','PATH=/usr/bin:/bin','LC_ALL=C','OPENBLAS_NUM_THREADS=1','OMP_NUM_THREADS=1','PYTHONDONTWRITEBYTECODE=1',profile['python'],'-s','-B',linux(worker),linux(source_input),linux(out/'original.tsv')])
        reference=rows(out/'original.tsv');assert len(reference)==len(values)
        reports=[]
        for platform,build in [('windows21',a.build21),('windows25',a.build25),('linux21',a.build21)]:
            receipt=json.loads((build/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_MTAG_WORKFLOW_DEVELOPMENT_BUILD_ONLY'
            jar=build.resolve()/'mtag-workflow-development.jar';assert pin(jar)==receipt['artifact'];protected[str(jar)]=pin(jar)
            java=prefix+['/usr/bin/java'] if platform=='linux21' else [str(Path('C:/Program Files')/('Common Files/Oracle/Java/javapath' if platform=='windows25' else 'Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')/'java.exe')]
            target=out/(platform+'.tsv');run(platform,java+['-cp',linux(jar) if platform=='linux21' else str(jar),'org.jlinalg.genetics.MtagTailReferenceProbe',linux(source_input) if platform=='linux21' else str(source_input),linux(target) if platform=='linux21' else str(target)])
            native=rows(target);assert len(native)==len(reference);maximum=0.;nonzero=zero=refused=0
            for index,(x,r,n) in enumerate(zip(values,reference,native,strict=True)):
                assert r['row']==n['row']==str(index+1) and r['z_hex']==n['z_hex']
                if not math.isfinite(x):assert n['status']=='NATIVE_FINITE_PROFILE_REFUSAL' and not n['p_hex'];refused+=1;continue
                expected,actual=float.fromhex(r['p_hex']),float.fromhex(n['p_hex']);assert n['status']=='NATIVE_RETURNED' and math.isfinite(actual) and 0<=actual<=1
                if expected==0:assert actual==0,(platform,x,expected,actual);zero+=1
                else:
                    relative=abs(actual-expected)/expected;assert relative<=state['nonzero_relative_tolerance'],(platform,x,expected,actual,relative);maximum=max(maximum,relative);nonzero+=1
            reports.append(dict(platform=platform,finite_cases=nonzero+zero,nonzero_tails=nonzero,original_zero_tails=zero,nonfinite_refusals=refused,max_nonzero_relative_error=maximum))
        for name,wanted in protected.items():assert pin(Path(name))==wanted,name
        state.update(status='PASS_SOURCE_BOUND_SCIPY121_MTAG_TAIL_COMPONENT_ONLY',cases=len(values),platforms=reports,original_zeros_preserved=True,source_coefficients_bound=True,canonical_executable_unchanged=True,
                     protected_files=protected,official_source_intake=pin(source/'intake.json'),values_clipped_or_repaired=False)
        print(json.dumps({k:state[k] for k in ['status','cases','platforms']}))
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');print(state['status']+': '+repr(error))
    save(out/'COMPLETED.json',state)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-coefficient, full-row or original-underflow checks')
    raise SystemExit(main())
