# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check strict production input parsing against complete source fixtures, without upstream execution."""
import argparse,csv,gzip,io,json,os,shutil,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','run','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_INPUT_CONTRACTS',checks=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};original=a.run.resolve();assert json.loads((original/'analysis-status.json').read_bytes())['result_admitted'];request=json.loads((original/'request.original.json').read_bytes());assert len(request['analyses'])==1;case=request['analyses'][0]['id'];before={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-files-before.json',before)
        mutations=['duplicate-id','empty-id','duplicate-coordinate','numeric-whitespace','nan-token','overflow','nonzero-underflow','blank-row','utf8-bom','reordered-weights','root-value-width','gzip-exact-rows']
        for mutation in mutations:
            home=out/mutation;home.mkdir();family=home/'family';shutil.copytree(original,family,copy_function=os.link,ignore=shutil.ignore_patterns('private-*'));name='inputs/'+case+'.coordinates.csv';source=(family/name).read_bytes();rows=list(csv.reader(io.StringIO(source.decode('utf8'),newline='')));spec=json.loads((family/'request.original.json').read_bytes());overlay=[]
            def replace(member,raw):
                target=family/member;temp=target.with_name(target.name+'.mutation');assert not temp.exists()
                with temp.open('xb') as stream:stream.write(raw)
                os.replace(temp,target);overlay.append(member)
            def table(rows):stream=io.StringIO(newline='');writer=csv.writer(stream,quoting=csv.QUOTE_ALL,lineterminator='\r\n');writer.writerows(rows);return stream.getvalue().encode()
            reject=True;message=''
            if mutation=='duplicate-id':rows[2][0]=rows[1][0];message='Duplicate input ID'
            elif mutation=='empty-id':rows[1][0]='';message='Empty/unsupported-control'
            elif mutation=='duplicate-coordinate':rows[0][2]=rows[0][1];message='Duplicate coordinate label'
            elif mutation in ['numeric-whitespace','nan-token','overflow','nonzero-underflow']:
                rows[1][1]={'numeric-whitespace':' '+rows[1][1]+' ','nan-token':'NaN','overflow':'1e1000','nonzero-underflow':'1e-1000'}[mutation];message={'overflow':'Input overflows float64','nonzero-underflow':'Empty input or nonzero tokens'}.get(mutation,'Require unchanged finite decimal')
            elif mutation=='blank-row':rows.append(['']);message='Ragged/blank/oversized complete input'
            elif mutation=='utf8-bom':replace(name,b'\xef\xbb\xbf'+source);message='Quote inside an unquoted field'
            elif mutation=='reordered-weights':
                weights=[row.copy() for row in rows];weights[1],weights[2]=weights[2],weights[1];member='inputs/'+case+'.weights.csv';replace(member,table(weights));spec['analyses'][0]['weights']=dict(path='unused-in-native-fixture.csv',format='csv',source='Deliberately wrong row order; no source analysis executed');message='Weight cells and coordinate labels/order must match exactly'
            elif mutation=='root-value-width':
                values=[['cell_id','signal1','signal2'],*[[row[0],'1','2'] for row in rows[1:]]];replace('inputs/'+case+'.root-values.csv',table(values));spec['analyses'][0]['root']=dict(mode='obs',values=dict(path='unused-in-native-fixture.csv',format='csv',source='Deliberately wrong root width'),tips_only=True,min_val=True);message='Root observations require exactly one value'
            else:replace(name,gzip.compress(source,mtime=0));reject=False
            if mutation not in ['utf8-bom','reordered-weights','root-value-width','gzip-exact-rows']:replace(name,table(rows))
            if mutation in ['reordered-weights','root-value-width']:replace('request.original.json',(json.dumps(spec)+'\n').encode())
            report=home/'native.json';command=[str(JAVA21/'java.exe'),'-Xmx1g','-cp',str(jar),'org.jlinalg.cli.ScfatesWorkflowAuditMatrix',str(family),str(report)];run=subprocess.run(command,capture_output=True,timeout=120);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);assert (run.returncode!=0 and message.encode() in run.stderr and not report.exists()) if reject else (run.returncode==0 and report.exists()),(mutation,run.stderr[-3000:]);state['checks'].append(dict(mutation=mutation,command=command,exit_code=run.returncode,expected_rejection=message,fixture_overlay={n:pin(family/n) for n in overlay},original_analysis_executed=False))
        after={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-files-after.json',after);assert before==after;state.update(status='PASS_11_INVALID_SCFATES_INPUTS_AND_EXACT_GZIP_FIXTURE_ONLY',original_files_unchanged=True,artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
