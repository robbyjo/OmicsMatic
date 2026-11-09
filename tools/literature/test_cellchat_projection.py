# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete native-JAR projection/reference comparisons and admission regressions."""
import argparse,csv,hashlib,json,math,shutil,subprocess,traceback
from pathlib import Path

def save(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def pin(path):raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def matrix(path):
    with path.open(encoding='utf8',newline='') as f:rows=list(csv.reader(f,delimiter='\t'))
    return rows[0],[r[0] for r in rows[1:]],[[float(x) for x in r[1:]] for r in rows[1:]]
def request(expression,ppi,out,case):
    return dict(schema_version=1,out=str(out),expression=str(expression),ppi=str(ppi),expression_units='external-log-normalized-nonnegative',alpha=case['alpha'],normalization=case['normalization'],limits=dict(input_bytes=1048576,max_network_genes=256,max_expression_genes=256,max_cells=100,working_bytes=67108864,arithmetic_updates=100000000))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--java',required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_CELLCHAT_PROJECTION_CONTROLS',implementation_credit=False,whole_goal_complete=False);records=[];jar=a.jar.resolve();reference=a.reference.resolve();own=Path(__file__).resolve();bindings={str(jar):pin(jar),str(own):pin(own)}
    def run(label,args,expected):
        command=[a.java,'-Xmx512m',*args];r=subprocess.run(command,capture_output=True,stdin=subprocess.DEVNULL,timeout=180)
        (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr)
        record=dict(id=label,command=command,exit_code=r.returncode,expected_exit=expected);records.append(record);assert r.returncode==expected,(label,r.returncode,r.stderr[-1500:]);return r
    try:
        assert json.loads((reference/'COMPLETED.json').read_bytes())['status']=='PASS_ORIGINAL_CELLCHAT_PROJECTION_REFERENCE_ONLY'
        run('api',['-cp',str(jar),'org.jlinalg.cli.CellChatProjectionProbe'],0)
        cases=json.loads((reference/'cases.json').read_bytes());comparisons=[];allpins={}
        requests=out/'requests';requests.mkdir();runs=out/'runs';runs.mkdir()
        for case in cases:
            home=reference/case['id'];target=runs/case['id'];recipe=request(home/'expression.tsv',home/'ppi.tsv',target,case);q=requests/(case['id']+'.json');save(q,recipe)
            for name in ['expression.tsv','ppi.tsv','original-projected.tsv']:allpins[str(home/name)]=pin(home/name)
            run(case['id'],['-jar',str(jar),'--request',str(q)],0);run(case['id']+'-verify',['-jar',str(jar),'--verify',str(target)],0)
            expected=matrix(home/'original-projected.tsv');actual=matrix(target/'projected.tsv');assert actual[:2]==expected[:2],case['id']
            error=max((abs(x-y)/max(1,abs(y)) for ar,er in zip(actual[2],expected[2]) for x,y in zip(ar,er)),default=0)
            tolerance=5e-9 if case['alpha']>.999 else 2e-12;assert error<=tolerance,(case['id'],error,tolerance)
            comparisons.append(dict(id=case['id'],maximum_scaled_error=error,tolerance=tolerance,genes=len(actual[1]),cells=len(actual[0])-1))
        # Complete late-row/data/schema/capacity failures, without admitting partial output.
        good=reference/'bridge-rows';failures=out/'failure-inputs';failures.mkdir()
        base=request(good/'expression.tsv',good/'ppi.tsv',out/'unused',dict(alpha=.5,normalization='rows'))
        variants={
            'alpha0':lambda r:r.update(alpha=0),'alpha1':lambda r:r.update(alpha=1),
            'unknown-field':lambda r:r.update(unapproved=True),
            'wrong-units':lambda r:r.update(expression_units='counts'),
            'network-capacity':lambda r:r['limits'].update(max_network_genes=2),
            'cell-capacity':lambda r:r['limits'].update(max_cells=0),
            'working-capacity':lambda r:r['limits'].update(working_bytes=1),
            'arithmetic-capacity':lambda r:r['limits'].update(arithmetic_updates=1),
            'input-capacity':lambda r:r['limits'].update(input_bytes=2),
        }
        for name,mutate in variants.items():
            r=json.loads(json.dumps(base));r['out']=str(out/('refused-'+name));mutate(r);q=requests/('refused-'+name+'.json');save(q,r)
            result=run('refused-'+name,['-jar',str(jar),'--request',str(q)],2);assert b'REQUIRED ACTION:' in result.stderr
            target=Path(r['out']);assert not (target/'COMPLETED.json').exists()
            if target.exists():assert not json.loads((target/'FAILED.json').read_bytes())['result_admitted'] and b'FAIL2' in (target/'WARNINGS.md').read_bytes()
        bad_inputs={
            'duplicate-gene':('expression.tsv',b'gene_id\tcell\nA\t1\nA\t2\n'),
            'nonfinite-late':('expression.tsv',b'gene_id\tcell\nA\t1\nC\tNaN\n'),
            'negative-late':('expression.tsv',b'gene_id\tcell\nA\t1\nC\t-1\n'),
            'malformed-utf8':('expression.tsv',b'gene_id\tcell\nA\t1\n\xff\t2\n'),
            'ragged-late':('expression.tsv',b'gene_id\tcell\nA\t1\nC\n'),
            'trimmed-number':('expression.tsv',b'gene_id\tcell\nA\t1\nC\t 2\n'),
            'axis-mismatch':('ppi.tsv',b'gene_id\tB\tA\nA\t1\t2\nB\t3\t1\n'),
            'zero-row':('ppi.tsv',b'gene_id\tA\tB\nA\t0\t0\nB\t1\t1\n'),
            'zero-column':('ppi.tsv',b'gene_id\tA\tB\nA\t0\t1\nB\t0\t1\n'),
            'negative-network':('ppi.tsv',b'gene_id\tA\tB\nA\t1\t-1\nB\t1\t1\n'),
        }
        for name,(field,raw) in bad_inputs.items():
            file=failures/(name+'.tsv');file.write_bytes(raw);r=json.loads(json.dumps(base));r['out']=str(out/('refused-'+name));r['expression' if field=='expression.tsv' else 'ppi']=str(file);q=requests/('refused-'+name+'.json');save(q,r)
            result=run('refused-'+name,['-jar',str(jar),'--request',str(q)],2);assert b'REQUIRED ACTION:' in result.stderr;target=Path(r['out']);assert not (target/'COMPLETED.json').exists() and not (target/'projected.tsv').exists()
            assert not json.loads((target/'FAILED.json').read_bytes())['result_admitted'] and b'FAIL2' in (target/'WARNINGS.md').read_bytes()
        # Offline replay refuses both ordinary tampering and rehashed false data.
        original=runs/'bridge-rows';tampers=out/'tampered';tampers.mkdir()
        for name in ['missing-file','extra-file','changed-output','rehashed-output','rehashed-diagnostic','changed-input']:
            target=tampers/name;shutil.copytree(original,target);manifest=json.loads((target/'COMPLETED.json').read_bytes())
            if name=='missing-file':(target/'ppi.tsv').unlink()  # only the owned tamper copy
            elif name=='extra-file':(target/'extra.txt').write_text('unexpected')
            elif name in ['changed-output','rehashed-output']:
                q=target/'projected.tsv';rows=q.read_text(encoding='utf8').splitlines();fields=rows[1].split('\t');fields[1]='123.5';rows[1]='\t'.join(fields);q.write_text('\n'.join(rows)+'\n',encoding='utf8');
                if name=='rehashed-output':manifest['files'][q.name]=pin(q)
            elif name=='rehashed-diagnostic':
                q=target/'projection.json';report=json.loads(q.read_bytes());report['overlapping_genes']=0;save(q,report);manifest['files'][q.name]=pin(q)
            else:
                q=target/'expression.tsv';q.write_bytes(q.read_bytes().replace(b'\t8\n',b'\t9\n'));manifest['files'][q.name]=pin(q)
            if name.startswith('rehashed') or name=='changed-input':save(target/'COMPLETED.json',manifest)
            run('tamper-'+name,['-jar',str(jar),'--verify',str(target)],2)
        assert all(pin(Path(n))==v for n,v in {**bindings,**allpins}.items())
        state.update(status='PASS_NATIVE_CELLCHAT_PROJECTION_CONTROLS_ONLY',original_comparisons=comparisons,reference_cases=len(cases),protocols=len(records),expected_analysis_refusals=len(variants)+len(bad_inputs),tamper_refusals=6,api_checks=19,maximum_scaled_error=max(r['maximum_scaled_error'] for r in comparisons),source_pins=bindings,reference_pins=allpins,records=records)
    except Exception as error:state['error']=repr(error);state['records']=records;(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete native/reference checks')
    raise SystemExit(main())
