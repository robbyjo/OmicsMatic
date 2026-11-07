# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise native motif requests with original prepared background selections and portable verification."""
import argparse
import csv
import json
import math
from pathlib import Path
import subprocess
from validate_mashr_native import JAVA21,linux,pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacMotifsRequestCli'

def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def array(value):return [] if value is None else value if isinstance(value,list) else [value]
def number(value):return float('nan') if value in ['NaN','NA',None] else float(value)
def compare(path,reference,legacy):
    with path.open(encoding='utf8',newline='') as stream:
        reader=csv.DictReader(stream,delimiter='\t');assert reader.fieldnames==['motif','observed','background','percent.observed','percent.background','fold.enrichment','pvalue','motif.name']+([] if legacy else ['p.adjust']);rows=list(reader)
    want=array(reference['rows']);assert len(rows)==len(want)
    for got,want in zip(rows,want,strict=True):
        assert (got['motif'],got['motif.name'])==(want['motif'],want['motif.name'])
        for key in ['observed','background','percent.observed','percent.background','fold.enrichment','pvalue']+([] if legacy else ['p.adjust']):
            a,b=number(got[key]),number(want[key]);assert a==b or math.isnan(a) and math.isnan(b) or math.isclose(a,b,rel_tol=2e-11,abs_tol=2e-12),(key,a,b)

def prepare(data,reference,out,target):
    c=next(c for c in data['cases'] if c['id']==reference['id']);d=data['datasets'][c['dataset']];out.mkdir();peaks=[f"{p['chromosome']}-{p['start']}-{p['end']}" for p in d['peaks']];motifs=d['motifs'];values=[(row+1,column+1,d['matrix'][row][column]) for column in range(len(motifs)) for row in range(len(peaks)) if d['matrix'][row][column]!=0]
    (out/'matrix.mtx').write_text('%%MatrixMarket matrix coordinate integer general\n'+f'{len(peaks)} {len(motifs)} {len(values)}\n'+''.join(f'{row} {column} {value}\n' for row,column,value in values),encoding='utf8',newline='\n');(out/'peaks.tsv').write_text('\n'.join(peaks)+'\n',encoding='utf8',newline='\n')
    labels=reference['result']['value']['effective_motif_names'] if 'error' not in reference['result']['value'] else dict(zip(motifs,d['motif_names'],strict=True));(out/'motifs.tsv').write_text('motif_id\tmotif_name\n'+''.join(id+'\t'+labels[id]+'\n' for id in motifs),encoding='utf8',newline='\n')
    if 'error' not in reference['result']['value']:
        query=array(reference['replay']['value']['effective_query']);bg=array(reference['replay']['value']['effective_background'])
    else:
        query=[peaks[v] if isinstance(v,int) else v for v in c['query']];bg=[peaks[v] if isinstance(v,int) else v for v in c['background']]
    for name,values in [('query.tsv',query),('background.tsv',bg)]:(out/name).write_text(''.join(v+'\n' for v in values),encoding='utf8',newline='\n')
    return dict(schema_version=1,mode='ENRICH',profile=reference['profile'],out=str(target),genome_build='synthetic-not-reference-certified',motif_collection='synthetic-original-controls-not-JASPAR',motif_matrix=str(out/'matrix.mtx'),peak_features=str(out/'peaks.tsv'),motif_labels=str(out/'motifs.tsv'),query=str(out/'query.tsv'),background=str(out/'background.tsv'),p_adjust_method=c['adjustment'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='motif-workflow-build-1');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in args.build and '\\' not in args.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-motif-request-original-control-matrix-in-progress'))
    try:
        build=read(BASE/args.build/'BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};data=read(BASE/'motif-original-2/cases.json');original=read(BASE/'motif-original-2/original.json');names=['tools/literature/validate_signac_motif_workflow.py'];names += [(BASE/'motif-original-2'/n).relative_to(ROOT).as_posix() for n in ['cases.json','original.json']];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1600:])
        inputs=out/'Unicode α inputs';inputs.mkdir();analyses=out/'Unicode β analyses';analyses.mkdir();runs=[];failures=[]
        for index,reference in enumerate(original['results']):
            expected=reference['result']['value'];failed='error' in expected
            if failed and reference['id'] not in ['missing-explicit-query','missing-explicit-background']:continue
            name=f"{index:03d}-{reference['profile']}-{reference['id']}";target=analyses/name;request=prepare(data,reference,inputs/name,target);path=out/(f'request-{index:03d}.json');save(path,request);before={p.name:pin(p) for p in (inputs/name).iterdir()};command(name,[JAVA21/'java.exe','-Xmx512m','-cp',jar,CLI,'--request',path],2 if failed else 0)
            if failed:
                assert not (target/'enrichment.tsv').exists() and read(target/'analysis-status.json')['status']=='failed';failures.append(dict(profile=reference['profile'],case=reference['id'],no_result_admitted=True))
            else:
                compare(target/'enrichment.tsv',expected,reference['profile']=='1.6.0-source')
                for platform,java,convert in platforms:
                    verify=out/(f'verify-{index:03d}-'+platform+'.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-verify-'+platform,[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(verify)])
                runs.append(dict(profile=reference['profile'],case=reference['id'],analysis_path=target.relative_to(ROOT).as_posix(),original_enrichment_statistics_match=True,prepared_original_background_used=True,captured_verifications=3))
            assert before=={p.name:pin(p) for p in (inputs/name).iterdir()};print(('retained missing-row failure ' if failed else 'passed native motif request ')+name,flush=True)
        assert len(runs)==111 and len(failures)==8 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-native-motif-request-original-control-matrix',source_pins=sources,artifact=build['artifact'],original_statistical_comparisons=runs,retained_explicit_row_failure_gates=failures,captured_verifications=333,inputs_unchanged=True,sequence_scanning_performed=False,upstream_background_selection_performed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],original_comparisons=111,captured_verifications=333,missing_row_failure_gates=8)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_MOTIF_REQUEST_MATRIX',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native motif workflow checks')
    main()
