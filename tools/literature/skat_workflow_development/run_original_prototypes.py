# SPDX-License-Identifier: GPL-3.0-or-later
# Development-only harness; no production process-containment claim.
from pathlib import Path
import argparse,csv,hashlib,json,os,shutil,subprocess,time,uuid
T=Path(__file__).resolve().parents[3];SOURCE=T/'src/main/resources/org/jlinalg/omics/skat-workflow'
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def filepin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def env():
    result={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS','R_HOME']};result.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');return result
def table(path):return list(csv.DictReader(path.read_text(encoding='utf-8').splitlines(),delimiter='\t',quoting=csv.QUOTE_NONE))
if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Development-only original SKAT worker prototypes; no Java CLI or whole-tool qualification');parser.add_argument('--out',type=Path,required=True);parser.add_argument('--rscript',required=True);parser.add_argument('--r-library',type=Path,required=True);parser.add_argument('--controls',type=Path,required=True);args=parser.parse_args();D=args.out.resolve();D.mkdir();PYLIB=args.r_library.resolve();REF=args.controls.resolve();RS=str(Path(args.rscript).resolve());inputs=D/'artificial-inputs';command=[RS,'--vanilla',str(Path(__file__).with_name('prepare_artificial_inputs.R')),str(REF/'ARTIFICIAL-INPUTS.rds'),str(inputs)]
    with (D/'inputs.stdout').open('xb') as out,(D/'inputs.stderr').open('xb') as err:p=subprocess.run(command,env=env(),stdin=subprocess.DEVNULL,stdout=out,stderr=err,timeout=60)
    save(D/'inputs.PROCESS.json',dict(exit_code=p.returncode));assert p.returncode==0
    expected={row['id']:row for row in csv.DictReader((REF/'RESULTS.tsv').read_text().splitlines(),delimiter='\t')};summary=[]
    for trait in ['C','D']:
        for method in ['davies','optimal.adj']:
            for adjustment in [False,True]:
                case=f'{trait}-{method}-{str(adjustment).upper()}';root=D/case;root.mkdir();(root/'worker').mkdir();(root/'results').mkdir();(root/'inputs').mkdir();shutil.copytree(SOURCE,root/'source')
                for name in ['genotypes.tsv','covariates.tsv','sets.tsv']:shutil.copyfile(inputs/name,root/'inputs'/name)
                shutil.copyfile(inputs/('continuous.tsv' if trait=='C' else 'binary.tsv'),root/'inputs/phenotype.tsv')
                parameters=dict(profile='2.2.5-r4.6.1',timeout_seconds='90',out_type=trait,null_function='SKAT_Null_Model',adjustment=str(adjustment).lower(),n_resampling='0',resampling='bootstrap',intercept='true',missing_model='reject',rank_policy='require-full-rank',kurtosis_adjustment='true',kurtosis_resamples='10000',kernel='linear.weighted',method=method,weights_beta='1,25',impute='fixed',r_corr='0',check_genotype='true',dosage='false',missing_cutoff='.15',max_maf='1',estimate_maf='1',seed='20261122',allow_empty_sets='true',allow_test_substitution='true',allow_adjustment_omission='false',allow_source_fallback='false',zero_policy='refuse',maximum_cells='10000000',maximum_set_cells='10000000',maximum_resampling_cells='10000000')
                (root/'worker/parameters.tsv').write_text('key\tvalue\n'+''.join(k+'\t'+v+'\n' for k,v in parameters.items()),encoding='utf-8',newline='\n');(root/'worker/runtime-paths.txt').write_text(str(PYLIB)+'\n',encoding='utf-8',newline='\n');nonce=str(uuid.uuid4());(root/'worker/launch-nonce.txt').write_text(nonce+'\n',encoding='utf-8',newline='\n')
                command=[RS,'--vanilla','../source/skat_worker.R'];save(root/'STARTED.json',dict(command=command,worker_source=filepin(SOURCE/'skat_worker.R'),reference_source=filepin(SOURCE/'SOURCE.json'),parameters=parameters,prototype_not_Java_adapter_qualification=True))
                critical=None
                with (root/'worker/stdout').open('xb') as out,(root/'worker/stderr').open('xb') as err:
                    process=subprocess.Popen(command,cwd=root/'worker',env=env(),stdin=subprocess.DEVNULL,stdout=out,stderr=err);start=time.monotonic()
                    while process.poll() is None:
                        if critical is None and (root/'worker/runtime-ready.txt').exists():
                            try:ready=(root/'worker/runtime-ready.txt').read_text().strip()
                            except PermissionError:
                                time.sleep(.02);continue
                            assert ready==nonce;critical={row['path']:filepin(Path(row['path'])) for row in table(root/'worker/runtime-files.tsv')};save(root/'RUNTIME-FINGERPRINTS.json',critical);(root/'worker/runtime-ack.txt').write_text(nonce+'\n',encoding='utf-8',newline='\n')
                        if time.monotonic()-start>110:process.kill();process.wait();raise RuntimeError('Owned prototype timed out;retained evidence: '+str(root))
                        time.sleep(.02)
                save(root/'PROCESS.json',dict(exit_code=process.returncode));print(case,'exit',process.returncode,flush=True)
                if process.returncode!=0:
                    print((root/'worker/stderr').read_text()[-3000:],flush=True)
                    if (root/'worker/worker-status.tsv').is_file():print(table(root/'worker/worker-status.tsv'),flush=True)
                    raise RuntimeError('Inspect retained failing prototype '+case)
                assert critical and all(filepin(Path(name))==value for name,value in critical.items());rows=table(root/'worker/set-results.tsv');assert len(rows)==3 and rows[0]['result_admitted']=='TRUE' and rows[1]['result_admitted']=='TRUE' and rows[2]['p_kind']=='not-tested-no-variants' and rows[2]['export_p']=='NA'
                observed=float(rows[0]['raw_p']);wanted=float(expected[case]['p']);assert abs(observed-wanted)<=max(8*__import__('math').ulp(wanted),abs(wanted)*2e-13),(case,observed,wanted)
                summary.append(dict(case=case,original_p=wanted,prototype_p=observed,sets=len(rows),runtime_files=len(critical),status='PASS_ACTUAL_ORIGINAL_WORKER_PROTOTYPE'))
    save(D/'RESULT.json',dict(status='PASS_EIGHT_ORIGINAL_SKAT_WORKER_PROTOTYPES',cases=summary,all_inputs_artificial=True,namespace_function_guards=239,Java_adapter_qualified=False,whole_goal_complete=False));print('PASS eight genuine original prototypes;three sets per null model;not Java/global-JAR qualification',flush=True)
