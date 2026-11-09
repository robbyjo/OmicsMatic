# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create explicit synthetic read/annotation inputs and run complete original Rsubread counting controls."""
import argparse,csv,gzip,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,pin,save,environment
from setup_rsubread_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_ORIGINAL_RSUBREAD_COUNTING_CONTROLS',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='passed-private-original-Rsubread2260-reference-installed-and-source-body-checked-only';lib=ROOT/setup['private_library']['path'];assert inventory(lib)==setup['private_library']['files'];inputs=out/'inputs';inputs.mkdir();reference=out/'original';reference.mkdir();gtf=[]
        for gene,transcript,chrom,start,end,strand in [('G1','T1','chr1',101,150,'+'),('G1','T1','chr1',201,250,'+'),('G1','T2','chr1',121,150,'+'),('G1','T2','chr1',231,260,'+'),('G2','T3','chr1',301,350,'-'),('G3','T4','chr1',121,170,'+'),('NA','T5','chr1',601,650,'+'),('G4','T6','chr2',101,200,'+')]:gtf.append(f'{chrom}\tsynthetic\texon\t{start}\t{end}\t.\t{strand}\t.\tgene_id "{gene}"; transcript_id "{transcript}";\n')
        raw=''.join(gtf).encode();(inputs/'genes.gtf').write_bytes(raw);(inputs/'genes.gtf.gz').write_bytes(gzip.compress(raw,mtime=0));(inputs/'peaks.saf').write_bytes(b'GeneID\tChr\tStart\tEnd\tStrand\npeakA\tchr1\t101\t150\t.\npeakB\tchr1\t201\t250\t.\npeakC\tchr1\t301\t350\t.\npeakD\tchr2\t101\t200\t.\n')
        bases=list('A'*1000)
        for pos,base in [(151,'G'),(152,'T'),(199,'A'),(200,'G')]:bases[pos-1]=base
        (inputs/'genome.fasta').write_bytes(('>chr1\n'+''.join(bases)+'\n>chr2\n'+'C'*1000+'\n').encode());header='@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n@SQ\tSN:chr2\tLN:1000\n@RG\tID:rg1\tSM:synthetic\n'
        def sam(name,flag,chrom,pos,cigar,length=10,mapq=60,mate='*',mate_pos=0,tlen=0,nh=1):return f'{name}\t{flag}\t{chrom}\t{pos}\t{mapq}\t{cigar}\t{mate}\t{mate_pos}\t{tlen}\t'+('A'*length)+'\t'+('I'*length)+f'\tNH:i:{nh}\tRG:Z:rg1\n'
        single=[sam('g1',0,'chr1',101,'10M'),sam('overlap',0,'chr1',125,'20M',20),sam('g2',16,'chr1',305,'20M',20),sam('junction',0,'chr1',141,'10M50N10M',20),sam('duplicate',1024,'chr1',105,'10M'),sam('low-mapq',0,'chr1',105,'10M',mapq=5),sam('multi',0,'chr1',105,'10M',nh=2),sam('multi',256,'chr1',305,'10M',nh=2),sam('unmapped',4,'*',0,'*',mapq=0),sam('outside',0,'chr1',800,'10M'),sam('chr2',0,'chr2',105,'10M')];(inputs/'single.sam').write_bytes((header+''.join(single)).encode());pairs=[]
        for name,left,right,first,second in [('p1',101,221,99,147),('p2',305,335,99,147),('pdup',101,221,1123,1171)]:tlen=right-left+10;pairs.extend([sam(name,first,'chr1',left,'10M',mate='=',mate_pos=right,tlen=tlen),sam(name,second,'chr1',right,'10M',mate='=',mate_pos=left,tlen=-tlen)])
        pairs.extend([sam('orphan',73,'chr1',101,'10M',mate='=',mate_pos=101),sam('orphan',133,'chr1',101,'*',mapq=0,mate='=',mate_pos=101),sam('chimera',65,'chr1',101,'10M',mate='chr2',mate_pos=105),sam('chimera',129,'chr2',105,'10M',mate='chr1',mate_pos=101)]);(inputs/'paired.sam').write_bytes((header+''.join(pairs)).encode())
        for name in ['one','two']:(inputs/name).mkdir();(inputs/name/'same.sam').write_bytes((inputs/'single.sam').read_bytes())
        pins={q.relative_to(out).as_posix():pin(q) for q in inputs.rglob('*') if q.is_file()};script=ROOT/'tools/literature/collect_rsubread_controls.R';command=[str(RSCRIPT),'--vanilla',str(script),str(lib),str(reference),str(inputs)]
        with (out/'original.stdout').open('xb') as first,(out/'original.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Retain original stderr/error and all partial original objects; no controls admitted';assert (reference/'R-status.txt').read_text().strip()=='PASS16_ORIGINAL_RSUBREAD_CONTROLS_AND10_ORIGINAL_CBIND_CASES_ONLY';assert inventory(lib)==setup['private_library']['files'] and all(pin(out/n)==value for n,value in pins.items())
        with (reference/'original-cases.tsv').open(newline='',encoding='utf8') as stream:cases=list(csv.DictReader(stream,delimiter='\t'))
        with (reference/'matrix-bind-cases.tsv').open(newline='',encoding='utf8') as stream:binds=list(csv.DictReader(stream,delimiter='\t'))
        assert len(cases)==16 and len(binds)==10 and (reference/'invalid-fragment-options/original-error.txt').read_text().startswith('EXPECTED_ORIGINAL_ERROR\n');state.update(status='passed-sixteen-complete-original-Rsubread2260-counting-and-ten-original-cbind-controls-only',original_cases=cases,bind_cases=binds,original_error_controls=1,input_pins=pins,original_package_unchanged=True,namespace_patched=False,reference_setup=dict(path=(a.setup/'COMPLETED.json').resolve().relative_to(ROOT).as_posix(),**pin(a.setup/'COMPLETED.json')),source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),script]},source_scientific_objects_complete=True,full_adapter_available=False,whole_paper_reproduced=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-object/source/input gates')
    raise SystemExit(main())
