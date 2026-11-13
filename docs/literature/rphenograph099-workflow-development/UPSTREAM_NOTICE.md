# Rphenograph source attribution

The RphenographJaccard099 Java class adapts the coefficient calculation in Hao Chen's Rphenograph 0.99.1 `src/jaccard_coeff.cpp`, selected commit `0298487f0ee13aac55eb77d19992f6bd878ba2fc` of https://github.com/JinmiaoChenLab/Rphenograph. The package declares Artistic-2.0. The supplied license text is from the SPDX license-list-data v3.26.0 collection and corresponds to https://opensource.org/license/Artistic-2.0.

The Java adaptation has a distinct class name and validates integer indices, complete shapes and explicit capacities. It computes unique neighbor intersections using sorted Java arrays, retains the original ordered events, half weights and trailing zero rows, and performs no RANN search or Louvain clustering. JLinAlg contributors' Java integration is distributed under the project's GPL-3.0-or-later terms. The original Rphenograph runtime is installed separately from unmodified source and can coexist with this class; it is not bundled in the Java artifact.

P040's reported Rphenograph version 0.1.1 remains unresolved. robustSingleCell 0.1.1 contains corresponding functions and coefficient code, but that does not establish the paper's package identity.
