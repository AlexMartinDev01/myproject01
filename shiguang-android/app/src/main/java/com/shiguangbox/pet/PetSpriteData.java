package com.shiguangbox.pet;

final class PetSpriteData {
    static String data() {
        StringBuilder sb = new StringBuilder();
        sb.append(PetSpriteChunk0.data());
        sb.append(PetSpriteChunk1.data());
        sb.append(PetSpriteChunk2.data());
        sb.append(PetSpriteChunk3.data());
        sb.append(PetSpriteChunk4.data());
        sb.append(PetSpriteChunk5.data());
        return sb.toString();
    }
}
