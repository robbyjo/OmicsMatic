"""Run six complete native response captures, original comparisons, replays and three-host verification."""
from pathlib import Path
import argparse,json,math,shutil,subprocess
from build import ROOT,pin,save
from packaged import mount

def number(value):
    # Original htest values can retain a1x1 matrix wrapper in jsonlite output.
    # Refuse every other shape rather than flattening or dropping values.
    while isinstance(value,list):
        assert len(value)==1,'Expected exactly one original scalar'
        value=value[0]
    assert isinstance(value,(int,float)) and not isinstance(value,bool)
    return float(value)

def close(wanted,actual,where):
    wanted=number(wanted);actual=number(actual)
    assert math.isfinite(actual) and abs(actual-wanted)<=1e-10+1e-11*abs(wanted),where

def table(path,ids,expected):
    rows=path.read_text(encoding='utf-8').splitlines();assert len(rows)==len(expected)+1
    for label,values,line in zip(ids,expected,rows[1:],strict=True):
        fields=line.split('\t');assert fields[0]==label and len(fields)==len(values)+1
        for wanted,value in zip(values,fields[1:],strict=True):
            got=float(value);assert abs(got-wanted)<=8*math.ulp(wanted)+2e-15

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',type=Path,required=True);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    build=args.build.resolve();normal=json.loads((build/'RESULT.json').read_bytes());assert normal['status']=='PASS_NORMAL_JAR_MONOCLE_RESPONSE_AND_STAGE_CONTROLS'
    jar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(jar)==normal['producer']
    reference=args.reference.resolve();original=json.loads((reference/'RESULT.json').read_bytes());assert original['status']=='PASS_ORIGINAL_MONOCLE_RESPONSE_BRANCH_AND_SPDEP_CONTROLS' and len(original['cases'])==6
    out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,out/'EXECUTED-examples.py')
    prefix=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--'];java=prefix+['java','-Xmx2g','-jar',mount(jar)];cli=java+['monocle-tools','RESPONSES-1.0.0'];cases={};total_responses=total_tests=total_fields=refusals=0
    def run(folder,label,command,expected=0):
        save(folder/(label+'.STARTED.json'),dict(command=command,expected_exit=expected))
        with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=240)
        row=dict(command=command,exit_code=p.returncode,expected_exit=expected);save(folder/(label+'.PROCESS.json'),row);assert p.returncode==expected,str(folder/label);return row
    for name,record in original['cases'].items():
        folder=out/name;folder.mkdir();work=folder/'work';work.mkdir();logs=folder/'commands';logs.mkdir()
        request=dict(schema_version=1,transform=record['transform'],expression='expression.tsv',input_provenance='Complete original-source synthetic control '+name,population_declaration='All supplied literal cells and genes, with explicitly supplied metadata/design.',out='preparation')
        for filename in ['expression.tsv','weights.tsv','original-design.tsv']:
            shutil.copyfile(reference/name/filename,work/filename)
        if record['size_factors'] is not None:shutil.copyfile(reference/name/'size_factors.tsv',work/'size_factors.tsv');request['size_factors']='size_factors.tsv'
        if record['detected_genes'] is not None:
            shutil.copyfile(reference/name/'detected_genes.tsv',work/'detected_genes.tsv');request.update(detected_genes='detected_genes.tsv',detected_genes_provenance='Explicit pre-filter nFeature_RNA; never recomputed from the selected matrix.')
        if record['base_design'] is not None:
            shutil.copyfile(reference/name/'base_design.tsv',work/'base_design.tsv');request.update(base_design='base_design.tsv',design_provenance='Explicit supplied intercept, batch contrasts, sex contrast and numeric PMI.')
        save(work/'preparation-request.json',request);commands={};capture=work/'preparation'
        commands['prepare']=run(logs,'prepare',cli+['RUN','--request',mount(work/'preparation-request.json')])
        analysis=json.loads((capture/'analysis.json').read_bytes());assert analysis['response_preparation_admitted'] and not analysis['scientific_test_admitted']
        table(capture/'responses.tsv',record['gene_ids'],record['expected_responses']);assert (capture/'responses.tsv').read_text(encoding='utf-8').splitlines()[0].split('\t')[1:]==record['cell_ids']
        total_responses+=sum(len(row) for row in record['expected_responses'])
        if record['expected_log_n_genes'] is not None:table(capture/'log_n_genes.tsv',record['cell_ids'],[[x] for x in record['expected_log_n_genes']])
        if record['base_design'] is not None:table(capture/'design.tsv',record['cell_ids'],record['expected_design'])
        parent={p.relative_to(capture).as_posix():pin(p) for p in capture.rglob('*') if p.is_file()}
        commands['replay']=run(logs,'replay',cli+['REPLAY','--capture',mount(capture),'--out',mount(work/'replayed')]);replay=json.loads((work/'replayed/replay.json').read_bytes());assert replay['parent_preserved'] and replay['complete_response_design_identical']
        commands['linux21']=run(logs,'linux21',cli+['VERIFY','--capture',mount(capture),'--out',mount(work/'verified-linux21')])
        for host,executable in [('windows21','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'),('windows25','C:/Program Files/Java/jdk-25/bin/java.exe')]:
            commands[host]=run(logs,host,[executable,'-Xmx2g','-jar',str(jar),'monocle-tools','RESPONSES-1.0.0','VERIFY','--capture',str(capture),'--out',str(work/('verified-'+host))])
        assert parent=={p.relative_to(capture).as_posix():pin(p) for p in capture.rglob('*') if p.is_file()}
        tests=fields=0
        for alternative,expected in record['tests'].items():
            result_path=work/('moran-'+alternative)
            model=dict(schema_version=1,design='preparation/design.tsv' if record['base_design'] is not None else 'original-design.tsv',weights='weights.tsv',expression='preparation/responses.tsv',response_units='prepared-linear-response',response_provenance='Native response preparation compared to exact original-source arithmetic.',population_declaration='Complete original synthetic control '+name,zero_policy=True,alternative=alternative,out=result_path.name)
            save(work/('moran-'+alternative+'.json'),model)
            command=java+['monocle-tools','MORAN-LM','--request',mount(work/('moran-'+alternative+'.json'))]
            commands['moran-'+alternative]=run(logs,'moran-'+alternative,command,2 if name=='asis-plain' else 0)
            if name=='asis-plain':
                assert len(record['expected_responses'])==4
                assert all(abs(y-(-4+48/7*row[1]))<=4e-15 for y,row in zip(record['expected_responses'][3],record['expected_design'],strict=True))
                assert not json.loads((result_path/'analysis.json').read_bytes())['result_admitted'] and not (result_path/'COMPLETED.json').exists() and not (result_path/'tests.json').exists()
                assert 'Gene gene-4: Residual variation is zero or unresolved at floating-point precision' in (logs/('moran-'+alternative+'.stderr')).read_text();refusals+=1;continue
            actual=json.loads((result_path/'tests.json').read_bytes());assert len(actual)==len(expected)
            for wanted,got in zip(expected,actual,strict=True):
                assert wanted['gene_id']==got['gene_id']
                for field in ['morans_I','expectation','variance','z','p_value','log_p_value','q_value']:close(wanted[field],got[field],(name,alternative,wanted['gene_id'],field));fields+=1
                assert (number(wanted['p_value'])==0)==(got['p_value']==0);tests+=1
        total_tests+=tests;total_fields+=fields
        row=dict(status='PASS_COMPLETE_NATIVE_RESPONSE_CASE',commands=commands,cells=len(record['cell_ids']),genes=len(record['gene_ids']),parent_preserved=True,replay_complete_outputs_identical=True,verify_hosts=['windows21','windows25','linux21'],original_gene_tests_compared=tests,original_fields_compared=fields,original_R_finite_but_native_degenerate_family_refused=name=='asis-plain')
        save(folder/'RESULT.json',row);cases[name]=row;print('PASS',name,tests,'original gene tests;complete response replay/three-host VERIFY',flush=True)
    assert (total_responses,total_tests,total_fields,refusals)==(984,54,378,3)
    # Also exercise the public copier and documented requests through the normal JAR.
    public=out/'public';public.mkdir();work=public/'work'
    run(public,'copy',prefix+['python3','-B',mount(ROOT/'examples/monocle-responses/prepare.py'),'--out',mount(work)])
    run(public,'preparation',cli+['RUN','--request',mount(work/'preparation-request.json')]);run(public,'moran',java+['monocle-tools','MORAN-LM','--request',mount(work/'moran-request.json')])
    assert json.loads((work/'statistics/analysis.json').read_bytes())['result_admitted']
    save(out/'RESULT.json',dict(status='PASS6_COMPLETE_RESPONSE_CASES_REPLAYS_THREE_HOST_VERIFY_AND_ORIGINAL_INFERENCE_POLICY',producer=pin(jar),cases=cases,response_values_compared=total_responses,admitted_original_gene_tests_compared=total_tests,original_fields_compared=total_fields,retained_original_R_finite_native_degenerate_family_refusals=refusals,original_source_gene_tests_retained=66,public_documented_example_pass=True,R_runtime_required_for_native_operations=False,new_catalog_credit=0,whole_goal_complete=False));return 0

if __name__=='__main__':raise SystemExit(main())
