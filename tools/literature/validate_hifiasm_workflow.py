# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run fresh public upstream/native workflows and compare EVERY graph with separate original controls."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','originals','runtimes','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_PUBLIC_WORKFLOW_CONTROLS',editions={},analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};fixtures=a.fixtures.resolve();producer=json.loads((fixtures/'COMPLETED.json').read_bytes());assert producer['status']=='prepared-twenty-seven-explicit-supported-original-hifiasm-controls' and pin(fixtures/'request.json')==producer['request'];original=json.loads((fixtures/'request.json').read_bytes());runtimes=a.runtimes.resolve();binding=json.loads((runtimes/'COMPLETED.json').read_bytes());assert binding['status']=='passed-six-original-hifiasm-host-runtime-bindings-only';editions=list(dict.fromkeys(c['edition'] for c in original['cases']));requests=out/'requests';requests.mkdir()
        for edition in editions:
            inputs={};analyses=[];home=out/edition;home.mkdir();base=a.originals.resolve()/edition/'original';reference=json.loads((base/'COMPLETED.json').read_bytes());assert reference['status']=='PASS_COMPLETE_ORIGINAL_HIFIASM_CONTROL_BATCH_ONLY'
            def reads(name):
                if name not in inputs:inputs[name]=dict(original['inputs'][name],kind='reads')
                return name
            for case in original['cases']:
                if case['edition']!=edition:continue
                mode=case['assembly_mode'];spec=dict(id='a-'+case['id'],mode=mode,reads=[reads('ont' if mode=='ont' else 'hifi')],threads=1,bloom_bits=0)
                if mode.endswith('-ul'):spec['ultralong']=[reads('ul')]
                if mode.startswith('hic'):spec['hic_pairs']=[dict(first=reads('hic1'),second=reads('hic2'))]
                if mode.startswith('trio'):
                    for name,key in [('parent1','paternal'),('parent2','maternal')]:file=base/'parents'/(name+'.yak');inputs[name]=dict(path=linux(file),**pin(file),kind='yak');spec[key]=name
                if case.get('purge_level') is not None:spec['purge_level']=case['purge_level']
                if case.get('disable_post_join'):spec['disable_post_join']=True
                analyses.append(spec)
            manifest=ROOT/binding['editions'][edition]['receipt']['path'];assert pin(manifest)=={k:binding['editions'][edition]['receipt'][k] for k in ['bytes','sha256']};request=dict(schema_version=1,out=linux(home/'workflow'),runtime=linux(manifest),edition=edition,inputs=inputs,analyses=analyses,limits_profile='bounded-v1');save(requests/(edition+'.json'),request)
        def execute(edition):
            home=out/edition;command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(jar),'org.jlinalg.cli.HifiasmWorkflowCli','--request',linux(requests/(edition+'.json'))];print('Starting PUBLIC workflow',edition,flush=True)
            with (home/'workflow.stdout').open('xb') as first,(home/'workflow.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=9000)
            save(home/'workflow.process.json',dict(command=command,exit_code=run.returncode));report=json.loads((home/'workflow/COMPLETED.json').read_bytes());print(edition,report['status'],report.get('error',''),flush=True)
            if run.returncode!=0:return edition,dict(exit_code=run.returncode,report=report)
            assert report['status']=='PASS_COMPLETE_ORIGINAL_HIFIASM_AND_NATIVE_GRAPH_WORKFLOW' and report['analysis_admitted'] and report['host_bindings_unchanged'] and not report['owned_live_after_cleanup'];compared=0
            for case in report['analyses']:
                id=case['id'].removeprefix('a-');reference=a.originals.resolve()/edition/'original'/id/'assembly';actual=home/'workflow/analyses'/case['id']/'original';first={q.name:pin(q) for q in reference.glob('*.gfa')};second={q.name:pin(q) for q in actual.glob('*.gfa')};assert first==second,(edition,id,'Complete fresh original/public graph population or bytes differ');compared+=len(first)
                audit=json.loads((actual.parent/'graph-audit.json').read_bytes());assert len(audit)==len(second)
                for graph in audit:
                    raw=(actual/graph['graph']).read_text(encoding='utf8').splitlines();segments=[line.split('\t') for line in raw if line.startswith('S\t')];present=[s for s in segments if s[2]!='*'];omitted=[s[1] for s in segments if s[2]=='*'];assert graph['statistics']['segments']==len(segments) and graph['statistics']['links']==sum(s.startswith('L\t') for s in raw) and graph['statistics']['placements']==sum(s.startswith('A\t') for s in raw);assert graph['fasta']['written']==[s[1] for s in present] and graph['fasta']['omitted']==omitted
                    if graph['fasta_file']:
                        expected=''.join('>'+s[1]+'\n'+'\n'.join(s[2][i:i+80] for i in range(0,len(s[2]),80))+'\n' for s in present);assert (actual.parent/'native'/graph['fasta_file']).read_bytes()==expected.encode('utf8')
            command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx1g','-cp',linux(jar),'org.jlinalg.cli.HifiasmVerifyCli','--out',linux(home/'workflow')];verified=subprocess.run(command,capture_output=True,timeout=300);(home/'verify.stdout').write_bytes(verified.stdout);(home/'verify.stderr').write_bytes(verified.stderr);assert verified.returncode==0,verified.stderr.decode();return edition,dict(exit_code=run.returncode,report=report,graph_views_compared_byte_for_byte=compared,offline_integrity_verified=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            for edition,record in pool.map(execute,editions):state['editions'][edition]=record
        assert len(state['editions'])==6 and all(v['exit_code']==0 and v.get('offline_integrity_verified') for v in state['editions'].values());assert sum(len(v['report']['analyses']) for v in state['editions'].values())==27;assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-twenty-seven-fresh-public-original-hifiasm-and-native-graph-workflows',artifact=built['artifact'],case_count=27,graph_views=sum(v['graph_views_compared_byte_for_byte'] for v in state['editions'].values()),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/native workflow comparisons')
    raise SystemExit(main())
