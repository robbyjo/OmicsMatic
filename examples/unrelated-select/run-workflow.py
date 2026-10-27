#!/usr/bin/env python3
"""Select a maximal unrelated set from a complete synthetic graph using only Java."""
import argparse,json,subprocess,tempfile
from pathlib import Path

HERE=Path(__file__).resolve().parent
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute()
    if out.exists():parser.error('--out must be new;existing outputs are preserved')
    out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='jlinalg-unrelated-example-') as work:
        carrier=Path(work)/'options.json'
        if not str(carrier).isascii():parser.error('Use an ASCII system TEMP for the Windows Java options carrier')
        options=dict(samples=str(HERE/'samples.tsv'),pairs=str(HERE/'pairs.tsv'),cutoff='0.125',**{'pair-generation-cutoff':'0.05','pair-coverage':'complete-above-cutoff', 'scale':'additive-grm','ties':'input-order','out':str(out)})
        carrier.write_text(json.dumps(options,ensure_ascii=False),encoding='utf-8');result=subprocess.run([args.java,'-jar',str(args.jar.absolute()),'unrelated-select','--options-file',str(carrier)])
    print('Required action: inspect '+str(out/'WARNINGS.md')+' and every eligibility/graph decision.')
    if result.returncode==0:
        if (out/'decisions.tsv').read_bytes()!=(HERE/'expected-decisions.tsv').read_bytes():raise RuntimeError('Synthetic independent expected decisions differ;retain output and investigate')
        print('Every synthetic decision matches the independent expected table.')
    return result.returncode
if __name__=='__main__':raise SystemExit(main())
