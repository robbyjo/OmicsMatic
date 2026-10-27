/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.protein;

import java.util.List;
import java.util.Objects;
import jdistlib.accelerator.ComputeBackend;

/**
 * Euclidean measurements and proper rigid fitting of explicitly paired coordinates.
 * Coordinates must use one common unit. Angles are degrees. The fit uses Horn's
 * quaternion solution (JOSA A 4:629, 1987), with no scale, reflection, sequence
 * alignment, atom matching, outlier rejection, symmetry or periodic images.
 * This arithmetic is independent of PyMOL's align/super correspondence algorithms.
 */
public final class MolecularGeometry {
    private MolecularGeometry() { }

    /** Finite Cartesian coordinates, bounded to absolute values of one billion. */
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) > 1e9)
                throw new IllegalArgumentException("Coordinates must be finite and have absolute value <=1e9 in the declared common unit");
        }
    }

    /** Handling of fits whose best rotation is not uniquely determined. */
    public enum Degeneracy { ERROR, ALLOW_NONUNIQUE }

    /**
     * Rotation maps mobile column vectors into target coordinates; translation is
     * then added. Residuals and RMSD use centred coordinates to reduce cancellation.
     * A nonunique solution is one optimal rotation, not a uniquely inferred pose.
     */
    public record Fit(int pairs, double initialRmsd, double rmsd, double maximumResidual,
            double[][] rotation, double[] translation, double[] residuals,
            boolean rotationUnique, double relativeEigenvalueGap) {
        public Fit {
            rotation = copy(rotation);
            translation = translation.clone();
            residuals = residuals.clone();
        }
        @Override public double[][] rotation() { return copy(rotation); }
        @Override public double[] translation() { return translation.clone(); }
        @Override public double[] residuals() { return residuals.clone(); }

        /** Apply the proper rotation and translation without changing the input. */
        public Point apply(Point point) {
            Objects.requireNonNull(point);
            double[] p = {point.x(), point.y(), point.z()};
            return new Point(dot(rotation[0], p) + translation[0],
                    dot(rotation[1], p) + translation[1], dot(rotation[2], p) + translation[2]);
        }
    }

    /** Distance in the input coordinate unit. */
    public static double distance(Point a, Point b) {
        return norm(difference(Objects.requireNonNull(a), Objects.requireNonNull(b)));
    }

    /** Angle ABC, in [0,180] degrees; zero-length arms are undefined and rejected. */
    public static double angle(Point a, Point b, Point c) {
        double[] u = unit(difference(a, b)), v = unit(difference(c, b));
        return Math.toDegrees(Math.atan2(norm(cross(u, v)), dot(u, v)));
    }

    /** Signed ABCD torsion in [-180,180] degrees; collinear/zero bonds are rejected. */
    public static double dihedral(Point a, Point b, Point c, Point d) {
        double[] ab = unit(difference(b, a)), bc = unit(difference(c, b)), cd = unit(difference(d, c));
        double[] left = unit(cross(ab, bc)), right = unit(cross(bc, cd));
        return Math.toDegrees(Math.atan2(dot(cross(left, right), bc), dot(left, right)));
    }

    /**
     * Unweighted least-squares proper rotation/translation, preserving every pair.
     * At least three and at most one million already matched points are required.
     * The supplied backend diagonalizes a symmetric 4x4 Horn matrix. Degeneracy is
     * determined by a relative largest-eigenvalue gap of at most 1e-12.
     */
    public static Fit fit(List<Point> mobile, List<Point> target, Degeneracy degeneracy,
            ComputeBackend backend) {
        Objects.requireNonNull(mobile); Objects.requireNonNull(target);
        Objects.requireNonNull(degeneracy); Objects.requireNonNull(backend);
        int n = mobile.size();
        if (n < 3 || n > 1_000_000 || target.size() != n)
            throw new IllegalArgumentException("Rigid fit needs 3..1000000 explicit coordinate pairs with equal counts");
        double[] mc = centre(mobile), tc = centre(target);
        double scale = 0;
        for (int i = 0; i < n; i++) {
            double[] m = centred(mobile.get(i), mc), t = centred(target.get(i), tc);
            for (int k = 0; k < 3; k++) scale = Math.max(scale, Math.max(Math.abs(m[k]), Math.abs(t[k])));
        }
        double[] covariance = new double[9], correction = new double[9];
        if (scale > 0) for (int i = 0; i < n; i++) {
            double[] m = centred(mobile.get(i), mc), t = centred(target.get(i), tc);
            for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++)
                add(covariance, correction, 3 * a + b, (m[a] / scale) * (t[b] / scale) / n);
        }
        double xx=covariance[0], xy=covariance[1], xz=covariance[2];
        double yx=covariance[3], yy=covariance[4], yz=covariance[5];
        double zx=covariance[6], zy=covariance[7], zz=covariance[8];
        double[] horn = {xx+yy+zz, yz-zy, zx-xz, xy-yx,
                yz-zy, xx-yy-zz, xy+yx, zx+xz,
                zx-xz, xy+yx, -xx+yy-zz, yz+zy,
                xy-yx, zx+xz, yz+zy, -xx-yy+zz};
        var eigen = backend.dsyev(horn.clone(), 4);
        double[] values=eigen.eigenvalues(), vectors=eigen.eigenvectors();
        if (values.length != 4 || vectors.length != 16)
            throw new IllegalArgumentException("Backend returned an invalid 4x4 eigendecomposition");
        int first=0, second=1;
        if (values[second] > values[first]) { int old=first; first=second; second=old; }
        for (int i=2; i<4; i++) {
            if (values[i] > values[first]) { second=first; first=i; }
            else if (values[i] > values[second]) second=i;
        }
        double largest=0;
        for (double value:values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite backend eigenvalue");
            largest=Math.max(largest,Math.abs(value));
        }
        double gap=largest==0 ? 0 : (values[first]-values[second])/largest;
        boolean unique=gap>1e-12;
        if (!unique && degeneracy==Degeneracy.ERROR)
            throw new IllegalArgumentException("Best rigid rotation is nonunique or numerically unresolved; provide noncollinear/informative pairs or explicitly allow a nonunique fit");
        double[] q = new double[4];
        double qnorm=0;
        for (int i=0; i<4; i++) { q[i]=vectors[i*4+first]; qnorm=Math.hypot(qnorm,q[i]); }
        if (!(qnorm>0) || !Double.isFinite(qnorm)) throw new IllegalArgumentException("Invalid backend quaternion");
        for (int i=0; i<4; i++) q[i]/=qnorm;
        for (int i=0; i<4; i++) {
            double residual=-values[first]*q[i];
            for (int j=0; j<4; j++) residual+=horn[i*4+j]*q[j];
            if (Math.abs(residual)>1e-10*Math.max(largest,1e-300))
                throw new IllegalArgumentException("Backend quaternion does not satisfy the Horn eigenproblem");
        }
        double w=q[0], x=q[1], y=q[2], z=q[3];
        double[][] rotation={{w*w+x*x-y*y-z*z,2*(x*y-w*z),2*(x*z+w*y)},
                {2*(x*y+w*z),w*w-x*x+y*y-z*z,2*(y*z-w*x)},
                {2*(x*z-w*y),2*(y*z+w*x),w*w-x*x-y*y+z*z}};
        double[] translation=new double[3];
        for (int k=0; k<3; k++) translation[k]=tc[k]-dot(rotation[k],mc);
        double[] residuals=new double[n], totals=new double[2], errors=new double[2];
        double maximum=0;
        for (int i=0; i<n; i++) {
            double[] m=centred(mobile.get(i),mc), t=centred(target.get(i),tc), delta=new double[3];
            for (int k=0; k<3; k++) delta[k]=dot(rotation[k],m)-t[k];
            residuals[i]=norm(delta); maximum=Math.max(maximum,residuals[i]);
            add(totals,errors,0,residuals[i]*residuals[i]/n);
            double initial=distance(mobile.get(i),target.get(i));
            add(totals,errors,1,initial*initial/n);
        }
        return new Fit(n,Math.sqrt(totals[1]),Math.sqrt(totals[0]),maximum,rotation,translation,
                residuals,unique,gap);
    }

    private static double[] centre(List<Point> points) {
        Point anchor=Objects.requireNonNull(points.get(0));
        double[] sum=new double[3], corrections=new double[3];
        for (Point point:points) {
            double[] delta=difference(Objects.requireNonNull(point),anchor);
            for (int k=0; k<3; k++) add(sum,corrections,k,delta[k]/points.size());
        }
        return new double[]{anchor.x()+sum[0],anchor.y()+sum[1],anchor.z()+sum[2]};
    }
    private static double[] centred(Point p,double[] c) { return new double[]{p.x()-c[0],p.y()-c[1],p.z()-c[2]}; }
    private static double[] difference(Point a,Point b) { return new double[]{a.x()-b.x(),a.y()-b.y(),a.z()-b.z()}; }
    private static double norm(double[] p) { return Math.hypot(Math.hypot(p[0],p[1]),p[2]); }
    private static double[] unit(double[] p) {
        double value=norm(p);
        if (!(value>0)) throw new IllegalArgumentException("Zero-length or collinear geometry has an undefined angle/torsion");
        return new double[]{p[0]/value,p[1]/value,p[2]/value};
    }
    private static double dot(double[] a,double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static double[] cross(double[] a,double[] b) { return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]}; }
    private static void add(double[] sum,double[] correction,int i,double value) {
        double adjusted=value-correction[i], next=sum[i]+adjusted;
        correction[i]=(next-sum[i])-adjusted; sum[i]=next;
    }
    private static double[][] copy(double[][] values) {
        double[][] result=new double[values.length][];
        for(int i=0;i<values.length;i++)result[i]=values[i].clone();
        return result;
    }
}
