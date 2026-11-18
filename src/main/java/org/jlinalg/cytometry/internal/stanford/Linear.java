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
 * Linear display transform.
 * 
 * Maps a data value onto the interval [0,1] linearly such that:
 * <ul>
 * <li>data value T is mapped to 1</li>
 * <li>data value -A is mapped to 0</li>
 * </ul>
 * 
 * @author Wayne A. Moore
 * @version 1.0
 */

public class Linear
		extends Transform
{
	/**
	 * Formal parameter of the linear scale as defined in the Gating-ML standard.
	 */
	public final double T, A;

	/**
	 * Actual parameter of the linear scale as implemented
	 */
	public final double a, b;

	protected Linear(double T, double A, int bins)
	{
		if (T <= 0)
			throw new TransformParameterException("T is not positive");
		if (T <= A)
			throw new TransformParameterException("T is not greater than A");
		if (A < 0)
			throw new TransformParameterException("A is negative");

		// standard parameters
		this.T = T;
		this.A = A;

		// actual parameters
		a = (T + A);
		b = -A;
	}

	public Linear(double T, double A)
	{
		this(T, A, 0);
	}

	public Linear(double T)
	{
		this(T, 0, 0);
	}

	@Override
	public double scale (double value)
	{
		return (value - b) / a;
	}

	@Override
	public double inverse (double scale)
	{
		return a * scale + b;
	}

	@Override
	protected double slope (double scale)
	{
		return a;
	}

	@Override
	public double[] axisLabels ()
	{
		// total range of the data
		double r = T - A;
		// largest power of 10 in the range
		double log10x = Math.floor(Math.log(r) / LN_10);
		double x = Math.exp(log10x * LN_10);

		// chose an interval x that gives at least three but not more than six
		// labels
		// suitable for use by monkeys, i.e., pretty combinations of 2, 5 and 10
		double n = r / x;
		if (n <= 1.2)
			x /= 5;
		else if (n <= 1.5)
			x /= 4;
		else if (n <= 3)
			x /= 2;
		else if (n >= 7)
			x *= 2;

		int np = (int) Math.floor(T / x);
		int nn = (int) Math.floor(A / x);

		// fill in the axis labels
		double[] label = new double[nn + np + 1];
		label[nn] = 0;
		for (int i = 1; i <= nn; ++i)
		{
			label[nn - i] = -x * i;
			label[nn + i] = x * i;
		}
		for (int i = nn + 1; i <= np; ++i)
		{
			label[nn + i] = x * i;
		}

		return label;
	}
}
