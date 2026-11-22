#!/usr/bin/env python3
"""Verify the packaged native motif scanner against frozen actual FIMO outputs."""
from __future__ import annotations
import argparse,collections,csv,gzip,hashlib,json,math,subprocess,time
from pathlib import Path

REFERENCE_SHA256='ea3243dda675ce226dd1af8a2b66b2a27d0bf2d03bb9ad6ba591fb0ebb35f442'
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def rows(path):
    with Path(path).open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def key(row):return tuple(row[k] for k in ['motif_id','motif_alt_id','sequence_name','start','stop','strand','matched_sequence'])
def compare(expected,actual):
    left=collections.defaultdict(list);right=collections.defaultdict(list)
    for row in expected:left[key(row)].append(row)
    for row in actual:right[key(row)].append(row)
    assert left.keys()==right.keys(),dict(missing=list(left.keys()-right.keys())[:4],unexpected=list(right.keys()-left.keys())[:4])
    for identity,values in left.items():
        assert len(values)==len(right[identity]),identity
        for a,b in zip(values,right[identity]):
            assert b['q-value']=='',b
            for field,precision in [('score',6),('p-value',3)]:
                observed=float(b[field]);reference=float(a[field]);assert math.isfinite(observed),(identity,b)
                # Original text prints only 6/3 significant digits. Tiny arithmetic
                # differences can straddle a last-digit halfway boundary (.9125).
                # Compare the reported rounding interval, without an absolute p floor.
                tolerance=0 if reference==0 else .5*10**(math.floor(math.log10(abs(reference)))-precision+1)
                assert abs(observed-reference)<=tolerance+abs(reference)*1e-12,(identity,field,reference,observed,tolerance)
            assert 0<float(b['p-value'])<=1

def controls(a,base):
    record=[];directory=a.work/'controls';directory.mkdir();motif=directory/'input.meme';fasta=directory/'input.fa'
    motif.write_text(base['motif'],newline='\n');fasta.write_text('>id\nACGTACGT\n',newline='\n')
    choices=[('q-unsupported',{'qv-thresh':'true'}),('invalid-background',{'background':'.1,-.1,.6,.4'}),
             ('missing-strands',{'strands':None}),('hit-limit',{'maximum-hits':'1'}),('residue-limit',{'maximum-residues':'2'}),
             ('convolution-limit',{'maximum-convolution-operations':'1'})]
    bad=directory/'invalid.fa';bad.write_text('>id\nACGZ\n');choices.append(('unknown-dna',{'fasta':str(bad.resolve())}))
    duplicate=directory/'duplicate.meme';duplicate.write_text(base['motif'].replace('w= 1','w= 1 w= 2'));choices.append(('duplicate-width',{'motifs':str(duplicate.resolve())}))
    corrupt=directory/'corrupt.fa';corrupt.write_bytes(b'\x1f\x8bgarbage');choices.append(('corrupt-gzip',{'fasta':str(corrupt.resolve())}))
    underflow=directory/'underflow.meme';underflow.write_text('MEME version 4\nALPHABET= ACGT\nMOTIF rare\nletter-probability matrix: alength= 4 w= 100 nsites= 20\n'+'1 0 0 0\n'*100)
    rare=directory/'rare.fa';rare.write_text('>rare\n'+'A'*100+'\n');choices.append(('null-underflow',dict(motifs=str(underflow.resolve()),fasta=str(rare.resolve()),background='1e-100,.1,.4,.5')))
    for name,updates in choices:
        output=directory/name;options=dict(base['options'],motifs=str(motif.resolve()),fasta=str(fasta.resolve()),out=str(output.resolve()))
        options.update(updates);options={k:v for k,v in options.items() if v is not None};recipe=directory/(name+'.json');recipe.write_text(json.dumps(options))
        r=subprocess.run([a.java,'-jar',str(a.jar.resolve()),'motif-scan','--options-file',str(recipe.resolve())],capture_output=True,timeout=180)
        (directory/(name+'.stderr')).write_bytes(r.stderr);assert r.returncode==2,(name,r.returncode,r.stderr)
        assert b'FAILED' in r.stderr or b'NOT STARTED' in r.stderr,(name,r.stderr)
        if output.exists():
            assert json.loads((output/'analysis-status.json').read_text())['status']=='failed',name
            assert 'Required action' in (output/'WARNINGS.md').read_text(),name
            for product in ['results.tsv','model-metadata.tsv','sequence-metadata.tsv','scan-counts.tsv']:assert not (output/product).exists(),(name,product)
        record.append(dict(name=name,status='passed-expected-failure',exit_code=r.returncode,persisted_failure_bundle=output.exists()))
        print('control',name,'passed',flush=True)
    for index,java in enumerate([a.java]+([a.secondary_java] if a.secondary_java else [])):
        unicode=directory/('unicode-'+str(index)+'-研究');unicode.mkdir();mf=unicode/'motifs-研究.meme';fa=unicode/'seq-研究.fa';mf.write_text(base['motif']);fa.write_text(base['fasta'])
        output=unicode/'results-研究';options=dict(base['options'],motifs=str(mf.resolve()),fasta=str(fa.resolve()),out=str(output.resolve()));recipe=directory/('unicode-'+str(index)+'.json');recipe.write_text(json.dumps(options,ensure_ascii=False),encoding='utf-8')
        command=[java,'-jar',str(a.jar.resolve()),'motif-scan','--options-file',str(recipe.resolve())];r=subprocess.run(command,capture_output=True,timeout=180)
        assert r.returncode==0,(java,r.stderr.decode(errors='replace'));compare(base['rows'],rows(output/'results.tsv'))
        before={p.relative_to(output).as_posix():sha(p) for p in output.rglob('*') if p.is_file()};second=subprocess.run(command,capture_output=True,timeout=180)
        assert second.returncode==2 and b'Output already exists' in second.stderr
        assert before=={p.relative_to(output).as_posix():sha(p) for p in output.rglob('*') if p.is_file()}
        record.append(dict(name='unicode-recipe-and-existing-output-'+str(index),status='passed',java=java,first_exit_code=r.returncode,retry_exit_code=second.returncode,compared_sites=len(base['rows'])))
        print('control','unicode-'+str(index),'passed',flush=True)
    return record

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--java',default='java')
    parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--work',type=Path,required=True)
    parser.add_argument('--secondary-java',help='Optional second actual Java runtime for Unicode recipe verification')
    a=parser.parse_args();a.work.mkdir(parents=True,exist_ok=False);assert sha(a.reference)==REFERENCE_SHA256
    data=json.loads(gzip.decompress(a.reference.read_bytes()));assert len(data['cases'])==72
    start=time.monotonic();record=dict(status='running',jar_sha256=sha(a.jar),reference_sha256=sha(a.reference),verifier_sha256=sha(__file__),cases=[])
    try:
        for case in data['cases']:
            directory=a.work/case['name'];directory.mkdir();motif=directory/'input.meme';fasta=directory/'input.fa';motif.write_text(case['motif'],newline='\n');fasta.write_text(case['fasta'],newline='\n')
            recipe=directory/'options.json';options=dict(case['options'],motifs=str(motif.resolve()),fasta=str(fasta.resolve()),out=str((directory/'analysis').resolve()))
            recipe.write_text(json.dumps(options,ensure_ascii=False),encoding='utf-8');command=[a.java,'-jar',str(a.jar.resolve()),'motif-scan','--options-file',str(recipe.resolve())]
            r=subprocess.run(command,capture_output=True,timeout=180);(directory/'stdout').write_bytes(r.stdout);(directory/'stderr').write_bytes(r.stderr)
            item=dict(name=case['name'],exit_code=r.returncode,expected_hits=len(case['rows']));record['cases'].append(item)
            assert r.returncode==0,(case['name'],r.stderr.decode(errors='replace'))
            output=directory/'analysis';actual=rows(output/'results.tsv');item['observed_hits']=len(actual);compare(case['rows'],actual)
            counts=rows(output/'scan-counts.tsv');assert len(counts)==1 and int(counts[0]['hits'])==len(actual)
            assert int(counts[0]['scored_sites'])==(int(counts[0]['windows'])-int(counts[0]['ambiguous_windows']))*(2 if options['strands']=='both' else 1)
            item.update(status='passed',results_sha256=sha(output/'results.tsv'),manifest_sha256=sha(output/'manifest.yaml'),counts=counts[0]);print(case['name'],'passed',len(actual),flush=True)
        record['controls']=controls(a,data['cases'][0])
        record.update(status='passed',workflows=len(record['cases']),compared_sites=sum(c['observed_hits'] for c in record['cases']),control_checks=len(record['controls']),seconds=time.monotonic()-start)
        assert sha(a.jar)==record['jar_sha256']
    except BaseException as failure:
        record.update(status='failed',error=repr(failure),seconds=time.monotonic()-start);raise
    finally:(a.work/'verification.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps({k:record[k] for k in ['status','workflows','compared_sites','jar_sha256','seconds']}),flush=True)
if __name__=='__main__':main()
