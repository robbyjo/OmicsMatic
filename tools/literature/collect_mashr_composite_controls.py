#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the reviewed standalone original function without installing all of dreamlet."""
import argparse,json,shutil,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mashr_preparation as bridge
from mashr_original_runtime import environment,pin,run_owned,write
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        rt=bridge.runtime(args.runtime);app=bridge.native(None,Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'))
        intake=BASE/'composite-development-1/source-intake-1';receipt=json.loads((intake/'dreamlet-receipt.json').read_text(encoding='utf-8'));original=intake/'dreamlet/R/compositePosteriorTest.R';expected=receipt['files']['R/compositePosteriorTest.R'];assert pin(original)=={k:expected[k] for k in ['bytes','sha256']}
        script=Path(__file__).with_name('mashr-composite-controls.R');sources=[Path(__file__),script,original,intake/'dreamlet/DESCRIPTION',intake/'Artistic-2.0.txt'];bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
        shutil.copyfile(original,out/'original-compositePosteriorTest.R');shutil.copyfile(script,out/'controls.R');shutil.copyfile(intake/'Artistic-2.0.txt',out/'Artistic-2.0.txt');shutil.copyfile(intake/'dreamlet/DESCRIPTION',out/'original-DESCRIPTION')
        process=run_owned([rt['rhome']/'bin/Rscript.exe','--vanilla',out/'controls.R',out/'original-compositePosteriorTest.R',out/'original'],out,'original',environment(rt['rhome'],rt['home'],out/'scratch'),rt['monitor'])
        assert (out/'original/STATUS.txt').read_text().strip()=='ORIGINAL_DREAMLET_COMPOSITE_CONTROLS_COMPLETE';cases=(out/'original/cases.tsv').read_text().splitlines()[1:];assert len(cases)==30,len(cases)
        bridge.checked_state(rt,app);assert all(pin(ROOT/n)==v for n,v in bound.items())
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-unmodified-original-dreamlet-composite-controls',cases=len(cases),process=process,source_pins=bound,original_source_commit=receipt['commit'],original_source_version='1.9.3',source_function_loaded_directly=True,full_dreamlet_package_installed_or_validated=False,paper_binary_edition_identified=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));print('Collected '+str(len(cases))+' unmodified-source composite cases');return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_ORIGINAL_COMPOSITE_CONTROLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_ORIGINAL_COMPOSITE_CONTROLS: '+str(failure),file=sys.stderr);sys.exit(2)
