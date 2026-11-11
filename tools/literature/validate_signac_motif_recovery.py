# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject rehashed semantic changes to native motif captures and preserve failed/existing outputs."""
import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
from validate_mashr_native import JAVA21,linux,pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacMotifsRequestCli'

def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def inventory(path):return {p.relative_to(path).as_posix():pin(p) for p in path.rglob('*') if p.is_file()}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-motif-corruption-and-failure-gates-in-progress'))
    try:
        build=read(BASE/'motif-workflow-build-1/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};main=BASE/'motif-workflow-1';original=main/'Unicode β analyses/000-1.6.0-source-explicit-BH';assert read(original/'analysis-status.json')['status']=='completed-with-warnings';sources={'tools/literature/validate_signac_motif_recovery.py':pin(Path(__file__))};save(out/'SOURCE-PINS.json',sources);target=out/'source-snapshot/tools/literature';target.mkdir(parents=True);shutil.copyfile(Path(__file__),target/Path(__file__).name)
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1400:])
        def verify(name,path,code):
            for platform,java,convert in platforms:
                request=out/(name+'-'+platform+'.json');save(request,dict(schema_version=1,mode='VERIFY',analysis=convert(path)));command(name+'-'+platform,[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(request)],code)
        def seal(path):
            target=path/'artifact-checksums.tsv';target.write_text('sha256\tpath\n'+''.join(pin(p)['sha256']+'\t'+p.relative_to(path).as_posix()+'\n' for p in sorted(path.rglob('*')) if p.is_file() and p!=target),encoding='utf8',newline='\n')
        baseline=out/'copied-baseline';shutil.copytree(original,baseline);verify('copied-baseline',baseline,0);request=read(baseline/'request.json');mutations=[('result-count','enrichment.tsv'),('result-pvalue','enrichment.tsv'),('result-order','enrichment.tsv'),('query',request['query']),('background',request['background']),('matrix',request['motif_matrix']),('motif-name',request['motif_labels']),('request','request.json'),('source-request','source-request.json'),('scope','manifest.json'),('diagnostics','diagnostics.json'),('warnings','WARNINGS.md')];rejections=[]
        for name,file in mutations:
            target=out/('tamper-'+name);shutil.copytree(baseline,target);path=target/file
            if file.endswith('.json'):
                value=read(path)
                if name=='request':value['profile']='1.17.1-package'
                elif name=='source-request':value['maximum_work']=123456
                elif name=='scope':value['sequence_scanning_performed']=True
                else:value['motifs']+=1
                save(path,value)
            elif name=='warnings':path.write_text('All motifs are causal and all p-values are FDR.\n',encoding='utf8',newline='\n')
            else:
                lines=path.read_text(encoding='utf8').splitlines()
                if name in ['result-count','result-pvalue']:
                    fields=lines[1].split('\t');column=1 if name=='result-count' else 6;fields[column]=str(float(fields[column])+0.01);lines[1]='\t'.join(fields)
                elif name=='result-order':lines[1],lines[2]=lines[2],lines[1]
                elif name in ['query','background']:lines[0]=lines[1]
                elif name=='motif-name':fields=lines[1].split('\t');fields[1]='changed TF name';lines[1]='\t'.join(fields)
                elif name=='matrix':index=next(i+1 for i,line in enumerate(lines) if not line.startswith('%'));fields=lines[index].split();fields[2]=str(int(fields[2])+1);lines[index]=' '.join(fields)
                path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            seal(target);verify('reject-'+name,target,2);rejections.append(dict(mutation=name,rejected_platforms=3));print('rejected altered motif '+name,flush=True)
        nested=out/'unlisted-nested-file';shutil.copytree(baseline,nested);(nested/'input/artifact-checksums.tsv').write_text('extra\n',encoding='utf8',newline='\n');verify('reject-unlisted-nested-file',nested,2)
        before=inventory(baseline);existing=copy.deepcopy(read(main/'request-000.json'));existing['out']=str(baseline);path=out/'existing-request.json';save(path,existing);command('existing-destination',[JAVA21/'java.exe','-Xmx512m','-cp',jar,CLI,'--request',path],2);assert inventory(baseline)==before
        failures=[]
        for name in ['missing-input','malformed-selection','work-capacity']:
            value=copy.deepcopy(existing);target=out/name;value['out']=str(target)
            if name=='missing-input':value['motif_matrix']=str(out/'not-installed-or-provided.mtx')
            elif name=='malformed-selection':selection=out/'bad-selection.tsv';selection.write_text('\n',encoding='utf8',newline='\n');value['query']=str(selection)
            else:value['maximum_work']=1
            path=out/(name+'-request.json');save(path,value);command(name,[JAVA21/'java.exe','-Xmx512m','-cp',jar,CLI,'--request',path],2);assert read(target/'analysis-status.json')['status']=='failed' and not (target/'enrichment.tsv').exists();verify('reject-failed-'+name,target,2);failures.append(dict(name=name,no_result_admitted=True))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-native-motif-corruption-and-recovery-controls',source_pins=sources,artifact=build['artifact'],copied_verifications=3,rehashed_rejections=rejections,unlisted_nested_file_rejections=3,existing_destination_preserved=True,retained_failure_gates=failures,failed_capture_rejections=9,native_enrichment_requires_no_R=True,full_Signac_workflow_available=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],rehashed_rejections=36,failure_gates=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_MOTIF_RECOVERY_CONTROLS',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native motif recovery checks')
    main()
