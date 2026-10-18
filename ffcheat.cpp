// libffcheat.so — Free Fire Advanced Cheat Native Engine
// Architecture: arm64-v8a / armeabi-v7a

#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <cstdint>
#include <cstdlib>
#include <cmath>
#include <unistd.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/uio.h>
#include <pthread.h>
#include <vector>
#include <string>
#include <atomic>
#include <fstream>
#include <sstream>

#include "offsets.h"

#define LOG_TAG "FFCheatNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global Offsets definition
GameOffsets g_offsets;

// Danh sách các package name phổ biến của Free Fire
static const char* const FF_PACKAGES[] = {
    "com.dts.freefireth",   // Free Fire Global
    "com.dts.freefiremax",  // Free Fire MAX
    "com.vng.freefireth",   // Free Fire VNG (Vietnam)
    "com.garena.game.ffthai",
    nullptr
};

// ─── Globals & Atomic State ──────────────────────────────────────────────────
static pid_t                g_ffPid       = 0;
static uintptr_t            g_il2cppBase  = 0;
static std::atomic<bool>    g_espOn{true};
static std::atomic<bool>    g_aimbotOn{false};
static std::atomic<bool>    g_fakeLagOn{false};
static std::atomic<int>     g_fakeLagMs{100};
static std::atomic<bool>    g_running{false};
static pthread_t            g_thread;

static std::atomic<float>   g_aimbotFov{180.f};

// Screen dimensions (cập nhật chính xác từ Android WindowManager)
static std::atomic<float>   g_screenW{1920.f};
static std::atomic<float>   g_screenH{1080.f};

// Shared player data buffer (7 floats per player)
#define MAX_PLAYERS 32
struct PlayerData {
    float screenX; // X chân/tâm
    float screenY; // Y chân
    float boxW;
    float boxH;
    float health;  // 0-100%
    float distance;
    float visible; // 1.0f = visible, 0.0f = behind wall
    float headX;   // Tọa độ màn hình đầu (dành riêng cho Aimbot)
    float headY;
};

static PlayerData      g_players[MAX_PLAYERS];
static int             g_playerCount = 0;
static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;

// ─── JSON Helper ─────────────────────────────────────────────────────────────
static uintptr_t parseHexOrDec(const std::string& val) {
    if (val.empty()) return 0;
    try {
        if (val.find("0x") == 0 || val.find("0X") == 0) {
            return std::stoull(val, nullptr, 16);
        }
        return std::stoull(val, nullptr, 10);
    } catch (...) {
        return 0;
    }
}

bool loadOffsetsFromJson(const char* filepath) {
    std::ifstream file(filepath);
    if (!file.is_open()) return false;

    std::string line;
    while (std::getline(file, line)) {
        size_t colon = line.find(':');
        if (colon == std::string::npos) continue;

        std::string key = line.substr(0, colon);
        std::string val = line.substr(colon + 1);

        // Trim quotes and whitespace
        auto clean = [](std::string& s) {
            size_t start = s.find_first_not_of(" \t\r\n\",");
            size_t end = s.find_last_not_of(" \t\r\n\",");
            if (start == std::string::npos) { s = ""; return; }
            s = s.substr(start, end - start + 1);
        };
        clean(key);
        clean(val);

        if (key == "world_to_screen")    g_offsets.worldToScreen = parseHexOrDec(val);
        else if (key == "player_manager") g_offsets.playerManager = parseHexOrDec(val);
        else if (key == "player_list")    g_offsets.playerListOffset = parseHexOrDec(val);
        else if (key == "local_player")   g_offsets.localPlayer = parseHexOrDec(val);
        else if (key == "transform")      g_offsets.playerTransform = parseHexOrDec(val);
        else if (key == "position")       g_offsets.playerPosition = parseHexOrDec(val);
        else if (key == "health")         g_offsets.playerHealth = parseHexOrDec(val);
        else if (key == "max_health")     g_offsets.playerMaxHealth = parseHexOrDec(val);
        else if (key == "bone_head")      g_offsets.boneHead = parseHexOrDec(val);
    }

    g_offsets.isLoaded = true;
    LOGI("Loaded offsets dynamically from JSON: %s", filepath);
    return true;
}

bool saveOffsetsToJson(const char* filepath, const GameOffsets& offsets) {
    FILE* f = fopen(filepath, "w");
    if (!f) return false;

    fprintf(f, "{\n");
    fprintf(f, "  \"world_to_screen\": \"0x%lx\",\n", (unsigned long)offsets.worldToScreen);
    fprintf(f, "  \"player_manager\": \"0x%lx\",\n",  (unsigned long)offsets.playerManager);
    fprintf(f, "  \"player_list\": \"0x%lx\",\n",     (unsigned long)offsets.playerListOffset);
    fprintf(f, "  \"local_player\": \"0x%lx\",\n",    (unsigned long)offsets.localPlayer);
    fprintf(f, "  \"transform\": \"0x%lx\",\n",       (unsigned long)offsets.playerTransform);
    fprintf(f, "  \"position\": \"0x%lx\",\n",        (unsigned long)offsets.playerPosition);
    fprintf(f, "  \"health\": \"0x%lx\",\n",          (unsigned long)offsets.playerHealth);
    fprintf(f, "  \"max_health\": \"0x%lx\",\n",      (unsigned long)offsets.playerMaxHealth);
    fprintf(f, "  \"bone_head\": \"0x%lx\"\n",        (unsigned long)offsets.boneHead);
    fprintf(f, "}\n");
    fclose(f);
    return true;
}

// ─── Memory Reading via process_vm_readv / Direct memcpy (No-Root Virtual Space) ───
template<typename T>
static inline bool memRead(pid_t pid, uintptr_t addr, T& out) {
    if (!addr || !pid) return false;
    // Nếu chạy chung tiến trình (trong Không Gian Ảo No-Root hoặc In-Process)
    if (pid == getpid()) {
        std::memcpy(&out, reinterpret_cast<const void*>(addr), sizeof(T));
        return true;
    }
    iovec local  = { &out, sizeof(T) };
    iovec remote = { reinterpret_cast<void*>(addr), sizeof(T) };
    return process_vm_readv(pid, &local, 1, &remote, 1, 0) == static_cast<ssize_t>(sizeof(T));
}

// ─── Multi-Package PID Finder (Hỗ trợ cả Root lẫn No-Root Virtual Space) ─────────
static pid_t findFFPid() {
    // 1. Kiểm tra nếu đang chạy chung tiến trình trong Không Gian Ảo No-Root (Virtual Container)
    char selfCmd[256];
    int selfFd = open("/proc/self/cmdline", O_RDONLY);
    if (selfFd >= 0) {
        ssize_t sn = read(selfFd, selfCmd, sizeof(selfCmd) - 1);
        close(selfFd);
        if (sn > 0) {
            selfCmd[sn] = '\0';
            for (int i = 0; FF_PACKAGES[i] != nullptr; ++i) {
                if (strstr(selfCmd, FF_PACKAGES[i]) != nullptr) {
                    LOGI("Found Free Fire in current container (No-Root Virtual Space): %s (PID: %d)", selfCmd, getpid());
                    return getpid();
                }
            }
        }
    }

    // 2. Quét danh sách tiến trình hệ thống qua /proc
    DIR* dp = opendir("/proc");
    if (!dp) return 0;

    struct dirent* de;
    char buf[512];
    pid_t found = 0;

    while ((de = readdir(dp)) != nullptr) {
        pid_t pid = atoi(de->d_name);
        if (pid <= 0) continue;

        snprintf(buf, sizeof(buf), "/proc/%d/cmdline", pid);
        int fd = open(buf, O_RDONLY);
        if (fd < 0) continue;

        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) continue;
        buf[n] = '\0';

        for (int i = 0; FF_PACKAGES[i] != nullptr; ++i) {
            if (strstr(buf, FF_PACKAGES[i]) != nullptr) {
                found = pid;
                LOGI("Discovered FF process: %s (PID: %d)", FF_PACKAGES[i], pid);
                break;
            }
        }
        if (found) break;
    }
    closedir(dp);
    return found;
}

// ─── Module Base Resolution ──────────────────────────────────────────────────
static uintptr_t getModuleBase(pid_t pid, const char* modName) {
    if (!pid) return 0;
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);

    FILE* f = fopen(path, "r");
    if (!f) return 0;

    char line[512];
    uintptr_t base = 0;

    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, modName)) {
            base = static_cast<uintptr_t>(strtoull(line, nullptr, 16));
            break;
        }
    }
    fclose(f);
    return base;
}

// ─── Vector & Matrix Math ────────────────────────────────────────────────────
struct Vec3 { float x, y, z; };
struct Matrix4x4 { float m[16]; };

static inline bool worldToScreen(
    const Vec3& pos,
    const Matrix4x4& vp,
    float scrW, float scrH,
    float& outX, float& outY)
{
    // Unity ViewProjection matrix (standard column-major transform)
    float clipX = pos.x * vp.m[0] + pos.y * vp.m[4] + pos.z * vp.m[8]  + vp.m[12];
    float clipY = pos.x * vp.m[1] + pos.y * vp.m[5] + pos.z * vp.m[9]  + vp.m[13];
    float clipW = pos.x * vp.m[3] + pos.y * vp.m[7] + pos.z * vp.m[11] + vp.m[15];

    // Đối tượng ở sau camera hoặc quá gần mặt phẳng cắt
    if (clipW < 0.1f) return false;

    float invW = 1.0f / clipW;
    float ndcX = clipX * invW;
    float ndcY = clipY * invW;

    // Chuyển từ NDC [-1, 1] sang màn hình Android [0, Width] và [0, Height]
    outX = (1.0f + ndcX) * scrW * 0.5f;
    outY = (1.0f - ndcY) * scrH * 0.5f;
    return true;
}

// ─── Cheat Memory Loop ───────────────────────────────────────────────────────
static void* cheatLoop(void*) {
    LOGI("ESP/Aimbot reader thread started");

    while (g_running.load()) {
        if (!g_ffPid || !g_il2cppBase) {
            g_ffPid = findFFPid();
            if (g_ffPid > 0) {
                g_il2cppBase = getModuleBase(g_ffPid, "libil2cpp.so");
                g_offsets.il2cppBase = g_il2cppBase;
            }
            if (!g_ffPid || !g_il2cppBase) {
                usleep(500000); // 500ms retry nếu chưa thấy game
                continue;
            }
        }

        // Đọc ma trận ViewProjection
        Matrix4x4 vp = {};
        uintptr_t vpAddr = g_il2cppBase + g_offsets.worldToScreen;
        if (!memRead(g_ffPid, vpAddr, vp)) {
            // Có thể game vừa khởi động lại hoặc crash
            g_ffPid = 0;
            g_il2cppBase = 0;
            usleep(250000);
            continue;
        }

        // Đọc PlayerManager pointer
        uintptr_t pmAddr = 0;
        memRead(g_ffPid, g_il2cppBase + g_offsets.playerManager, pmAddr);
        if (!pmAddr) {
            usleep(50000);
            continue;
        }

        // Đọc con trỏ danh sách người chơi
        uintptr_t listPtr = 0;
        memRead(g_ffPid, pmAddr + g_offsets.playerListOffset, listPtr);

        // Đọc Local Player
        uintptr_t localPtr = 0;
        memRead(g_ffPid, pmAddr + g_offsets.localPlayer, localPtr);
        Vec3 localPos = {0.f, 0.f, 0.f};
        if (localPtr) {
            uintptr_t localTfm = 0;
            if (memRead(g_ffPid, localPtr + g_offsets.playerTransform, localTfm) && localTfm) {
                memRead(g_ffPid, localTfm + g_offsets.playerPosition, localPos);
            }
        }

        float curW = g_screenW.load();
        float curH = g_screenH.load();

        int count = 0;
        PlayerData localBuffer[MAX_PLAYERS];

        if (listPtr) {
            for (int i = 0; i < MAX_PLAYERS; ++i) {
                uintptr_t playerPtr = 0;
                if (!memRead(g_ffPid, listPtr + (i * sizeof(uintptr_t)), playerPtr)) break;
                if (!playerPtr || playerPtr == localPtr) continue;

                // Tọa độ vị trí World
                uintptr_t tfm = 0;
                Vec3 wPos = {0.f, 0.f, 0.f};
                if (memRead(g_ffPid, playerPtr + g_offsets.playerTransform, tfm) && tfm) {
                    if (!memRead(g_ffPid, tfm + g_offsets.playerPosition, wPos)) continue;
                } else {
                    // Fallback đọc trực tiếp từ playerPtr + positionOffset
                    if (!memRead(g_ffPid, playerPtr + g_offsets.playerPosition, wPos)) continue;
                }

                // Vị trí đầu (ước tính hoặc offset 1.75m phía trên chân)
                Vec3 headPos = { wPos.x, wPos.y + 1.75f, wPos.z };

                float sx = 0.f, sy = 0.f; // Chân
                float hx = 0.f, hy = 0.f; // Đầu
                if (!worldToScreen(wPos,    vp, curW, curH, sx, sy)) continue;
                if (!worldToScreen(headPos, vp, curW, curH, hx, hy)) continue;

                // Máu
                float hp = 100.f, maxHp = 100.f;
                memRead(g_ffPid, playerPtr + g_offsets.playerHealth, hp);
                memRead(g_ffPid, playerPtr + g_offsets.playerMaxHealth, maxHp);
                if (maxHp < 1.0f) maxHp = 100.f;
                if (hp < 0.0f) hp = 0.0f;
                float hpPct = (hp / maxHp) * 100.f;

                // Khoảng cách tới local player
                float dx = wPos.x - localPos.x;
                float dy = wPos.y - localPos.y;
                float dz = wPos.z - localPos.z;
                float dist = sqrtf(dx * dx + dy * dy + dz * dz);

                float boxH = fabsf(sy - hy);
                if (boxH < 2.0f) continue; // Quá xa hoặc lỗi ma trận
                float boxW = boxH * 0.45f;

                // Visible check: trong phạm vi 250m
                float vis = (dist < 250.0f) ? 1.0f : 0.0f;

                localBuffer[count++] = {
                    (sx + hx) * 0.5f, // screenX (tâm giữa)
                    sy,               // screenY (chân)
                    boxW,
                    boxH,
                    hpPct,
                    dist,
                    vis,
                    hx,               // headX
                    hy                // headY
                };

                if (count >= MAX_PLAYERS) break;
            }
        }

        // Cập nhật mảng players an toàn
        pthread_mutex_lock(&g_mutex);
        g_playerCount = count;
        for (int i = 0; i < count; ++i) {
            g_players[i] = localBuffer[i];
        }
        pthread_mutex_unlock(&g_mutex);

        // Chu kỳ quét 16ms (~60 FPS) + độ trễ mô phỏng FakeLag
        int baseSleep = 16000;
        if (g_fakeLagOn.load()) {
            int lagMs = g_fakeLagMs.load();
            if (lagMs > 0 && lagMs <= 2000) {
                baseSleep += (lagMs * 1000);
            }
        }
        usleep(baseSleep);
    }

    LOGI("Cheat thread stopped cleanly");
    return nullptr;
}

// ─── JNI EXPORTS ─────────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT void JNICALL
Java_com_ff_cheat_CheatBridge_setScreenSize(JNIEnv*, jclass, jint width, jint height) {
    if (width > 0 && height > 0) {
        g_screenW.store(static_cast<float>(width));
        g_screenH.store(static_cast<float>(height));
        LOGI("Screen resolution updated: %.0fx%.0f", g_screenW.load(), g_screenH.load());
    }
}

JNIEXPORT jint JNICALL
Java_com_ff_cheat_MainActivity_nativeInject(
        JNIEnv*, jobject,
        jboolean esp, jboolean aimbot, jboolean fakeLag)
{
    g_espOn.store(esp);
    g_aimbotOn.store(aimbot);
    g_fakeLagOn.store(fakeLag);

    g_ffPid = findFFPid();
    if (g_ffPid > 0) {
        g_il2cppBase = getModuleBase(g_ffPid, "libil2cpp.so");
        g_offsets.il2cppBase = g_il2cppBase;
    }

    if (!g_running.load()) {
        g_running.store(true);
        pthread_create(&g_thread, nullptr, cheatLoop, nullptr);
    }

    if (!g_ffPid || !g_il2cppBase) {
        LOGI("Free Fire PID not resolved yet. Watcher loop running in background (pid=%d)", g_ffPid);
        return 1; // 1: Engine đã bật và tự động hook khi game chạy
    }

    LOGI("Injected successfully: pid=%d base=0x%lx", g_ffPid, (unsigned long)g_il2cppBase);
    return 0; // 0: Đã gắn trực tiếp
}

JNIEXPORT void JNICALL
Java_com_ff_cheat_MainActivity_nativeStop(JNIEnv*, jobject) {
    if (g_running.load()) {
        g_running.store(false);
        pthread_join(g_thread, nullptr);
    }
    pthread_mutex_lock(&g_mutex);
    g_playerCount = 0;
    pthread_mutex_unlock(&g_mutex);
    LOGI("Cheat engine stopped");
}

JNIEXPORT jfloatArray JNICALL
Java_com_ff_cheat_CheatBridge_getPlayerPositions(JNIEnv* env, jclass) {
    pthread_mutex_lock(&g_mutex);
    int count = g_playerCount;
    if (count == 0) {
        pthread_mutex_unlock(&g_mutex);
        // Trả về mảng rỗng để ESPView xóa ngay các khung vẽ cũ
        return env->NewFloatArray(0);
    }

    // 7 floats cho mỗi player: [x, y, w, h, hp, dist, vis]
    jfloatArray arr = env->NewFloatArray(count * 7);
    float buf[MAX_PLAYERS * 7];

    for (int i = 0; i < count; ++i) {
        int off = i * 7;
        buf[off + 0] = g_players[i].screenX;
        buf[off + 1] = g_players[i].screenY;
        buf[off + 2] = g_players[i].boxW;
        buf[off + 3] = g_players[i].boxH;
        buf[off + 4] = g_players[i].health;
        buf[off + 5] = g_players[i].distance;
        buf[off + 6] = g_players[i].visible;
    }

    env->SetFloatArrayRegion(arr, 0, count * 7, buf);
    pthread_mutex_unlock(&g_mutex);
    return arr;
}

JNIEXPORT jfloatArray JNICALL
Java_com_ff_cheat_CheatBridge_getAimbotTarget(JNIEnv* env, jclass) {
    if (!g_aimbotOn.load()) return nullptr;

    pthread_mutex_lock(&g_mutex);
    float bestDist = 1e9f;
    float bestX = -1.f, bestY = -1.f;
    float cx = g_screenW.load() * 0.5f;
    float cy = g_screenH.load() * 0.5f;

    // Dynamic Aimbot FOV
    const float fovLimit = g_aimbotFov.load();

    for (int i = 0; i < g_playerCount; ++i) {
        // Khóa trực tiếp vào tọa độ đầu đã tính chính xác
        float hx = g_players[i].headX;
        float hy = g_players[i].headY;

        float dx = hx - cx;
        float dy = hy - cy;
        float distToCrosshair = sqrtf(dx * dx + dy * dy);

        if (distToCrosshair < fovLimit && distToCrosshair < bestDist) {
            bestDist = distToCrosshair;
            bestX = hx;
            bestY = hy;
        }
    }
    pthread_mutex_unlock(&g_mutex);

    if (bestX < 0.f) return nullptr;

    jfloatArray arr = env->NewFloatArray(2);
    float buf[2] = { bestX, bestY };
    env->SetFloatArrayRegion(arr, 0, 2, buf);
    return arr;
}

JNIEXPORT void JNICALL
Java_com_ff_cheat_CheatBridge_setESP(JNIEnv*, jclass, jboolean on) {
    g_espOn.store(on);
}

JNIEXPORT void JNICALL
Java_com_ff_cheat_CheatBridge_setAimbot(JNIEnv*, jclass, jboolean on) {
    g_aimbotOn.store(on);
}

JNIEXPORT void JNICALL
Java_com_ff_cheat_CheatBridge_setAimbotFov(JNIEnv*, jclass, jfloat fov) {
    if (fov > 10.f && fov < 1000.f) {
        g_aimbotFov.store(fov);
    }
}

JNIEXPORT void JNICALL
Java_com_ff_cheat_CheatBridge_setFakeLag(JNIEnv*, jclass, jboolean on, jint lagMs) {
    g_fakeLagOn.store(on);
    g_fakeLagMs.store(lagMs);
}

JNIEXPORT jboolean JNICALL
Java_com_ff_cheat_CheatBridge_loadOffsets(JNIEnv* env, jclass, jstring jpath) {
    if (!jpath) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = loadOffsetsFromJson(path);
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
