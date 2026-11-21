from pathlib import Path
import hashlib,json,tarfile,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';B.mkdir(parents=True);Q=T/'src/main/resources/org/jlinalg/omics/limma-workflow';Q.mkdir()
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
archives={
 'limma':(R/'build/lm-2/build/limma-native-1/source-3.68.5-1/limma_3.68.5.tar.gz','3.68.5','40b1d2059a76ad98db4bf80139760acf8e8bae92a8265aaaf16ac199831533f4'),
 'statmod':(R/'build/lm-2/build/limma-native-1/statmod-source-1.5.2-1/statmod_1.5.2.tar.gz','1.5.2','5f83d82043c5e26f1048fc0139580da1b7e40becf95fb4cb26ed3afcd46a581d'),
 'edgeR':(R/'build/edge-1/build/edger-high-impact-1/source-intake-1/4.10.7/edgeR_4.10.7.tar.gz','4.10.7','a3cc0f66be29f1a61a74021f9848ec940794e653905c4332327a719dd9ad8393')}
pins={};members={}
with zipfile.ZipFile(Q/'source.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
    for package,(path,version,sha) in archives.items():
        assert pin(path.read_bytes())['sha256']==sha;pins[package]=dict(version=version,archive=pin(path.read_bytes()))
        with tarfile.open(path) as tar:
            for member in tar.getmembers():
                if member.isfile() and (member.name.startswith(package+'/R/') and member.name.endswith('.R') or member.name in [package+'/DESCRIPTION',package+'/NAMESPACE']):
                    name=member.name;raw=tar.extractfile(member).read();z.writestr(name,raw);members[name]=pin(raw)
profile='limma-3.68.5-edgeR-4.10.7-statmod-1.5.2-R4.6.1'
(Q/'SOURCE-PROFILE.json').write_text(json.dumps(dict(schema_version=1,profiles={profile:dict(R='4.6.1',packages=pins)},files={'source.zip':pin((Q/'source.zip').read_bytes())},members=members,scope='Original dense prepared-data matrix workflows; exact function bodies/formals and recorded package/DLL identities, not native-DLL or host authentication'),indent=2)+'\n',encoding='utf-8',newline='\n')
source=(T/'src/main/java/org/jlinalg/cli/EdgeRWorkflowRuntime.java').read_text(encoding='utf-8').replace('EdgeRWorkflow','LimmaWorkflow').replace('edger-workflow','limma-workflow').replace('edger_workflow','limma_workflow').replace('edger-models','limma-workflows').replace('edgeR','limma/edgeR/statmod')
source=source.replace('Map<String,Long> baseline=footprint(worker);','Map<String,Long> baseline=footprint(root);').replace('checkOutputFootprint(worker,baseline,outputCap)','checkOutputFootprint(root,baseline,outputCap)')
(T/'src/main/java/org/jlinalg/cli/LimmaWorkflowRuntime.java').write_text(source,encoding='utf-8',newline='\n')
(B/'SOURCES-PREPARED.json').write_text(json.dumps(dict(status='SAVED_ORIGINAL_WORKFLOW_SOURCES',packages=pins,profile=profile,members=len(members),source_archive=pin((Q/'source.zip').read_bytes()),old_sources_changed=False),indent=2)+'\n',encoding='utf-8',newline='\n')
print('Prepared original limma/edgeR/statmod source profiles and separate owned-worker runtime',flush=True)
