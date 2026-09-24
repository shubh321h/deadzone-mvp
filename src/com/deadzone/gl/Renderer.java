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
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * HD GLES3 renderer.
 *
 * Vertex format (12 floats / 48 bytes):
 *   pos(3) color(3) normal(3) uv(2) material(1)
 *
 * Features: 2x2 material atlas (asphalt / concrete / metal / rust),
 * sun + sky lighting (wrap diffuse, hemispheric ambient), procedural
 * emissive windows on static concrete, distance fog, procedural sky
 * dome with sun glow, MSAA (4x when the device offers it).
 *
 * Batching: everything still lands in 2-3 draw calls; mid-range GPUs
 * stay comfortable.
 */
public class Renderer implements GLSurfaceView.Renderer {

    public interface Driver {
        void frame(float dt, Renderer r);
    }

    // ---------------- shaders ----------------

    private static final String SOLID_VS =
        "#version 300 es\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "layout(location=1) in vec3 aCol;\n" +
        "layout(location=2) in vec3 aNrm;\n" +
        "layout(location=3) in vec2 aUV;\n" +
        "layout(location=4) in float aMat;\n" +
        "uniform mat4 uVP;\n" +
        "uniform vec3 uCam;\n" +
        "uniform float uFogS, uFogE;\n" +
        "out vec3 vCol;\n" +
        "out vec3 vNrm;\n" +
        "out vec2 vUV;\n" +
        "out float vMat;\n" +
        "out float vFog;\n" +
        "out vec3 vWPos;\n" +
        "void main(){\n" +
        "  gl_Position = uVP * vec4(aPos,1.0);\n" +
        "  vCol = aCol; vNrm = aNrm; vUV = aUV; vMat = aMat; vWPos = aPos;\n" +
        "  float d = distance(uCam, aPos);\n" +
        "  float f = clamp((d - uFogS) / max(uFogE - uFogS, 0.001), 0.0, 1.0);\n" +
        "  vFog = f * f;\n" +
        "}\n";

    private static final String SOLID_FS =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "precision mediump sampler2D;\n" +
        "in vec3 vCol; in vec3 vNrm; in vec2 vUV; in float vMat; in float vFog; in vec3 vWPos;\n" +
        "uniform sampler2D uAtlas;\n" +
        "uniform vec3 uFogC;\n" +
        "uniform vec3 uSunDir;\n" +   // pointing TOWARD the sun
        "uniform vec3 uSunCol;\n" +
        "uniform vec3 uAmbT;\n" +     // hemispheric ambient, sky
        "uniform vec3 uAmbB;\n" +     // hemispheric ambient, ground bounce
        "uniform float uEmisPass;\n" +// 1 during the static (level) pass
        "uniform float uTime;\n" +
        "out vec4 o;\n" +
        "float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453); }\n" +
        "void main(){\n" +
        // ---- atlas select: 2x2 tiles [asphalt concrete / metal rust] ----
        "  float m = clamp(vMat, 0.0, 3.0);\n" +
        "  vec2 tile = vec2(mod(m,2.0), floor(m/2.0));\n" +
        "  vec2 auv = tile * 0.5 + fract(vUV) * 0.496 + 0.002;\n" +
        "  vec3 tex = texture(uAtlas, auv).rgb * (2.0/255.0) * 4.0;\n" + // atlas stored dim; normalize
        "  vec3 alb = vCol * tex;\n" +
        // ---- lighting: wrap diffuse sun + hemispheric ambient ----
        "  vec3 N = normalize(vNrm);\n" +
        "  float ndl = dot(N, uSunDir);\n" +
        "  float diff = clamp(ndl * 0.5 + 0.5, 0.0, 1.0);   // wrap\n" +
        "  diff = diff * diff * (3.0 - 2.0 * diff);         // smoothstep\n" +
        "  vec3 amb = mix(uAmbB, uAmbT, N.y * 0.5 + 0.5);\n" +
        "  vec3 lit = alb * (uSunCol * diff + amb);\n" +
        // ---- procedural emissive windows (static concrete only) ----
        "  if (uEmisPass > 0.5 && m > 0.5 && m < 1.5 && abs(N.y) < 0.7) {\n" +
        "    vec2 cell = floor(vUV * vec2(2.2, 3.2));\n" +
        "    vec2 f = fract(vUV * vec2(2.2, 3.2));\n" +
        "    float on = step(0.90, hash(cell));\n" +
        "    float shape = step(0.18, f.x) * step(f.x, 0.82) * step(0.22, f.y) * step(f.y, 0.86);\n" +
        "    float flick = step(-0.85, sin(uTime * 2.7 + hash(cell) * 41.0));\n" +
        "    lit += vec3(0.75, 0.95, 0.62) * on * shape * flick * 1.35;\n" +
        "  }\n" +
        "  vec3 c = mix(lit, uFogC, vFog);\n" +
        "  o = vec4(c, 1.0);\n" +
        "}\n";

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

    // procedural sky: fullscreen triangle, ray reconstructed via inverse VP
    private static final String SKY_VS =
        "#version 300 es\n" +
        "out vec2 vNDC;\n" +
        "void main(){\n" +
        "  vec2 p = vec2((gl_VertexID == 1) ? 3.0 : -1.0, (gl_VertexID == 2) ? 3.0 : -1.0);\n" +
        "  gl_Position = vec4(p, 0.99999, 1.0);\n" +
        "  vNDC = p;\n" +
        "}\n";

    private static final String SKY_FS =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "in vec2 vNDC;\n" +
        "uniform mat4 uInvVP;\n" +
        "uniform vec3 uEye;\n" +
        "uniform vec3 uZen;\n" +
        "uniform vec3 uHor;\n" +
        "uniform vec3 uSunDir;\n" +
        "uniform vec3 uSunCol;\n" +
        "out vec4 o;\n" +
        "void main(){\n" +
        "  vec4 a = uInvVP * vec4(vNDC, -1.0, 1.0);\n" +
        "  vec4 b = uInvVP * vec4(vNDC,  1.0, 1.0);\n" +
        "  vec3 dir = normalize(b.xyz / b.w - a.xyz / a.w);\n" +
        "  float t = clamp(dir.y * 1.6 + 0.18, 0.0, 1.0);\n" +
        "  vec3 c = mix(uHor, uZen, pow(t, 0.8));\n" +
        "  float s = max(dot(dir, uSunDir), 0.0);\n" +
        "  c += uSunCol * (pow(s, 180.0) * 0.9 + pow(s, 8.0) * 0.16);\n" +
        "  c += uHor * pow(1.0 - t, 6.0) * 0.35;   // ground haze band\n" +
        "  o = vec4(c, 1.0);\n" +
        "}\n";

    // ---------------- sky/light presets ----------------
    // 0 Mumbai storm, 1 Delhi dust, 2 Ladakh alpine, 3 interior dim
    private static final float[][] PRESET = {
        // zen(r,g,b) hor(r,g,b) sun(r,g,b) ambT ambB        | sunDir x,y,z
        {0.045f,0.065f,0.100f,  0.150f,0.190f,0.235f,  0.75f,0.82f,0.88f,  0.36f,0.41f,0.50f,  0.15f,0.15f,0.16f,  -0.35f,-0.80f,-0.30f},
        {0.100f,0.105f,0.135f,  0.440f,0.360f,0.260f,  1.10f,0.97f,0.80f,  0.46f,0.44f,0.42f,  0.24f,0.21f,0.17f,  -0.30f,-0.76f,-0.25f},
        {0.110f,0.175f,0.300f,  0.540f,0.610f,0.710f,  1.25f,1.22f,1.15f,  0.50f,0.55f,0.65f,  0.46f,0.50f,0.58f,  -0.25f,-0.85f,-0.15f},
        {0.030f,0.032f,0.038f,  0.120f,0.105f,0.090f,  0.55f,0.50f,0.42f,  0.26f,0.23f,0.20f,  0.10f,0.09f,0.08f,  -0.20f,-0.90f,-0.10f},
    };

    // ---------------- MSAA config chooser ----------------
    public static class MsaaChooser implements GLSurfaceView.EGLConfigChooser {
        @Override public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] num = new int[1];
            for (int samples : new int[]{4, 2}) {
                int[] attrs = {
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples,
                    EGL10.EGL_NONE
                };
                EGLConfig[] cfg = new EGLConfig[1];
                if (egl.eglChooseConfig(display, attrs, cfg, 1, num) && num[0] > 0 && cfg[0] != null)
                    return cfg[0];
            }
            // fallback: no MSAA
            int[] attrs = {
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_NONE
            };
            EGLConfig[] cfg = new EGLConfig[1];
            egl.eglChooseConfig(display, attrs, cfg, 1, num);
            return cfg[0];
        }
    }

    // ---------------- state ----------------

    public int w = 800, h = 450;
    private Driver driver;

    private int progSolid, progLine, progSky;
    private int uniVP, uniCam, uniFogS, uniFogE, uniFogC, uniAtlas,
            uniSunDir, uniSunCol, uniAmbT, uniAmbB, uniEmisPass, uniTime;
    private int uniVLP, uniVCam, uniVFogS, uniVFogE, uniVFogC;
    private int uniSkyInvVP, uniSkyEye, uniSkyZen, uniSkyHor, uniSkySunDir, uniSkySunCol;
    private int vaoDyn, vboDyn, vaoStatic, vboStatic, vaoLine, vboLine, vaoEmpty;
    private int atlasTex;

    private static final int STRIDE = 48; // 12 floats

    private float[] dyn = new float[16384 * 12];
    private int dynCount;
    private float[] lines = new float[4096 * 7];
    private int lineCount;
    private float[] statics = new float[65536 * 12];
    private int staticCount;
    private boolean capturing;

    private float camX, camY, camZ, drawDist;
    private float[] vp = new float[16];
    private float[] invVP = new float[16];
    private float fogS = 40, fogE = 90;
    private float fogR = 0.04f, fogG = 0.055f, fogB = 0.09f;
    private int skyPreset = 0;
    private boolean skyDirty = true;

    private float sunX = -0.35f, sunY = -0.8f, sunZ = -0.3f;

    private long lastNs;
    private float timeS;
    private int frames;

    public Renderer(Driver driver) { this.driver = driver; }

    // ---------------- GL lifecycle ----------------

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        com.deadzone.core.Boot.log("GL surface: "
                + GLES30.glGetString(GLES30.GL_RENDERER) + " / "
                + GLES30.glGetString(GLES30.GL_VERSION));
        GLES30.glClearColor(0.04f, 0.055f, 0.09f, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);

        progSolid = link("s", SOLID_VS, SOLID_FS);
        uniVP = loc(progSolid, "uVP");
        uniCam = loc(progSolid, "uCam");
        uniFogS = loc(progSolid, "uFogS");
        uniFogE = loc(progSolid, "uFogE");
        uniFogC = loc(progSolid, "uFogC");
        uniAtlas = loc(progSolid, "uAtlas");
        uniSunDir = loc(progSolid, "uSunDir");
        uniSunCol = loc(progSolid, "uSunCol");
        uniAmbT = loc(progSolid, "uAmbT");
        uniAmbB = loc(progSolid, "uAmbB");
        uniEmisPass = loc(progSolid, "uEmisPass");
        uniTime = loc(progSolid, "uTime");

        progLine = link("l", LINE_VS, LINE_FS);
        uniVLP = loc(progLine, "uVP");
        uniVCam = loc(progLine, "uCam");
        uniVFogS = loc(progLine, "uFogS");
        uniVFogE = loc(progLine, "uFogE");
        uniVFogC = loc(progLine, "uFogC");

        progSky = link("sky", SKY_VS, SKY_FS);
        uniSkyInvVP = loc(progSky, "uInvVP");
        uniSkyEye = loc(progSky, "uEye");
        uniSkyZen = loc(progSky, "uZen");
        uniSkyHor = loc(progSky, "uHor");
        uniSkySunDir = loc(progSky, "uSunDir");
        uniSkySunCol = loc(progSky, "uSunCol");

        int[] tmp = new int[1];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoDyn = tmp[0];
        GLES20.glGenBuffers(1, tmp, 0); vboDyn = tmp[0];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoStatic = tmp[0];
        GLES20.glGenBuffers(1, tmp, 0); vboStatic = tmp[0];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoLine = tmp[0];
        GLES20.glGenBuffers(1, tmp, 0); vboLine = tmp[0];
        GLES30.glGenVertexArrays(1, tmp, 0); vaoEmpty = tmp[0];

        bind12(vaoStatic, vboStatic);
        bind12(vaoDyn, vboDyn);
        bind7(vaoLine, vboLine);

        loadAtlas();
        lastNs = System.nanoTime();
    }

    private int loc(int p, String n) { return GLES30.glGetUniformLocation(p, n); }

    private void loadAtlas() {
        int[] t = new int[1];
        GLES30.glGenTextures(1, t, 0);
        atlasTex = t[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTex);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_MIRRORED_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_MIRRORED_REPEAT);
        android.graphics.Bitmap bmp = null;
        try {
            java.io.InputStream in = new java.io.FileInputStream(
                    new java.io.File(com.deadzone.data.DataLib.assetDir(), "atlas.png"));
            bmp = android.graphics.BitmapFactory.decodeStream(in);
            in.close();
        } catch (Exception e) {
            com.deadzone.core.Boot.log("atlas load failed: " + e);
        }
        if (bmp != null) {
            android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
            com.deadzone.core.Boot.log("atlas " + bmp.getWidth() + "x" + bmp.getHeight() + " uploaded");
            bmp.recycle();
        } else {
            // fallback: 1x1 grey so the game still renders if art is missing
            byte[] px = {(byte) 128, (byte) 128, (byte) 128, (byte) 255};
            ByteBuffer bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            bb.put(px).position(0);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb);
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
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

    private void bind12(int vao, int vbo) {
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, STRIDE, 24);
        GLES30.glEnableVertexAttribArray(3);
        GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, STRIDE, 36);
        GLES30.glEnableVertexAttribArray(4);
        GLES30.glVertexAttribPointer(4, 1, GLES30.GL_FLOAT, false, STRIDE, 44);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private void bind7(int vao, int vbo) {
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 28, 12);
        GLES30.glBindVertexArray(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.w = width; this.h = height;
        GLES30.glViewport(0, 0, width, height);
    }

    @Override public void onDrawFrame(GL10 gl) {
        frames++;
        if (frames == 1 || frames == 60 || frames % 600 == 0)
            com.deadzone.core.Boot.log("GL frame " + frames + " drawn");
        long now = System.nanoTime();
        float dt = (now - lastNs) / 1e9f;
        lastNs = now;
        if (dt > 0.1f) dt = 0.1f;
        timeS += dt;
        driver.frame(dt, this);
    }

    // ---------------- per-frame API ----------------

    public void begin(float fr, float fg, float fb) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        dynCount = 0;
        lineCount = 0;
    }

    public void setCam(float x, float y, float z, float dist) {
        camX = x; camY = y; camZ = z; drawDist = dist;
        fogS = dist * 0.45f;
        fogE = dist * 0.97f;
    }

    public void setVP(float[] vp) {
        System.arraycopy(vp, 0, this.vp, 0, 16);
        invertM(this.vp, invVP);
    }

    public void setLookVP(Vec3 eye, Vec3 center, Vec3 up, float fov) {
        float[] proj = new float[16];
        Matrix.perspectiveM(proj, 0, fov * (float) (Math.PI / 180.0), (float) w / (float) h, 0.15f, 500f);
        float[] view = new float[16];
        Mat4.lookAt(view, eye, center, up);
        Mat4.mul(vp, proj, view);
        invertM(vp, invVP);
    }

    public void setFog(float r, float g, float b) { fogR = r; fogG = g; fogB = b; }

    /** Select sky + lighting preset and matching fog. 0 Mumbai, 1 Delhi, 2 Ladakh, 3 interior. */
    public void atmosphere(int preset) {
        skyPreset = Math.max(0, Math.min(3, preset));
        float[] p = PRESET[skyPreset];
        // fog = horizon color, slightly dimmed
        fogR = p[3] * 0.9f; fogG = p[4] * 0.9f; fogB = p[5] * 0.9f;
        sunX = p[15]; sunY = p[16]; sunZ = p[17];
        skyDirty = true;
    }

    /** Draw the sky dome (call right after begin(), before geometry). */
    public void drawSky() {
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glUseProgram(progSky);
        GLES30.glUniformMatrix4fv(uniSkyInvVP, 1, false, invVP, 0);
        GLES30.glUniform3f(uniSkyEye, camX, camY, camZ);
        float[] p = PRESET[skyPreset];
        GLES30.glUniform3f(uniSkyZen, p[0], p[1], p[2]);
        GLES30.glUniform3f(uniSkyHor, p[3], p[4], p[5]);
        GLES30.glUniform3f(uniSkySunDir, p[15], p[16], p[17]);
        GLES30.glUniform3f(uniSkySunCol, p[6], p[7], p[8]);
        GLES30.glBindVertexArray(vaoEmpty);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
    }

    public void end() {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, atlasTex);
        // solid pass — static level geometry first (windows lit), then entities
        GLES30.glUseProgram(progSolid);
        GLES30.glUniformMatrix4fv(uniVP, 1, false, vp, 0);
        GLES30.glUniform3f(uniCam, camX, camY, camZ);
        GLES30.glUniform1f(uniFogS, fogS);
        GLES30.glUniform1f(uniFogE, fogE);
        GLES30.glUniform3f(uniFogC, fogR, fogG, fogB);
        GLES30.glUniform1i(uniAtlas, 0);
        GLES30.glUniform3f(uniSunDir, sunX, sunY, sunZ);
        float[] p = PRESET[skyPreset];
        GLES30.glUniform3f(uniSunCol, p[6], p[7], p[8]);
        GLES30.glUniform3f(uniAmbT, p[9], p[10], p[11]);
        GLES30.glUniform3f(uniAmbB, p[12], p[13], p[14]);
        GLES30.glUniform1f(uniTime, timeS);
        if (staticCount > 0) {
            GLES30.glUniform1f(uniEmisPass, 1f);
            GLES30.glBindVertexArray(vaoStatic);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, staticCount);
        }
        GLES30.glUniform1f(uniEmisPass, 0f);
        if (dynCount > 0) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboDyn);
            FloatBuffer fb = ByteBuffer.allocateDirect(dynCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(dyn, 0, dynCount);
            fb.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, dynCount * 4, fb, GLES30.GL_DYNAMIC_DRAW);
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
            FloatBuffer fb = ByteBuffer.allocateDirect(lineCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(lines, 0, lineCount);
            fb.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, lineCount * 4, fb, GLES30.GL_DYNAMIC_DRAW);
            GLES30.glBindVertexArray(vaoLine);
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, lineCount);
        }
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindVertexArray(0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    // ---------------- static capture (level build) ----------------

    private int material = 1; // concrete default

    /** Set the material used by subsequent addBox calls (static level build).
     *  0 asphalt, 1 concrete, 2 metal, 3 rust. */
    public void setMaterial(int m) { material = m; }

    public void beginStatic() { capturing = true; staticCount = 0; }

    public void endStatic() {
        capturing = false;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboStatic);
        if (staticCount > 0) {
            FloatBuffer fb = ByteBuffer.allocateDirect(staticCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(statics, 0, staticCount);
            fb.position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, staticCount * 4, fb, GLES30.GL_STATIC_DRAW);
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    public void clearStatic() {
        staticCount = 0;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboStatic);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 0, null, GLES30.GL_STATIC_DRAW);
    }

    // ---------------- geometry ----------------

    // face corner indices (into CORD), per-face shading, per-face normal
    private static final int[][] FACES = {
        {3, 2, 6, 7}, // +y
        {0, 4, 5, 1}, // -y
        {1, 5, 6, 2}, // +x
        {0, 3, 7, 4}, // -x
        {4, 7, 6, 5}, // +z
        {0, 1, 2, 3}, // -z
    };
    private static final float[] SHADE = {1.0f, 0.45f, 0.82f, 0.68f, 0.9f, 0.72f};
    private static final float[][] NRM = {
        {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
    };
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

    /** Add an axis box centered at (px,py,pz), yaw around Y, optional rotX,
     *  size sx,sy,sz, color r,g,b. UVs are world-scaled for uniform texel
     *  density; normals are per-face; material comes from setMaterial(). */
    public void addBox(float px, float py, float pz, float yaw, float rx,
                       float sx, float sy, float sz, float r, float g, float b) {
        if (!capturing) {
            float dx = px - camX, dz = pz - camZ;
            if (dx * dx + dz * dz > drawDist * drawDist) return;
        }
        int base = capturing ? staticCount : dynCount;
        int need = 24 * 12;
        if (capturing) ensureStatic(base + need); else ensureDyn(base + need);
        float[] target = capturing ? statics : dyn;
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        float cr = (float) Math.cos(rx), sr = (float) Math.sin(rx);
        float[] t = target;
        float mat = capturing ? material : 1f; // entities: concrete noise, no windows
        for (int f = 0; f < 6; f++) {
            float sh = SHADE[f];
            int[] fc = FACES[f];
            float[] nm = NRM[f];
            // face normal rotated by yaw (rotX small; ignore for lighting)
            float nx = nm[0] * cyw + nm[2] * syw;
            float nz = -nm[0] * syw + nm[2] * cyw;
            float ny = nm[1];
            // uv scale per face orientation (world meters -> tiles)
            float uw, vw;
            if (f < 2) { uw = sx; vw = sz; }       // top/bottom
            else if (f < 4) { uw = sz; vw = sy; }  // x sides
            else { uw = sx; vw = sy; }             // z sides
            float us = uw / 4f, vs = vw / 4f;
            for (int k = 0; k < 4; k++) {
                float[] c = CORD[fc[k]];
                float x = c[0], y = c[1], z = c[2];
                // rotX (local)
                float y2 = y * cr - z * sr;
                float z2 = y * sr + z * cr;
                // yaw (local)
                float x3 = x * cyw + z2 * syw;
                float z3 = -x * syw + z2 * cyw;
                int o = (base + f * 4 + k) * 12;
                t[o] = px + x3 * sx;
                t[o + 1] = py + y2 * sy;
                t[o + 2] = pz + z3 * sz;
                t[o + 3] = clamp01(r * sh);
                t[o + 4] = clamp01(g * sh);
                t[o + 5] = clamp01(b * sh);
                t[o + 6] = nx; t[o + 7] = ny; t[o + 8] = nz;
                // stable uv from local face coords (mirrored repeat wraps)
                t[o + 9] = (x3 * (f < 2 ? sx : (f < 4 ? sz : sx))) / 4f;
                t[o + 10] = (f < 2 ? z3 * sz : y2 * sy) / 4f;
                t[o + 11] = mat;
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

    // ---------------- math ----------------

    /** General 4x4 inverse (column-major, OpenGL layout). */
    private static void invertM(float[] m, float[] out) {
        float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];

        float b00 = a00 * a11 - a01 * a10;
        float b01 = a00 * a12 - a02 * a10;
        float b02 = a00 * a13 - a03 * a10;
        float b03 = a01 * a12 - a02 * a11;
        float b04 = a01 * a13 - a03 * a11;
        float b05 = a02 * a13 - a03 * a12;
        float b06 = a20 * a31 - a21 * a30;
        float b07 = a20 * a32 - a22 * a30;
        float b08 = a20 * a33 - a23 * a30;
        float b09 = a21 * a32 - a22 * a31;
        float b10 = a21 * a33 - a23 * a31;
        float b11 = a22 * a33 - a23 * a32;

        float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (Math.abs(det) < 1e-10f) {
            System.arraycopy(m, 0, out, 0, 16);
            return;
        }
        float inv = 1f / det;

        out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * inv;
        out[1] = (a02 * b10 - a01 * b11 - a03 * b09) * inv;
        out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * inv;
        out[3] = (a22 * b04 - a21 * b05 - a23 * b03) * inv;
        out[4] = (a12 * b08 - a10 * b11 - a13 * b07) * inv;
        out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * inv;
        out[6] = (a32 * b02 - a30 * b05 - a33 * b01) * inv;
        out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * inv;
        out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * inv;
        out[9] = (a01 * b08 - a00 * b10 - a03 * b06) * inv;
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * inv;
        out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * inv;
        out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * inv;
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * inv;
        out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * inv;
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * inv;
    }
}
