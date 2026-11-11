# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run bounded original Sniffles scientific controls, retaining complete VCFs and logs.

This is an upstream reference experiment, not the Java execution adapter or a
paper reproduction. No external SNF/pickle input is accepted or deserialized.
"""
import argparse
import json
import os
import resource
import signal
import subprocess
import time
import traceback
from pathlib import Path

from setup_sniffles_reference_linux import ROOT, pin, save, inventory, members


def original_vcf(path):
    raw = path.read_bytes()
    assert raw.endswith(b'\n')
    lines = raw.decode('utf8').splitlines()
    metadata = [line for line in lines if line.startswith('##')]
    header = [line for line in lines if line.startswith('#CHROM\t')]
    assert len(header) == 1 and metadata[0] == '##fileformat=VCFv4.2'
    columns = header[0].split('\t')
    records = []
    for line in lines:
        if line.startswith('#'):
            continue
        fields = line.split('\t')
        assert len(fields) == len(columns)
        info = [dict(key=token.split('=',1)[0],value=token.split('=',1)[1] if '=' in token else None)
                for token in fields[7].split(';')] if fields[7] != '.' else []
        records.append(dict(raw_line=line, columns=fields, info=info,
                            format=fields[8].split(':'), samples=[v.split(':') for v in fields[9:]]))
    return dict(metadata_lines=metadata,column_line=header[0],samples=columns[9:],records=records)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--fixtures',type=Path,required=True)
    args=parser.parse_args()
    out=args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_ORIGINAL_REFERENCE_CONTROLS',implementation_credit=False,
               Java_adapter_validated=False,whole_goal_complete=False,cases=[])
    runtime=args.runtime.resolve();fixtures=args.fixtures.resolve()
    try:
        setup=json.loads((runtime/'COMPLETED.json').read_bytes())
        assert setup['status']=='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY'
        assert pin(runtime/'RUNTIME-FILES.json')==setup['runtime_files']
        assert pin(runtime/'SOURCE-COPY-FILES.json')==setup['source_copy_files']
        assert inventory(runtime/'venv')==json.loads((runtime/'RUNTIME-FILES.json').read_bytes())
        assert inventory(runtime/'sources')==json.loads((runtime/'SOURCE-COPY-FILES.json').read_bytes())
        fixture_receipt=json.loads((fixtures/'COMPLETED.json').read_bytes())
        assert fixture_receipt['status']=='PASS_SYNTHETIC_INDEXED_LONG_READ_CONTROL_CREATION_ONLY'
        assert all(pin(fixtures/n)==value for n,value in fixture_receipt['files'].items())
        before=dict(runtime_receipt=pin(runtime/'COMPLETED.json'),fixture_receipt=pin(fixtures/'COMPLETED.json'),
                    sources=setup['source_copy_files'],runtime=setup['runtime_files'])
        save(out/'bindings-before.json',before)
        env={k:v for k,v in os.environ.items() if not k.startswith(('PYTHON','LD_','PIP_'))}
        env.update(LANG='C.UTF-8',LC_ALL='C.UTF-8',PATH='/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1',
                   OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        def limits():
            resource.setrlimit(resource.RLIMIT_AS,(8589934592,8589934592))
            resource.setrlimit(resource.RLIMIT_CPU,(120,120))
            resource.setrlimit(resource.RLIMIT_FSIZE,(268435456,268435456))
            resource.setrlimit(resource.RLIMIT_NOFILE,(256,256))
            resource.setrlimit(resource.RLIMIT_CORE,(0,0))
        def call(tag,sample,name,extra=()):
            home=out/name;home.mkdir()
            source_command=setup['probes'][tag]['command']
            if tag!='v1.0.7':
                # -B prevents writes but does not prevent reads of existing pyc.
                # A fresh nonexistent cache prefix plus -B forces source reads.
                cache=home/'never-created-bytecode-cache'
                assert not cache.exists()
                source_command=[source_command[0],'-X','pycache_prefix='+str(cache),*source_command[1:]]
            input_path=fixtures/(sample+'.bam')
            target=home/'original.vcf'
            command=[*source_command,'-m' if tag=='v1.0.7' else '--input',str(input_path),
                     '--vcf',str(target),'--threads','1',*map(str,extra)]
            save(home/'command.json',command)
            record=dict(name=name,edition=tag,sample=sample,command=command,analysis_admitted=False)
            save(home/'STARTED.json',record)
            child=None;failure=None;code=None;started=time.monotonic()
            try:
                environment=dict(env,HOME=str(home),TMPDIR=str(home))
                with (home/'original.stdout').open('xb') as first,(home/'original.stderr').open('xb') as second:
                    child=subprocess.Popen(command,cwd=home,env=environment,stdin=subprocess.DEVNULL,
                                           stdout=first,stderr=second,start_new_session=True,preexec_fn=limits)
                    record['owned_process_group']=child.pid
                    while child.poll() is None:
                        assert time.monotonic()-started<180,'Original caller exceeded wall-time limit'
                        time.sleep(.025)
                    code=child.returncode
                    assert code==0,'Original caller failed; inspect complete original.stderr/stdout'
                assert not any(r['state']!='Z' for r in members(child.pid)), 'Original main left live owned workers'
                if tag!='v1.0.7':assert not cache.exists(), 'Unexpected Python bytecode cache was created'
                decoded=original_vcf(target)
                # Truth contains three events. The retained first experiment
                # shows that legacy 1.0.7 omits the initial 120-bp deletion.
                # Preserve this source behavior rather than fabricate a call.
                expected_lengths=[70,90] if tag=='v1.0.7' else [70,90,120]
                assert len(decoded['records'])==len(expected_lengths),(name,'unexpected complete original call count',len(decoded['records']))
                lengths=[]
                for row in decoded['records']:
                    info={r['key']:r['value'] for r in row['info']}
                    assert info['SVTYPE'] in ['INS','DEL']
                    lengths.append(abs(int(info['SVLEN'])))
                assert sorted(lengths)==expected_lengths,(name,lengths)
                if name=='v24-phased':
                    assert all('PS' in row['format'] for row in decoded['records'])
                    assert all('|' in row['samples'][0][row['format'].index('GT')] for row in decoded['records'])
                    assert all(row['samples'][0][row['format'].index('PS')]=='1001' for row in decoded['records'])
                save(home/'complete-original-records.json',decoded)
                record.update(status='PASS_COMPLETE_ORIGINAL_SYNTHETIC_SV_CALLING_REFERENCE_ONLY',
                              records=len(decoded['records']),original_vcf=pin(target),complete_decoding=pin(home/'complete-original-records.json'),
                              constructed_truth_events=3,omitted_constructed_events=['initial 120-bp deletion'] if tag=='v1.0.7' else [])
            except Exception as error:
                failure=error;record.update(status='FAIL2_ORIGINAL_REFERENCE_CASE',error=repr(error))
            finally:
                if child is not None:
                    record['owned_before_cleanup']=members(child.pid)
                    for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                        if not any(r['state']!='Z' for r in members(child.pid)):break
                        try:os.killpg(child.pid,sig)
                        except ProcessLookupError:pass
                        deadline=time.monotonic()+seconds
                        while any(r['state']!='Z' for r in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                    child.wait(timeout=10)
                    record['owned_after_cleanup']=members(child.pid)
                    record['owned_live_after_cleanup']=[r for r in record['owned_after_cleanup'] if r['state']!='Z']
                    if record['owned_live_after_cleanup']:
                        failure=RuntimeError('Owned workers remain alive');record.update(status='FAIL2_OWNED_CLEANUP_INCOMPLETE',error=repr(failure))
                record.update(exit_code=code,elapsed_seconds=time.monotonic()-started)
                save(home/'COMPLETED.json',record);state['cases'].append(record)
            if failure:raise failure
            print(name+': complete original '+str(len(decoded['records']))+'-record control captured; truth has 3 events',flush=True)
            return target
        discovered={}
        for tag in setup['probes']:
            for sample in ['sampleA','sampleB']:
                discovered[tag,sample]=call(tag,sample,tag.replace('.','')+'-'+sample)
        call('v2.0.2','sampleA','v202-tandem',['--tandem-repeats',fixtures/'tandem-repeats.bed'])
        call('v2.4','sampleA','v24-phased',['--phase'])
        call('v1.0.7','sampleB','v107-force',['--Ivcf',discovered['v1.0.7','sampleA']])
        call('v2.0.7','sampleB','v207-force',['--genotype-vcf',discovered['v2.0.7','sampleA']])
        assert inventory(runtime/'venv')==json.loads((runtime/'RUNTIME-FILES.json').read_bytes())
        assert inventory(runtime/'sources')==json.loads((runtime/'SOURCE-COPY-FILES.json').read_bytes())
        assert all(pin(fixtures/n)==value for n,value in fixture_receipt['files'].items())
        assert pin(runtime/'COMPLETED.json')==before['runtime_receipt']
        state.update(status='PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY',
                     original_cases=14,original_records=sum(c['records'] for c in state['cases']),runtime_and_inputs_unchanged=True,
                     runtime=str(runtime),fixtures=str(fixtures),
                     source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__)),
                                  'tools/literature/setup_sniffles_reference_linux.py':pin(ROOT/'tools/literature/setup_sniffles_reference_linux.py')})
    except Exception as failure:
        state['error']=repr(failure)
        (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','All complete original controls passed.')+
        '\n\nThis is a constructed-alignment reference experiment. It does not validate a Java execution adapter, a biological dataset, downstream consensus/merging or a paper. '
        'Every case uses one explicitly selected thread; other caller settings are original defaults except the named tandem, phase or force-call option. '
        'Preserve failures and full original diagnostics. Failed or incomplete cases admit no analysis result.\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original complete-record scientific checks')
    raise SystemExit(main())
