# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add the original NumPy BSD notice while preserving every tested scientific class byte."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    build=base/('recovery-build21-3' if a.compiler=='javac21' else 'recovery-build25-3');record=json.loads((build/'COMPLETED.json').read_bytes());assert record['status']=='PASS_ISOLATED_JAVA17_CISTARGET_RECOVERY_COMPONENT_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    parent=ROOT/record['artifact']['path'];assert pin(parent)=={k:record['artifact'][k] for k in ['bytes','sha256']}
    license=ROOT/'docs/literature/CISTARGET_NUMPY_LICENSE.txt';reference=base/'numpy-source-1';sources=json.loads((reference/'sources.json').read_bytes())
    assert license.read_bytes()==(reference/'LICENSE.txt').read_bytes() and pin(license)=={k:sources['LICENSE.txt'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(parent) as z:payload={n:z.read(n) for n in z.namelist()}
    assert 'META-INF/NUMPY-LICENSE.txt' not in payload;old=dict(payload);payload['META-INF/NUMPY-LICENSE.txt']=license.read_bytes();jar=out/'cistarget-recovery-development.jar'
    with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(payload.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    with zipfile.ZipFile(jar) as z:assert all(z.read(n)==raw for n,raw in old.items())
    save(out/'COMPLETED.json',dict(status='PASS_LICENSED_CISTARGET_RECOVERY_ARTIFACT_WITH_UNCHANGED_SCIENTIFIC_BYTES',compiler=a.compiler,
        artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=record['artifact'],all_parent_entries_unchanged=True,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),license]},analysis_reexecuted=False,implementation_credit=False,whole_goal_complete=False));print('Added original BSD notice; all tested class bytes unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific byte preservation checks')
    main()
