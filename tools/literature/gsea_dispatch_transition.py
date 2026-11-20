"""Exact native Broad GSEA global route/help inverse; scientific components stay frozen."""
import hashlib
DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE='8d09589e78ff758cd7b648a92357b8cebcd7748e'
BEFORE={'bytes': 55519, 'sha256': '816e8058a4b85d55bda3c181c731511261294235fd4175b4f7cb33589713dc15'}
ROUTE=b'            if (arguments.length > 0 && arguments[0].equals("broad-gsea-tools"))\n                return BroadGseaWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar broad-gsea-tools --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("edger-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar edger-tools --help\n'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(ok,message):
    if not ok:raise ValueError(message)
def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing/duplicate qualified Broad GSEA route/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE,'Dispatcher differs beyond the three additive Broad GSEA lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original edgeR anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'Broad GSEA lines moved outside their reviewed anchors')
    return before
