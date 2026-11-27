"""Exact successor edits; previous published source and qualification stay immutable."""

FOLDER = 'docs/literature/rfmix-range-validation'
ATTRIBUTE_APPEND = ('docs/literature/rfmix-range-validation/** -text whitespace=cr-at-eol\n'
                    'tools/literature/rfmix_range_derivation.py -text whitespace=cr-at-eol\n'
                    'tools/literature/verify_rfmix_range_workflows.py -text whitespace=cr-at-eol\n')
FORWARD = ("    successor = root / 'docs/literature/rfmix-range-validation/QUALIFICATION.json'\n"
           "    if successor.is_file():\n"
           "        from verify_rfmix_range_workflows import source_record as successor_record\n"
           "        return successor_record(root)['predecessor']\n")
REPLACEMENTS = {
    'src/main/java/org/jlinalg/cli/RfmixRequest.java': (
        'if(settings.get("range")!=null){var range=object(settings.get("range"));args.add("--analyze-range="+number(range.get("start_mbp"))+"-"+number(range.get("end_mbp")));}',
        'if(settings.get("range")!=null)args.add(RfmixRange.argument(object(settings.get("range"))));'),
    'src/main/java/org/jlinalg/cli/RfmixBundle.java': (
        'return map("initial_printed_log_likelihood",initial,"warnings",sourceWarnings,',
        'return map("range_selection",RfmixRange.auditSourceNotice(log,spec.settings().get("range")!=null,spec.range()),"initial_printed_log_likelihood",initial,"warnings",sourceWarnings,'),
    'tools/literature/verify_rfmix_workflows.py': ('def source_record(root):\n', 'def source_record(root):\n' + FORWARD),
}


def derive(name, raw):
    if name == '.gitattributes':
        if not raw.endswith(b'\n') or ATTRIBUTE_APPEND.encode() in raw:
            raise ValueError('Published predecessor attributes differ')
        return raw + ATTRIBUTE_APPEND.encode()
    before, after = REPLACEMENTS[name]
    if raw.count(before.encode()) != 1:
        raise ValueError('Published predecessor source differs: ' + name)
    return raw.replace(before.encode(), after.encode(), 1)
