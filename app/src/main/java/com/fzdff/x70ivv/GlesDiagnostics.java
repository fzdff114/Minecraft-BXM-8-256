package com.fzdff.x70ivv;

import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Runs conservative, non-destructive capability tests in the helper app's own EGL context. */
public final class GlesDiagnostics implements GLSurfaceView.Renderer {
    public interface Listener { void onDiagnostics(String report); }
    private final Listener listener;

    public GlesDiagnostics(Listener listener) { this.listener = listener; }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        StringBuilder out = new StringBuilder();
        String vendor = safe(GLES20.glGetString(GLES20.GL_VENDOR));
        String renderer = safe(GLES20.glGetString(GLES20.GL_RENDERER));
        String version = safe(GLES20.glGetString(GLES20.GL_VERSION));
        String shading = safe(GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
        out.append("助手进程 GLES Vendor：").append(vendor).append('\n');
        out.append("助手进程 GLES Renderer：").append(renderer).append('\n');
        out.append("GLES Version：").append(version).append('\n');
        out.append("GLSL Version：").append(shading).append('\n');
        out.append("EGL Vendor：").append(safe(EGL14.eglQueryString(EGL14.eglGetCurrentDisplay(), EGL14.EGL_VENDOR))).append('\n');
        out.append("EGL Version：").append(safe(EGL14.eglQueryString(EGL14.eglGetCurrentDisplay(), EGL14.EGL_VERSION))).append('\n');

        int[] v = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, v, 0);
        out.append("GL_MAX_TEXTURE_SIZE：").append(v[0]).append('\n');
        GLES20.glGetIntegerv(0x84E8, v, 0);
        out.append("GL_MAX_RENDERBUFFER_SIZE：").append(v[0]).append('\n');
        GLES30.glGetIntegerv(0x8D57, v, 0);
        out.append("GL_MAX_SAMPLES：").append(v[0]).append('\n');

        Set<String> extensions = new LinkedHashSet<>();
        int[] extensionCount = new int[1];
        GLES30.glGetIntegerv(0x821D, extensionCount, 0); // GL_NUM_EXTENSIONS
        for (int i = 0; i < Math.max(0, extensionCount[0]); i++) {
            String item = GLES30.glGetStringi(GLES20.GL_EXTENSIONS, i);
            if (item != null && !item.isEmpty()) extensions.add(item);
        }
        String[] important = {
                "GL_EXT_color_buffer_float",
                "GL_EXT_color_buffer_half_float",
                "GL_EXT_sRGB",
                "GL_EXT_texture_format_BGRA8888",
                "GL_EXT_disjoint_timer_query",
                "GL_EXT_multisampled_render_to_texture",
                "GL_OES_depth24",
                "GL_OES_packed_depth_stencil",
                "GL_EXT_depth_clamp",
                "GL_EXT_shader_framebuffer_fetch",
                "GL_EXT_texture_storage",
                "GL_EXT_draw_buffers_indexed"
        };
        out.append("\n关键扩展：\n");
        for (String item : important) out.append(extensions.contains(item) ? "[有] " : "[无] ").append(item).append('\n');

        out.append("\nFramebuffer 可用性（助手进程测试）：\n");
        out.append("RGBA8：").append(testColorTarget(0x8058, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE) ? "完整" : "失败").append('\n');
        out.append("RGBA16F：").append(testColorTarget(0x881A, GLES20.GL_RGBA, 0x140B) ? "完整" : "失败/不支持").append('\n');
        out.append("sRGB8_ALPHA8：").append(testColorTarget(0x8C43, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE) ? "完整" : "失败/不支持").append('\n');
        out.append("Depth24Stencil8：").append(testDepthStencil() ? "完整" : "失败/不支持").append('\n');
        out.append("简单 GLSL 300 ES 编译/链接：").append(testShader() ? "成功" : "失败").append('\n');

        out.append("\n注意：这些结果来自助手自身的 EGL/GLES 进程，不能代表 Minecraft RenderDragon 的实际 Vulkan/EGL 上下文。它们用于判断驱动基础能力，不是 VV 兼容保证。");
        final String report = out.toString();
        if (listener != null) listener.onDiagnostics(report);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) { }
    @Override public void onDrawFrame(GL10 gl) { }

    private static boolean testColorTarget(int internalFormat, int format, int type) {
        int[] ids = new int[1];
        int[] fb = new int[1];
        try {
            while (GLES20.glGetError() != GLES20.GL_NO_ERROR) { }
            GLES20.glGenTextures(1, ids, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            GLES30.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, internalFormat, 16, 16, 0, format, type, null);
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) return false;
            GLES20.glGenFramebuffers(1, fb, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, ids[0], 0);
            return GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
                    && GLES20.glGetError() == GLES20.GL_NO_ERROR;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            if (fb[0] != 0) GLES20.glDeleteFramebuffers(1, fb, 0);
            if (ids[0] != 0) GLES20.glDeleteTextures(1, ids, 0);
        }
    }

    private static boolean testDepthStencil() {
        int[] texture = new int[1];
        int[] renderbuffer = new int[1];
        int[] framebuffer = new int[1];
        try {
            GLES20.glGenTextures(1, texture, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 16, 16, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glGenRenderbuffers(1, renderbuffer, 0);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderbuffer[0]);
            GLES30.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, 0x88F0, 16, 16);
            GLES20.glGenFramebuffers(1, framebuffer, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, texture[0], 0);
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, 0x821A,
                    GLES20.GL_RENDERBUFFER, renderbuffer[0]);
            return GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
                    && GLES20.glGetError() == GLES20.GL_NO_ERROR;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            if (framebuffer[0] != 0) GLES20.glDeleteFramebuffers(1, framebuffer, 0);
            if (renderbuffer[0] != 0) GLES20.glDeleteRenderbuffers(1, renderbuffer, 0);
            if (texture[0] != 0) GLES20.glDeleteTextures(1, texture, 0);
        }
    }

    private static boolean testShader() {
        int vertex = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        int fragment = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        int program = GLES20.glCreateProgram();
        try {
            GLES20.glShaderSource(vertex, "#version 300 es\nin vec4 aPos; void main(){gl_Position=aPos;}");
            GLES20.glCompileShader(vertex);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(vertex, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) return false;
            GLES20.glShaderSource(fragment, "#version 300 es\nprecision highp float; out vec4 fragColor; void main(){fragColor=vec4(0.2,0.4,0.6,1.0);}");
            GLES20.glCompileShader(fragment);
            GLES20.glGetShaderiv(fragment, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) return false;
            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
            return ok[0] != 0 && GLES20.glGetError() == GLES20.GL_NO_ERROR;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
        }
    }

    private static String safe(String value) { return value == null ? "(null)" : value; }
}
