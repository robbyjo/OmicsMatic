# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep terminal admission, warnings and file inventories consistent after recording errors."""

LIMITS = dict(maximum_files=1000000, maximum_file_bytes=137438953472,
              maximum_workspace_bytes=137438953472)
EXCLUDED = ['MANIFEST.json', 'analysis-status.json']
RECORDING_ACTION = ('No complete result is admitted after a recording failure. '
                    'Preserve the complete console and every available original/partial file. '
                    'Resolve the named filesystem error, provide writable regular-file '
                    'destinations in a NEW output directory, and rerun. Do not delete '
                    'previous evidence or change saved hashes to obtain a pass.')


def finalize(out, *, code, cause, admitted, counts, counts_complete, action,
             fields, tree, save):
    """Return the final exit code/cause; status-file failure can never emit PASS."""
    manifest_complete = False

    def fail(label, error):
        nonlocal code, cause, admitted
        previous = cause
        code = 2
        admitted = False
        cause = 'FAIL2_' + label + ': ' + repr(error) + '; previous outcome: ' + previous
        print(cause + '\n' + RECORDING_ACTION + '\n' + action, flush=True)

    def warning():
        text = ('# ' + cause + '\n\nComplete result admitted: ' + str(admitted)
                + '. R handler warnings/messages/errors: ' + str(counts)
                + '. R handler totals complete: ' + str(counts_complete)
                + '. **Raw/native/Python console diagnostic totals are unavailable, '
                'never inferred to be zero. Review every raw console and original '
                'condition, including the unresolved native stack diagnostic.**\n\n'
                + (RECORDING_ACTION + '\n\n' if code == 2 else '') + action + '\n')
        try:
            (out / 'WARNINGS.md').write_text(text, encoding='utf-8', newline='\n')
        except BaseException as error:
            fail('WARNING_RECORDING_UNAVAILABLE', error)

    def manifest():
        nonlocal manifest_complete
        manifest_complete = False
        try:
            files = tree(out, LIMITS, True, retain_links=True)
            for name in EXCLUDED:
                files.pop(name, None)
            save(out / 'MANIFEST.json', dict(files=files, excluded_final_records=EXCLUDED))
            manifest_complete = True
        except BaseException as error:
            fail('COMPLETE_INVENTORY_UNAVAILABLE', error)

    if code != 0:
        admitted = False
        if cause.startswith('PASS'):
            fail('INCONSISTENT_PRIOR_OUTCOME', ValueError('Nonzero exit forbids complete admission'))
    warning()
    manifest()
    if not manifest_complete:
        warning()
    status = dict(fields, status=cause, exit_code=code, result_admitted=admitted,
                  complete_manifest=manifest_complete, R_handler_warnings=counts[0],
                  R_handler_messages=counts[1], R_handler_errors=counts[2],
                  R_handler_condition_counts_complete=counts_complete,
                  raw_console_diagnostic_counts_complete=False,
                  warning_review_required=True, required_action=action)
    try:
        save(out / 'analysis-status.json', status)
    except BaseException as error:
        fail('STATUS_RECORDING_UNAVAILABLE', error)
        # A separate failure record is best effort. It never grants admission or
        # pretends that the required final status file was saved successfully.
        try:
            save(out / 'FINALIZATION_ERROR.json', dict(status=cause, exit_code=2,
                 result_admitted=False, required_status_recorded=False,
                 required_action=RECORDING_ACTION + '\n' + action))
        except BaseException as recording:
            fail('FALLBACK_RECORDING_UNAVAILABLE', recording)
        warning()
        # WARNINGS.md changed after the first inventory. Rebuild its hash rather
        # than leaving a supposedly complete manifest containing a stale PASS.
        manifest()
        if not manifest_complete:
            warning()
    return code, cause
