# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the exact beginner Java example against retained and fresh typed original results."""
import argparse,json,re,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    guide=ROOT/'docs/literature/RSUBREAD_RESULT_VALUES.md';code=re.findall(r'```java\n(.*?)\n```',guide.read_text(encoding='utf8'),re.S);assert len(code)==1
    source=out/'InspectRsubread.java';source.write_text(code[0]+'\n',encoding='utf8',newline='\n');base=ROOT/'build/literature-review/rsubread-runtime';jar=base/'result-api-build-1/rsubread-result-values-api.jar'
    cases=[('retained',base/'result-exports-1/2260/single-meta','5 rows, 1 libraries'),('integer',base/'result-edges-1/2260/numeric-ids/values','2 rows, 1 libraries'),('logical',base/'result-edges-1/2232-R441/logical-ids/values','3 rows, 1 libraries'),('empty-junction',base/'result-edges-1/2260/empty-junctions/values','5 rows, 1 libraries')]
    for name,values,expected in cases:
        command=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx512m','--class-path',str(jar),str(source),str(values)]
        run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0 and run.stdout.decode().strip()==expected,name
    save(out/'COMPLETED.json',dict(status='passed-four-exact-beginner-Java-examples',examples=4,artifact=pin(jar),source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),guide]},analysis_admitted=False,implementation_credit=False,whole_goal_complete=False));print('passed-four-exact-beginner-Java-examples')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact documentation-example gates')
    main()
