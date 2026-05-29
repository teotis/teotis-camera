# 参考设备 输出一致性 QA 检查清单

> 设备: 参考设备
> 日期: 2026-05-22
> 目标: 验证预览、保存 JPEG、捕获反馈缩略图三路输出的一致性

## 测试前准备

- [ ] 安装最新 debug 包
- [ ] 确认 `adb logcat` 可用
- [ ] 准备中性光照场景（无强色偏）
- [ ] 清空 `Pictures/OpenCamera` 目录

## 测试用例

### TC-1: 无滤镜无水印 4:3 拍摄（基准）

- [ ] 设置: 默认拍照模式，4:3 画幅，无滤镜，无水印
- [ ] 拍摄一张照片
- [ ] **预期**: 缩略图快速出现（ALLOW_PREVIEW_BITMAP）
- [ ] **预期**: 保存 JPEG 与预览内容基本一致
- [ ] **预期**: pipeline notes 无 `algorithm-render:applied`、`frame-ratio:applied`、`watermark:rendered` 条目
- [ ] 收集: 保存 JPEG 文件 + logcat pipeline notes

### TC-2: Color Lab 强滤镜拍摄

- [ ] 设置: 拍照模式 → 色彩实验室 → 选择一个强滤镜（如自定义高对比度高饱和度滤镜）
- [ ] 拍摄一张照片
- [ ] **预期**: 保存完成前不出现原始预览反馈缩略图（SUPPRESS_UNTIL_SAVED_MEDIA）
- [ ] **预期**: 保存完成后缩略图替换为已处理版本
- [ ] **预期**: pipeline notes 包含 `algorithm-render:applied:<profile-id>`
- [ ] 收集: 保存 JPEG 文件 + logcat pipeline notes

### TC-3: 16:9 画幅拍摄

- [ ] 设置: 拍照模式 → 画幅选择 16:9
- [ ] 拍摄一张照片
- [ ] **预期**: 保存完成前不出现原始预览反馈缩略图
- [ ] **预期**: pipeline notes 包含 `frame-ratio:applied:16:9`
- [ ] **预期**: pipeline notes 包含 `frame-ratio:bounds=` 裁剪参数
- [ ] 收集: 保存 JPEG 文件 + logcat pipeline notes

### TC-4: 水印拍摄

- [ ] 设置: 拍照模式 → 开启水印（经典叠加模板）
- [ ] 拍摄一张照片
- [ ] **预期**: 保存完成前不出现原始预览反馈缩略图
- [ ] **预期**: pipeline notes 包含 `watermark:rendered:classic-overlay`
- [ ] **预期**: 保存 JPEG 底部可见水印文字
- [ ] 收集: 保存 JPEG 文件 + logcat pipeline notes

### TC-5: 4:3 画幅拍摄（无裁剪触发）

- [ ] 设置: 拍照模式 → 4:3 画幅，无滤镜，无水印
- [ ] 拍摄一张照片
- [ ] **预期**: pipeline notes 不包含 `frame-ratio:applied`（4:3 为默认比例，不触发裁剪）
- [ ] 收集: 保存 JPEG 文件 + logcat pipeline notes

### TC-6: 组合场景（滤镜 + 水印 + 16:9）

- [ ] 设置: 拍照模式 → 16:9 画幅 + 色彩实验室滤镜 + 水印
- [ ] 拍摄一张照片
- [ ] **预期**: 保存完成前不出现原始预览反馈缩略图
- [ ] **预期**: pipeline notes 同时包含:
  - `algorithm-render:applied:<profile>`
  - `frame-ratio:applied:16:9`
  - `watermark:rendered:<template>`
- [ ] 收集: 保存 JPEG 文件 + logcat pipeline notes

## 收集清单

每个测试用例需收集:

| 项目 | 说明 |
|------|------|
| 屏幕录制 | 从按下快门到缩略图出现的全过程 |
| 保存 JPEG | `adb pull` 从 `Pictures/OpenCamera` |
| pipeline notes | `adb logcat -s Pipeline` 或应用内调试输出 |
| 缩略图行为 | 记录是否出现原始预览闪烁 |

## 通过标准

- [ ] TC-1 到 TC-6 全部通过
- [ ] 无原始预览反馈在需要后处理的场景中出现
- [ ] 保存后缩略图正确替换为已处理版本
- [ ] pipeline notes 对每个请求的 recipe 部分显示 `applied` 或明确的 `skipped`/`failed` 原因
- [ ] 预览帧与保存 JPEG 内容在 `UseCaseGroup`/`ViewPort` 后明显更接近，剩余差异用样本记录

## 失败记录

| 用例 | 失败现象 | 证据 | 备注 |
|------|----------|------|------|
| | | | |
