using System.Collections.Generic;
using UnityEngine;

/// <summary>
/// Script C# Unity mẫu: Vẽ Khung Xương (Skeleton ESP) và Tâm ngắm ở Cổ (Neck Aim)
/// Hỗ trợ thao tác Kéo Tâm (Drag Headshot) tự nhiên, chống phát hiện Snap Head.
/// </summary>
public class SkeletonAndDragAim : MonoBehaviour
{
    [Header("--- CÀI ĐẶT HIỂN THỊ ---")]
    public bool enableSkeleton = true;      // Bật/tắt vẽ khung xương
    public bool enableNeckCrosshair = true; // Bật/tắt tâm ở cổ
    public Color skeletonColor = Color.green;
    public Color neckAimColor = Color.red;
    public float boneThickness = 2.0f;
    public float neckCircleRadius = 6.0f;

    [Header("--- KÉO TÂM (DRAG HEADSHOT) ---")]
    [Range(0f, 1f)]
    public float neckToHeadRatio = 0.25f;   // 0 = Chuẩn đốt cổ, 0.25 = Vùng họng/cằm (chuẩn kéo tâm vuốt tay)

    private Camera mainCamera;
    private static Texture2D lineTexture;

    void Start()
    {
        mainCamera = Camera.main;
        if (lineTexture == null)
        {
            lineTexture = new Texture2D(1, 1);
            lineTexture.SetPixel(0, 0, Color.white);
            lineTexture.Apply();
        }
    }

    void OnGUI()
    {
        if (Event.current.type != EventType.Repaint) return;
        if (mainCamera == null) mainCamera = Camera.main;
        if (mainCamera == null) return;

        // Quét các nhân vật Humanoid
        Animator[] targets = FindObjectsOfType<Animator>();

        foreach (var anim in targets)
        {
            if (anim.gameObject == this.gameObject || !anim.isHuman) continue;

            if (enableSkeleton)
            {
                DrawHumanoidSkeleton(anim);
            }

            if (enableNeckCrosshair)
            {
                DrawNeckAimPoint(anim);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 1. TÍNH TOÁN & VẼ TÂM NGAY CỔ (HỖ TRỢ VUỐT KÉO LÊN ĐẦU)
    // ─────────────────────────────────────────────────────────────
    void DrawNeckAimPoint(Animator anim)
    {
        Transform neck = anim.GetBoneTransform(HumanBodyBones.Neck);
        Transform head = anim.GetBoneTransform(HumanBodyBones.Head);

        if (neck == null) return;

        // Điểm ngắm: Đặt tại đốt cổ, hơi nhích nhẹ lên cằm
        Vector3 aimWorldPos = neck.position;
        if (head != null && neckToHeadRatio > 0f)
        {
            aimWorldPos = Vector3.Lerp(neck.position, head.position, neckToHeadRatio);
        }

        Vector3 screenPos = mainCamera.WorldToScreenPoint(aimWorldPos);

        if (screenPos.z > 0)
        {
            float guiY = Screen.height - screenPos.y; // Đảo trục Y cho Unity GUI

            DrawCircle(new Vector2(screenPos.x, guiY), neckCircleRadius, neckAimColor);
            
            // 4 tia định vị tâm ngắm
            DrawLine(new Vector2(screenPos.x - 12, guiY), new Vector2(screenPos.x - 4, guiY), neckAimColor, 2f);
            DrawLine(new Vector2(screenPos.x + 4, guiY), new Vector2(screenPos.x + 12, guiY), neckAimColor, 2f);
            DrawLine(new Vector2(screenPos.x, guiY - 12), new Vector2(screenPos.x, guiY - 4), neckAimColor, 2f);
            DrawLine(new Vector2(screenPos.x, guiY + 4), new Vector2(screenPos.x, guiY + 12), neckAimColor, 2f);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. VẼ TOÀN BỘ KHUNG XƯƠNG (SKELETON ESP)
    // ─────────────────────────────────────────────────────────────
    void DrawHumanoidSkeleton(Animator anim)
    {
        // Nhóm xương cột sống & đầu
        DrawBoneConnection(anim, HumanBodyBones.Head, HumanBodyBones.Neck);
        DrawBoneConnection(anim, HumanBodyBones.Neck, HumanBodyBones.Chest);
        DrawBoneConnection(anim, HumanBodyBones.Chest, HumanBodyBones.Spine);
        DrawBoneConnection(anim, HumanBodyBones.Spine, HumanBodyBones.Hips);

        // Nhóm tay trái
        DrawBoneConnection(anim, HumanBodyBones.Chest, HumanBodyBones.LeftUpperArm);
        DrawBoneConnection(anim, HumanBodyBones.LeftUpperArm, HumanBodyBones.LeftLowerArm);
        DrawBoneConnection(anim, HumanBodyBones.LeftLowerArm, HumanBodyBones.LeftHand);

        // Nhóm tay phải
        DrawBoneConnection(anim, HumanBodyBones.Chest, HumanBodyBones.RightUpperArm);
        DrawBoneConnection(anim, HumanBodyBones.RightUpperArm, HumanBodyBones.RightLowerArm);
        DrawBoneConnection(anim, HumanBodyBones.RightLowerArm, HumanBodyBones.RightHand);

        // Nhóm chân trái
        DrawBoneConnection(anim, HumanBodyBones.Hips, HumanBodyBones.LeftUpperLeg);
        DrawBoneConnection(anim, HumanBodyBones.LeftUpperLeg, HumanBodyBones.LeftLowerLeg);
        DrawBoneConnection(anim, HumanBodyBones.LeftLowerLeg, HumanBodyBones.LeftFoot);

        // Nhóm chân phải
        DrawBoneConnection(anim, HumanBodyBones.Hips, HumanBodyBones.RightUpperLeg);
        DrawBoneConnection(anim, HumanBodyBones.RightUpperLeg, HumanBodyBones.RightLowerLeg);
        DrawBoneConnection(anim, HumanBodyBones.RightLowerLeg, HumanBodyBones.RightFoot);
    }

    void DrawBoneConnection(Animator anim, HumanBodyBones fromBone, HumanBodyBones toBone)
    {
        Transform t1 = anim.GetBoneTransform(fromBone);
        Transform t2 = anim.GetBoneTransform(toBone);
        if (t1 == null || t2 == null) return;

        Vector3 p1 = mainCamera.WorldToScreenPoint(t1.position);
        Vector3 p2 = mainCamera.WorldToScreenPoint(t2.position);

        if (p1.z > 0 && p2.z > 0)
        {
            Vector2 start = new Vector2(p1.x, Screen.height - p1.y);
            Vector2 end = new Vector2(p2.x, Screen.height - p2.y);
            DrawLine(start, end, skeletonColor, boneThickness);
        }
    }

    public static void DrawLine(Vector2 pointA, Vector2 pointB, Color color, float width)
    {
        Matrix4x4 savedMatrix = GUI.matrix;
        Color savedColor = GUI.color;

        GUI.color = color;
        float angle = Vector3.Angle(pointB - pointA, Vector2.right);
        if (pointA.y > pointB.y) angle = -angle;

        GUIUtility.RotateAroundPivot(angle, pointA);
        GUI.DrawTexture(new Rect(pointA.x, pointA.y, (pointB - pointA).magnitude, width), lineTexture);

        GUI.matrix = savedMatrix;
        GUI.color = savedColor;
    }

    public static void DrawCircle(Vector2 center, float radius, Color color)
    {
        int segments = 12;
        float angleStep = 360f / segments;
        for (int i = 0; i < segments; i++)
        {
            float a1 = Mathf.Deg2Rad * (i * angleStep);
            float a2 = Mathf.Deg2Rad * ((i + 1) * angleStep);
            Vector2 p1 = center + new Vector2(Mathf.Cos(a1), Mathf.Sin(a1)) * radius;
            Vector2 p2 = center + new Vector2(Mathf.Cos(a2), Mathf.Sin(a2)) * radius;
            DrawLine(p1, p2, color, 1.5f);
        }
    }
}
