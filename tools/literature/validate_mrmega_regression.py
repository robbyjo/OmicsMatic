# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check both compiler kernels on three JVMs against original WLS and base-R tails."""
import argparse,concurrent.futures,json,math,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save,RSCRIPT

BASE=ROOT/'build/literature-review/mrmega-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def rows(path):
    result=[]
    for line in path.read_text(encoding='utf8').splitlines():
        fields=line.split('\t');assert len(fields)==21
        result.append(dict(id=fields[0],cohorts=int(fields[1]),axes=int(fields[2]),
            vectors=[[float(x) for x in field.split(',')] for field in fields[3:6]],scalars=[float(x) for x in fields[6:]],raw=fields))
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_NATIVE_REGRESSION_CONTROLS',implementation_credit=False,whole_goal_complete=False,cases=[])
    try:
        fixture=BASE/'kernel-controls-1';reference=BASE/'kernel-original-1';receipt=json.loads((reference/'COMPLETED.json').read_bytes())
        assert receipt['status']=='PASS_FIFTY_FOUR_ORIGINAL_MRMEGA_REGRESSION_KERNEL_CONTROLS_ONLY'
        assert pin(reference/'original-kernel.tsv')==receipt['original_kernel_output'];original=rows(reference/'original-kernel.tsv');assert len(original)==54
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';builds={}
        protected=[canonical,Path(__file__).resolve(),fixture/'controls.txt',reference/'original-kernel.tsv']
        for compiler,folder in [('javac21','kernel-build-21-1'),('javac25','kernel-build-25-1')]:
            value=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert value['status']=='PASS_ISOLATED_JAVA17_MRMEGA_REGRESSION_BUILD_ONLY'
            assert all(pin(ROOT/name)==want for name,want in value['source_pins'].items()) and pin(canonical)==value['canonical_executable_unchanged']
            artifact=ROOT/value['artifact']['path'];assert pin(artifact)=={k:value['artifact'][k] for k in ['bytes','sha256']}
            builds[compiler]=artifact;protected.append(artifact)
        before={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def execute(home,command):
            home.mkdir();save(home/'command.json',command);run=subprocess.run(command,capture_output=True,timeout=180)
            (home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);save(home/'process.json',dict(exit_code=run.returncode))
            assert run.returncode==0,(home,run.stderr.decode(errors='replace'))
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            classpath=(':' if platform=='linux21' else ';').join(convert(path) for path in [builds[compiler],canonical])
            prefix=[*platforms[platform],'-cp',classpath,'org.jlinalg.meta.MrMegaRegressionReferenceProbe']
            execute(home/'contracts',prefix+['--contracts']);result=home/'native-kernel.tsv'
            execute(home/'complete-values',prefix+[convert(fixture/'controls.txt'),convert(result)])
            native=rows(result);assert len(native)==len(original)==54;maximum=0;values=0
            for actual,expected in zip(native,original,strict=True):
                assert (actual['id'],actual['cohorts'],actual['axes'])==(expected['id'],expected['cohorts'],expected['axes'])
                k=actual['axes']+1;assert [len(v) for v in actual['vectors']]==[len(v) for v in expected['vectors']]==[k,k,k*k]
                left=[x for v in actual['vectors'] for x in v]+actual['scalars'][:11]+actual['scalars'][14:]
                right=[x for v in expected['vectors'] for x in v]+expected['scalars'][:11]+expected['scalars'][14:]
                for observed,wanted in zip(left,right,strict=True):
                    assert math.isfinite(observed) and math.isfinite(wanted)
                    difference=abs(observed-wanted)/max(1,abs(wanted));assert difference<=2e-10,(actual['id'],observed,wanted,difference)
                    maximum=max(maximum,difference);values+=1
            print('PASS original WLS values '+compiler+'/'+platform,flush=True)
            return dict(compiler=compiler,platform=platform,complete_cases=54,numeric_values_compared=values,
                maximum_scaled_numeric_difference=maximum,normalization='abs(native-original)/max(1,abs(original)); no output bytes changed',
                native_output=pin(result),path=home.relative_to(ROOT).as_posix(),native_rows=native)
        tasks=[(compiler,platform) for compiler in builds for platform in platforms]
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(check,tasks))
        first=results[0]['native_rows'];qinput=out/'r-input.tsv'
        lines=['id\tassociation\tancestry\tresidual\tadf\txdf\trdf']
        for row in first:lines.append('\t'.join([row['id'],*[format(row['scalars'][i],'.17g') for i in [5,6,7,8,9,10]]]))
        qinput.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
        script=out/'chi-square-reference.R';script.write_text('''args <- commandArgs(trailingOnly=TRUE)
d <- read.delim(args[1],check.names=FALSE)
o <- data.frame(id=d$id,association=pchisq(d$association,d$adf,lower.tail=FALSE),ancestry=pchisq(d$ancestry,d$xdf,lower.tail=FALSE),residual=pchisq(d$residual,d$rdf,lower.tail=FALSE))
options(digits=17)
write.table(o,file=args[2],sep="\\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NaN")
cat(R.version.string,"\\n")
''',encoding='utf8',newline='\n')
        assert RSCRIPT.is_file(),'Required permitted R probability reference unavailable; skip its validation and grant no complete kernel validation credit'
        routput=out/'r-chi-square.tsv';execute(out/'r-reference',[str(RSCRIPT),'--vanilla',str(script),str(qinput),str(routput)])
        expected_r=[line.split('\t') for line in routput.read_text().splitlines()[1:]];assert len(expected_r)==54
        source_probability_differences=[];undefined=[]
        for run in results:
            largest=0;compared=0
            for actual,wanted,source in zip(run['native_rows'],expected_r,original,strict=True):
                assert actual['id']==wanted[0]==source['id']
                for number,label in enumerate(['association','ancestry','residual']):
                    native_p=actual['scalars'][11+number];r_p=float(wanted[1+number]);original_raw=source['raw'][17+number]
                    q=actual['scalars'][5+number];degrees=actual['scalars'][8+number]
                    if q<=0 or degrees<=0:
                        assert math.isnan(native_p)
                        if run is results[0]:undefined.append(dict(id=actual['id'],test=label,native='NaN',base_R=repr(r_p),original_raw=original_raw,reason='Explicit original-style undefined-test boundary; base-R boundary value retained separately.'))
                    else:
                        assert math.isfinite(native_p) and math.isfinite(r_p) and 0<=native_p<=1 and 0<=r_p<=1
                        difference=abs(native_p-r_p)/max(r_p,1e-300);assert difference<=3e-11,(actual['id'],label,native_p,r_p,difference)
                        largest=max(largest,difference);compared+=1
                    if run is results[0]:
                        old=float(original_raw)
                        if not math.isfinite(old) or old<0 or old>1 or (math.isfinite(native_p) and abs(old-native_p)>1e-12):
                            source_probability_differences.append(dict(id=actual['id'],test=label,original_raw=original_raw,native_stable=repr(native_p),base_R=repr(r_p)))
            del run['native_rows'];run.update(base_R_probability_comparisons=compared,maximum_R_probability_relative_difference=largest)
            state['cases'].append(run)
        assert before=={name:pin(ROOT/name) for name in before}
        save(out/'original-probability-discrepancies.json',source_probability_differences);save(out/'undefined-test-boundaries.json',undefined)
        state.update(status='PASS_BOTH_MRMEGA_KERNEL_BUILDS_ON_THREE_JVMS_WITH_ORIGINAL_WLS_AND_SEPARATE_R_TAILS_ONLY',
            compiler_platform_cases=6,complete_regression_comparisons=324,complete_source_cases=54,contract_suites=6,
            protected_inputs_unchanged=before,original_probability_discrepancies=len(source_probability_differences),
            original_probabilities_retained_without_relabeling=True,full_MRMEGA_file_workflow_available=False,
            scope='Prepared-cohort regression kernel only. Stable p-fields are independently checked against base R, and never substituted into original output. No allele/float32-file parsing, ancestry marker selection/MDS, GC, complete file workflow or whole-paper reproduction credit.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full numerical populations or explicit probability-scope checks')
    raise SystemExit(main())
