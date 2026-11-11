# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check rehashed native capture corruption, exact failure gates and preservation of existing outputs."""
import argparse,copy,json,shutil,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import JAVA21,linux,pin
CLI='org.jlinalg.cli.ChromVarRequestCli'
def read(p):return json.loads(p.read_bytes())
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-chromVAR-corruption-and-failure-checks-in-progress'))
    try:
        build=read(BASE/'chromvar-workflow-build-1/BUILD.json');jar=ROOT/build['artifact']['path'];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source=BASE/'chromvar-request-probe-1/Unicode β analysis';request=read(BASE/'chromvar-request-probe-1/request.json');names=['tools/literature/validate_chromvar_recovery.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1400:])
        def verify(name,target,code):
            for platform,java,convert in platforms:
                path=out/(name+'-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)],code)
        def reseal(target):
            p=target/'artifact-checksums.tsv';p.write_text('sha256\tpath\n'+''.join(pin(f)['sha256']+'\t'+f.relative_to(target).as_posix()+'\n' for f in sorted(target.rglob('*')) if f.is_file() and f!=p),encoding='utf8',newline='\n')
        baseline=out/'copied-baseline';shutil.copytree(source,baseline);verify('copied-baseline',baseline,0);captured=read(baseline/'request.json');rejections=[]
        mutations=['z-value','deviation-value','expectation-value','annotation-fraction','NA-kind','raw-counts','raw-background','raw-annotation-duplicate','raw-cell-order','request-norm','source-profile','source-function','warning-text','diagnostics','manifest-engine','scope','raw-hash-population','verify-request']
        for name in mutations:
            target=out/('tamper-'+name);shutil.copytree(baseline,target)
            def change(file,column,delta=1):
                p=target/file;lines=p.read_text(encoding='utf8').splitlines();index=lines[0].split('\t').index(column);f=lines[1].split('\t');f[index]=str(float(f[index])+delta);lines[1]='\t'.join(f);p.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            raw_field=None
            if name in ['z-value','deviation-value']:change('deviations.tsv','z' if name=='z-value' else 'deviation')
            elif name=='expectation-value':change('expectations.tsv','expectation',.1)
            elif name=='annotation-fraction':change('motif-summary.tsv','fraction_matches',.1)
            elif name=='NA-kind':
                p=target/'deviations.tsv';text=p.read_text(encoding='utf8');assert '\tNA' in text;p.write_text(text.replace('\tNA','\tNaN',1),encoding='utf8',newline='\n')
            elif name=='raw-counts':
                raw_field='counts';p=target/captured[raw_field];lines=p.read_text(encoding='utf8').splitlines();at=next(i+1 for i,line in enumerate(lines) if not line.startswith('%'));f=lines[at].split();f[2]=str(int(f[2])+1);lines[at]=' '.join(f);p.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name=='raw-background':
                raw_field='background';p=target/captured[raw_field];lines=p.read_text(encoding='utf8').splitlines();f=lines[1].split('\t');f[1]='1' if f[1]!='1' else '2';lines[1]='\t'.join(f);p.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name=='raw-annotation-duplicate':
                raw_field='annotation_peaks';p=target/captured[raw_field];lines=p.read_text(encoding='utf8').splitlines();p.write_text('\n'.join(lines+[lines[1]])+'\n',encoding='utf8',newline='\n')
            elif name=='raw-cell-order':
                raw_field='cells';p=target/captured[raw_field];lines=p.read_text(encoding='utf8').splitlines();lines[0],lines[1]=lines[1],lines[0];p.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            elif name=='request-norm':
                for file in ['source-request.json','request.json']:
                    p=target/file;value=read(p);value['norm']=True;save(p,value)
                value=read(target/'manifest.json');value['source_request_sha256']=pin(target/'source-request.json')['sha256'];save(target/'manifest.json',value)
            elif name=='source-profile':value=read(target/'request.json');value['profile']='1.16.0-source';save(target/'request.json',value)
            elif name=='source-function':
                p=target/'source/1.34.1/compute_deviations_single.R';p.write_bytes(p.read_bytes()+b'\n# altered reference source\n')
            elif name=='warning-text':(target/'WARNINGS.md').write_text('No review is required.\n',encoding='utf8',newline='\n')
            elif name=='diagnostics':value=read(target/'diagnostics.json');value['native_work']+=1;save(target/'diagnostics.json',value)
            elif name=='manifest-engine':value=read(target/'manifest.json');value['engine']='unknown';save(target/'manifest.json',value)
            elif name=='scope':value=read(target/'manifest.json');value['upstream_background_sampling_performed']=True;save(target/'manifest.json',value)
            elif name=='raw-hash-population':value=read(target/'manifest.json');value['captured_input_sha256']['input/not-an-input']='0'*64;save(target/'manifest.json',value)
            elif name=='verify-request':value=read(target/'verify-request.json');value['analysis']='elsewhere';save(target/'verify-request.json',value)
            else:raise AssertionError(name)
            if raw_field:
                value=read(target/'manifest.json');value['captured_input_sha256'][captured[raw_field]]=pin(target/captured[raw_field])['sha256'];save(target/'manifest.json',value)
            reseal(target);verify('reject-'+name,target,2);rejections.append(dict(mutation=name,rejected_platforms=3));print('rejected rehashed native capture '+name,flush=True)
        nested=out/'unlisted-nested-file';shutil.copytree(baseline,nested);(nested/'input/artifact-checksums.tsv').write_text('unlisted\n',encoding='utf8');verify('reject-nested',nested,2)
        before=inventory(baseline);value=copy.deepcopy(request);value['out']=str(baseline);path=out/'existing-output.json';save(path,value);command('existing-output',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],2);assert inventory(baseline)==before
        gates=[]
        for name,options in [('work-capacity',dict(maximum_work=1)),('output-capacity',dict(maximum_output_bytes=1)),('negative-count',{}),('duplicate-count',{})]:
            value=copy.deepcopy(request);target=out/name;value['out']=str(target)
            if options:value.update(options)
            else:
                p=out/(name+'.mtx');lines=Path(value['counts']).read_text(encoding='utf8').splitlines();at=next(i for i,line in enumerate(lines) if not line.startswith('%'));fields=lines[at+1].split()
                if name=='negative-count':fields[2]='-1';lines[at+1]=' '.join(fields)
                else:shape=lines[at].split();shape[2]=str(int(shape[2])+1);lines[at]=' '.join(shape);lines.append(lines[at+1])
                p.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n');value['counts']=str(p)
            path=out/(name+'.json');save(path,value);command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],2);assert read(target/'analysis-status.json')['status']=='failed' and not (target/'deviations.tsv').exists() and (target/'WARNINGS.md').read_text(encoding='utf8').startswith('# FAIL2');verify('reject-failed-'+name,target,2);gates.append(dict(name=name,no_result_admitted=True))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-native-chromVAR-rehashed-corruption-and-failure-gates',source_pins=sources,artifact=build['artifact'],rehashed_rejections=rejections,unlisted_nested_file_rejections=3,copied_verifications=3,existing_output_preserved=True,retained_failure_gates=gates,failed_capture_rejections=12,native_analysis_requires_no_R=True,full_Signac_workflow_available=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-native-chromVAR-rehashed-corruption-and-failure-gates',rehashed_rejections=54,retained_failure_gates=4)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_RECOVERY_CHECKS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable semantic corruption guards')
    main()
