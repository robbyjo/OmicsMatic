# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run exact original Monocle2 differential tests on synthetic counts, preserving all existing libraries."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--setup',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    setup=a.setup.resolve(strict=True);assert setup.is_relative_to(base);installed=json.loads((setup/'RESULT.json').read_bytes());assert installed['status']=='INSTALLED_ORIGINAL_MONOCLE2_REFERENCE_ONLY' and installed['preexisting_libraries_unchanged']
    libraries=[str(setup/'R-lib'),*json.loads((setup/'STARTED.json').read_bytes())['libraries']];before=inventory(map(Path,libraries));save(out/'LIBRARIES-BEFORE.json',before)
    source=ROOT/'tools/literature/probe_monocle2_reference.R';wanted=pin(source);(out/'source-used.R').write_bytes(source.read_bytes())
    save(out/'STARTED.json',dict(source_pin=wanted,runner_pin=pin(Path(__file__)),libraries=libraries,setup_result=pin(setup/'RESULT.json'),setup_started=pin(setup/'STARTED.json')))
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(source),str(out),*libraries]
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
        r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1'),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=600)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert pin(source)==wanted
    after=inventory(map(Path,libraries));save(out/'LIBRARIES-AFTER.json',after);assert before==after
    save(out/'PROVENANCE.json',dict(libraries_unchanged=True,existing_files_checked=sum(map(len,before.values())),source_pin=wanted,original_package='monocle 2.40.0',analysis_admitted=False,Java_adapter_validated=False))
    result=json.loads((out/'RESULT.json').read_bytes());print(json.dumps({k:v for k,v in result.items() if k not in ['messages','warnings']}),flush=True)
    return r.returncode


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/library preservation checks')
    raise SystemExit(main())
