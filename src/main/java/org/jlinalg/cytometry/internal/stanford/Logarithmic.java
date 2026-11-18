/*
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2009, 2011, 2012, The Board of Trustees of The Leland Stanford Junior University.
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the Leland Stanford Junior University nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Project changes: package relocation, line endings, and serialVersionUID only.
 */
package org.jlinalg.cytometry.internal.stanford;

/**
 * Logarithmic display transform.
 * 
 * Maps a data value onto the interval [0,1] such that:
 * <ul>
 * <li>data value T is mapped to 1</li>
 * <li>M decades of data are mapped into the interval
 * </ul>
 * 
 * @author Wayne A. Moore
 * @version 1.0
 */
public class Logarithmic
		extends Transform
{
	/**
	 * Formal parameter of the logarithmic scale as defined in the Gating-ML
	 * standard.
	 */
	public final double T, M;

	/**
	 * Actual parameter of the logarithmic scale as implemented
	 */
	public final double a, b;

	protected Logarithmic(double T, double M, int bins)
	{
		if (T <= 0)
			throw new TransformParameterException("T is not positive");
		if (M <= 0)
			throw new TransformParameterException("M is not positive");

		// standard parameters
		this.T = T;
		this.M = M;

		// actual parameters
		b = M * LN_10;
		a = T / Math.exp(b);
	}

	public Logarithmic(double T, double M)
	{
		this(T, M, 0);
	}

	public Logarithmic(double T)
	{
		this(T, DEFAULT_DECADES, 0);
	}

	@Override
	public double scale (double value)
	{
		return Math.log(value / a) / b;
	}

	@Override
	public double inverse (double scale)
	{
		return a * Math.exp(b * scale);
	}

	@Override
	protected double slope (double scale)
	{
		return a * b * Math.exp(b * scale);
	}

	@Override
	public double[] axisLabels ()
	{
		// smallest power of 10 in the display region
		double log10x = Math.ceil(Math.log(T) / LN_10 - M);
		// data value at that point
		double x = Math.exp(LN_10 * log10x);
		// number of labels
		int n;
		if (x > T)
		{
			x = T;
			n = 0;
		}
		else
			n = (int) (Math.floor(Math.log(T) / LN_10 - log10x));

		// fill in the axis labels
		double[] label = new double[n + 1];
		for (int i = 0; i <= n; ++i)
		{
			label[i] = x;
			x *= 10;
		}

		return label;
	}
}
