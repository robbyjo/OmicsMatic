# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain official R RNG sources and original Signac coverage/annotation controls."""
import argparse
import subprocess
import urllib.request
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();base=ROOT/'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists();out.mkdir();(out/'official-R').mkdir()
    downloads=[]
    for name in ['RNG.c','random.c']:
        attempts=[]
        for edition in ['tags/R-4-6-1','trunk']:
            url='https://svn.r-project.org/R/'+edition+'/src/main/'+name
            try:
                request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-validation/1.0'})
                with urllib.request.urlopen(request,timeout=60) as response:data=response.read(1000001);final=response.geturl()
                assert len(data)<=1000000 and b'RNG' in data
                path=out/'official-R'/name;path.write_bytes(data)
                downloads.append(dict(name=name,url=url,final_url=final,edition=edition,archive=pin(path),prior_attempts=attempts));break
            except Exception as e:attempts.append(dict(url=url,error=str(e)))
        else:raise RuntimeError('No authoritative R source available: '+str(attempts))
    save(out/'DOWNLOADS.json',downloads)
    argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla','tools/literature/collect_signac_coverage_controls.R',str(out/'original')]
    with (out/'stdout').open('xb') as a,(out/'stderr').open('xb') as b:result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=900)
    save(out/'PROCESS.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0,'Inspect retained original coverage logs'
    save(out/'COMPLETED.json',dict(status='retained-original-Signac-coverage-and-annotation-controls',
         result=pin(out/'original/RESULT.json'),source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/collect_signac_coverage_controls.R']},
         R_version='4.6.1',historical_whole_environments=False,upstream_functions_modified=False,downloads=downloads))
    print('Retained original coverage/annotation calculations and official R RNG sources')


if __name__=='__main__':main()
