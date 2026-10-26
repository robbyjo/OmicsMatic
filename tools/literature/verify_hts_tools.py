"""Retained packaged SAMtools/BCFtools checks against independently specified data."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import random
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs/literature/omics-2023-2026"


def vcf_rows(path):
    return [line.split("\t") for line in path.read_text(encoding="utf-8").splitlines() if not line.startswith("#")]


class Unavailable(Exception):
    pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--samtools", type=Path, default=ROOT / "build/omics-hts/ucrt64/bin/samtools.exe")
    parser.add_argument("--bcftools", type=Path, default=ROOT / "build/omics-hts/ucrt64/bin/bcftools.exe")
    args = parser.parse_args()
    out = ROOT / "build/literature-review" / ("hts-packaged-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    out.mkdir(parents=True, exist_ok=False)
    attempts = []

    def run(name, method, source, *extra, expected=0):
        destination = out / name
        tool = None if method == "fasta-index" else "samtools" if method.startswith("sam-") else "bcftools"
        command = ["java", "-jar", str(args.jar.resolve()), "hts-tools", "--method", method,
            "--input", str(source), "--out", str(destination), "--no-config", *extra]
        if tool: command += ["--" + tool, str(getattr(args, tool).resolve())]
        p = subprocess.run(command, capture_output=True, text=True, timeout=240, cwd=ROOT)
        (out / (name + ".log")).write_text(p.stdout + p.stderr, encoding="utf-8")
        attempts.append({"name": name, "command": command, "exit_code": p.returncode, "expected_exit": expected,
            "status": "passed" if p.returncode == expected else "skipped-dependency" if p.returncode == 3 else "failed", "output": str(destination)})
        if p.returncode == 3:
            raise Unavailable("Required runtime unavailable; inspect " + str(destination / "WARNINGS.md"))
        assert p.returncode == expected, name + ": " + p.stdout + p.stderr
        if expected == 0:
            assert (destination / "results.tsv").is_file()
        else:
            assert not (destination / "results.tsv").exists()
            assert "Required action" in (destination / "WARNINGS.md").read_text(encoding="utf-8")
        return destination

    header = "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg\tSM:S1\tPL:ILLUMINA\n"
    reference = out / "reference.fa"
    reference.write_text(">chr1\n" + "A"*1000 + "\n", encoding="utf-8", newline="\n")
    (out / "reference.fa.fai").write_text("chr1\t1000\t6\t1000\t1001\n", encoding="utf-8")
    sam = out / "input.sam"
    records = "".join(f"{name}\t{flag}\tchr1\t{pos}\t{mq}\t10M\t*\t0\t0\t{'A'*10}\t{'I'*10}\tRG:Z:rg\tNM:i:0\n"
        for name, flag, pos, mq in [("read10", 0, 200, 60), ("read2", 1024, 100, 50), ("read3", 512, 110, 10)])
    records += "unmapped\t4\t*\t0\t0\t*\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\n"
    sam.write_text(header + records, encoding="utf-8")
    status, error = "failed", None
    try:
        crlf=out/'crlf.fa';crlf.write_bytes(b'>chr1\r\nACGT\r\nAC\r\n>chr2\r\nTTT\r\n')
        indexed=run('native-fasta-index','fasta-index',crlf)
        assert (indexed/'crlf.fa.fai').read_text().splitlines()==['chr1\t6\t7\t4\t6','chr2\t3\t24\t3\t5']
        sorted_ = run("sort", "sam-sort", sam, "--create-index", "true")
        bam = sorted_ / "alignment.bam"
        run("queryname", "sam-sort", sam, "--sort-order", "queryname")
        run("index", "sam-index", bam)
        run("select", "sam-view", bam, "--min-mapq", "20", "--exclude-flags", "1024")
        run("region", "sam-view", bam, "--region", "chr1:109-110")
        flags = run("flagstat", "sam-flagstat", bam)
        counts = json.loads((flags / "flagstat.json").read_text(encoding="utf-8"))
        assert counts["QC-passed reads"]["total"] == 3 and counts["QC-failed reads"]["total"] == 1
        assert counts["QC-passed reads"]["duplicates"] == 1
        pileup = run("pileup", "sam-mpileup", bam, "--reference", str(reference), "--min-mapq", "30", "--min-baseq", "30", "--baq", "false", "--exclude-flags", "0")
        rows = [line.split("\t") for line in (pileup / "pileup.tsv").read_text().splitlines()]
        assert [int(r[1]) for r in rows] == list(range(100,110)) + list(range(200,210))
        assert all(r[2] == "A" and r[3] == "1" for r in rows)
        paired = out / "paired.sam"
        records = []
        for i in range(2):
            for flag, pos, mate, size in [(99,100,200,120),(147,200,100,-120)]:
                records.append(f"INST:1:FC:1:1101:{100+i*100}:{100+i*100}\t{flag}\tchr1\t{pos}\t60\t20M\t=\t{mate}\t{size}\t{'A'*20}\t{('I' if i==0 else 'H')*20}\tRG:Z:rg\n")
        paired.write_text(header+"".join(records), encoding="utf-8")
        for remove in (False, True):
            result = run("remove" if remove else "mark", "sam-markdup", paired, "--remove-duplicates", str(remove).lower())
            metrics = dict(line.split("\t",1) for line in (result/"results.tsv").read_text().splitlines()[1:])
            assert metrics["output_records"] == ("2" if remove else "4")
            assert metrics["output_duplicate_flags"] == ("0" if remove else "2")
        vcf = out/"input.vcf"
        vcf.write_text('##fileformat=VCFv4.2\n##contig=<ID=chr1,length=1000>\n##FILTER=<ID=Existing,Description="Old flag">\n'
            '##INFO=<ID=AC,Number=A,Type=Integer,Description="Alt count">\n##INFO=<ID=AN,Number=1,Type=Integer,Description="Allele count">\n'
            '##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">\n'
            '#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\tS2\n'
            'chr1\t100\tv1\tA\tC,G\t50\tPASS\tAC=1,2;AN=4\tGT\t0/2\t1/2\n'
            'chr1\t200\tv2\tAA\tA\t10\tExisting\tAC=1;AN=4\tGT\t0/1\t0/0\n'
            'chr1\t300\tv3\tA\tT\t.\tPASS\tAC=1;AN=2\tGT\t./.\t0/1\n', encoding='utf-8')
        normalized = run("normalize", "bcf-norm", vcf, "--reference", str(reference))
        rows = vcf_rows(normalized/"variants.vcf"); assert len(rows)==4 and rows[0][1:5]==['1','v2','AA','A']
        filtered = run("filter", "bcf-filter", vcf, "--min-qual", "30")
        rows = vcf_rows(filtered/"variants.vcf"); assert rows[0][6]=='PASS' and set(rows[1][6].split(';'))=={'Existing','JL_LOW_QUAL'} and rows[2][6]=='JL_LOW_QUAL'
        samples = out/"samples.txt"; samples.write_text("S2\n")
        selected = run("samples", "bcf-select", vcf, "--samples-list", str(samples))
        rows = vcf_rows(selected/"variants.vcf"); assert len(rows)==3 and len(rows[0])==10 and 'AC=1,1' in rows[0][7] and 'AN=2' in rows[0][7]
        run("stats", "bcf-stats", vcf)
        rng = random.Random(8731); sequence = ''.join(rng.choice('ACGT') for _ in range(1000))
        callref=out/"callref.fa";callref.write_text('>chr1\n'+sequence+'\n',newline='\n');(out/"callref.fa.fai").write_text('chr1\t1000\t6\t1000\t1001\n')
        alternate='C' if sequence[149]=='A' else 'A'
        for ploidy in (2,1):
            call_sam=out/f'call{ploidy}.sam';records=[]
            for start in range(100,121):
                for repeat in range(4):
                    bases=list(sequence[start-1:start+99])
                    if ploidy==1 or repeat>=2:bases[150-start]=alternate
                    records.append(f'R{start}_{repeat}\t0\tchr1\t{start}\t60\t100M\t*\t0\t0\t'+''.join(bases)+'\t'+'I'*100+'\tRG:Z:rg\n')
            call_sam.write_text(header+''.join(records))
            call_bam=run(f'call-sort-{ploidy}', 'sam-sort', call_sam,'--create-index','true')/'alignment.bam'
            called=run(f'call-{ploidy}','bcf-call',call_bam,'--reference',str(callref),'--region','chr1:100-220','--ploidy',str(ploidy))
            rows=vcf_rows(called/'variants.vcf');assert len(rows)==1 and rows[0][1]=='150' and rows[0][3:5]==[sequence[149],alternate]
            keys=rows[0][8].split(':');values=rows[0][9].split(':');assert values[keys.index('GT')]==('0/1' if ploidy==2 else '1')
        bad_samples=out/'unknown.txt';bad_samples.write_text('unknown\n')
        run('unknown-sample','bcf-select',vcf,'--samples-list',str(bad_samples),expected=2)
        bad_reference=out/'stale-index.fa';bad_reference.write_bytes(b'>chr1\r\n'+b'A'*1000+b'\r\n')
        (out/'stale-index.fa.fai').write_text('chr1\t1000\t6\t1000\t1001\n')
        rejected=run('stale-fasta-index','bcf-norm',vcf,'--reference',str(bad_reference),expected=2)
        assert 'LF/CRLF' in (rejected/'WARNINGS.md').read_text() and not (rejected/'external.log').exists()
        status='passed'
    except Unavailable as failure:
        status,error='skipped-dependency',str(failure)
    except Exception as failure:
        error=repr(failure)
    result={'status':status,'error':error,'jar':str(args.jar.resolve()),'jar_sha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        'attempts':attempts,'passed':sum(a['status']=='passed' for a in attempts),'source_fixture_directory':str(out),
        'scope':'Real packaged 1.24 applications with independently specified records, alleles and genotypes; not complete upstream or old paper-version parity.'}
    report=DATA/'hts-packaged-validation.json'
    if report.exists():
        previous=json.loads(report.read_text(encoding='utf-8'));result['history']=previous.get('history',[])+[{k:v for k,v in previous.items() if k!='history'}]
    report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    if status!='passed':(out/'WARNINGS.md').write_text('# HTS VERIFICATION '+status.upper()+'\n\n'+str(error)+'\n\nRequired action: inspect the named attempt and its WARNINGS.md/logs, correct the dependency/input error and rerun to a new output bundle.\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in {'attempts','history'}}))
    return 0 if status=='passed' else 3 if status.startswith('skipped') else 2


if __name__=='__main__':
    raise SystemExit(main())
