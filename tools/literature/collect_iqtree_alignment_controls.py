#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze retained real IQ-TREE alignment controls; launches no scientific program.

All failed controls are kept with their original expected/actual outcome records.
Independent DNA/Poisson ancestral-state enumeration checks every accepted site.
Codon controls check complete source output and the reported partial-codon rule;
they do not claim independent reconstruction of the fitted MG likelihood.
"""
from pathlib import Path
import argparse,decimal,hashlib,json,math,re,zipfile

ROOT=Path(__file__).resolve().parents[2]
VERSIONS=['1.6.12','2.1.2','2.1.4-beta','2.2.0.3','2.3.1','2.3.5','2.3.6','2.4.0']
ROUNDS=[('states-with-exclamation','alignment-original-controls-1',56,'run_alignment_original_controls_1.py'),
        ('states-common-markers','alignment-original-controls-2',24,'run_alignment_original_controls_2.py'),
        ('phylip-default-parser','alignment-format-controls-1',32,'run_alignment_format_controls_1.py'),
        ('phylip-explicit-sequential','alignment-sequential-controls-1',16,'run_alignment_sequential_controls_1.py')]
SHA=lambda raw:hashlib.sha256(raw).hexdigest()
def rounded(actual,displayed):
    quantum=float(decimal.Decimal(displayed).as_tuple().exponent)
    assert abs(actual-float(displayed))<=.5*10**quantum+2e-11,(actual,displayed)

def enumeration(sequences,kind):
    """Explicit root/internal assignments, not the Java pruning implementation."""
    alphabet='ACGT' if kind=='DNA' else 'ACDEFGHIKLMNPQRSTVWY'
    unknown=set(alphabet)
    masks={c:{c} for c in alphabet}
    masks.update({c:unknown for c in ('NX?-~.!' if kind=='DNA' else 'XUO*?-~.!')})
    if kind=='DNA':masks.update(dict(U={'T'},R=set('AG'),Y=set('CT'),S=set('CG'),W=set('AT'),K=set('GT'),M=set('AC'),B=set('CGT'),D=set('AGT'),H=set('ACT'),V=set('ACG')))
    else:masks.update(dict(B=set('ND'),Z=set('QE'),J=set('IL')))
    n=len(alphabet)
    def p(a,b,t):return 1/n+((n-1)/n if a==b else -1/n)*math.exp(-n*t/(n-1))
    transitions={t:[[p(a,b,t) for b in alphabet] for a in alphabet] for t in [.1,.2,.3,.4,.5]}
    sites=[]
    for column in range(len(sequences['A'])):
        emit={}
        for name,t in [('A',.1),('B',.2),('C',.4),('D',.5)]:
            states=masks[sequences[name][column].upper()]
            emit[name]=[math.fsum(transitions[t][a][b] for b,symbol in enumerate(alphabet) if symbol in states) for a in range(n)]
        probability=math.fsum(transitions[.3][root][internal]*emit['A'][internal]*emit['B'][internal]*emit['C'][root]*emit['D'][root]/n for root in range(n) for internal in range(n))
        sites.append(math.log(probability))
    return sites

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/iqtree-runtime')
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args();base=args.cache
    assert not args.output.exists() and not args.report.exists(),'Choose fresh artifact/report paths; prior attempts are retained'
    entries={};props={};cases=[];codon_departures=[];counts=dict(protocols=0,source_fits=0,source_refusals=0,legacy_marker_refusals=0,default_sequential_parser_refusals=0,independent_likelihood_fits=0,codon_fits=0)
    def add(name,raw):assert name not in entries;entries[name]=raw
    profiles=json.loads((base/'original-profile-inspection-1/observed.json').read_bytes());assert [p['version'] for p in profiles]==VERSIONS
    add('capture/programs.json',(base/'original-profile-inspection-1/observed.json').read_bytes())
    add('capture/official-downloads.json',(base/'upstream-downloads-1/observed.json').read_bytes())
    add('UPSTREAM-GPL-2.0.txt',(base/'upstream-downloads-1/iqtree2-GPL.txt').read_bytes())
    add('collector/collect_iqtree_alignment_controls.py',Path(__file__).read_bytes())
    source_audit={name:dict(url='https://raw.githubusercontent.com/iqtree/iqtree2/v2.3.6/'+('alignment/' if name=='alignment.cpp' else 'utils/')+name,sha256=SHA((base/'source-input-audit-1'/name).read_bytes())) for name in ['alignment.cpp','tools.cpp']}
    add('capture/source-code-observation.json',(json.dumps(source_audit,indent=2)+'\n').encode())
    for round_name,folder,number,controller in ROUNDS:
        terminal=json.loads((base/folder/'running.json').read_bytes());assert terminal['status']=='terminal' and terminal['protocols']==number
        records=json.loads((base/folder/'observed.json').read_bytes());assert len(records)==number
        assert set(r['profile'] for r in records)==set(VERSIONS)
        add('capture/'+round_name+'/observed.json',(base/folder/'observed.json').read_bytes())
        add('capture/'+round_name+'/terminal.json',(base/folder/'running.json').read_bytes())
        add('capture/'+round_name+'/controller.py',(base/controller).read_bytes())
        for record in records:
            assert record['timeout'] is None and record['owned_live_after_cleanup']==[]
            profile=next(p for p in profiles if p['version']==record['profile']);assert record['program_sha256']==profile['program_sha256']
            command=record['command'];assert command[:6]==['/usr/bin/prlimit','--as=4294967296','--cpu=20','--fsize=8388608','--core=0','--'] and command[6]==profile['program']
            for option,value in [('-st',record['case']['kind']),('-m',record['case']['model']),('-nt','1'),('-seed','1729')]:assert command[command.index(option)+1]==value
            assert '-blfix' in command and '-wsl' in command and '-te' in command and (('--sequential' in command)==(round_name=='phylip-explicit-sequential'))
            directory=base/folder/'linux'/record['name'];assert {p.name for p in directory.iterdir() if p.is_file()}==set(record['files'])
            prefix='cases/'+round_name+'/'+record['name']+'/';case_id=str(len(cases));cases.append(case_id)
            for name,expected in record['files'].items():
                raw=(directory/name).read_bytes();assert len(raw)==expected['bytes'] and SHA(raw)==expected['sha256'];add(prefix+name,raw)
            code=record['exit_code'];native='accept';reason='source-fit'
            if round_name=='states-with-exclamation' and record['case']['expected']==0 and record['profile'] in VERSIONS[:4]:
                assert code==2 and 'Unrecognized character !' in (directory/'stderr').read_text();native='reject-legacy-marker';reason='legacy-marker-refusal';counts['legacy_marker_refusals']+=1
            elif round_name=='phylip-default-parser' and record['case']['name'].endswith('sequential'):
                assert code==2 and 'wrong sequence length' in (directory/'stderr').read_text();reason='explicit-sequential-option-required';counts['default_sequential_parser_refusals']+=1
            else:
                assert code==record['expected_exit_code'],(round_name,record['name'],code)
                if code:native='reject-invalid';reason='invalid-source-state-or-stop'
            key='case.'+case_id+'.';props.update({key+'path':prefix,key+'profile':record['profile'],key+'kind':record['case']['kind'],key+'format':record['case'].get('format','FASTA'),key+'source_exit_code':str(code),key+'native':native,key+'reason':reason})
            values=record['case']['sequences'];assert list(values)==['A','B','C','D'] and len({len(s) for s in values.values()})==1
            for name,sequence in values.items():props[key+'sequence.'+name]=sequence
            counts['protocols']+=1
            if code:counts['source_refusals']+=1;continue
            counts['source_fits']+=1
            model_sites=len(values['A'])//(3 if record['case']['kind']=='CODON' else 1)
            raw=(directory/'result.sitelh').read_text().split();assert raw[:3]==['1',str(model_sites),'Site_Lh'] and len(raw)==3+model_sites
            numbers=list(map(float,raw[3:]));assert all(math.isfinite(value) for value in numbers)
            report=(directory/'result.iqtree').read_text();total=re.search(r'Log-likelihood of the tree: ([^ ]+)',report).group(1)
            assert re.search(r'Alignment has 4 sequences with '+str(model_sites)+r' columns', (directory/'stdout').read_text())
            props[key+'model_sites']=str(model_sites)
            if record['case']['kind'] in ['DNA','AA']:
                assert all(value<=0 for value in numbers)
                sites=enumeration(values,record['case']['kind']);assert len(sites)==model_sites
                for actual,displayed in zip(sites,raw[3:]):rounded(actual,displayed)
                rounded(math.fsum(sites),total);props[key+'oracle.total']=repr(math.fsum(sites))
                for i,value in enumerate(sites):props[key+'oracle.site.'+str(i)]=repr(value)
                counts['independent_likelihood_fits']+=1
            else:
                stdout=(directory/'stdout').read_text();assert 'ambiguous character AAR at site 19' in stdout and 'ambiguous character NTN at site 22' in stdout
                assert all(value<=0 for value in numbers[:-3])
                # The exact source reports tiny signed values for wholly missing
                # codon columns. Retain those values, never clamp/rewrite them.
                # This is a bounded observation, not fitted-MG parity validation.
                assert all(abs(value)<=32*math.ulp(1.0) for value in numbers[-3:])
                for index,value in enumerate(raw[-3:]):props[key+'source_missing_codon_site.'+str(index)]=value
                codon_departures.append(dict(case=case_id,profile=record['profile'],source_values=raw[-3:],theoretical_log_likelihood=0.0))
                counts['codon_fits']+=1
    assert counts==dict(protocols=128,source_fits=68,source_refusals=60,legacy_marker_refusals=12,default_sequential_parser_refusals=16,independent_likelihood_fits=56,codon_fits=12),counts
    props['cases']='|'.join(cases);props['scope']='Complete synthetic alignment admission and fixed-tree source controls only; no public IQ-TREE adapter, tree-search, bootstrap, dating or whole-paper claim.'
    add('source.properties',('\n'.join(key+'='+value for key,value in props.items())+'\n').encode())
    add('capture/all-missing-codon-departures.json',(json.dumps(codon_departures,indent=2)+'\n').encode())
    add('SHA256SUMS',''.join(SHA(raw)+'  '+name+'\n' for name,raw in sorted(entries.items())).encode())
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as output:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;output.writestr(info,raw)
    record=dict(status='passed-complete-original-alignment-source-controls',**counts,artifact=dict(path=str(args.output),bytes=args.output.stat().st_size,sha256=SHA(args.output.read_bytes()),members=len(entries),decoded_bytes=sum(map(len,entries.values()))),independent_oracle='Explicit root/internal assignments for JC69 DNA and twenty-state Poisson protein; every printed site and total must fit its own decimal quantization.',legacy_expectation_failures_retained=True,default_sequential_parser_failures_retained=True,all_missing_codon_source_departures=codon_departures,codon_MG_likelihood_independently_validated=False,IQ_TREE_adapter_available=False,whole_paper_reproduced=False)
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))

if __name__=='__main__':main()
