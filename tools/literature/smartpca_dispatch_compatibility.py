# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit saved predecessor context; historical hashes are never refreshed."""
from pathlib import Path
import hashlib,json

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root,read):
    folder='pre-dispatch-baseline-1/'
    baseline=json.loads(read(folder+'RESULT.json'));extension=json.loads(read(folder+'DISPATCHER-EXTENSION.json'));before=read(folder+'JLinAlgCli.java')
    current=(Path(root)/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_bytes()
    assert baseline['status']=='PASS_PRE_SMARTPCA_DISPATCH_FROZEN_READERS' and baseline['current_source_context_at_execution'] and not baseline['historical_hashes_refreshed']
    readers={r['reader']:r for r in baseline['readers']};assert set(readers)=={'verify_mrpresso_catalog_checkpoint.py','verify_smartpca_workflow_checkpoint.py'}
    assert all(r['exit_code']==0 for r in readers.values())
    for name,row in readers.items():assert json.loads(read(folder+name+'.stdout'))==row['measured'] and json.loads(read(folder+name+'.json'))['exit_code']==0
    mr=readers['verify_mrpresso_catalog_checkpoint.py']['measured'];pca=readers['verify_smartpca_workflow_checkpoint.py']['measured']
    assert mr['counts']['scoped_families']==153 and mr['counts']['reviewed_uses']==1691 and mr['complete_manifest_entries']==859
    assert pca['status']=='PASS_NATIVE_PREPARED_SMARTPCA_RUN_VERIFY_REPLAY' and pca['normal_tests']==1892 and pca['packaged_protocol_commands']==261
    assert pin(before)==baseline['dispatcher']==extension['before'] and pin(current)==extension['after']
    allowed={b'if (arguments.length > 0 && arguments[0].equals("smartpca-tools"))',b'return SmartpcaCommandCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);',b'java -jar omicsmatic-<version>.jar smartpca-tools --help'}
    assert set(extension['allowed_added_lines'])=={s.decode() for s in allowed} and extension['exact_source_extension_lines']==3 and not extension['historical_hashes_refreshed']
    lines=current.splitlines(keepends=True);added=[line.strip() for line in lines if line.strip() in allowed]
    assert len(added)==3 and set(added)==allowed and b''.join(line for line in lines if line.strip() not in allowed)==before,'Dispatcher exceeds the exact qualified source extension'
    return dict(status='PASS_EXACT_SMARTPCA_DISPATCH_EXTENSION_WITH_RETAINED_PREDECESSOR_CONTEXT',predecessor_commit=extension['predecessor_commit'],exact_source_extension_lines=3,
        old_dispatcher=extension['before'],current_dispatcher=extension['after'],historical_hashes_refreshed=False,
        recorded_predecessor_readers=list(readers),old_readers_reexecuted_in_changed_dispatch_context=False,source_contexts_kept_distinct=True)
