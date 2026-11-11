# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recover a complete direct-original site-coverage reference from its saved successful relative-path run."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();direct=base/'site-coverage-relative-diagnosis-1';diagnosis=json.loads((direct/'DIAGNOSIS.json').read_bytes());assert diagnosis['returned'] and diagnosis['original_functions_unchanged'] and diagnosis['coverage_bytes_unchanged'];source=direct/'original-return.rds';source_pin=pin(source);ref=base/'reference-setup-3';libs=[ref/'R-lib',*map(Path,json.loads((ref/'STARTED.json').read_bytes())['libraries'])];before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);script=ROOT/'tools/literature/export_dndscv_saved_reference.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,direct/'DIAGNOSIS.json',source]};command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source),*map(str,libs)];save(out/'STARTED.json',dict(command=command,source_pins=pins,model_refitted=False))
    for n in pins:
        if n.endswith('.rds'):continue
        target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
    try:
        r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en'),stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(out/'stdout').write_bytes(r.stdout);(out/'stderr').write_bytes(r.stderr);save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0 and all(pin(ROOT/n)==v for n,v in pins.items());assert pin(source)==source_pin
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);assert before==after
    save(out/'COMPLETED.json',dict(status='exported-complete-original-site-coverage-reference-without-refit',source_pins=pins,preserved_direct_result=source_pin,independent_original_functions_unchanged=True,relative_path_avoids_UCSC_drive_protocol_without_data_changes=True,original_absolute_path_failure_retained=True,preexisting_libraries_unchanged=True,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Exported complete independent relative-path site-coverage reference; original result retained; no refit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original reference/source checks')
    main()
