# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay frozen GENESIS development workflow bundles using Java only; no R, fits or installation."""
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
HOME=ROOT/'docs/literature/genesis-workflow-development'
STATUS='PASS_FROZEN_GENESIS242_DEVELOPMENT_WORKFLOW_CONTROLS_ONLY'
PREFIX='build/literature-review/genesis-runtime/'
CASES=['pcair','pcrelate','null-model-gaussian','association-gaussian','null-model-binomial','association-binomial']
ACTION='Java17+ missing or forbidden: SKIP saved GENESIS verification; supply a permitted Java runtime or leave verification skipped. This helper installs nothing, runs no R and performs no fits. FAIL2: no verified result is admitted; preserve the attempt and resolve the stated source/archive/scientific cause before retrying NEW paths. A development replay grants no catalog, whole-package, historical-paper, biological or producer-authentication certificate.'


class Unavailable(RuntimeError):pass


def inspect(home=HOME):
    home=Path(home);proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']==STATUS and proof['new_catalog_credit'] is False and proof['GENESIS_tool_still_missing'] is True and proof['whole_goal_complete'] is False
    for name,value in proof['source_pins'].items():
        if name=='.gitattributes':verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,value['sha256'])
        else:assert pin(ROOT/name)==value,('Changed scientific/source/verification bytes',name)
    assert pin(home/'controls.zip')==proof['archive'];files=unpack(home/'controls.zip',proof)
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    for name,value in proof['artifacts'].items():assert pin(home/name)==value
    def record(name):return json.loads(files[PREFIX+name+'/COMPLETED.json'])
    original=record('adapter-pilot-2');assert original['status']=='PASS_SIX_GENESIS_ADAPTER_PILOTS_ONLY' and len(original['records'])==6 and all(r['exit_code']==0 for r in original['records'])
    protocol=record('adapter-protocol-4');assert protocol['status']=='PASS_GENESIS_SAVED_CONTROLS_AND_RESEALED_SCIENTIFIC_REFUSALS' and (protocol['positive_verifications'],protocol['resealed_scientific_refusals'])==(18,75)
    comparison=record('full-original-comparison-2');assert comparison['status']=='PASS_COMPLETE_RETAINED_ORIGINAL_GENESIS_WORKFLOW_FIELD_COMPARISONS' and (comparison['comparisons'],comparison['compared_cells'])==(92,49218)
    assert record('request-contracts-4')['status']=='PASS30_GENESIS_REQUEST_CONTRACT_EXECUTIONS'
    failure=record('adapter-failure-contracts-1');assert failure['status']=='PASS_THREE_ACTUAL_GENESIS_REFUSAL_CONTRACTS' and [r['exit_code'] for r in failure['records']]==[2,2,3]
    for case in CASES:
        prefix=PREFIX+'adapter-pilot-2/'+case+'/';done=json.loads(files[prefix+'COMPLETED.json']);assert done['result_admitted'] is True and done['downstream_eligible'] is True
        captured={n.removeprefix(prefix):byte_pin(v) for n,v in files.items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'};assert captured==done['files']
    assert proof['canonical_executable_unchanged']['sha256']=='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof,files


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=HOME);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--java',type=Path);p.add_argument('--jar-compiler',choices=['21','25'],default='21');a=p.parse_args();out=a.out.resolve();home=a.home.resolve()
    if out.exists() or out.is_relative_to(home) or home.is_relative_to(out):print('FAIL2: choose a NEW output directory separate from the frozen evidence. '+ACTION);return 2
    out.mkdir(parents=True);state=dict(status='FAIL2_GENESIS_DEVELOPMENT_REPLAY',original_R_analysis_executed=False,downloads_or_installation=False,new_catalog_credit=False,whole_goal_complete=False,required_action=ACTION);code=2
    try:
        proof,files=inspect(home);java=a.java or (Path(shutil.which('java')) if shutil.which('java') else None)
        if java is None or not java.is_file():raise Unavailable('Java17+ is missing; supply --java with an allowed executable')
        try:version=subprocess.run([str(java),'-version'],stdin=subprocess.DEVNULL,capture_output=True,timeout=30)
        except OSError as error:raise Unavailable('Java could not be launched: '+repr(error)) from error
        (out/'java-version.stdout').write_bytes(version.stdout);(out/'java-version.stderr').write_bytes(version.stderr)
        match=re.search(rb'version\s+"(\d+)(?:\.(\d+))?',version.stdout+version.stderr);major=int(match[1]) if match else 0
        if version.returncode or major<17:raise Unavailable('A working Java17+ executable is required; inspect the retained version output')
        controls=out/'retained-controls';controls.mkdir();prefix=PREFIX+'adapter-pilot-2/'
        for name,raw in files.items():
            if name.startswith(prefix) and any(name.startswith(prefix+case+'/') for case in CASES):
                target=controls/name.removeprefix(prefix);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        jar=home/('genesis-tools-javac'+a.jar_compiler+'.jar');before=pin(jar);records=[]
        for case in CASES:
            command=[str(java),'-Xmx2g','-jar',str(jar),'--verify',str(controls/case)]
            try:r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
            except OSError as error:raise Unavailable('Java could not be launched for saved verification: '+repr(error)) from error
            (out/(case+'.stdout')).write_bytes(r.stdout);(out/(case+'.stderr')).write_bytes(r.stderr);record=dict(case=case,command=command,exit_code=r.returncode);save(out/(case+'.process.json'),record);records.append(record)
            assert r.returncode==0,(case,r.stderr[-5000:]);print('PASS_SAVED_GENESIS',case,flush=True)
        assert pin(jar)==before
        for case in CASES:
            prefix=PREFIX+'adapter-pilot-2/'+case+'/'
            assert {p.relative_to(controls/case).as_posix():pin(p) for p in (controls/case).rglob('*') if p.is_file()}=={n.removeprefix(prefix):byte_pin(v) for n,v in files.items() if n.startswith(prefix)}
        state.update(status='PASS6_FROZEN_GENESIS_DEVELOPMENT_WORKFLOW_REPLAYS_WITHOUT_R',records=records,proof_pin=pin(home/'VALIDATION.json'),archive=proof['archive']);code=0
    except Unavailable as error:state.update(status='SKIP3_GENESIS_DEVELOPMENT_JAVA_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Saved native contracts passed; no original fit or independent estimator refit was performed.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n');save(out/'COMPLETED.json',state)
    print(state['status'],state.get('cause','')[:5000],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen source/archive/scientific replay checks')
    raise SystemExit(main())
