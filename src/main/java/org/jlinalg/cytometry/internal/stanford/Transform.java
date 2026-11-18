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
/**
 * 
 */
package org.jlinalg.cytometry.internal.stanford;

/**
 * Abstract display transform. A display transform maps a range of scaled and
 * compensated data values monotonically onto the interval [0,1]. They are
 * primarily for use in constructing data visualizations but also useful for
 * clustering and other applications.
 * 
 * @author Wayne A. Moore
 * @version 1.0
 */
public abstract class Transform
{
	/**
	 * Number of decades in default log or log like scale
	 */
	public static final double DEFAULT_DECADES = 4.5;

	/**
	 * Constant needed by log and log like scales
	 */
	protected static final double LN_10 = Math.log(10);

	/**
	 * Computes the scale position for a given data value
	 * 
	 * @param value
	 *          a scaled and compensated data value
	 * @return the double scale position
	 */
	abstract public double scale (final double value);

	/**
	 * Computes the data value mapped to a given position on the scale
	 * 
	 * @param scale
	 *          the position on the display scale
	 * @return the double data value mapped to the scale position
	 */
	abstract public double inverse (final double scale);

	/**
	 * A suggested list of data values suitable for use as labels on an axis for
	 * this transform. Each data value may be turned into a string to be located
	 * at the scale position of the data.
	 * 
	 * @return the array of data values
	 */
	abstract public double[] axisLabels ();

	/**
	 * Computes the slope of the inverse function at a scale value. Needed by the
	 * unit tests.
	 * 
	 * @param scale
	 * @return The slope of the inverse transform at the scale point
	 */
	abstract protected double slope (double scale);
}
