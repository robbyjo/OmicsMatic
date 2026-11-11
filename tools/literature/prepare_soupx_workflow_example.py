# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create a runnable original-SoupX toy example from an existing prepared reference; installs nothing."""
import argparse,gzip,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference-home',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();reference=a.reference_home.resolve();out=a.out.resolve();assert not out.exists();setup=json.loads((reference/'COMPLETED.json').read_bytes());start=json.loads((reference/'STARTED.json').read_bytes());assert setup['status']=='installed-source-bound-original-SoupX-reference-only' and setup['version'] in ['1.6.2','1.3.0'];version=setup['version'];source=ROOT/setup['source_tree']/'source';rscript=Path(start['command'][0]);libraries=[reference/'R-lib',*map(Path,start['libraries'])];fixture=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-ambient/inputs-v1.json.gz';data=json.loads(gzip.decompress(fixture.read_bytes()));out.mkdir(parents=True);raw=data['raw'];filtered=data['filtered'];assert raw['genes']==filtered['genes'];(out/'genes.tsv').write_text('gene_id\n'+''.join(g+'\n' for g in raw['genes']),encoding='utf8',newline='\n');(out/'droplets.tsv').write_text('droplet_id\n'+''.join(c+'\n' for c in raw['cells']),encoding='utf8',newline='\n');(out/'cells.tsv').write_text('cell_id\tcluster\n'+''.join(c+'\t'+data['clusters'][c]+'\n' for c in filtered['cells']),encoding='utf8',newline='\n')
    for name,m in [('raw.mtx',raw),('filtered.mtx',filtered)]:
        (out/name).write_text('%%MatrixMarket matrix coordinate real general\n'+f"{len(m['genes'])} {len(m['cells'])} {len(m['entries'])}\n"+''.join(f"{e['gene']+1} {e['cell']+1} {e['value']}\n" for e in m['entries']),encoding='utf8',newline='\n')
    estimation=dict(kind='fixed',rho_mode='constant',rho=.1)
    if version=='1.6.2':estimation['force_accept']=False
    save(out/'request.json',dict(schema_version=1,mode='AMBIENT',version=version,count_units='raw-integer-counts',matrix_format='matrix-market',clusters='supplied',counts_relation='raw-subset' if data['raw_subset_equal'] else 'supplied-independent',raw_counts='raw.mtx',filtered_counts='filtered.mtx',gene_metadata='genes.tsv',droplet_metadata='droplets.tsv',cell_metadata='cells.tsv',soup_source=str(source),soup_range=[0,100],analyses=[dict(id='toy-fixed-rho',seed=190731,estimation=estimation,adjustment=dict(method='subtraction',clustering_scope='cell-only',round_to_integer=False,tolerance=.001,p_cut=.01))],out='result',rscript=str(rscript),r_home=str(rscript.parent.parent),r_libraries=list(map(str,libraries))))
    save(out/'EXAMPLE-PROVENANCE.json',dict(status='prepared-toy-example-not-analysis-admission',version=version,fixture=pin(fixture),reference_setup=pin(reference/'COMPLETED.json'),fixed_rho=.1,biological_contamination_estimate=False,count_relation='Original official toy matrices are preserved exactly; any independent-count declaration is explicit, not a repair.',required_action='Run the typed CLI with a current source-build JAR. Require exit0 and result_admitted=true, then read WARNINGS.md, status, provenance and the complete output matrix. This toy rho is a demonstration, not an estimate for a paper or biological sample. Preserve failures; use new paths.'))
    print('Prepared',version,'toy example:',out/'request.json','; analysis not yet executed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable prepared-reference checks')
    main()
