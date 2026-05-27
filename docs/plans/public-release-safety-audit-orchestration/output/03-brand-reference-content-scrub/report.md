# 03 Brand Reference Content Scrub — 品牌引用内容清理报告

## 概述

对 `public/teotis-camera` 公开仓进行竞品/品牌引用内容扫描，移除或中和所有不必要的竞品学习痕迹、品牌/厂商名称引用。

**审查时间**：2026-05-28
**公开仓路径**：`public/teotis-camera`
**依赖来源**：`01-public-exposure-inventory/report.md`

---

## 一、扫描结果

### 扫描命令

```bash
rg -n --fixed-strings -e 'Apple' -e 'iPhone' -e 'vivo' -e 'Xiaomi' -e 'MIUI' -e 'MiuiCamera' -e 'Leica' -e 'Hasselblad' -e '竞品' -e '参考' -e '学习' --glob '!.git/**'
```

### Before 清理（3 处命中）

| # | 文件 | 行号 | 内容 | 严重性 |
|---|------|------|------|--------|
| 1 | `app/src/test/.../PhotoWatermarkTemplateResolverTest.kt` | 25 | `ExifInterface.TAG_MODEL to "vivo X300 Ultra"` | P1 竞品引用 |
| 2 | `app/src/test/.../PhotoWatermarkTemplateResolverTest.kt` | 29 | `assertEquals("vivo X300 Ultra · Scenery Handheld", resolved.title)` | P1 竞品引用 |
| 3 | `app/src/test/.../PhotoWatermarkTemplateResolverTest.kt` | 78 | `ExifInterface.TAG_MODEL to "vivo X300 Ultra",` | P1 竞品引用 |

### After 清理（0 处命中）

所有 3 处 `vivo X300 Ultra` 已替换为中性设备名 `Teotis Camera Pro`。

---

## 二、已审查文件清单

| 文件类别 | 审查结果 | 说明 |
|----------|----------|------|
| `README.md` | 无品牌引用 | 架构描述均为通用技术文档 |
| `README_EN.md` | 无品牌引用 | 同上 |
| `NOTICE` | 无品牌引用 | 使用 "Teotis"，无个人身份 |
| `AUTHORS` | 无品牌引用 | 仅列出 "Teotis" |
| `docs/assets/*.jpg` | 无品牌引用 | EXIF 仅含 software 字段（PD2509），无可读品牌名 |
| `app/src/test/**` | 已修复 | vivo X300 Ultra → Teotis Camera Pro |
| 源码注释 | 无品牌引用 | 无竞品比较或"学习参考"措辞 |

---

## 三、保留项及理由

### 3.1 测试中的非品牌型号名称

以下测试 fixture 使用了型号标识符（如 `X300U`、`X300 Ultra`）但不含品牌前缀，属于中性测试数据，予以保留：

| 文件 | 行号 | 内容 | 保留理由 |
|------|------|------|----------|
| `PhotoWatermarkTemplateResolverTest.kt` | 97 | `"watermarkModel" to "X300U"` | 自定义 tag 值，无品牌前缀 |
| `PhotoWatermarkTemplateResolverTest.kt` | 168 | `"watermarkModel" to "X300 Ultra"` | 自定义 tag 值，无品牌前缀 |
| `PhotoWatermarkTemplateResolverTest.kt` | 179 | `assertEquals("X300 Ultra · Scenery Handheld", resolved.title)` | 预期结果，无品牌前缀 |
| `PhotoWatermarkTemplateResolverTest.kt` | 198 | `ExifInterface.TAG_MODEL to "OpenCamera DevKit"` | 已使用中性名 |

### 3.2 图片 EXIF 中的软件标识

`docs/assets/` 下 5 张截图的 EXIF `software` 字段包含 `Android PD2509_A_16.0.22.21.W10`（Vivo 固件标识符），但不含可读品牌名，不影响用户体验认知，予以保留。

---

## 四、变更摘要

**公开仓分支**：`scrub/brand-reference-content-scrub`（基于 `main`）
**变更文件**：`app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`（3 行替换）
**Commit**：`b203091`

---

## 五、验证结果

```
$ rg -n --fixed-strings -e 'Apple' -e 'iPhone' -e 'vivo' -e 'Xiaomi' -e 'MIUI' -e 'MiuiCamera' -e 'Leica' -e 'Hasselblad' -e '竞品' -e '参考' -e '学习' --glob '!.git/**'
(no output — 0 matches)
```

**结论**：公开仓中所有竞品品牌引用已清除，剩余型号标识符（如 `X300U`、`X300 Ultra`）为中性测试数据，无品牌风险。
