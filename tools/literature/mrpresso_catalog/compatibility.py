"""Explicit pre-MR catalog-context bridge; never refresh a historical hash."""
import json,zipfile
from mrpresso_catalog import primary as p

def verify():
    root=p.ROOT;folder=root/'docs/literature/mrpresso-development-handover-20261123'
    evidence=json.loads((folder/'EVIDENCE.json').read_bytes());archive=folder/evidence['archive']
    p.require(p.pin(archive.read_bytes())==evidence['identity']==dict(bytes=4816667,sha256='a04fe84b4341689199dfb2189cd4b98754cee0fe2dc130323f1848de9b0a7c5f'),'Changed retained pre-dispatch packet')
    with zipfile.ZipFile(archive) as z:
        def member(name):
            raw=z.read(name);p.require(p.pin(raw)==evidence['members'][name],'Changed archived baseline: '+name);return raw
        old=json.loads(member('pre-dispatch-baseline-1/RESULT.json'));before=member('pre-dispatch-baseline-1/JLinAlgCli.java')
        extension=json.loads(member('pre-dispatch-baseline-1/DISPATCHER-EXTENSION.json'))
    p.require(old['exit_code']==0 and old['measured']['status']=='PASS_SQUIDPY_ONE_FAMILY_SIX_ROLES_WITH_RETAINED_WEBSITE_FAILURE' and
        old['measured']['complete_manifest_entries']==857 and old['measured']['counts']['scoped_families']==152 and old['measured']['counts']['reviewed_uses']==1685,
        'Missing successful recorded pre-MR catalog verification')
    source='src/main/java/org/jlinalg/cli/JLinAlgCli.java';current=(root/source).read_bytes()
    p.require(p.pin(before)==extension['before'] and p.pin(current)==extension['after'],'Changed old/new dispatcher identities')
    allowed={b'if (arguments.length > 0 && arguments[0].equals("mrpresso-tools"))',
        b'return MrPressoToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);',
        b'java -jar omicsmatic-<version>.jar mrpresso-tools --help'}
    lines=current.splitlines(keepends=True);removed=[x.strip() for x in lines if x.strip() in allowed]
    p.require(len(removed)==3 and set(removed)==allowed and b''.join(x for x in lines if x.strip() not in allowed)==before,'Dispatcher change exceeds two route lines plus one help line')
    q=root/'docs/literature/mrpresso-workflow-qualification';index=json.loads((q/'EVIDENCE.json').read_bytes())
    with zipfile.ZipFile(q/index['archive']) as z:
        name='normal-build-4/PAYLOADS.json';raw=z.read(name);p.require(p.pin(raw)==index['members'][name],'Changed actual preceding-payload comparison');payloads=json.loads(raw)
    p.require(payloads['previous_payloads']==22895 and payloads['modified']==['org/jlinalg/cli/JLinAlgCli.class'],'Prior scientific payloads changed')
    return dict(status='PASS_EXPLICIT_PRE_MR_CATALOG_AND_DISPATCH_EXTENSION_BRIDGE',recorded_prior_catalog_commit='e763d9ce33e8f8f2ef7dc6fd7bf0d8fc3c885e51',
        recorded_pre_dispatch_reader=old['measured']['status'],historical_reader_reexecuted_now=False,
        historical_source_hashes_refreshed=False,exact_source_extension_lines=3,previous_payloads=22895,unchanged_previous_payloads=22894,
        sole_changed_previous_payload='org/jlinalg/cli/JLinAlgCli.class',old_dispatcher=extension['before'],current_dispatcher=extension['after'],
        old_packet=evidence['identity'],source_contexts_kept_distinct=True)
