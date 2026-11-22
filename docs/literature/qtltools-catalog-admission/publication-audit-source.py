"""Audit this bounded publication's complete files and nested archives; execute no saved code."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,re,zipfile

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[3]);parser.add_argument('--files',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();root=args.root.resolve();document=json.loads(args.files.read_bytes());files=document.get('audited_top_level_files',document);members={};decoded=0
    secrets=re.compile(rb'AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (?:OPENSSH |RSA |EC )?PRIVATE KEY-----')
    forbidden={'.pdf','.xml','.gz','.bam','.bai','.bcf','.vcf','.whl','.jar','.class','.exe','.dll','.so','.pyc'}
    def visit(name,raw,depth=0):
        nonlocal decoded
        assert depth<=5 and len(raw)<=32*1024*1024,('Capacity',name);decoded+=len(raw);assert decoded<=256*1024*1024,'Total capacity'
        assert not secrets.search(raw),('Credential candidate',name)
        suffix=PurePosixPath(name).suffix.lower();assert suffix not in forbidden,('Private/source/runtime member',name)
        assert not raw.startswith((b'%PDF-',b'\x7fELF',b'BAM\x01',b'BCF\x02')),('Private binary payload',name)
        if suffix=='.zip':
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                assert len(archive.namelist())==len(set(archive.namelist())) and len(archive.namelist())<=4096 and archive.testzip() is None
                for item in archive.infolist():
                    path=PurePosixPath(item.filename);assert not path.is_absolute() and '..' not in path.parts and (item.external_attr>>16)&0o170000!=0o120000
                    if item.is_dir():continue
                    content=archive.read(item);label=name+'!/'+item.filename;members[label]=pin(content);visit(label,content,depth+1)
        elif suffix=='.png':assert raw.startswith(b'\x89PNG\r\n\x1a\n') and ('desktop.png' in name or 'mobile.png' in name),'Only rendered public-catalog screenshots are allowed'
        else:
            text=raw.decode('utf-8');assert '<!DOCTYPE article' not in text and '<article xmlns' not in text,('Primary full text',name)
            if suffix=='.json':
                value=json.loads(text)
                def check(value):
                    if isinstance(value,dict):
                        for key,item in value.items():
                            assert not (key in ['evidence_text','paragraph_text','fulltext','full_text'] and isinstance(item,str) and len(item)>300),('Primary-text field',name,key)
                            check(item)
                    elif isinstance(value,list):
                        for item in value:check(item)
                check(value)
    for name,wanted in files.items():
        path=(root/name).resolve();assert path.is_relative_to(root) and pin(path.read_bytes())==wanted,('Changed publication input',name);visit(name,path.read_bytes())
    result=dict(status='PASS_BOUNDED_PUBLICATION_SOURCE_PARAPHRASES_PREVIOUS_PUBLIC_METADATA_AND_CONTROLS_ONLY',audited_top_level_files=files,nested_members=members,total_decoded_bytes=decoded,credential_candidates=0,private_primary_XML_PDF_text_renders_dependencies_binaries_or_individual_cohort_data_published=False,scope='Exact previously public catalog metadata;paraphrased source roles;code;owned process/test/source-refusal logs;hashes;public-catalog browser screenshots. Publisher PDF/renders,source XML/corrupted-XML controls,reader wheel/installation and scientific runtimes stay private.',audit_source=pin(Path(__file__).read_bytes()),whole_goal_complete=False)
    with args.out.open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print('PASS publication audit:',len(files),'files;',len(members),'nested members;',decoded,'decoded bytes;zero credential candidates;private primary files and runtimes excluded')

if __name__=='__main__':main()
