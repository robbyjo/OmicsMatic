from pathlib import Path
import hashlib,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/gc-6';B=T/'build/mtcojo-qualification-1';A=B/'execution-paths-1';O=B/'execution-audit-1';O.mkdir()
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as stream:json.dump(v,stream,indent=2);stream.write('\n')
saved=json.loads((A/'COMPLETED.json').read_bytes());assert set(saved['results'])=={'windows21','windows25','linux21'};summary={};probes=[];corrections=[]
for host,rows in saved['results'].items():
    assert len(rows)==19 and len({r['name'] for r in rows})==19;counts=dict(completed=0,FAIL2=0,SKIP3=0)
    for row in rows:
        name=row['name'];folder=A/host;bundle=folder/(name+'-bundle');error=(folder/(name+'.stderr')).read_text(errors='replace');assert row['exit_code']==row['expected_exit'],(host,name)
        if row['expected_exit']==0:
            assert row['verify_exit']==0 and row['all_original_table_tokens_equal'];counts['completed']+=1
            actual=[s.split() for s in (bundle/'worker/result.mtcojo.cma').read_text().splitlines()];expected=[s.split() for s in Path(row['original_reference']).read_text().splitlines()];assert actual==expected and len(actual)==10001
        elif row['bundle_created']:
            state=json.loads((bundle/'analysis-status.json').read_bytes());assert state['results_admitted'] is False and row['incomplete_verify_exit']==2
            assert state['status']==('SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if row['expected_exit']==3 else 'FAIL2_NO_COMPLETE_RESULT')
            warnings=(bundle/'WARNINGS.md').read_text();assert 'retry NEW output' in warnings and 'preserve inputs' in warnings
            manifest=json.loads((bundle/'manifest.json').read_bytes());assert manifest['results_admitted'] is False
            request=json.loads((folder/(name+'.request.json')).read_bytes());expected_inputs=3+len(request['traits'])+66-(1 if name=='missing-LD' else 0);assert len(manifest['captured_input_sha256'])==expected_inputs,(host,name,expected_inputs,len(manifest['captured_input_sha256']))
            for captured,sha in manifest['captured_input_sha256'].items():assert pin(bundle/captured)['sha256']==sha
        else:
            assert name in ['supplied-sentinel','fractional-LD-FDR'] and 'FAIL2' in error
        if row['expected_exit']!=0:counts['SKIP3' if row['expected_exit']==3 else 'FAIL2']+=1
        if name in ['gzip-final-LF','gzip-long-record']:
            wanted='unterminated final record' if name=='gzip-final-LF' else 'line exceeds declared/source gzip byte capacity';assert wanted in error
            assert row['cause_seen'] is False;corrections.append(dict(host=host,name=name,old_expected_substring=row['expected_cause'],actual_specific_cause=wanted,old_harness_metadata_retained=True,source_or_adapter_change=False))
        else:assert row['cause_seen'],(host,name)
        if name in ['single-GSMR-low-IV','multi-GSMR-low-IV']:assert row['original_exit_code']==0 and row['source_NaN_table_retained']
        if name in ['missing-runtime','wrong-runtime','missing-LD','gzip-CRC','gzip-final-LF','gzip-long-record','duplicate-LD']:assert not (bundle/'worker/analysis-command.json').exists()
        if name in ['original-timeout','original-output-cap']:
            assert row['terminal_probe']['observed_terminal'];cleanup=row['owned_process_cleanup'];assert cleanup and all(not p['alive_after_cleanup'] for p in cleanup)
            for p in cleanup:
                pid=int(p['pid']);command=['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--exec','/usr/bin/kill','-0',str(pid)] if host=='linux21' else ['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-NonInteractive','-Command',f'if(Get-Process -Id {pid} -ErrorAction SilentlyContinue){{exit 1}}else{{exit 0}}'];probe=subprocess.run(command,capture_output=True,timeout=30);terminal=probe.returncode==(1 if host=='linux21' else 0);probes.append(dict(host=host,name=name,pid=pid,command=command,exit_code=probe.returncode,observed_terminal=terminal));assert terminal,(host,name,pid)
    assert counts==dict(completed=6,FAIL2=10,SKIP3=3);summary[host]=counts
assert len(corrections)==6
save(O/'RESULT.json',dict(status='PASS_57_ACTUAL_MTCOJO_EXECUTION_PATHS',per_JVM=summary,successful_original_table_comparisons=18,actual_zero_exit_nonfinite_GSMR_results_refused=6,incomplete_capture_verifications_refused=33,prelaunch_prerequisite_or_intake_refusals=21,all_available_input_captures_verified=True,initial_harness_substring_corrections=corrections,source_or_adapter_changed=False,terminal_probes=probes,new_catalog_credit=0))
print('PASS57 execution paths;18 original table comparisons;6 zero-exit NaN refusals;',len(probes),'owned PID terminal probes;6 saved harness substring corrections',flush=True)
