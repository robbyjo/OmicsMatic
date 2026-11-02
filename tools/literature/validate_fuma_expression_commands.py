"""Run public expression preparation, GCT workflow and an actual original MAGMA property fit."""
import argparse
import csv
import importlib.util
import json
import math
from pathlib import Path
import random
import re
import shutil
import subprocess
import sys
from validate_mashr_native import ROOT,JAVA21,pin


def write(p,j):p.write_text(json.dumps(j,indent=2)+'\n',encoding='utf-8',newline='\n')
def table(p):
    with p.open(encoding='utf-8',newline='') as f:return list(csv.DictReader(f,delimiter='\t'))


def main():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',required=True,type=Path);parser.add_argument('--classes',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();jar,classes,out=a.artifact.resolve(),a.classes.resolve(),a.out.resolve();base=(ROOT/'build/literature-review/fuma-runtime').resolve()
    assert jar.is_relative_to(base) and classes.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir()
    try:
        artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');home=ROOT/'build/omics-magma';runtime=pin(home/'magma.exe');assert runtime['sha256']=='e05e3d963215a973064852ec33640118d65745884cc800a5119ca7cffe6cb859'
        files=[Path(__file__),ROOT/'docs/literature/FUMA_EXPRESSION_PREPARATION.md',ROOT/'examples/fuma-tissue-workflow/prepare_inputs.py',ROOT/'examples/fuma-tissue-workflow/synthetic-inputs.zip',*sorted((ROOT/'examples/fuma-expression-preparation').glob('*'))];sources={p.relative_to(ROOT).as_posix():pin(p) for p in files};calls=[]
        def command(label,argv):
            with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:p=subprocess.run(list(map(str,argv)),cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=90,check=False)
            r=dict(label=label,argv=list(map(str,argv)),exit_code=p.returncode);write(out/(label+'.json'),r);calls.append(r)
            if p.returncode==3:write(out/'SKIPPED.json',dict(status='SKIP3_REQUIRED_RUNTIME',command=r,required_action='Read the retained runtime/analysis warnings. Where permitted install the pinned unmodified runtime; otherwise skip the dependent MAGMA analysis until supplied on a supported host.'));return False
            assert p.returncode==0,label+': inspect complete retained logs';print(label+': exit0',flush=True);return True
        java=[JAVA21/'java.exe','-Xmx128m','-jar',jar];work=out/'work';assert command('create-inputs',[JAVA21/'java.exe','-Xmx128m','-cp',classes,'CreateInputs',work]);shutil.copyfile(ROOT/'examples/fuma-expression-preparation/workflow.json',work/'workflow.json')
        assert command('typed-workflow',[*java,'fuma-tools','--method','workflow','--workflow-options-file',work/'workflow.json','--out',work/'workflow-1'])
        args=['--method','prepare-expression','--matrix',work/'input/raw.tsv','--format','tsv','--samples',work/'input/samples.tsv','--labels',work/'input/labels.tsv','--extra-samples','exclude','--build','synthetic-GRCh37','--annotation-id','annotation-v1','--expression-id','synthetic-v1','--gene-namespace','exact-ensembl','--units','tpm','--empty','error','--out',work/'standalone-1']
        assert command('standalone-tsv',[*java,'fuma-tools',*args]);prepared=work/'workflow-1/expression-preparation'
        for name in ['log2.tsv','norm.tsv','gene-covariates.tsv','sample-selection.tsv','label-samples.tsv']:assert (prepared/name).read_bytes()==(work/'standalone-1'/name).read_bytes(),name
        assert len(table(prepared/'gene-audit.tsv'))==4 and len(table(prepared/'log2.tsv'))==2
        assert json.loads((prepared/'session.json').read_text(encoding='utf-8'))['label_sample_counts']==[1,3]
        assert len(table(work/'workflow-1/expression/DEG.txt'))==3
        spec=importlib.util.spec_from_file_location('owned_tissue_example',ROOT/'examples/fuma-tissue-workflow/prepare_inputs.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);intake=work/'magma-intake';module.prepare(intake,home)
        genes=[r['GENE'] for r in table(intake/'gene-bundle/results.tsv')];assert len(genes)==201 and len(set(genes))==201
        raw=work/'magma-raw';raw.mkdir();rng=random.Random(271590);samples=['s'+str(i) for i in range(9)];labels=['TissueA','TissueB','TissueC'];groups=[labels[0]]*2+[labels[1]]*3+[labels[2]]*4
        with (raw/'input.tsv').open('x',encoding='utf-8',newline='\n') as f:
            f.write('gene\t'+'\t'.join(samples)+'\n')
            for gene in genes:f.write(gene+'\t'+'\t'.join(format(2+78*rng.random(),'.17g') for _ in samples)+'\n')
        (raw/'samples.tsv').write_text('sample\tlabel\n'+''.join(s+'\t'+g+'\n' for s,g in zip(samples,groups)),encoding='utf-8',newline='\n');(raw/'labels.tsv').write_text('label\n'+'\n'.join(labels)+'\n',encoding='utf-8',newline='\n')
        prep=work/'magma-prepared';assert command('prepare-magma-covariates',[*java,'fuma-tools','--method','prepare-expression','--matrix',raw/'input.tsv','--format','tsv','--samples',raw/'samples.tsv','--labels',raw/'labels.tsv','--extra-samples','error','--build','GRCh38','--annotation-id','owned-synthetic-gene-IDs','--expression-id','owned-synthetic-expression','--gene-namespace','exact-synthetic','--units','tpm','--empty','error','--out',prep])
        fit=work/'magma-fit';ok=command('actual-original-magma-fit',[*java,'magma-tools','--method','properties','--build','GRCh38','--home',home,'--gene-bundle',intake/'gene-bundle','--properties',prep/'gene-covariates.tsv','--property-direction','positive','--condition-hide','Average','--missing-values','error','--max-missing','0','--seed','42','--out',fit])
        if not ok:return 3
        result=table(fit/'results.tsv');assert len(result)==3 and {r['VARIABLE'] for r in result}==set(labels)
        for r in result:assert r['NGENES']=='201' and all(math.isfinite(float(r[k])) for k in ['BETA','BETA_STD','SE','P']) and 0<=float(r['P'])<=1
        # MAGMA's existing command publishes session.yaml. Read only its two
        # fixed, plain scalar receipts; this is not a general YAML parser.
        receipt=(fit/'session.yaml').read_text(encoding='utf-8');pairs=re.findall(r'(?m)^(runtime_version|runtime_executable_sha256): ([^\r\n]+)$',receipt);assert len(pairs)==2 and len(dict(pairs))==2;session=dict(pairs);assert session['runtime_executable_sha256']==runtime['sha256']
        assert '1.10' in session['runtime_version']
        for r in table(prep/'gene-covariates.tsv'):assert abs(float(r['Average'])-sum(float(r[g]) for g in labels)/3)<2e-15
        assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and pin(home/'magma.exe')==runtime and all(pin(ROOT/n)==v for n,v in sources.items())
        write(out/'COMPLETED.json',dict(status='passed-public-expression-preparation-workflow-and-original-MAGMA-fit',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,canonical_executable_unchanged=canonical,calls=calls,equivalent_tsv_gct_payloads=5,original_MAGMA_runtime=runtime,original_MAGMA_version=session['runtime_version'],original_MAGMA_tested_properties=3,original_MAGMA_gene_count=201,synthetic_data_only=True,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_EXPRESSION_COMMANDS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_EXPRESSION_COMMANDS: '+str(failure)+'\nRequired action: retain the attempt, inspect complete command/fit/comparison logs and rerun to new paths.',file=sys.stderr);sys.exit(2)
