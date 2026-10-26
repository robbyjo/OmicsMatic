"""Self-contained real executable-JAR checks against frozen original R imports."""
import argparse
import csv
import datetime as dt
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import traceback
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--report',required=True,type=Path);args=parser.parse_args()
    sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
    jar=args.jar.resolve();report=args.report.resolve();folder=ROOT/'build/literature-review/tximport-runtime'/('packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));folder.mkdir(parents=True)
    steps=[];checks=[];result=dict(status='running',jar=str(jar),jar_sha256=sha(jar),verifier_sha256=sha(Path(__file__)),fixture_directory=str(folder),steps=steps,checks=checks)
    java=shutil.which('java');assert java,'Java executable missing'
    def write(path,header,rows):
        with path.open('w',encoding='utf-8',newline='') as stream:
            writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(header);writer.writerows(rows)
    def table(path):
        with path.open(encoding='utf-8',newline='') as stream:return list(csv.reader(stream,delimiter='\t'))
    def run(name,arguments,expected):
        command=[java,'-jar',str(jar),'transcript-import',*map(str,arguments),'--no-config'];process=subprocess.run(command,capture_output=True,text=True,timeout=90)
        log=folder/(name+'.log');log.write_text(process.stdout+process.stderr,encoding='utf-8')
        steps.append(dict(name=name,command=command,exit_code=process.returncode,expected=expected,log_sha256=sha(log)))
        assert process.returncode==expected,(name,process.returncode,process.stdout,process.stderr)
        return process.stdout+process.stderr
    def inputs(case,name):
        base=folder/name;base.mkdir();ids=case['transcripts'].split(',');samples=case['samples'].split(',');genes=case['genes'].split(',');n=len(samples)
        values={key:[float(v) for v in case[key].split(',')] for key in ['counts','abundance','length']}
        mapping=[]
        for id_,gene in zip(ids,genes):
            key=id_.split('.')[0] if case['policy']=='strip-version' else id_.split('|')[0] if case['policy']=='strip-bar' else id_
            if gene:mapping.append([key,gene])
        write(base/'map.tsv',['transcript','gene'],mapping)
        files=[]
        for column,sample in enumerate(samples):
            rows=[]
            for i,id_ in enumerate(ids):
                c,a,l=[values[key][i*n+column] for key in ['counts','abundance','length']]
                rows.append([id_,5000,l,a,c] if case['format']=='salmon' else [id_,5000,l,c,a] if case['format']=='kallisto' else [id_,genes[i] or 'unmapped',5000,l,c,a,0,0] if case['format']=='rsem' else [id_,c,a,l])
            header={'salmon':['Name','Length','EffectiveLength','TPM','NumReads'],'kallisto':['target_id','length','eff_length','est_counts','tpm'],'rsem':['transcript_id','gene_id','length','effective_length','expected_count','TPM','FPKM','IsoPct'],'tsv':['transcript','counts','abundance','length']}[case['format']]
            path=base/('sample'+str(column)+'.tsv');write(path,header,rows)
            if column==0:path.with_suffix('.tsv.gz').write_bytes(gzip.compress(path.read_bytes(),mtime=0));path=path.with_suffix('.tsv.gz')
            files.append(path)
        write(base/'samples.tsv',['sample','path'],[[sample,path.name] for sample,path in zip(samples,files)])
        # CRLF manifest is intentional; IDs, including numeric strings, stay literal.
        manifest=base/'samples.tsv';manifest.write_bytes(manifest.read_bytes().replace(b'\n',b'\r\n'))
        output=base/'output';arguments=['--samples',manifest,'--format',case['format'],'--level',case['level'],'--counts-from-abundance',case['mode'],'--id-policy',case['policy'],'--transcriptome-id','frozen-synthetic-transcriptome-v1','--inferential-replicates','drop','--out',output]
        if case['level']=='gene' or case['mode']=='dtuScaledTPM':arguments+=['--tx2gene',base/'map.tsv']
        if case['level']=='gene':arguments+=['--unmapped',case['unmapped']]
        return base,output,arguments
    try:
        reference=ROOT/'src/test/resources/tximport/reference.tsv';provenance=json.loads((reference.parent/'reference-provenance.json').read_text());assert sha(reference)==provenance['reference_sha256']
        with reference.open(encoding='utf-8',newline='') as stream:cases=list(csv.DictReader(stream,delimiter='\t'))
        selected=[case for case in cases if int(case['case'])<84 and case['policy']=='exact']
        selected += [cases[number] for number in [7,14,28,35,85,95,105,115]]
        scalars=0;bundles=[]
        for case in selected:
            name='original-'+case['case'];base,output,arguments=inputs(case,name);sources={str(path):sha(path) for path in base.iterdir() if path.is_file()}
            console=run(name,arguments,0);assert 'ANALYSIS WARNINGS' in console
            samples=case['samples'].split(',');features=case['expected_features'].split(',');n=len(samples)
            for kind,file in [('counts','results.tsv'),('abundance','abundance.tsv'),('length','length.tsv')]:
                rows=table(output/file);assert rows[0]==['feature',*samples];actual={row[0]:list(map(float,row[1:])) for row in rows[1:]};assert set(actual)==set(features)
                expected=list(map(float,case['expected_'+kind].split(',')))
                for i,feature in enumerate(features):
                    for j in range(n):assert math.isclose(actual[feature][j],expected[i*n+j],rel_tol=2e-12,abs_tol=1e-10),(name,kind,feature,j,actual[feature][j],expected[i*n+j]);scalars+=1
            statuses=table(output/'transcript-status.tsv');assert len(statuses)==len(case['transcripts'].split(','))+1
            warning=(output/'WARNINGS.md').read_text();assert 'POINT ESTIMATES ONLY' in warning and 'REFERENCE/ID ACTION' in warning
            assert ('LENGTH OFFSET ACTION' if case['mode']=='no' else 'NO DOUBLE LENGTH CORRECTION') in warning
            for path,digest in sources.items():assert sha(Path(path))==digest,path
            assert json.loads((output/'analysis-status.json').read_text())['status']=='completed-with-warnings'
            bundles.append(dict(case=case['case'],source_sha256=sources,output_sha256={path.name:sha(path) for path in output.iterdir() if path.is_file()}))
        checks.append(dict(name='original-R-import-values',cases=len(selected),scalar_values=scalars))
        # Independent two-isoform, three-sample zero-expression length arithmetic.
        synthetic=dict(cases[0]);synthetic.update(format='tsv',level='gene',mode='no',policy='exact',unmapped='error',transcripts='a,b,c',samples='1,B,C',genes='A,A,Z',counts='1.5,0,2,2.5,0,4,0,0,0',abundance='1,0,3,3,0,1,0,0,0',length='10,20,30,30,40,50,100,200,300')
        base,output,arguments=inputs(synthetic,'hand');run('hand',arguments,0);rows=table(output/'length.tsv');byid={row[0]:list(map(float,row[1:])) for row in rows[1:]};assert all(math.isclose(a,b,rel_tol=1e-13) for a,b in zip(byid['A'],[25,math.sqrt(875),35]));assert byid['Z']==[200,200,200];assert len(table(output/'length-fallbacks.tsv'))==5;checks.append(dict(name='independent-zero-expression-fallbacks',fallbacks=4))
        # Failure cases create durable warnings and no successful table.
        failures=[]
        for name in ['missing-file','conflicting-map','changed-order','zero-scaling','small-cells','expanded-bytes','id-collision']:
            base,output,arguments=inputs(synthetic,name)
            if name=='missing-file':(base/'sample0.tsv.gz').unlink()
            elif name=='conflicting-map':(base/'map.tsv').write_text('transcript\tgene\na\tA\na\tB\nb\tA\nc\tZ\n')
            elif name=='changed-order':
                path=base/'sample1.tsv';rows=table(path);write(path,rows[0],list(reversed(rows[1:])))
            elif name=='zero-scaling':
                arguments[arguments.index('--counts-from-abundance')+1]='scaledTPM'
                for path in base.glob('sample[0-9]*.tsv'):
                    rows=table(path)
                    for row in rows[1:]:row[1]='0';row[2]='0'
                    write(path,rows[0],rows[1:])
                (base/'sample0.tsv.gz').write_bytes(gzip.compress((base/'sample0.tsv').read_bytes(),mtime=0))
            elif name=='small-cells':arguments+=['--max-cells','2']
            elif name=='expanded-bytes':
                path=base/'sample0.tsv.gz';path.write_bytes(gzip.compress(('transcript\tcounts\tabundance\tlength\n'+'a\t1\t1\t10\n'*100).encode(),mtime=0));arguments+=['--max-bytes','256']
            else:
                arguments[arguments.index('--id-policy')+1]='strip-version'
                for path in base.glob('sample[0-9]*.tsv'):
                    rows=table(path);rows[1][0]='a.1';rows[2][0]='a.2';write(path,rows[0],rows[1:])
                (base/'sample0.tsv.gz').write_bytes(gzip.compress((base/'sample0.tsv').read_bytes(),mtime=0))
            console=run(name,arguments,2);assert 'ANALYSIS FAILED' in console;assert not (output/'results.tsv').exists();assert 'Required action' in (output/'WARNINGS.md').read_text();failures.append(name)
        checks.append(dict(name='actionable-retained-failures',cases=failures))
        # Confirm executable distribution contains the engine/reference attribution.
        with zipfile.ZipFile(jar) as archive:assert {'org/jlinalg/sequence/TranscriptImport.class','org/jlinalg/cli/TranscriptImportCli.class','META-INF/NOTICE-tximport.txt'}<=set(archive.namelist())
        checks.append(dict(name='native-executable-distribution',external_runtime_required=False))
        result.update(status='passed-native-point-estimate-subset',original_R_cases=len(selected),original_R_scalar_values=scalars,bundles=bundles,check_count=len(checks))
    except Exception as failure:result.update(status='failed',error=str(failure),traceback=traceback.format_exc());raise
    finally:
        result['finished_utc']=dt.datetime.now(dt.timezone.utc).isoformat();report.parent.mkdir(parents=True,exist_ok=True);report.write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(dict(status=result['status'],workflow_calls=len(steps),checks=len(checks),report=str(report))))


if __name__=='__main__':main()
