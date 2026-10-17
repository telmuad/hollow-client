import QtQuick
import QtQuick.Window
import QtQuick.Effects
import "components"
import "pages"

// The Hollow Client launcher window: frameless and rounded with its own title bar and shadow, the nebula
// background, a sidebar, and pages that spring in and out.
Window {
    id: app
    width: 1240
    height: 800
    minimumWidth: 1020
    minimumHeight: 680
    visible: true
    color: "transparent"
    flags: Qt.Window | Qt.FramelessWindowHint
    title: "Hollow Client"

    // ---- look (theme.json shared with the game)
    readonly property bool maximized: visibility === Window.Maximized || visibility === Window.FullScreen
    readonly property int margin: maximized ? 0 : 16
    readonly property int corner: maximized ? 0 : 16
    property real hue: 0
    NumberAnimation on hue { from: 0; to: 1; duration: 14000; loops: Animation.Infinite; running: theme.rainbow }
    readonly property color accent: theme.rainbow ? Qt.hsla(hue, 0.85, 0.68, 1) : theme.accent
    readonly property color start: theme.rainbow ? Qt.hsla(hue, 0.85, 0.62, 1) : theme.gradientStart
    readonly property color end: theme.rainbow ? Qt.hsla((hue + 0.35) % 1, 0.85, 0.62, 1) : theme.gradientEnd
    readonly property color base: "#08080e"
    readonly property color text: "#f1f2f7"
    readonly property color dim: "#a3a8b8"
    readonly property color faint: "#6f7486"
    readonly property real speed: Math.max(0.25, theme.speed)
    readonly property int radius: theme.radius
    readonly property string fontFamily: fontLoader.status === FontLoader.Ready ? fontLoader.name : "Segoe UI"
    readonly property string sigilUrl: sigilImage

    // ---- shared state for components
    property real time: 0
    property point mouse: Qt.point(frameWidth / 2, frameHeight / 3)
    property int page: 0
    property int layoutTick: 0
    readonly property alias frameRoot: frame
    readonly property real frameWidth: frame.width
    readonly property real frameHeight: frame.height

    function toggleMaximized() {
        if (maximized) showNormal()
        else showMaximized()
        layoutTick++
    }
    function goTo(index) {
        if (index === page) return
        sounds.play("whoosh")
        page = index
    }
    function openAccountMenu(anchorItem) {
        var p = anchorItem.mapToItem(frame, 0, 0)
        var items = []
        if (backend.signedIn || backend.accountName !== "") {
            items.push({ text: backend.signedIn ? "Signed in as " + backend.accountName : backend.accountName + " (signs in on PLAY)", enabled: false })
            items.push({ separator: true })
            items.push({ text: "Log out", action: "logout", danger: true })
        } else {
            items.push({ text: "Sign in with Microsoft", action: "login" })
        }
        items.push({ separator: true })
        items.push({ text: "Open config.json folder", action: "config" })
        menu.show(Qt.point(p.x, p.y - 6), items)
    }
    /** Show the intro again (Settings preview and the screenshot run). */
    function playIntro() {
        intro.leaving = false
        intro.opacity = 1
        intro.visible = true
        intro.start()
    }
    function presetColor(name, i) {
        var p = theme.presetColors[name]
        return p ? p[i] : (i === 0 ? app.start : app.end)
    }

    onWidthChanged: layoutTick++
    onHeightChanged: layoutTick++

    FontLoader { id: fontLoader; source: fontMedium }
    FontLoader { source: fontSemiBold }
    FontLoader { source: fontBold }

    FrameAnimation {
        running: backend.animatedBackground && app.visible && app.visibility !== Window.Minimized
        onTriggered: app.time += Math.min(0.05, frameTime)
    }

    Connections {
        target: backend
        function onDialog(title, text) { dialog.show(title, text) }
    }
    Connections {
        target: menu
        function onPicked(action) {
            if (action === "login") backend.login()
            else if (action === "logout") backend.logout()
            else if (action === "config") backend.openFolder("config")
        }
    }
    Binding { target: sounds; property: "enabled"; value: backend.uiSounds }

    Item {
        id: frame
        anchors.fill: parent
        anchors.margins: app.margin

        HoverHandler {
            onPointChanged: app.mouse = point.position
        }

        RectangularShadow {
            anchors.fill: parent
            visible: !app.maximized
            radius: app.corner
            blur: 28
            spread: 0
            offset: Qt.vector2d(0, 6)
            color: Qt.rgba(0, 0, 0, 0.62)
        }
        Nebula {
            anchors.fill: parent
            radius: app.corner
        }
        Rectangle {
            anchors.fill: parent
            radius: app.corner
            color: "transparent"
            border.width: 1
            border.color: Qt.rgba(1, 1, 1, 0.08)
        }

        TitleBar {
            id: titleBar
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
        }
        Sidebar {
            id: sidebar
            anchors.left: parent.left
            anchors.top: titleBar.bottom
            anchors.bottom: parent.bottom
        }

        // pages: the one you pick springs in, the old one fades out
        Item {
            id: pages
            anchors.left: sidebar.right
            anchors.right: parent.right
            anchors.top: titleBar.bottom
            anchors.bottom: parent.bottom
            anchors.leftMargin: 8
            anchors.rightMargin: 26
            anchors.topMargin: 10
            anchors.bottomMargin: 24

            Repeater {
                model: [homePage, skyblockPage, wardrobePage, statsPage, settingsPage, logsPage]
                Item {
                    id: slot
                    anchors.fill: parent
                    readonly property bool current: app.page === index
                    property real appearTarget: current ? 1 : 0
                    property real appear: appearTarget
                    Behavior on appear {
                        enabled: !app.instant
                        SpringAnimation { spring: 2.6; damping: 0.42; epsilon: 0.004 }
                    }
                    visible: appear > 0.01
                    opacity: Math.min(1, appear * 1.4)
                    Loader {
                        anchors.fill: parent
                        sourceComponent: modelData
                        active: slot.current || slot.visible || index === 0
                        onLoaded: item.appear = Qt.binding(function() { return slot.appear })
                    }
                }
            }
        }
        Component { id: homePage; HomePage {} }
        Component { id: skyblockPage; SkyBlockPage {} }
        Component { id: wardrobePage; WardrobePage {} }
        Component { id: statsPage; StatsPage {} }
        Component { id: settingsPage; SettingsPage {} }
        Component { id: logsPage; LogsPage {} }

        PopupMenu { id: menu }
        MessageDialog { id: dialog }
        Intro {
            id: intro
            visible: false
            onDone: app.revealed = true
        }
    }

    // resize from the edges (the window has no system frame)
    Repeater {
        model: [
            { e: Qt.LeftEdge, x: 0, y: 1, w: 0, h: 1, c: Qt.SizeHorCursor },
            { e: Qt.RightEdge, x: 1, y: 1, w: 0, h: 1, c: Qt.SizeHorCursor },
            { e: Qt.TopEdge, x: 1, y: 0, w: 1, h: 0, c: Qt.SizeVerCursor },
            { e: Qt.BottomEdge, x: 1, y: 2, w: 1, h: 0, c: Qt.SizeVerCursor },
            { e: Qt.LeftEdge | Qt.TopEdge, x: 0, y: 0, w: 0, h: 0, c: Qt.SizeFDiagCursor },
            { e: Qt.RightEdge | Qt.BottomEdge, x: 2, y: 2, w: 0, h: 0, c: Qt.SizeFDiagCursor },
            { e: Qt.RightEdge | Qt.TopEdge, x: 2, y: 0, w: 0, h: 0, c: Qt.SizeBDiagCursor },
            { e: Qt.LeftEdge | Qt.BottomEdge, x: 0, y: 2, w: 0, h: 0, c: Qt.SizeBDiagCursor }
        ]
        MouseArea {
            enabled: !app.maximized
            readonly property int grip: app.margin + 4
            x: modelData.x === 0 ? 0 : (modelData.x === 2 ? app.width - grip : grip)
            y: modelData.y === 0 ? 0 : (modelData.y === 2 ? app.height - grip : grip)
            width: modelData.w === 1 ? app.width - 2 * grip : grip
            height: modelData.h === 1 ? app.height - 2 * grip : grip
            cursorShape: modelData.c
            onPressed: app.startSystemResize(modelData.e)
        }
    }

    property bool revealed: false
    property bool instant: false
    Component.onCompleted: {
        if (backend.showIntro && !skipIntro) {
            intro.visible = true
            intro.start()
        } else {
            revealed = true
        }
    }
}
