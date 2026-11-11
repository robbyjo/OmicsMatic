# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the guarded original scan worker protocol against the independently captured full-function baseline."""
import argparse
import csv
import json
import os
from pathlib import Path
import shutil
import subprocess
from validate_mashr_native import pin
from validate_signac_motif_workflow import compare
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def prepare(data,case,profile,root):
    root.mkdir();inputs=root/'input';inputs.mkdir();d=data['datasets'][case['dataset']];peaks=[f"{p['chromosome']}-{p['start']}-{p['end']}" for p in d['peaks']]
    newline='\r\n' if d['crlf'] else '\n';(inputs/'reference.fa').write_bytes(''.join('>'+id+newline+seq+newline for id,seq in d['sequences'].items()).encode('utf8'))
    values=[(i+1,j+1,row[j]) for j in range(len(d['cells'])) for i,row in enumerate(d['counts']) if row[j]!=0];(inputs/'counts.mtx').write_text('%%MatrixMarket matrix coordinate real general\n'+f"{len(peaks)} {len(d['cells'])} {len(values)}\n"+''.join(f'{i} {j} {value}.0\n' for i,j,value in values),encoding='utf8',newline='\n')
    (inputs/'peaks.tsv').write_text('chromosome\tstart\tend\n'+''.join(f"{r['chromosome']}\t{r['start']}\t{r['end']}\n" for r in d['peaks']),encoding='utf8',newline='\n');(inputs/'features.tsv').write_text('\n'.join(peaks)+'\n',encoding='utf8',newline='\n');(inputs/'cells.tsv').write_text('\n'.join(d['cells'])+'\n',encoding='utf8',newline='\n')
    lines=['motif_id\tmotif_name\tposition\tA\tC\tG\tT\tbg_A\tbg_C\tbg_G\tbg_T']
    for m in d['pfms']:
        for i,row in enumerate(m['rows']):lines.append('\t'.join(map(str,[m['id'],m['name'],i+1,*row,*m['background']])))
    (inputs/'pfms.tsv').write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n');selected=lambda values:[peaks[v] if isinstance(v,int) else v for v in values];(inputs/'query.tsv').write_text(''.join(v+'\n' for v in selected(case['query'])),encoding='utf8',newline='\n')
    params=dict(profile=profile,counts='input/counts.mtx',features='input/features.tsv',cells='input/cells.tsv',peaks='input/peaks.tsv',pfm='input/pfms.tsv',fasta='input/reference.fa',query='input/query.tsv',features_match=case['features_match'],p_adjust_method=case['adjustment'],seed=case['seed'],maximum_hits=1000000)
    if case['background'] is None:params['background_kind']='all'
    elif isinstance(case['background'],int):params.update(background_kind='matched',background_n=case['background'])
    else:(inputs/'background.tsv').write_text(''.join(v+'\n' for v in selected(case['background'])),encoding='utf8',newline='\n');params.update(background_kind='explicit',background_file='input/background.tsv')
    save(root/'parameters.json',params);source=root/'source';source.mkdir();shutil.copytree(ROOT/'src/main/resources/org/jlinalg/omics/signac-scan',source/'signac-scan')
    for name in ['signac_motif_scan.R','signac_motif_legacy_access.R','signac_scan_legacy_fasta.R']:shutil.copyfile(ROOT/'src/main/resources/org/jlinalg/omics'/name,source/name)
    (root/'worker-request.txt').write_text(str(BASE/'motif-reference-setup-1/R-lib')+'\n'+str(BASE/'reference-setup-1/R-lib')+'\n'+str(ROOT/'build/omics-sctransform-r')+'\nparameters.json\n',encoding='utf8',newline='\n');return params

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='guarded-original-scan-worker-protocol-probe-in-progress'));process=None
    try:
        names=['tools/literature/probe_signac_scan_worker.py','tools/literature/validate_signac_motif_workflow.py','src/main/resources/org/jlinalg/omics/signac_motif_scan.R','src/main/resources/org/jlinalg/omics/signac_motif_legacy_access.R','src/main/resources/org/jlinalg/omics/signac_scan_legacy_fasta.R'];names += [p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/resources/org/jlinalg/omics/signac-scan').rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');data=json.loads((BASE/'scan-original-7/cases.json').read_bytes());original=json.loads((BASE/'scan-original-7/original.json').read_bytes());case=next(c for c in data['cases'] if c['id']=='matched');runs=[]
        for profile in data['profiles']:
            root=out/profile;prepare(data,case,profile,root);argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla','source/signac_motif_scan.R','--request-file','worker-request.txt']
            with (root/'stdout.log').open('xb') as stdout,(root/'stderr.log').open('xb') as stderr:process=subprocess.Popen(argv,cwd=root,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);save(out/'RUNNING.json',dict(pid=process.pid,profile=profile,command=argv));code=process.wait(timeout=120)
            save(root/'process.json',dict(command=argv,exit_code=code));assert code==0,(profile,code,(root/'worker-status.tsv').read_text(encoding='utf8'));reference=next(r['result']['value'] for r in original['results'] if r['profile']==profile and r['id']=='matched');compare(root/'enrichment-original.tsv',dict(rows=reference['enrichment']),profile=='1.6.0-source')
            for name,key in [('rng-before.tsv','seed_before'),('rng-after.tsv','seed_after')]:
                rows=list(csv.DictReader((root/name).read_text(encoding='utf8').splitlines(),delimiter='\t'));assert [int(r['value']) for r in rows]==reference[key]
            runs.append(dict(profile=profile,original_enrichment_statistics_and_RNG_match=True));print('passed guarded scan worker '+profile,flush=True)
        assert all(pin(ROOT/n)==v for n,v in sources.items());save(out/'COMPLETED.json',dict(status='passed-guarded-original-scan-worker-baselines',source_pins=sources,runs=runs,user_request_workflow_admitted=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-guarded-original-scan-worker-baselines',profiles=4)),flush=True)
    except BaseException as error:
        if process is not None and process.poll() is None:subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20,check=False);process.wait(timeout=20)
        save(out/'FAILURE.json',dict(status='FAILED_GUARDED_SCAN_WORKER_PROTOCOL_PROBE',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original scan worker checks')
    main()
