# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the owned file interface against complete saved original dndscv outputs, without Java admission."""
import argparse,json,os,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def base_analysis(identifier='targeted-192',model='192r_3w'):
    return dict(id=identifier,seed=190731,substitution_model=model,max_muts_per_gene_per_sample='Inf',max_coding_muts_per_sample='Inf',use_indel_sites=True,minimum_indels=5,maximum_covariates=20,constrain_truncation=True,output='global-local-cv',minimum_genes_for_covariates=500,one_sided=False,gene_set_comparison=False)


def table(path):
    manifest=json.loads(path.read_bytes());columns=None;rows=[];row_names=[]
    for part in manifest['parts']:
        value=json.loads((path.parent/part).read_bytes());names=[c['name'] for c in value['columns']]
        if columns is None:columns=names
        assert columns==names and len(value['row_names'])==value['rows'];row_names.extend(value['row_names']);rows.extend([[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])])
    assert columns==manifest['column_names'] or manifest['rows']==0;assert len(rows)==manifest['rows'];return manifest['column_names'],row_names,rows


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();stage=out/'stage';inputs=stage/'inputs';worker=stage/'worker';inputs.mkdir(parents=True);worker.mkdir();source=base/'source-intake-1';reference=base/'reference-setup-3';setup=json.loads((reference/'COMPLETED.json').read_bytes());assert setup['status']=='installed-source-bound-original-dndscv-reference-only';libs=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);resources=ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*resources.glob('*')]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    shutil.copytree(source/'source',stage/'source/original')
    for name in ['worker.R','SOURCE-PROFILE.json']:shutil.copyfile(resources/name,stage/'source'/name)
    saved=base/'original-controls-1';original=json.loads((saved/'inputs.json').read_bytes());frame=original['mutations'];rows=[[c['tokens'][i] for c in frame['columns']] for i in range(frame['rows'])];(inputs/'mutations.tsv').write_text('sample_id\tchromosome\tposition\treference\talternate\n'+''.join('\t'.join(r)+'\n' for r in rows),encoding='utf8',newline='\n');(inputs/'genes.tsv').write_text('gene_id\n'+'\n'.join(original['target_genes'])+'\n',encoding='utf8',newline='\n');cases=[base_analysis(),base_analysis('targeted-2','2r_3w'),dict(base_analysis('one-sided-selection'),one_sided=True)];save(worker/'parameters.json',dict(profile='source-43c5e2f1beaaf3ac26df9c046b281ceb0edbfff7',duplicate_policy='source-collapse-with-ledger',reference=dict(kind='builtin',assembly='hg19',opportunities='verify-unweighted-standard-code'),genes=dict(kind='supplied',cdkn2a_policy='source-expand-isoforms'),covariates=dict(kind='builtin',name='hg19'),known_cancer_genes=dict(kind='builtin',name='cgc81'),duplex=dict(kind='none'),analyses=cases,gene_sets_provided=False,maximum_genes=30000,maximum_reference_bases=150000000,maximum_matrix_entries=60000000,transport_chunk_rows=1000));(worker/'runtime-paths.txt').write_text('\n'.join(map(str,libs))+'\n',encoding='utf8',newline='\n');command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla','../source/worker.R'];save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libs)),source_pins=pins));state=dict(status='FAIL2_DNDSCV_WORKER_PROBE_INCOMPLETE',analysis_admitted=False,implementation_credit=False,source_pins=pins)
    try:
        env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,libs)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(worker),R_USER_CACHE_DIR=str(worker))
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=worker,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect retained worker-report and case errors';report=json.loads((worker/'worker-report.json').read_bytes());assert report['status']=='completed-with-warnings' and report['source_bodies_and_formals_checked_before_and_after'];comparisons=[]
        for case in cases:
            home=worker/'cases'/case['id'];old=saved/case['id'];compared=0
            for original in sorted(old.glob('*.json')):
                if original.name.endswith(('-summary.json','-coefficients.json','-rows.json')) or original.name in ['matrix-axes.json','status.json']:continue
                value=json.loads(original.read_bytes())
                if not isinstance(value,dict) or 'columns' not in value or 'row_names' not in value:continue
                observed=table(home/original.name);expected=([c['name'] for c in value['columns']],value['row_names'],[[c['tokens'][i] for c in value['columns']] for i in range(value['rows'])]);assert observed==expected,(case['id'],original.name);compared+=1
            for name in ['N.f64be.gz','L.f64be.gz']:
                import gzip
                assert gzip.decompress((home/name).read_bytes())==gzip.decompress((old/name).read_bytes()),(case['id'],name)
            comparisons.append(dict(id=case['id'],complete_original_tables_compared=compared,complete_N_and_L_arrays_exact=True))
        state.update(status='passed-unchanged-original-dndscv-worker-file-interface',cases=len(cases),reference_comparisons=comparisons)
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert before==after and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',state)
    print(json.dumps(state));return 0 if state['status']=='passed-unchanged-original-dndscv-worker-file-interface' else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    raise SystemExit(main())
