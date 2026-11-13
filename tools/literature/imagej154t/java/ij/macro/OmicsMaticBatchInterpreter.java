/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package ij.macro;

import ij.Macro;

/** Retain original interpreter error state and text instead of showing a modal error dialog. */
public final class OmicsMaticBatchInterpreter extends Interpreter {
    private String diagnostic;
    @Override void showError(String title,String message,String[] variables){
        diagnostic=title+"\n"+message;throw new RuntimeException(Macro.MACRO_CANCELED);
    }
    public String diagnostic(){return diagnostic;}
}
