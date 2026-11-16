#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create the deterministic synthetic prepared-input example without NumPy."""
from pathlib import Path
import hashlib,io,json,zipfile

ROOT=Path(__file__).resolve().parents[3]
def main():
    source=ROOT/'src/test/resources/org/jlinalg/cli/pops-workflow-inputs.zip';raw=source.read_bytes()
    assert hashlib.sha256(raw).hexdigest()=='6184068323d4ca6ea6f889fe0a7119cdf1f0084a26c0232ea625d27aa901260e'
    files={}
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for name in ['annotation.tsv','features.rows.txt','features.cols.0.txt','features.mat.0.npy','scores.tsv']:files[name]=z.read('ridge-plain/'+name)
    config=dict(profile='pops-native-prepared-v1',edition='0.2',method='ridge',target_type='custom',gene_annotation='annotation.tsv',feature_prefix='features',feature_chunks='1',scores='scores.tsv',genome_build='synthetic-no-biological-build',feature_set_id='synthetic-original-pops-control',target_source_id='synthetic-ridge-plain',java_heap_mb='512',wall_ms='120000',max_input_bytes='10000000',max_work_bytes='100000000',max_files='500',batch_columns='3')
    files['request.properties']=''.join(k+'='+v+'\n' for k,v in sorted(config.items())).encode('utf-8')
    files['NOTICE.txt']=b'Synthetic software control, not patient data or a biological result.\nInputs originate from the source-pinned original PoPS 0.2 reference controls.\nGPL-3.0-only; see docs/literature/pops-native-development/POPS-LICENSE.txt.\n'
    destination=ROOT/'examples/pops-workflow/synthetic-inputs.zip';destination.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(destination,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    print(json.dumps(dict(archive=str(destination),bytes=destination.stat().st_size,sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),files=len(files))))

if __name__=='__main__':main()
