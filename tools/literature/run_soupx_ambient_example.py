# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented native ambient arithmetic example; no correction or upstream runtime."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source=ROOT/'examples/soupx-ambient-core/Example.java';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),source]};classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace');command=[str(JAVA21/'java.exe'),'-cp',str(classes)+';'+str(jar),'Example'];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=30);(out/'stdout').write_bytes(r.stdout);(out/'stderr').write_bytes(r.stderr);save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0 and r.stdout.decode('utf8').replace('\r\n','\n')=='Selected droplets: [empty1, empty2]\nSoup fractions: [0.5, 0.5]\nAnalytical Poisson rho: 0.1\nNo count correction was performed.\n';assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='passed-documented-native-ambient-arithmetic-example',artifact=build['artifact'],source_pins=pins,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Executed native ambient arithmetic example; no correction performed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/example checks')
    main()
