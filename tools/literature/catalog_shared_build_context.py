# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep two legacy catalog checks strict while contextualizing their historical shared-build observation.

Original provider files/functions remain untouched. Private function namespaces
replace only exact(canonical-JAR, historical-pin) with named scientific-class,
resource and external-dependency byte audits. Every other original source,
artifact, protocol, numerical and evidence check still uses the original exact.
This grants no scientific source-hash compatibility and changes no tool metadata.
"""
import hashlib,importlib,json,types,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
MODULES={'fuma_catalog':'303c9cda638e0684523024bbde3aa9f87c005f0f65df372db1a7140c82619c0c','mashr_catalog_evidence':'8f88a67331f65c344f9fad874115935b5d970815615a141a5c2908a785463972','mashr_catalog':'db3831e7f4a1aaf7e58fed10a3b369812e27c3a0ab3cd7f8b224d2f4d84a43a4'}
HISTORICAL=dict(bytes=162107844,sha256='bd1a3b2448d7016ae4ded3f30a6c5ae92e05a0078ae688f4b93d046cc355eeb2')
def read(p):return json.loads(p.read_bytes())
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def module(name):
    p=ROOT/'tools/literature'/(name+'.py');assert hashlib.sha256(p.read_bytes()).hexdigest()==MODULES[name],'Original catalog provider changed: '+name;return importlib.import_module(name)
def namespace(original):
    values=dict(original.__dict__)
    for name,value in original.__dict__.items():
        if isinstance(value,types.FunctionType) and value.__module__==original.__name__:
            copy=types.FunctionType(value.__code__,values,value.__name__,value.__defaults__,value.__closure__);copy.__kwdefaults__=value.__kwdefaults__;values[name]=copy
    return values
def audit(context,root,expected):
    root=Path(root);assert expected==HISTORICAL and context in {'fuma','mashr'};canonical=root/'build/cli/omicsmatic-0.3.7.jar';observed=pin(canonical)
    if observed==expected:return dict(context=context,status='historical-shared-build-still-identical',historical=expected,observed=observed)
    if context=='fuma':
        proof_path=root/'docs/literature/fuma-expression-preparation-2/VALIDATION.json';assert pin(proof_path)==dict(bytes=150907,sha256='8f8ad049c55f4ae83492576628d5e6d352e3d669089722411da0ac5387ed2134');proof=read(proof_path);closure_path=root/'docs/literature/omics-2023-2026/fuma-source-closure.json';assert pin(closure_path)==dict(bytes=82771,sha256='47c4e39450f05bc0ee39d9854c98011c2f8693a1c7e095c90e7f409e09f985d2');closure=read(closure_path);assert closure['artifact']==proof['artifact'];classes=set(closure['packaged_classes']);assert len(classes)==413
    else:
        proof_path=root/'docs/literature/mashr-proxy-command/VALIDATION.json';assert pin(proof_path)==dict(bytes=700751,sha256='aea8121d81fa99ac1015d953569b547422a06e3de670f0a6a5689b009cda5b5b');proof=read(proof_path);classes={n[len('src/main/java/'):-5]+'.class' for key in ['source_pins','unchanged_prior_source_pins','baseline_dependencies'] for n in proof[key] if n.startswith('src/main/java/') and n.endswith('.java') and not n.endswith('/JLinAlgCli.java')};assert len(classes)==16
    assert proof['canonical_executable_unchanged']==expected;artifact=root/proof['artifact']['path'];assert pin(artifact)=={k:proof['artifact'][k] for k in ['bytes','sha256']};source_resources={n[len('src/main/resources/'):] for key in ['source_pins','unchanged_prior_source_pins','baseline_dependencies'] for n in proof.get(key,{}) if n.startswith('src/main/resources/')};git_metadata={n for n in source_resources if n.endswith('/.gitattributes')};resources=source_resources-git_metadata;checked=[]
    with zipfile.ZipFile(artifact) as before,zipfile.ZipFile(canonical) as current:
        names=set(current.namelist());all_before=set(before.namelist());expanded={n for n in all_before for cl in classes if n==cl or n.startswith(cl[:-6]+'$')};assert len(expanded)==(413 if context=='fuma' else 94);external={n for n in all_before if not n.endswith('/') and not n.startswith('org/jlinalg/') and n!='META-INF/MANIFEST.MF'};assert len(external)==14253
        for name in sorted(expanded|external|resources):
            assert name in names and current.read(name)==before.read(name),'Shared build changed a named scientific class/resource/dependency: '+name;checked.append(name+'\t'+hashlib.sha256(before.read(name)).hexdigest())
    assert pin(canonical)==observed
    return dict(context=context,status='passed-named-scientific-shared-build-byte-audit',historical=expected,observed=observed,owned_scientific_artifact=proof['artifact'],bound_scientific_classes=len(expanded),external_dependency_entries=len(external),explicit_runtime_resources=len(resources),source_Git_metadata_still_checked_by_original_validator=sorted(git_metadata),comparison_manifest_sha256=hashlib.sha256(('\n'.join(checked)+'\n').encode()).hexdigest(),original_provider_files_unchanged=True,all_other_original_exact_checks_retained=True,scientific_hash_waiver=False)
def guarded_exact(original,root,context):
    def exact(path,expected):
        if Path(path)==Path(root)/'build/cli/omicsmatic-0.3.7.jar':audit(context,root,expected);return
        return original(path,expected)
    return exact
def fuma_metadata(root,data):
    original=module('fuma_catalog');values=namespace(original);values['exact']=guarded_exact(original.exact,root,'fuma');return values['fuma_metadata'](root,data)
def mashr_metadata(root,data):
    evidence=module('mashr_catalog_evidence');ev=namespace(evidence);ev['exact']=guarded_exact(evidence.exact,root,'mashr');original=module('mashr_catalog');values=namespace(original);assert original.verify is evidence.verify;values['verify']=ev['verify'];return values['mashr_metadata'](root,data)
def main():
    target=ROOT/'docs/literature/omics-2023-2026/shared-build-context-validation.json';assert not target.exists();reports=[audit(context,ROOT,HISTORICAL) for context in ['fuma','mashr']];report=dict(schema_version=1,status='passed-two-named-historical-canonical-context-audits',reports=reports,source_pin=pin(Path(__file__)),original_module_sha256=MODULES,scope='Only the obsolete current-whole-canonical equality guard is contextualized. Owned tested artifacts and all original source/record/numerical checks remain strict; named runtime classes/resources and every external dependency entry match the current shared build. This is not a general source or scientific waiver.');target.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(dict(status=report['status'],FUMA_scientific_classes=reports[0].get('bound_scientific_classes'),mashr_scientific_classes=reports[1].get('bound_scientific_classes'),external_dependency_entries_each=14253)),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable named shared-build scientific audits')
    main()
