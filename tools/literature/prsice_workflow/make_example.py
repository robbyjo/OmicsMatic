#!/usr/bin/env python3
"""Create artificial inputs and explicit requests; do not download or run an analysis."""
from pathlib import Path
import argparse,json
from fixture import generate,request

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--runtime-home',type=Path,required=True);a=p.parse_args();root=a.out.absolute();root.mkdir();generate(root/'inputs')
    for name in ('target-clump-score','linear','binary','reference-frequency','prepared'):
        body=request(name,a.runtime_home.absolute(),'inputs',name);(root/(name+'.json')).write_text(json.dumps(body,indent=2)+'\n',encoding='utf-8',newline='\n')
    print('Created artificial inputs and five complete request files. No analysis executed.')
if __name__=='__main__':main()
