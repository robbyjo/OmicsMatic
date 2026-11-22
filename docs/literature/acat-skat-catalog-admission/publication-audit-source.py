"""Audit the bounded source/catalog publication and every nested archive member."""
from pathlib import Path
import hashlib,io,json,re,sys,zipfile
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/literature'))
from acat_skat_catalog import primary as p,model as m,evidence as e
FORBIDDEN={'.pdf','.bam','.bai','.vcf','.bcf','.xml','.gz','.rds','.rda','.rdata','.jar','.class','.exe','.dll','.so','.whl','.pyc','.tsv','.csv'}
TOKEN=re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|(?:AKIA|ASIA)[A-Z0-9]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
FULLTEXT=re.compile(rb'<!DOCTYPE\s+article\b|<article\s+xmlns|'+b'<'+rb'PubmedArticle\b')
LIMIT=256*1024*1024
def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    parser=__import__('argparse').ArgumentParser(description=__doc__);parser.add_argument('--publication-files',type=Path,required=True);args=parser.parse_args()
    files=json.loads(args.publication_files.read_bytes());require(len(files)==56,'Different publication population');q=ROOT/e.FOLDER;target=q/'PUBLICATION-DATA-AUDIT.json';require(not target.exists(),'Retain existing audit;use a NEW checkpoint instead of overwriting')
    nested={};total=0
    def check(name,raw,depth=0):
        nonlocal total
        total+=len(raw);require(total<=LIMIT and depth<=5,'Publication archive capacity exceeded')
        require(not name.startswith('/') and '..' not in Path(name).parts,'Unsafe member path: '+name)
        suffix=Path(name).suffix.lower();require(suffix not in FORBIDDEN,'Private/runtime/input artifact in publication: '+name)
        require(not TOKEN.search(raw),'Credential candidate;manual review required: '+name)
        require(not FULLTEXT.search(raw),'Primary full-text candidate;manual review required: '+name)
        if suffix=='.png':require('/browser/desktop.png' in name or '/browser/mobile.png' in name,'Unapproved image;publisher renders must stay private: '+name)
        if suffix=='.zip':
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                require(len(archive.namelist())==len(set(archive.namelist())),'Duplicate archive names: '+name)
                for member in archive.infolist():
                    require(member.file_size<=32*1024*1024,'Oversized member: '+member.filename);pure=Path(member.filename);require(not pure.is_absolute() and '..' not in pure.parts,'Unsafe nested member');data=archive.read(member);key=name+'!/'+member.filename;nested[key]=pin(data);check(key,data,depth+1)
    for name,wanted in files.items():
        path=ROOT/name;require(path.is_file() and not path.is_symlink(),'Not a regular publication file: '+name);raw=path.read_bytes();require(pin(raw)==wanted,'Changed prepared publication bytes: '+name);check(name,raw)
    own=Path(__file__).resolve();files[own.relative_to(ROOT).as_posix()]=pin(own.read_bytes());check(own.relative_to(ROOT).as_posix(),own.read_bytes())
    census=p.frozen();require(len(census['roles'])==22 and all('text' not in anchor for candidate in census['candidates'] for anchor in candidate['anchors']),'Unexpected source population/full text')
    validation=json.loads((q/'VALIDATION.json').read_bytes());require(validation['result']['integrations']==147 and validation['result']['reviewed_uses']==1661 and validation['result']['pending_uses']==1062 and validation['all65_prior_owners_preserved'],'Different installed qualification')
    result=dict(status='PASS_ACAT_SKAT_BOUNDED_PUBLICATION_DATA_AUDIT',audited_top_level_files=dict(sorted(files.items())),nested_archive_members=nested,total_bytes_scanned=total,credential_candidates=0,primary_full_text_candidates=0,private_XML_PDF_text_publisher_renders_dependencies_binaries_or_individual_cohort_inputs_published=False,primary_census_is_pointers_hashes_and_paraphrases=True,UI_images_are_own_reports=True,source_guarded_canonical_update=True,scientific_source_changed=False,private_transfer_completed=False,whole_goal_complete=False)
    with target.open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print('PASS publication data audit:',len(files),'files;',len(nested),'nested members;',total,'bytes;no private full text,runtime/input or credential candidates.')
    return 0
if __name__=='__main__':raise SystemExit(main())
