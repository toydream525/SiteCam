# 高清重画来源

工具：内置 imagegen（未使用 CLI/API fallback）。用户最终选择高清重画，原图放大尝试不采用。原图 `15-first-launch-steps-v2.png` 提供主题和构图参考；应用页面控件不包含在生成图中。

| 步骤 | 生成文件（位于 `/Users/ninja/.codex/generated_images/01a148ff-e97d-7210-a667-2ce6eb1d66e4/`） | 采用文件 | 内容约束摘要 |
| --- | --- | --- | --- |
| 工程 | `exec-54384eaf-bed7-4ca0-882f-c6b13690eb27.png` | `hd/project.png` | 三张原工程卡片、黄色文件夹、清晰中文、浅冷白背景与柔和阴影；保留原主题与层次。 |
| 拍摄 | `exec-b6fa0fca-de5e-4093-92bb-490443845df9.png` | `hd/capture.png` | 采用下面的完整最终提示词；替换前一版粗厚、横屏比例不协调的手机。 |
| 资料 | `exec-9d79d3c6-131d-4025-b8ec-ebac5b5e4da7.png` | `hd/media.png` | 2026年10月、全部/今天/问题、六张真实施工照片及黄色问题标记；清晰细节、等比构图。 |
| 归档 | `exec-64cdca5b-0630-4a1d-9780-ef74d654103d.png` | `hd/archive.png` | 黄色文件夹、施工照片、ZIP文档与锁；真实材质、柔和阴影、浅冷白背景。 |

## 拍摄插图完整最终提示词

```text
Use case: precise-object-edit / high-resolution onboarding illustration.
Input image 1 is the adopted four-step SiteCam onboarding design; use ONLY its second illustration (portrait phone + landscape phone + yellow rotation arrow) for composition and visual meaning. Input image 2 is the rejected redraw: improve its PHONE HARDWARE, do not repeat its squat landscape phone or thick bulky body. Input image 3 is the selected project-card illustration: match this clean white/light-gray backdrop, engineering-yellow accents, soft premium studio lighting, crisp realistic details.
Primary request: redraw the second illustration at high native detail. The two phones must look beautiful, physically believable, contemporary and elegant. Use the SAME slim premium graphite smartphone model in portrait and rotated 90 degrees in landscape; each whole device has an accurate long-to-short ratio around 2.1:1, balanced rounded corners, a fine satin-metal frame, a flat crisp display, very slim consistent bezels, and no bulging edges. Absolutely no square/squat landscape device, no bent or melted frame, no extra nested hardware outline.
Composition: one tall portrait phone on the left in the foreground, a true wide landscape phone behind it extending toward the lower right, overlapping naturally as in the adopted composition. A single bold smooth yellow curved double-ended rotation arrow in the upper-right negative space. Both phone bodies fit entirely in the canvas with generous breathing room and a subtle contact shadow. Near-frontal product view, mechanically straight clean silhouettes.
Screens: believable SiteCam camera app, black UI bars with small neat white camera controls (flash, orientation, settings; shutter and gallery) and crisp actual-looking construction photos showing concrete structure, scaffolding, an excavator and blue sky. Use a visually balanced camera viewfinder rather than huge dead blank black screen area. If the short project label is visible, spell exactly 滨江路改造. Phones have no brand logo. Preserve the camera/engineering meaning.
Output: single illustration only, portrait canvas with aspect ratio 386:445 (about 1168x1346 or higher native resolution); light cool off-white full background, edge-to-edge backdrop. Polished professional app onboarding artwork with sharp construction detail and clean edges.
Avoid: page title, onboarding step heading, next button, dots, explanatory text, border around the image, watermark, extra objects, toy-like chunky phones, tablet proportions, huge rear-camera bumps, fisheye, excessive perspective, mismatched device aspect ratios.
```
