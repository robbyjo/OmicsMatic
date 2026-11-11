# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile and run the exact documented Java snippet against the native candidate."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};guide=ROOT/'docs/literature/SMR_STATISTICS_DEVELOPMENT.md';snippet=guide.read_text(encoding='utf8').split('```java\n',1)[1].split('\n```',1)[0];lines=snippet.splitlines();imports='\n'.join(line for line in lines if line.startswith('import '));body='\n'.join(line for line in lines if not line.startswith('import '));source=out/'RunSmrNativeExample.java';source.write_text(imports+'\npublic final class RunSmrNativeExample {public static void main(String[] args){\n'+body+'\nif(ratio.effect()!=.2 || Math.abs(ratio.standardError()-Math.sqrt(.00064))>1e-16 || !(withOverlap.standardError()<ratio.standardError()))throw new AssertionError("Documented native example changed");\n}}\n',encoding='utf8',newline='\n');pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),guide]}
    for name,args in [('compile',[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),str(source)]),('run',[str(JAVA21/'java.exe'),'-cp',str(out)+';'+str(jar),'RunSmrNativeExample'])]:
        r=subprocess.run(args,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'-process.json'),dict(command=args,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-exact-documented-native-SMR-example',artifact=build['artifact'],source_pins=pins,snippet_compiled_from_document=True,original_SMR_runtime_required=False,SMR_or_HEIDI_workflow_admitted=False,whole_goal_complete=False));print('Exact documented native Java example compiled and passed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example/source checks')
    main()
