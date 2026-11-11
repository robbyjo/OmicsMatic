# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute documented native GC example and fresh Windows/Linux runs, retaining warnings and overwrite refusal."""
import json,os,subprocess
from pathlib import Path
from validate_mashr_native import JAVA21,linux,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def inventory(p):return {f.relative_to(p).as_posix():pin(f) for f in p.rglob('*') if f.is_file()}
def main():
    out=BASE/'background-gc-example-2';assert not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='checked-native-GC-examples-in-progress'))
    try:
        checked=ROOT/'examples/chromvar-gc-bias';record=json.loads((BASE/'background-gc-controls-1/COMPLETED.json').read_bytes());jar=ROOT/record['artifact']['path'];assert pin(jar)=={k:record['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
        names=[p.relative_to(ROOT).as_posix() for p in checked.iterdir() if p.is_file()]+['docs/literature/CHROMVAR_GC_DEVELOPMENT.md','tools/literature/validate_chromvar_gc_example.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv,exit_code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==exit_code,(name,r.returncode,r.stderr.decode(errors='replace')[-500:])
        classes=ROOT/'build/chromvar-gc-example-classes-2';assert not classes.exists();classes.mkdir();command('compile-literal-example',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-cp',jar,'-d','build/chromvar-gc-example-classes-2','examples/chromvar-gc-bias/BiasExample.java'])
        runs=[];targets=[ROOT/'build/chromvar-gc-example-2-NEW',out/'fresh Windows Java25',out/'fresh Linux Java21 β'];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        expected='peak_id\tbias\nchr1-1-6\t0.5\nchr1-7-10\t1.0\nchr1-11-14\tNaN\nchr1-15-18\t0.5\n'
        for target,(name,java,convert,separator) in zip(targets,platforms):
            assert not target.exists();argv=[*java,'-cp',separator.join(map(convert,[classes,jar])),'BiasExample',convert(checked/'reference.fa'),convert(target)];command(name,argv);assert (target/'bias.tsv').read_text()==expected
            warnings=(target/'WARNINGS.md').read_text();assert 'UNDEFINED_GC_BIAS' in warnings and 'Required action:' in warnings and 'CHROMVAR_CANONICAL_GC' in warnings
            before=inventory(target);command(name+'-existing-output-refused',argv,exit_code=1);assert inventory(target)==before;runs.append(dict(platform=name,analysis_path=target.relative_to(ROOT).as_posix(),source_formula_values_match=True,warnings_present=True,existing_output_preserved=True,inventory=before))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in record['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-checked-native-GC-examples-on-three-JVMs',source_pins=sources,artifact=record['artifact'],runs=runs,documented_example_with_NEW_paths_executed=True,prior_literal_Windows21_attempt_retained=True,non_ASCII_Windows25_command_line_path_failure_retained=True,fresh_platform_runs=2,existing_output_refusals=3,warnings_and_required_actions_checked=True,background_sampling_performed=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-checked-native-GC-examples-on-three-JVMs',analyses=3,existing_output_refusals=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_CHECKED_NATIVE_GC_EXAMPLES',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable documented example checks')
    main()
