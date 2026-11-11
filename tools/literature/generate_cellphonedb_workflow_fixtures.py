# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create an entirely synthetic schema-complete database and raw-file controls for the unchanged CellPhoneDB5.0.1 methods1/2/3; no biological database is redistributed."""
import argparse,copy,csv,io,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True)
    def table(path,header,rows):
        buffer=io.StringIO(newline='');writer=csv.writer(buffer,delimiter='\t' if path.suffix=='.tsv' else ',',lineterminator='\n');writer.writerow(header);writer.writerows(rows);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(buffer.getvalue().encode('utf8'))
    tables=out/'synthetic-database-tables';tables.mkdir();gene_header=['id_gene','ensembl','gene_name','hgnc_symbol','protein_name','protein_id'];protein_header=['id_protein','protein_name','tags','tags_reason','tags_description','protein_multidata_id'];gene_rows=[[i,'ENSG_TOY_'+str(i),'G'+str(i),'G'+str(i),'TOY_PROTEIN_'+str(i),101+i] for i in range(6)];table(tables/'gene_table.csv',gene_header,gene_rows)
    protein_rows=[[101+i,'TOY_PROTEIN_'+str(i),'','','',101+i] for i in range(6)];table(tables/'protein_table.csv',protein_header,protein_rows)
    headers=['id_multidata','name','receptor','receptor_desc','other','other_desc','secreted_highlight','secreted_desc','transmembrane','secreted','peripheral','integrin','is_complex'];rows=[]
    for i in range(6):rows.append([101+i,'TOY_ACCESSION_'+str(i),i%2==1,'synthetic',False,'',False,'',i%2==1,i%2==0,False,False,False])
    rows.extend([[900,'TOY_COMPLEX_900',True,'synthetic',False,'',False,'',True,False,False,False,True],[700,'TOY_COMPLEX_700',False,'',False,'',False,'synthetic',False,True,False,False,True]]);table(tables/'multidata_table.csv',headers,rows)
    table(tables/'complex_table.csv',['id_complex','complex_multidata_id','pdb_structure','pdb_id','stoichiometry','comments_complex','reactome_reaction','reactome_complex','complexPortal_complex','rhea_reaction'],[[0,900,False,'','','synthetic','','','',''],[1,700,False,'','','synthetic','','','','']])
    table(tables/'complex_composition_table.csv',['id_complex_composition','complex_multidata_id','protein_multidata_id','total_protein'],[[0,900,101,2],[1,900,102,2],[2,700,102,2],[3,700,103,2]])
    endpoints=[(101,102),(103,104),(900,103),(700,900),(104,105),(106,102),(101,102),(102,102)]
    table(tables/'interaction_table.csv',['id_interaction','id_cp_interaction','multidata_1_id','multidata_2_id','source','annotation_strategy','is_ppi','curator','directionality','classification'],[[i,'CPI_TOY_'+str(i),x,y,'synthetic-control','curated',True,'project-fixture','Ligand-Receptor','Synthetic'] for i,(x,y) in enumerate(endpoints)])
    table(tables/'gene_synonym_to_gene_name.csv',['Gene Synonym','Gene Name'],[['UNUSED_SYNONYM','G0']]);table(tables/'receptor_to_transcription_factor.csv',['Receptor','TF'],[['G1','TF_B'],['TOY_COMPLEX_900','TF_C']])
    def database(name,directory):
        with zipfile.ZipFile(out/name,'x') as archive:
            for path in sorted(directory.iterdir()):info=zipfile.ZipInfo(path.name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,path.read_bytes())
    database('synthetic-cellphonedb.zip',tables)
    alias_tables=out/'synthetic-alias-database-tables';alias_tables.mkdir();offset_tables=out/'synthetic-noncoincident-database-tables';offset_tables.mkdir()
    for path in tables.iterdir():(alias_tables/path.name).write_bytes(path.read_bytes());(offset_tables/path.name).write_bytes(path.read_bytes())
    table(alias_tables/'gene_table.csv',gene_header,[*gene_rows,[6,'ENSG_TOY_ALIAS','G1_alias','G1_alias','TOY_PROTEIN_1',102]]);database('synthetic-alias-cellphonedb.zip',alias_tables)
    table(offset_tables/'gene_table.csv',gene_header,[[*row[:-1],row[-1]-101] for row in gene_rows]);table(offset_tables/'protein_table.csv',protein_header,[[row[0]-101,*row[1:]] for row in protein_rows]);database('synthetic-noncoincident-id-cellphonedb.zip',offset_tables)
    cases=[];labels=['B','A','C','A','B','C','B','A','C','A','B','C'];order=[2,0,11,5,1,9,6,3,10,7,4,8,12];base_cells=['cell-'+str(i) for i in range(12)]+['extra-count-cell'];base_genes=['G3','G1','G0','G2','G4','G5','UNMAPPED','UNUSED_SYNONYM'];base_values=[[((i+2)*(j+3))%13 if (i+j)%5 else 0 for j in range(13)] for i in range(len(base_genes))]
    def case(name,method,**changes):
        c=dict(id=name,method=method,genes=list(base_genes),cells=[base_cells[i] for i in order],expression=[[row[i] for i in order] for row in base_values],meta=[['cell-'+str(i),labels[i]] for i in reversed(range(12))],counts_data='gene_name',microenvironments=None,degs=None,active_tfs=None,threshold=.1,separator='|',result_precision=3,iterations=100,threads=1,debug_seed=4711,pvalue=.05,subsampling=False,subsampling_log=False,subsampling_num_pc=2,subsampling_num_cells=8,score_interactions=False,expected_error=None);c.update(changes);cases.append(c);return c
    case('simple-raw-gene-mapping-and-duplicate-interaction-annotations','simple')
    c=case('simple-many-to-one-distinct-alias-annotation-source-error','simple',expected_error='InvalidIndexError',database='synthetic-alias-cellphonedb.zip',capture_preparation_after_expected_error=True);c['genes'].append('G1_alias');c['expression'].append([2+i%3 for i in range(len(c['cells']))])
    c=case('simple-duplicate-raw-gene-rows-compensated-mean','simple');c['expression'][c['genes'].index('G1')]=[16777216.]*len(c['cells']);c['genes']+=['G1','G1'];c['expression'] += [[1.]*len(c['cells']),[1.]*len(c['cells'])]
    c=case('simple-fractional-float32-casting-ledger','simple');c['expression']=[[v+.1 if v else 1e-50 for v in row] for row in c['expression']]
    c=case('simple-Unicode-cell-axis-order','simple');names={old:new for old,new in zip(c['cells'],['z','é','🐭','\ue000','A','α','β','cell-space','cell-quote"','Γ','x','Ω','extra-count-cell'],strict=True)};c['cells']=[names[name] for name in c['cells']];c['meta']=[[names[name],label] for name,label in c['meta']]
    c=case('simple-ensembl-many-to-one-mapping','simple',counts_data='ensembl');lookup={r[2]:r[1] for r in gene_rows};c['genes']=[lookup.get(g,g) for g in c['genes']]
    case('statistical-original100-permutations','statistical')
    environments=[['C','z'],['B','z'],['C','z'],['A','a'],['B','a']]
    case('statistical-overlapping-microenvironments','statistical',microenvironments=environments,pvalue=0.01,result_precision=6)
    case('degs-hgnc-duplicate-and-unmapped-marks','degs',counts_data='hgnc_symbol',degs=[['A','G1'],['A','G1'],['B','G2'],['C','G3'],['A','UNMAPPED']])
    case('simple-expression-specificity-scores','simple',score_interactions=True)
    case('simple-scoring-noncoincident-protein-ids-source-error','simple',score_interactions=True,database='synthetic-noncoincident-id-cellphonedb.zip',expected_error='ValueError')
    case('statistical-expression-specificity-scores','statistical',score_interactions=True)
    case('degs-CellSign-and-scoring','degs',degs=[['A','G0'],['B','G1'],['C','G2']],active_tfs=[['B','TF_B'],['C','TF_C'],['B','TF_B']],score_interactions=True,microenvironments=environments)
    case('statistical-subsampling-original-geometry','statistical',subsampling=True)
    c=case('simple-no-complete-complex-gene-name-source-error','simple',expected_error='InvalidIndexError',capture_preparation_after_expected_error=True);keep=[i for i,g in enumerate(c['genes']) if g in ['G3','G4']];c['genes']=[c['genes'][i] for i in keep];c['expression']=[c['expression'][i] for i in keep]
    d=copy.deepcopy(c);d.update(id='simple-no-complete-complex-ensembl',counts_data='ensembl',expected_error=None,capture_preparation_after_expected_error=False);d['genes']=['ENSG_TOY_'+g[1:] for g in d['genes']];cases.append(d)
    c=copy.deepcopy(c);c.update(id='degs-no-complete-complex-source-error',method='degs',degs=[['A','G3']],expected_error='TypeError',capture_preparation_after_expected_error=False);cases.append(c)
    c=case('all-unmapped-source-error','simple',expected_error='AllCountsFilteredException');c['genes']=['UNKNOWN_'+str(i) for i in range(len(c['genes']))]
    c=case('metadata-cell-absent-from-counts-source-error','simple',expected_error='ParseCountsException');c['meta'].append(['absent','A'])
    for c in cases:
        home=out/c['id'];home.mkdir();table(home/'counts.tsv',['Gene',*c['cells']],[[g,*row] for g,row in zip(c['genes'],c['expression'],strict=True)]);table(home/'meta.tsv',['cell','cell_type'],c['meta'])
        for field,header in [('microenvironments',['cell_type','microenvironment']),('degs',['cluster','gene']),('active_tfs',['cell_type','TF'])]:
            if c[field] is not None:table(home/(field+'.tsv'),header,c[field])
    assert len(cases)==19;save(out/'cases.json',dict(database='synthetic-cellphonedb.zip',cases=cases));(out/'generator-at-attempt.py').write_bytes(Path(__file__).read_bytes());pins={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()};save(out/'COMPLETED.json',dict(status='PASS_NINETEEN_SYNTHETIC_CELLPHONEDB_FULL_WORKFLOW_INPUT_CONTROLS_ONLY',cases=19,synthetic_tables_per_database=8,synthetic_database_variants=3,biological_claim=False,curated_database_included=False,files=pins,source_pin=pin(Path(__file__)),implementation_credit=False,whole_goal_complete=False));print('Prepared19 full original-workflow controls and3 entirely synthetic database variants')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete fixture checks')
    main()
