The eigenvalue-only Java adaptation follows the symmetric Householder reduction
and QL parts of JAMA 1.0.3 `EigenvalueDecomposition.java`, a cooperative MathWorks
and NIST reference released to the public domain. NIST's original distribution
and notice are linked at <https://math.nist.gov/javanumerics/jama/>. The selected
original files and archive identity are retained in `JAMA-ORIGINAL-SOURCE.zip`.

The adaptation omits eigenvector accumulation, adds whole-input scaling and
finite checks, and bounds QL iterations. No JAMA binary dependency is required.
The new adaptation is distributed under the project's GPL terms.
