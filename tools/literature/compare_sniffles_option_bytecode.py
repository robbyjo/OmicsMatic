# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare unchanged execution/admission paths around the isolated typed-option patch.

Inspects javap-resolved instructions and bootstrap recipes, not independent
execution, authenticity or biological accuracy. Original tests remain tied to
their exact artifacts; only the unchanged paths are eligible for reuse.
"""
import argparse,json,re,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def cp(text):return re.sub(r'#\d+','#CP',text)


def inspect_workflow(javap,out,artifact,label):
        run=subprocess.run([str(javap),'-c','-p','-classpath',str(artifact),'org.jlinalg.cli.SnifflesWorkflowCli'],capture_output=True,timeout=60)
        assert run.returncode==0;(out/(label+'.instructions')).write_bytes(run.stdout);methods={};name=None
        for line in run.stdout.decode('utf8').splitlines():
            if re.match(r'^  \S.*\(.*\)(?: throws [^;]+)?;$',line):
                name=line.strip();assert name not in methods;methods[name]=[]
            elif name and line.strip()!='}':
                left,marker,right=cp(line).partition('//')
                # javap pads CP references to align comments. Normalize that
                # formatting while retaining resolved comments/string values.
                methods[name].append(' '.join(left.split())+(' //'+right if marker else ''))
        normalized={key:'\n'.join(lines).strip() for key,lines in methods.items()}
        run=subprocess.run([str(javap),'-v','-p','-classpath',str(artifact),'org.jlinalg.cli.SnifflesWorkflowCli'],capture_output=True,timeout=60)
        assert run.returncode==0;(out/(label+'.verbose')).write_bytes(run.stdout)
        lines=run.stdout.decode('utf8').splitlines();index=lines.index('BootstrapMethods:');bootstrap=[]
        for line in lines[index+1:]:
            if line and not line[0].isspace():break
            bootstrap.append(cp(line))
        assert bootstrap and normalized;return normalized,'\n'.join(bootstrap)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    javap=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javap.exe');results=[]
    preview=base/'option-interface-preview-1';prepared=json.loads((preview/'COMPLETED.json').read_bytes())
    for compiler,build in [('javac21','execution-build-14'),('javac25','execution-build-25-5')]:
        previous=base/build/'sniffles-execution-development.jar';candidate=ROOT/prepared['artifacts'][compiler]['path']
        old,old_bootstrap=inspect_workflow(javap,out,previous,compiler+'-base');new,new_bootstrap=inspect_workflow(javap,out,candidate,compiler+'-candidate')
        assert old.keys()==new.keys() and old_bootstrap==new_bootstrap,'Execution/bootstrap population or recipes changed'
        changed=[key for key in old if old[key]!=new[key]]
        assert len(changed)==1 and changed[0].startswith('private static java.util.List<java.lang.String> arguments('),changed
        assert 'public static int run(java.lang.String[]);' in old
        with zipfile.ZipFile(previous) as before,zipfile.ZipFile(candidate) as after:
            assert before.namelist()==after.namelist()
            altered=[name for name in before.namelist() if before.read(name)!=after.read(name)]
            assert altered and all(name.startswith(('org/jlinalg/cli/SnifflesWorkflowRequest','org/jlinalg/cli/SnifflesWorkflowCli')) and name.endswith('.class') for name in altered)
        results.append(dict(compiler=compiler,base_artifact=pin(previous),isolated_artifact=pin(candidate),workflow_methods=len(old),
            changed_methods=changed,unchanged_methods=[key for key in old if key not in changed],bootstrap_recipes_unchanged=True,
            all_process_host_native_VCF_and_resource_bytes_unchanged=True,changed_class_family_members=altered))
    save(out/'COMPLETED.json',dict(status='PASS_BOTH_OPTION_INTERFACE_EXECUTION_ADMISSION_PATH_COMPARISONS_ONLY',
        comparisons=results,implementation_credit=False,whole_goal_complete=False,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
        scope='Exact javap instruction/offset/target/symbol/bootstrap equality outside the argument mapper; request parser/record changes and argument behavior require their own actual option controls. Does not authenticate execution or certify biology.'))
    print('Both compilers: execution/admission methods and bootstrap recipes match; only argument construction changes; low-level/native/resource bytes unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable execution-path and bootstrap comparisons')
    main()
