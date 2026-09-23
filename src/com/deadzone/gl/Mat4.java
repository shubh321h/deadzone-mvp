package com.deadzone.gl;

/** Column-major 4x4 float matrices, OpenGL conventions. */
public final class Mat4 {
    private Mat4() {}

    public static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    public static void mul(float[] out, float[] a, float[] b) {
        float[] t = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float s = 0f;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                t[c * 4 + r] = s;
            }
        }
        System.arraycopy(t, 0, out, 0, 16);
    }

    public static void persp(float[] out, float fovyDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(fovyDeg * Math.PI / 360.0));
        float nf = 1f / (near - far);
        java.util.Arrays.fill(out, 0f);
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) * nf;
        out[11] = -1f;
        out[14] = 2f * far * near * nf;
    }

    public static void trans(float[] out, float x, float y, float z) {
        java.util.Arrays.fill(out, 0f);
        out[0] = out[5] = out[10] = out[15] = 1f;
        out[12] = x; out[13] = y; out[14] = z;
    }

    public static void rotY(float[] out, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        java.util.Arrays.fill(out, 0f);
        out[0] = c; out[2] = -s; out[5] = 1f; out[8] = s; out[10] = c; out[15] = 1f;
    }

    public static void rotX(float[] out, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        java.util.Arrays.fill(out, 0f);
        out[0] = 1f; out[5] = c; out[6] = s; out[9] = -s; out[10] = c; out[15] = 1f;
    }

    public static void scale(float[] out, float sx, float sy, float sz) {
        java.util.Arrays.fill(out, 0f);
        out[0] = sx; out[5] = sy; out[10] = sz; out[15] = 1f;
    }

    /** out = T(p) * Ry(yaw) * Rx(rx) * S(s) */
    public static void composePose(float[] out, float px, float py, float pz,
                                   float yaw, float rx, float sx, float sy, float sz) {
        float[] t1 = new float[16], t2 = new float[16], t3 = new float[16];
        trans(t1, px, py, pz);
        rotY(t2, yaw);
        mul(t3, t1, t2);
        rotX(t1, rx);
        mul(t2, t3, t1);
        scale(t1, sx, sy, sz);
        mul(out, t2, t1);
    }

    /** out = view * proj (view = Rx(-pitch)*Ry(-yaw)*T(-eye)) */
    public static void viewProj(float[] out, float eyeX, float eyeY, float eyeZ,
                                float yaw, float pitch, float fovDeg, float aspect) {
        float[] t1 = new float[16], t2 = new float[16], t3 = new float[16], proj = new float[16];
        persp(proj, fovDeg, aspect, 0.15f, 400f);
        trans(t1, -eyeX, -eyeY, -eyeZ);
        rotY(t2, -yaw);
        mul(t3, t2, t1);
        rotX(t1, -pitch);
        mul(t2, t1, t3);
        mul(out, proj, t2);
    }

    public static void lookAt(float[] out, Vec3 eye, Vec3 center, Vec3 up) {
        Vec3 f = center.clone().sub(eye).norm();
        Vec3 s = Vec3.cross(f, up).norm();
        Vec3 u = Vec3.cross(s, f);
        out[0] = s.x; out[1] = u.x; out[2] = -f.x; out[3] = 0;
        out[4] = s.y; out[5] = u.y; out[6] = -f.y; out[7] = 0;
        out[8] = s.z; out[9] = u.z; out[10] = -f.z; out[11] = 0;
        out[12] = -s.dot(eye); out[13] = -u.dot(eye); out[14] = f.dot(eye); out[15] = 1;
    }

    public static void forwardY(float[] out, float yaw, float pitch) {
        // world-space forward (view direction)
        float cyp = (float) Math.cos(pitch), sph = (float) Math.sin(pitch);
        float cyy = (float) Math.cos(yaw), syy = (float) Math.sin(yaw);
        out[0] = -syy * cyp;
        out[1] = sph;
        out[2] = -cyy * cyp;
    }
}
