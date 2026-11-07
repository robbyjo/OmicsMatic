# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run exact checked requests against fresh full-package original results and captured three-JVM replay."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
from validate_signac_annotation_workflow import compare_tss
from validate_signac_fragment_workflow import compare_matrix
BASE=ROOT/'build/literature-review/signac-runtime'
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='exact-native-annotation-examples-and-fresh-original-controls-in-progress'))
    try:
        build=read(BASE/'annotation-workflow-build-1/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        example=ROOT/'examples/signac-annotations';sources={p.relative_to(ROOT).as_posix():pin(p) for p in example.iterdir() if p.is_file()}
        for name in ['tools/literature/validate_signac_annotation_examples.py','tools/literature/collect_signac_annotation_example_reference.R','tools/literature/validate_signac_annotation_workflow.py','tools/literature/validate_signac_fragment_workflow.py']:sources[name]=pin(ROOT/name)
        save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,argv,env=None):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1200:])
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');reference=out/'fresh-original';reference.mkdir();command('original',['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',ROOT/'tools/literature/collect_signac_annotation_example_reference.R',example,reference],env)
        original=read(reference/'original.json');assert original['status']=='fresh-full-original-Signac-checked-annotation-examples' and len(original['results'])==4;runs=[]
        for name in ['fast','profile','derived','gene','verify','lsi']:
            path=example/(name+'-request.json');request=read(path);cli='org.jlinalg.cli.SignacLsiRequestCli' if name=='lsi' else 'org.jlinalg.cli.SignacAnnotationsRequestCli';command(name,[JAVA21/'java.exe','-Xmx512m','-cp',jar,cli,'--request',path])
            if name=='verify':continue
            target=(example/(request['options']['out'] if name=='lsi' else request['out'])).resolve();assert target.is_relative_to(ROOT/'build')
            if name in ['fast','profile','derived']:compare_tss(target,original['results'][name]['value'])
            elif name=='gene':compare_matrix(target,original['results'][name]['value']['matrix'])
            for platform,java,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]:
                path=out/(name+'-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-cp',convert(jar),cli,'--request',convert(path)])
            runs.append(dict(name=name,analysis_path=target.relative_to(ROOT).as_posix(),actual_run=True,captured_verifications=3,fresh_original_compared=name!='lsi'))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-exact-annotation-examples-and-fresh-full-original-comparisons',source_pins=sources,artifact=build['artifact'],runs=runs,native_runs=4,actual_LSI_fits=1,fresh_original_comparisons=4,original_package='1.17.1',historical_packages_executed=False,full_Signac_workflow_available=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-exact-annotation-examples-and-fresh-full-original-comparisons',native_runs=4,fresh_original_comparisons=4)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_CHECKED_ANNOTATION_EXAMPLES',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact checked annotation examples')
    main()
