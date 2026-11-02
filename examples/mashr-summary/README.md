# Verified native mash summaries

Follow [the command guide](../../docs/literature/MASHR_SUMMARY_COMMAND.md).
`composite-request` matches the packaged synthetic native fit example with
feature `0001` and conditions `A`, `B`. Its nominal P values are synthetic
controls supplied independently of the posterior LFSR. They are not estimates
for another study. Replace them with the actual, completely aligned nominal P
matrix for real data; never copy these values into a real analysis.

Copy only the four TSV files into a new request directory. The `all` group has
two conditions and can pass the nominal count gate. The `first` group has one
condition and cannot pass the declared minimum of two. This distinction is
retained in the output rather than changing either group or the gate.
