#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real summary CLI workflows, original-prepared inputs and cross-produced portability."""
import argparse,concurrent.futures,json,os,shutil,subprocess,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from validate_mashr_native import ROOT,BASE,JAVA21,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def add_products(request,native):
    settings=request/'settings.tsv';settings.write_text(settings.read_text(encoding='utf-8').replace('composite_mode\toff','composite_mode\tmarginal_sign_product')+'nominal_p_threshold\t0.05\nminimum_nominal\t2\nscore_threshold\t0.9\n',encoding='utf-8',newline='\n')
    conditions=(native/'execution/conditions.tsv').read_text(encoding='utf-8').splitlines();features=(native/'execution/target-ids.tsv').read_text(encoding='utf-8').splitlines()
    (request/'groups.tsv').write_text('group\tcondition\n'+''.join('all\t'+c+'\n' for c in conditions)+'first\t'+conditions[0]+'\n',encoding='utf-8',newline='\n')
    # Explicit synthetic nominal P values for controls; no paper-level P claims.
    (request/'nominal-p.tsv').write_text('feature\t'+'\t'.join(conditions)+'\n'+''.join(f+'\t'+'\t'.join(['0.001']*len(conditions))+'\n' for f in features),encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        artifact=pin(jar);bound={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'src/main/java/org/jlinalg/cli/MashSummaryCli.java',ROOT/'src/main/java/org/jlinalg/cli/MashSummaryLimits.java']}
        prepared=BASE/'original-preparation-development-1/controls-3/pca-ed/result/native-analysis';prepared_before={p.relative_to(prepared).as_posix():pin(p) for p in sorted(prepared.rglob('*')) if p.is_file()}
        specifications=[('windows-java21',[str(JAVA21/'java.exe')],str),('windows-java25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','timeout','--signal=TERM','--kill-after=10s','600','/usr/bin/java'],linux)]
        def execute(spec):
            name,java,path=spec;folder=out/name;folder.mkdir();commands=[];native=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-jar',path(jar),'mashr-tools','--no-config'];summary=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',path(jar),'org.jlinalg.cli.MashSummaryCli']
            def call(label,base,arguments,expected=0):
                argv=[*base,*arguments]
                with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(argv,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=630,check=False)
                record=dict(label=label,argv=argv,expected_exit_code=expected,exit_code=result.returncode);write(folder/(label+'.json'),record);commands.append(record);assert result.returncode==expected,(name,label,result.returncode);return record
            exported=folder/'native-export';native_result=folder/'native-result';call('native-export',native,['--export-example',path(exported)]);call('native-fit',native,['--input',path(exported/'fit-input'),'--out',path(native_result)])
            request=folder/'summary-request';call('summary-template',summary,['--export-template',path(native_result),'--out',path(request)])
            off=folder/'summary-off';call('summary-off',summary,['--native-bundle',path(native_result),'--input',path(request/'input'),'--out',path(off)]);call('summary-off-verify',summary,['--verify',path(off)])
            enabled=folder/'products-request';shutil.copytree(request/'input',enabled);add_products(enabled,native_result);on=folder/'summary-products';call('summary-products',summary,['--native-bundle',path(native_result),'--input',path(enabled),'--out',path(on)]);call('summary-products-verify',summary,['--verify',path(on)])
            assert '0001\tall\t' in (on/'results/marginal-sign-products.tsv').read_text(encoding='utf-8')
            formal=folder/'formal-request';shutil.copytree(request/'input',formal);s=formal/'settings.tsv';s.write_text(s.read_text(encoding='utf-8').replace('composite_mode\toff','composite_mode\tformal_composite'),encoding='utf-8',newline='\n');skipped=folder/'skipped';call('formal-skip',summary,['--native-bundle',path(native_result),'--input',path(formal),'--out',path(skipped)],3)
            invalid=folder/'invalid-request';shutil.copytree(enabled,invalid);p=invalid/'nominal-p.tsv';p.write_text(p.read_text(encoding='utf-8').replace('0001\t','1\t'),encoding='utf-8',newline='\n');failed=folder/'failed';call('invalid-identity',summary,['--native-bundle',path(native_result),'--input',path(invalid),'--out',path(failed)],2)
            for root,label in [(skipped,'SKIP3'),(failed,'FAIL2')]:assert not (root/'COMPLETED.json').exists() and label in (root/'WARNINGS.md').read_text(encoding='utf-8') and 'Required action' in (root/'WARNINGS.md').read_text(encoding='utf-8')
            learned_request=folder/'learned-request';call('learned-template',summary,['--export-template',path(prepared),'--out',path(learned_request)]);add_products(learned_request/'input',prepared)
            learned=folder/'learned-summary';call('learned-summary',summary,['--native-bundle',path(prepared),'--input',path(learned_request/'input'),'--out',path(learned)]);call('learned-summary-verify',summary,['--verify',path(learned)])
            native_result.rename(folder/'native-producer-retained');enabled.rename(folder/'nominal-request-retained');call('summary-products-producer-absent',summary,['--verify',path(on)])
            print(name+': '+str(len(commands))+' real summary/native commands passed',flush=True);return dict(name=name,commands=commands)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(execute,specifications))
        copied=[]
        for name,java,path in specifications:
            folder=out/name
            for producer,_,_ in specifications:
                if producer==name:continue
                target=folder/('copied-learned-from-'+producer);source=out/producer/'learned-summary';shutil.copytree(source,target);before={p.relative_to(target).as_posix():pin(p) for p in sorted(target.rglob('*')) if p.is_file()}
                argv=[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',path(jar),'org.jlinalg.cli.MashSummaryCli','--verify',path(target)];prefix=folder/('copied-learned-from-'+producer)
                with prefix.with_suffix('.stdout').open('xb') as stdout,prefix.with_suffix('.stderr').open('xb') as stderr:result=subprocess.run(argv,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=630,check=False)
                assert result.returncode==0,(name,producer);assert before=={p.relative_to(target).as_posix():pin(p) for p in sorted(target.rglob('*')) if p.is_file()};record=dict(producer=producer,verifier=name,exit_code=0,source_unchanged=True,argv=argv);write(prefix.with_suffix('.json'),record);copied.append(record)
        assert len(copied)==6 and artifact==pin(jar) and all(pin(ROOT/n)==v for n,v in bound.items()) and prepared_before=={p.relative_to(prepared).as_posix():pin(p) for p in sorted(prepared.rglob('*')) if p.is_file()}
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-public-summary-native-source-and-portability-commands',runs=runs,cross_produced_bundles=copied,source_pins=bound,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),prepared_source_files_unchanged=True,nominal_P_controls_synthetic=True,whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_SUMMARY_COMMANDS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_SUMMARY_COMMANDS: '+str(failure),file=sys.stderr);sys.exit(2)
