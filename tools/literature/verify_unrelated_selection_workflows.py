#!/usr/bin/env python3
"""Verify actual packaged native selection on synthetic science and failure controls."""
import argparse,ctypes,hashlib,json,os,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--java21',required=True);parser.add_argument('--java25',required=True);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();out.mkdir(parents=True,exist_ok=False);example=ROOT/'examples/unrelated-select';source={p.relative_to(ROOT).as_posix():sha(p) for p in example.iterdir() if p.is_file()};jar=args.jar.absolute();jar_sha=sha(jar);runs=[];env=os.environ.copy();flags=0
    if os.name=='nt':
        buffer=ctypes.create_unicode_buffer(32768);ctypes.windll.kernel32.GetWindowsDirectoryW(buffer,len(buffer));env['SystemRoot']=buffer.value;env['WINDIR']=buffer.value;ctypes.windll.kernel32.SetErrorMode(0x1|0x2|0x8000);flags=subprocess.CREATE_NO_WINDOW
    def execute(label,java,options,wanted,expected=None):
        carrier=out/(label+'-options.json');carrier.write_text(json.dumps(options,ensure_ascii=False),encoding='utf-8');command=[java,'-jar',str(jar),'unrelated-select','--options-file',str(carrier)];result=subprocess.run(command,cwd=ROOT,env=env,creationflags=flags,capture_output=True,timeout=45);(out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr);target=Path(options['out']);assert result.returncode==wanted,(label,result.returncode,result.stderr.decode(errors='replace'));assert 'Required action:' in (target/'WARNINGS.md').read_text();assert ('ANALYSIS WARNINGS' if wanted==0 else 'ANALYSIS FAILED') in result.stderr.decode(errors='replace');status=json.loads((target/'analysis-status.json').read_text())['status'];assert status==('completed-with-review-warnings' if wanted==0 else 'failed'),(label,status)
        if expected is not None:assert (target/'decisions.tsv').read_text()==expected,label
        if wanted:assert not (target/'results.json').exists() and not (target/'retained-samples.tsv').exists(),label
        runs.append(dict(label=label,exit_code=result.returncode,status=status,output=str(target),warnings_sha256=sha(target/'WARNINGS.md')))
    for label,java in [('java21',args.java21),('java25',args.java25)]:
        def options(name):return dict(samples=str(example/'samples.tsv'),pairs=str(example/'pairs.tsv'),cutoff='0.125',**{'pair-generation-cutoff':'0.05','pair-coverage':'complete-above-cutoff','scale':'additive-grm','ties':'input-order','out':str(out/(label+'-'+name))})
        execute(label+'-native',java,options('native'),0,(example/'expected-decisions.tsv').read_text());base=options('boundary');base['cutoff']='0.25';expected='sample_id\teligible\tinitial_degree\tstatus\texcluded_by\n'+''.join(identity+'\t'+eligible+'\t0\t'+('retained' if eligible=='true' else 'ineligible')+'\t\n' for identity,eligible in [line.split('\t') for line in (example/'samples.tsv').read_text().splitlines()[1:]]);execute(label+'-boundary',java,base,0,expected)
        base=options('lexical');base['ties']='lexical-id';execute(label+'-lexical',java,base,0,(example/'expected-decisions.tsv').read_text().replace('excluded-related\tleaf2','excluded-related\tleaf1'));assert (Path(base['out'])/'retained-visit-order.txt').read_text()=='isolated\nleaf1\nleaf2\n'
        base=options('empty');samples=out/(label+'-empty-samples.tsv');samples.write_text((example/'samples.tsv').read_text().replace('true','false'));base['samples']=str(samples);execute(label+'-empty',java,base,0);assert 'EMPTY ELIGIBLE COHORT' in (Path(base['out'])/'WARNINGS.md').read_text()
        for name,key,value in [('sparse','pair-generation-cutoff','0.2'),('scale','scale','king-kinship'),('coverage','pair-coverage',None)]:
            base=options(name)
            if value is None:base.pop(key)
            else:base[key]=value
            execute(label+'-'+name,java,base,2)
        for name,rows in [('duplicate','center\tleaf1\t.3\nleaf1\tcenter\t.2\n'),('unknown','center\tunknown\t.3\n')]:
            base=options(name);pairs=out/(label+'-'+name+'-pairs.tsv');pairs.write_text('sample_a\tsample_b\trelatedness\n'+rows);base['pairs']=str(pairs);execute(label+'-'+name,java,base,2)
        base=options('native');target=Path(base['out']);before={p.relative_to(target).as_posix():sha(p) for p in target.rglob('*') if p.is_file()};carrier=out/(label+'-existing-options.json');carrier.write_text(json.dumps(base));result=subprocess.run([java,'-jar',str(jar),'unrelated-select','--options-file',str(carrier)],env=env,creationflags=flags,capture_output=True,timeout=45);assert result.returncode==2 and b'Required action:' in result.stderr;assert before=={p.relative_to(target).as_posix():sha(p) for p in target.rglob('*') if p.is_file()};runs.append(dict(label=label+'-existing',exit_code=2,status='existing-output-preserved'))
    # The public runner is unchanged; the Unicode output path travels inside UTF8 JSON.
    target=out/'公开 示例';result=subprocess.run([sys.executable,'-X','utf8',str(example/'run-workflow.py'),'--java',args.java21,'--jar',str(jar),'--out',str(target)],env=env,creationflags=flags,capture_output=True,timeout=45);(out/'public-example.stdout').write_bytes(result.stdout);(out/'public-example.stderr').write_bytes(result.stderr);assert result.returncode==0,(result.stdout,result.stderr);assert (target/'decisions.tsv').read_bytes()==(example/'expected-decisions.tsv').read_bytes();runs.append(dict(label='public-java21-Unicode-example',exit_code=0,status='unchanged-public-runner-passed'))
    assert sha(jar)==jar_sha
    for name,digest in source.items():assert sha(ROOT/name)==digest,name
    record=dict(status='passed',candidate=str(jar),candidate_sha256=jar_sha,cases=len(runs),scientific_cases=9,failure_controls=12,runs=runs,example_source_sha256=source,scope='Actual packaged Java21/25 exact full decisions,strict boundary,explicit lexical/input ties,empty eligible warning;invalid sparse coverage,scale,missing declaration,duplicate/unknown pairs and existing-output preservation. Unchanged public Java21 Unicode example passed. No upstream SAIGE integration implied.');assert len(runs)==21;(out/'verification.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n');print(json.dumps({key:record[key] for key in ['status','cases','scientific_cases','failure_controls','candidate_sha256']}));return 0
if __name__=='__main__':raise SystemExit(main())
