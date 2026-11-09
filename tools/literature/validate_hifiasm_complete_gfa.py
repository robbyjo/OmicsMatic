# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay the entire27-case GFA corpus in six bounded metadata partitions on all three JVMs."""
import argparse,collections,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_COMPLETE_HIFIASM_GFA_REPLAY',partitions={},analysis_admitted=False,implementation_credit=False)
    try:
        fixture=a.fixtures.resolve();producer=json.loads((fixture/'COMPLETED.json').read_bytes());assert pin(fixture/'cases.json')==producer['cases'];cases=json.loads((fixture/'cases.json').read_bytes())['cases'];groups=collections.defaultdict(list)
        for case in cases:groups[case['path'].split('/paper-modes-1/')[1].split('/')[0]].append(case)
        assert len(groups)==6 and len(cases)==246 and len({c['path'] for c in cases})==246
        for edition,part in sorted(groups.items()):
            home=out/edition;home.mkdir();inputs=home/'fixtures';inputs.mkdir();save(inputs/'cases.json',dict(schema_version=1,cases=part));record=dict(status='prepared-complete-retained-hifiasm-graph-components-only',graph_views=len(part),segment_rows=sum(len(c['segments']) for c in part),link_rows=sum(len(c['links']) for c in part),placement_rows=sum(len(c['placements']) for c in part),cases=pin(inputs/'cases.json'),partition_parent=producer['cases']);save(inputs/'COMPLETED.json',record);assert (inputs/'cases.json').stat().st_size<67108864;command=[__import__('sys').executable,'-B',str(ROOT/'tools/literature/validate_hifiasm_gfa.py'),'--build',str(a.build.resolve()),'--fixtures',str(inputs),'--out',str(home/'replay')];run=subprocess.run(command,capture_output=True,timeout=1200);(home/'replay.stdout').write_bytes(run.stdout);(home/'replay.stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stdout.decode()+run.stderr.decode();report=json.loads((home/'replay/COMPLETED.json').read_bytes());assert len(report['runs'])==3 and all(r['report']['graph_views']==len(part) for r in report['runs']);state['partitions'][edition]=dict(graph_views=len(part),report=report);print(edition,len(part),'complete graph views passed all three JVMs',flush=True)
        assert sum(v['graph_views'] for v in state['partitions'].values())==producer['graph_views'] and pin(fixture/'cases.json')==producer['cases'];state.update(status='passed-three-JVM-complete-246-original-hifiasm-graph-and-FASTA-views',graph_views=246,per_jvm_complete_graph_views=246,partitioned_for_original_64MiB_metadata_capacity=True,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),ROOT/'tools/literature/validate_hifiasm_gfa.py']})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete graph population checks')
    raise SystemExit(main())
