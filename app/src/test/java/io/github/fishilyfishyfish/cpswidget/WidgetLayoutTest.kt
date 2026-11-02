package io.github.fishilyfishyfish.cpswidget

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 홈 화면 위젯 레이아웃은 정해진 요소만 쓸 수 있다. 다른 요소(예: View)가 하나라도 있으면
 * 폰에서 "위젯을 추가할 수 없습니다"가 뜨고 빌드는 그냥 통과해버리니 여기서 막는다.
 * https://developer.android.com/develop/ui/views/appwidgets/build-layouts (RemoteViews 지원 클래스)
 */
class WidgetLayoutTest {

    private val allowed = setOf(
        // 레이아웃
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "AdapterViewFlipper", "GridView", "ListView", "StackView", "ViewFlipper",
        // 위젯 (minSdk 26에서 쓸 수 있는 것만)
        "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView",
        "ProgressBar", "TextClock", "TextView",
        // 레이아웃 파일 문법
        "merge", "include", "ViewStub",
    )

    /** 위젯에 쓰는 레이아웃 */
    private val widgetLayouts = listOf("widget_today.xml")

    @Test
    fun widgetLayoutsUseOnlyAllowedViews() {
        val dir = listOf(File("src/main/res/layout"), File("app/src/main/res/layout")).first { it.isDirectory }
        for (name in widgetLayouts) {
            val file = File(dir, name)
            assertTrue("$name 없음", file.isFile)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val all = doc.getElementsByTagName("*")
            val bad = (0 until all.length)
                .map { (all.item(it) as Element).tagName }
                .filter { it !in allowed }
                .distinct()
            assertTrue("$name 에 위젯에서 못 쓰는 요소가 있음: $bad", bad.isEmpty())
        }
    }
}
