# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the exact captured source reference/codon controls in a small lossless portable fixture."""
import gzip,json
from setup_deseq2_reference import ROOT,pin,save


def main():
    controls=ROOT/'build/literature-review/dndscv-runtime/original-controls-1';proof=json.loads((controls/'COMPLETED.json').read_bytes());assert proof['status']=='captured-original-dndscv-controls-not-adapter-validation' and proof['preexisting_libraries_unchanged'];source=controls/'coding-controls.json';assert pin(source)==proof['coding_controls'];raw=source.read_bytes();data=json.loads(raw);assert len(data['codons'])==64 and len(data['channels'])==192 and data['numcode']==1;target=ROOT/'src/test/resources/org/jlinalg/genetics/dndscv';assert not target.exists();target.mkdir(parents=True);(target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');path=target/'coding-controls-v1.json.gz';path.write_bytes(gzip.compress(raw,mtime=0));assert gzip.decompress(path.read_bytes())==raw;save(target/'ORIGINS.json',dict(status='exact-original-source-reference-and-seqinr-control-fixture',source=source.relative_to(ROOT).as_posix(),source_file=pin(source),fixture=pin(path),source_commit=json.loads((controls/'STARTED.json').read_bytes())['source_commit'],references=len(data['reference_genes']),genetic_code=1,complete_reference_opportunity_entries=768*len(data['reference_genes']),source_program=proof['source_pins'],scope='Unmodified packaged human reference opportunities and source seqinr translations; no coverage-adjusted references, dN/dS fitting or biological validation.',implementation_credit=False));print('Packed',len(data['reference_genes']),'complete reference genes and64codon/192channel controls;',pin(path)['bytes'],'bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/fixture checks')
    main()
