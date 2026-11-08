# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare project-derived data codec and supervisor independently of the immutable runtime binding."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);prior=ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow';target=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-workflow'
    supervisor=(prior/'supervisor.py').read_text().replace('SCFATES','GSEAPY').replace('scFates','GSEApy').replace('scfates','gseapy');codec=(prior/'codec.py').read_text();old='        if isinstance(value,(list,tuple)):return [self.clean(v) for v in value]';assert codec.count(old)==1;codec=codec.replace(old,"        if isinstance(value,(set,frozenset)):return dict(kind='unordered-set',values=[self.clean(v) for v in value])\n"+old)
    for name,text in [('supervisor.py',supervisor),('codec.py',codec)]:
        path=target/name;raw=text.encode()
        if path.exists():assert path.read_bytes()==raw,'Existing worker source protected'
        else:path.write_bytes(raw)
    files=['worker.py','codec.py','supervisor.py'];profile=dict(schema_version=1,files={n:pin(target/n) for n in files});path=target/'worker-profile.json';raw=(json.dumps(profile,indent=2)+'\n').encode()
    if path.exists() and path.read_bytes()!=raw:(out/'previous-worker-profile.json').write_bytes(path.read_bytes());path.write_bytes(raw)
    elif not path.exists():path.write_bytes(raw)
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*[target/n for n in [*files,'worker-profile.json']]]}
    for path in [target/n for n in [*files,'worker-profile.json']]:copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(path.read_bytes())
    save(out/'COMPLETED.json',dict(status='passed-prepared-original-GSEApy-worker-source-only',source_pins=pins,runtime_profile_changed=False,full_adapter_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Prepared worker/codec/supervisor; runtime binding unchanged')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable worker source checks')
    main()
