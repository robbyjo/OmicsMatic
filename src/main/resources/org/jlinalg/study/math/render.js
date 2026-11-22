/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
(() => {
    let failures = 0;
    document.querySelectorAll('.manuscript-math[data-tex]').forEach(element => {
        const source = element.dataset.tex;
        const original = element.textContent;
        try {
            if (source.length > 20000) throw new Error('Equation exceeds the rendering size limit.');
            // External resources, hyperlinks and author-supplied HTML have no place in an equation.
            if (/\\(?:href|url|includegraphics|html\w*)\b/.test(source))
                throw new Error('Use mathematical notation without links or HTML.');
            katex.render(source, element, {
                displayMode: element.dataset.display === 'true', output: 'mathml',
                throwOnError: true, strict: 'error', trust: false,
                maxExpand: 1000, maxSize: 20, macros: {}
            });
            element.dataset.mathStatus = 'complete';
        } catch (error) {
            failures++;
            element.textContent = original + ' [equation could not be typeset]';
            element.dataset.mathStatus = 'error';
            element.title = String(error.message || error);
        }
    });
    document.documentElement.dataset.manuscriptMath = failures ? 'error' : 'complete';
})();
