# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Coherent data-only corruptions must fail the packaged native audit; frozen controls are never modified."""
import argparse,base64,json,shutil,struct,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_AUDIT_ADVERSARIAL_CONTROLS',implementation_credit=False,whole_goal_complete=False,checks=[])
    try:
        built=json.loads((args.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};control=ROOT/'build/literature-review/cellphonedb-runtime/safe-worker-controls-3';original=json.loads((control/'request.json').read_bytes());bound={p.relative_to(ROOT).as_posix():pin(p) for p in control.rglob('*') if p.is_file()}
        def read(p):return json.loads(p.read_bytes())
        def number(frame,column,row,value):
            v=frame['column_data'][column];raw=bytearray(base64.b64decode(v['base64']));fmt={'<f4':'<f','<f8':'<d'}[v['dtype']];struct.pack_into(fmt,raw,row*struct.calcsize(fmt),value);v['base64']=base64.b64encode(raw).decode('ascii')
        def observer(home,family):
            v=read(home/'return-observer.json');v['records'][family]=pin(home/('observed-'+family+'.json'));save(home/'return-observer.json',v)
        def mutate(kind,home):
            if kind=='mapping':v=read(home/'preparation.json');number(v['mapped'],0,0,999);save(home/'preparation.json',v)
            elif kind=='axis':v=read(home/'preparation.json');v['source_filtered_counts']['columns'].reverse();save(home/'preparation.json',v)
            elif kind=='observed':v=read(home/'observed-means.json');number(v['returned'],0,0,999);save(home/'observed-means.json',v);observer(home,'means')
            elif kind=='null-bit':
                v=read(home/'observed-permutation-greater-packed.json');g=read(home/'observed-eligibility.json')['returned'];m=read(home/'observed-means.json')['returned'];index=None
                for i in range(len(g['index'])):
                    for j in range(len(g['columns'])):
                        eligible=struct.unpack_from('<q',base64.b64decode(g['column_data'][j]['base64']),i*8)[0];mean=struct.unpack_from('<f',base64.b64decode(m['column_data'][j]['base64']),i*4)[0]
                        if eligible and mean>0:index=i*len(g['columns'])+j;break
                    if index is not None:break
                assert index is not None;bits=v['returned'][0];data=bytearray(base64.b64decode(bits['base64']));data[index//8]^=1<<(7-index%8);bits['base64']=base64.b64encode(data).decode('ascii');save(home/'observed-permutation-greater-packed.json',v);observer(home,'permutation-greater-packed')
            elif kind=='missing-family':
                v=read(home/'returned.json');del v['interaction_scores'];save(home/'returned.json',v);v=read(home/'CASE-COMPLETED.json');del v['tables']['interaction_scores'];save(home/'CASE-COMPLETED.json',v)
            elif kind=='false-empty-sentinel':
                v=read(home/'returned.json');v['CellSign_active_interactions']=dict(kind='dataframe',index=[],columns=[],dtypes=[],column_data=[]);save(home/'returned.json',v);v=read(home/'CASE-COMPLETED.json');v['non_table_states']['CellSign_active_interactions']='original-empty-dataframe';save(home/'CASE-COMPLETED.json',v)
            elif kind=='coherent-tsv-pin':
                v=read(home/'CASE-COMPLETED.json');table=v['tables']['means_result'];file=home/'original'/table['file'];lines=file.read_text(encoding='utf8').splitlines();cells=lines[1].split('\t');cells[-1]='999';lines[1]='\t'.join(cells);file.write_text('\n'.join(lines)+'\n',encoding='utf8');table['pin']=pin(file);save(home/'CASE-COMPLETED.json',v)
            else:raise ValueError(kind)
        simple=original['analyses'][0]['id'];statistical='statistical-original100-permutations';scoring='simple-expression-specificity-scores'
        for kind,case,marker in [('mapping',simple,'mapped float32 compensated means'),('axis',simple,'source prefilters column order'),('observed',simple,'interaction means'),('null-bit',statistical,'actual permutation-tail P values'),('missing-family',scoring,'complete requested original method families'),('false-empty-sentinel',statistical,'class sentinel remains distinct'),('coherent-tsv-pin',simple,'complete TSV float dtype round trip')]:
            home=out/kind;home.mkdir();copy=home/'copied-originals'/case;shutil.copytree(control/'candidate'/case,copy);mutate(kind,copy);request=json.loads(json.dumps(original));request['analyses']=[next(a for a in request['analyses'] if a['id']==case)];request.update(mode='RUN',runtime='unused-runtime.json',out='unused-output')
            for analysis in request['analyses']:
                for field in ['counts','metadata','database','degs','microenvironments','active_tfs']:
                    if field in analysis:analysis[field]['path']=str(ROOT/Path(analysis[field]['path']).relative_to('/mnt/d/projects/JLinAlg'))
            save(home/'request.json',request);command=[str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.CellPhoneDbWorkflowAuditMatrix',str(home/'request.json'),str(home/'copied-originals'),str(home/'must-not-exist.json')];run=subprocess.run(command,capture_output=True,timeout=120);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);assert run.returncode!=0 and marker in run.stderr.decode('utf8') and not(home/'must-not-exist.json').exists(),kind+' was admitted or rejected by the wrong gate';record=dict(kind=kind,exit_code=run.returncode,expected_gate=marker);save(home/'COMPLETED.json',record);state['checks'].append(record)
        assert all(pin(ROOT/n)==want for n,want in bound.items());state.update(status='PASS_SEVEN_COHERENT_CELLPHONEDB_AUDIT_CORRUPTION_CONTROLS_ONLY',artifact=built['artifact'],frozen_controls_unchanged=True)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':raise SystemExit(main())
