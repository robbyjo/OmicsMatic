"""Execute all33 complete original controls through both explicitly selected engines."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,subprocess,sys,time
ROOT=Path(__file__).resolve().parents[4]
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False)
    fixture=ROOT/'src/test/resources/org/jlinalg/singlecell/monocle100/weights.json';controls=json.loads(fixture.read_bytes());assert len(controls)==33
    save(out/'STARTED.json',dict(jar=str(args.jar.resolve()),jar_sha256=hashlib.sha256(args.jar.read_bytes()).hexdigest(),expected_fixture_sha256=hashlib.sha256(fixture.read_bytes()).hexdigest(),complete_cases=33,explicit_engines=['original','java'],started_unix=time.time()))
    def one(item):
        name,engine=item;destination=out/(engine+'-'+name)
        command=[sys.executable,'-X','utf8','-B',str(Path(__file__).with_name('execute.py')),'--out',str(destination),'--jar',str(args.jar.resolve()),'--case',name,'--engine',engine]
        with (out/(engine+'-'+name+'.stdout')).open('xb') as stdout,(out/(engine+'-'+name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        expected=0 if controls[name]['reference']['returned'] else 2
        complete=(destination/'capture/COMPLETED.json').exists();admitted=json.loads((destination/'capture/analysis.json').read_bytes())['weights_admitted']
        row=dict(case=name,engine=engine,command=command,exit_code=process.returncode,expected_exit_code=expected,complete=complete,weights_admitted=admitted,all_expected=process.returncode==expected and complete==(expected==0) and admitted==(expected==0))
        save(out/(engine+'-'+name+'.RESULT.json'),row);print(engine,name,'exit',process.returncode,'expected',expected,flush=True);return engine+'/'+name,row
    items=[(name,engine) for name in controls for engine in ['original','java']]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(one,items))
    all_expected=all(row['all_expected'] for row in results.values());save(out/'RESULT.json',dict(status='PASS_ALL66_ACTUAL_NEIGHBOR_EXPECTATIONS' if all_expected else 'FAILED_ACTUAL_EXPECTATIONS',cases_per_engine=33,total=len(results),completed_graphs=sum(row['complete'] for row in results.values()),retained_failures=sum(row['exit_code']!=0 for row in results.values()),results=results,new_catalog_credit=0,whole_goal_complete=False))
    assert all_expected,'Retain all attempts and inspect failing case evidence';return 0
if __name__=='__main__':raise SystemExit(main())
