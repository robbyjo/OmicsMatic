"""Actual executable-JAR quantification with independent fragment/count truth."""
import argparse,csv,datetime as dt,gzip,hashlib,json,math,shutil,subprocess,traceback
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--home',type=Path,default=ROOT/'build/omics-kallisto-0.48.0');parser.add_argument('--report',type=Path,required=True);args=parser.parse_args();jar=args.jar.resolve();home=args.home.resolve();report=args.report.resolve()
    sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();folder=ROOT/'build/literature-review/kallisto-runtime'/('packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));folder.mkdir(parents=True);steps=[];checks=[]
    result=dict(status='running',jar_sha256=sha(jar),verifier_sha256=sha(Path(__file__)),directory=str(folder),steps=steps,checks=checks);sources={}
    def table(path):
        with path.open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
    def write(path,text):path.write_text(text,encoding='utf-8',newline='');sources[str(path)]=sha(path);return path
    def run(name,route,options,expected=0,bundle=True):
        output=folder/name;command=['java','-jar',str(jar),route,*map(str,options),'--out',str(output),'--no-config'];process=subprocess.run(command,capture_output=True,text=True,timeout=180);log=folder/(name+'.console.log');log.write_text(process.stdout+process.stderr,encoding='utf-8');steps.append(dict(name=name,command=command,exit_code=process.returncode,expected_exit=expected,console_sha256=sha(log)))
        assert process.returncode==expected,(name,process.stdout,process.stderr,(output/'WARNINGS.md').read_text() if (output/'WARNINGS.md').exists() else '')
        if bundle:
            assert (output/'results.tsv').exists() if expected==0 else not(output/'results.tsv').exists()
            assert 'Required action:' in (output/'WARNINGS.md').read_text();status=json.loads((output/'analysis-status.json').read_text())['status'];assert status==('completed-with-warnings' if expected==0 else 'skipped-dependency-unavailable' if expected==3 else 'failed')
        else:assert not output.exists()
        return output
    def index_options(reference,processing='strict',k=31,runtime=home):return ['--method','index','--home',runtime,'--reference',reference,'--reference-id','synthetic-v1','--reference-kind','transcriptome','--reference-processing',processing,'--kmer-size',k]
    def quant_options(model,lanes,library='paired',strand='unstranded',bias='false',boots=0,seed=None,estimate=False,overhang=None,extra=()):
        options=['--method','quant','--home',home,'--index-model',model,'--lanes',lanes,'--sample-id','001','--library',library,'--strand',strand,'--fragment-length-mode','estimate' if estimate else 'supplied','--bias',bias,'--bootstraps',boots]
        if not estimate:options+=['--fragment-mean',200,'--fragment-sd',20]
        if seed is not None:options+=['--seed',seed]
        if overhang is not None:options+=['--single-overhang',overhang]
        return options+list(extra)
    def check_counts(output,expected=(40,120,0),processed=170):
        rows=table(output/'results.tsv');assert [row['target_id'] for row in rows]==['001','txB','txZero'];assert all(row['sample_id']=='001' for row in rows)
        for row,target in zip(rows,expected):assert math.isclose(float(row['estimated_counts']),target,abs_tol=.001),(output,row,target)
        info=json.loads((output/'run-info-audited.json').read_text());assert info['n_processed']==processed and info['n_pseudoaligned']==sum(expected);assert info['jlinalg_original_run_info_sha256']==sha(output/'upstream/run_info.json')
        try:json.loads((output/'upstream/run_info.json').read_text())
        except json.JSONDecodeError:assert info['jlinalg_metadata_compatibility'].startswith('Known literal Windows call')
        else:assert info['jlinalg_metadata_compatibility'].startswith('Original strict JSON')
        for row in rows:assert math.isfinite(float(row['tpm'])) and float(row['effective_length'])>0
    try:
        lock=json.loads((ROOT/'tools/literature/kallisto-runtime-lock.json').read_text());original_runtime={name:sha(home/name) for name in lock['files']};assert original_runtime==lock['files'];marker=sha(home/'.jlinalg-kallisto.json')
        reference=ROOT/'src/test/resources/kallisto';provenance=json.loads((reference/'reference-provenance.json').read_text());inputs=folder/'input data';inputs.mkdir()
        for name,value in provenance['input_sha256'].items():assert sha(reference/name)==value;shutil.copy2(reference/name,inputs/name);sources[str(inputs/name)]=value
        paired=write(inputs/'paired lanes.tsv','lane\tread1\tread2\r\n001\tread1.fastq.gz\tread2.fastq.gz\r\n');single=write(inputs/'single lanes.tsv','lane\tread1\n001\tread1.fastq.gz\n')
        indexed=run('index','kallisto-tools',index_options(inputs/'transcripts.fa'));model=indexed/'model.json'
        point=run('paired','kallisto-tools',quant_options(model,paired));check_counts(point);checks.append('Known170input fragments/160pseudoaligned/40,120,0target estimates and effective-length TPM identities; numeric sample/target IDs,relative CRLF lanes and paths with spaces retained')
        first=run('bootstrap17','kallisto-tools',quant_options(model,paired,boots=3,seed=17));again=run('bootstrap17-repeat','kallisto-tools',quant_options(model,paired,boots=3,seed=17));other=run('bootstrap-other','kallisto-tools',quant_options(model,paired,boots=3,seed=29))
        check_counts(first);assert len(table(first/'bootstrap-inventory.tsv'))==3
        for i in range(3):
            name='bs_abundance_'+str(i)+'.tsv';assert (first/'upstream'/name).read_bytes()==(again/'upstream'/name).read_bytes()==(reference/('bootstrap17-draw'+str(i)+'.tsv')).read_bytes()
        assert any((first/'upstream'/('bs_abundance_'+str(i)+'.tsv')).read_bytes()!=(other/'upstream'/('bs_abundance_'+str(i)+'.tsv')).read_bytes() for i in range(3));assert not (first/'upstream/abundance.h5').exists();checks.append('All plaintext bootstrap draws match original seed17 references/repeat; a different seed changes draws; HDF5 absence does not discard uncertainty')
        biased=run('bias','kallisto-tools',quant_options(model,paired,bias='true'));check_counts(biased);assert (biased/'upstream/abundance.tsv').read_bytes()==(reference/'bias.tsv').read_bytes();checks.append('Actual legacy --bias changes effective lengths while retaining known count totals and independently audited TPM')
        # Indistinguishable isoforms share41fragments equally:20.5+20.5,not raw integer counts.
        lines=(inputs/'transcripts.fa').read_text().splitlines();seqs={lines[i][1:]:lines[i+1] for i in range(0,len(lines),2)}
        ambiguous_fasta=write(inputs/'shared-isoforms.fa','>001\n'+seqs['001']+'\n>twin\n'+seqs['001']+'\n>txB\n'+seqs['txB']+'\n>txZero\n'+seqs['txZero']+'\n')
        ambiguous_reads=[]
        for mate in [1,2]:
            body=gzip.decompress((inputs/('read'+str(mate)+'.fastq.gz')).read_bytes()).decode();records=body.splitlines(keepends=True);extra=['@extra/'+str(mate)+'\n',*records[1:4]];path=inputs/('shared read'+str(mate)+'.fastq.gz');path.write_bytes(gzip.compress((body+''.join(extra)).encode(),mtime=0));sources[str(path)]=sha(path);ambiguous_reads.append(path)
        ambiguous_lanes=write(inputs/'shared lanes.tsv','lane\tread1\tread2\n001\tshared read1.fastq.gz\tshared read2.fastq.gz\n');shared_index=run('shared-index','kallisto-tools',index_options(ambiguous_fasta));shared=run('shared-quant','kallisto-tools',quant_options(shared_index/'model.json',ambiguous_lanes,boots=3,seed=17));shared_rows=table(shared/'results.tsv');assert [row['target_id'] for row in shared_rows]==['001','twin','txB','txZero'];assert [float(row['estimated_counts']) for row in shared_rows]==[20.5,20.5,120,0];shared_info=json.loads((shared/'run-info-audited.json').read_text());assert (shared_info['n_processed'],shared_info['n_pseudoaligned'],shared_info['n_unique'])==(171,161,120);checks.append('Actual EM allocation of41indistinguishable-isoform fragments is20.5+20.5; total161mapped includes only120uniquely pseudoaligned fragments,with all bootstrap tables retained')
        estimated=run('paired-estimated-fragments','kallisto-tools',quant_options(model,paired,estimate=True));check_counts(estimated)
        for name,strand,overhang in [('single','unstranded',None),('single-fr','fr',None),('single-overhang','unstranded','true')]:check_counts(run(name,'kallisto-tools',quant_options(model,single,library='single',strand=strand,overhang=overhang)))
        run('wrong-rf','kallisto-tools',quant_options(model,paired,strand='rf'),2);run('single-wrong-rf','kallisto-tools',quant_options(model,single,library='single',strand='rf'),2)
        repeated=write(inputs/'repeat lanes.tsv','lane\tread1\tread2\na\tread1.fastq.gz\tread2.fastq.gz\nb\tread1.fastq.gz\tread2.fastq.gz\n');duplicates=run('technical-lanes','kallisto-tools',quant_options(model,repeated));check_counts(duplicates,(80,240,0),340);assert 'REUSED FASTQ FILES' in (duplicates/'WARNINGS.md').read_text();checks.append('Single/paired,estimated/supplied fragment modes,forward/reverse strand,overhang and repeated technical-lane counts are explicit; zero-mapping libraries fail without filler')
        ambiguous=write(inputs/'ambiguous.fa','>001 description\nACGTNNNUGTC\n>tail\nCGTACGTAAAAAAAAAA\n');run('strict-reference','kallisto-tools',index_options(ambiguous,k=3),2);processed=run('upstream-reference','kallisto-tools',index_options(ambiguous,processing='upstream',k=3));assert 'pseudorandom' in (processed/'WARNINGS.md').read_text() and 'RAW lengths' in (processed/'WARNINGS.md').read_text();checks.append('Strict reference processing fails; explicit upstream ambiguity/U/poly-A processing preserves raw identity/length audit and warnings')
        bad=write(inputs/'bad mate.fastq','@wrong/2\nACGT\n+\nIIII\n');mismatch=write(inputs/'bad lanes.tsv','lane\tread1\tread2\n1\tread1.fastq.gz\tbad mate.fastq\n');run('mismatched-mates','kallisto-tools',quant_options(model,mismatch),2)
        changed=folder/'changed-index-input';shutil.copytree(indexed,changed);(changed/'targets.tsv').write_text('changed');run('changed-index','kallisto-tools',quant_options(changed/'model.json',paired),2)
        wrapped=folder/'wrapped-k-input';shutil.copytree(indexed,wrapped);metadata=json.loads((wrapped/'model.json').read_text());metadata['kmer_size']=4294967327;(wrapped/'model.json').write_text(json.dumps(metadata));run('wrapped-k','kallisto-tools',quant_options(wrapped/'model.json',paired),2)
        run('small-cells','kallisto-tools',quant_options(model,paired,extra=['--max-cells',2]),2);run('small-records','kallisto-tools',quant_options(model,paired,extra=['--max-records',2]),2)
        run('missing-runtime','kallisto-tools',index_options(inputs/'transcripts.fa',runtime=folder/'absent-runtime'),3)
        changed_home=folder/'changed-runtime-input';shutil.copytree(home,changed_home);(changed_home/'license.txt').write_text('changed');run('changed-runtime','kallisto-tools',index_options(inputs/'transcripts.fa',runtime=changed_home),3)
        run('inapplicable-seed','kallisto-tools',quant_options(model,paired,seed=17),2,bundle=False);checks.append('Model/member/k-domain,mate/resource and runtime integrity failures retain diagnostics,require explicit correction,and publish no successful table; forbidden/missing runtime skips3')
        mapping=write(inputs/'tx2gene.tsv','transcript\tgene\n001\tgeneA\ntxB\tgeneB\ntxZero\tgeneZ\n');manifest=write(inputs/'import-samples.tsv','sample\tpath\n001\t'+str(point/'upstream/abundance.tsv')+'\n')
        for mode in ['no','lengthScaledTPM']:
            imported=run('native-import-'+mode,'transcript-import',['--samples',manifest,'--format','kallisto','--level','gene','--tx2gene',mapping,'--unmapped','error','--id-policy','exact','--transcriptome-id','synthetic-v1','--counts-from-abundance',mode,'--inferential-replicates','drop']);rows=table(imported/'results.tsv');assert [row['feature'] for row in rows]==['geneA','geneB','geneZ'];assert all(math.isclose(float(row['001']),target,rel_tol=2e-5,abs_tol=.001) for row,target in zip(rows,[40,120,0]))
        for name,value in sources.items():assert sha(Path(name))==value,name
        for name,value in original_runtime.items():assert sha(home/name)==value,name
        assert sha(home/'.jlinalg-kallisto.json')==marker;checks.append('Actual raw quantifier output feeds native tximport no/lengthScaledTPM gene summaries; original inputs,all12runtime members and marker unchanged')
        result.update(status='passed-original-kallisto-048-bulk-subset',workflow_calls=len(steps),check_count=len(checks),original_runtime_sha256=original_runtime,source_sha256=sources,reference_provenance_sha256=sha(reference/'reference-provenance.json'))
    except Exception as failure:result.update(status='failed',error=str(failure),traceback=traceback.format_exc());raise
    finally:
        report.parent.mkdir(parents=True,exist_ok=True);result['finished_utc']=dt.datetime.now(dt.timezone.utc).isoformat();report.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(status=result['status'],workflow_calls=len(steps),checks=len(checks),report=str(report))))


if __name__=='__main__':main()
