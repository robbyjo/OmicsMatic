#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect real original sharing summaries with immutable admitted R/package runtime."""
import argparse,json,shutil,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mashr_preparation as bridge
from mashr_original_runtime import census,environment,pin,run_owned,write
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        rt=bridge.runtime(args.runtime);app=bridge.native(None,Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'))
        source=Path(__file__).with_name('mashr-sharing-controls.R');bound={p.relative_to(ROOT).as_posix():pin(p) for p in [source,Path(__file__),ROOT/'src/main/java/org/jlinalg/shrinkage/MashSharing.java',ROOT/'src/main/java/org/jlinalg/shrinkage/MashSignScores.java']}
        posterior=BASE/'original-preparation-development-1/controls-3/pca-ed/result/native-analysis/execution/posterior';before=census(posterior)
        shutil.copyfile(source,out/'original-controls.R')
        process=run_owned([rt['rhome']/'bin/Rscript.exe','--vanilla',out/'original-controls.R',rt['home']/'library',out/'original',posterior],out,'original',environment(rt['rhome'],rt['home'],out/'scratch'),rt['monitor'])
        assert (out/'original/STATUS.txt').read_text().strip()=='ORIGINAL_MASHR_SHARING_AND_EXPLICIT_MARGINAL_PRODUCT_CONTROLS_COMPLETE'
        cases=(out/'original/cases.tsv').read_text().splitlines()[1:];assert len(cases)==32,len(cases)
        bridge.checked_state(rt,app);assert all(pin(ROOT/n)==v for n,v in bound.items()) and before==census(posterior)
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-original-mashr-sharing-control-collection',cases=len(cases),process=process,source_pins=bound,posterior_source_files=before,runtime_setup=pin(args.runtime),whole_paper_reproduced=False,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));print('Collected '+str(len(cases))+' actual original sharing/product cases');return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_ORIGINAL_SHARING_CONTROLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_ORIGINAL_SHARING_CONTROLS: '+str(failure),file=sys.stderr);sys.exit(2)
