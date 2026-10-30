package com.necromancermod.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cista Java (bez zavislosti na Minecraftu): nacita model Necromancera
 * (prevedeny z Blockbench glTF) vcetne animaci a umi je vzorkovat.
 *
 * Format souboru geo/necromancer.bin (big endian):
 *   int magic, ushort nodeCount
 *   node: utf name, short parent, 3f translation, 4f rotation(xyzw), ushort quadCount,
 *         quad = 4 vrcholy x (x,y,z,u,v,nx,ny,nz)
 *   ushort animCount
 *   anim: utf name, float length, ushort channelCount,
 *         channel = ushort node, byte path(0=translation,1=rotation), ushort keys, keys*time, keys*(3|4)*value
 */
public final class NecroModelData {

    public static final int PATH_TRANSLATION = 0;
    public static final int PATH_ROTATION = 1;

    public static final class Anim {
        public String name;
        public float length;
        public int[] node;
        public int[] path;
        public float[][] times;
        public float[][] values;
    }

    public final int nodeCount;
    public final String[] names;
    public final int[] parent;
    public final float[] restT;   // nodeCount * 3
    public final float[] restR;   // nodeCount * 4 (x,y,z,w)
    public final float[][] quads; // per node: quadCount * 4 * 8 floats
    public final int[][] children;
    public final int[] roots;
    public final Map<String, Anim> anims = new HashMap<>();

    public NecroModelData(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            if (in.readInt() != 0x4E435259) {
                throw new IOException("Bad necromancer model magic");
            }
            nodeCount = in.readUnsignedShort();
            names = new String[nodeCount];
            parent = new int[nodeCount];
            restT = new float[nodeCount * 3];
            restR = new float[nodeCount * 4];
            quads = new float[nodeCount][];
            for (int i = 0; i < nodeCount; i++) {
                names[i] = in.readUTF();
                parent[i] = in.readShort();
                for (int k = 0; k < 3; k++) restT[i * 3 + k] = in.readFloat();
                for (int k = 0; k < 4; k++) restR[i * 4 + k] = in.readFloat();
                int qc = in.readUnsignedShort();
                float[] q = new float[qc * 4 * 8];
                for (int k = 0; k < q.length; k++) q[k] = in.readFloat();
                quads[i] = q;
            }
            int animCount = in.readUnsignedShort();
            for (int a = 0; a < animCount; a++) {
                Anim anim = new Anim();
                anim.name = in.readUTF();
                anim.length = in.readFloat();
                int cc = in.readUnsignedShort();
                anim.node = new int[cc];
                anim.path = new int[cc];
                anim.times = new float[cc][];
                anim.values = new float[cc][];
                for (int c = 0; c < cc; c++) {
                    anim.node[c] = in.readUnsignedShort();
                    anim.path[c] = in.readUnsignedByte();
                    int keys = in.readUnsignedShort();
                    int comps = anim.path[c] == PATH_TRANSLATION ? 3 : 4;
                    float[] t = new float[keys];
                    float[] v = new float[keys * comps];
                    for (int k = 0; k < keys; k++) t[k] = in.readFloat();
                    for (int k = 0; k < v.length; k++) v[k] = in.readFloat();
                    anim.times[c] = t;
                    anim.values[c] = v;
                }
                anims.put(anim.name, anim);
            }
        }

        List<List<Integer>> ch = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) ch.add(new ArrayList<>());
        List<Integer> rootList = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            if (parent[i] >= 0) ch.get(parent[i]).add(i);
            else rootList.add(i);
        }
        children = new int[nodeCount][];
        for (int i = 0; i < nodeCount; i++) {
            List<Integer> l = ch.get(i);
            children[i] = new int[l.size()];
            for (int k = 0; k < children[i].length; k++) children[i][k] = l.get(k);
        }
        roots = new int[rootList.size()];
        for (int i = 0; i < roots.length; i++) roots[i] = rootList.get(i);
    }

    public static NecroModelData loadFromClasspath(String path) {
        try (InputStream in = NecroModelData.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing model resource " + path);
            }
            return new NecroModelData(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read model " + path, e);
        }
    }

    /** Najde uzel podle jmena, ktery ma deti (v modelu je vic uzlu stejneho jmena). */
    public int findParentNodeByName(String name) {
        for (int i = 0; i < nodeCount; i++) {
            if (names[i].equals(name) && children[i].length > 0) return i;
        }
        return -1;
    }

    /** Zapise klidovou pozu do poli outT (n*3) a outR (n*4). */
    public void writeRest(float[] outT, float[] outR) {
        System.arraycopy(restT, 0, outT, 0, restT.length);
        System.arraycopy(restR, 0, outR, 0, restR.length);
    }

    /** Vzorkuje animaci v case t (sekundy) a prepise odpovidajici uzly v outT/outR (ostatni nechava). */
    public void sample(Anim a, float t, float[] outT, float[] outR) {
        for (int c = 0; c < a.node.length; c++) {
            int n = a.node[c];
            float[] times = a.times[c];
            float[] v = a.values[c];
            int comps = a.path[c] == PATH_TRANSLATION ? 3 : 4;
            int last = times.length - 1;
            float[] dst = a.path[c] == PATH_TRANSLATION ? outT : outR;
            int off = n * comps;
            if (last == 0 || t <= times[0]) {
                for (int k = 0; k < comps; k++) dst[off + k] = v[k];
                continue;
            }
            if (t >= times[last]) {
                for (int k = 0; k < comps; k++) dst[off + k] = v[last * comps + k];
                continue;
            }
            int i = 0;
            while (i < last - 1 && times[i + 1] <= t) i++;
            float f = (t - times[i]) / (times[i + 1] - times[i]);
            int o0 = i * comps;
            int o1 = (i + 1) * comps;
            if (comps == 3) {
                for (int k = 0; k < 3; k++) dst[off + k] = v[o0 + k] + (v[o1 + k] - v[o0 + k]) * f;
            } else {
                nlerp(v, o0, v, o1, f, dst, off);
            }
        }
    }

    /** Linearni interpolace kvaternionu (nejkratsi cesta) s normalizaci. */
    public static void nlerp(float[] a, int ao, float[] b, int bo, float f, float[] out, int oo) {
        float dot = a[ao] * b[bo] + a[ao + 1] * b[bo + 1] + a[ao + 2] * b[bo + 2] + a[ao + 3] * b[bo + 3];
        float s = dot < 0f ? -1f : 1f;
        float x = a[ao] * (1 - f) + s * b[bo] * f;
        float y = a[ao + 1] * (1 - f) + s * b[bo + 1] * f;
        float z = a[ao + 2] * (1 - f) + s * b[bo + 2] * f;
        float w = a[ao + 3] * (1 - f) + s * b[bo + 3] * f;
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len < 1e-8f) {
            out[oo] = 0; out[oo + 1] = 0; out[oo + 2] = 0; out[oo + 3] = 1;
            return;
        }
        out[oo] = x / len; out[oo + 1] = y / len; out[oo + 2] = z / len; out[oo + 3] = w / len;
    }

    /** Smicha pozy: out = lerp(a, b, f) pro uzly oznacene v mask (null = vsechny). */
    public void blendPose(float[] aT, float[] aR, float[] bT, float[] bR, float f, boolean[] mask, float[] outT, float[] outR) {
        for (int i = 0; i < nodeCount; i++) {
            if (mask != null && !mask[i]) {
                System.arraycopy(aT, i * 3, outT, i * 3, 3);
                System.arraycopy(aR, i * 4, outR, i * 4, 4);
                continue;
            }
            for (int k = 0; k < 3; k++) outT[i * 3 + k] = aT[i * 3 + k] + (bT[i * 3 + k] - aT[i * 3 + k]) * f;
            nlerp(aR, i * 4, bR, i * 4, f, outR, i * 4);
        }
    }

    /** Vynasobi kvaterniony (out = a * b), vse jako x,y,z,w. */
    public static void qmul(float ax, float ay, float az, float aw, float bx, float by, float bz, float bw, float[] out, int o) {
        out[o]     = aw * bx + ax * bw + ay * bz - az * by;
        out[o + 1] = aw * by - ax * bz + ay * bw + az * bx;
        out[o + 2] = aw * bz + ax * by - ay * bx + az * bw;
        out[o + 3] = aw * bw - ax * bx - ay * by - az * bz;
    }
}
