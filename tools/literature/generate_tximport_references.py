"""Freeze actual original R tximport1.40.0 point-estimate outputs, not Java outputs.

Requires an already permitted/private R installation and tximport library.
No download, installation or OS mutation occurs in this generator.
"""
import argparse
import csv
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import random
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[2]
ARCHIVE_SHA = '72841a6b2ac1f64ccb05af2d9fba4eea781596771798cefcB3a9327f9dc929f9'.lower()


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--rscript',required=True,type=Path)
    parser.add_argument('--library',required=True,type=Path)
    parser.add_argument('--archive',required=True,type=Path)
    args=parser.parse_args()
    sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
    assert sha(args.archive)==ARCHIVE_SHA,'Original package archive changed'
    output=ROOT/'src/test/resources/tximport';output.mkdir(parents=True,exist_ok=True)
    cache=ROOT/'build/literature-review/tximport-runtime';cache.mkdir(parents=True,exist_ok=True)
    folder=cache/('references-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));folder.mkdir()
    rng=random.Random(42974513);cases=[]
    def write(path,header,rows):
        with path.open('w',encoding='utf-8',newline='') as stream:
            writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(header);writer.writerows(rows)
    def add(format_,level,mode,policy,randomized=False,drop=False):
        index=len(cases);case=folder/str(index);case.mkdir()
        rows=rng.randint(6,20) if randomized else 6;columns=rng.randint(1,5) if randomized else 3
        ids=['001','txB','txC','txD','txE','txZero'] if not randomized else ['tx'+str(i) for i in range(rows)]
        if policy=='strip-version':ids=[x+'.7' for x in ids]
        if policy=='strip-bar':ids=[x+'|annotation' for x in ids]
        matching=[x.split('.')[0] if policy=='strip-version' else x.split('|')[0] if policy=='strip-bar' else x for x in ids]
        genes=['gB','gB','gA','gA','gOnly','gZero'] if not randomized else ['g'+str(i//2) for i in range(rows)]
        counts=[];abundance=[];length=[]
        for row in range(rows):
            counts.append([]);abundance.append([]);length.append([])
            for column in range(columns):
                zero=row==rows-1 or (row in (2,3) and column==0) or (randomized and rng.random()<.15)
                value=0 if zero else rng.randint(1,2000)/8
                counts[-1].append(value);abundance[-1].append(0 if zero else rng.randint(1,20000)/3)
                length[-1].append(rng.randint(100,4000)/2)
        if format_=='rsem':length[-1][0]=0.25
        mapping=[[matching[i],genes[i]] for i in range(rows) if not(drop and i==rows-2)]
        write(case/'tx2gene.tsv',['transcript','gene'],mapping)
        files=[]
        for column in range(columns):
            if format_=='salmon':header=['Name','Length','EffectiveLength','TPM','NumReads'];data=[[ids[i],5000,length[i][column],abundance[i][column],counts[i][column]] for i in range(rows)]
            elif format_=='kallisto':header=['target_id','length','eff_length','est_counts','tpm'];data=[[ids[i],5000,length[i][column],counts[i][column],abundance[i][column]] for i in range(rows)]
            elif format_=='rsem':header=['transcript_id','gene_id','length','effective_length','expected_count','TPM','FPKM','IsoPct'];data=[[ids[i],genes[i],5000,length[i][column],counts[i][column],abundance[i][column],0,0] for i in range(rows)]
            else:header=['transcript','counts','abundance','length'];data=[[ids[i],counts[i][column],abundance[i][column],length[i][column]] for i in range(rows)]
            path=case/('sample'+str(column)+'.tsv');write(path,header,data);files.append(path)
        samples=['1' if i==0 else 'sample'+str(i) for i in range(columns)]
        write(case/'samples.tsv',['sample','path'],[[samples[i],files[i].name] for i in range(columns)])
        needs=level=='gene' or mode=='dtuScaledTPM'
        cases.append(dict(case=str(index),format=format_,level=level,mode=mode,policy=policy,unmapped='drop' if drop else 'error',samples=','.join(samples),files=';'.join(str(p) for p in files),mapping=str(case/'tx2gene.tsv'),
            transcripts=','.join(ids),genes=','.join(mapping_row[1] if mapping_row[0] in dict(mapping) else '' for mapping_row in [[matching[i],genes[i]] for i in range(rows)]),
            counts=','.join(str(v) for row in counts for v in row),abundance=','.join(str(v) for row in abundance for v in row),length=','.join(str(v) for row in length for v in row),
            needs_map=str(needs).lower()))
    for format_ in ['salmon','kallisto','rsem','tsv']:
        for policy in ['exact','strip-version','strip-bar']:
            for level in ['gene','transcript']:
                for mode in ['no','scaledTPM','lengthScaledTPM']:add(format_,level,mode,policy)
            add(format_,'transcript','dtuScaledTPM',policy)
    for number in range(120):
        level='gene' if number%2 else 'transcript';mode=['no','scaledTPM','lengthScaledTPM','dtuScaledTPM'][number%4]
        if mode=='dtuScaledTPM':level='transcript'
        add(['salmon','kallisto','rsem','tsv'][number%4],level,mode,['exact','strip-version','strip-bar'][number%3],True,level=='gene' and number%5==0)
    write(folder/'cases.tsv',list(cases[0]),[[case[key] for key in cases[0]] for case in cases])
    script=Path(__file__).with_name('tximport_reference.R')
    process=subprocess.run([str(args.rscript),'--vanilla',str(script),str(args.library.resolve()),str(folder/'cases.tsv'),str(folder/'original.tsv')],capture_output=True,text=True,timeout=180)
    (folder/'original-R.log').write_text(process.stdout+process.stderr,encoding='utf-8')
    assert process.returncode==0,process.stderr
    with (folder/'original.tsv').open(encoding='utf-8',newline='') as stream:expected=list(csv.DictReader(stream,delimiter='\t'))
    assert len(expected)==len(cases)
    references=[]
    for case,result in zip(cases,expected):
        assert case['case']==result['case']
        references.append({key:value for key,value in case.items() if key not in ['files','mapping','needs_map']}|result)
    write(output/'reference.tsv',list(references[0]),[[case[key] for key in references[0]] for case in references])
    sources={}
    with tarfile.open(args.archive) as archive:
        for member in archive.getmembers():
            if member.name.startswith('tximport/R/') and member.isfile():sources[member.name]=hashlib.sha256(archive.extractfile(member).read()).hexdigest()
    provenance=dict(status='passed-original-reference-generation',package='tximport',version='1.40.0',archive_url='https://bioconductor.org/packages/3.23/bioc/src/contrib/tximport_1.40.0.tar.gz',archive_sha256=ARCHIVE_SHA,original_R_sources_sha256=sources,
        generator_sha256=sha(Path(__file__)),R_generator_sha256=sha(script),reference_sha256=sha(output/'reference.tsv'),cases=len(cases),reference_scalar_values=sum(len(row[key].split(',')) for row in references for key in ['expected_counts','expected_abundance','expected_length']),
        original_run_log=str(folder/'original-R.log'),original_run_log_sha256=sha(folder/'original-R.log'),fixture_directory=str(folder),scope='Actual unmodified original R API with character-ID importer and dropInfReps TRUE. Dense point estimates; values compared by exact identities, not host-locale gene order. RSEM lengths below1 clamped by original. No all-zero scaled denominator,uncertainty,HDF5 or downstream DE parity.')
    (output/'reference-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
    print(json.dumps({key:provenance[key] for key in ['status','cases','reference_scalar_values','reference_sha256']}))


if __name__=='__main__':main()
