"""Review the new replay destination guard as a separately qualified control change.

This is not a source-lookup inverse for scientific qualification. Callers must
retain the previous source as evidence and qualify the actual new producer.
"""
from susie_dispatch_transition import pin,require

WORKFLOW='src/main/java/org/jlinalg/cli/SusieWorkflowCli.java'
BEFORE=dict(bytes=15709,sha256='73beae64fd966c7a572f584c9df1e308f97bf3b3a40cd6c71c298cab1c44d6e2')
ARCHIVE='docs/literature/susie-dispatch-checks/previous-workflow-control.java'
OLD_OPEN=b'                control(raw,true);Path prior=resolve(source.getParent(),text(raw,"analysis"));verify(prior);var request=read(prior.resolve("request.json"));\n'
NEW_OPEN=(b'                control(raw,true);Path prior=resolve(source.getParent(),text(raw,"analysis"));verify(prior);\n'
          b'                Path destination=resolve(source.getParent(),text(raw,"out"));replayOutput(prior,destination);var request=read(prior.resolve("request.json"));\n')
OLD_OUT=b'                request.put("out",resolve(source.getParent(),text(raw,"out")).toString());\n'
NEW_OUT=b'                request.put("out",destination.toString());\n'
ANCHOR=b'    private static Path newOutput(Path root)throws IOException {'
GUARD=b'''    private static void replayOutput(Path prior,Path destination)throws IOException {
        Path original=prior.toRealPath(),existing=destination.toAbsolutePath().normalize();var missing=new ArrayDeque<Path>();
        while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)) {
            if(existing.getParent()==null)throw new IOException("Cannot resolve REPLAY output; choose a NEW directory outside the original analysis");
            missing.addFirst(existing.getFileName());existing=existing.getParent();
        }
        Path actual=existing.toRealPath();for(Path name:missing)actual=actual.resolve(name);
        if(actual.normalize().startsWith(original))throw new IOException("REPLAY output must be outside the original analysis; preserve the parent capture and choose a NEW sibling directory");
    }
'''


def check(before,after):
    require(pin(before)==BEFORE,'Previous workflow-control source differs from its original qualification')
    for part in [OLD_OPEN,OLD_OUT,ANCHOR]:require(before.count(part)==1,'Ambiguous reviewed replay-control source anchor')
    expected=before.replace(OLD_OPEN,NEW_OPEN,1).replace(OLD_OUT,NEW_OUT,1).replace(ANCHOR,GUARD+ANCHOR,1)
    require(after==expected,'Workflow control differs beyond the reviewed replay-output admission guard')
    return dict(status='PASS_REVIEWED_REPLAY_OUTPUT_ADMISSION_CONTROL_CHANGE',previous=pin(before),current=pin(after),
        current_producer_requires_separate_qualification=True,previous_fingerprint_refreshed=False,
        source_lookup_overlay=False,model_fitting_source_changed=False)
