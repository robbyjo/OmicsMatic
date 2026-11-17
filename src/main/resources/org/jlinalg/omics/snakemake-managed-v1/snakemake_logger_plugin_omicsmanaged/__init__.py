# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Developer recorder enabling original-deployer policy in every instrumented engine process."""
from dataclasses import dataclass
from snakemake_interface_logger_plugins.settings import LogHandlerSettingsBase
from omicsmatic_logger_base import LogHandler as Recorder
from omicsmatic_policy_runtime import bootstrap_policy

@dataclass
class LogHandlerSettings(LogHandlerSettingsBase):
    def __post_init__(self):
        # The original CLI constructs selected plugin settings even in
        # subprocess mode, where it deliberately skips plugin log handlers.
        bootstrap_policy()

class LogHandler(Recorder):
    pass
