package com.shiguangbox.pet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

public final class PetAssets {
    public static final int IDLE = 0;
    public static final int WAVE_1 = 1;
    public static final int WAVE_2 = 2;
    public static final int WAVE_3 = 3;
    public static final int REMINDER = 4;
    public static final int SUCCESS = 5;
    public static final int HAPPY = 6;
    public static final int TIRED = 7;
    public static final int SLEEP = 8;
    public static final int WAKE = 9;
    public static final int NOTE = 10;

    private static Bitmap spriteSheet;
    private static final Bitmap[] frames = new Bitmap[11];
    private static final int FRAME = 144;
    private static final int COLS = 4;

    private PetAssets() {}

    public static synchronized Bitmap frame(Context context, int index) {
        if (index < 0 || index >= frames.length) index = IDLE;
        if (frames[index] != null && !frames[index].isRecycled()) return frames[index];
        if (spriteSheet == null || spriteSheet.isRecycled()) {
            byte[] raw = Base64.decode(PetSpriteData.data(), Base64.DEFAULT);
            spriteSheet = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        }
        if (spriteSheet == null) return null;
        int x = (index % COLS) * FRAME;
        int y = (index / COLS) * FRAME;
        frames[index] = Bitmap.createBitmap(spriteSheet, x, y, FRAME, FRAME);
        return frames[index];
    }
}
