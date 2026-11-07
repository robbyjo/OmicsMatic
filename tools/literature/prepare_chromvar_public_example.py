# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a complete public-root request pair from the already validated synthetic population."""
import json,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];DEST=ROOT/'examples/chromvar-tools';SOURCE=ROOT/'examples/chromvar-background'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    assert not DEST.exists();DEST.mkdir();shutil.copytree(SOURCE/'inputs',DEST/'inputs')
    bg=json.loads((SOURCE/'request.json').read_bytes());bg['out']='../../build/chromvar-tools-background-demo-NEW';save(DEST/'background.json',bg)
    dev=json.loads((SOURCE/'deviations-request.json').read_bytes());dev['out']='../../build/chromvar-tools-deviations-demo-NEW';dev['background']='../../build/chromvar-tools-background-demo-NEW/background.tsv';save(DEST/'deviations.json',dev)
    for name,analysis in [('verify-background','chromvar-tools-background-demo-NEW'),('verify-deviations','chromvar-tools-deviations-demo-NEW')]:save(DEST/(name+'.json'),dict(schema_version=1,mode='VERIFY',analysis='../../build/'+analysis))
    (DEST/'.gitattributes').write_text('*.json -text\ninputs/* -text\n',encoding='utf8',newline='\n');print('Prepared complete public chromvar-tools background/deviation requests')
if __name__=='__main__':main()
