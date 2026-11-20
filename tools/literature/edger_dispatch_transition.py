"""Exact two-route edgeR dispatcher inverse; original component engines are unchanged."""
import hashlib
DISPATCH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BASE='33db0648621a359e03fb6da3e048c409b45588c4'
BEFORE={'bytes': 54982, 'sha256': 'a5e2e4f16c574e26458b911fa9cb1868c1e789fd1e8ec8fdb697fd58568b8c7e'}
ROUTE=b'            if (arguments.length > 0 && arguments[0].equals("edger-tools"))\n                return EdgeRWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n            if (arguments.length > 0 && arguments[0].equals("edger-normalize"))\n                return EdgeRNormalizationCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
HELP=b'              java -jar omicsmatic-<version>.jar edger-tools --help\n              java -jar omicsmatic-<version>.jar edger-normalize --help\n'
ANCHOR=b'            if (arguments.length > 0 && arguments[0].equals("deseq2-tools"))\n'
HELP_ANCHOR=b'              java -jar omicsmatic-<version>.jar deseq2-tools --help\n'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def require(value,message):
    if not value:raise ValueError(message)
def dispatcher_before(after):
    require(after.count(ROUTE)==after.count(HELP)==1,'Missing or duplicate qualified edgeR routes/help')
    before=after.replace(ROUTE,b'',1).replace(HELP,b'',1)
    require(pin(before)==BEFORE,'Dispatcher differs beyond the six additive edgeR lines')
    require(before.count(ANCHOR)==before.count(HELP_ANCHOR)==1,'Ambiguous original DESeq2 anchors')
    require(after==before.replace(ANCHOR,ROUTE+ANCHOR,1).replace(HELP_ANCHOR,HELP+HELP_ANCHOR,1),'EdgeR routes/help inserted outside the reviewed anchors')
    return before
