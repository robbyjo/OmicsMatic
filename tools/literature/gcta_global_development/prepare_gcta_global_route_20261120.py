from pathlib import Path
import hashlib,json
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';B=T/'build/gcta-global-1';O=B/'dispatcher-edit-1';O.mkdir(parents=True,exist_ok=False)
name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';path=T/name;before=path.read_bytes()
anchor=b'            if (arguments.length > 0 && arguments[0].equals("broad-gsea-tools"))\n'
help_anchor=b'              java -jar omicsmatic-<version>.jar broad-gsea-tools --help\n'
route=b'            if (arguments.length > 0 && arguments[0].equals("gcta-tools"))\n                return GctaWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
help_line=b'              java -jar omicsmatic-<version>.jar gcta-tools --help\n'
assert before.count(anchor)==before.count(help_anchor)==1 and b'"gcta-tools"' not in before
after=before.replace(anchor,route+anchor,1).replace(help_anchor,help_line+help_anchor,1)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
for label,raw in [('before.java',before),('after.java',after)]: (O/label).write_bytes(raw)
path.write_bytes(after)
(O/'CHANGE.json').write_text(json.dumps(dict(base='cfeee186d5068a2af3be33ef9fb69508feefdd17',path=name,before=pin(before),after=pin(after),additive_lines=3,scientific_engines_changed=False),indent=2)+'\n',encoding='utf-8',newline='\n')
module='''"""Exact GCTA route/help inverse. No scientific source or historical hash waiver."""
import hashlib
DISPATCH=%r
BASE='cfeee186d5068a2af3be33ef9fb69508feefdd17'
BEFORE=%r
AFTER=%r
ROUTE=%r
HELP=%r
ANCHOR=%r
HELP_ANCHOR=%r
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,message):
    if not ok:raise ValueError(message)
def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing/duplicate GCTA global route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE and pin(after)==AFTER,'Dispatcher differs beyond the three GCTA lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original Broad GSEA anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'GCTA route/help moved outside reviewed anchors')
    return before
'''%(name,pin(before),pin(after),route,help_line,anchor,help_anchor)
(T/'tools/literature/gcta_dispatch_transition.py').write_text(module,encoding='utf-8',newline='\n')
print('Added only three GCTA dispatcher/help lines',pin(after),flush=True)
