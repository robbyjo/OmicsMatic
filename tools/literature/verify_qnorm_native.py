"""Actual executable-JAR finite column normalization; no qnorm runtime needed."""
import argparse
import csv
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import traceback
import zipfile

ROOT=Path(__file__).resolve().parents[2]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args();jar=args.jar.resolve();report=args.report.resolve()
    folder=ROOT/'build/literature-review'/('qnorm-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'))
    folder.mkdir(parents=True);steps=[];checks=[];original_positions=0
    sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
    result=dict(status='running',jar=str(jar),jar_sha256=sha(jar),steps=steps,checks=checks,fixture_directory=str(folder),verifier_sha256=sha(Path(__file__)))
    java=shutil.which('java')
    assert java,'Java executable is missing'
    def run(name,arguments,expected):
        command=[java,'-jar',str(jar),'quantile-normalize',*map(str,arguments),'--no-config']
        process=subprocess.run(command,capture_output=True,text=True,timeout=90)
        (folder/(name+'.log')).write_text(process.stdout+process.stderr,encoding='utf-8')
        steps.append(dict(name=name,command=command,exit_code=process.returncode,expected=expected))
        assert process.returncode==expected,(name,process.returncode,process.stdout,process.stderr)
        return process.stdout+process.stderr
    def base(matrix,output,columns=('A','B'),kind='continuous'):
        return ['--matrix',matrix,'--id-column','feature','--columns',','.join(columns),'--axis','columns','--input-kind',kind,'--out',output]
    try:
        reference=ROOT/'src/test/resources/qnorm/column-reference.tsv'
        provenance=json.loads((reference.parent/'reference-provenance.json').read_text())
        assert sha(reference)==provenance['reference_sha256']
        with reference.open(encoding='utf-8',newline='') as stream:cases=list(csv.DictReader(stream,delimiter='\t'))
        for number in [0,1,2,3,4,5,10,55]:
            case=cases[number];rows,columns=int(case['rows']),int(case['columns']);values=[float(value) for value in case['input_row_major'].split(',')];expected=[float(value) for value in case['expected_row_major'].split(',')]
            names=['sample'+str(column) for column in range(columns)]
            matrix=folder/('case'+str(number)+'.tsv')
            with matrix.open('w',encoding='utf-8',newline='') as stream:
                writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(['feature',*names])
                for row in range(rows):writer.writerow(['00'+str(row)+'.v1',*[format(value,'.17g') for value in values[row*columns:(row+1)*columns]]])
            source=sha(matrix);output=folder/('result'+str(number));arguments=base(matrix,output,tuple(reversed(names)))
            if case['target']!='default':
                target=folder/('target'+str(number)+'.tsv');target.write_text('value\n'+'\n'.join(case['target'].split(','))+'\n');arguments+=['--target',target,'--target-column','value']
            console=run('original-'+str(number),arguments,0);assert 'ANALYSIS WARNINGS' in console
            with (output/'results.tsv').open(newline='',encoding='utf-8') as stream:actual=list(csv.reader(stream,delimiter='\t'))
            assert actual[0]==['feature',*reversed(names)] and len(actual)==rows+1
            for row,fields in enumerate(actual[1:]):
                assert fields[0]=='00'+str(row)+'.v1'
                for offset,column in enumerate(reversed(range(columns))):
                    wanted=expected[row*columns+column];observed=float(fields[offset+1]);assert math.isfinite(observed) and math.isclose(observed,wanted,rel_tol=1e-12,abs_tol=1e-12),(number,row,column,wanted,observed);original_positions+=1
            assert source==sha(matrix)
            assert json.loads((output/'analysis-status.json').read_text())['status']=='completed-with-warnings'
        checks.append('Eight actual original-function fixtures, reversed sample selection, exact versioned/leading-zero IDs and immutable input')
        for name,value,kind in [('nan','NaN','continuous'),('missing','NA','continuous'),('fraction','1.5','raw-counts'),('negative','-1','estimated-counts'),('subtle-fraction','1.0000000000000001','raw-counts'),('unsafe-integer','9007199254740993','raw-counts')]:
            matrix=folder/(name+'.tsv');matrix.write_text('feature\tA\tB\nf\t'+value+'\t2\n');output=folder/name
            run(name,base(matrix,output,kind=kind),2)
            assert not (output/'results.tsv').exists() and (output/'WARNINGS.md').is_file()
            assert json.loads((output/'analysis-status.json').read_text())['status']=='failed'
        checks.append('Nonfinite/missing/negative/fractional/unsafe integer inputs fail, including decimal text that would otherwise round silently to an integer double')
        matrix=folder/'fraction.tsv';output=folder/'estimated';run('estimate',base(matrix,output,kind='estimated-counts'),0)
        assert 'not raw counts' in (output/'WARNINGS.md').read_text()
        target=folder/'short-target.tsv';target.write_text('value\n1\n2\n');output=folder/'bad-target';run('target-length',base(matrix,output)+['--target',target,'--target-column','value'],2)
        assert 'Target length' in (output/'WARNINGS.md').read_text() and not (output/'results.tsv').exists()
        duplicate=folder/'duplicate.tsv';duplicate.write_text('feature\tA\tB\n001\t1\t2\n001\t2\t1\n');output=folder/'duplicate';run('duplicate-ids',base(duplicate,output),2);assert 'Duplicate row ID' in (output/'WARNINGS.md').read_text()
        checks.append('Estimated-count units remain explicit; wrong target shape and duplicate IDs require correction')
        output=folder/'limited';run('resource-limit',base(duplicate,output)+['--max-cells','1'],2);assert 'max-cells' in (output/'WARNINGS.md').read_text()
        arguments=base(matrix,folder/'row-axis');arguments[arguments.index('columns')]='rows';assert 'axis0 orientation' in run('unsupported-axis',arguments,2)
        assert 'No imputation' in run('native-help',['--help'],0)
        checks.append('Explicit orientation/resource limits and user-facing native scope')
        with zipfile.ZipFile(jar) as archive:
            for name in ['LICENSE-qnorm.txt','NOTICE-qnorm.txt']:
                assert archive.read('META-INF/'+name)==(ROOT/'src/main/resources/META-INF'/name).read_bytes()
        checks.append('Original MIT license and attribution packaged in the executable')
        result['status']='passed-native-subset'
    except Exception:
        result.update(status='failed',error=traceback.format_exc())
    result.update(check_count=len(checks),original_function_positions=original_positions,recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),scope='Actual native Java column-wise finite-double API/CLI subset. No Python runtime was called by the Java analyses. No axis0,dtype32,pandas,incremental,multiprocessing,bitwise or whole-paper parity.')
    report.parent.mkdir(parents=True,exist_ok=True)
    if report.exists():
        old=json.loads(report.read_text());result['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
    return 0 if result['status']=='passed-native-subset' else 1

if __name__=='__main__':
    sys.exit(main())
