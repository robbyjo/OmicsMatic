# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the real main dispatcher and compare complete native outputs byte-for-byte."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mrmega_workflow import BASE,DISTRO,linux


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_ROOT_DISPATCH_CONTROLS',whole_goal_complete=False)
    try:
        controls=BASE/'workflow-controls-1';cases=json.loads((controls/'cases.json').read_bytes());canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        root_pins={};artifacts={}
        for compiler,folder in [('javac21','catalog-root-build21'),('javac25','catalog-root-build25')]:
            built=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_MRMEGA_ROOT_SUPPLEMENT_WITH_UNCHANGED_PARENT_SCIENTIFIC_BYTES'
            assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items())
            artifact=ROOT/built['published_artifact']['path'];assert pin(artifact)=={k:built['published_artifact'][k] for k in ['bytes','sha256']};artifacts[compiler]=artifact;root_pins[artifact.relative_to(ROOT).as_posix()]=pin(artifact)
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [artifacts[compiler],canonical]);prefix=[*platforms[platform],'-cp',cp,'org.jlinalg.cli.JLinAlgCli'];rows=[]
            def run(folder,arguments,code=0):
                folder.mkdir();command=prefix+arguments;save(folder/'command.json',command);process=subprocess.run(command,capture_output=True,timeout=120)
                (folder/'stdout').write_bytes(process.stdout);(folder/'stderr').write_bytes(process.stderr);save(folder/'process.json',dict(exit_code=process.returncode))
                assert process.returncode==code,(folder,process.stderr.decode(errors='replace'));return process
            root_help=run(home/'root-help',['--help']);assert b'mrmega-tools --help' in root_help.stdout
            help=run(home/'mrmega-help',['mrmega-tools','--help']);assert b'Native MR-MEGA' in help.stdout
            wrong=run(home/'mrmega-invalid',['mrmega-tools','--invented','1'],2);assert b'Required action:' in wrong.stderr
            for route in ['chipseeker-tools','chromvar-tools','deseq2-tools','deseq2-normalize','htseq-tools','meme-tools','monocle-tools','signac-tools']:
                answer=run(home/('prior-help-'+route),[route,'--help']);assert answer.stdout
            for case in cases:
                home_case=home/case['id'];native=home_case/'native'
                run(home_case,['mrmega-tools','--settings',convert(controls/case['settings_file']),'--file-list',convert(controls/case['file_lists']['linux' if platform=='linux21' else 'windows']),'--out',convert(native)])
                previous=BASE/'workflow-validation-4'/(compiler+'-'+platform)/case['id']/'native'
                names={p.name for p in previous.iterdir() if p.is_file()};assert names=={p.name for p in native.iterdir() if p.is_file()}
                for name in names-{'run.json'}:assert (native/name).read_bytes()==(previous/name).read_bytes(),(case['id'],name)
                assert json.loads((native/'run.json').read_bytes())==json.loads((previous/'run.json').read_bytes())
                rows.append(dict(id=case['id'],all_native_tables_settings_and_warnings_byte_identical=True,semantic_run_receipt_equal=True,
                    files={n:pin(native/n) for n in sorted(names)}))
            print('PASS root dispatcher22 workflows/11 help-refusal routes '+compiler+'/'+platform,flush=True)
            return dict(compiler=compiler,platform=platform,cases=rows,dispatcher_contracts=11)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,[(c,p) for c in artifacts for p in platforms]))
        assert pin(canonical)==canonical_pin and all(pin(ROOT/n)==v for n,v in root_pins.items())
        state.update(status='PASS_MRMEGA_ROOT_132_COMPLETE_NATIVE_WORKFLOWS_AND_66_DISPATCH_CONTRACTS',runs=runs,
            complete_root_workflow_comparisons=132,dispatcher_contracts=66,canonical_executable_unchanged=canonical_pin,root_artifacts=root_pins,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java']})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable root dispatcher or full-output preservation checks')
    raise SystemExit(main())
