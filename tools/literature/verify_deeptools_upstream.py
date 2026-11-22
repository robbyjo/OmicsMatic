"""Packaged typed original-deepTools subset against independently specified truth."""
import argparse
import csv
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import traceback
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sha = lambda path: hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--runtime-home', type=Path, default=ROOT/'build/omics-deeptools-runtime-v2')
    parser.add_argument('--samtools', type=Path, default=ROOT/'build/omics-hts/ucrt64/bin/samtools.exe')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    folder = ROOT/'build/literature-review'/('deeptools-upstream-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'))
    folder.mkdir(parents=True)
    report = dict(status='running', jar=str(args.jar.resolve()), jar_sha256=sha(args.jar),
                  verifier_sha256=sha(__file__), directory=str(folder), steps=[], checks=[])
    sources = {}

    def save():
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')

    def file(name, body):
        path = folder/name
        path.write_text(body, encoding='utf-8', newline='\n')
        sources[path] = sha(path)
        return path

    def command(stage, command, expected=0):
        result = subprocess.run(list(map(str, command)), cwd=ROOT, capture_output=True, text=True, timeout=480)
        log = folder/(stage+'.console.log')
        log.write_text(result.stdout+result.stderr, encoding='utf-8')
        report['steps'].append(dict(stage=stage, command=list(map(str, command)), exit_code=result.returncode,
                                    expected_exit=expected, console_sha256=sha(log)))
        save()
        assert result.returncode == expected, (stage, result.returncode, result.stdout, result.stderr)
        print('PASSED', stage, flush=True)
        return result.stdout

    def run(stage, method, source, extra=(), expected=0, runtime=None, reference=None):
        output = folder/stage
        options = ['java', '-jar', args.jar.resolve(), 'deep-tools', '--method', method,
                   '--input', source, '--sizes', reference or sizes, '--genome-build', 'SYNTHETIC',
                   '--runtime-home', runtime or args.runtime_home.resolve(), '--out', output, '--no-config', *extra]
        command(stage, options, expected)
        state = json.loads((output/'analysis-status.json').read_text())['status']
        assert state.startswith('completed') if expected == 0 else state == ('skipped-dependency' if expected == 3 else 'failed')
        warning = (output/'WARNINGS.md').read_text()
        assert 'Required action:' in warning
        if expected:
            assert not (output/'results.tsv').exists() and not (output/'shifted.bam').exists()
            assert (output/'partial').is_dir()
        else:
            for marker in output.glob('*-execution.json'):
                data = json.loads(marker.read_text())
                assert data['status'] in ['probe-completed', 'audit-completed', 'original-call-completed']
                assert data['versions'] == lock['critical_versions']
                assert data['source_sha256'] == lock['source_modules_sha256']
                if data.get('python_core_dll_maps'):
                    assert len(data['python_core_dll_maps'].splitlines()) == 1
        return output

    def dense(path):
        output = [math.nan]*1000
        for line in path.read_text().splitlines():
            chrom, start, stop, value = line.split('\t')
            assert chrom == 'chr1'
            output[int(start):int(stop)] = [float(value)]*(int(stop)-int(start))
        return output

    def close(actual, expected):
        assert len(actual) == len(expected)
        for index, (a, e) in enumerate(zip(actual, expected)):
            assert math.isfinite(a) and math.isclose(a, e, rel_tol=6e-6, abs_tol=1e-6), (index, a, e)

    try:
        lock = json.loads((ROOT/'tools/literature/deeptools-runtime-lock.json').read_text())
        with zipfile.ZipFile(args.jar) as jar:
            for name in ['deeptools_bridge.py', 'deeptools_inspect.py', 'deeptools-runtime-lock.json']:
                relative = 'org/jlinalg/omics/'+name
                assert jar.read(relative) == (ROOT/'src/main/resources'/relative).read_bytes()
            assert 'org/jlinalg/cli/DeepToolsUpstreamCli.class' in jar.namelist()
        report['checks'].append('Actual JAR packages the exact typed adapter, inspector, original bridge and source lock')
        sizes = file('exact.sizes', 'chr1\t1000\n')
        header = '@HD\tVN:1.6\tSO:coordinate\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg\tSM:synthetic\n'
        unpaired = file('unpaired.sam', header+''.join(f'r{i}\t0\tchr1\t{pos}\t40\t10M\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\tNM:i:0\n' for i, pos in enumerate([1, 6, 21])))
        paired = file('paired.sam', header+''.join(f'pair\t{flag}\tchr1\t{pos}\t40\t10M\t=\t{mate}\t{length}\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\tNM:i:0\tCB:Z:cell-1\tRX:Z:umi-1\n' for flag,pos,mate,length in [(99,101,131,40),(147,131,101,-40)]))
        bams = []
        report['samtools_executable_sha256'] = sha(args.samtools)
        for source in [unpaired, paired]:
            bam = folder/(source.stem+'.bam')
            command(source.stem+'-prepare', [args.samtools, 'sort', '-o', bam, source])
            command(source.stem+'-index', [args.samtools, 'index', bam])
            sources[bam] = sha(bam)
            sources[Path(str(bam)+'.bai')] = sha(str(bam)+'.bai')
            bams.append(bam)
        first = ROOT/'src/test/resources/deeptools/ramp.bw'
        second = ROOT/'src/test/resources/deeptools/two.bw'
        sources[first] = sha(first)
        sources[second] = sha(second)
        base = [2. if index < 10 else 1. if index < 30 else 0. for index in range(1000)]
        for normalization, factor in [('NONE',1.),('CPM',1e6/3),('RPKM',1e9/30),('BPM',1e6/3),('RPGC',100/30)]:
            extra = ['--bin-size','10','--normalization',normalization,'--exact-scaling','true','--uncovered','zero']
            if normalization == 'RPGC':
                extra += ['--effective-genome-size','100']
            output = run('coverage-'+normalization, 'bam-coverage', bams[0], extra)
            close(dense(output/'signal.bedgraph'), [value*factor for value in base])
        report['checks'].append('Five original BAM normalizations match hand-calculated overlap counts and depth/read-length denominators')
        for operation in ['ratio','log2','reciprocal_ratio','subtract','add','first','second','mean']:
            extra = ['--input2',second,'--bin-size','10','--operation',operation,'--scale1','1','--scale2','2','--pseudo1','1','--pseudo2','1','--missing','error','--invalid','error']
            output = run('compare-'+operation, 'bigwig-compare', first, extra)
            expected = []
            for index in range(1000):
                a, b = index//10+1., 4.
                ratio = (a+1)/(b+1)
                expected.append({'ratio':ratio,'log2':math.log2(ratio),'reciprocal_ratio':ratio if ratio>=1 else -1/ratio,
                                 'subtract':a-b,'add':a+b,'first':a,'second':b,'mean':(a+b)/2}[operation])
            close(dense(output/'signal.bedgraph'), expected)
        report['checks'].append('All eight original fixed-step BigWig operations match independent scale-then-pseudocount calculations')
        gapped = ROOT/'src/test/resources/deeptools/gapped.bw'
        negative = ROOT/'src/test/resources/deeptools/negative-two.bw'
        sources[gapped] = sha(gapped)
        sources[negative] = sha(negative)
        for missing in ['zero','retain']:
            output = run('gap-'+missing,'bigwig-compare',gapped,['--input2',second,'--bin-size','10','--operation','subtract','--missing',missing,'--invalid','retain'])
            values = dense(output/'signal.bedgraph')
            for index,value in enumerate(values):
                if 10 <= index < 20:
                    assert value == -2 if missing=='zero' else math.isnan(value)
                else:
                    assert value == 0
        run('gap-error','bigwig-compare',gapped,['--input2',second,'--bin-size','10','--operation','subtract','--missing','error','--invalid','error'],2)
        output = run('signed-cancellation-filter','bigwig-compare',second,['--input2',negative,'--bin-size','10','--operation','subtract','--missing','error','--invalid','error','--skip-zero-zero','true'])
        assert (output/'signal.bedgraph').stat().st_size == 0
        assert 'Signed nonzero values that cancel' in (output/'WARNINGS.md').read_text()
        output = run('negative-scale','bigwig-compare',second,['--input2',second,'--bin-size','10','--operation','first','--scale1','-1','--missing','error','--invalid','error'])
        close(dense(output/'signal.bedgraph'),[-2.]*1000)
        report['checks'].append('Missing zero/retain/error, signed-cancellation filtering and negative scale-factor argument routing match actual original behavior')
        options = ['--input2',second,'--bin-size','10','--operation','ratio','--scale1','0','--scale2','0','--missing','error','--invalid','retain']
        output = run('undefined-retained','bigwig-compare',first,options)
        with (output/'results.tsv').open(newline='') as stream:
            rows = list(csv.DictReader(stream, delimiter='\t'))
        assert len(rows)==100 and all(row['value']=='NA' and row['status']=='MISSING_OR_NONFINITE' for row in rows)
        assert (output/'signal.bedgraph').stat().st_size == 0
        run('undefined-error','bigwig-compare',first,[*options[:-1],'error'],2)
        report['checks'].append('Undefined original ratios remain explicit NA and cannot silently become zeros or successful error-policy results')
        output = run('atac-shift','atac-shift',bams[1],['--accept-sequence-tag-loss','true','--cigar-policy','match-only'])
        records = [line.split('\t') for line in command('inspect-shift', [args.samtools,'view',output/'shifted.bam']).splitlines()]
        assert [(int(r[3]),r[5],int(r[7]),int(r[8])) for r in records] == [(105,'6M',131,31),(131,'5M',105,-31)]
        assert all(r[9:11]==['*','*'] and len(r)==11 for r in records)
        assert 'ALL optional SAM tags' in (output/'WARNINGS.md').read_text()
        report['checks'].append('Actual tagged proper pairs have exact Tn5 geometry and original SEQ/QUAL/optional-tag loss, with a prominent recovery warning')
        wrong = file('wrong.sizes','chr1\t999\n')
        coverage = ['--bin-size','10','--normalization','NONE','--exact-scaling','true','--uncovered','zero']
        run('wrong-reference','bam-coverage',bams[0],coverage,2,reference=wrong)
        run('resource-limit','bam-coverage',bams[0],[*coverage,'--max-records','2'],2)
        run('missing-runtime','bam-coverage',bams[0],coverage,3,runtime=folder/'missing-runtime')
        report['checks'].append('Reference/resource failures quarantine partials; absent managed dependencies skip3 with exact setup and no output claim')
        for path,digest in sources.items():
            assert sha(path) == digest, path
        assert sha(args.jar) == report['jar_sha256']
        report.update(status='passed-original-upstream-subset', source_sha256={str(path):digest for path,digest in sources.items()},
                      scope='Typed BAM coverage,BigWig comparison and simple-M-CIGAR ATAC shifts only. Actual original deepTools3.5.6 through Java; no matrices,plots,correlation/full-package or full-paper parity.')
    except BaseException:
        report.update(status='failed', error=traceback.format_exc())
        print(report['error'], flush=True)
    save()
    print(report['status'],len(report['steps']),'steps',len(report['checks']),'checks',flush=True)
    return 0 if report['status']=='passed-original-upstream-subset' else 1


if __name__ == '__main__':
    raise SystemExit(main())
