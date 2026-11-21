"""Freeze genuine complete synthetic captures for portable graph admission tests."""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[4]
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();out.mkdir(parents=True,exist_ok=False)
    cases={'learn':'learn-base','run':'run-cell-root','disconnected':'run-two-partitions-one-root','unconverged':'learn-retain-original-unconverged'};inventory={};target=ROOT/'src/test/resources/org/jlinalg/cli/monocle100-graph.zip'
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for label,case in cases.items():
            root=args.matrix.resolve()/case/'capture';assert (root/'COMPLETED.json').exists() and json.loads((root/'analysis.json').read_bytes())['graph_admitted'] is True
            for path in sorted(root.rglob('*')):
                if not path.is_file():continue
                assert not path.is_symlink();raw=path.read_bytes();name=label+'/'+path.relative_to(root).as_posix();info=zipfile.ZipInfo(name,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw);inventory[name]=pin(raw)
    with (out/'RESULT.json').open('x',encoding='utf-8',newline='\n') as stream:json.dump(dict(status='PASS_GENUINE_SYNTHETIC_GRAPH_CAPTURE_FIXTURE',cases=cases,fixture=pin(target.read_bytes()),members=inventory),stream,indent=2);stream.write('\n')
    print('Frozen',len(inventory),'genuine complete capture members; fixture',target.stat().st_size,'bytes',flush=True);return 0
if __name__=='__main__':raise SystemExit(main())
