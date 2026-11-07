# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create a deterministic fixture from complete captured original controls; no statistical refitting."""
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/fitdistrplus-runtime';out=base/'native-fixture-1';assert not out.exists();out.mkdir()
    original=base/'original-controls-1';proof=json.loads((original/'COMPLETED.json').read_bytes());assert proof['status']=='captured-original-normal-fit-and-tail-BH-controls' and proof['preexisting_libraries_unchanged']
    source=original/'controls.json';raw=source.read_bytes();assert pin(source)==proof['controls'];data=json.loads(raw);assert len(data['fits'])==43 and len(data['probabilities'])==36
    target=ROOT/'src/test/resources/org/jlinalg/distributional/normal-fit/original-controls-v1.json.gz';assert not target.exists();target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(gzip.compress(raw,mtime=0));assert gzip.decompress(target.read_bytes())==raw
    save(out/'COMPLETED.json',dict(status='captured-43-original-fitting-and-36-probability-families',source_pin=pin(Path(__file__)),original_control=pin(original/'COMPLETED.json'),fixture=pin(target),uncompressed=pin(source),native_analytical_domain=sum(v['native_positive_finite_variance_domain'] for v in data['fits'].values()),original_fit_calls=86,analysis_admitted=False));print('Prepared43 fitting and36 probability families')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source bindings')
    main()
