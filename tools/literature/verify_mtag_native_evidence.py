# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete saved MTAG development evidence; optional Java replay needs no original Python2 runtime."""
import argparse
import csv
import io
import json
import math
import os
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import unpack, byte_pin, safe
from setup_mtag_reference import BASE, COMMIT, linux
from build_mtag_native import DEPENDENCY, DEPENDENCY_SHA

DEST = ROOT / 'docs/literature/mtag-native-development'
SOURCE_FILES = [
    'src/main/java/org/jlinalg/genetics/MtagEstimator.java',
    'src/test/java/org/jlinalg/genetics/MtagEstimatorTest.java',
    'src/test/java/org/jlinalg/genetics/MtagReferenceProbe.java',
    'docs/literature/MTAG_DEVELOPMENT.md',
    *['tools/literature/' + name + '.py' for name in [
        'intake_mtag_reference','setup_mtag_reference','build_mtag_native','validate_mtag_native',
        'probe_mtag_workflow','capture_mtag_parser_reference','validate_mtag_workflow_output',
        'curate_mtag_reviews','seal_mtag_native_evidence','verify_mtag_native_evidence']],
]


def j(files, name):
    return json.loads(files[name])


def table(raw):
    return list(csv.DictReader(io.StringIO(raw.decode('utf8')), delimiter='\t'))


def numeric_rows(raw, expected, shape):
    rows = table(raw)
    assert len(rows) == sum(r*c for r,c in shape.values())
    seen = set()
    count = 0
    for row in rows:
        kind,i,k = row['kind'],int(row['row']),int(row['column'])
        assert kind in shape and 0 <= i < shape[kind][0] and 0 <= k < shape[kind][1]
        assert (kind,i,k) not in seen
        seen.add((kind,i,k))
        a,b = float.fromhex(row['value_hex']),expected(kind,i,k)
        assert math.isfinite(a)
        if b is None:
            continue
        assert math.isfinite(b) and abs(a-b)/max(1,abs(b)) <= 5e-12,(kind,i,k,a,b)
        if kind=='p':
            assert (a==0 if b==0 else abs(a-b)/b <= 5e-10),(kind,i,k,a,b)
        count += 1
    return count


def inspect():
    proof=json.loads((DEST/'validation.json').read_bytes())
    assert proof['status']=='PASS_NATIVE_MTAG_COMPONENT_AND_ORIGINAL_SYNTHETIC_CONTROLS_ONLY'
    assert proof['source_commit']==COMMIT and not proof['new_catalog_credit'] and not proof['project_adapter_validated']
    assert proof['mtag_remains_missing'] and not proof['whole_goal_complete']
    assert set(proof['source_pins'])==set(SOURCE_FILES)
    for name,wanted in proof['source_pins'].items():
        safe(name);assert pin(ROOT/name)==wanted,name
    for name,wanted in proof['artifacts'].items():
        safe(name);assert pin(DEST/name)==wanted,name
    assert pin(DEST/'controls.zip')==proof['archive']
    files=unpack(DEST/'controls.zip',proof['evidence'])
    for name,wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==wanted,name
    old_api=files['native-build21-1/source-snapshot/src/main/java/org/jlinalg/genetics/MtagEstimator.java']
    old_estimate=b'65536L + 128L * m * t + 256L * t * t'
    corrected_estimate=b'65536L + 512L * m + 128L * m * t + 256L * t * t'
    assert old_api.count(old_estimate)==1
    assert old_api.replace(old_estimate,corrected_estimate)==files['current-source/src/main/java/org/jlinalg/genetics/MtagEstimator.java']
    prior_capacity=json.loads((DEST/'prior-capacity-validation.json').read_bytes())
    assert pin(DEST/'prior-capacity-controls.zip')==prior_capacity['archive']
    prior_files=unpack(DEST/'prior-capacity-controls.zip',prior_capacity['evidence'])
    assert prior_files['current-source/src/main/java/org/jlinalg/genetics/MtagEstimator.java']==old_api
    native=j(files,'native-validation-3/COMPLETED.json')
    full=j(files,'full-output-audit-3/COMPLETED.json')
    assert native['status']=='PASS_NATIVE_MTAG_ESTIMATOR_AND_GMM_COMPONENT_ONLY' and len(native['cases'])==26
    assert full['status']=='PASS_COMPLETE_SYNTHETIC_MTAG_ESTIMATOR_OUTPUT_AUDIT_ONLY' and full['input_variants']==20000
    cases=j(files,'native-validation-3/inputs/manifest.json')
    assert cases==native['cases'] and sum(c['native_expected'] for c in cases)==22
    original=j(files,'native-validation-3/original-reference.json')
    assert set(original)=={c['case'] for c in cases}
    totals={}
    for platform in ['windows21','windows25','linux21']:
        count=0
        for case in cases:
            assert byte_pin(files['native-validation-3/inputs/'+case['input']])==case['pin']
            prefix=platform+'-'+case['case']
            process=j(files,'native-validation-3/'+prefix+'.process.json')
            output='native-validation-3/'+platform+'/'+case['case']+'.tsv'
            if not case['native_expected']:
                assert process['exit_code']==2 and output not in files
                assert b'FAIL2_NO_NATIVE_MTAG_RESULT' in files['native-validation-3/'+prefix+'.stderr']
                continue
            assert process['exit_code']==0 and original[case['case']]['status']=='ORIGINAL_RETURNED'
            assert files[output]==files['native-validation-2/'+platform+'/'+case['case']+'.tsv']
            m,t=case['variants'],case['traits']
            shape={kind:(m,t) for kind in ['beta','se','factor','z','p']};shape['gmm']=(t,t)
            expected=lambda kind,i,k: original[case['case']][kind][i][k]
            count+=numeric_rows(files[output],expected,shape)
        assert count==85858
        assert j(files,'native-validation-3/'+platform+'-junit.process.json')['exit_code']==0
        stdout=files['native-validation-3/'+platform+'-junit.stdout'].decode('utf8')
        assert '8 tests successful' in stdout and '0 tests failed' in stdout and '0 tests skipped' in stdout
        totals[platform]=count
    parser=j(files,'original-parser-control-1/original-parsed-hex.json')
    changes=[]
    results=[]
    for t in range(3):
        raw=table(files['original-workflow-probe-1/inputs/trait%d.tsv'%(t+1)])
        result=table(files['original-workflow-probe-1/original/mtag_trait_%d.txt'%(t+1)])
        assert len(raw)==len(result)==20000
        results.append(result)
        for i,(r,s) in enumerate(zip(raw,result,strict=True)):
            assert r['snpid']==s['SNP']==parser['variants'][i]
            for before,after in [('chr','CHR'),('bpos','BP'),('a1','A1'),('a2','A2')]:assert r[before]==s[after]
            for before,after in [('z','Z'),('n','N'),('freq','FRQ')]:
                a,b=float(r[before]),float.fromhex(parser[before][i][t])
                assert math.isfinite(b) and b==float(s[after])
                if a!=b:
                    changes.append(dict(trait=t+1,input_row=i+1,snp=r['snpid'],field=before,raw_decimal=r[before],
                        java_python3_value_hex=a.hex(),original_pandas_value_hex=b.hex(),absolute_difference=abs(a-b),ulp_distance=abs(a-b)/math.ulp(a)))
    ledger=j(files,'full-output-audit-3/original-parser-differences.json')
    assert ledger['changes']==changes and ledger['changed_cells']==len(changes)==31497
    assert ledger['compared_numeric_inputs']==180000 and ledger['maximum_ulp_distance']==max(r['ulp_distance'] for r in changes)==2
    for platform in totals:
        count=0;gmm_parts=[]
        for batch in range(4):
            raw=files['full-output-audit-3/'+platform+'/batch%d.tsv'%batch]
            assert raw==files['full-output-audit-2/'+platform+'/batch%d.tsv'%batch]
            def expected(kind,i,t):
                if kind in ['factor','gmm']:return None
                r=results[t][batch*5000+i]
                value=float(r[{'beta':'mtag_beta','se':'mtag_se','z':'mtag_z','p':'mtag_pval'}[kind]])
                if kind in ['beta','se']:
                    freq=float(r['FRQ']);value*=math.sqrt(2*freq*(1-freq))
                return value
            shape={kind:(5000,3) for kind in ['beta','se','factor','z','p']};shape['gmm']=(3,3)
            count+=numeric_rows(raw,expected,shape)
            gm=[[0.]*3 for _ in range(3)]
            for row in table(raw):
                if row['kind']=='gmm':gm[int(row['row'])][int(row['column'])]=float.fromhex(row['value_hex'])
            gmm_parts.append(gm)
            assert j(files,'full-output-audit-3/'+platform+'-batch'+str(batch)+'.process.json')['exit_code']==0
        assert count==240000
        omega=[[float(v) for v in line.split()] for line in files['original-workflow-probe-1/original/mtag_omega_hat.txt'].decode().splitlines()]
        for i in range(3):
            for k in range(3):
                actual=sum(gm[i][k] for gm in gmm_parts)/4
                assert abs(actual-omega[i][k])<=1e-10*max(abs(omega[i][k]),1e-300)
        totals[platform]+=count
    assert sum(totals.values())==977574
    roles=json.loads((DEST/'paper-use-reviews.json').read_bytes())
    assert roles['status']=='FIVE_SOURCE_BOUND_MTAG_ROLE_DRAFTS_NOT_APPLIED' and not roles['applied_to_catalog'] and len(roles['reviews'])==5
    assert j(files,'original-workflow-probe-1/original.process.json')['exit_code']==0
    for group in ['reference-setup-1','reference-setup-2','native-validation-1','full-output-audit-1']:
        assert not j(files,group+'/COMPLETED.json')['status'].startswith('PASS_'),group
    for name,wanted in proof['artifacts'].items():
        if name.endswith('.jar'):
            compiler='21' if 'javac21' in name else '25'
            kind='consumer' if 'consumer' in name else 'api'
            assert byte_pin(files['native-build'+compiler+'-2/mtag-'+kind+'.jar'])==wanted
    return proof,files,dict(status=proof['status'],source_matrix_controls=26,native_finite_cases=22,native_refusals=4,
        compared_numeric_cells=977574,junit_passes=24,source_parser_difference_cells=31497,source_parser_max_ulp=2,
        new_catalog_credit=False,mtag_remains_missing=True,whole_goal_complete=False)


def replay(platform,out,proof,files):
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_SAVED_JAVA_REPLAY',platform=platform,new_catalog_credit=False,original_python_or_fit_executed=False)
    try:
        if not DEPENDENCY.is_file() or pin(DEPENDENCY)['sha256']!=DEPENDENCY_SHA:
            state['status']='SKIP3_MTAG_REPLAY_DEPENDENCY_UNAVAILABLE'
            raise RuntimeError('Pinned JDistlib0.10.2 is unavailable; skip dependent replay and supply the permitted exact dependency')
        paths=[DEST/('mtag-api-javac25.jar' if platform=='windows25' else 'mtag-api-javac21.jar'),
               DEST/('mtag-consumer-javac25.jar' if platform=='windows25' else 'mtag-consumer-javac21.jar'),DEPENDENCY]
        prefix=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java'] if platform=='linux21' else [str(Path('C:/Program Files')/('Common Files/Oracle/Java/javapath' if platform=='windows25' else 'Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')/'java.exe')]
        cp=':'.join(map(linux,paths)) if platform=='linux21' else ';'.join(map(str,paths))
        preflight=subprocess.run(prefix+['-version'],stdin=subprocess.DEVNULL,capture_output=True,timeout=60)
        (out/'java-version.stdout').write_bytes(preflight.stdout);(out/'java-version.stderr').write_bytes(preflight.stderr)
        if preflight.returncode:
            state['status']='SKIP3_MTAG_REPLAY_JAVA_UNAVAILABLE';raise RuntimeError('Selected Java runtime is unavailable; skip replay and provide it where permitted')
        tasks=[]
        for c in j(files,'native-validation-3/inputs/manifest.json'):
            tasks.append((c['case'],'native-validation-3/inputs/'+c['input'],c['native_expected'],'native-validation-3/'+platform+'/'+c['case']+'.tsv'))
        for i in range(4):tasks.append(('full-batch'+str(i),'full-output-audit-3/inputs/batch%d.tsv'%i,True,'full-output-audit-3/'+platform+'/batch%d.tsv'%i))
        completed=[]
        for name,source,admitted,reference in tasks:
            ip,op=out/(name+'.input.tsv'),out/(name+'.output.tsv');ip.write_bytes(files[source])
            command=prefix+['-cp',cp,'org.jlinalg.genetics.MtagReferenceProbe',linux(ip) if platform=='linux21' else str(ip),linux(op) if platform=='linux21' else str(op)]
            result=subprocess.run(command,cwd=ROOT,env=dict(os.environ),stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
            (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=result.returncode))
            assert result.returncode==(0 if admitted else 2),(name,result.stderr[-1500:])
            if admitted:
                actual,old=table(op.read_bytes()),table(files[reference])
                assert len(actual)==len(old)
                for a,b in zip(actual,old,strict=True):
                    assert {k:v for k,v in a.items() if k!='value_hex'}=={k:v for k,v in b.items() if k!='value_hex'}
                    x,y=float.fromhex(a['value_hex']),float.fromhex(b['value_hex'])
                    assert math.isfinite(x) and abs(x-y)/max(1,abs(y))<=5e-12
                    if a['kind']=='p':assert x==0 if y==0 else abs(x-y)/y<=5e-10
            else:assert not op.exists() and b'FAIL2_NO_NATIVE_MTAG_RESULT' in result.stderr
            completed.append(dict(case=name,admitted=admitted))
        state.update(status='PASS_MTAG_SAVED_JAVA_REPLAY_ONLY',cases=completed,original_matrix_cases=26,full_synthetic_batches=4,
                     source_evidence_archive=proof['archive'],fit_executed=False)
        print(state['status']+' '+platform)
    except Exception as error:
        if isinstance(error,FileNotFoundError):state['status']='SKIP3_MTAG_REPLAY_JAVA_UNAVAILABLE'
        state['error']=repr(error)
        print(state['status']+': '+repr(error),file=sys.stderr)
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Saved synthetic Java replay only; no original-runtime fit, full adapter or biological admission.')
        +'\n\nREQUIRED ACTION: preserve all attempts, skip dependent replay if exact prerequisites are unavailable or forbidden, and retry NEW paths after resolving the named cause. Do not repair source or result values.\n',encoding='utf8',newline='\n')
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--replay',choices=['windows21','windows25','linux21']);p.add_argument('--out',type=Path);a=p.parse_args()
    try:
        proof,files,summary=inspect()
        if a.replay:
            assert a.out is not None,'Supply a NEW --out for replay'
            return replay(a.replay,a.out.resolve(),proof,files)
        assert a.out is None
        print(json.dumps(summary));return 0
    except Exception as error:
        print('FAIL2_MTAG_EVIDENCE: '+repr(error)+'\nRequired action: preserve evidence and resolve the named source/archive/contract mismatch before retrying. No result, full workflow or catalog credit is admitted.',file=sys.stderr)
        return 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete evidence/scientific checks')
    raise SystemExit(main())
