# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject altered complete observed workflows through the production native audit, preserving originals."""
import argparse,csv,hashlib,io,json,os,shutil,struct,sys,traceback,subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for key in ['build','run','out']:p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_OBSERVED_WORKFLOW_CORRUPTIONS',checks=[],implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};original=a.run.resolve();admission=json.loads((original/'analysis-status.json').read_bytes());assert admission['result_admitted'];request=json.loads((original/'request.original.json').read_bytes());assert len(request['analyses'])==1;case=request['analyses'][0]['id'];before={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-files-before.json',before)
        mutations=['input-value','cell-order','assignment','conditional-system','fitted-node','root-edge','root-time','projection-time','final-segment','milestone-categories','export-cell-order','export-truncated','unbound-array-bytes','native-capacity','partial-original-family']
        for mutation in mutations:
            home=out/mutation;home.mkdir();family=home/'family';shutil.copytree(original,family,copy_function=os.link,ignore=shutil.ignore_patterns('private-*'));overlay=[];prefix='worker/'+case+'/'
            def replace(name,raw):
                target=family/name;temp=target.with_name(target.name+'.mutation');assert not temp.exists()
                with temp.open('xb') as stream:stream.write(raw)
                os.replace(temp,target);overlay.append(name)
            def read(name):return json.loads((family/name).read_bytes())
            def write(name,value):replace(name,(json.dumps(value,ensure_ascii=True,allow_nan=False)+'\n').encode())
            def numeric(name,keys,delta=.1,index=0):
                value=read(name);metadata=value
                for key in keys:metadata=metadata[key]
                assert metadata['kind']=='binary-numeric-array' and metadata['dtype']=='<f8';parent=Path(name).parent;raw=bytearray((family/parent/metadata['path']).read_bytes());old=struct.unpack_from('<d',raw,index*8)[0];struct.pack_into('<d',raw,index*8,old+delta);sha=hashlib.sha256(raw).hexdigest();member='objects/'+sha+'.bin';file=(parent/member).as_posix();assert not(family/file).exists();replace(file,raw);metadata.update(path=member,bytes=len(raw),sha256=sha);write(name,value)
            if mutation=='input-value':numeric(prefix+'source-input.json',['coordinates']);expected='Captured numeric input changed'
            elif mutation=='cell-order':name=prefix+'pseudotime-return.json';value=read(name);value['obs']['index'][:2]=value['obs']['index'][1::-1];write(name,value);expected='Original identity/order changed'
            elif mutation=='assignment':numeric(prefix+'observed-final-ppt-step.json',['locals','R']);expected='Original numeric equation differs'
            elif mutation=='conditional-system':numeric(prefix+'observed-final-ppt-step.json',['locals','M']);expected='Original numeric equation differs'
            elif mutation=='fitted-node':numeric(prefix+'observed-final-ppt-step.json',['locals','F_mat_cpu']);expected='Original numeric equation differs'
            elif mutation=='root-edge':
                name=prefix+'observed-root-step.json';value=read(name);meta=value['to_g'];raw=(family/prefix/meta['path']).read_bytes();values=struct.unpack('<'+'d'*(len(raw)//8),raw);at=next(i for i,v in enumerate(values) if v>0);numeric(name,['to_g'],index=at);expected='Observed Euclidean squared root-edge equation'
            elif mutation=='root-time':name=prefix+'root-return.json';value=read(name);at=value['pp_info']['columns'].index('time');numeric(name,['pp_info','column_data',at],index=1);expected='Original numeric equation differs'
            elif mutation=='projection-time':numeric(prefix+'pseudotime-return.json',['pseudotime_list','0','column_data',0]);expected='Original numeric equation differs'
            elif mutation=='final-segment':
                name=prefix+'pseudotime-return.json';value=read(name);at=value['obs']['columns'].index('seg');column=value['obs']['column_data'][at];assert column['kind']=='object-array';old=column['values'][0];column['values'][0]=next(v for v in column['values'] if v!=old);write(name,value);expected='Original identity/order changed'
            elif mutation=='milestone-categories':name=prefix+'pseudotime-return.json';value=read(name);at=value['obs']['columns'].index('milestones');value['obs']['dtypes'][at]['categories'].reverse();write(name,value);expected='Original identity/order changed'
            elif mutation.startswith('export-'):
                name=prefix+'pseudotime.tsv';rows=list(csv.reader(io.StringIO((family/name).read_bytes().decode('utf8'),newline=''),delimiter='\t'))
                if mutation=='export-cell-order':rows[1][0],rows[2][0]=rows[2][0],rows[1][0];expected='Original identity/order changed'
                else:rows.pop();expected='Incomplete public exported cell family'
                stream=io.StringIO(newline='');writer=csv.writer(stream,delimiter='\t',quoting=csv.QUOTE_ALL,lineterminator='\r\n');writer.writerows(rows);replace(name,stream.getvalue().encode())
            elif mutation=='unbound-array-bytes':
                meta=read(prefix+'source-input.json')['coordinates'];name=prefix+meta['path'];raw=bytearray((family/name).read_bytes());raw[0]^=1;replace(name,raw);expected='Complete array bytes, shape or source hash differs'
            elif mutation=='native-capacity':name='request.original.json';value=read(name);value['maximum_audit_operations']=1;write(name,value);expected='Complete native audit operation capacity exceeded'
            else:name='worker/COMPLETED.json';value=read(name);value['cases']=[];write(name,value);expected='A complete unchanged original family is required'
            command=[str(JAVA21/'java.exe'),'-Xmx3g','-cp',str(jar),'org.jlinalg.cli.ScfatesWorkflowAuditMatrix',str(family),str(home/'must-not-exist.json')];run=subprocess.run(command,capture_output=True,timeout=120);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);assert run.returncode!=0 and expected.encode() in run.stderr and not(home/'must-not-exist.json').exists(),(mutation,run.stderr[-3000:]);state['checks'].append(dict(mutation=mutation,command=command,exit_code=run.returncode,expected_rejection=expected,whole_family_rejected=True,fixture_overlay={name:pin(family/name) for name in overlay}))
        after={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-files-after.json',after);assert before==after;state.update(status='PASS_15_SCFATES_OBSERVED_WORKFLOW_CORRUPTIONS_REJECTED_ONLY',original_files_unchanged=True,artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
