"""Independently check every publication blob and artificial-input lineage;run no model."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/qtltools-workflow-development'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)
def audit(root):
    folder=root/FOLDER;index=json.loads((folder/'INDEX.json').read_bytes())
    require(len(index)<=60000,'Excessive retained population')
    expected={'blobs/'+row['sha256']:row for row in index.values()}
    with zipfile.ZipFile(folder/'controls.zip') as archive:
        require(len(archive.namelist())==len(expected)==len(set(archive.namelist())) and set(archive.namelist())==set(expected),'Different complete publication blob population')
        def raw(name):
            wanted=index[name];value=archive.read('blobs/'+wanted['sha256']);require(pin(value)==wanted,'Changed logical publication file');return value
        regenerated=json.loads(raw('current/input-provenance-1/RESULT.json'))
        require(regenerated['status']=='PASS_ALL11_ARTIFICIAL_INPUTS_INDEPENDENTLY_REGENERATED_BYTE_IDENTICAL' and regenerated['seed']==20261121 and len(regenerated['files'])==11,'Different artificial-input regeneration scope')
        first=json.loads(raw('current/synthetic-1/RESULT.json'));second=json.loads(raw('current/synthetic-regeneration-1/RESULT.json'))
        require(first['exit_code']==second['exit_code']==0 and first['inputs']==second['inputs']==regenerated['files'],'Artificial inputs do not regenerate byte-identically')
        generator=root/'tools/literature/qtltools_workflow/SyntheticQtltoolsInputs.java';require(pin(generator.read_bytes())==regenerated['generator_source'],'Changed artificial-input generator')
        allowed={row['sha256'] for row in regenerated['files'].values()}
        for edition in ['1.1','1.2','1.3.1']:
            for name in ['current/adapter-pilots-2/'+edition+'/prepared-null.txt','current/direct-original-1/'+edition+'/trans-sample/result.best.txt.gz']:allowed.add(index[name]['sha256'])
        inputs=bio=0
        for name,row in index.items():
            require('private-context/' not in name and 'private-primary/' not in name,'Private paper context selected')
            if '/inputs/' in name:
                require(row['sha256'] in allowed,'Captured input is not a verified artificial input/null table: '+name);inputs+=1
                if name.endswith(('.bam','.vcf','.vcf.gz','.bcf','.sam','.fastq','.fastq.gz')):bio+=1
        patterns=[re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),re.compile(rb'\bgh[pousr]_[A-Za-z0-9]{36}\b'),re.compile(rb'\bgithub_pat_[A-Za-z0-9_]{50,}\b')]
        candidates=[];decoded=0
        for member,wanted in expected.items():
            require(re.fullmatch(r'blobs/[0-9a-f]{64}',member) is not None,'Unsafe blob name');info=archive.getinfo(member);require(info.file_size<=32*1024*1024,'Excessive member');decoded+=info.file_size;require(decoded<=512*1024*1024,'Excessive total bytes')
            value=archive.read(member);require(pin(value)==wanted,'Changed publication blob');require(not value.startswith((b'\x7fELF',b'\xca\xfe\xba\xbe')),'Runtime/core/class binary selected')
            if any(pattern.search(value) for pattern in patterns):candidates.append(member)
        require(not candidates,'Credential-pattern candidate requires private inspection')
    source=json.loads((folder/'SOURCE.json').read_bytes());vendor_files=0
    for edition,record in source['editions'].items():
        require(edition in {'1.1','1.2','1.3.1'},'Unknown upstream source edition')
        for name,wanted in record['selected_sources'].items():
            require(name in {'LICENSE','Makefile','src/QTLtools.cpp'} or re.fullmatch(r'src/mode_(?:cis|trans|match|quan|fdensity)/[A-Za-z0-9_]+\.(?:cpp|h)',name),'Non-source upstream member selected')
            value=(folder/'upstream-source'/edition/name).read_bytes();require(pin(value)==wanted,'Changed exact upstream source/license');require(not any(pattern.search(value) for pattern in patterns),'Upstream source contains a credential-pattern candidate');vendor_files+=1
    require(vendor_files==141,'Different upstream source/license population')
    return dict(status='PASS_PUBLICATION_ALL_BLOBS_ARTIFICIAL_INPUTS_PUBLIC_SOURCE_AND_DIAGNOSTICS_ONLY',logical_files=len(index),unique_blobs=len(expected),decoded_unique_bytes=decoded,independently_regenerated_artificial_files=11,captured_input_files_all_verified_artificial=inputs,captured_BAM_VCF_files_all_verified_artificial=bio,exact_public_upstream_source_and_license_files=vendor_files,artificial_BAM_VCF_formats_published=True,individual_or_project_cohort_data_published=False,private_primary_fulltext_or_author_data_published=False,runtime_executables_dependencies_classes_or_crash_cores_published=False,credential_candidates=0,controls_archive=pin((folder/'controls.zip').read_bytes()),index=pin((folder/'INDEX.json').read_bytes()),generator_source=regenerated['generator_source'],audit_source=pin(Path(__file__).read_bytes()),scope='All input bytes match seed20261121 regeneration or explicitly derived all-row artificial null tables. Scientific outputs,source snapshots,exact GPL/BSD-noticed upstream code,public metadata and diagnostics only. Hashes are integrity,not authentication.')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--out',type=Path,required=True);args=p.parse_args();require(__debug__,'Use Python without -O');require(not args.out.exists(),'Use NEW audit JSON path');result=audit(args.root.resolve());args.out.parent.mkdir(parents=True,exist_ok=True)
    with args.out.open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print(json.dumps({key:value for key,value in result.items() if key not in {'scope','generator_source','controls_archive','index'}}));return 0
if __name__=='__main__':raise SystemExit(main())
