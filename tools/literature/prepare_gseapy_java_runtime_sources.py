# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive separate GSEApy runtime transport/export sources from the unchanged owned-process implementation."""
import argparse,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);java=ROOT/'src/main/java/org/jlinalg/cli';sources={}
    for old,new in [('ScfatesWorkflowRuntime.java','GseapyWorkflowRuntime.java'),('ScfatesSetupCli.java','GseapySetupCli.java')]:
        s=(java/old).read_text().replace('SCFATES','GSEAPY').replace('Scfates','Gseapy').replace('scFates','GSEApy').replace('scfates','gseapy').replace('native SimplePPT arithmetic','native GSEApy statistics')
        if new=='GseapyWorkflowRuntime.java':
            old='text(health.get("edition")).equals(request.edition())';assert s.count(old)==1;s=s.replace(old,'object(health.get("editions")).containsKey(request.edition())');s=s.replace('Complete original GSEApy/SimplePPT health unavailable','Complete original four-edition GSEApy health unavailable')
        else:
            s=s.replace('GSEApy1.2.5/SimplePPT1.1.7 original wheels, pinned dependencies','GSEApy1.0.4/1.1.2/1.1.3/1.1.8 original wheels and exact base requirements')
        path=java/new;raw=s.encode()
        if path.exists():assert path.read_bytes()==raw,'Existing source protected'
        else:path.write_bytes(raw)
        sources[path.relative_to(ROOT).as_posix()]=pin(path);copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(raw)
    save(out/'COMPLETED.json',dict(status='prepared-separate-GSEApy-runtime-transport-and-export-sources-only',source_pins=sources,full_adapter_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Prepared typed versioned runtime transport/export; no complete adapter admitted')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source derivation checks')
    main()
