# Confirmed Python hash-seed option defect

**The first-generation `CellPhoneDbWorkflowCli` does not honor its `python_hash_seed` option.** Its supervisor sets `PYTHONHASHSEED` but launches Python with `-I`, which implies ignoring Python environment variables ([official Python documentation](https://docs.python.org/3.11/using/cmdline.html#cmdoption-I)). Six actual launches of the same pinned Python confirm the conflict. A separately validated runner correction is required; it is not implemented in this checkpoint.

**Required action:** skip or postpone work that requires this option to control Python string/set hash ordering. Do not describe existing runs as Python-hash-seeded on the strength of that request field. Preserve their original outputs and provenance. Do not edit the frozen runtime, supervisor or evidence hashes to make an old receipt appear corrected.

NumPy global/permutation seeds are separate: the CellPhoneDB worker sets those through the original numerical API. The observed original output and RNG comparisons remain retained evidence for their specific controls; they do not prove that Python hashing was seeded. Original full-method subsampling still reseeds NumPy from entropy and is a further, separate source of nondeterminism.

The Spyrrow component reference launcher uses a sanitized environment with `-B -P -s`, excluding unsafe startup paths and user site packages while honoring the explicit hash seed. Its collector asserts the effective interpreter flags and seed0 profile before any reference computation. Both seed0 and seed123 were checked across repeated non-isolated launches. `-I` launches report environment ignored and randomized hashing even when the environment says seed0.

This finding is scoped to the inspected launch behavior and the exposed CellPhoneDB option. Other Python launchers need their own audit; no blanket claim about their numerical results or seed options is made. The correction remains active work under the full250-paper project goal.
