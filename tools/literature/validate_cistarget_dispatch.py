# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete routed cisTarget flows with sealed standalone runs and preserve prior routes."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_cistarget_recovery import linux,DISTRO


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True);state=dict(status='FAIL2_CISTARGET_ROOT_DISPATCH_CONTROLS',implementation_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);artifacts={};root_pins={}
        for compiler,folder in [('javac21','catalog-root-build21-1'),('javac25','catalog-root-build25-1')]:
            built=json.loads((base/folder/'COMPLETED.json').read_bytes());assert built['status']=='PASS_CISTARGET_ROOT_WITH_ALL_PREDECESSOR_AND_NATIVE_SCIENCE_BYTES_UNCHANGED'
            assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());artifact=ROOT/built['artifact']['path'];assert pin(artifact)=={k:built['artifact'][k] for k in ['bytes','sha256']};artifacts[compiler]=artifact;root_pins[artifact.relative_to(ROOT).as_posix()]=pin(artifact)
        cases=json.loads((base/'workflow-controls-1/cases.json').read_bytes());assert len(cases)==16
        mrbase=ROOT/'build/literature-review/mrmega-runtime';mr_cases=json.loads((mrbase/'workflow-controls-1/cases.json').read_bytes())
        # Preserve one quantitative and one binary full predecessor flow per JVM/build.
        prior_cases=[];seen=set()
        for case in mr_cases:
            settings=json.loads((mrbase/'workflow-controls-1'/case['settings_file']).read_bytes());trait=settings['trait']
            if trait not in seen:prior_cases.append(case);seen.add(trait)
        assert seen=={'quantitative','binary'}
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve());cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [artifacts[compiler],canonical]);prefix=[*platforms[platform],'-cp',cp,'org.jlinalg.cli.JLinAlgCli']
            def run(name,args,wanted=0):
                logs=home/(name+'-process');logs.mkdir();command=prefix+args;save(logs/'command.json',command);process=subprocess.run(command,capture_output=True,timeout=180)
                (logs/'stdout').write_bytes(process.stdout);(logs/'stderr').write_bytes(process.stderr);save(logs/'process.json',dict(exit_code=process.returncode));assert process.returncode==wanted,(name,process.returncode,process.stderr.decode(errors='replace'));return process
            assert b'cistarget-tools --help' in run('root-help',['--help']).stdout
            assert b'Native cisTarget' in run('cistarget-help',['cistarget-tools','--help']).stdout
            assert b'Required action:' in run('cistarget-invalid',['cistarget-tools','--invented','1'],2).stderr
            for route in ['mrmega-tools','chipseeker-tools','chromvar-tools','deseq2-tools','deseq2-normalize','htseq-tools','meme-tools','monocle-tools','signac-tools']:assert run('prior-help-'+route,[route,'--help']).stdout
            def identical(native,previous,semantic_receipt=False):
                names={p.name for p in previous.iterdir() if p.is_file()};assert names=={p.name for p in native.iterdir() if p.is_file()}
                for name in names:
                    if semantic_receipt and name=='run.json':assert json.loads((native/name).read_bytes())==json.loads((previous/name).read_bytes())
                    else:assert (native/name).read_bytes()==(previous/name).read_bytes(),(native,name)
                return {n:pin(native/n) for n in names}
            report=[]
            for case in cases:
                inputs=base/'workflow-controls-1'/case['id'];native=home/case['id'];args=['cistarget-tools']
                for name in ['settings','rankings','signature','annotations']:args.extend(['--'+name,convert(inputs/(name+('.json' if name=='settings' else '.tsv')))])
                run(case['id'],args+['--out',convert(native)]);previous=base/'workflow-validation-4'/(compiler+'-'+platform)/case['id']
                report.append(dict(id=case['id'],all_output_bytes_identical=identical(native,previous)))
            for name,inputs,previous in [('literal',ROOT/'examples/cistarget-ranked-features',base/'literal-example-2'/(compiler+'-'+platform)/'native'),('original-feather',base/'feather-validation-3',base/'feather-validation-3'/(compiler+'-'+platform))]:
                native=home/name;args=['cistarget-tools']
                for key in ['settings','rankings','signature','annotations']:
                    path=inputs/(key+('.json' if key=='settings' else '.tsv'))
                    if name=='original-feather' and key=='rankings':path=inputs/'original-v1-export/rankings.tsv'
                    args.extend(['--'+key,convert(path)])
                run(name,args+['--out',convert(native)]);report.append(dict(id=name,all_output_bytes_identical=identical(native,previous)))
            predecessors=[]
            for case in prior_cases:
                native=home/('prior-'+case['id']);inputs=mrbase/'workflow-controls-1'
                run('prior-'+case['id'],['mrmega-tools','--settings',convert(inputs/case['settings_file']),'--file-list',convert(inputs/case['file_lists']['linux' if platform=='linux21' else 'windows']),'--out',convert(native)])
                previous=mrbase/'workflow-validation-4'/(compiler+'-'+platform)/case['id']/'native';predecessors.append(dict(id=case['id'],all_nonreceipt_files_byte_identical=True,run_receipt_semantically_equal=True,output_pins=identical(native,previous,True)))
            print('PASS18 complete cisTarget root flows,2 predecessor workflows,12 dispatch contracts '+compiler+'/'+platform,flush=True)
            return dict(compiler=compiler,platform=platform,cases=report,prior_workflows=predecessors,dispatcher_contracts=12)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,[(c,p) for c in artifacts for p in platforms]))
        assert pin(canonical)==before and all(pin(ROOT/n)==v for n,v in root_pins.items())
        state.update(status='PASS108_COMPLETE_CISTARGET_ROOT_FLOWS_12_PREDECESSOR_FLOWS_AND72_DISPATCH_CONTRACTS',runs=runs,complete_root_workflow_comparisons=108,predecessor_workflows=12,dispatcher_contracts=72,
            root_artifacts=root_pins,canonical_executable_unchanged=before,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java']})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS108_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable root route/result preservation checks')
    raise SystemExit(main())
