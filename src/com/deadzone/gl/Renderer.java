package com.deadzone.gl;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.deadzone.world.World;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * Minimal GLES3 renderer: one solid program (per-vertex color + distance fog),
 * one line program (rain, tracers, rings). Dynamic per-frame vertex buffer for
 * entities/FX; static buffer uploaded once per level. Batching everything into
 * 2-3 draw calls keeps mid-range phones comfortable.
 */
public class Renderer implements GLSurfaceView.Renderer {

    public interface Driver {
        void frame(float dt, Renderer r);
    }

    private static final String SOLID_VS =
        "#version 300 es\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "layout(location=1) in vec3 aCol;\n" +
        "uniform mat4 uVP;\n" +
        "uniform vec3 uCam;\n" +
        "uniform float uFogS, uFogE;\n" +
        "out vec3 vCol;\n" +
        "out float vFog;\n" +
        "void main(){\n" +
        "  gl_Position = uVP * vec4(aPos,1.0);\n" +
        "  vCol = aCol;\n" +
        "  float d = distance(uCam, aPos);\n" +
        "  float f = clamp((d - uFogS) / max(uFogE - uFogS, 0.001), 0.0, 1.0);\n" +
        "  vFog = f * f;\n" +
        "}\n";
    private static final String SOLID_FS =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "in vec3 vCol; in float vFog;\n" +
        "uniform vec3 uFogC;\n" +
        "out vec4 o;\n" +
        "void main(){ o = vec4(mix(vCol, uFogC, vFog), 1.0); }\n";
    private static final String LINE_VS =
        "#version 300 es\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "layout(location=1) in vec4 aColA;\n" +
        "uniform mat4 uVP;\n" +
        "uniform vec3 uCam;\n" +
        "uniform float uFogS, uFogE;\n" +
        "out vec4 vColA;\n" +
        "out float vFog;\n" +
        "void main(){\n" +
        "  gl_Position = uVP * vec4(aPos,1.0);\n" +
        "  vColA = aColA;\n" +
        "  float d = distance(uCam, aPos);\n" +
        "  float f = clamp((d - uFogS) / max(uFogE - uFogS, 0.001), 0.0, 1.0);\n" +
        "  vFog = f * f;\n" +
        "}\n";
    private static final String LINE_FS =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "in vec4 vColA; in float vFog;\n" +
        "uniform vec3 uFogC;\n" +
        "out vec4 o;\n" +
        "void main(){ vec3 c = mix(vColA.rgb, uFogC, vFog); o = vec4(c, vColA.a); }\n";

    public int w = 800, h = 450;
    private Driver driver;

    private int progSolid, progLine, uniVP, uniCam, uniFogS, uniFogE, uniFogC,
            uniVLP, uniVCam, uniVFogS, uniVFogE, uniVFogC;
    private int vaoDyn, vboDyn, vaoStatic, vboStatic, vaoLine, vboLine;

    private float[] dyn = new float[8192 * 6];
    private int dynCount;
    private float[] lines = new float[4096 * 7];
    private int lineCount;
    private float[] statics = new float[16384 * 6];
    private int staticCount;
    private boolean capturing;

    private float camX, camY, camZ, drawDist;
    private float[] vp = new float[16];
    private float fogS, fogE;
    private float fogR = 0.04f, fogG = 0.055f, fogB = 0.09f;

    private long lastNs;

    public Renderer(Driver driver) { this.driver = driver; }

    private int frames;
    private boolean fatal;

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            onSurfaceCreatedInner(gl, config);
        } catch (Throwable t) {
            fatal = true;
            android.util.Log.e("DEADZONE", "gl surface create error", t);
            reportFatal(t);
        }
    }

    private void reportFatal(final Throwable t) {
        // Runs on the GL thread — hop to the UI thread before touching any View.
        if (driver instanceof com.deadzone.core.Game) {
            android.app.Activity act = ((com.deadzone.core.Game) driver).act;
            if (act instanceof com.deadzone.MainActivity) {
                act.runOnUiThread(new Runnable() {
                    @Override public void run() { ((com.deadzone.MainActivity) act).showFatal(t); }
                });
            }
        }
    }

    private void onSurfaceCreatedInner(GL10 gl, EGLConfig config) {
        com.deadzone.core.Boot.log("GL surface: "
                + GLES30.glGetString(GLES30.GL_RENDERER) + " / "
                + GLES30.glGetString(GLES30.GL_VERSION));
        GLES30.glClearColor(0.04f, 0.055f, 0.09f, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        progSolid = link("s", SOLID_VS, SOLID_FS);
        uniVP = GLES30.glGetUniformLocation(progSolid, "uVP");
        uniCam = GLES30.glGetUniformLocation(progSolid, "uCam");
        uniFogS = GLES30.glGetUniformLocation(progSolid, "uFogS");
        uniFogE = GLES30.glGetUniformLocation(progSolid, "uFogE");
        uniFogC = GLES30.glGetUniformLocation(progSolid, "uFogC");
        progLine = link("l", LINE_VS, LINE_FS);
        uniVLP = GLES30.glGetUniformLocation(progLine, "uVP");
        uniVCam = GLES30.glGetUniformLocation(progLine, "uCam");
        uniVFogS = GLES30.glGetUniformLocation(progLine, "uFogS");
        uniVFogE = GLES30.glGetUniformLocation(progLine, "uFogE");
        uniVFogC = GLES30.glGetUniformLocation(progLine, "uFogC");

        int[] tmp = new int[1];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoDyn = tmp[0];
        GLES20.glGenBuffers(1, tmp, 0); vboDyn = tmp[0];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoStatic = tmp[0];
        GLES20.glGenBuffers(1, tmp, 0); vboStatic = tmp[0];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoLine = tmp[0];
        GLES20.glGenBuffers(1, tmp, 0); vboLine = tmp[0];
        lastNs = System.nanoTime();
    }

    private int link(String tag, String vs, String fs) {
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs, tag + "vs"));
        GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs, tag + "fs"));
        GLES30.glLinkProgram(p);
        int[] st = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, st, 0);
        if (st[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(p);
            throw new RuntimeException("link failed " + tag + ": " + log);
        }
        return p;
    }

    private int compile(int type, String src, String tag) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] st = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0);
        if (st[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            throw new RuntimeException("shader failed " + tag + ": " + log);
        }
        return s;
    }

    private void bind6(int vao, int vbo) {
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12);
        GLES30.glBindVertexArray(0);
    }

    private void bind7(int vao, int vbo) {
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 28, 12);
        GLES30.glBindVertexArray(0);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (fatal) return;
        try {
            this.w = width; this.h = height;
            GLES30.glViewport(0, 0, width, height);
            bind6(vaoDyn, vboDyn);
            bind6(vaoStatic, vboStatic);
            bind7(vaoLine, vboLine);
        } catch (Throwable t) {
            fatal = true;
            android.util.Log.e("DEADZONE", "gl surface changed error", t);
            reportFatal(t);
        }
    }

    @Override public void onDrawFrame(GL10 gl) {
        if (fatal) return; // surface/context is broken; error screen already requested
        frames++;
        // Only log the first couple of seconds worth of heartbeats — enough to
        // confirm rendering started without the log (and its on-screen overlay)
        // growing forever while the game is being played.
        if (frames == 1 || frames == 60 || frames == 180)
            com.deadzone.core.Boot.log("GL frame " + frames + " drawn");
        long now = System.nanoTime();
        float dt = (now - lastNs) / 1e9f;
        lastNs = now;
        if (dt > 0.1f) dt = 0.1f;
        driver.frame(dt, this);
    }

    // ---------- per-frame API (called on GL thread by Driver) ----------

    public void begin(float fr, float fg, float fb) {
        fogR = fr; fogG = fg; fogB = fb;
        GLES30.glClearColor(fr, fg, fb, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        dynCount = 0;
        lineCount = 0;
    }

    public void setCam(float x, float y, float z, float dist) {
        camX = x; camY = y; camZ = z; drawDist = dist;
        fogS = dist * 0.45f;
        fogE = dist * 0.97f;
    }

    public void setViewProj(float[] vp, float fov, float aspect) {
        Matrix.perspectiveM(this.vp, 0, fov * (float) (Math.PI / 180.0), aspect, 0.15f, 500f);
        System.arraycopy(this.vp, 0, vp, 0, 16);
    }

    /** Use a precomputed view-projection matrix. */
    public void setVP(float[] vp) {
        System.arraycopy(vp, 0, this.vp, 0, 16);
    }

    public void setLookVP(Vec3 eye, Vec3 center, Vec3 up, float fov) {
        float[] proj = new float[16];
        Matrix.perspectiveM(proj, 0, fov * (float) (Math.PI / 180.0), (float) w / (float) h, 0.15f, 500f);
        float[] view = new float[16];
        Mat4.lookAt(view, eye, center, up);
        Mat4.mul(vp, proj, view);
    }

    public void setFog(float r, float g, float b) { fogR = r; fogG = g; fogB = b; }

    public void end() {
        // dynamic solid
        GLES30.glUseProgram(progSolid);
        GLES30.glUniformMatrix4fv(uniVP, 1, false, vp, 0);
        GLES30.glUniform3f(uniCam, camX, camY, camZ);
        GLES30.glUniform1f(uniFogS, fogS);
        GLES30.glUniform1f(uniFogE, fogE);
        GLES30.glUniform3f(uniFogC, fogR, fogG, fogB);
        if (staticCount > 0) {
            GLES30.glBindVertexArray(vaoStatic);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, staticCount);
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboDyn);
        if (dynCount > 0) {
            FloatBuffer fb = ByteBuffer.allocateDirect(dynCount * 24).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(dyn, 0, dynCount);
            fb.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, dynCount * 24, fb, GLES30.GL_DYNAMIC_DRAW);
            GLES30.glBindVertexArray(vaoDyn);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, dynCount);
        }
        // lines
        GLES30.glUseProgram(progLine);
        GLES30.glUniformMatrix4fv(uniVLP, 1, false, vp, 0);
        GLES30.glUniform3f(uniVCam, camX, camY, camZ);
        GLES30.glUniform1f(uniVFogS, fogS);
        GLES30.glUniform1f(uniVFogE, fogE);
        GLES30.glUniform3f(uniVFogC, fogR, fogG, fogB);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(false);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboLine);
        if (lineCount > 0) {
            FloatBuffer fb = ByteBuffer.allocateDirect(lineCount * 28).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(lines, 0, lineCount);
            fb.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, lineCount * 28, fb, GLES30.GL_DYNAMIC_DRAW);
            GLES30.glBindVertexArray(vaoLine);
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, lineCount);
        }
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindVertexArray(0);
    }

    // ---------- static capture (level build) ----------

    public void beginStatic() { capturing = true; staticCount = 0; }
    public void endStatic() {
        capturing = false;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboStatic);
        if (staticCount > 0) {
            FloatBuffer fb = ByteBuffer.allocateDirect(staticCount * 24).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(statics, 0, staticCount);
            fb.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, staticCount * 24, fb, GLES30.GL_STATIC_DRAW);
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public void clearStatic() {
        staticCount = 0;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboStatic);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 0, null, GLES30.GL_STATIC_DRAW);
    }

    // ---------- geometry ----------

    // face corner indices (corners listed in CORD below), and per-face shading
    private static final int[][] FACES = {
        {3, 2, 6, 7}, // +y
        {0, 4, 5, 1}, // -y
        {1, 5, 6, 2}, // +x
        {0, 3, 7, 4}, // -x
        {4, 7, 6, 5}, // +z
        {0, 1, 2, 3}, // -z
    };
    private static final float[] SHADE = {1.0f, 0.45f, 0.82f, 0.68f, 0.9f, 0.72f};
    private static final float[][] CORD = {
        {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
        {-0.5f, -0.5f, 0.5f}, {0.5f, -0.5f, 0.5f}, {0.5f, 0.5f, 0.5f}, {-0.5f, 0.5f, 0.5f},
    };

    private void ensureDyn(int need) {
        if (need > dyn.length) {
            float[] nd = new float[Math.max(need, dyn.length * 2)];
            System.arraycopy(dyn, 0, nd, 0, dyn.length);
            dyn = nd;
        }
    }

    private void ensureLine(int need) {
        if (need > lines.length) {
            float[] nl = new float[Math.max(need, lines.length * 2)];
            System.arraycopy(lines, 0, nl, 0, lines.length);
            lines = nl;
        }
    }

    private void ensureStatic(int need) {
        if (need > statics.length) {
            float[] ns = new float[Math.max(need, statics.length * 2)];
            System.arraycopy(statics, 0, ns, 0, statics.length);
            statics = ns;
        }
    }

    /** Add an axis box centered at (px,py,pz), yaw around Y, optional rotX, size sx,sy,sz, color r,g,b. */
    public void addBox(float px, float py, float pz, float yaw, float rx,
                       float sx, float sy, float sz, float r, float g, float b) {
        if (!capturing) {
            float dx = px - camX, dz = pz - camZ;
            if (dx * dx + dz * dz > drawDist * drawDist) return;
        }
        int base = capturing ? staticCount : dynCount;
        int need = 24 * 6;
        if (capturing) ensureStatic(base + need); else ensureDyn(base + need);
        float[] target = capturing ? statics : dyn;
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        float cr = (float) Math.cos(rx), sr = (float) Math.sin(rx);
        float[] t = target;
        for (int f = 0; f < 6; f++) {
            float sh = SHADE[f];
            int[] fc = FACES[f];
            for (int k = 0; k < 4; k++) {
                float[] c = CORD[fc[k]];
                float x = c[0], y = c[1], z = c[2];
                // rotX (local)
                float y2 = y * cr - z * sr;
                float z2 = y * sr + z * cr;
                // yaw (local)
                float x3 = x * cyw + z2 * syw;
                float z3 = -x * syw + z2 * cyw;
                int o = (base + f * 4 + k) * 6;
                t[o] = px + x3 * sx;
                t[o + 1] = py + y2 * sy;
                t[o + 2] = pz + z3 * sz;
                t[o + 3] = clamp01(r * sh);
                t[o + 4] = clamp01(g * sh);
                t[o + 5] = clamp01(b * sh);
            }
        }
        if (capturing) staticCount = base + 24; else dynCount = base + 24;
    }

    private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    public void addLine(float x1, float y1, float z1, float x2, float y2, float z2,
                        float r, float g, float b, float a) {
        ensureLine(lineCount + 2);
        int o = lineCount * 7;
        lines[o] = x1; lines[o + 1] = y1; lines[o + 2] = z1;
        lines[o + 3] = r; lines[o + 4] = g; lines[o + 5] = b; lines[o + 6] = a;
        o += 7;
        lines[o] = x2; lines[o + 1] = y2; lines[o + 2] = z2;
        lines[o + 3] = r; lines[o + 4] = g; lines[o + 5] = b; lines[o + 6] = a;
        lineCount += 2;
    }
}
