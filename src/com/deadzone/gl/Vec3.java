package com.deadzone.gl;

public class Vec3 {
    public float x, y, z;

    public Vec3() {}

    public Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vec3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }

    public Vec3 copy(Vec3 v) { this.x = v.x; this.y = v.y; this.z = v.z; return this; }

    public Vec3 clone() { return new Vec3(x, y, z); }

    public Vec3 add(Vec3 v) { x += v.x; y += v.y; z += v.z; return this; }

    public Vec3 sub(Vec3 v) { x -= v.x; y -= v.y; z -= v.z; return this; }

    public Vec3 mul(float f) { x *= f; y *= f; z *= f; return this; }

    public Vec3 addScaled(Vec3 v, float f) { x += v.x * f; y += v.y * f; z += v.z * f; return this; }

    public float len() { return (float) Math.sqrt(x * x + y * y + z * z); }

    public float len2() { return x * x + y * y + z * z; }

    public float dist(Vec3 v) { float dx = x - v.x, dy = y - v.y, dz = z - v.z; return (float) Math.sqrt(dx * dx + dy * dy + dz * dz); }

    public float dist2(Vec3 v) { float dx = x - v.x, dy = y - v.y, dz = z - v.z; return dx * dx + dy * dy + dz * dz; }

    public Vec3 norm() { float l = len(); if (l > 1e-6f) { x /= l; y /= l; z /= l; } return this; }

    public float dot(Vec3 v) { return x * v.x + y * v.y + z * v.z; }

    public static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }
}
