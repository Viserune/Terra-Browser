# Terra Browser

Terra Browser 是一款面向 Android 手机和平板的浏览器，交互参考 iOS 和 iPadOS 上的 Safari。网页由 Android System WebView 渲染，工具栏和浮层通过 LiquidKit 取样下方页面，随滚动呈现折射、模糊与高光。

**[下载 Android 1.30 体验版 APK](https://github.com/Viserune/Terra-Browser/releases/download/v1.30/Terra-Browser-1.30-Android.apk)** · [版本说明与源码包](https://github.com/Viserune/Terra-Browser/releases/tag/v1.30)

支持 Android 10 及以上，完整的液态玻璃着色效果需要 Android 13 及以上。

## 浏览体验

- **手机手势**　在底部地址栏左右滑动可切换标签，上划可进入标签总览。从网页边缘拖动可预览前后页面，往回拖可取消。
- **平板布局**　顶部工具栏与标签条常驻。点地址栏时，输入框从中央展开，搜索建议紧接着从玻璃下沿出现。
- **长按菜单**　长按链接可预览目标页面，保持按住并滑向菜单项，松手即可执行。标签卡片也支持长按拖动排序。
- **阅读与整理**　支持书签、阅读列表、标签组、阅读器和页内查找。阅读器可调整底色、字体与字号。

## 从源码构建

使用 JDK 21 和 Android SDK Platform 37。在 Android Studio 打开 `android/`，并保留同级的 `shared/`。也可以在仓库根目录运行：

```sh
cd android
./gradlew :app:assemblePreview
```

Windows PowerShell 使用 `.\gradlew.bat :app:assemblePreview`。生成的 APK 位于 `android/app/build/outputs/apk/preview/app-preview.apk`。

[Android 工程与手势测试说明](android/README.md) · [LiquidKit 上游](https://github.com/egysoloman/LiquidKit) · [第三方许可](shared/notices/THIRD_PARTY_NOTICES.md)
