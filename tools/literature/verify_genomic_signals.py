"""Actual executable checks of native bedGraph summaries, state weighting and diagnostics."""
import argparse
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args()
    out=ROOT/'build/literature-review'/('genomic-signal-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True)
    sizes=out/'reference.sizes';sizes.write_text('chrA\t60\nchrB\t40\n',newline='\n')
    signal=out/'signal.bedGraph';signal.write_text('chrA\t0\t5\t2\nchrA\t5\t15\t6\nchrA\t35\t50\t4\nchrB\t0\t40\t1\n',newline='\n')
    regions=out/'regions.bed';regions.write_text('chrA\t3\t10\tmixed\nchrA\t15\t35\tgap\nchrB\t5\t15\tother\n',newline='\n')
    states=out/'states.bed';states.write_text('chrA\t5\t15\tHOT\nchrA\t35\t45\tHOT\nchrA\t15\t35\tCOLD\n',newline='\n')
    attempts=[]
    def run(name,method,bed=regions,track=signal,reference=sizes,missing='zero',expected=0,extra=()):
        destination=out/name;command=['java','-jar',str(args.jar.resolve()),'genomic-signal','--method',method,'--input',str(track),'--regions',str(bed),'--sizes',str(reference),'--genome-build','independent-fixture','--missing',missing,'--out',str(destination),'--no-config',*extra]
        result=subprocess.run(command,capture_output=True,text=True,timeout=90);(out/(name+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8');attempts.append({'name':name,'command':command,'exit_code':result.returncode,'expected_exit':expected,'output':str(destination)})
        assert result.returncode==expected,(name,result.stdout,result.stderr)
        if expected==0:assert(destination/'results.tsv').is_file()
        else:assert not(destination/'results.tsv').exists() and 'Required action' in(destination/'WARNINGS.md').read_text()
        return destination
    def table(directory):
        rows=(directory/'results.tsv').read_text().splitlines();keys=rows[0].split('\t');return [dict(zip(keys,row.split('\t'))) for row in rows[1:]]
    status,error='failed',None
    try:
        summary=run('summary','summarize');rows=table(summary)
        assert [row['region_id'] for row in rows]==['mixed','gap','other']
        assert float(rows[0]['integral'])==34 and abs(float(rows[0]['mean_zero_filled'])-34/7)<1e-14
        assert rows[1]['covered_bases']=='0' and rows[1]['mean_covered']=='NA'
        assert float(rows[2]['mean_covered'])==1 and 'UNREPRESENTED SIGNAL' in(summary/'WARNINGS.md').read_text()
        enrichment=run('states','state-enrichment',bed=states,extra=('--pseudocount','0'));rows=table(enrichment)
        assert rows[0]['regions']=='2' and rows[0]['bases']=='20'
        assert float(rows[0]['mean_signal'])==5 and float(rows[0]['genome_mean'])==1.7
        assert abs(float(rows[0]['log2_enrichment'])-math.log2(5/1.7))<1e-14 and rows[1]['log2_enrichment']=='NA'
        assert 'ZERO-SIGNAL STATES' in(enrichment/'WARNINGS.md').read_text()
        adjusted=run('pseudocount','state-enrichment',bed=states,extra=('--pseudocount','1'));assert abs(float(table(adjusted)[1]['log2_enrichment'])-math.log2(1/2.7))<1e-14
        assert 'PSEUDOCOUNT CHANGES' in(adjusted/'WARNINGS.md').read_text()
        run('reject-missing','summarize',missing='error',expected=2)
        signed=out/'signed.bedGraph';signed.write_text('chrA\t0\t60\t-1\nchrB\t0\t40\t1\n',newline='\n');run('reject-signed','state-enrichment',bed=states,track=signed,expected=2,extra=('--pseudocount','0'))
        overlap=out/'overlap.bed';overlap.write_text('chrA\t0\t10\ta\nchrA\t5\t20\tb\n',newline='\n');run('reject-overlap','state-enrichment',bed=overlap,expected=2,extra=('--pseudocount','0'))
        unknown=out/'unknown.bed';unknown.write_text('A\t0\t10\twrong\n',newline='\n');run('reject-contig','summarize',bed=unknown,expected=2)
        fai=out/'declared.fai';fai.write_text('chrA\t60\t0\t60\t61\nchrB\t40\t0\t40\t41\n',newline='\n');as_fai=run('fai-dictionary','summarize',reference=fai,extra=('--sizes-format','fai'));assert table(as_fai)==table(summary)
        status='passed'
    except Exception as failure:error=str(failure)
    record={'status':status,'error':error,'jar':str(args.jar.resolve()),'jar_sha256':hashlib.sha256(args.jar.read_bytes()).hexdigest(),'checks':len(attempts),'attempts':attempts,'fixture_directory':str(out),
        'scope':'Independent actual executable length-weighted means, declared whole-reference background, repeated state IDs, zero/positive pseudocount, explicit NA/coverage warnings, FAI dictionary and rejected missing/signed/overlap/alias inputs. No peak calling, track normalization or full paper parity claim.'}
    target=ROOT/'docs/literature/omics-2023-2026/native-genomic-signal-packaged-validation.json'
    if target.exists():
        old=json.loads(target.read_text(encoding='utf-8'));record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    target.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(out/'verification.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in record.items() if k not in ['attempts','validation_history']}));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
