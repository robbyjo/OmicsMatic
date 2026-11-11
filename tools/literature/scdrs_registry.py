# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact scDRS singular/plural name extension, retaining the frozen base registry."""
import copy

BASE_PATTERN = r'(?<![A-Za-z0-9_])(?:scDRS)(?![A-Za-z0-9_])'
PATTERN = r'(?<![A-Za-z0-9_])(?:scDRSs?)(?![A-Za-z0-9_])'


def entry(original):
    assert original['id'] == 'scdrs' and original['pattern'] in [BASE_PATTERN, PATTERN]
    result = copy.deepcopy(original)
    result['pattern'] = PATTERN
    return result
