# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create two explicit synthetic Tomtom recipes inside Linux/WSL; no analysis or installation."""
import argparse
import json
from pathlib import Path


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();root=args.out.absolute()
    if root.exists() or not root.parent.is_dir():raise ValueError('Use a NEW recipe directory with existing parent')
    models=Path(__file__).resolve().parent/'inputs/models.meme'
    if not models.is_file() or not str(models).startswith('/'):raise ValueError('Run with Linux Python inside WSL on Windows; the complete example inputs must exist')
    root.mkdir(mode=0o700)
    for name,mode,distance in [('text','TEXT','kullback'),('full','FULL_REPORT','ed')]:
        recipe=dict(schema_version=1,profile='linux-x86_64-tomtom-5.5.9',query=str(models),targets=[str(models)],distance=distance,reverse_complement=True,pseudocount=.1,minimum_overlap=1,internal=False,complete_scores=True,threshold=dict(kind='QVALUE',value=1),background=dict(source='QUERY',path=None),output_mode=mode,limits=dict(input_bytes=8388608,decoded_characters=8388608,matrix_values=1000000,table_bytes=8388608,wall_seconds=240,cpu_seconds=240,address_space_bytes=2147483648,file_bytes=67108864,workspace_bytes=536870912,files=10000,processes=32,threads=256,sampled_rss_bytes=1073741824,poll_millis=50))
        (root/(name+'.json')).write_text(json.dumps(recipe,indent=2)+'\n',encoding='utf-8',newline='\n')
    print('Created explicit TEXT/Kullback and FULL_REPORT/Euclidean recipes in '+str(root))


if __name__=='__main__':main()
