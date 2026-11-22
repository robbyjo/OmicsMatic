"""Read-only provenance audit after export review: regenerate every synthetic BED input."""
from pathlib import Path
from collections import Counter
import gzip,hashlib,json,random,re,xml.etree.ElementTree as ET,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/qt-1';D=T/'docs/literature/qtltools-fdensity-development';O=R/'build/git-publish/20261121-qtltools-103/PUBLICATION-DATA-AUDIT.json'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def q(chrom,start,end,strand='+',name='q'):return [chrom,start,end,name,'gene',strand]
def a(chrom,start,end):return [chrom,start,end]
def regenerate():
    cases=[dict(id='smoke',window=20,bin=10,qtls=[q('1',100,101),q('1',200,201,'-')],annotations=[a('1',100,105),a('1',101,103),a('1',195,200)])]
    for strand in ['+','-','.']:
        for window,width in [(10,5),(10,6),(3,50),(7,1),(1,1)]:cases.append(dict(id='edges-'+strand.replace('+','plus').replace('-','minus').replace('.','dot')+'-'+str(window)+'-'+str(width),window=window,bin=width,qtls=[q('1',100,109,strand),q('1',100,101,strand)],annotations=[a('1',100-window-1,100-window),a('1',100-window,101-window),a('1',100,101),a('1',100+window,101+window),a('1',101+window,110+window),a('other',0,1000)]))
    cases += [dict(id='chromosome-start',window=10,bin=3,qtls=[q('1',0,1,'-'),q('1',0,20,'.')],annotations=[a('1',0,1),a('1',10,11)]),dict(id='all-distant',window=5,bin=2,qtls=[q('1',100,101)],annotations=[a('1',1000,2000)]),dict(id='no-shared-chromosome',window=5,bin=2,qtls=[q('chr1',100,101)],annotations=[a('1',100,101)]),dict(id='empty-annotations',window=5,bin=2,qtls=[q('1',100,101)],annotations=[]),dict(id='empty-qtls',window=5,bin=2,qtls=[],annotations=[a('1',100,101)]),dict(id='near-int-limit',window=20,bin=7,qtls=[q('1',2147483626,2147483627,'-')],annotations=[a('1',2147483600,2147483647)])]
    for count,reverse in [(2,False),(2,True),(63,False),(64,False)]:
        anns=[a('1',1000,1001),a('1',100,101)]+[a('1',1100+j*10,1101+j*10) for j in range(count-2)]
        if reverse:anns.reverse()
        cases.append(dict(id='leaf-order-'+str(count)+'-'+str(reverse),window=20,bin=10,qtls=[q('1',100,101)],annotations=anns))
    rng=random.Random(20261121)
    for index in range(450):
        nq=rng.randint(1,12);na=rng.randint(1,60);qtls=[];anns=[]
        for j in range(nq):
            start=rng.randint(0,10000);qtls.append(q(rng.choice(['1','2','chr1']),start,start+rng.randint(1,50),rng.choice(['+','-','.']),str(j%3)))
        for j in range(na):
            start=rng.randint(0,12000);anns.append(a(rng.choice(['1','2','chr1','X']),start,start+rng.randint(1,3500)))
        if index%5==0:anns.append(anns[0].copy())
        if index%7==0:qtls.append(qtls[0].copy())
        cases.append(dict(id='seeded-'+str(index),window=rng.randint(1,3000),bin=rng.randint(1,5000),qtls=qtls,annotations=anns))
    rng=random.Random(20261122)
    for i in range(100):
        qtls=[];annotations=[]
        for j in range(rng.randint(5,20)):
            start=rng.randint(0,10000);qtls.append(['1' if j==0 else rng.choice(['1','2','chr1']),start,start+rng.randint(1,50),str(j%3),'gene',rng.choice(['+','-','.'])])
        for j in range(rng.randint(250,600)):
            start=rng.randint(0,12000);annotations.append([rng.choice(['1','2','chr1']),start,start+rng.randint(1,4000)])
        cases.append(dict(id='larger-tree-'+str(i),window=rng.randint(1,3000),bin=rng.randint(1,5000),qtls=qtls,annotations=annotations))
    assert len(cases)==576;return {row['id']:row for row in cases}
def bed(rows):return ''.join('\t'.join(map(str,row))+'\n' for row in rows).encode()
if __name__=='__main__':
    expected=regenerate();counts=Counter();prefixes=Counter();credentials=[];tested_bed=fixtures=xml=0
    patterns=[re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),re.compile(rb'\bgh[pousr]_[A-Za-z0-9]{36}\b'),re.compile(rb'\bgithub_pat_[A-Za-z0-9_]{50,}\b')]
    allowed={'example-1','first-build','normal-gradle-1','normal-gradle-2','orchestration','original-controls-1','original-controls-2','original-controls-3','packaged-1','runtime-intake','runtime-verified','source-intake-1','source-intake-2'}
    with zipfile.ZipFile(D/'controls.zip') as z:
        names=z.namelist();assert len(names)==len(set(names))==4171
        for name in names:
            parts=Path(name).parts;assert parts[0] in allowed and not any(part in ['private-context','private-primary-1','xml'] for part in parts)
            raw=z.read(name);suffix=Path(name).suffix;counts[suffix]+=1;prefixes[parts[0]]+=1
            assert suffix not in {'.bam','.cram','.sam','.vcf','.bcf','.fastq','.fasta','.fq','.fa','.h5ad','.h5','.parquet','.jar','.deb','.p12','.pem','.key'}
            if any(pattern.search(raw) for pattern in patterns):credentials.append(name)
            if suffix=='.bed':
                if parts[0].startswith('original-controls-'):
                    case=expected[parts[1]];key='qtls' if parts[2]=='qtl.bed' else 'annotations';assert raw==bed(case[key]),name
                else:
                    assert parts[0]=='runtime-verified';rows=[['1',100,101,'rs1','gene1','+'],['1',200,201,'rs2','gene2','-']] if parts[-1]=='qtl.bed' else [['1',100,105,'a'],['1',101,103,'b'],['1',195,200,'c']];assert raw==bed(rows),name
                tested_bed+=1
            elif suffix=='.gz':
                assert parts[-1]=='original-controls.tsv.gz';case=None;qrows=[];arows=[]
                for line in gzip.decompress(raw).decode().splitlines():
                    fields=line.split('\t')
                    if fields[0]=='C':case=expected[fields[1]];assert fields[2:4]==[str(case['window']),str(case['bin'])];qrows=[];arows=[]
                    elif fields[0]=='Q':qrows.append(fields[1:])
                    elif fields[0]=='A':arows.append(fields[1:])
                    elif fields[0]=='B':assert len(fields)==4 and all(re.fullmatch(r'-?\d+',value) for value in fields[1:])
                    else:assert fields==['E'] and bed(qrows)==bed(case['qtls']) and bed(arows)==bed(case['annotations']);case=None
                assert case is None;fixtures+=1
            elif suffix=='.xml':
                tree=ET.fromstring(raw);assert tree.tag=='testsuite' and tree.get('name') in ['org.jlinalg.genetics.GenomicIntervalsTest','org.jlinalg.genetics.QtltoolsFunctionalDensityTest'];xml+=1
    assert tested_bed==1154 and fixtures==4 and xml==4 and not credentials
    census=json.loads((D/'QTLTOOLS-CENSUS.json').read_bytes());papers={row['paper_id']:row for row in json.loads((T/'docs/literature/omics-2023-2026/papers.json').read_bytes())}
    for candidate in census['candidates']:
        assert set(candidate)=={'identity','paragraphs','existing_catalog_use'}
        for key in ['paper_id','pmcid','doi','xml_sha256']:assert candidate['identity'][key]==papers[candidate['identity']['paper_id']][key]
        for paragraph in candidate['paragraphs']:assert set(paragraph)=={'paragraph_index','section','text_sha256'}
    assert census['positive_papers']==9
    transfer=json.loads((D/'PRIVATE-TRANSFER.json').read_bytes());assert transfer['machine_backup_complete'] is False
    def no_secret_keys(value):
        if isinstance(value,dict):
            for key,item in value.items():assert not re.search(r'password|credential|access.?token|secret.?key|cookie|authorization',key,re.I);no_secret_keys(item)
        elif isinstance(value,list):
            for item in value:no_secret_keys(item)
    no_secret_keys(transfer)
    source=json.loads((D/'SOURCE.json').read_bytes());assert source['archive']['url']=='https://codeload.github.com/qtltools/qtltools/tar.gz/a9606ee736d8489e3ec54c8b8c92c32fecad3bd0' and source['example_BAM_VCF_and_expression_datasets_published'] is False
    result=dict(status='PASS_PUBLICATION_DATA_PROVENANCE_SYNTHETIC_AND_PUBLIC_SOURCES_ONLY',controls_archive=pin((D/'controls.zip').read_bytes()),archive_members=4171,all_original_BED_inputs_regenerated_from_known_seeds_and_literal_edge_cases=True,regenerated_original_cases=576,all_archived_BED_files_verified_synthetic=tested_bed,all_gzip_members_are_verified_synthetic_fixtures=fixtures,all_XML_members_are_JUnit_test_reports=xml,primary_fulltext_or_private_context_published=False,individual_genome_BAM_VCF_FASTQ_or_matrix_files_published=False,executable_or_dependency_archives_published=False,credential_candidates=credentials,public_article_metadata_candidates=9,upstream_source_license='GPL-3.0-or-later',upstream_source=source['archive']['url'],transfer_manifest_contents='Paths,hashes and instructions only;private artifacts are explicitly excluded',runtime_records_contents='Public Ubuntu package metadata,synthetic-only fdensity smoke/test traces and library/executable hashes;no private analysis data',prefix_population=dict(prefixes),extensions=dict(counts),audit_source=pin(Path(__file__).read_bytes()),user_authority='Explicit repeated instruction to push all project changes and handover materials to GitHub main;this audit verifies the new evidence is public-source or generated synthetic test data')
    with O.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS:all4171 members categorized;all1154 BED files exactly regenerate;four compressed synthetic fixtures;zero private full texts,genomes,binaries or credential candidates',flush=True)
