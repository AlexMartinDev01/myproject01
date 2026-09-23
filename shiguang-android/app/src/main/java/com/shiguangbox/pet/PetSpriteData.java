package com.shiguangbox.pet;

final class PetSpriteData {
    static String data() {
        StringBuilder sb = new StringBuilder(26000);
        sb.append(PetSpriteChunk0.data());
        sb.append(PetSpriteChunk1.data());
        sb.append(PetSpriteChunk2.data());
        sb.append(PetSpriteChunk3.data());
        return sb.toString();
    }
}
