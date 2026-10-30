package com.necromancermod.client;

/**
 * Vybira a michá animace (idle / walk / projectile / summon) a pocita finalni pozu vsech uzlu.
 * Cista Java, aby sla logika snadno testovat mimo Minecraft.
 */
public final class NecroPoseController {

    public static final int STATE_NONE = 0;
    public static final int STATE_SHOOT = 1;
    public static final int STATE_SUMMON = 2;

    /** Kolik sekund animace chuze odpovida jedne jednotce limbSwing (vanilla: cyklus ~ 9.43 jednotky). */
    private static final float WALK_SECONDS_PER_LIMB_UNIT = 0.3846f / 9.43f;

    private final NecroModelData data;
    private final NecroModelData.Anim idle;
    private final NecroModelData.Anim walk;
    private final NecroModelData.Anim projectile;
    private final NecroModelData.Anim summon;
    private final boolean[] legMask;
    private final int headNode;

    private final float[] aT, aR, bT, bR;
    public final float[] outT, outR;
    private final float[] tmpQ = new float[4];

    public NecroPoseController(NecroModelData data) {
        this.data = data;
        this.idle = data.anims.get("idle");
        this.walk = data.anims.get("walk");
        this.projectile = data.anims.get("projectile");
        this.summon = data.anims.get("summon");
        int n = data.nodeCount;
        aT = new float[n * 3];
        aR = new float[n * 4];
        bT = new float[n * 3];
        bR = new float[n * 4];
        outT = new float[n * 3];
        outR = new float[n * 4];
        headNode = data.findParentNodeByName("head");

        // nohy a sukne: pri strelbe / kouzleni se u nich zachova chuze, kdyz se postava pohybuje
        legMask = new boolean[n];
        if (walk != null) {
            for (int c = 0; c < walk.node.length; c++) {
                String nm = data.names[walk.node[c]];
                if (nm.equals("rightLeg") || nm.equals("leftLeg") || nm.equals("leg_skirt") || nm.equals("lowerslips")) {
                    legMask[walk.node[c]] = true;
                }
            }
        }
        data.writeRest(outT, outR);
    }

    private static float loop(float t, float length) {
        if (length <= 0f) return 0f;
        float r = t % length;
        return r < 0f ? r + length : r;
    }

    /**
     * @param state         STATE_NONE / STATE_SHOOT / STATE_SUMMON
     * @param actionSeconds kolik sekund uz bezi akcni animace (shoot / summon)
     * @param limbSwing     vanilla limbSwing (pro cas animace chuze)
     * @param limbSwingAmount vanilla limbSwingAmount (0..1, jak moc se postava hybe)
     * @param ageInTicks    vek entity v tickach (idle)
     * @param headYawDeg    netHeadYaw ve stupnich
     * @param headPitchDeg  headPitch ve stupnich
     */
    public void update(int state, float actionSeconds, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float headYawDeg, float headPitchDeg) {
        float walkBlend = Math.max(0f, Math.min(1f, limbSwingAmount * 4f));

        NecroModelData.Anim action = null;
        if (state == STATE_SHOOT) action = projectile;
        else if (state == STATE_SUMMON) action = summon;

        data.writeRest(aT, aR);
        if (action != null) {
            data.sample(action, Math.max(0f, Math.min(actionSeconds, action.length)), aT, aR);
        } else if (idle != null) {
            data.sample(idle, loop(ageInTicks / 20f, idle.length), aT, aR);
        }

        if (walk != null && walkBlend > 0.01f) {
            data.writeRest(bT, bR);
            data.sample(walk, loop(limbSwing * WALK_SECONDS_PER_LIMB_UNIT, walk.length), bT, bR);
            data.blendPose(aT, aR, bT, bR, walkBlend, action != null ? legMask : null, outT, outR);
        } else {
            System.arraycopy(aT, 0, outT, 0, aT.length);
            System.arraycopy(aR, 0, outR, 0, aR.length);
        }

        applyHeadLook(headYawDeg, headPitchDeg);
    }

    private void applyHeadLook(float yawDeg, float pitchDeg) {
        if (headNode < 0) return;
        float yaw = (float) Math.toRadians(Math.max(-75f, Math.min(75f, yawDeg)));
        float pitch = (float) Math.toRadians(Math.max(-60f, Math.min(60f, pitchDeg)));
        // Souradnice modelu (glTF) jsou oproti vanilla modelu otocene o 180 stupnu kolem osy Z,
        // proto se yaw i pitch aplikuji se zapornym znamenkem.
        float sy = (float) Math.sin(-yaw / 2f), cy = (float) Math.cos(-yaw / 2f);
        float sp = (float) Math.sin(-pitch / 2f), cp = (float) Math.cos(-pitch / 2f);
        int o = headNode * 4;
        // q = anim * yaw(Y) * pitch(X)
        NecroModelData.qmul(outR[o], outR[o + 1], outR[o + 2], outR[o + 3], 0f, sy, 0f, cy, tmpQ, 0);
        NecroModelData.qmul(tmpQ[0], tmpQ[1], tmpQ[2], tmpQ[3], sp, 0f, 0f, cp, outR, o);
    }
}
