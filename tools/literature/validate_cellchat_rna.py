# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare prepared native RNA calculations with all six original R references."""
import argparse,concurrent.futures,csv,itertools,json,math,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from create_cellchat_rna_controls import LABELS

BASE=ROOT/'build/literature-review/cellchat-runtime'
PLATFORMS={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
 'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],
 'linux21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java']}

def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]

def table(path):
    with path.open(encoding='utf8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build21',required=True);p.add_argument('--build25',required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CELLCHAT_NATIVE_COMPONENT_CONTROLS',implementation_credit=False,whole_goal_complete=False,full_workflow_available=False,runs=[])
    try:
        reference=BASE/'reference-1';receipt=json.loads((reference/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_SIX_UNCHANGED_CELLCHAT_RNA_FUNCTION_REFERENCES_ONLY'
        fixtures=BASE/'controls-1';control=json.loads((fixtures/'COMPLETED.json').read_bytes());assert pin(fixtures/'cases.txt')==control['cases_pin'];cases=json.loads((fixtures/'cases.json').read_bytes());assert len(cases)==150
        protected=[ROOT/'build/cli/omicsmatic-0.3.7.jar',fixtures/'cases.txt',fixtures/'cases.json',Path(__file__).resolve()];artifacts={}
        for name,folder in [('javac21',a.build21),('javac25',a.build25)]:
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_JAVA17_CELLCHAT_RNA_COMPONENT_BUILD_ONLY'
            for n,wanted in built['source_pins'].items():assert pin(ROOT/n)==wanted;protected.append(ROOT/n)
            artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']};artifacts[name]=artifact;protected.append(artifact)
        protected.extend(f for f in reference.rglob('*') if f.is_file());before={f.relative_to(ROOT).as_posix():pin(f) for f in protected};save(out/'PROTECTED-BEFORE.json',before)
        def execute(home,command):
            home.mkdir();save(home/'command.json',command)
            with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:
                run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
            save(home/'process.json',dict(exit_code=run.returncode));assert run.returncode==0,(home,'Inspect retained stdout/stderr')
        def run(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            command=[*PLATFORMS[platform],'-Xmx256m','-cp',convert(artifacts[compiler])]
            execute(home/'contracts',command+['org.jlinalg.omics.CellChatRnaContracts'])
            comparisons=[];mismatches=[];maximum=0;values=0;boundaries=0
            for label,_ in LABELS:
                native=home/label/'results';execute(home/label,command+['org.jlinalg.omics.CellChatRnaProbe',convert(fixtures/'cases.txt'),convert(reference/label),convert(native),label])
                for case in [c for c in cases if c['label']==label]:
                    original=table(reference/label/case['id']/'metrics.tsv');actual=table(native/case['id']/'metrics.tsv');assert len(actual)==len(original)
                    localmax=0;localmismatches=0
                    for x,y in zip(actual,original,strict=True):
                        assert all(x[k]==y[k] for k in ['interaction','source','target'])
                        left=float(x['probability']);right=float(y['probability']);assert math.isfinite(left) and math.isfinite(right)
                        error=abs(left-right)/max(1,abs(right));assert error<=2e-12,(case,x,y,error);localmax=max(localmax,error);values+=1
                        # R write.table formats doubles to 15 significant digits;
                        # compare the exact permutation exceedance counts, not
                        # different decimal spellings of the same rational p.
                        nativep=float(x['pvalue']);originalp=float(y['pvalue']);nb=case['nboot']
                        nativecount=round(nativep*nb);originalcount=round(originalp*nb)
                        assert abs(nativep-nativecount/nb)<1e-13 and abs(originalp-originalcount/nb)<1e-13
                        if nativecount!=originalcount:
                            localmismatches+=1;mismatches.append(dict(case=case['id'],interaction=x['interaction'],source=x['source'],target=x['target'],native=float(x['pvalue']),original=float(y['pvalue'])))
                    boundary=int((native/case['id']/'boundaries.txt').read_text());boundaries+=boundary
                    if localmismatches:assert boundary>0,'P-value mismatch with no reported numerical boundary'
                    comparisons.append(dict(case=case['id'],label=label,rows=len(actual),maximum_scaled_probability_difference=localmax,pvalue_mismatches=localmismatches,numerical_boundary_comparisons=boundary));maximum=max(maximum,localmax)
            save(home/'pvalue-mismatches.json',mismatches);save(home/'comparisons.json',comparisons)
            result=dict(compiler=compiler,platform=platform,cases=len(comparisons),observed_values=values,maximum_scaled_probability_difference=maximum,permutation_pvalue_mismatches=len(mismatches),numerical_boundary_comparisons=boundaries,contracts=20)
            print('PASS prepared RNA observed scores '+compiler+'/'+platform+' cases='+str(len(comparisons))+' p-value mismatches='+str(len(mismatches)),flush=True)
            return result
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            futures=[pool.submit(run,item) for item in itertools.product(artifacts,PLATFORMS)]
            for future in futures:state['runs'].append(future.result())
        after={n:pin(ROOT/n) for n in before};save(out/'PROTECTED-AFTER.json',after);assert before==after
        state.update(status='PASS_NATIVE_CELLCHAT_OBSERVED_RNA_SCORES_ONLY',source_control_families=150,build_runtime_comparisons=900,protected_inputs_unchanged=True,permutation_p_values_source_equivalent=all(r['permutation_pvalue_mismatches']==0 for r in state['runs']),canonical_executable_unchanged=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar'))
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Prepared-input observed scores are validated. Read every pvalue-mismatches.json: native floating-point permutation tails are diagnostics and may differ from original R at exact/near ties.')+'\n\nRequired action: use unchanged original R for authoritative permutation p-values; do not silently round, repair ties or claim statistical equivalence. Missing/forbidden runtime means SKIP3 and dependent analysis skipped. Invalid input or process/integrity/capacity failure means FAIL2 and no result admitted. Preserve failed attempts and use NEW output paths. No full CellChat workflow or catalog implementation credit is admitted here.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable reference and source checks')
    raise SystemExit(main())
