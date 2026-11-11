# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check complete original full-method reference files and exact raw-preparation parity on three actual JVMs; no execution-adapter/tool credit."""
import argparse,csv,io,json,math,os,re,struct,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux
from validate_cellphonedb_statistics import compare


def f32(value):
    try:return struct.unpack('f',struct.pack('f',value))[0]
    except OverflowError:return math.copysign(float('inf'),value)


def source_tables(reference):
    marker=json.loads((reference/'COMPLETED.json').read_bytes());assert marker['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_FULL_WORKFLOW_CONTROLS_ONLY' and marker['runtime_unchanged'];completed=json.loads((reference/'ORIGINAL-COMPLETED.json').read_bytes());assert completed['cases']==19 and completed['successful_workflows']==13 and completed['expected_source_errors']==6 and completed['original_namespace_patched'] is False
    expected=dict(cases=[]);request=json.loads((reference/'native-input.json').read_bytes());results=json.loads((reference/'original-results.json').read_bytes());checks=dict(successful_original_workflows=0,expected_original_errors=0,complete_TSV_tables=0,complete_TSV_cells=0,float32_TSV_cells=0,original_class_sentinels=0,scoring_workflows=0,CellSign_workflows=0,subsampling_workflows=0,prepared_cases=0)
    def clean_number(value):return 'NaN' if math.isnan(value) else '+Inf' if value==math.inf else '-Inf' if value==-math.inf else value
    for case in results['cases']:
        home=reference/case['id'];record=json.loads((home/'CASE-COMPLETED.json').read_bytes());assert record==case and record['scientific_result_admitted'] is False
        if case['status'].startswith('FAIL2_'):
            checks['expected_original_errors']+=1;assert (home/'original-error.traceback').read_bytes() and case['exception_type'] in ['InvalidIndexError','TypeError','AllCountsFilteredException','ParseCountsException','ValueError'];continue
        assert case['status']=='PASS_COMPLETE_ORIGINAL_FULL_METHOD_REFERENCE_ONLY';checks['successful_original_workflows']+=1;checks['scoring_workflows']+=case['scoring_executed'];checks['CellSign_workflows']+=case['CellSign_requested'];checks['subsampling_workflows']+=case['subsampling_requested'];returned=json.loads((home/'original-returned.json').read_bytes());covered=set(case['returned_tables'])|set(case['returned_non_table_states']);assert covered<=set(returned)
        for name in set(returned)-covered:assert set(returned[name])=={'$dataframe'} and returned[name]['$dataframe']['values']==[],'Uncaptured nonempty returned family'
        for name,state in case['returned_non_table_states'].items():assert name.startswith('CellSign_active_interactions') and state['state']=='original-pandas-DataFrame-class-sentinel-not-a-dataframe-or-result' and returned[name]=={'$python_class':'pandas.core.frame.DataFrame'};checks['original_class_sentinels']+=1
        for name,table in case['returned_tables'].items():
            path=home/'original-output'/table['file'];assert pin(path)==table['pin'];frame=returned[name]['$dataframe'];rows=list(csv.reader(io.StringIO(path.read_bytes().decode('utf8')),delimiter='\t'));assert rows[0]==frame['columns']==table['columns'] and len(rows)-1==table['rows']==len(frame['values']);assert len(frame['dtypes'])==len(rows[0]);checks['complete_TSV_tables']+=1
            for raw,values in zip(rows[1:],frame['values'],strict=True):
                assert len(raw)==len(values)==len(frame['dtypes'])
                for text,value,dtype in zip(raw,values,frame['dtypes'],strict=True):
                    checks['complete_TSV_cells']+=1
                    if dtype in ['float32','float64']:
                        actual=float(text) if text else math.nan
                        if dtype=='float32':actual=f32(actual);checks['float32_TSV_cells']+=1
                        assert clean_number(actual)==value,(case['id'],name,dtype,text,value)
                    elif dtype.startswith(('int','uint')):assert int(text)==value
                    elif dtype=='bool' or isinstance(value,bool):assert text in ['True','False'] and (text=='True') is value
                    else:assert text==value or text=='' and value in [None,'NaN'],(case['id'],name,dtype,text,value)
        for name,want in case['files'].items():assert pin(home/'original-output'/name)==want
    for case in request['cases']:
        if 'expected_original_error' in case:continue
        preparation=json.loads((reference/case['id']/'preparation.json').read_bytes());expected['cases'].append(dict(id=case['id'],preparation=preparation));checks['prepared_cases']+=1
        columns={name:i for i,name in enumerate(case['raw_cells'])};metadata=dict(zip(case['meta_cells'],case['meta_cell_types'],strict=True));retained=[c for c in case['raw_cells'] if c in metadata];assert preparation['preprocessed_cells']==retained and preparation['cells']==sorted(retained) and preparation['aligned_cell_types']==[metadata[c] for c in sorted(retained)];assert preparation['preprocessed_expression']==[[f32(row[columns[c]]) for c in retained] for row in case['raw_expression']]
        if case['id']=='simple-duplicate-raw-gene-rows-compensated-mean':assert preparation['mapped_expression'][preparation['mapped_features'].index(102)]==[5592406.]*len(retained),'Original pandas compensated mean control changed'
    assert checks['successful_original_workflows']==13 and checks['expected_original_errors']==6 and checks['prepared_cases']==15 and checks['scoring_workflows']==3 and checks['CellSign_workflows']==1 and checks['subsampling_workflows']==1;return request,expected,checks


def verify_ledgers(request,actual):
    checks=dict(gene_rows=0,unmapped_gene_rows=0,relations=0,removed_cells=0,cast_changes=0,underflow_to_zero=0)
    for case in request['cases']:
        if 'expected_original_error' in case:continue
        ledger=actual[case['id']];assert ledger['removed_cells']==[name for name in case['raw_cells'] if name not in case['meta_cells']];checks['removed_cells']+=len(ledger['removed_cells']);relations=[];genes=[]
        for i,gene in enumerate(case['raw_genes']):
            ids=[]
            for j,row in enumerate(case['gene_rows']):
                if row[case['counts_data']]==gene:ids.append(row['id_multidata']);relations.append(dict(source_row=i,gene_row=j))
            genes.append(dict(source_row=i,original_gene=gene,mapped_ids=ids));checks['gene_rows']+=1;checks['unmapped_gene_rows']+=not ids
        assert ledger['gene_ledger']==genes and ledger['relation_rows']==relations;checks['relations']+=len(relations);cast=[]
        for i,row in enumerate(case['raw_expression']):
            for j,value in enumerate(row):
                converted=f32(value)
                if struct.pack('d',value)!=struct.pack('d',converted):cast.append(dict(source_row=i,cell_column=j,original=value,source_float32=converted));checks['underflow_to_zero']+=converted==0 and value>0
        assert ledger['casting_ledger']==cast;checks['cast_changes']+=len(cast);assert any('synonym substitution is disabled' in w for w in ledger['warnings'])
        if case['id']=='simple-many-to-one-distinct-alias-annotation-source-error':assert any('duplicate annotation indices' in w for w in ledger['warnings'])
        if case['id']=='simple-no-complete-complex-gene-name-source-error':assert any('No complete complex remains with gene_name' in w for w in ledger['warnings'])
    return checks


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='FAIL2_CELLPHONEDB_PREPARATION_AUDIT_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);errors=[]
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_CELLPHONEDB_PREPARATION_ONLY_NOT_SCIENTIFIC_VALIDATION';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};sources=dict(build['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));assert all(pin(ROOT/n)==want for n,want in sources.items());request,expected,original_checks=source_tables(a.reference);save(out/'cases.json',request);save(out/'expected.json',expected);env=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
        jvms=dict(windows21=[str(JAVA21/'java.exe')],windows25=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],linux21=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']);checks={};versions={};ledgers={}
        for name,java in jvms.items():
            version=subprocess.run([*java,'-version'],cwd=ROOT,env=env,capture_output=True,timeout=60)
            (out/(name+'.version.stdout')).write_bytes(version.stdout);(out/(name+'.version.stderr')).write_bytes(version.stderr)
            versions[name]=(version.stdout+version.stderr).decode('utf8')
            assert version.returncode==0 and re.search(r'version "'+('25' if name=='windows25' else '21')+r'(?:\.|\")',versions[name])
            destination=out/name
            command=[*java,'-Xmx2g','-cp',linux(jar) if name=='linux21' else str(jar),'org.jlinalg.cli.CellPhoneDbPreparationMatrix',linux(out/'cases.json') if name=='linux21' else str(out/'cases.json'),linux(destination) if name=='linux21' else str(destination)]
            run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
            (out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode))
            assert run.returncode==0,'Actual native preparation controls failed; inspect '+name+' stderr'
            report=json.loads((destination/'COMPLETED.json').read_bytes())
            assert report['status']=='PASS_NATIVE_CELLPHONEDB_TYPED_PREPARATION_ONLY' and report['cases']==15 and len(set(report['expected_native_rejections']))==15 and report['defensive_copy_controls']
            assert report['original_full_workflow_error_controls']==[case['id'] for case in request['cases'] if 'expected_original_error' in case]
            count=dict(cases=15,numeric_values=0,exact_values=0,native_rejections=15,operations=report['operations'])
            compare(expected,json.loads((destination/'results.json').read_bytes()),name,errors,count);checks[name]=count
            ledgers[name]=verify_ledgers(request,json.loads((destination/'ledgers.json').read_bytes()));print(name,count,flush=True)
        save(out/'complete-mismatches.json',errors);assert not errors,'Exact original/native mapping/filtering/identity mismatch';assert all(pin(ROOT/n)==want for n,want in sources.items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];state.update(status='PASS_EXACT_ORIGINAL_NATIVE_CELLPHONEDB_PREPARATION_THREE_JVMS_ONLY',artifact=build['artifact'],source_pins=sources,checks=checks,ledger_checks=ledgers,original_checks=original_checks,jvm_versions=versions,reference_manifest=pin(a.reference/'COMPLETED.json'),canonical_executable_unchanged=build['canonical_executable_unchanged'],exact_finite_numeric_comparison=True,original_full_methods_benchmarked=True,public_execution_adapter_admitted=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');save(out/'complete-mismatches.json',errors)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Typed preparation parity and full original reference controls only; public typed execution adapter, independent full-output audits and biological claims remain open.')+'\n\nSKIP3: skip dependent analysis when required installation/input is unavailable or forbidden. FAIL2: any unexpected source/native/state mismatch admits no partial batch. Preserve original aliases, float32 casts, failed directories and errors; correct the named cause and retry NEW paths. Do not patch original code, discard aliases to admit a failing analysis, silently repair IDs or widen numeric comparisons.\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/native preparation checks')
    raise SystemExit(main())
