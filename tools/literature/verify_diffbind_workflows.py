"""Verify the scoped DiffBind source context and retained complete-workflow evidence."""
from pathlib import Path
import argparse,hashlib,json,os,stat,subprocess,sys
from diffbind_primary import audit as primary_audit

ROOT=Path(__file__).resolve().parents[2]
QUALIFICATION='docs/literature/diffbind-validation/QUALIFICATION.json'
def require(condition,message):
    if not condition:raise ValueError(message)
def read(path):return json.loads(Path(path).read_bytes())
def pin(path):
    path=Path(path);before=path.stat();h=hashlib.sha256();size=0
    with path.open('rb')as stream:
        for block in iter(lambda:stream.read(1048576),b''):h.update(block);size+=len(block)
    after=path.stat();require((before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns)and size==before.st_size,'File changed while hashing: '+str(path))
    return dict(bytes=size,sha256=h.hexdigest())
def member(name):
    require(isinstance(name,str)and name and'\\'not in name and':'not in name and all(part not in('','.','..')for part in name.split('/'))and not name.startswith('/'),'Unsafe relative evidence member');return name
def source_record(root):
    q=read(root/QUALIFICATION)
    require(q['status']=='PASS_SCOPED_DIFFBIND_BAM_PEAK_MODELS_REPORTS_AND_PLOTS','Incomplete DiffBind qualification')
    require(q['Windows_JUnit']==q['Linux_JUnit']==21 and q['JUnit_skips']==0 and q['complete_captures']==q['complete_fresh_saved_replays']==4,'Incomplete project/workflow checks')
    require(q['independent_raw_count_cells']==3600 and q['full_model_native_projection_rows']==1800 and q['direct_original_reference_files']==14 and q['controlled_refusals']==7,'Incomplete independent/original/negative controls')
    require(q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and q['new_source_reviews']==3 and q['existing_source_review_expanded']==1,'Expanded family or review credit')
    require(all(q[k]is False for k in('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete')),'Expanded scientific scope')
    for name,expected in q['protected_sources'].items():require(pin(root/member(name))==expected,'Source context changed: '+name+'. Use the matching published revision or retained source-context.zip; never refresh historical hashes')
    require(pin(root/'docs/literature/diffbind-validation/source-context.zip')==q['source_context_archive'],'Changed retained source context')
    return q
def runtime_inventory(inventory):
    nodes=inventory['nodes'];found=set()
    for raw in inventory['roots']:
        path=Path(raw);require(path.is_dir()and not path.is_symlink()and str(path.resolve(strict=True))==raw,'Required canonical runtime root missing: '+raw)
        for directory,dirs,files in os.walk(path,followlinks=False):
            found.add(str(Path(directory)));found.update(str(Path(directory)/name)for name in dirs+files)
    found.update(inventory['files']);require(found==set(nodes),'Complete runtime node population changed')
    for name,row in nodes.items():
        path=Path(name);s=path.lstat();kind=row['kind']
        if kind in('symlink','dangling-symlink'):
            require(path.is_symlink()and os.readlink(path)==row['target'],'Runtime link changed: '+name)
            if kind=='symlink':require(str(path.resolve(strict=True))==row['real_path'],'Runtime link resolution changed: '+name)
            else:require(not path.exists(),'Previously unresolved runtime link became resolvable: '+name)
        elif kind=='directory':require(stat.S_ISDIR(s.st_mode)and stat.S_IMODE(s.st_mode)==row['mode'],'Runtime directory/mode changed: '+name)
        else:require(kind=='file'and stat.S_ISREG(s.st_mode)and stat.S_IMODE(s.st_mode)==row['mode']and pin(path)=={k:row[k]for k in('bytes','sha256')},'Complete runtime file/mode changed: '+name)
def private_evidence(root,private):
    q=source_record(root);manifest=private/'qualification-evidence-1/EVIDENCE.json';require(pin(manifest)==q['private_evidence_manifest'],'Changed private evidence manifest');e=read(manifest)
    require(e['status']=='FROZEN_SCOPED_DIFFBIND_EVIDENCE'and e['whole_goal_complete']is False,'Invalid evidence scope')
    for name,expected in e['files'].items():require(pin(private/member(name))==expected,'Changed retained private evidence: '+name)
    require(pin(private/member(e['producer_path']))==q['producer'],'Changed exact producing JAR')
    for case in e['cases']:
        capture=private/member(case['capture']);replay=private/member(case['replay'])
        require(read(capture/'COMPLETED.json')['status']=='COMPLETE_DIFFBIND_SCOPED_WORKFLOW'and read(replay/'COMPLETED.json')['status']=='PASS_COMPLETE_DIFFBIND_BINARY_AND_NUMERICAL_REPLAY','Incomplete full captured/replayed workflow')
        require(read(capture/'canonical.json')==read(replay/'canonical.json'),'Complete saved canonical output population/bytes differ')
    controls=read(private/'controls-Linux-1/COMPLETED.json');require(controls['status']=='PASS_SCOPED_DIFFBIND_CONTROLLED_REFUSALS'and len(controls['controls'])==7 and all(c['no_complete_result']for c in controls['controls']),'Incomplete controlled refusals')
    require(sys.platform.startswith('linux'),'Use permitted Linux/WSL for complete private runtime verification')
    runtime_inventory(read(private/'runtime-binding-Linux-3/inventory.json'))
    return q,e
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private-root',type=Path,required=True);p.add_argument('--cache',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--out',type=Path);args=p.parse_args()
    try:
        root=args.root.resolve(strict=True);private=args.private_root.resolve(strict=True);q,e=private_evidence(root,private);primary=primary_audit(root,args.cache)
        result=dict(status='PASS_RETAINED_DIFFBIND_COMPLETE_SOURCE_AND_RUNTIME_EVIDENCE',cases=4,controlled_refusals=7,primary=primary,fresh_replays_executed_now=0,whole_goal_complete=False)
        if args.execute:
            require(args.out is not None,'--execute requires a NEW private --out directory');out=args.out.absolute();require(not out.exists()and out.parent.resolve(strict=True)==out.parent,'Canonical parent and NEW verification directory required');out.mkdir()
            for index,case in enumerate(e['cases'],1):
                command=['/usr/bin/java','-Xmx2g','-Djava.awt.headless=true','-jar',str(private/e['producer_path']),'diffbind-tools','--verify',str(private/case['capture']),'--scratch',str(out/('replay-'+str(index)))]
                (out/('command-'+str(index)+'.json')).write_text(json.dumps(command,indent=2)+'\n')
                with(out/('stdout-'+str(index))).open('xb')as stdout,(out/('stderr-'+str(index))).open('xb')as stderr:
                    child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True)
                    try:code=child.wait(timeout=3720)
                    except BaseException:
                        if child.poll()is None:child.terminate()
                        try:child.wait(timeout=20)
                        except subprocess.TimeoutExpired:child.kill();child.wait()
                        raise
                require(code==0,'Fresh saved replay failed; preserve the private output and inspect its warnings/logs');result['fresh_replays_executed_now']+=1
            result['status']='PASS_FOUR_FRESH_DIFFBIND_COMPLETE_BINARY_AND_NUMERICAL_REPLAYS';(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps(result,sort_keys=True));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
        print(('SKIP3 DEPENDENT DIFFBIND EVIDENCE CHECK SKIPPED: 'if code==3 else'FAIL2 NO DIFFBIND EVIDENCE ADMITTED: ')+str(error)+'. Required action: restore exact retained sources, evidence, original XMLs, producer and permitted runtime; inspect all warnings. Preserve failures and historical seals. Use NEW output paths. No complete scientific result is supplied by a failed or skipped check.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
