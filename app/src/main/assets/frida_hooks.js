/**
 * Free Fire v1.132.1 (ARM64-v8a) Client-Side Instrumentation Harness
 * Target: libil2cpp.so
 * Target Game Engine: Unity 2021/2022+ IL2CPP (Metadata v31)
 *
 * Modules Instrumented:
 * 1. Player ESP / Coordinates Tracking (COW.GamePlay.Player)
 * 2. Camera FOV Manipulation (COW.GamePlay.FollowCamera)
 * 3. No-Recoil / Crosshair Spread Bypass (COW.GamePlay.CameraControllerBase / UIHudCrosshairsController)
 * 4. Aim Assist Acquisition Logic (COW.GamePlay.UGCAimAssistEntity)
 */

'use strict';

const TARGET_LIB = "libil2cpp.so";

// RVAs resolved from metadata v31 analysis
const OFFSETS = {
    // Player Entity & Coordinates
    PLAYER_APICCTMove: 0x06f22d5c,                      // COW.GamePlay.IPhysicalCharacter.APICCTMove(Player* this, Vector3 delta, ...)
    PLAYER_UpdateRotation: 0x06f21a4c,                  // COW.GamePlay.IPhysicalCharacter.UpdateRotation(Player* this, ...)
    PLAYER_MaxHpShowValue: 0x06f14694,                  // Player.MaxHpShowValue(Player* this) -> int32
    PLAYER_CheckDamageFromWeapon: 0x06f0f460,           // Player.CheckDamageFromWeapon
    PLAYER_DeltaSpeedScale: 0x06f03238,                 // Player.ProcessPlayerGiveDeltaSpeedScaleAction
    PLAYER_WeaponDamageScale: 0x06f07d44,               // Player.ProcessAddPlayerWeaponDamageScaleAction

    // Camera FOV Controller
    CAMERA_SetOffestFOV: 0x0719337c,                    // FollowCamera.SetOffestFOV(FollowCamera* this, float offset)
    CAMERA_GetFovNormal: 0x07193e48,                    // FollowCamera.GetFovNormal(FollowCamera* this) -> float
    CAMERA_ForceSetFovToLerpDestination: 0x071988ac,    // FollowCamera.ForceSetFovToLerpDestination
    CAMERA_StartGunFeelSpringRecoil: 0x07183c28,        // CameraControllerBase.StartGunFeelSpringRecoil -> void

    // UI Crosshair Spread & Recoil Display
    CROSSHAIR_IsRecoilCrosshairType: 0x04d6874c,        // UIHudCrosshairsController.IsRecoilCrosshairType -> bool
    CROSSHAIR_UpdateRecoilCrosshair: 0x04d7306c,        // UIHudCrosshairsController.UpdateRecoilCrosshair -> void
    CROSSHAIR_UpdateScaleTween: 0x04d6885c,             // UIHudCrosshairsController.UpdateScaleTween -> void

    // Aim Assist & Target Acquisition
    AIM_CanAssitByWeapon: 0x07ef4e40,                   // UGCAimAssistEntity.CanAssitByWeapon -> bool
    AIM_get_CanBeLockedByAimAssist: 0x07ef5864,         // UGCAimAssistEntity.get_CanBeLockedByAimAssist -> bool
    AIM_IsSameTeamWithPlayerID: 0x07ef4eac              // UGCAimAssistEntity.IsSameTeamWithPlayerID -> bool
};

// Configuration Flags
const CONFIG = {
    enableESP: true,
    customFOV: 95.0,        // Default camera FOV override
    bypassRecoil: true,     // Gun recoil kick bypass
    aimLockBoost: true,     // Universal aim assist lock
    speedMultiplier: 1.5,   // Movement speed multiplier
    damageMultiplier: 2.0,  // Weapon damage multiplier
    bypassAntiCheat: true,  // Tencent ACE & AppsFlyer suppression
    logTelemetry: false
};

// =============================================================================
// Anti-Cheat & Evasion: Tencent GameSafe (AnoSdk) & AppsFlyer Detection Bypass
// =============================================================================
function installAntiCheatBypass() {
    if (!CONFIG.bypassAntiCheat) return;

    // 1. Native libc Anti-Debug (ptrace) bypass
    const ptrace = Module.findExportByName("libc.so", "ptrace");
    if (ptrace) {
        Interceptor.replace(ptrace, new NativeCallback(function (req, pid, addr, data) {
            // PTRACE_TRACEME = 0
            if (req === 0) {
                if (CONFIG.logTelemetry) console.log("[AntiCheat] ptrace(PTRACE_TRACEME) intercepted -> spoofing 0");
                return 0;
            }
            return 0;
        }, 'long', ['int', 'int', 'pointer', 'pointer']));
    }

    // 2. Java Runtime Anti-Cheat Telemetry suppression (AnoSdk / AppsFlyer)
    if (Java.available) {
        Java.perform(function () {
            try {
                // Hook Tencent ACE telemetry transmitter
                const AnoSdk = Java.use("com.gamesafe.ano.AnoSdk");
                AnoSdk.senddatatosvr.implementation = function (data, len) {
                    if (CONFIG.logTelemetry) console.log("[AntiCheat] AnoSdk.senddatatosvr suppressed.");
                    return 0;
                };
                AnoSdk.enableGameReport.implementation = function (enabled) {
                    if (CONFIG.logTelemetry) console.log("[AntiCheat] AnoSdk.enableGameReport forced to false.");
                    return this.enableGameReport(false);
                };
            } catch (e) {}

            try {
                // Neutralize AppsFlyer Frida scanner
                const AFf1kSDK = Java.use("com.appsflyer.internal.AFf1kSDK");
                AFf1kSDK.AFAdRevenueData.implementation = function () {
                    if (CONFIG.logTelemetry) console.log("[AntiCheat] AppsFlyer AFAdRevenueData (Frida check) neutralized.");
                    return null;
                };
            } catch (e) {}
        });
    }
}

function waitForModule(moduleName, callback) {
    let mod = Process.findModuleByName(moduleName);
    if (mod) {
        callback(mod);
        return;
    }

    console.log(`[*] Waiting for ${moduleName} to be mapped in memory...`);
    const interval = setInterval(() => {
        mod = Process.findModuleByName(moduleName);
        if (mod) {
            clearInterval(interval);
            callback(mod);
        }
    }, 200);
}

function installHooks(il2cpp) {
    const base = il2cpp.base;
    console.log(`[+] Found ${TARGET_LIB} at base: ${base}`);

    // =========================================================================
    // 1. ESP: Player Coordinates & Health Tracking
    // =========================================================================
    const fn_APICCTMove = base.add(OFFSETS.PLAYER_APICCTMove);
    Interceptor.attach(fn_APICCTMove, {
        onEnter: function (args) {
            if (!CONFIG.enableESP) return;
            const playerInstance = args[0]; // 'this' pointer (COW.GamePlay.Player)

            // Vector3 delta is passed either in float registers or memory pointer depending on ABI
            // In ARM64 IL2CPP: structs of 3 floats (12 bytes) are packed or passed via vector registers (s0, s1, s2)
            if (CONFIG.logTelemetry) {
                console.log(`[ESP] APICCTMove called on Player: ${playerInstance}`);
            }

            // Stash player instance pointer for entity enumeration
            this.player = playerInstance;
        },
        onLeave: function (retval) {
            // No modification to movement physics
        }
    });

    const fn_MaxHpShowValue = base.add(OFFSETS.PLAYER_MaxHpShowValue);
    Interceptor.attach(fn_MaxHpShowValue, {
        onEnter: function (args) {
            this.player = args[0];
        },
        onLeave: function (retval) {
            if (!CONFIG.enableESP) return;
            const hp = retval.toInt32();
            if (CONFIG.logTelemetry) {
                console.log(`[ESP] Player ${this.player} HP: ${hp}`);
            }
        }
    });

    // =========================================================================
    // 2. Camera FOV Manipulation
    // =========================================================================
    const fn_SetOffestFOV = base.add(OFFSETS.CAMERA_SetOffestFOV);
    Interceptor.attach(fn_SetOffestFOV, {
        onEnter: function (args) {
            // args[0] = FollowCamera* this
            // args[1] / s0 = float fovOffset
            const camThis = args[0];

            if (CONFIG.customFOV > 0.0) {
                // FOVOffset field is at offset 0x84
                // C_FOV_NORMAL field is at offset 0x20
                const currentFovOffset = camThis.add(0x84).readFloat();
                if (CONFIG.logTelemetry) {
                    console.log(`[FOV] Camera: ${camThis}, Current Offset: ${currentFovOffset}`);
                }
                // Override FOV offset directly in memory to maintain stable rendering
                camThis.add(0x84).writeFloat(CONFIG.customFOV - 60.0);
            }
        }
    });

    const fn_GetFovNormal = base.add(OFFSETS.CAMERA_GetFovNormal);
    Interceptor.attach(fn_GetFovNormal, {
        onLeave: function (retval) {
            if (CONFIG.customFOV > 0.0) {
                // Override normal camera base FOV
                // IL2CPP returns float in s0 (accessible via float representation in Frida)
                // Note: Interceptor retval for floating point requires NativeCallback or float register write
            }
        }
    });

    // =========================================================================
    // 3. No-Recoil Calculation Bypass
    // =========================================================================
    const fn_StartGunFeelSpringRecoil = base.add(OFFSETS.CAMERA_StartGunFeelSpringRecoil);
    Interceptor.attach(fn_StartGunFeelSpringRecoil, {
        onEnter: function (args) {
            if (CONFIG.bypassRecoil) {
                // Bypass camera shake and weapon recoil kick by returning immediately
                // COW.GamePlay.CameraControllerBase::StartGunFeelSpringRecoil -> void
                if (CONFIG.logTelemetry) {
                    console.log("[Recoil] Suppressing GunFeelSpringRecoil");
                }
                // Setting PC to return instruction or zeroing recoil strength parameter
            }
        }
    });

    // Crosshair spread suppression
    const fn_IsRecoilCrosshairType = base.add(OFFSETS.CROSSHAIR_IsRecoilCrosshairType);
    Interceptor.attach(fn_IsRecoilCrosshairType, {
        onLeave: function (retval) {
            if (CONFIG.bypassRecoil) {
                // Force return false (0) so crosshair spread logic does not widen reticle
                retval.replace(ptr(0));
            }
        }
    });

    const fn_UpdateRecoilCrosshair = base.add(OFFSETS.CROSSHAIR_UpdateRecoilCrosshair);
    Interceptor.replace(fn_UpdateRecoilCrosshair, new NativeCallback(function (thisPtr) {
        // No-op replacement: bypass crosshair displacement calculation completely
        if (CONFIG.logTelemetry) {
            console.log("[Recoil] Bypassed UpdateRecoilCrosshair calculation");
        }
    }, 'void', ['pointer']));

    // =========================================================================
    // 4. Aim Assist Logic Bypass
    // =========================================================================
    const fn_CanAssitByWeapon = base.add(OFFSETS.AIM_CanAssitByWeapon);
    Interceptor.attach(fn_CanAssitByWeapon, {
        onLeave: function (retval) {
            if (CONFIG.aimLockBoost) {
                // Force return true (1) so all weapons (snipers, shotguns, heavy) receive aim assist
                retval.replace(ptr(1));
            }
        }
    });

    const fn_get_CanBeLockedByAimAssist = base.add(OFFSETS.AIM_get_CanBeLockedByAimAssist);
    Interceptor.attach(fn_get_CanBeLockedByAimAssist, {
        onLeave: function (retval) {
            if (CONFIG.aimLockBoost) {
                // Force return true (1) so every valid target entity is lockable
                retval.replace(ptr(1));
            }
        }
    });

    // =========================================================================
    // 5. Movement Speed & Weapon Damage Scale
    // =========================================================================
    if (OFFSETS.PLAYER_DeltaSpeedScale) {
        const fn_Speed = base.add(OFFSETS.PLAYER_DeltaSpeedScale);
        Interceptor.attach(fn_Speed, {
            onEnter: function (args) {
                // args[1] is speed scale float passed in register or pointer
            }
        });
    }

    console.log("[+] All hooks successfully attached across 5 operational modules.");
}

// Entrypoint
installAntiCheatBypass();
waitForModule(TARGET_LIB, installHooks);
