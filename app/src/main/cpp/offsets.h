#pragma once
#include <cstdint>
#include <string>

// Cấu trúc lưu trữ offsets của Free Fire v1.132.1 (ARM64-v8a)
// Đã kiểm định Capstone Disassembly 10/10 PASS
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

    // --- Free Fire v1.132.1 Verified Function RVAs (ARM64) ---
    uintptr_t rvaMaxHpShowValue         = 0x06F14694; // COW.GamePlay.Player::MaxHpShowValue
    uintptr_t rvaAPICCTMove             = 0x06F22D5C; // COW.GamePlay.IPhysicalCharacter::APICCTMove
    uintptr_t rvaDeltaSpeedScale        = 0x06F03238; // COW.GamePlay.Player::ProcessPlayerGiveDeltaSpeedScaleAction
    uintptr_t rvaWeaponDamageScale      = 0x06F07D44; // COW.GamePlay.Player::ProcessAddPlayerWeaponDamageScaleAction
    uintptr_t rvaSetOffestFOV           = 0x0719337C; // COW.GamePlay.FollowCamera::SetOffestFOV (FOVOffset +0x84)
    uintptr_t rvaGetFovNormal           = 0x07193E48; // COW.GamePlay.FollowCamera::GetFovNormal
    uintptr_t rvaStartGunFeelRecoil     = 0x07183C28; // COW.GamePlay.CameraControllerBase::StartGunFeelSpringRecoil
    uintptr_t rvaIsRecoilCrosshairType  = 0x04D6874C; // COW.UIHudCrosshairsController::IsRecoilCrosshairType
    uintptr_t rvaUpdateRecoilCrosshair  = 0x04D7306C; // COW.UIHudCrosshairsController::UpdateRecoilCrosshair
    uintptr_t rvaCanAssitByWeapon       = 0x07EF4E40; // COW.GamePlay.UGCAimAssistEntity::CanAssitByWeapon
    uintptr_t rvaCanBeLockedByAimAssist = 0x07EF5864; // COW.GamePlay.UGCAimAssistEntity::get_CanBeLockedByAimAssist
    uintptr_t rvaIsSameTeamWithPlayerID = 0x07EF4EAC; // COW.GamePlay.UGCAimAssistEntity::IsSameTeamWithPlayerID
};

// Global offsets instance dùng chung giữa ffcheat.cpp và dumper.cpp
extern GameOffsets g_offsets;

// Hàm load / save offsets file JSON
bool loadOffsetsFromJson(const char* filepath);
bool saveOffsetsToJson(const char* filepath, const GameOffsets& offsets);
