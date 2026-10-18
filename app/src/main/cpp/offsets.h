#pragma once
#include <cstdint>
#include <string>

// Cấu trúc lưu trữ offsets của Free Fire (hỗ trợ load/save dynamic từ JSON và dumper)
struct GameOffsets {
    uintptr_t il2cppBase        = 0;
    uintptr_t worldToScreen     = 0x2A4B8C; // ViewProjection Matrix
    uintptr_t playerManager     = 0x3C8490; // PlayerManager instance / static pointer
    uintptr_t playerListOffset  = 0x128;    // Offset từ PlayerManager tới m_PlayerList
    uintptr_t localPlayer       = 0x8;      // Offset tới LocalPlayer
    uintptr_t playerTransform   = 0x68;     // Offset tới Transform hoặc Native Transform
    uintptr_t playerPosition    = 0x90;     // Offset Vector3 position
    uintptr_t playerHealth      = 0x130;    // Current HP (float)
    uintptr_t playerMaxHealth   = 0x134;    // Max HP (float)
    uintptr_t boneHead          = 0x60;     // Head bone transform
    bool      isLoaded          = false;
};

// Global offsets instance dùng chung giữa ffcheat.cpp và dumper.cpp
extern GameOffsets g_offsets;

// Hàm load / save offsets file JSON
bool loadOffsetsFromJson(const char* filepath);
bool saveOffsetsToJson(const char* filepath, const GameOffsets& offsets);
