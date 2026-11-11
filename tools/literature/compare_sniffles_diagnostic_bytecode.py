# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Confirm causal diagnostics leave successful execution, arguments and admission unchanged."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from compare_sniffles_option_bytecode import inspect_workflow


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    old=json.loads((base/'option-interface-preview-1/COMPLETED.json').read_bytes());new=json.loads((base/'diagnostic-interface-preview-1/COMPLETED.json').read_bytes())
    assert old['status']=='PASS_ISOLATED_SNIFFLES_OPTION_INTERFACE_PREVIEW_BUILD_ONLY' and new['status']=='PASS_ISOLATED_SNIFFLES_DIAGNOSTIC_INTERFACE_BUILD_ONLY'
    javap=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javap.exe');reports=[]
    for compiler in ['javac21','javac25']:
        previous=ROOT/old['artifacts'][compiler]['path'];candidate=ROOT/new['artifacts'][compiler]['path']
        assert pin(previous)=={key:old['artifacts'][compiler][key] for key in ['bytes','sha256']}
        assert pin(candidate)=={key:new['artifacts'][compiler][key] for key in ['bytes','sha256']}
        before,bootstrap_before=inspect_workflow(javap,out,previous,compiler+'-option')
        after,bootstrap_after=inspect_workflow(javap,out,candidate,compiler+'-diagnostic')
        added=set(after)-set(before);assert len(added)==1 and next(iter(added)).startswith('private static java.lang.String failureText(')
        assert not set(before)-set(after) and bootstrap_before==bootstrap_after
        changed=[name for name in before if before[name]!=after[name]]
        assert len(changed)==1 and changed[0].startswith('private static void details('),changed
        assert before['public static int run(java.lang.String[]);']==after['public static int run(java.lang.String[]);']
        with zipfile.ZipFile(previous) as first,zipfile.ZipFile(candidate) as second:
            assert first.namelist()==second.namelist();entries=[name for name in first.namelist() if first.read(name)!=second.read(name)]
            assert entries==['org/jlinalg/cli/SnifflesWorkflowCli.class'],entries
        reports.append(dict(compiler=compiler,previous_artifact=pin(previous),diagnostic_artifact=pin(candidate),changed_methods=changed,added_methods=sorted(added),
            unchanged_execution_argument_admission_and_other_methods=[name for name in before if name not in changed],bootstrap_recipes_unchanged=True,
            all_request_process_host_native_VCF_and_resource_bytes_unchanged=True))
    source=[Path(__file__).resolve(),ROOT/'tools/literature/compare_sniffles_option_bytecode.py']
    save(out/'COMPLETED.json',dict(status='PASS_BOTH_CAUSAL_DIAGNOSTIC_ONLY_BYTECODE_COMPARISONS',comparisons=reports,implementation_credit=False,whole_goal_complete=False,
        source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in source},
        scope='Only failure message formatting changed. Instructions, offsets/targets/resolved symbols and bootstrap recipes of all execution/argument/admission paths remain identical. Not a new execution/authenticity/biological test.'))
    print('Both compilers: only details/failureText change; successful argument, execution, admission and all supporting class/resource bytes remain unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable diagnostic-only bytecode comparisons')
    main()
