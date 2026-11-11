# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive a JDK-only auditor without changing any original matrix/cbind assertion."""
from setup_deseq2_reference import ROOT

REPORTER='''    private static String json(Map<String,Object> report) {
        var fields = new ArrayList<String>();
        for (var entry : report.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z_]+")) throw new IllegalArgumentException("Unexpected report key");
            Object value = entry.getValue(); String text;
            if (value instanceof String string) {
                if (!string.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Unexpected report string");
                text = "\\\"" + string + "\\\"";
            } else if (value instanceof Integer || value instanceof Long || value instanceof Boolean) text = value.toString();
            else throw new IllegalArgumentException("Unexpected report type");
            fields.add("\\\"" + entry.getKey() + "\\\":" + text);
        }
        return "{" + String.join(",", fields) + "}";
    }

'''


def generated():
    source=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadMatrixAudit.java';text=source.read_text(encoding='utf8');assert text.count('RsubreadMatrixAudit')==2 and text.count('RegenieRuntime.json(report)')==2;text=text.replace('RsubreadMatrixAudit','RsubreadStandaloneMatrixAudit').replace('RegenieRuntime.json(report)','json(report)');marker='    public static void main(String[] args) throws Exception {';assert text.count(marker)==1;return text.replace(marker,REPORTER+marker).encode()


def verify():assert (ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadStandaloneMatrixAudit.java').read_bytes()==generated()


if __name__=='__main__':
    path=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadStandaloneMatrixAudit.java';raw=generated()
    if path.exists():assert path.read_bytes()==raw
    else:path.write_bytes(raw)
    verify();print('Prepared independent JDK-only reporter; all original scientific assertions remain exact')
