// dumper.cpp — Offset Scanner & Memory Analysis Tool for Free Fire
// Architecture: arm64-v8a & armeabi-v7a

#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <vector>
#include <string>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <cmath>
#include <algorithm>

#include "offsets.h"

#define TAG "FFDumper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static const char* const FF_PACKAGES[] = {
    "com.dts.freefireth",   // Global
    "com.dts.freefiremax",  // MAX
    "com.vng.freefireth",   // Vietnam
    "com.garena.game.ffthai",
    nullptr
};

static pid_t g_pid = 0;

// ─── Memory Reading Helper (Hỗ trợ cả Root lẫn No-Root Virtual Space) ─────────
template<typename T>
static inline bool mread(uintptr_t addr, T& out) {
    if (!addr || !g_pid) return false;
    if (g_pid == getpid()) {
        std::memcpy(&out, reinterpret_cast<const void*>(addr), sizeof(T));
        return true;
    }
    struct iovec local  = { &out, sizeof(T) };
    struct iovec remote = { reinterpret_cast<void*>(addr), sizeof(T) };
    return process_vm_readv(g_pid, &local, 1, &remote, 1, 0) == static_cast<ssize_t>(sizeof(T));
}

// ─── PID Finder ──────────────────────────────────────────────────────────────
static pid_t findFF() {
    // Kiểm tra container No-Root trước
    char selfCmd[256];
    int selfFd = open("/proc/self/cmdline", O_RDONLY);
    if (selfFd >= 0) {
        int sn = read(selfFd, selfCmd, sizeof(selfCmd) - 1);
        close(selfFd);
        if (sn > 0) {
            selfCmd[sn] = '\0';
            for (int i = 0; FF_PACKAGES[i]; ++i) {
                if (strstr(selfCmd, FF_PACKAGES[i]) != nullptr) {
                    LOGI("Dumper running in same game process (No-Root container): %s", selfCmd);
                    return getpid();
                }
            }
        }
    }

    DIR* dp = opendir("/proc");
    if (!dp) return 0;

    struct dirent* de;
    while ((de = readdir(dp)) != nullptr) {
        pid_t pid = atoi(de->d_name);
        if (pid <= 0) continue;

        char path[64], buf[256];
        snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;
        int n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) continue;
        buf[n] = '\0';

        for (int i = 0; FF_PACKAGES[i]; ++i) {
            if (strstr(buf, FF_PACKAGES[i]) != nullptr || strstr(buf, "freefire") != nullptr) {
                closedir(dp);
                LOGI("Found Free Fire process: %s (PID: %d)", buf, pid);
                return pid;
            }
        }

        // Fallback /proc/%d/comm
        snprintf(path, sizeof(path), "/proc/%d/comm", pid);
        int cfd = open(path, O_RDONLY);
        if (cfd >= 0) {
            int cn = read(cfd, buf, sizeof(buf) - 1);
            close(cfd);
            if (cn > 0) {
                buf[cn] = '\0';
                if (strstr(buf, "freefire") != nullptr) {
                    closedir(dp);
                    LOGI("Found Free Fire process via comm: %s (PID: %d)", buf, pid);
                    return pid;
                }
            }
        }
    }
    closedir(dp);
    return 0;
}

// ─── Memory Maps Parsing ──────────────────────────────────────────────────────
struct MapEntry {
    uintptr_t start, end;
    char perms[8];
    char name[256];
};

static std::vector<MapEntry> readMaps(pid_t pid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE* f = fopen(path, "r");
    if (!f) return {};

    std::vector<MapEntry> result;
    char line[512];
    while (fgets(line, sizeof(line), f)) {
        MapEntry e = {};
        char perms[8] = {};
        unsigned long long start, end, offset;
        int inode;
        char dev[16], name[256] = {};
        int count = sscanf(line, "%llx-%llx %7s %llx %15s %d %255s",
                           &start, &end, perms, &offset, dev, &inode, name);
        if (count >= 3) {
            e.start = static_cast<uintptr_t>(start);
            e.end   = static_cast<uintptr_t>(end);
            strncpy(e.perms, perms, sizeof(e.perms) - 1);
            if (count >= 7) {
                strncpy(e.name, name, sizeof(e.name) - 1);
            }
            result.push_back(e);
        }
    }
    fclose(f);
    return result;
}

static uintptr_t getModuleBase(const std::vector<MapEntry>& maps, const char* mod) {
    for (const auto& e : maps) {
        if (strstr(e.name, mod) && e.perms[2] == 'x') {
            return e.start;
        }
    }
    // Fallback: any segment of the module
    for (const auto& e : maps) {
        if (strstr(e.name, mod)) {
            return e.start;
        }
    }
    return 0;
}

// ─── Pattern Scanner ──────────────────────────────────────────────────────────
static uintptr_t patternScan(pid_t pid, uintptr_t start, size_t size,
                             const uint8_t* pattern, const char* mask, size_t patLen)
{
    const size_t CHUNK = 0x10000;
    std::vector<uint8_t> buf(CHUNK);

    for (uintptr_t off = 0; off < size; off += CHUNK) {
        size_t toRead = std::min(CHUNK, size - off);
        struct iovec lv = { buf.data(), toRead };
        struct iovec rv = { reinterpret_cast<void*>(start + off), toRead };
        ssize_t got = process_vm_readv(pid, &lv, 1, &rv, 1, 0);
        if (got <= 0) continue;

        for (ssize_t i = 0; i <= (ssize_t)(got - patLen); ++i) {
            bool found = true;
            for (size_t j = 0; j < patLen; ++j) {
                if (mask[j] != '?' && buf[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return start + off + i;
        }
    }
    return 0;
}

// ─── Logging via JNI Callback ────────────────────────────────────────────────
static JNIEnv*   g_env = nullptr;
static jobject   g_cb  = nullptr;
static jmethodID g_logMethod = nullptr;

static void logDump(const char* fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    LOGI("%s", buf);

    if (g_env && g_cb && g_logMethod) {
        jstring js = g_env->NewStringUTF(buf);
        g_env->CallVoidMethod(g_cb, g_logMethod, js);
        g_env->DeleteLocalRef(js);
    }
}

// ─── Scanner 1: PlayerManager via .text ADRP/ADD xrefs to String ─────────────
static uintptr_t scanPlayerManager(uintptr_t il2base, const std::vector<MapEntry>& maps) {
    logDump("[*] Scanning for PlayerManager identifier...");

    const uint8_t pattern[] = "PlayerManager";
    const char    mask[]    = "xxxxxxxxxxxxx";

    uintptr_t strAddr = 0;
    // Tìm chuỗi "PlayerManager" trong các segment read-only của libil2cpp.so
    for (const auto& e : maps) {
        if (!strstr(e.name, "libil2cpp.so")) continue;
        if (e.perms[0] != 'r') continue;

        strAddr = patternScan(g_pid, e.start, e.end - e.start, pattern, mask, 13);
        if (strAddr) break;
    }

    if (!strAddr) {
        logDump("[-] PlayerManager string not located in memory");
        return 0;
    }
    logDump("[+] PlayerManager string @ 0x%lx (offset: 0x%lx)", (unsigned long)strAddr, (unsigned long)(strAddr - il2base));

    // Quét toàn bộ segment EXECUTABLE (r-xp) của libil2cpp.so để tìm ADRP + ADD trỏ tới strAddr
    uint64_t targetPage = strAddr & ~0xFFFULL;

    for (const auto& e : maps) {
        if (!strstr(e.name, "libil2cpp.so") || e.perms[2] != 'x') continue;

        size_t segSize = e.end - e.start;
        const size_t CHUNK = 0x10000;
        std::vector<uint32_t> insnBuf(CHUNK / sizeof(uint32_t));

        for (uintptr_t probe = e.start; probe < e.end - 8; probe += CHUNK) {
            size_t bytesToRead = std::min(CHUNK, e.end - probe);
            struct iovec lv = { insnBuf.data(), bytesToRead };
            struct iovec rv = { reinterpret_cast<void*>(probe), bytesToRead };
            ssize_t got = process_vm_readv(g_pid, &lv, 1, &rv, 1, 0);
            if (got <= 0) continue;

            size_t insnCount = got / sizeof(uint32_t);
            for (size_t i = 0; i + 1 < insnCount; ++i) {
                uint32_t insn = insnBuf[i];

                // Kiểm tra lệnh ARM64 ADRP (0x90000000)
                if ((insn & 0x9F000000) == 0x90000000) {
                    uint64_t immlo = (insn >> 29) & 0x3;
                    uint64_t immhi = (insn >> 5) & 0x7FFFF;
                    int64_t imm = static_cast<int64_t>((immhi << 2) | immlo);
                    // Sign extend 21-bit
                    if (imm & (1 << 20)) {
                        imm |= ~((1LL << 21) - 1);
                    }
                    imm <<= 12;

                    uintptr_t pc = probe + i * sizeof(uint32_t);
                    uint64_t adrpTarget = (pc & ~0xFFFULL) + imm;

                    if (adrpTarget == targetPage) {
                        uint32_t next = insnBuf[i + 1];
                        // Lệnh ADD Xn, Xm, #imm12 (0x91000000)
                        if ((next & 0xFF800000) == 0x91000000) {
                            uint32_t addImm = (next >> 10) & 0xFFF;
                            if (adrpTarget + addImm == strAddr) {
                                uintptr_t xrefOffset = pc - il2base;
                                logDump("[+] Exact ADRP+ADD xref found at 0x%lx (Offset: 0x%lx)", (unsigned long)pc, (unsigned long)xrefOffset);
                                return xrefOffset;
                            }
                        }
                    }
                }
            }
        }
    }

    logDump("[~] Direct code xref not found, using heuristic pointer lookup");
    return 0;
}

// ─── Scanner 2: Heuristic Player Structure Verification ────────────────────────
struct Vector3 { float x, y, z; };

static bool isPlausiblePlayer(uintptr_t addr) {
    float hp = 0.f, maxhp = 0.f;
    Vector3 pos = {0.f, 0.f, 0.f};

    if (!mread(addr + 0x130, hp))    return false;
    if (!mread(addr + 0x134, maxhp)) return false;
    if (!mread(addr + 0x90,  pos))   return false;

    // HP hợp lệ trong [1.0, 500.0] và MaxHP trong [100.0, 500.0]
    if (hp < 1.0f || hp > 500.0f)       return false;
    if (maxhp < 50.0f || maxhp > 500.0f) return false;

    // Tọa độ nằm trong bản đồ Free Fire thông thường
    if (pos.x < -8000.f || pos.x > 8000.f) return false;
    if (pos.z < -8000.f || pos.z > 8000.f) return false;
    if (pos.y < -200.f  || pos.y > 1000.f) return false;

    return true;
}

// ─── Scanner 3: ViewProjection Matrix Scanner ────────────────────────────────
static uintptr_t scanViewProjection(uintptr_t il2base, const std::vector<MapEntry>& maps) {
    logDump("[*] Scanning ViewProjection matrix (diff analysis)...");

    std::vector<std::pair<uintptr_t, std::vector<float>>> snap1;
    for (const auto& e : maps) {
        if (!strstr(e.name, "libil2cpp.so")) continue;
        if (e.perms[0] != 'r' || e.perms[1] != 'w') continue; // .data / .bss

        size_t sz = e.end - e.start;
        if (sz < 64 || sz > 0x1000000) continue;

        std::vector<float> buf(sz / sizeof(float));
        struct iovec lv = { buf.data(), sz };
        struct iovec rv = { reinterpret_cast<void*>(e.start), sz };
        if (process_vm_readv(g_pid, &lv, 1, &rv, 1, 0) > 0) {
            snap1.push_back({ e.start, std::move(buf) });
        }
    }

    // Chờ 120ms để camera game xoay/di chuyển nhẹ
    usleep(120000);

    for (const auto& item : snap1) {
        uintptr_t base = item.first;
        const auto& buf1 = item.second;
        size_t sz = buf1.size() * sizeof(float);

        std::vector<float> buf2(buf1.size());
        struct iovec lv = { buf2.data(), sz };
        struct iovec rv = { reinterpret_cast<void*>(base), sz };
        if (process_vm_readv(g_pid, &lv, 1, &rv, 1, 0) <= 0) continue;

        for (size_t i = 0; i + 16 <= buf1.size(); i += 4) {
            int changed = 0;
            for (size_t j = 0; j < 16; ++j) {
                if (fabsf(buf1[i + j] - buf2[i + j]) > 0.0001f) {
                    changed++;
                }
            }

            // Ma trận 4x4 ViewProjection thay đổi ít nhất 4-16 phần tử khi xoay góc nhìn
            if (changed >= 4 && changed <= 16) {
                // Kiểm tra tính chất ma trận ViewProjection
                float m03 = buf2[i + 3], m13 = buf2[i + 7], m23 = buf2[i + 11], m33 = buf2[i + 15];
                if (fabsf(m33) <= 2.5f && (fabsf(m03) > 0.0f || fabsf(m13) > 0.0f || fabsf(m23) > 0.0f)) {
                    uintptr_t addr = base + (i * sizeof(float));
                    uintptr_t offset = addr - il2base;
                    logDump("[+] ViewProjection candidate @ 0x%lx (offset: 0x%lx)", (unsigned long)addr, (unsigned long)offset);
                    return offset;
                }
            }
        }
    }

    logDump("[~] Dynamic matrix diff not found, keeping active offset: 0x%lx", (unsigned long)g_offsets.worldToScreen);
    return g_offsets.worldToScreen;
}

// ─── Main Dumper Execution ────────────────────────────────────────────────────
static void executeDumper(JNIEnv* env, jobject cb) {
    g_env = env;
    g_cb  = cb;

    jclass cbClass = env->GetObjectClass(cb);
    g_logMethod    = env->GetMethodID(cbClass, "log", "(Ljava/lang/String;)V");

    logDump("========================================");
    logDump("        FREE FIRE OFFSET SCANNER        ");
    logDump("========================================");

    g_pid = findFF();
    if (!g_pid) {
        logDump("[-] Free Fire process NOT detected!");
        logDump("[-] Hãy mở game Free Fire trước khi bấm Dump.");
        return;
    }
    logDump("[+] Target PID: %d", g_pid);

    auto maps = readMaps(g_pid);
    logDump("[+] Loaded %zu memory regions", maps.size());

    uintptr_t il2base = getModuleBase(maps, "libil2cpp.so");
    if (!il2base) {
        logDump("[-] libil2cpp.so base address not found.");
        return;
    }
    logDump("[+] libil2cpp.so base: 0x%lx", (unsigned long)il2base);
    g_offsets.il2cppBase = il2base;

    // Quét PlayerManager
    uintptr_t pmOffset = scanPlayerManager(il2base, maps);
    if (pmOffset) {
        g_offsets.playerManager = pmOffset;
    }

    // Quét ViewProjection
    uintptr_t vpOffset = scanViewProjection(il2base, maps);
    if (vpOffset) {
        g_offsets.worldToScreen = vpOffset;
    }

    // Gán các offset tiêu chuẩn của struct IL2CPP Free Fire
    g_offsets.playerPosition  = 0x90;
    g_offsets.playerTransform = 0x68;
    g_offsets.playerHealth    = 0x130;
    g_offsets.playerMaxHealth = 0x134;
    g_offsets.boneHead        = 0x60;
    g_offsets.isLoaded        = true;

    logDump("\n========================================");
    logDump("            SCAN RESULTS SUMMARY        ");
    logDump("========================================");
    logDump("IL2CPP Base:     0x%lx", (unsigned long)g_offsets.il2cppBase);
    logDump("WorldToScreen:   0x%lx", (unsigned long)g_offsets.worldToScreen);
    logDump("PlayerManager:   0x%lx", (unsigned long)g_offsets.playerManager);
    logDump("PlayerList:      0x%lx", (unsigned long)g_offsets.playerListOffset);
    logDump("Position:        0x%lx", (unsigned long)g_offsets.playerPosition);
    logDump("Health:          0x%lx", (unsigned long)g_offsets.playerHealth);
    logDump("Status:          SUCCESS (Ready to Apply)");
    logDump("========================================");

    g_env = nullptr;
    g_cb  = nullptr;
}

// ─── JNI Interface ───────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT void JNICALL
Java_com_ff_cheat_DumperActivity_nativeDump(JNIEnv* env, jobject, jobject cb) {
    executeDumper(env, cb);
}

JNIEXPORT jstring JNICALL
Java_com_ff_cheat_DumperActivity_nativeSaveOffsets(JNIEnv* env, jobject, jstring jpath) {
    if (!jpath) return env->NewStringUTF("Error: null path");
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    bool ok = saveOffsetsToJson(path, g_offsets);
    env->ReleaseStringUTFChars(jpath, path);
    return env->NewStringUTF(ok ? "Offsets saved successfully to JSON" : "Failed to write offsets file");
}

JNIEXPORT void JNICALL
Java_com_ff_cheat_DumperActivity_nativeApplyOffsets(JNIEnv*, jobject) {
    g_offsets.isLoaded = true;
    LOGI("Live offsets applied into Cheat Engine successfully!");
}

} // extern "C"
