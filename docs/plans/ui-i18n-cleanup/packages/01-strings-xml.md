# Package 01: strings.xml 资源审计与补全

## Objective
审计 `values/strings.xml` 中残留的英文文本，并补全其他 package 需要的所有新 string 资源。同时同步 `values-en/strings.xml`。

## Prerequisites
Wave 1 — 无依赖，可立即启动。

## Allowed Paths
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Forbidden Paths
- 所有 `*.kt` 源文件
- 所有 `feature/` 和 `core/` 目录
- 所有布局 XML 文件

## Acceptance Criteria
1. `values/strings.xml` 中不存在英文文本（品牌名 `app_name=OpenCamera` 除外）
2. `format_color_tone` 已改为中文: `颜色: %.2f, 色调: %.2f`
3. 所有新增 string ID 已添加，命名遵循现有 snake_case 模式
4. `values-en/strings.xml` 包含所有新增 ID 的英文值
5. 两个 strings.xml 的 `<string name="...">` key 集合完全一致（通过 diff 验证）
6. 无重复 key

## 具体修改

### Part A: 修复 values/strings.xml 现有英文

| Key | Line | Current | Change To |
|---|---|---|---|
| format_color_tone | 285 | `Color: %.2f, Tone: %.2f` | `颜色: %.2f, 色调: %.2f` |

### Part B: 新增 string 资源（values/strings.xml 中文 + values-en/strings.xml 英文）

按逻辑分组插入现有文件，不要全部追加到末尾。每个 key 下方列出中文和英文值。

**镜头标签**（插入在 `lens_back`/`lens_front` 附近）:
| Key | ZH | EN |
|---|---|---|
| lens_wide | 广角 | Wide |
| lens_telephoto | 长焦 | Telephoto |
| lens_periscope | 潜望 | Periscope |

**模式状态 headline**（插入在现有状态字符串附近）:
| Key | ZH | EN |
|---|---|---|
| status_document_pipeline_ready | 文档管线就绪 | Document pipeline ready |
| status_document_mode_inactive | 文档模式未激活 | Document mode inactive |
| status_document_capture_requested | 文档拍摄已请求 | Document capture requested |
| status_document_scan_in_progress | 文档扫描进行中 | Document scan in progress |
| status_document_saved | 文档已保存 | Document saved |
| status_document_capture_failed | 文档拍摄失败 | Document capture failed |
| status_humanistic_pipeline_ready | 人文管线就绪 | Humanistic pipeline ready |
| status_humanistic_mode_active | 人文模式已激活 | Humanistic mode active |
| status_humanistic_mode_inactive | 人文模式未激活 | Humanistic mode inactive |
| status_humanistic_resolution_updated | 人文分辨率已更新 | Humanistic resolution updated |
| status_humanistic_style_updated | 人文风格已更新 | Humanistic style updated |
| status_humanistic_capture_in_progress | 人文拍摄进行中 | Humanistic capture in progress |
| status_humanistic_photo_saved | 人文照片已保存 | Humanistic photo saved |
| status_scenery_pipeline_ready | 风景管线就绪 | Scenery pipeline ready |
| status_scenery_mode_inactive | 风景模式未激活 | Scenery mode inactive |
| status_photo_pipeline_ready | 拍照管线就绪 | Photo pipeline ready |
| status_photo_mode_active | 拍照模式已激活 | Photo mode active |
| status_photo_mode_inactive | 拍照模式未激活 | Photo mode inactive |
| status_photo_resolution_updated | 拍照分辨率已更新 | Photo resolution updated |
| status_photo_quality_updated | 拍照画质已更新 | Photo quality updated |
| status_flash_mode_updated | 闪光灯模式已更新 | Flash mode updated |
| status_portrait_pipeline_ready | 人像管线就绪 | Portrait pipeline ready |
| status_portrait_mode_inactive | 人像模式未激活 | Portrait mode inactive |
| status_pro_pipeline_ready | 专业管线就绪 | Pro pipeline ready |
| status_pro_mode_inactive | 专业模式未激活 | Pro mode inactive |
| status_pro_capture_requested | 专业拍摄已请求 | Pro capture requested |
| status_video_pipeline_ready | 视频管线就绪 | Video pipeline ready |
| status_video_mode_active | 视频模式已激活 | Video mode active |
| status_video_mode_inactive | 视频模式未激活 | Video mode inactive |
| status_recording_in_progress | 录制进行中 | Recording in progress |
| status_recording_requested | 录制已请求 | Recording requested |
| status_recording_failed | 录制失败 | Recording failed |
| status_fullclear_mode_active | 全清模式已激活 | Full Clear mode active |

**模式 UI 标签**:
| Key | ZH | EN |
|---|---|---|
| mode_label_document | 文档 | Document |
| mode_label_humanistic | 人文 | Humanistic |
| mode_label_scenery | 风景 | Scenery |
| mode_label_photo | 拍照 | Photo |
| mode_label_portrait | 人像 | Portrait |
| mode_label_pro | 专业 | Pro |
| mode_label_fullclear | 全清 | Full Clear |
| mode_label_video | 视频 | Video |

**快门按钮标签**:
| Key | ZH | EN |
|---|---|---|
| shutter_label_scan_document | 扫描文档 | Scan Document |
| shutter_label_capture_humanistic | 拍摄人文 | Capture Humanistic |
| shutter_label_capture_scenery | 拍摄风景 | Capture Scenery |
| shutter_label_capture_still | 拍摄静态 | Capture Still |
| shutter_label_capture_portrait | 拍摄人像 | Capture Portrait |
| shutter_label_capture_pro | 专业拍摄 | Capture Pro Still |
| shutter_label_capture_fullclear | 拍摄 | Capture |

**次要操作标签**:
| Key | ZH | EN |
|---|---|---|
| secondary_action_cycle_scan_style | 切换扫描风格 | Cycle Scan Style |
| secondary_action_cycle_humanistic_style | 切换人文风格 | Cycle Humanistic Style |
| secondary_action_cycle_scenery_style | 切换风景风格 | Cycle Scenery Style |
| secondary_action_cycle_portrait_style | 切换人像风格 | Cycle Portrait Style |
| secondary_action_cycle_preset | 切换预设 | Cycle Preset |

**模式 detail 消息**:
| Key | ZH | EN |
|---|---|---|
| detail_humanistic_inactive | 切换回人文模式以继续街拍生活摄影。 | Switch back to Humanistic to resume street-life capture. |
| detail_photo_inactive | 切换回拍照以继续静态拍摄。 | Switch back to photo to resume still capture. |
| detail_video_inactive | 将录制决策交由 Session Kernel 处理。 | Leave recording decisions to the Session Kernel. |
| detail_fullclear_pipeline_ready | 全清拍摄管线就绪。 | Full clear capture pipeline ready. |
| detail_humanistic_capture_progress | 街拍静态请求已被统一拍摄管线接受。 | Street-life still request accepted by the unified shot pipeline. |
| detail_recording_requested | 等待 Session Kernel 启动 %s 录制任务。 | Waiting for Session Kernel to start the %s recording task. |
| detail_video_saved | 视频已保存 | Video saved |

**滤镜预设/风格标签**:
| Key | ZH | EN |
|---|---|---|
| filter_profile_receipt | 收据 | Receipt |
| filter_profile_whiteboard | 白板 | Whiteboard |
| filter_profile_contract | 合同 | Contract |
| filter_profile_archive | 归档 | Archive |
| filter_profile_color_copy | 彩色复印 | Color Copy |
| filter_profile_handheld | 手持 | Handheld |
| filter_profile_tripod | 三脚架 | Tripod |
| filter_profile_balanced | 平衡 | Balanced |
| filter_profile_warm | 暖色 | Warm |
| filter_profile_neutral | 中性 | Neutral |
| filter_profile_night | 夜景 | Night |
| filter_profile_contrast | 对比 | Contrast |
| filter_profile_low_light | 弱光 | Low Light |
| contrast_label_high | 高 | High |
| contrast_label_balanced | 平衡 | Balanced |
| contrast_label_natural | 自然 | Natural |

**变焦标签**:
| Key | ZH | EN |
|---|---|---|
| label_zoom_preset_steps | 预设步进 | Preset steps |
| label_zoom_continuous | 连续 | Continuous |
| label_zoom_unsupported | 不支持 | Unsupported |

**实况照片标签**:
| Key | ZH | EN |
|---|---|---|
| live_photo_still_only | 仅静态 | Still Only |
| live_photo_motion_metadata_only | 仅动态元数据 | Motion Metadata Only |
| live_photo_motion_burned_in | 动态嵌入 | Motion Burned In |
| live_photo_unsupported | 不支持 | Unsupported |
| watermark_still_only | 水印: 仅静态 | Watermark: Still Only |
| watermark_metadata_only | 水印: 仅元数据 | Watermark: Metadata Only |
| watermark_burned_in | 水印: 动态嵌入 | Watermark: Burned In |
| watermark_unsupported | 水印: 不支持 | Watermark: Unsupported |

**Toast/调试消息**:
| Key | ZH | EN |
|---|---|---|
| toast_debug_log_exported | 调试日志已导出: %s | Debug log exported: %s |
| toast_export_failed | 导出失败 | Export failed |
| toast_cleanup_failed | 清理失败 | Cleanup failed |

**滤镜等级标签**:
| Key | ZH | EN |
|---|---|---|
| level_off | 关 | Off |
| level_low | 低 | Low |
| level_medium | 中 | Medium |
| level_high | 高 | High |

**运行时问题标签**:
| Key | ZH | EN |
|---|---|---|
| issue_bind_failure | 绑定失败 | Bind failure |
| issue_preview_stall | 预览停滞 | Preview stalled |
| issue_provider_failure | 提供者故障 | Provider failure |
| issue_camera_recoverable | 相机可恢复错误 | Camera recoverable error |
| issue_camera_fatal | 相机致命错误 | Camera fatal error |
| issue_camera_unavailable | 相机不可用 | Camera unavailable |
| issue_thermal_critical | 严重过热 | Thermal critical |
| issue_runtime_error | 运行时错误 | Runtime error |

## Verification Commands
```bash
# 1. 检查 values/strings.xml 中无英文文本（豁免 app_name 和格式化占位符）
grep -n '>[A-Z][a-z]' app/src/main/res/values/strings.xml | grep -v 'app_name' | grep -v '%' || echo "PASS: no English found"

# 2. 检查两个文件的 key 集合一致
diff <(grep -oP 'name="\K[^"]+' app/src/main/res/values/strings.xml | sort) <(grep -oP 'name="\K[^"]+' app/src/main/res/values-en/strings.xml | sort) || echo "FAIL: key mismatch"

# 3. 检查重复 key
grep -oP 'name="\K[^"]+' app/src/main/res/values/strings.xml | sort | uniq -d | grep . && echo "FAIL: duplicate keys" || echo "PASS: no duplicates"

# 4. XML 格式校验
xmllint --noout app/src/main/res/values/strings.xml && echo "PASS: values/strings.xml valid"
xmllint --noout app/src/main/res/values-en/strings.xml && echo "PASS: values-en/strings.xml valid"
```

## Notes
- `app_name` (OpenCamera) 是品牌名，不翻译
- `format_color_tone` 在 values-en/strings.xml 中保持英文即可
- 新增 string 按逻辑分组插入，不要简单追加到文件末尾
- XML 特殊字符注意转义（`&` → `&amp;`）
- `%s` 和 `%.2f` 等格式化占位符不需要转义
