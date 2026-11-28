# Terra Browser

Terra Browser 是一款面向 Android 手机和平板的浏览器，交互参考 iOS 和 iPadOS 上的 Safari。网页由 Android System WebView 渲染，工具栏和浮层通过 LiquidKit 取样下方页面，随滚动呈现折射、模糊与高光。

**[下载 Android 1.36 体验版 APK](https://github.com/Viserune/Terra-Browser/releases/download/v1.36/Terra-Browser-1.36-Android.apk)** · [版本说明与源码包](https://github.com/Viserune/Terra-Browser/releases/tag/v1.36)

支持 Android 10 及以上，完整的液态玻璃着色效果需要 Android 13 及以上。

## 浏览体验

- **手机浏览**　从网页左边缘向右拖可返回，反方向可前进。手指停住时，当前网页仍会更新，往回拖可取消。底部地址栏支持左右切换标签、上划进入总览。地址栏和操作按钮分别读取下方网页的颜色，图标随背景切换深浅。
- **平板布局**　点顶部地址栏，输入框会从原位展开，显示个人收藏。开始输入后可以搜索或切换到已打开的标签。页面菜单从对应按钮展开，轻轻回弹后停住。
- **标签总览**　平板上，当前网页连同工具栏一起缩进自己的预览卡，背景网格随之变清晰。点卡片可从原位展开网页。顶部的加号会展开新标签并聚焦地址栏，输入后出现建议。在 820 dp 宽的竖屏平板上，每行显示三张纵向预览，标题位于下方。
- **侧栏**　按下侧栏按钮时，玻璃会随手指产生反馈。侧栏从屏幕边缘滑入，后方网页逐渐变暗。点空白处即可收回。
- **共享**　平板上点共享按钮会弹出面板，手机上可拖动底部把手。点「选项」后，格式窗口从屏幕下方升起，可选自动、PDF、阅读器视图版 PDF 或网页归档。选中后点左上角关闭，即可发送或保存文件。PDF 支持逐页手写标记、撤销和重做。普通链接可加入书签或阅读列表，「查看更多」里可共享标题和链接或打印网页。
- **长按菜单**　长按链接，预览会从指尖展开。可以滑到菜单项松手执行，也可以先松手，再按住列表滑选。点预览时，当前标签立即加载链接，卡片收回后淡入已绘制的网页。在平板上保持按住并拖离链接，可将网址放到接收拖放的位置。标签卡片支持长按拖动排序。
- **阅读与整理**　支持书签、阅读列表、标签组、阅读器和页内查找。阅读器可调整底色、字体与字号。

## 从源码构建

使用 JDK 21 和 Android SDK Platform 37。在 Android Studio 打开 `android/`，并保留同级的 `shared/`。也可以在仓库根目录运行：

```sh
cd android
./gradlew :app:assemblePreview
```

Windows PowerShell 使用 `.\gradlew.bat :app:assemblePreview`。生成的 APK 位于 `android/app/build/outputs/apk/preview/app-preview.apk`。

[Android 工程与手势测试说明](android/README.md) · [LiquidKit 上游](https://github.com/egysoloman/LiquidKit) · [第三方许可](shared/notices/THIRD_PARTY_NOTICES.md)
