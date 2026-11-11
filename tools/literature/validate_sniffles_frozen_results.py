# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check both frozen verifiers across three JVMs and reject a rehashed false value."""
import argparse,copy,json,os,re,stat,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/sniffles-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['result','artifact21','artifact25','out']:parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    result=a.result.resolve();artifacts={'javac21':a.artifact21.resolve(),'javac25':a.artifact25.resolve()}
    state=dict(status='FAIL2_SNIFFLES_FROZEN_RESULT_CONTROLS',implementation_credit=False,whole_goal_complete=False,verifications=[])
    try:
        master=json.loads((result/'COMPLETED.json').read_bytes());assert master['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and master['analysis_admitted']
        total=sum(case['records'] for case in master['analyses']);assert total>0
        protected=[result/'COMPLETED.json',result/'result-files.json',result/'WARNINGS.md',*artifacts.values(),Path(__file__).resolve()]
        before={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
        runtimes={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def run(home,command,code):
            home.mkdir();save(home/'command.json',command);process=subprocess.run(command,capture_output=True,timeout=300)
            (home/'stdout').write_bytes(process.stdout);(home/'stderr').write_bytes(process.stderr);save(home/'process.json',dict(observed_exit_code=process.returncode,expected_exit_code=code))
            assert process.returncode==code,(home,process.returncode,process.stderr.decode(errors='replace'))
        for compiler,artifact in artifacts.items():
            for platform,prefix in runtimes.items():
                transform=linux if platform=='linux21' else lambda path:str(path.resolve());target=out/(compiler+'-'+platform+'-verify')
                command=[*prefix,'-cp',transform(artifact),'org.jlinalg.cli.SnifflesVerifyCli','--result',transform(result),'--out',transform(target)]
                run(out/(compiler+'-'+platform+'-process'),command,0)
                receipt=json.loads((target/'COMPLETED.json').read_bytes());assert receipt['verification_passed'] and receipt['complete_records']==total
                assert all(warning in (target/'WARNINGS.md').read_text(encoding='utf8') for warning in master['warnings'])
                state['verifications'].append(dict(compiler=compiler,platform=platform,receipt=pin(target/'COMPLETED.json'),complete_records=total,original_warnings_retained=True))
        clone=out/'false-rehashed-view';clone.mkdir();pending=[result]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    path=Path(item.path);target=clone/path.relative_to(result);mode=item.stat(follow_symlinks=False).st_mode
                    if stat.S_ISDIR(mode):target.mkdir();pending.append(path)
                    else:assert stat.S_ISREG(mode);target.write_bytes(path.read_bytes())
        case=next(row for row in master['analyses'] if row['records']>0);target=clone/'analyses'/case['id']/'native/variants.jsonl'
        raw=target.read_bytes();rows=[json.loads(line) for line in raw.splitlines()];assert isinstance(rows[0]['read_support'],int)
        pattern=rb'("read_support":)([0-9]+)';match=re.search(pattern,raw);assert match and int(match.group(2))==rows[0]['read_support']
        replacement=b'999' if rows[0]['read_support']!=999 else b'998';changed=raw[:match.start(2)]+replacement+raw[match.end(2):]
        expected=copy.deepcopy(rows);expected[0]['read_support']=int(replacement)
        assert [json.loads(line) for line in changed.splitlines()]==expected
        target.write_bytes(changed);save(out/'TAMPER.json',dict(controlled_copy_only=True,path=target.relative_to(clone).as_posix(),
            only_one_scalar_value_changed=True,original_read_support=rows[0]['read_support'],false_read_support=int(replacement),original_bytes=len(raw),changed_bytes=len(changed)))
        index=clone/'result-files.json';files=json.loads(index.read_bytes());files[target.relative_to(clone).as_posix()]=pin(target);save(index,files)
        altered=copy.deepcopy(master);altered['result_files_pin']=pin(index);save(clone/'COMPLETED.json',altered)
        rejected=out/'false-view-verification';command=[*runtimes['windows21'],'-cp',str(artifacts['javac21']),'org.jlinalg.cli.SnifflesVerifyCli','--result',str(clone),'--out',str(rejected)]
        run(out/'false-view-process',command,2);failure=json.loads((rejected/'COMPLETED.json').read_bytes())
        assert not failure['verification_passed'] and 'Frozen native full-record view differs from original VCF' in failure['error']
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_SIX_SNIFFLES_PORTABLE_FROZEN_CHECKS_AND_REHASHED_FALSE_VALUE_REJECTION_ONLY',complete_cases=len(master['analyses']),
            complete_records=total,protected_inputs_unchanged=before,false_rehashed_view_rejected=pin(rejected/'COMPLETED.json'),
            verification_scope='Frozen integrity and structural consistency only; no original caller/runtime execution, independent SV refit, authenticity or biological certification.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen/result and false-value checks')
    raise SystemExit(main())
