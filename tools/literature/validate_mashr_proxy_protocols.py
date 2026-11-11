#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Public proxy commands, original R score comparisons and cross-JVM portability."""
import argparse, concurrent.futures, json, shutil, subprocess, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from validate_mashr_native import ROOT,BASE,JAVA21,linux,pin
import mashr_preparation as bridge
from mashr_original_runtime import census,environment,run_owned


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def proxy_request(request,native):
    settings=request/'settings.tsv';settings.write_text(settings.read_text(encoding='utf-8').replace('composite_mode\toff','composite_mode\tindependent_marginal_proxy')+'proxy_assumption\tindependent_marginal_proxies\nscore_threshold\t0.05\nscore_boundary\tinclusive\n',encoding='utf-8',newline='\n')
    names=(native/'execution/conditions.tsv').read_text(encoding='utf-8').splitlines();assert len(names)>=2
    (request/'patterns.tsv').write_text('pattern\tmode\nall\tall\nany-first-two\tat_least_one\nfirst-specific\tall\nempty-all\tall\nempty-any\tat_least_one\nrepeat-overlap\tall\n',encoding='utf-8',newline='\n')
    rows=[('all','include',c) for c in names]+[('any-first-two','include',c) for c in names[:2]]+[('first-specific','include',names[0])]+[('first-specific','exclude',c) for c in names[1:]]+[('repeat-overlap','include',names[0]),('repeat-overlap','include',names[0]),('repeat-overlap','exclude',names[0])]
    (request/'selectors.tsv').write_text('pattern\trole\tcondition\n'+''.join('\t'.join(r)+'\n' for r in rows),encoding='utf-8',newline='\n')


def main():
    assert __debug__, 'Do not disable evidence assertions'
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--runtime',required=True,type=Path);args=parser.parse_args();jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        sources=[Path(__file__),ROOT/'src/main/java/org/jlinalg/cli/MashSummaryCli.java',ROOT/'src/main/java/org/jlinalg/shrinkage/MashCompositeScores.java',Path(__file__).with_name('mashr-proxy-command-controls.R')]
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources};artifact=pin(jar)
        prepared=BASE/'original-preparation-development-1/controls-3/pca-ed/result/native-analysis';prepared_before=census(prepared,1_073_741_824,100000)
        prior_run=BASE/'summary-command-development-1/commands-2/windows-java21';old=[prior_run/'summary-off',prior_run/'learned-summary'];old_before={p:census(p,1_073_741_824,100000) for p in old}
        specifications=[('windows-java21',[str(JAVA21/'java.exe')],str),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','timeout','--signal=TERM','--kill-after=10s','600','/usr/bin/java'],linux)]
        def execute(spec):
            name,java,path=spec;folder=out/name;folder.mkdir();commands=[];summary=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',path(jar),'org.jlinalg.cli.MashSummaryCli']
            def call(label,arguments,expected=0):
                argv=[*summary,*arguments]
                with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(argv,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=630,check=False)
                record=dict(label=label,argv=argv,expected_exit_code=expected,exit_code=result.returncode);write(folder/(label+'.json'),record);commands.append(record);assert result.returncode==expected,(name,label,result.returncode);return record
            call('help',['--help']);call('limits',['--default-limits'])
            native=folder/'native-producer';shutil.copytree(prepared,native);assert census(native,1_073_741_824,100000)==prepared_before
            req=folder/'proxy-request';call('template',['--export-template',path(native),'--out',path(req)]);proxy_request(req/'input',native)
            result=folder/'proxy-result';call('produce',['--native-bundle',path(native),'--input',path(req/'input'),'--out',path(result)]);call('verify',['--verify',path(result)])
            invalid=folder/'invalid-request';shutil.copytree(req/'input',invalid);s=invalid/'selectors.tsv';s.write_text(s.read_text(encoding='utf-8').replace('\t'+(native/'execution/conditions.tsv').read_text(encoding='utf-8').splitlines()[0]+'\n','\tunknown-condition\n'),encoding='utf-8',newline='\n');failed=folder/'failed';call('invalid-selector',['--native-bundle',path(native),'--input',path(invalid),'--out',path(failed)],2)
            formal=folder/'formal-request';shutil.copytree(req/'input',formal);s=formal/'settings.tsv';s.write_text(s.read_text(encoding='utf-8').replace('composite_mode\tindependent_marginal_proxy','composite_mode\tformal_composite'),encoding='utf-8',newline='\n');skipped=folder/'skipped';call('formal-skip',['--native-bundle',path(native),'--input',path(formal),'--out',path(skipped)],3)
            for root,label in [(failed,'FAIL2'),(skipped,'SKIP3')]:assert not (root/'COMPLETED.json').exists() and label in (root/'WARNINGS.md').read_text(encoding='utf-8') and 'Required action' in (root/'WARNINGS.md').read_text(encoding='utf-8')
            native.rename(folder/'native-producer-retained');req.rename(folder/'proxy-request-retained');call('producer-absent',['--verify',path(result)])
            for i,p in enumerate(old):call('prior-v1-'+str(i),['--verify',path(p)])
            text=(result/'WARNINGS.md').read_text(encoding='utf-8');assert 'INDEPENDENT MARGINAL PROXY' in text and 'repeatedSelectorScoreCells=20' in text and 'overlappingScoreCells=20' in text
            assert len((result/'results/independent-marginal-proxies.tsv').read_text(encoding='utf-8').splitlines())==121
            print(name+': '+str(len(commands))+' public proxy/legacy commands passed',flush=True);return dict(name=name,commands=commands)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(execute,specifications))
        copied=[]
        for name,java,path in specifications:
            folder=out/name
            for producer,_,_ in specifications:
                if producer==name:continue
                target=folder/('copied-from-'+producer);shutil.copytree(out/producer/'proxy-result',target);before=census(target,1_073_741_824,100000)
                argv=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',path(jar),'org.jlinalg.cli.MashSummaryCli','--verify',path(target)];prefix=folder/('copied-from-'+producer)
                with prefix.with_suffix('.stdout').open('xb') as stdout,prefix.with_suffix('.stderr').open('xb') as stderr:result=subprocess.run(argv,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=630,check=False)
                assert result.returncode==0,(name,producer);assert before==census(target,1_073_741_824,100000);record=dict(producer=producer,verifier=name,exit_code=0,source_unchanged=True,argv=argv);write(prefix.with_suffix('.json'),record);copied.append(record)
        # Fresh actual-original comparisons for every score/decision in each public result.
        rt=bridge.runtime(args.runtime);app=bridge.native(None,JAVA21/'java.exe');intake=BASE/'composite-development-1/source-intake-1';original=intake/'dreamlet/R/compositePosteriorTest.R';receipt=json.loads((intake/'dreamlet-receipt.json').read_text(encoding='utf-8'));assert pin(original)=={k:receipt['files']['R/compositePosteriorTest.R'][k] for k in ['bytes','sha256']}
        reference=out/'original-R';reference.mkdir();shutil.copyfile(original,reference/'original-compositePosteriorTest.R');shutil.copyfile(Path(__file__).with_name('mashr-proxy-command-controls.R'),reference/'controls.R');shutil.copyfile(intake/'Artistic-2.0.txt',reference/'Artistic-2.0.txt');original_runs=[]
        for name,_,_ in specifications:
            folder=reference/name;folder.mkdir();process=run_owned([rt['rhome']/'bin/Rscript.exe','--vanilla',reference/'controls.R',reference/'original-compositePosteriorTest.R',out/name/'proxy-result',folder/'original'],folder,'original',environment(rt['rhome'],rt['home'],folder/'scratch'),rt['monitor'])
            assert (folder/'original/STATUS.txt').read_text().strip()=='ORIGINAL_PROXY_COMMAND_CONTROLS_COMPLETE';assert len((folder/'original/original-vs-native.tsv').read_text().splitlines())==121;original_runs.append(dict(platform=name,scores_and_decisions=120,process=process))
        bridge.checked_state(rt,app)
        assert len(copied)==6 and all(len(r['commands'])==10 for r in runs) and pin(jar)==artifact and all(pin(ROOT/n)==v for n,v in bound.items()) and census(prepared,1_073_741_824,100000)==prepared_before and all(census(p,1_073_741_824,100000)==g for p,g in old_before.items())
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-public-proxy-original-and-portability-protocols',runs=runs,cross_produced_bundles=copied,original_R_runs=original_runs,original_R_score_decisions=360,source_pins=bound,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),prepared_source_files_unchanged=True,prior_v1_bundles_unchanged=True,original_source_commit=receipt['commit'],original_source_function_unmodified=True,full_dreamlet_package_validated=False,whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_PROXY_PROTOCOLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_PROXY_PROTOCOLS: '+str(failure),file=sys.stderr);sys.exit(2)
