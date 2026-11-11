# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real dependency skips, owned timeout cleanup, failed admissions and portable capture limits."""
import argparse,copy,json,shutil,subprocess
from pathlib import Path
from prepare_chromvar_background_request import prepare,save,ROOT,BASE
from validate_mashr_native import JAVA21,linux,pin
CLI='org.jlinalg.cli.ChromVarBackgroundRequestCli'
def read(p):return json.loads(p.read_bytes())
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def reindex(root):
    p=root/'artifact-checksums.tsv';p.write_text('sha256\tpath\n'+''.join(pin(f)['sha256']+'\t'+f.relative_to(root).as_posix()+'\n' for f in sorted(root.rglob('*')) if f.is_file() and f!=p),encoding='utf8',newline='\n')
def replace_line(path,line_index,field_index,new):
    lines=path.read_text(encoding='utf8').splitlines();fields=lines[line_index].split('\t');fields[field_index]=new(fields[field_index]);lines[line_index]='\t'.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='background-recovery-and-capture-limit-checks-in-progress'))
    try:
        build=read(BASE/'background-workflow-build-2/BUILD.json');jar=ROOT/build['artifact']['path'];probe=read(BASE/'background-request-probe-3/COMPLETED.json');baseline=ROOT/probe['analysis_path'];assert probe['artifact']==build['artifact'] and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        names=['tools/literature/validate_chromvar_background_recovery.py','tools/literature/prepare_chromvar_background_request.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(folder,name,argv,expected):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(folder/(name+'.stdout')).write_bytes(r.stdout);(folder/(name+'.stderr')).write_bytes(r.stderr);save(folder/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==expected,(folder.name,name,r.returncode,r.stderr.decode('utf8',errors='replace')[-650:])
        fixtures=out/'fixtures';fixtures.mkdir()
        def verify(name,analysis,expected):
            req=out/(name+'-verify.json');save(req,dict(schema_version=1,mode='VERIFY',analysis=analysis.relative_to(out).as_posix()))
            for platform,java,convert in platforms:command(out,name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(req)],expected)
        clean=fixtures/'copied-baseline';shutil.copytree(baseline,clean);verify('copied-baseline',clean,0)
        def change_json(root,name,key,value):d=read(root/name);d[key]=value(d.get(key)) if callable(value) else value;save(root/name,d)
        def change_count(root):
            request=read(root/'request.json');path=root/request['counts'];lines=path.read_text().splitlines();f=lines[2].split();f[2]=str(float(f[2])+1);lines[2]=' '.join(f);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n');m=read(root/'manifest.json');m['captured_input_sha256'][request['counts']]=pin(path)['sha256'];save(root/'manifest.json',m)
        mutations=[
            ('published-background',lambda r:replace_line(r/'background.tsv',1,1,lambda v:str(int(v)%20+1))),
            ('worker-background',lambda r:replace_line(r/'worker/background-original.tsv',1,1,lambda v:str(int(v)%20+1))),
            ('published-bias',lambda r:replace_line(r/'bias.tsv',1,1,lambda v:'.123')),
            ('worker-bias',lambda r:replace_line(r/'worker/bias.tsv',1,1,lambda v:'.123')),
            ('raw-count-rehashed',change_count),
            ('rng-after',lambda r:replace_line(r/'worker/rng-after.tsv',3,1,lambda v:str(int(v)+1))),
            ('rng-kind',lambda r:replace_line(r/'worker/rng-before.tsv',1,1,lambda v:'10404')),
            ('worker-profile',lambda r:replace_line(r/'worker/effective-settings.tsv',1,1,lambda v:'1.16.0-source')),
            ('source-function',lambda r:(r/'worker/source/chromvar-background/1.34.1/get_background_peaks_core.R').write_bytes((r/'worker/source/chromvar-background/1.34.1/get_background_peaks_core.R').read_bytes()+b'\n# altered source\n')),
            ('diagnostic-population',lambda r:change_json(r,'diagnostics.json','peaks',21)),
            ('scope',lambda r:change_json(r,'manifest.json','full_Signac_workflow_available',True)),
            ('warnings',lambda r:(r/'WARNINGS.md').write_text('Warnings removed\n',encoding='utf8')),
            ('runtime-binding',lambda r:change_json(r,'worker/runtime-bindings.json','rscript','C:/different/Rscript.exe')),
            ('followup-background',lambda r:change_json(r,'prepared-inputs.json','background','other.tsv')),
        ]
        rejected=[]
        for name,mutate in mutations:
            target=fixtures/name;shutil.copytree(baseline,target);mutate(target);reindex(target);verify(name,target,2);rejected.append(dict(case=name,rejected_platforms=3))
        extra=fixtures/'unlisted-nested';shutil.copytree(baseline,extra);(extra/'stray').mkdir();(extra/'stray/extra.sha256').write_text('not part of the original capture\n');verify('unlisted-nested',extra,2)
        forged=fixtures/'coordinated-forgery-NOT-A-RESULT';shutil.copytree(baseline,forged)
        for name in ['background.tsv','worker/background-original.tsv']:replace_line(forged/name,1,1,lambda v:str(int(v)%20+1))
        reindex(forged);verify('known-no-authentication-limit',forged,0);save(out/'KNOWN_LIMITATION.json',dict(status='EXPECTED_STRUCTURAL_ACCEPTANCE_OF_COORDINATED_FORGERY',purpose='Synthetic adversarial fixture, NOT an analysis result. Changing the worker and published index together then rehashing passes structural capture checks. VERIFY does not rerun sampling, authenticate execution or independently replay RNG; its warning states this limitation.',accepted_platforms=3))
        source_request=read(BASE/'background-request-probe-3/input-request/request.json');before=inventory(baseline);command(out,'existing-output-preserved',[JAVA21/'java.exe','-cp',jar,CLI,'--request',BASE/'background-request-probe-3/input-request/request.json'],2);assert inventory(baseline)==before
        gates=[]
        def gate(name,request,expected):
            target=out/('analysis-'+name);assert not target.exists();request=copy.deepcopy(request);request['out']=str(target);request.setdefault('timeout',90);path=out/(name+'-request.json');save(path,request);command(out,name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],expected);m=read(target/'manifest.json');assert not m['results_admitted'] and not any((target/n).exists() for n in ['background.tsv','bias.tsv','diagnostics.json','prepared-inputs.json','verify-request.json']);verify(name+'-failed-capture',target,2);gates.append(dict(case=name,exit_code=expected,analysis_path=target.relative_to(ROOT).as_posix(),no_result_admitted=True));return m
        missing=copy.deepcopy(source_request);missing['rscript']=str(out/'unavailable/Rscript.exe');gate('missing-Rscript',missing,3)
        empty=out/'empty-private-library';empty.mkdir();missing_package=copy.deepcopy(source_request);missing_package['r_motif_library']=str(empty);m=gate('actual-R-missing-package',missing_package,3);assert m['process_exit_code']==3
        work=copy.deepcopy(source_request);work['maximum_work']=1;gate('work-capacity',work,2)
        size=copy.deepcopy(source_request);size['maximum_output_bytes']=1;gate('output-capacity',size,2)
        timeout=copy.deepcopy(source_request);timeout['timeout']=1;m=gate('actual-owned-timeout',timeout,2);assert m['owned_process_cleanup'] and all(not v['alive_after_cleanup'] for v in m['owned_process_cleanup'])
        data=read(BASE/'background-original-controls-1/cases.json');case=next(v for v in data['cases'] if v['id']=='baseline')
        for name in ['missing-contig','wrong-fai']:
            directory=out/('inputs-'+name);request,_,_=prepare(directory,out/('unused-'+name),data['datasets']['baseline'],case,'1.34.1-package',reference=True)
            if name=='missing-contig':p=Path(request['peak_ranges']);p.write_text(p.read_text(encoding='utf8').replace('\tchr1\t','\tmissing\t'),encoding='utf8',newline='\n')
            else:p=Path(request['fai']);p.write_text(p.read_text().replace('chr1\t900\t','chr1\t901\t'),encoding='ascii',newline='\n')
            m=gate(name,request,2);assert 'owned_worker_pid' not in m
        assert len(rejected)==14 and len(gates)==7 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        save(out/'COMPLETED.json',dict(status='passed-background-recovery-and-explicit-verifier-limit-controls',source_pins=sources,artifact=build['artifact'],rehashed_single_change_rejections=rejected,total_rehashed_rejections=42,unlisted_nested_rejections=3,copied_verifications=3,known_coordinated_forgery_structurally_accepted=3,known_forgery_is_NOT_an_analysis_result=True,independent_native_RNG_replay=False,existing_output_preserved=True,retained_gates=gates,actual_dependency_skips=2,actual_owned_timeout_cleanup=True,failed_capture_rejections=21,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-background-recovery-and-explicit-verifier-limit-controls',rehashed_rejections=42,dependency_skips=2,owned_timeout_cleanup=True,known_authentication_limit_demonstrated=True)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_BACKGROUND_RECOVERY_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable real recovery/capture-limit checks')
    main()
