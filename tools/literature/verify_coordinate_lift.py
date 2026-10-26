"""Executable-JAR coordinate checks with independent geometry and primary dbSNP placements."""
import argparse
import csv
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'


def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--reference',type=Path,default=ROOT/'build/literature-review/liftover-runtime');args=parser.parse_args();jar=args.jar.resolve();reference=args.reference.resolve()
    out=ROOT/'build/literature-review'/('coordinate-lift-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));out.mkdir(parents=True);attempts=[]
    def write(name,text):path=out/name;path.write_text(text,encoding='utf-8',newline='\n');return path
    chain=write('forward chain with spaces.chain','chain 1000 chrS 1000 + 100 350 chrT 2000 + 500 740 1\n100 50 40\n100\n\nchain 1 chrS 1000 + 500 600 chrT 2000 - 900 1000 2\n100\n')
    source=write('source.sizes','chrS\t1000\n');target=write('target.sizes','chrT\t2000\n');queries=write('queries.tsv','chrom\tstart\tend\tid\tstrand\nchrS\t110\t120\tplus\t+\nchrS\t100\t350\tgap\t-\nchrS\t0\t1\tmissing\t.\nchrS\t200\t250\tallgap\t.\nchrS\t510\t520\treversed\t+\n')
    original={p:digest(p) for p in [chain,source,target,queries]}
    def run(name,input_=queries,chain_=chain,source_=source,target_=target,minimum='.8',policy='retain',extra=(),expected=0):
        dest=out/name;command=['java','-jar',str(jar),'coordinate-lift','--input',str(input_),'--chain',str(chain_),'--source-sizes',str(source_),'--target-sizes',str(target_),'--source-build','DECLARED_FROM','--target-build','DECLARED_TO','--min-match',minimum,'--unmapped',policy,'--out',str(dest),'--no-config',*map(str,extra)]
        p=subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=180);log=out/(name+'.console.log');log.write_text(p.stdout+p.stderr);attempts.append({'name':name,'command':command,'exit_code':p.returncode,'expected_exit':expected,'output':str(dest),'console_sha256':digest(log)})
        assert p.returncode==expected,(name,p.stdout,p.stderr,(dest/'WARNINGS.md').read_text() if(dest/'WARNINGS.md').exists() else '')
        status=json.loads((dest/'analysis-status.json').read_text())['status'];assert status.startswith('completed') if expected==0 else status=='failed'
        if expected==0:assert(dest/'results.tsv').is_file()
        else:assert not(dest/'results.tsv').exists() and not(dest/'mapped.bed').exists() and 'Required action:' in(dest/'WARNINGS.md').read_text()
        return dest
    status,error='failed',None;truth_checks=0
    try:
        result=run('retain');rows={r['id']:r for r in table(result/'results.tsv')};assert len(rows)==5
        assert (rows['plus']['target_chrom'],rows['plus']['target_start'],rows['plus']['target_end'],rows['plus']['target_strand'])==('chrT','510','520','+')
        assert (rows['reversed']['target_start'],rows['reversed']['target_end'],rows['reversed']['target_strand'])==('1080','1090','-')
        assert rows['missing']['status']=='NO_CHAIN' and rows['allgap']['status']=='NOT_UNIQUELY_MAPPED';assert rows['gap']['target_length']=='240';assert len((result/'mapped.bed').read_text().splitlines())==3
        assert 'UNMAPPED/REJECTED' in(result/'WARNINGS.md').read_text() and 'not sequence identity' in(result/'WARNINGS.md').read_text()
        failed=run('error-policy',policy='error',expected=2);assert len(table(failed/'partial/results.tsv'))==5
        raised=run('above-exact-threshold',minimum='0.8000000000000002');assert next(r for r in table(raised/'results.tsv') if r['id']=='gap')['status']=='BELOW_MIN_MATCH'
        capped=run('target-cap',extra=['--max-target-length','200']);assert next(r for r in table(capped/'results.tsv') if r['id']=='gap')['status']=='TARGET_SPAN_EXCEEDS_LIMIT'
        reverse=write('reverse.chain','chain 1 chrT 2000 + 500 740 chrS 1000 + 100 350 1\n100 40 50\n100\n\nchain 1 chrT 2000 + 1000 1100 chrS 1000 - 400 500 2\n100\n')
        back=run('exact-roundtrip',extra=['--reverse-chain',reverse,'--reverse-min-match','.8','--roundtrip-policy','exact']);assert all(r['roundtrip_status']=='passed' for r in table(back/'results.tsv') if r['status']=='MAPPED')
        one=write('one.tsv','chrom\tstart\tend\tid\tstrand\nchrS\t110\t120\tq\t+\n');shifted=write('shifted.chain','chain 1 chrT 2000 + 500 600 chrS 1000 + 105 205 1\n100\n')
        for name,minimum_,expected_status in [('reciprocal-half','.5','MAPPED'),('reciprocal-over-half','.5000000000000001','REJECTED_ROUNDTRIP')]:
            found=run(name,input_=one,extra=['--reverse-chain',shifted,'--reverse-min-match','1','--roundtrip-policy','overlap','--roundtrip-fraction',minimum_]);row=table(found/'results.tsv')[0];assert row['status']==expected_status and row['overlap_source']==row['overlap_returned']=='0.5'
        wrong=write('wrong-length.sizes','chrT\t1999\n');run('wrong-reference',target_=wrong,expected=2)
        run('missing-local-chain',chain_=out/'absent.chain',expected=2)
        truth=table(ROOT/'src/test/resources/liftover/dbsnp-coordinate-reference.tsv');human=write('hg19-public-points.tsv','chrom\tstart\tend\tid\tstrand\n'+''.join(r['chrom']+'\t'+r['hg19_start']+'\t'+str(int(r['hg19_start'])+1)+'\trs'+r['rsid']+'\t+\n' for r in truth))
        actual=run('independent-human-hg19-to-hg38',input_=human,chain_=reference/'hg19ToHg38.over.chain.gz',source_=reference/'hg19.chrom.sizes',target_=reference/'hg38.chrom.sizes',minimum='1',policy='error',extra=['--reverse-chain',reference/'hg38ToHg19.over.chain.gz','--reverse-min-match','1','--roundtrip-policy','exact'])
        expected={r['rsid']:r for r in truth}
        for row in table(actual/'results.tsv'):
            known=expected[row['id'][2:]];assert row['status']=='MAPPED' and row['target_chrom']==known['chrom'] and row['target_start']==known['hg38_start'] and row['target_end']==str(int(known['hg38_start'])+1) and row['roundtrip_status']=='passed';truth_checks+=2
        assert truth_checks==8 and all(digest(p)==sha for p,sha in original.items());status='passed'
    except Exception as failure:error=repr(failure)
    record={'status':status,'error':error,'checks':len(attempts),'independent_primary_coordinate_checks':truth_checks,'jar':str(jar),'jar_sha256':digest(jar),'steps':attempts,'fixture_directory':str(out),'verifier_sha256':digest(Path(__file__)),'primary_reference_fixture_sha256':digest(ROOT/'src/test/resources/liftover/dbsnp-coordinate-reference.tsv'),'primary_provenance_sha256':digest(ROOT/'src/test/resources/liftover/provenance.json'),'private_chain_download_manifest_sha256':digest(reference/'download-provenance.json'),'scope':'Real executable JAR, independent hand-calculated coordinate/strand/gap/fraction/size/round-trip/rejected-input checks and actual UCSC chains versus four independent primary dbSNP placements in both directions. No SNP allele/REF normalization, split/BED12/whole-tool/old-version/full-paper parity.'}
    report=DATA/'native-coordinate-lift-packaged-validation.json'
    if report.exists():
        old=json.loads(report.read_text());record['validation_history']=old.get('validation_history',[])+[{k:v for k,v in old.items() if k!='validation_history'}]
    (out/'validation.json').write_text(json.dumps(record,indent=2)+'\n');report.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps({'status':status,'checks':len(attempts),'independent_coordinate_checks':truth_checks,'error':error}));return 0 if status=='passed' else 1


if __name__=='__main__':raise SystemExit(main())
