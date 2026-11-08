# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add a separate execution observer/transport while retaining every validated foundation source unchanged."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def generated():
    prior=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-workflow';target=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-execution';java=ROOT/'src/main/java/org/jlinalg/cli';worker=(prior/'worker.py').read_text();old='            return None\n        def enrich(self,frame,event,value):';assert worker.count(old)==1
    new='''            if event=='call' and frame.f_code.co_name=='calc_pvalues' and Path(frame.f_code.co_filename).resolve()==Path(importlib.import_module('gseapy.stats').__file__).resolve():
                frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.stats
            return None
        def stats(self,frame,event,value):
            if event=='return' and value is not None:
                # Observe the original completed list; never consume/replace its returned zip iterator.
                self.writer.save('observed-enrichr-statistics.json',dict(values=frame.f_locals['vals'],query=frame.f_locals['query'],population=frame.f_locals['bg'],query_size=frame.f_locals['k']));self.events.append('complete-original-local-enrichment-statistics-before-formatting')
            return self.stats
        def enrich(self,frame,event,value):'''
    worker=worker.replace(old,new);runtime=(java/'GseapyWorkflowRuntime.java').read_text().replace('GseapyWorkflowRuntime','GseapyExecutionRuntime');old='"/org/jlinalg/omics/gseapy-workflow/"+name';assert runtime.count(old)==1;runtime=runtime.replace(old,'"/org/jlinalg/omics/"+(Set.of("worker.py","codec.py","supervisor.py","worker-profile.json").contains(name)?"gseapy-execution/":"gseapy-workflow/")+name');setup=(java/'GseapySetupCli.java').read_text().replace('GseapySetupCli','GseapyExecutionSetupCli').replace('GseapyWorkflowRuntime','GseapyExecutionRuntime')
    return {target/'worker.py':worker.encode(),target/'codec.py':(prior/'codec.py').read_bytes(),target/'supervisor.py':(prior/'supervisor.py').read_bytes(),java/'GseapyExecutionRuntime.java':runtime.encode(),java/'GseapyExecutionSetupCli.java':setup.encode()}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);files=generated()
    for path,raw in files.items():
        path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists():assert path.read_bytes()==raw,'Existing execution source protected'
        else:path.write_bytes(raw)
    target=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-execution';profile=target/'worker-profile.json';raw=(json.dumps(dict(schema_version=1,files={n:pin(target/n) for n in ['worker.py','codec.py','supervisor.py']}),indent=2)+'\n').encode()
    if profile.exists():assert profile.read_bytes()==raw
    else:profile.write_bytes(raw)
    files[profile]=raw;pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),*files]}
    for path,raw in files.items():copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(raw)
    save(out/'COMPLETED.json',dict(status='prepared-additive-GSEApy-execution-observer-and-transport-only',source_pins=pins,foundation_sources_changed=False,runtime_binding_changed=False,full_adapter_admitted=False,whole_goal_complete=False));print('Prepared additive execution layer; original runtime and validated foundation unchanged')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/provenance checks')
    main()
