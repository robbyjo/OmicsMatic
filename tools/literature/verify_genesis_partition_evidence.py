# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete frozen native GENESIS partition controls without R; missing Java gives an explicit SKIP3."""
import argparse
import json
import re
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source
from validate_genesis_partition_native import compare
HOME=ROOT/'docs/literature/genesis-native-development'
STATUS='PASS_FROZEN_NATIVE_GENESIS242_MATRIX_PARTITION_AND_R461_SUM64_COMPONENT_ONLY'
BASE='build/literature-review/genesis-runtime/'
ACTION='Missing or forbidden Java17+ prerequisite: SKIP native saved verification. Supply a permitted Java runtime, installing only where allowed; otherwise keep verification skipped. FAIL2 admits no verified result: preserve the attempt, inspect the named source/archive/native-field cause, correct it explicitly and retry NEW paths. This verifier installs nothing and runs no R, PCA, PC-Relate or association model. A passing component replay grants no full GENESIS integration, biological, producer-authentication or whole-paper certificate.'


class JavaUnavailable(RuntimeError):pass


def run_java(command,timeout):
    try:process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    except OSError as failure:raise JavaUnavailable('Java could not be launched: '+repr(failure)) from failure
    try:stdout,stderr=process.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        process.kill();process.communicate();raise
    return subprocess.CompletedProcess(command,process.returncode,stdout,stderr)


def inspect(home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']==STATUS and not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and proof['GENESIS_tool_still_missing']
    for name,value in proof['source_pins'].items():
        if name=='.gitattributes':verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,value['sha256'])
        else:assert pin(ROOT/name)==value,('Changed native, input, reference or verification source',name)
    assert pin(home/'controls.zip')==proof['archive'];files=unpack(home/'controls.zip',proof)
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    for name,value in proof['artifacts'].items():assert pin(home/name)==value
    for compiler in [21,25]:
        build=json.loads(files[BASE+f'native-partition-api{compiler}-1/COMPLETED.json'])
        assert build['status']=='PASS_ISOLATED_NATIVE_GENESIS_PARTITION_API_AND_EXTERNAL_CONSUMER_BUILD'
    validated=json.loads(files[BASE+'native-partition-validation-1/COMPLETED.json'])
    assert validated['status']=='PASS300_NATIVE_GENESIS_CASE_DISPOSITIONS_2048_R64_SUMS_AND30_API_TEST_EXECUTIONS'
    assert (validated['complete_partitions'],validated['reproduced_original_failures'],validated['unsupported_nonfinite_refusals'],validated['scalar_sum_comparisons'],validated['JUnit_executions'])==(268,24,8,2048,30)
    reference=json.loads(files[BASE+'original-partition-controls-6/COMPLETED.json'])
    assert reference['status']=='PASS_ORIGINAL_GENESIS242_PARTITION_AND512_R64_REFERENCE_CONTROLS' and reference['R_longdouble_significand_bits']==64
    assert (reference['original_source_successes'],reference['original_source_failures'],reference['native_finite_profile_unsupported_cases'])==(69,6,2)
    for name,value in reference['files'].items():assert byte_pin(files[BASE+'original-partition-controls-6/'+name])==value
    protocol=json.loads(files[BASE+'partition-reference-protocol-1/COMPLETED.json']);assert protocol['fresh_complete_original_files_equal']==528 and len(protocol['records'])==3
    return proof,files


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=HOME);p.add_argument('--out',type=Path,required=True);p.add_argument('--java',type=Path);a=p.parse_args()
    out=a.out.resolve()
    if out.exists():print('FAIL2: preserve the existing attempt; required action: choose a NEW verifier output directory');return 2
    out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_GENESIS_COMPONENT_REPLAY',required_action=ACTION,original_R_analysis_executed=False,
        downloads_or_installation=False,new_catalog_credit=False,whole_goal_complete=False);code=2;records=[]
    try:
        proof,files=inspect(a.home)
        java=a.java or (Path(shutil.which('java')) if shutil.which('java') else None)
        if java is None or not java.is_file():
            state.update(status='SKIP3_NATIVE_GENESIS_JAVA_UNAVAILABLE',cause='Java17+ executable is unavailable; use --java with a permitted runtime');code=3
        else:
            version=run_java([str(java),'-version'],30)
            (out/'java-version.stdout').write_bytes(version.stdout);(out/'java-version.stderr').write_bytes(version.stderr)
            found=re.search(rb'version\s+"(\d+)(?:\.(\d+))?',version.stdout+version.stderr)
            major=(int(found[2]) if found and int(found[1])==1 and found[2] else int(found[1]) if found else 0)
            if version.returncode or major<17:
                state.update(status='SKIP3_NATIVE_GENESIS_JAVA_UNSUPPORTED',cause='A permitted Java17+ runtime is required; inspect the retained version probe');code=3
            else:
                reference=out/'retained-reference'
                prefix=BASE+'original-partition-controls-6/'
                for name,raw in files.items():
                    if name.startswith(prefix):
                        target=reference/name.removeprefix(prefix);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
                for compiler in [21,25]:
                    api=a.home/f'genesis-partition-api-javac{compiler}.jar';consumer=a.home/f'genesis-partition-consumer-javac{compiler}.jar'
                    target=out/f'javac{compiler}';separator=';' if __import__('os').name=='nt' else ':'
                    command=[str(java),'-Xmx512m','-cp',str(consumer.resolve())+separator+str(api.resolve()),'org.jlinalg.genetics.GenesisPartitionReferenceProbe',str(reference),str(target)]
                    run=run_java(command,240)
                    (out/f'javac{compiler}.stdout').write_bytes(run.stdout);(out/f'javac{compiler}.stderr').write_bytes(run.stderr)
                    save(out/f'javac{compiler}.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,(compiler,run.returncode,run.stderr[-3500:])
                    compared=compare(reference,target);save(target/'COMPARISON.json',compared);records.append(dict(compiler=compiler,**compared))
                inspect(a.home);state.update(status='PASS150_NATIVE_GENESIS_CASE_DISPOSITIONS_AND1024_R64_SUMS_OFFLINE_WITHOUT_R',records=records,archive=proof['archive']);code=0
    except JavaUnavailable as error:
        state.update(status='SKIP3_NATIVE_GENESIS_JAVA_UNAVAILABLE',cause=repr(error));code=3
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    except Exception as error:
        state.update(cause=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+ACTION+'\n\nNamed cause: '+state.get('cause','None; review the original source errors, nonfinite-input refusals and unrelated-set violation flags.')+'\n',encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('cause','')[:3000],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete native source, reference, archive or semantic comparison checks')
    raise SystemExit(main())
