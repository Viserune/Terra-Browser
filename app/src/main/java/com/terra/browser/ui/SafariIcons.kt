package com.terra.browser.ui

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import io.liquidkit.LiquidKitSymbol
import io.liquidkit.LocalLiquidKitContentColor

@Composable
fun TerraIcon(
    imageVector: ImageVector,
    tint: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resolvedTint = if (tint == Color.Unspecified) LocalLiquidKitContentColor.current else tint
    Image(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(resolvedTint),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
fun TerraSymbolIcon(
    symbol: LiquidKitSymbol,
    tint: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) {
    val vector = when (symbol) {
        LiquidKitSymbol.Add -> Icons.Rounded.Add
        LiquidKitSymbol.Ascending -> Icons.Rounded.KeyboardArrowUp
        LiquidKitSymbol.Back -> Icons.Rounded.ChevronLeft
        LiquidKitSymbol.Bookmark,
        LiquidKitSymbol.BookPages -> Icons.Outlined.Bookmarks
        LiquidKitSymbol.Saved -> Icons.Rounded.Bookmark
        LiquidKitSymbol.Check -> Icons.Rounded.Check
        LiquidKitSymbol.CheckCircle -> Icons.Outlined.CheckCircle
        LiquidKitSymbol.CheckCircleFilled -> Icons.Rounded.CheckCircle
        LiquidKitSymbol.Unchecked -> Icons.Outlined.Circle
        LiquidKitSymbol.CheckmarkSeal -> Icons.Outlined.Shield
        LiquidKitSymbol.ChevronDown -> Icons.Rounded.KeyboardArrowDown
        LiquidKitSymbol.ChevronUpDown -> Icons.Rounded.UnfoldMore
        LiquidKitSymbol.CircleHalfFilled -> Icons.Rounded.Contrast
        LiquidKitSymbol.Clipboard -> Icons.Rounded.ContentCopy
        LiquidKitSymbol.Clock,
        LiquidKitSymbol.FilledClock -> Icons.Rounded.Schedule
        LiquidKitSymbol.HistoryClock -> Icons.Rounded.History
        LiquidKitSymbol.Close -> Icons.Rounded.Close
        LiquidKitSymbol.Code -> Icons.Rounded.Code
        LiquidKitSymbol.Descending -> Icons.Rounded.KeyboardArrowDown
        LiquidKitSymbol.Document -> Icons.Outlined.Description
        LiquidKitSymbol.Download,
        LiquidKitSymbol.DownloadSquare -> Icons.Rounded.Download
        LiquidKitSymbol.DownloadCircle,
        LiquidKitSymbol.DownloadCircleFilled -> Icons.Rounded.DownloadForOffline
        LiquidKitSymbol.Eye -> Icons.Rounded.Visibility
        LiquidKitSymbol.Filter -> Icons.Rounded.FilterList
        LiquidKitSymbol.Folder -> Icons.Outlined.Folder
        LiquidKitSymbol.FilledFolder -> Icons.Rounded.Folder
        LiquidKitSymbol.Forward -> Icons.Rounded.ChevronRight
        LiquidKitSymbol.Gear -> Icons.Rounded.Settings
        LiquidKitSymbol.Globe -> Icons.Rounded.Public
        LiquidKitSymbol.Grid,
        LiquidKitSymbol.GridFilled -> Icons.Rounded.GridView
        LiquidKitSymbol.InfoCircle -> Icons.Rounded.Info
        LiquidKitSymbol.Link -> Icons.Rounded.Link
        LiquidKitSymbol.List -> Icons.AutoMirrored.Rounded.List
        LiquidKitSymbol.ListBulletRectangle,
        LiquidKitSymbol.SliderHorizontal -> Icons.Rounded.Tune
        LiquidKitSymbol.Lock -> Icons.Rounded.Lock
        LiquidKitSymbol.Moon -> Icons.Rounded.DarkMode
        LiquidKitSymbol.More -> Icons.Rounded.MoreHoriz
        LiquidKitSymbol.NewFolder -> Icons.Rounded.CreateNewFolder
        LiquidKitSymbol.Palette -> Icons.Rounded.Palette
        LiquidKitSymbol.Pause -> Icons.Rounded.Pause
        LiquidKitSymbol.Person -> Icons.Rounded.Person
        LiquidKitSymbol.PersonCircle,
        LiquidKitSymbol.PersonCircleFilled -> Icons.Rounded.AccountCircle
        LiquidKitSymbol.Play -> Icons.Rounded.PlayArrow
        LiquidKitSymbol.Reload,
        LiquidKitSymbol.ReloadCircle -> Icons.Rounded.Refresh
        LiquidKitSymbol.Rename -> Icons.Rounded.Edit
        LiquidKitSymbol.Safari,
        LiquidKitSymbol.SafariFilled -> Icons.Outlined.Explore
        LiquidKitSymbol.Search -> Icons.Rounded.Search
        LiquidKitSymbol.Share -> Icons.Rounded.Share
        LiquidKitSymbol.Sidebar -> Icons.AutoMirrored.Rounded.ViewSidebar
        LiquidKitSymbol.Sparkles,
        LiquidKitSymbol.WandSparkles -> Icons.Rounded.AutoAwesome
        LiquidKitSymbol.SquareOnSquare,
        LiquidKitSymbol.SquareFillOnSquareFill -> Icons.Rounded.FilterNone
        LiquidKitSymbol.Star -> Icons.Rounded.StarBorder
        LiquidKitSymbol.StarFilled -> Icons.Rounded.Star
        LiquidKitSymbol.Sun -> Icons.Rounded.LightMode
        LiquidKitSymbol.Trash -> Icons.Rounded.DeleteOutline
        LiquidKitSymbol.Warning,
        LiquidKitSymbol.WarningFilled -> Icons.Rounded.Warning
        else -> Icons.Rounded.Circle
    }
    TerraIcon(imageVector = vector, tint = tint, modifier = modifier)
}

@Composable
fun SafariPageSettingsIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Rounded.Tune, tint = color, modifier = modifier)

@Composable
fun SafariOpenBookIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Outlined.Bookmarks, tint = color, modifier = modifier)

@Composable
fun SafariReadingGlassesIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.AutoMirrored.Rounded.ChromeReaderMode, tint = color, modifier = modifier)

@Composable
fun SafariShieldIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Outlined.Shield, tint = color, modifier = modifier)

@Composable
fun SafariTabsIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Rounded.FilterNone, tint = color, modifier = modifier)

@Composable
fun SafariPrivateMaskIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Rounded.VisibilityOff, tint = color, modifier = modifier)

@Composable
fun SafariCompassIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Outlined.Explore, tint = color, modifier = modifier)

@Composable
fun SafariLockIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.Rounded.Lock, tint = color, modifier = modifier)

@Composable
fun SafariSidebarIcon(
    color: Color = LocalLiquidKitContentColor.current,
    modifier: Modifier = Modifier,
) = TerraIcon(imageVector = Icons.AutoMirrored.Rounded.ViewSidebar, tint = color, modifier = modifier)
