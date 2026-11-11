# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile/run the documented native API example with actual candidate JAR and warning checks."""
import argparse
import json
import math
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21, linux, pin
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    save(out/'INCOMPLETE.json',dict(status='example-validation-in-progress'))
    try:
        validation=json.loads((BASE/'normalization-matrix-1/COMPLETED.json').read_bytes())
        assert all(pin(ROOT/n)==v for n,v in validation['source_pins'].items())
        jar=ROOT/validation['artifact']['path'];assert pin(jar)=={k:validation['artifact'][k] for k in ['bytes','sha256']}
        example=ROOT/'examples/signac-normalization/NormalizePeaks.java';example_pin=pin(example)
        (out/'NormalizePeaks.java').write_bytes(example.read_bytes());compiled=out/'classes';compiled.mkdir()
        def command(name,argv,expected=0):
            argv=list(map(str,argv))
            result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=60,check=False)
            (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
            save(out/(name+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==expected,(name,result.returncode)
            return result.stdout.decode('utf-8').splitlines(),result.stderr.decode('utf-8')
        command('compile-example',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,example])
        platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),
                   ('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        runs=[]
        for name,java,convert,separator in platforms:
            for empty in [False,True]:
                output,errors=command(name+('-empty-cell' if empty else '-default'),[*java,'-cp',separator.join(map(convert,[compiled,jar])),'NormalizePeaks',*(['--empty-cell'] if empty else [])])
                assert output[0]=='feature\tcell\ttfidf' and output[-1]=='selected(count>5)\tpeak-C' and len(output)==7
                n=4 if empty else 3
                expected=[('peak-A','cell-1',math.log1p((n/3)*0.25*1e4)),('peak-B','cell-1',math.log1p((n/3)*0.75*1e4)),
                          ('peak-A','cell-2',math.log1p((n/3)*(2/6)*1e4)),('peak-C','cell-2',math.log1p((n/12)*(4/6)*1e4)),('peak-C','cell-3',math.log1p((n/12)*1e4))]
                for row,(feature,cell,value) in zip(output[1:-1],expected,strict=True):
                    fields=row.split('\t');assert fields[:2]==[feature,cell] and math.isclose(float(fields[2]),value,rel_tol=5e-12)
                if empty:assert 'WARNING ZERO_CELL_COUNTS: Some cells contain 0 total counts' in errors and 'Required action: Review empty-cell QC' in errors
                else:assert errors.strip()==''
                runs.append(dict(platform=name,empty_cell=empty,passed=True))
            _,errors=command(name+'-bad-argument',[*java,'-cp',separator.join(map(convert,[compiled,jar])),'NormalizePeaks','--unknown'],expected=2)
            assert errors.startswith('Usage: NormalizePeaks');runs.append(dict(platform=name,invalid_arguments_rejected=True))
        assert pin(example)==example_pin and all(pin(ROOT/n)==v for n,v in validation['source_pins'].items())
        save(out/'COMPLETED.json',dict(status='passed-documented-native-Signac-example-and-prominent-warning-output',runs=runs,
                                     source_pins={'examples/signac-normalization/NormalizePeaks.java':example_pin,'tools/literature/validate_signac_example.py':pin(Path(__file__))},
                                     candidate=validation['artifact'],public_workflow_admitted=False))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-documented-native-Signac-example-and-prominent-warning-output',runs=len(runs))))
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='FAILED_EXAMPLE_VALIDATION_NO_CREDIT',error=str(error)));raise

if __name__=='__main__':main()
