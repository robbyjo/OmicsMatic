# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check original Rust arithmetic relationships and all observed ranked/null outcomes on three JVMs."""
import argparse,hashlib,json,re,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def body(source,name):
    pattern=r'(?m)^\s*(?:pub )?fn '+name+r'\b[^;{]*\{';matches=list(re.finditer(pattern,source));assert len(matches)==1,(name,len(matches));start=matches[0].end()-1;depth=0
    for end in range(start,len(source)):
        if source[end]=='{':depth+=1
        if source[end]=='}':
            depth-=1
            if depth==0:
                raw=source[start:end+1];tokens=re.sub(r'\s+','',re.sub(r'//[^\n]*','',raw));return dict(exact_sha256=hashlib.sha256(raw.encode()).hexdigest(),comment_whitespace_normalized_sha256=hashlib.sha256(tokens.encode()).hexdigest())
    raise AssertionError('Unclosed original function')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','original','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_RANKED_NATIVE_AUDIT',runs=[],full_adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-GSEApy-runtime-transport-and-export-foundation-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};original=a.original.resolve();report=json.loads((original/'COMPLETED.json').read_bytes());assert report['status']=='passed-33-complete-original-GSEApy-workflow-controls-only' and report['complete_runtime_trees_unchanged'];original_pins={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-input-pins.json',original_pins)
        functions={};source_pins={}
        for edition in ['1.0.4','1.1.2','1.1.3','1.1.8']:
            functions[edition]={}
            for file,names in [('stats.rs',['normalize','pval','fdr','fwer_pval']),('algorithm.rs',['running_enrichment_score','fast_random_walk','fast_random_walk_ss']),('utils.rs',['mean'])]:
                path=ROOT/'build/literature-review/gseapy-runtime/backend-source-1'/edition/'source/src'/file;source_pins[path.relative_to(ROOT).as_posix()]=pin(path)
                for name in names:functions[edition][name]=body(path.read_text(),name)
        for name in functions['1.0.4']:assert len({f[name]['comment_whitespace_normalized_sha256'] for f in functions.values()})==1,'Original math bodies differ: '+name
        callers={}
        for edition in functions:
            source=(ROOT/'build/literature-review/gseapy-runtime/backend-source-1'/edition/'source/src/stats.rs').read_text();callers[edition]=dict(gsea_body=body(source,'gsea'),phenotype_dense_metric='absolute-power-before-walk' if edition=='1.1.8' else 'raw-signed-walk-with-separately-weighted-ES')
            caller=source[source.index('pub fn gsea('):source.index('pub fn prerank(')]
            if edition=='1.1.8':assert caller.index('self.rankings.push')<caller.index('sorted_metric.iter_mut().for_each')<caller.index('let run_es =')
            else:assert caller.index('let run_es =')<caller.index('let weighted_gm: Vec')
        save(out/'original-function-relationships.json',dict(status='passed-shared-original-arithmetic-bodies-after-comment-whitespace-normalization',functions=functions,edition_specific_callers=callers,source_pins=source_pins))
        for label,java,linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir()
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            def command(args):return ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java,*args] if linux else [java,*args]
            version=subprocess.run(command(['-version']),capture_output=True,timeout=60);(home/'java-version.stderr').write_bytes(version.stderr);assert version.returncode==0 and ('"'+major+'.' in (version.stdout+version.stderr).decode(errors='replace') or '"'+major+'"' in (version.stdout+version.stderr).decode(errors='replace'));arguments=command(['-Xmx2g','-cp',path(jar),'org.jlinalg.cli.GseapyRankedMatrix',path(original),path(home/'audit.json')]);run=subprocess.run(arguments,capture_output=True,cwd=ROOT,timeout=180);(home/'audit.stdout').write_bytes(run.stdout);(home/'audit.stderr').write_bytes(run.stderr);save(home/'process.json',dict(command=arguments,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');state['runs'].append(dict(jvm=label,audit=json.loads((home/'audit.json').read_bytes())));print(label,'passed',flush=True)
            arguments=command(['-Xmx512m','-cp',path(jar),'org.jlinalg.cli.GseapyRankedContractMatrix',path(home/'contracts.json')]);run=subprocess.run(arguments,capture_output=True,cwd=ROOT,timeout=60);(home/'contracts.stdout').write_bytes(run.stdout);(home/'contracts.stderr').write_bytes(run.stderr);save(home/'contracts.process.json',dict(command=arguments,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');contract=json.loads((home/'contracts.json').read_bytes());assert contract['status']=='passed-native-ranked-boundaries-domains-and-immutability' and contract['invalid_inputs_rejected']==13;state['runs'][-1]['contracts']=contract
        assert all(pin(original/n)==v for n,v in original_pins.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-three-JVM-original-ranked-and-null-arithmetic-only',artifact=built['artifact'],original_input_pins=pin(out/'original-input-pins.json'),original_function_relationships=pin(out/'original-function-relationships.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
