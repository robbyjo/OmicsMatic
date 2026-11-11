#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only PAL2NAL evidence: complete source/byte census and independent cell replay.

Launches no Java, upstream executable, download or installation. Source differences
and deliberately permissive synthetic controls do not certify biological accuracy.
"""
from collections import Counter
import copy,csv,hashlib,io,json,re,stat
from pathlib import Path,PurePosixPath
import xml.etree.ElementTree as ET
import zipfile
from catalog_source_integrity import verify_source

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'
GUIDE='docs/literature/PAL2NAL_WORKFLOW.md'
OLD_GUIDE=b'JAR. Older released 0.3.7 executables lack this command. Catalog registration and\npaper-use curation are still pending, so the project integration count remains 59.\n'
NEW_GUIDE=b'JAR. Older released 0.3.7 executables lack this command. The current catalog records\nthis as a validated scoped native integration; see summary.json for current totals.\n'
SOURCES={f'src/main/java/org/jlinalg/cli/Pal2Nal{name}.java' for name in ['Bundle','Inputs','Json','Recipe','ToolsCli','Workflow']}
SOURCES|={f'src/main/java/org/jlinalg/sequence/Pal2Nal{name}.java' for name in ['','Clustal','Codes']}
SOURCES|={f'src/test/java/org/jlinalg/{folder}/{name}.java' for folder,name in [('cli','Pal2NalToolsCliTest'),('sequence','Pal2NalClustalTest'),('sequence','Pal2NalTest')]}
SOURCES|={f'src/main/java/org/jlinalg/cli/{name}.java' for name in ['JLinAlgCli','SimpleJson','RegenieRuntime','IqtreeFiles']}
SOURCES|={'src/main/java/org/jlinalg/sequence/MafftSequences.java','src/main/java/org/jlinalg/phylogeny/PhylogeneticAlignment.java',GUIDE,
 'src/main/resources/org/jlinalg/omics/pal2nal-v14-codes.tsv','src/main/resources/org/jlinalg/omics/pal2nal-v14-MIT-NOTICE.txt',
 'tools/literature/collect_pal2nal_controls.py','tools/literature/pal2nal-development.init.gradle'}
SOURCES|={'src/test/resources/org/jlinalg/sequence/pal2nal-original/'+name for name in ['controls.zip','controls-141.zip','ncbi-codes.zip']}
PROFILES={'native-pal2nal-v14-matching','native-pal2nal-v14.1-matching'}
sha=lambda raw:hashlib.sha256(raw).hexdigest()


def object_(raw):
    def pairs(items):
        result={}
        for key,value in items:
            assert key not in result,'Duplicate JSON key';result[key]=value
        return result
    return json.loads(raw.decode('utf-8'),object_pairs_hook=pairs)


def safe_path(name):
    p=PurePosixPath(name)
    assert name and name!='.' and p.as_posix()==name and not p.is_absolute() and not {'..','.'}&set(p.parts) and '\\' not in name and not re.match('[A-Za-z]:',name),name


def unpack(raw,maximum_files=10000,maximum_bytes=268435456):
    result={};total=0
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries=archive.infolist();assert len(entries)<=maximum_files
        for entry in entries:
            safe_path(entry.filename);assert entry.filename not in result and not entry.is_dir()
            assert stat.S_IFMT(entry.external_attr>>16) in {0,stat.S_IFREG} and entry.file_size<=8388608
            total+=entry.file_size;assert total<=maximum_bytes
            result[entry.filename]=archive.read(entry)
    return result


def verify_guide(original,current,proof):
    assert proof['path']==GUIDE and proof['before_sha256']==sha(original) and proof['after_sha256']==sha(current)
    assert original.count(OLD_GUIDE)==current.count(NEW_GUIDE)==1
    assert current.replace(NEW_GUIDE,OLD_GUIDE)==original,'Only the complete documented catalog-status paragraph may change'


def fasta(raw,headers='WHOLE_HEADER'):
    rows=[];header=None;sequence=[]
    for line in raw.decode('utf-8').splitlines():
        if line.startswith('>'):
            if header is not None:rows.append((header,''.join(sequence)))
            header=line[1:];sequence=[];assert header and not any(ord(c)<32 and c!='\t' for c in header)
        elif line:
            assert header is not None
            sequence.append(line.replace(' ','').replace('\t',''))
    if header is not None:rows.append((header,''.join(sequence)))
    assert rows and all(sequence for _,sequence in rows)
    result=[];seen=set()
    for header,sequence in rows:
        name=re.split('[ \t]',header,1)[0] if headers=='FIRST_FIELD' else header
        assert name and name not in seen;seen.add(name);result.append((name,header,sequence))
    return result


def clustal(raw):
    rows={};block={};order=None;mask='';width=offset=None;header=False
    def finish():
        nonlocal block,order,mask
        if not block:return
        if order is None:order=list(block)
        assert list(block)==order
        mask+=' '*len(next(iter(block.values())));block={}
    for line in raw.decode('utf-8').splitlines():
        if not line.strip():finish();continue
        if not header:assert line.startswith('CLUSTAL');header=True;continue
        if line[0] in ' \t':
            assert block
            current=line[offset:offset+width].ljust(width)
            assert set(current)<={' ','#','*',':','.'}
            before=len(mask);finish();mask=mask[:before]+''.join('#' if c=='#' else ' ' for c in current);continue
        match=re.fullmatch(r'([^\s]+)([ \t]+)([A-Za-z0-9*_.\\/\-]+)(?:[ \t]+[0-9]+)?[ \t]*',line);assert match
        name,segment=match[1],match[3];assert name not in block
        if not block:width=len(segment);offset=match.start(3)
        assert len(segment)==width and match.start(3)==offset
        block[name]=segment;rows[name]=rows.get(name,'')+segment
    finish();assert rows and len({len(value) for value in rows.values()})==1
    return [(name,name,value) for name,value in rows.items()],mask


def normalized(sequence,options):
    assert sequence.isascii();value=sequence.upper()
    if options['normalize_documented_symbols']:value=value.replace('.','-').replace('_','*')
    if options['normalize_tfastx']:value=re.sub(r'/(-*)[A-Z*]',r'-\g<1>2',value.replace('\\','1'))
    assert len(value)==len(sequence) and set(value)<=set('ACDEFGHIKLMNPQRSTVWYX*-123456789')
    return value


def steps(peptide,start_allowed=True):
    result=[];word=False
    for aa in peptide:
        if aa=='-':continue
        if aa.isdigit():result.append((int(aa),None));continue
        result.append((3,'B' if start_allowed and not word and aa=='M' else aa))
        if aa!='X':word=True
    return result


def search(pattern,dna,table,fallback=False):
    width=sum(size for size,_ in pattern);found=[]
    for offset in range(max(0,len(dna)-width+1)):
        position=offset
        for size,aa in pattern:
            codon=dna[position:position+size]
            regex='[ACGR]TG' if fallback and aa=='B' else table[aa] if aa is not None else None
            if regex is not None and re.fullmatch(regex,codon,re.I) is None:break
            position+=size
        else:
            found.append(offset)
            if len(found)==2:break
    return found


def placement(peptide,dna,table,options):
    exact=search(steps(peptide),dna,table)
    if exact:return exact,False
    assert options['matching']=='ALLOW_V14_RELAXED'
    anchors=[];start=count=0
    for index,aa in enumerate(peptide):
        if aa!='-':count+=1
        if count==10 or index==len(peptide)-1:anchors.append(peptide[start:index+1]);start=index+1;count=0
    if len(anchors[-1])<10:
        assert len(anchors)>1,'Undefined native-refused short fallback'
        tail=anchors.pop();anchors[-1]+=tail
    relaxed=[]
    for index,anchor in enumerate(anchors):
        part=steps(anchor,index==0)
        if not search(part,dna,table,True):part=[(width,None) for width,_ in part]
        relaxed.extend(part)
    found=search(relaxed,dna,table,True);assert found
    return found,True


def tsv(raw):return list(csv.DictReader(io.StringIO(raw.decode('utf-8')),delimiter='\t'))


def verify_case(files,tables):
    assert {'manifest.json','COMPLETED.json','session.json'}<=files.keys()
    manifest=object_(files['manifest.json']);marker=object_(files['COMPLETED.json']);session=object_(files['session.json'])
    assert set(files)==set(manifest['files'])|{'manifest.json','COMPLETED.json'}
    assert set(manifest['directories'])=={'','inputs'} and len(manifest['directories'])==2
    for name,pin in manifest['files'].items():assert len(files[name])==pin['bytes'] and sha(files[name])==pin['sha256'],name
    assert marker['manifest_sha256']==sha(files['manifest.json']) and marker['scientific_result_admitted'] is True
    for obj in [marker,manifest,session]:assert obj['tool']=='PAL2NAL-native' and obj['scope']=='complete-codon-backtranslation' and obj['schema_version']==1
    assert marker['original_tool_executed'] is marker['biological_accuracy_certified'] is marker['whole_paper_reproduced'] is False
    assert manifest['status']==session['status']=='audited-provisional' and session['exit_code']==0 and session['scientific_result_admitted'] is session['original_tool_executed'] is False
    request=object_(files['recipe-request.json']);recipe=object_(files['recipe-replay.json']);expected=copy.deepcopy(request)
    expected['protein']['path']='inputs/protein.original';expected['dna']['paths']=[f'inputs/dna-{i+1:04d}.fasta' for i in range(len(request['dna']['paths']))]
    if 'path' in expected['selection']:expected['selection']['path']='inputs/selection.mask'
    assert expected==recipe and recipe['profile'] in PROFILES and session['recipe_sha256']==sha(files['recipe-request.json'])
    defaults=dict(inputBytes=268435456,parser=dict(sequences=1000000,sequenceCharacters=10000000,cells=100000000,decodedCharacters=1073741824,lineCharacters=16777216,headerCharacters=1048576,estimatedRetainedBytes=1073741824),engine=dict(sequences=100000,proteinColumns=1000000,dnaCharactersPerRow=30000000,inputCells=30000000,outputCells=30000000,workSteps=200000000,estimatedRetainedBytes=1073741824),bundleBytes=1073741824,bundleFiles=512)
    if 'limits' in recipe:
        limits=recipe['limits'];defaults={target:limits[source] for source,target in [('input_bytes','inputBytes'),('parser','parser'),('engine','engine'),('bundle_bytes','bundleBytes'),('bundle_files','bundleFiles')]}
    assert manifest['limits']==defaults,'Declared recipe and manifest resource ceilings differ'
    snapshots=['recipe-request.json',recipe['protein']['path'],*recipe['dna']['paths']]
    if 'path' in recipe['selection']:snapshots.append(recipe['selection']['path'])
    pins={name:dict(bytes=len(files[name]),sha256=sha(files[name])) for name in snapshots};assert pins==session['input_snapshot_pins']
    captured=object_(files['capture-receipts.json']);assert captured['schema_version']==1 and len(captured['captures'])==len(pins)
    assert {row['snapshot'] for row in captured['captures']}==set(pins)
    for row in captured['captures']:assert {key:row['original'][key] for key in ['bytes','sha256']}==pins[row['snapshot']]
    heap=session['runtime_heap_bytes'];effective=copy.deepcopy(manifest['limits']);assert type(heap) is int and heap>=33554432
    for name in ['parser','engine']:effective[name]['estimatedRetainedBytes']=min(effective[name]['estimatedRetainedBytes'],heap//5)
    assert session['effective_limits']==effective
    native_mask=None
    if recipe['protein']['format']=='FASTA':proteins=fasta(files[recipe['protein']['path']],recipe['protein']['headers'])
    else:proteins,native_mask=clustal(files[recipe['protein']['path']])
    dna=[]
    for name in recipe['dna']['paths']:dna+=fasta(files[name],recipe['dna']['headers'])
    assert len(dna)==len(proteins) and len({row[0] for row in dna})==len(dna)
    assert all(set(row[2].upper())<=set('ACGTURYSWKMBDHVN') for row in dna)
    option=recipe['options'];peptides=[normalized(row[2],option) for row in proteins];columns=len(peptides[0]);assert all(len(p)==columns for p in peptides)
    mode=recipe['selection']['mode'];selection=native_mask if mode=='CLUSTAL_HASH' else None
    if mode=='MASK_FILE':selection=files[recipe['selection']['path']].decode('utf-8');selection=selection[:-2] if selection.endswith('\r\n') else selection[:-1] if selection.endswith(('\r','\n')) else selection
    if selection is not None:assert len(selection)==columns and set(selection)<=set('# ') and '#' in selection
    if option['pairing']=='EXACT_ID':
        by_id={row[0]:row for row in dna};assert set(by_id)=={row[0] for row in proteins};dna=[by_id[row[0]] for row in proteins]
    else:assert option['pairing']=='INPUT_ORDER'
    widths=[max(3,max(((int(p[c])-1)//3+1)*3 if p[c].isdigit() else 3 for p in peptides)) for c in range(columns)]
    cell_rows=tsv(files['cells.tsv']);row_info=[object_(line) for line in files['rows.ndjson'].splitlines()];assert len(cell_rows)==len(proteins)*columns and len(row_info)==len(proteins)
    expanded=[];mismatches=set();states=Counter();table=tables[option['codon_table']];cell_count=0
    for index,(protein,nucleotide,peptide,info) in enumerate(zip(proteins,dna,peptides,row_info),1):
        matches,relaxed=placement(peptide,nucleotide[2],table,option);position=matches[0];verified=wildcard=mismatch=residues=0;word=False;chunks=[]
        assert info['row']==index and info['output_id']==f'PN{index:08d}' and info['protein_id']==protein[0] and info['dna_id']==nucleotide[0]
        assert info['protein_header']['header']==protein[1] and info['dna_header']['header']==nucleotide[1]
        assert info['dna_start']==position+1 and info['placements_at_least']==len(matches) and info['relaxed']==relaxed
        if option['placement']=='REQUIRE_UNIQUE':assert len(matches)==1
        for column,aa in enumerate(peptide):
            length=0 if aa=='-' else int(aa) if aa.isdigit() else 3;bases=nucleotide[2][position:position+length];assert len(bases)==length
            if aa=='-':state='GAP'
            elif aa.isdigit():state='FRAME_SHIFT'
            else:
                residues+=1
                if aa=='X':state='WILDCARD';wildcard+=1
                else:
                    start=(residues==1 if relaxed else not word) and aa=='M'
                    regex='[ACGR]TG' if relaxed and start else table['B' if start else aa]
                    okay=re.fullmatch(regex,bases,re.I) is not None
                    if okay:state='VERIFIED';verified+=1
                    else:state='MISMATCH';mismatch+=1;mismatches.add(column)
                    word=True
            observed=cell_rows[cell_count];expected=dict(row=str(index),protein_column=str(column+1),dna_start=str(position+1 if length else 0),bases=bases,state=state);assert observed==expected,(index,column,'cell')
            cell_count+=1;states[state]+=1;chunks.append(bases+'-'*(widths[column]-length));position+=length
        assert info['dna_end']==position and info['dna_length']==len(nucleotide[2])
        assert (info['verified_residues'],info['wildcard_residues'],info['mismatched_residues'])==(verified,wildcard,mismatch)
        assert verified>=option['minimum_verified_residues'] and (verified/residues if residues else 0)>=option['minimum_verified_fraction']
        expanded.append(''.join(chunks))
    triplets=tsv(files['triplets.tsv']);expected_triplets=[];position=output=0;kept=[]
    for column,width in enumerate(widths):
        for part in range(width//3):
            start=position+3*part;drops=set()
            if selection is not None and selection[column]!='#':drops.add('UNSELECTED_COLUMN')
            if option['remove_mismatch_columns'] and column in mismatches:drops.add('MISMATCH_COLUMN')
            if option['gap_stop_filter']!='KEEP':
                for row in expanded:
                    value=row[start:start+3]
                    if '-' in value:drops.add('GAP_TRIPLET')
                    stop=re.fullmatch(r'(?:(?:U|T)A(?:A|G|R)|(?:T|U)GA)',value) if option['gap_stop_filter']=='V14_NOGAP' else re.fullmatch(table['*'],value,re.I)
                    if stop:drops.add('STOP_TRIPLET')
            expected_triplets.append(dict(expanded_start=str(start+1),protein_column=str(column+1),part=str(part+1),output_start=str(output+1 if not drops else 0),drops=','.join(sorted(drops))))
            if not drops:kept.append(start);output+=3
        position+=width
    assert triplets==expected_triplets and output>0
    expected_sequences={p[0]:''.join(row[start:start+3] for start in kept) for p,row in zip(proteins,expanded)}
    assert {name:seq for name,_,seq in fasta(files['alignment.fasta'])}==expected_sequences
    assert {name:seq for name,_,seq in fasta(files['alignment.expanded.fasta'])}==dict(zip((p[0] for p in proteins),expanded))
    assert {name:seq for name,_,seq in fasta(files['protein.normalized.fasta'])}==dict(zip((p[0] for p in proteins),peptides))
    phy=files['alignment.phy'].decode().splitlines();assert phy[0]==f'{len(proteins)} {output}';cursor=1
    for index,p in enumerate(proteins,1):
        line=phy[cursor];cursor+=1;assert line[:10]==f'PN{index:08d}' and line[10:11]==' ';sequence=line[11:]
        while len(sequence)<output:sequence+=phy[cursor];cursor+=1
        assert sequence==expected_sequences[p[0]]
    assert cursor==len(phy)
    clustal_rows,_=clustal(files['alignment.aln']);assert {name:sequence for name,_,sequence in clustal_rows}=={f'PN{i:08d}':expected_sequences[p[0]] for i,p in enumerate(proteins,1)}
    summary=object_(files['summary.json']);assert summary['rows']==len(proteins) and summary['protein_columns']==columns and summary['expanded_columns']==position and summary['output_columns']==output and summary['profile']==recipe['profile']
    assert summary['original_tool_executed'] is summary['biological_accuracy_certified'] is summary['whole_paper_reproduced'] is False
    expected_options={'codonTable':option['codon_table'],'pairing':option['pairing'],'matching':option['matching'],'placement':option['placement'],'gapStopFilter':option['gap_stop_filter'],'removeMismatchColumns':option['remove_mismatch_columns'],'selection':selection,'normalizeDocumentedSymbols':option['normalize_documented_symbols'],'normalizeTfastx':option['normalize_tfastx'],'minimumVerifiedResidues':option['minimum_verified_residues'],'minimumVerifiedFraction':option['minimum_verified_fraction']};assert summary['options']==expected_options
    warning=files['WARNINGS.md'].decode();assert 'Required action' in warning and 'SKIP3' in warning and 'FAIL2' in warning and 'not authenticity' in warning
    assert type(summary['work_steps']) is int and 0<summary['work_steps']<=defaults['engine']['workSteps']
    if option['codon_table']==10:assert 'SOURCE_V14_TABLE_10' in warning
    if option['codon_table'] in {3,11,13}:assert 'SOURCE_V14_INITIATORS' in warning
    if option['gap_stop_filter']=='V14_NOGAP':assert 'SOURCE_V14_NOGAP' in warning
    for info in row_info:
        if info['verified_residues']==0:assert 'NO_VERIFIED_RESIDUES' in warning
        if info['mismatched_residues']>0:assert 'MISMATCHED_CODONS' in warning
        if info['wildcard_residues']>0:assert 'UNVERIFIED_X' in warning
    return dict(cells=cell_count,triplets=len(triplets),dropped_triplets=len(triplets)-len(kept),states=dict(states),rows=len(proteins))


def verify(root=ROOT,data=DATA,emit=True):
    proof=object_((data/'pal2nal-catalog-validation.json').read_bytes());report=object_((root/'docs/literature/PAL2NAL_WORKFLOW_DEVELOPMENT_VALIDATION.json').read_bytes())
    assert report['status']=='passed-scoped-PAL2NAL-typed-native-workflow-development' and set(report['source_pins'])==SOURCES
    assert report['native_tests']['tests_per_JVM']==report['native_tests']['passed_per_JVM']==206 and report['native_tests']['failed_per_JVM']==report['native_tests']['skipped_per_JVM']==0
    assert len(report['native_tests']['JVMs'])==3 and report['whole_goal_complete'] is report['biological_accuracy_certified'] is report['released_or_deployed'] is False
    artifact=root/'docs/literature'/report['artifact']['path'];raw=artifact.read_bytes();assert sha(raw)==report['artifact']['sha256'] and len(raw)==report['artifact']['bytes'];captured=unpack(raw)
    for path,pin in report['source_pins'].items():
        saved=captured['source/'+path];assert sha(saved)==pin['sha256'] and len(saved)==pin['bytes']
        if path==GUIDE:verify_guide(saved,(root/path).read_bytes(),proof['guide_delta'])
        elif path=='src/main/java/org/jlinalg/cli/JLinAlgCli.java':verify_source(root,data,path,pin['sha256'])
        else:assert (root/path).read_bytes()==saved,path
    for name,pin in proof['verification_sources'].items():assert sha((root/name).read_bytes())==pin
    rows={}
    for line in captured['source/src/main/resources/org/jlinalg/omics/pal2nal-v14-codes.tsv'].decode().splitlines():
        table,aa,regex=line.split('\t');rows.setdefault(int(table),{})[aa]=regex
    assert len(rows)==17 and sum(map(len,rows.values()))==408
    ncbi=unpack(captured['source/src/test/resources/org/jlinalg/sequence/pal2nal-original/ncbi-codes.zip']);differences=[];initiators=[];extra=[];codons=[a+b+c for a in 'TCAG' for b in 'TCAG' for c in 'TCAG']
    for line in ncbi['tables.tsv'].decode().splitlines():
        number,amino,starts=line.split('\t');table=int(number)
        for index,codon in enumerate(codons):
            aa=[key for key in 'ACDEFGHIKLMNPQRSTVWY*' if re.fullmatch(rows[table][key],codon)];assert len(aa)==1
            if aa[0]!=amino[index]:differences.append((table,codon,amino[index],aa[0]))
            actual=re.fullmatch(rows[table]['B'],codon) is not None
            if starts[index]=='M' and not actual:initiators.append((table,codon))
            if starts[index]!='M' and actual:extra.append((table,codon))
    assert differences==[(10,'TGA','C','*')] and set(initiators)=={(3,'GTG'),(11,'ATT'),(11,'ATC'),(11,'ATA'),(13,'ATA')} and not extra
    fixture=unpack(captured['source/src/test/resources/org/jlinalg/sequence/pal2nal-original/controls.zip']);original=object_(fixture['cases.json']);assert len(original)==85
    other=unpack(captured['source/src/test/resources/org/jlinalg/sequence/pal2nal-original/controls-141.zip'])
    for case in original:assert fixture[case['name']+'/stdout']==other[case['name']+'/stdout']
    matrix_raw=(data/proof['native_controls']['path']).read_bytes();assert sha(matrix_raw)==proof['native_controls']['sha256'] and len(matrix_raw)==proof['native_controls']['bytes'];matrix=unpack(matrix_raw)
    collected=object_(matrix['summary.json']);assert collected['protocols']==170 and collected['completed']==156 and collected['expected_failures']==14 and collected['all_owned_processes_terminal'] is True
    assert collected['jar_sha256']==report['packaged_candidate_sha256'] and collected['collector_sha256']==sha((root/'tools/literature/collect_pal2nal_catalog_controls.py').read_bytes())
    census={'summary.json'};total=Counter();outcomes=Counter()
    for version in ['14','14.1']:
        for case in original:
            prefix='v'+version+'/'+case['name']+'/';expected=2 if case['comparison']=='native-refusal' else 0
            assert matrix[prefix+'protein.aln']==fixture[case['name']+'/protein.aln'] and matrix[prefix+'dna.fasta']==fixture[case['name']+'/dna.fasta']
            observed=object_(matrix[prefix+'observed.json']);assert observed['exit_code']==observed['expected_exit_code']==expected and observed['version']==version
            census|={prefix+name for name in ['protein.aln','dna.fasta','recipe.json','stdout','stderr','observed.json']}
            result={name[len(prefix+'result/'):]:raw for name,raw in matrix.items() if name.startswith(prefix+'result/')};census|={prefix+'result/'+name for name in result}
            if expected==0:
                assert object_(result['recipe-request.json'])==object_(matrix[prefix+'recipe.json'])
                assert object_(result['recipe-request.json'])['profile']=='native-pal2nal-v'+version+'-matching'
                checked=verify_case(result,rows)
                for key in ['cells','triplets','dropped_triplets','rows']:total[key]+=checked[key]
                if case['comparison']=='exact-rows':assert {name:sequence for name,_,sequence in fasta(result['alignment.fasta'])}=={name:sequence for name,_,sequence in fasta(fixture[case['name']+'/stdout'])}
                else:assert case['comparison']=='documented-normalization-departure'
            else:assert 'COMPLETED.json' not in result and object_(result['FAILURE.json'])['exit_code']==2
            outcomes[expected]+=1
    assert set(matrix)==census and outcomes=={0:156,2:14}
    for name,tests in [('Pal2NalToolsCliTest',103),('Pal2NalClustalTest',5),('Pal2NalTest',98)]:
        path=next(key for key in captured if key.startswith('validation/TEST-') and key.endswith(name+'.xml'));suite=ET.fromstring(captured[path]);assert int(suite.attrib['tests'])==tests and all(int(suite.attrib[k])==0 for k in ['failures','errors','skipped'])
    packaged=object_(captured['packaged-tests-2/summary.json']);assert packaged['tests_per_JVM']==206 and len(packaged['runs'])==3 and all(run['exit_code']==0 for run in packaged['runs']) and packaged['artifact_sha256']==report['packaged_candidate_sha256']
    for name in ['public-cli-2/public-workflows.zip','public-cli-2/linux-result-persistent.zip']:
        payload=unpack(captured[name])
        if name.endswith('public-workflows.zip'):
            for prefix in ['result/','result-141/']:verify_case({key[len(prefix):]:raw for key,raw in payload.items() if key.startswith(prefix)},rows)
        else:verify_case(payload,rows)
    assert report['capture_recovery']['files']==19 and report['capture_recovery']['Windows_exit_code']==0 and report['retention_warning']
    result=dict(status='passed-complete-PAL2NAL-catalog-evidence',protocols=170,completed=156,expected_failures=14,**dict(total),independent_codons=1088,reviewed_roles=2)
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':
    try:verify()
    except Exception as failure:print('PAL2NAL EVIDENCE FAIL2: '+str(failure)+'\nRequired action: preserve all source/results, correct the named mismatch, and repeat validation. Do not promote a partial or changed scientific result.',file=__import__('sys').stderr);raise
