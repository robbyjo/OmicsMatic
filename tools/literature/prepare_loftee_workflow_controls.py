# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare complete synthetic compressed/genotyped/ancestral/SQLite controls without modifying original reference attempts."""
import argparse,gzip,json,shutil,sqlite3,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/loftee-runtime';assert out.is_relative_to(base) and not out.exists();shutil.copytree(base/'original-probe-4/inputs',out);source=Path(__file__).resolve();(out/'preparer-at-attempt.py').write_bytes(source.read_bytes());before={p.name:pin(p) for p in out.iterdir() if p.is_file()};reference=base/'workflow-fasta-probe-1'
    for suffix in ['.fa.gz','.fa.gz.fai','.fa.gz.gzi']:shutil.copyfile(reference/('reference'+suffix),out/('reference'+suffix))
    lines=(out/'input.vcf').read_text(encoding='ascii').splitlines();genotyped=[];record=0
    for line in lines:
        if line.startswith('#CHROM'):line+='\tFORMAT\tSYN_A\tSYN_B'
        elif not line.startswith('#'):
            record+=1;fields=line.split('\t');first='1/2' if ',' in fields[4] else '0/1';second='./.' if record%3==0 else '0|0';line+='\tGT:DP\t'+first+':42\t'+second+':.'
        genotyped.append(line)
    raw=('\n'.join(genotyped)+'\n').encode('ascii');(out/'genotyped.vcf').write_bytes(raw);(out/'genotyped.vcf.gz').write_bytes(gzip.compress(raw,mtime=0));fasta=(out/'reference.fa').read_text(encoding='ascii');header,*body=fasta.splitlines();sequence=list(''.join(body));assert sequence[103]=='C';sequence[103]='T';sequence=''.join(sequence);(out/'ancestor.fa').write_text(header+'\n'+'\n'.join(sequence[i:i+80] for i in range(0,len(sequence),80))+'\n',encoding='ascii',newline='\n')
    runtime=ROOT/'build/lft-runtime-1/environment';command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-i','PATH='+linux(runtime/'bin'),'LD_LIBRARY_PATH='+linux(runtime/'lib'),'LC_ALL=C',linux(runtime/'bin/samtools'),'faidx',linux(out/'ancestor.fa')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'ancestral-faidx.stdout').write_bytes(r.stdout);(out/'ancestral-faidx.stderr').write_bytes(r.stderr);save(out/'ancestral-faidx.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0
    rows=[('SYNTXPLUS',1,-5.,10.),('SYNTXPLUS',2,-3.,-1.),('SYNTXMINUS',1,4.,8.),('SYNTXMINUS',2,4.,8.),('SYNTXSINGLE',1,-1.,-1.)]
    with sqlite3.connect(out/'conservation.sqlite') as db:db.execute('CREATE TABLE phylocsf_summary (transcript TEXT, exon INTEGER, corresponding_orf_score REAL, max_score REAL)');db.executemany('INSERT INTO phylocsf_summary VALUES (?,?,?,?)',rows)
    with sqlite3.connect(out/'missing-schema.sqlite') as db:db.execute('CREATE TABLE explicit_wrong_schema (purpose TEXT)');db.execute('INSERT INTO explicit_wrong_schema VALUES (?)',('Retained negative control; no phylocsf_summary table is supplied',))
    assert all(pin(out/name)==value for name,value in before.items());files={p.name:pin(p) for p in out.iterdir() if p.is_file()};save(out/'COMPLETED.json',dict(status='prepared-synthetic-LOFTEE-workflow-expanded-input-controls',original_input_files_preserved=before,files=files,variant_rows=15,sample_ids=['SYN_A','SYN_B'],reference_origin='synthetic-only',ancestral_changed_base=dict(contig='SYN1',position=104,original='C',ancestral='T'),synthetic_phylocsf_rows=rows,source_pins={source.relative_to(ROOT).as_posix():pin(source)},analysis_executed=False,implementation_credit=False));print('Prepared gzip/BGZF, original genotype-token, ancestral-state and local SQLite controls; original controls preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-control preservation checks')
    main()
