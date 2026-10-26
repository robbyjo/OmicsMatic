"""Additional independent geometry/filter/resource checks on the actual JAR."""
import argparse
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import subprocess
import traceback

ROOT=Path(__file__).resolve().parents[2]
sha=lambda path:hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--fixtures',type=Path,required=True)
    parser.add_argument('--runtime-home',type=Path,default=ROOT/'build/omics-deeptools-runtime-v2')
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    folder=ROOT/'build/literature-review'/('deeptools-options-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'))
    folder.mkdir()
    result=dict(status='running',jar_sha256=sha(args.jar),verifier_sha256=sha(__file__),directory=str(folder),steps=[],checks=[])
    sources={}
    def save():
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(json.dumps(result,indent=2)+'\n')
    def call(stage,command,expected=0):
        child=subprocess.run(list(map(str,command)),capture_output=True,text=True,cwd=ROOT,timeout=480)
        log=folder/(stage+'.console.log');log.write_text(child.stdout+child.stderr,encoding='utf-8')
        result['steps'].append(dict(stage=stage,command=list(map(str,command)),exit_code=child.returncode,expected_exit=expected,console_sha256=sha(log)))
        save();assert child.returncode==expected,(stage,child.stdout,child.stderr)
        print('PASSED',stage,flush=True)
    def run(stage,source,options=(),method='bam-coverage',expected=0,normalization='NONE'):
        output=folder/stage
        common=['--bin-size','10','--normalization',normalization,'--exact-scaling','true','--uncovered','zero'] if method=='bam-coverage' else ['--accept-sequence-tag-loss','true','--cigar-policy','match-only']
        call(stage,['java','-jar',args.jar.resolve(),'deep-tools','--method',method,'--input',source,'--sizes',sizes,'--genome-build','SYNTHETIC','--runtime-home',args.runtime_home.resolve(),'--out',output,'--no-config',*common,*options],expected)
        if expected:
            assert not(output/'shifted.bam').exists() and not(output/'results.tsv').exists()
            assert json.loads((output/'analysis-status.json').read_text())['status']=='failed'
        return output
    def dense(output):
        values=[math.nan]*1000
        for line in (output/'signal.bedgraph').read_text().splitlines():
            chrom,start,stop,value=line.split('\t');assert chrom=='chr1'
            values[int(start):int(stop)]=[float(value)]*(int(stop)-int(start))
        return values
    def compare(actual,expected):
        for i,(a,e) in enumerate(zip(actual,expected)):
            assert math.isclose(a,e,rel_tol=6e-6,abs_tol=1e-6),(i,a,e)
    try:
        sizes=folder/'sizes';sizes.write_text('chr1\t1000\n')
        sam=folder/'filters.sam';header='@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n'
        sam.write_text(header+''.join(f'{name}\t{flag}\tchr1\t{pos}\t{mapq}\t{cigar}\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\n' for name,flag,pos,mapq,cigar in [('A',0,1,40,'10M'),('B',0,6,10,'10M'),('C',16,21,40,'10M'),('D',1024,51,40,'10M'),('E',0,51,40,'10M'),('N',0,101,40,'5M15N5M')]))
        bam=folder/'filters.bam';samtools=ROOT/'build/omics-hts/ucrt64/bin/samtools.exe'
        call('prepare',[samtools,'sort','-o',bam,sam]);call('index',[samtools,'index',bam])
        for path in [sizes,sam,bam,Path(str(bam)+'.bai'),args.fixtures/'unpaired.bam',args.fixtures/'paired.bam']:
            sources[path]=sha(path)
        output=run('filtered-records',bam,['--min-mapq','20','--sam-exclude','1040'])
        # NONE mode isolates geometry and filtering without a guessed denominator.
        expected=[0.]*1000
        for start in [0,50,100,120]:expected[start:start+10]=[1.]*10
        compare(dense(output),expected)
        result['checks'].append('MAPQ and SAM exclusion select exact independent records; a spliced read contributes only aligned blocks,not its intron')
        output=run('filtered-cpm',bam,['--min-mapq','20','--sam-exclude','1040'],normalization='CPM')
        compare(dense(output),[value*1e6/3 for value in expected])
        result['checks'].append('Exact CPM uses three retained alignments,not the four summed spliced-bin counts or six unfiltered records')
        # The production CLI rejects duplicate options. Supply the complete override
        # explicitly below instead of permitting last-value-wins semantics.
        output=folder/'reverse-only'
        call('reverse-only',['java','-jar',args.jar.resolve(),'deep-tools','--method','bam-coverage','--input',bam,'--sizes',sizes,'--genome-build','SYNTHETIC','--runtime-home',args.runtime_home.resolve(),'--out',output,'--no-config','--bin-size','10','--normalization','NONE','--exact-scaling','true','--uncovered','omit','--sam-include','16'])
        rows=(output/'signal.bedgraph').read_text().splitlines()
        assert rows==['chr1\t20\t30\t1'],rows
        result['checks'].append('SAM inclusion and uncovered omission preserve the one selected reverse read without inventing zero rows')
        output=run('coordinate-duplicates',bam,['--ignore-duplicates','true'])
        expected=[2. if i<10 else 1. if 10<=i<30 or 50<=i<60 or 100<=i<110 or 120<=i<130 else 0. for i in range(1000)]
        compare(dense(output),expected)
        result['checks'].append('Original coordinate duplicate handling counts the repeated position once while preserving distinct starts and spliced blocks')
        output=run('explicit-extension',args.fixtures/'unpaired.bam',['--extend-reads','20'])
        compare(dense(output),[2. if i<30 else 1. if i<40 else 0. for i in range(1000)])
        result['checks'].append('Declared single-end20bp extension changes overlap counts to2,2,2,1 on the four10bp bins')
        output=run('no-atac-survivors',args.fixtures/'paired.bam',['--min-fragment-length','50'],method='atac-shift',expected=2)
        assert 'No ATAC alignments survived' in (output/'WARNINGS.md').read_text()
        result['checks'].append('A filter removing every40bp pair fails prominently instead of publishing an apparently usable shifted BAM')
        for path,digest in sources.items():assert sha(path)==digest,path
        assert sha(args.jar)==result['jar_sha256']
        result.update(status='passed-original-option-subset',source_sha256={str(path):digest for path,digest in sources.items()})
    except BaseException:
        result.update(status='failed',error=traceback.format_exc());print(result['error'],flush=True)
    save();print(result['status'],len(result['steps']),'steps',len(result['checks']),'checks',flush=True)
    return 0 if result['status']=='passed-original-option-subset' else 1


if __name__=='__main__':
    raise SystemExit(main())
