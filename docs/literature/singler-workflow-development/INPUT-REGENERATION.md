# Exact synthetic request regeneration

The first frozen replay stopped before analysis because the AnnoyParam object's
`dir` slot contained a different R temporary directory. The complete matrices,
labels, RNG and every other request component compared equal. This was a real
request difference, not a passing byte comparison. Both complete requests and
the failed replay remain retained.

`MakeControls.R` now accepts an optional fourth argument pointing to the captured
Annoy request. It reads only its caller directory and supplies that literal path
explicitly to the original `BiocNeighbors::AnnoyParam` constructor. The fixed
11-tree setting and every other request component are generated independently
from the project fixture source and fixed RNG. The default three-argument
fixture generation still uses the constructor's default temporary directory.
The selected BiocNeighbors in the 2.8.0 profile has no AnnoyParam `dir` slot;
that profile uses its unchanged original constructor without that argument.
An intermediate replay's assumption that all releases had this slot failed
during fixture generation, before analysis. That failure and all partial
regenerated requests are retained. No directory field is invented for 2.8.0.

Replay compares every complete regenerated RDS byte for byte, including the
entire constructed AnnoyParam object and all RNG state. No field, directory,
environment or identifier is removed or rewritten after generation. Analysis
uses the unchanged captured request; upstream methods are not patched. This
caller-directory choice is synthetic fixture metadata, not an author setting.
