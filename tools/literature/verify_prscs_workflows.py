#!/usr/bin/env python3
"""Verify a packaged PRS-CS command against frozen actual-original synthetic outputs."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import traceback
import install_prscs as runtime

ROOT=Path(__file__).resolve().parents[2]
FIXTURE=ROOT/'src/test/resources/org/jlinalg/genetics/prscs-reference'
CASES=[
    ('current-beta-se-auto-both','source-fbc9ec0','beta-se','auto','standardized','both',True),
    ('current-or-se-fixed-mean','source-fbc9ec0','or-se','fixed','per-allele','mean',True),
    ('current-beta-p-fixed-draws','source-fbc9ec0','beta-p','fixed','standardized','draws',False),
    ('legacy110-or-p-auto-mean','1.1.0','or-p','auto','per-allele','mean',True),
    ('legacy100-beta-p-auto-mean','1.0.0','beta-p','auto','standardized','mean',False),
    ('legacy100-or-p-fixed-mean','1.0.0','or-p','fixed','per-allele','mean',False),
]


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def require(condition,message):
    if not condition:
        raise ValueError(message)


def tree(root):
    return {p.relative_to(root).as_posix():sha(p) for p in sorted(root.rglob('*')) if p.is_file()}


def base(data,out,home):
    return dict(profile='source-fbc9ec0',home=str(home),summary=str(data/'beta-se.txt'),reference=str(data/'ldblk_1kg_eur'),format='beta-se',**{
        'target-bim':str(data/'target.bim'),'reference-kind':'1kg','chromosomes':'1,22',
        'trait-kind':'quantitative','sample-size':'20000','sample-size-kind':'total',
        'gwas-population':'synthetic-fixture','ld-population':'synthetic-fixture','assembly':'synthetic-coordinate-label',
        'a':'1','b':'0.5','phi':'0.01','iterations':'40','burnin':'20','thin':'5','seed':'17',
        'effect-units':'per-allele','posterior':'mean','write-psi':'true','score-export':'per-allele',
        'unmatched':'exclude-with-warning','palindromic':'allow-upstream-with-warning','strand':'allow-complement-with-warning',
        'positions':'require-match','p-floor':'error','minimum-model-variants':'1','out':str(out),
        'maximum-model-variants':'100','maximum-input-rows':'1000','maximum-input-mib':'1','maximum-retained-mib':'16',
        'maximum-line-bytes':'4096','maximum-ld-cells':'10000','maximum-block-snps':'100','maximum-blocks':'100',
        'maximum-report-mib':'16','maximum-posterior-cells':'1000'})


def process(command,cwd,label,expected,report,timeout):
    stdout=cwd/(label+'.stdout');stderr=cwd/(label+'.stderr')
    options={'creationflags':subprocess.CREATE_NEW_PROCESS_GROUP} if os.name=='nt' else {'start_new_session':True}
    with stdout.open('wb') as out,stderr.open('wb') as err:
        child=subprocess.Popen(list(map(str,command)),cwd=ROOT,stdout=out,stderr=err,**options)
        item=dict(label=label,command=list(map(str,command)),owned_pid=child.pid,expected_exit=expected)
        report['processes'].append(item)
        try:
            code=child.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            if os.name=='nt':
                stopped=subprocess.run(['taskkill','/PID',str(child.pid),'/T','/F'],capture_output=True,timeout=30)
                (cwd/(label+'.owned-tree-stop.stdout')).write_bytes(stopped.stdout)
                (cwd/(label+'.owned-tree-stop.stderr')).write_bytes(stopped.stderr)
            else:
                import signal
                os.killpg(child.pid,signal.SIGKILL)
            child.wait(timeout=30)
            item.update(timed_out=True,exit_code=child.returncode)
            raise RuntimeError('Outer verification deadline expired;owned Java process tree terminated: '+label)
    item.update(exit_code=code,stdout_sha256=sha(stdout),stderr_sha256=sha(stderr))
    require(code==expected,f'{label}: expected exit{expected},observed{code};inspect retained stderr and result/WARNINGS.md')


def rows(path):
    return [line.split('\t') for line in path.read_text(encoding='utf-8').splitlines()]


def validate(options,result,cases,inputs):
    require(json.loads((result/'analysis-status.json').read_text())['status']=='passed-with-review-warnings','No successful analysis status')
    warning=(result/'WARNINGS.md').read_text(encoding='utf-8')
    require(warning.startswith('# PRS-CS — REVIEW BEFORE USE') and 'Required action:' in warning,'Prominent review/action warning missing')
    require(len(rows(result/'variant-ledger.tsv'))==26 and len(rows(result/'native-marginals.tsv'))==21,'Input/variant ledger family differs')
    reference={r[1]:r for r in inputs['reference']};target={r[1]:r for r in inputs['target']};mapping=dict(zip('ATGC','TACG'))
    totals=dict(variants=20,posterior_scalars=0,psi_fields=0,original_files=0,score_fields=0)
    modes=['mean','draws'] if options['posterior']=='both' else [options['posterior']]
    original_rows={}
    for mode in modes:
        label=options['profile']+'-'+options['format']+'-'+('auto' if options['phi']=='auto' else 'fixed')+'-'+('std' if options['effect-units']=='standardized' else 'allele')+'-'+mode
        expected=cases[label];collected=[];psi=[]
        for filename,value in expected['files_sha256'].items():
            if '_psi_' in filename and options['write-psi']=='false':
                continue
            actual=result/('upstream-'+mode)/filename;require(sha(actual)==value,'Original output bytes differ: '+label+'/'+filename)
            totals['original_files']+=1
            (psi if '_psi_' in filename else collected).extend(rows(actual))
        original_rows[mode]=collected
        canonical=rows(result/('posterior-means.tsv' if mode=='mean' else 'posterior-draws.tsv'))
        require(len(canonical)==21 and len(collected)==20,'Canonical posterior family differs')
        for original,row in zip(collected,canonical[1:],strict=True):
            require(row[:5]==original[:5] and row[7:]==original[5:] and row[6]==options['effect-units'],'Canonical posterior strings/identity/units differ')
            r=reference[row[1]];t=target[row[1]];pair=r[3:5];tgt=t[4:6]
            positive=tgt==pair or tgt!=pair[::-1] and tgt==[mapping[a] for a in pair]
            frequency=r[5] if positive else 1-r[5]
            require(float(row[5])==frequency,'Canonical reference effect frequency differs')
            require(all(math.isfinite(float(v)) for v in row[7:]),'Nonfinite canonical posterior')
            totals['posterior_scalars']+=len(row[7:])
        if psi and mode==modes[0]:
            canonical_psi=rows(result/'shrinkage.tsv');require(len(canonical_psi)==21,'Canonical psi family differs')
            for expected_row,row in zip(psi,canonical_psi[1:],strict=True):
                require(row[1:]==expected_row and 0<float(row[2])<=1,'Canonical psi strings/family/range differs')
                totals['psi_fields']+=1
    if options['write-psi']=='false':
        require(not (result/'shrinkage.tsv').exists(),'Unrequested psi output was invented')
    if options['score-export']=='per-allele':
        score=rows(result/'score-weights.tsv');mean=rows(result/'posterior-means.tsv')
        require(score[0]==['variant','ea','oa','weight','ea_frequency'] and len(score)==21,'Score export schema/family differs')
        for weight,m in zip(score[1:],mean[1:],strict=True):
            require(weight[:3]==[m[1],m[3],m[4]] and float(weight[4])==float(m[5]),'Score export identity/frequency differs')
            f=float(m[5]);expected=float(m[7])/(math.sqrt(2*f*(1-f)) if options['effect-units']=='standardized' else 1)
            require(math.isclose(float(weight[3]),expected,rel_tol=3e-15,abs_tol=3e-15),'Explicit per-allele score conversion differs')
            totals['score_fields']+=1
    else:
        require(not (result/'score-weights.tsv').exists(),'Unrequested score export was invented')
    return totals


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--java',type=Path,action='append',required=True,help='Repeat for independently tested JVM executables')
    parser.add_argument('--home',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True,help='New directory;all attempts and diagnostics retained')
    parser.add_argument('--outer-timeout-seconds',type=int,default=1200)
    args=parser.parse_args();args.out=args.out.resolve();args.jar=args.jar.resolve();args.home=args.home.resolve()
    require(args.outer_timeout_seconds>=120,'Require outer deadline >=120 seconds,including runtime integrity checks')
    args.out.mkdir(parents=True,exist_ok=False);record=dict(status='running',processes=[],cases=[],jar=str(args.jar),jar_sha256=sha(args.jar),runtime_lock_sha256=sha(runtime.LOCK_PATH),scope='Synthetic tests only. Actual packaged command must reproduce independent frozen original posterior bytes and exact canonical identities/units. No real cohort,predictive performance,convergence or full-project gate claim.');start=time.monotonic()
    def save():
        (args.out/'verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    save();code=2
    try:
        require(runtime.available(args.home,['source-fbc9ec0','1.1.0','1.0.0']),'Pinned three-profile private runtime unavailable;where permitted use install_prscs.py with a new private home. This verifier never installs or downloads.')
        for line in (FIXTURE/'SHA256SUMS').read_text().splitlines():
            value,name=line.split('  ',1);require(sha(FIXTURE/name)==value,'Changed frozen reference: '+name)
        source_proof=json.loads((FIXTURE/'posterior-provenance.json').read_text());cases={r['label']:r for r in source_proof['cases']};require(len(cases)==56,'Require56 independent original reference runs')
        data=args.out/'input';data.mkdir()
        for name in ['target.bim','beta-se.txt','or-se.txt','beta-p.txt','or-p.txt','independent-inputs.json','ldblk_1kg_eur/snpinfo_1kg_hm3','ldblk_1kg_eur/ldblk_1kg_chr1.hdf5','ldblk_1kg_eur/ldblk_1kg_chr22.hdf5']:
            destination=data/name;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(FIXTURE/name,destination)
        input_hashes=tree(data);inputs=json.loads((data/'independent-inputs.json').read_text());record['input_sha256']=input_hashes;record['runtime_marker_sha256']=sha(args.home/runtime.MARKER)
        for index,java in enumerate(args.java,1):
            jvm=args.out/f'jvm{index}';jvm.mkdir();process([java,'-version'],jvm,'java-version',0,record,60);process([java,'-jar',args.jar,'prs-cs-tools','--help'],jvm,'help',0,record,60)
            for label,profile,form,phi,units,posterior,psi in CASES:
                work=jvm/label;work.mkdir();result=work/'result';options=base(data,result,args.home)
                options.update(profile=profile,format=form,summary=str(data/(form+'.txt')),phi='auto' if phi=='auto' else '.01',posterior=posterior,**{'effect-units':units,'write-psi':str(psi).lower(),'score-export':'none' if posterior=='draws' else 'per-allele','p-floor':'original-floor-with-warning' if form.endswith('-p') else 'error'})
                if form.startswith('or'):
                    options.update({'trait-kind':'binary','sample-size-kind':'effective'})
                if profile!='source-fbc9ec0':
                    options['legacy-use']='offline-reproduction'
                settings=work/'options.json';settings.write_text(json.dumps(options,indent=2)+'\n');process([java,'-jar',args.jar,'prs-cs-tools','--options-file',settings],work,'analysis',0,record,args.outer_timeout_seconds)
                record['cases'].append(dict(jvm=index,label=label,totals=validate(options,result,cases,inputs),bundle_sha256=tree(result)));save();print(json.dumps(dict(jvm=index,passed=label)),flush=True)
            controls=[('missing-home',{'home':str(jvm/'absent-runtime')},3),('historical-use-required',{'profile':'1.1.0'},2),('unsupported100-se',{'profile':'1.0.0','legacy-use':'offline-reproduction'},2),('unsupported110-draws',{'profile':'1.1.0','legacy-use':'offline-reproduction','posterior':'draws','score-export':'none'},2),('fractional-schedule',{'iterations':'12','burnin':'0'},2),('extra-retained-iteration',{'iterations':'12','burnin':'3'},2),('negative-seed',{'seed':'-1'},2),('inexact-n',{'sample-size':'9007199254740992'},2),('or-quantitative',{'format':'or-se'},2),('duplicate-chromosomes',{'chromosomes':'1,1'},2),('unused-floor',{'p-floor':'original-floor-with-warning'},2),('unknown-option',{'unknown-option':'value'},2),('current-legacy-use',{'legacy-use':'offline-reproduction'},2),('output-overlap-reference',{'out':str(data/'ldblk_1kg_eur/forbidden-output')},2)]
            for label,changes,expected in controls:
                work=jvm/label;work.mkdir();options=base(data,work/'result',args.home);options.update(changes);settings=work/'options.json';settings.write_text(json.dumps(options,indent=2)+'\n');process([java,'-jar',args.jar,'prs-cs-tools','--options-file',settings],work,'control',expected,record,args.outer_timeout_seconds)
                if expected==3:
                    warning=(work/'result/WARNINGS.md').read_text(encoding='utf-8');require(warning.startswith('# PRS-CS SKIPPED') and '--allow-install' in warning,'Missing actionable dependency skip');require(not (jvm/'absent-runtime').exists(),'Analysis installed an unavailable runtime')
                else:
                    require(not Path(options['out']).exists(),'Invalid configuration created an analysis directory')
                record['cases'].append(dict(jvm=index,label=label,expected_failure_code=expected));save()
            require(tree(data)==input_hashes,'Supplied reference/summary/BIM input changed during packaged tests')
        require(runtime.available(args.home),'Private runtime changed during packaged tests');require(sha(args.jar)==record['jar_sha256'],'Packaged executable changed')
        record.update(status='passed-packaged-original-and-canonical-contracts',successful_workflows=6*len(args.java),failure_controls=14*len(args.java),seconds=time.monotonic()-start);code=0
    except Exception as error:
        record.update(status='failed-retained',error=str(error),traceback=traceback.format_exc(),seconds=time.monotonic()-start)
    save();warning='# PRS-CS PACKAGED VERIFICATION '+('PASSED — TEST DATA ONLY' if code==0 else 'FAILED — DO NOT PROMOTE')+'\n\n'+record.get('error','The listed synthetic original-output and canonical checks passed.')+'\n\nRequired action: inspect verification.json and each retained attempt/WARNINGS.md and stderr. Correct the named executable,runtime,input or implementation and rerun to a new output directory. Do not use fixture sampling settings as scientific recommendations. No installation,download,clinical or convergence validation is performed.\n'
    (args.out/'WARNINGS.md').write_text(warning,encoding='utf-8');print(json.dumps(dict(status=record['status'],report=str(args.out/'WARNINGS.md'),exit_code=code)));return code


if __name__=='__main__':
    raise SystemExit(main())
