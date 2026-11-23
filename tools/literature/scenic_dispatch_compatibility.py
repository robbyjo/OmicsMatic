"""Retain historical verifier contexts across the exact new SCENIC dispatch lines."""
from pathlib import Path
import hashlib,json


def pin(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def verify(root,read):
    prefix='pre-dispatch-baseline-1/'
    baseline=json.loads(read(prefix+'RESULT.json'));extension=json.loads(read(prefix+'DISPATCHER-EXTENSION.json'))
    before=read(prefix+'JLinAlgCli.java');current=(Path(root)/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_bytes()
    if baseline['status']!='PASS_CURRENT_PRE_SCENIC_DISPATCH_READERS' or not baseline['current_source_context_at_execution'] or baseline['historical_hashes_refreshed']:
        raise ValueError('Incorrect retained predecessor context')
    readers=baseline['readers']
    if {(r['host'],r['reader']) for r in readers}!={(host,name) for host in ('windows','linux') for name in ('verify_pyscenic_aucell_component.py','verify_smartpca_catalog_checkpoint.py')}:
        raise ValueError('Missing historical reader/host context')
    for row in readers:
        folder=prefix+row['host']+'-'+row['reader']+'/'
        if row['exit_code']!=0 or json.loads(read(folder+'RESULT.json'))['exit_code']!=0 or json.loads(read(folder+'stdout'))!=row['measured']:
            raise ValueError('Historical predecessor readback differs')
        if row['reader']=='verify_smartpca_catalog_checkpoint.py':
            count=row['measured']['counts']
            if (count['scoped_families'],count['reviewed_uses'],count['pending_uses'])!=(154,1697,1025):
                raise ValueError('Historical catalog totals differ')
    allowed={b'if (arguments.length > 0 && arguments[0].equals("scenic-tools"))',b'return ScenicToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);',b'java -jar omicsmatic-<version>.jar scenic-tools --help'}
    if pin(before)!=baseline['dispatcher'] or pin(before)!=extension['before'] or pin(current)!=extension['after']:
        raise ValueError('Dispatcher source identities differ')
    if set(extension['allowed_added_lines'])!={v.decode() for v in allowed} or extension['exact_source_extension_lines']!=3 or extension['historical_hashes_refreshed']:
        raise ValueError('Changed permitted source extension')
    lines=current.splitlines(keepends=True);added=[line.strip() for line in lines if line.strip() in allowed]
    if len(added)!=3 or set(added)!=allowed or b''.join(line for line in lines if line.strip() not in allowed)!=before:
        raise ValueError('Dispatcher exceeds the exact SCENIC extension')
    return dict(status='PASS_EXACT_SCENIC_DISPATCH_WITH_RETAINED_PREDECESSOR_CONTEXT',old_reader_passes=4,exact_added_lines=3,
        old_readers_reexecuted_in_changed_dispatch_context=False,historical_hashes_refreshed=False,source_contexts_kept_distinct=True)
