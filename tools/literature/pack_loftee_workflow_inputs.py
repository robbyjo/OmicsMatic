# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pack only complete synthetic expanded input controls; no human reference or runtime software."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

NAMES=['input.vcf','genotyped.vcf','genotyped.vcf.gz','reference.fa','reference.fa.fai','reference.fa.gz','reference.fa.gz.fai','reference.fa.gz.gzi','genes.gtf.gz','genes.gtf.gz.tbi','gerp.bw','ancestor.fa','ancestor.fa.fai','conservation.sqlite','missing-schema.sqlite']

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--inputs',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();inputs=a.inputs.resolve();out=a.out.resolve();assert out.is_relative_to(ROOT/'src/test/resources/org/jlinalg/genetics/loftee-original') and not out.exists();receipt=json.loads((inputs/'COMPLETED.json').read_bytes());assert receipt['reference_origin']=='synthetic-only';files={n:pin(inputs/n) for n in NAMES};assert all(files[n]==receipt['files'][n] for n in NAMES)
    with zipfile.ZipFile(out,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name in sorted(NAMES):
            entry=zipfile.ZipInfo(name,date_time=(2026,11,7,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,(inputs/name).read_bytes())
    save(inputs/'PACKED.json',dict(status='packed-complete-synthetic-inputs-only',archive=dict(path=out.relative_to(ROOT).as_posix(),**pin(out)),members=files,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},runtime_analysis_executed=False));print(out.relative_to(ROOT).as_posix())

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact fixture checks')
    main()
