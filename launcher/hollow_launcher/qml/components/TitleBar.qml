import QtQuick

// The window's own title bar: drag to move (Windows snapping works), double-click to maximize, and the three
// window buttons.
Item {
    id: bar
    height: 42

    DragHandler {
        target: null
        grabPermissions: PointerHandler.CanTakeOverFromAnything
        onActiveChanged: if (active) app.startSystemMove()
    }
    TapHandler {
        acceptedButtons: Qt.LeftButton
        onDoubleTapped: app.toggleMaximized()
    }

    Row {
        anchors.left: parent.left
        anchors.leftMargin: 16
        anchors.verticalCenter: parent.verticalCenter
        spacing: 10
        Image {
            source: app.sigilUrl
            width: 22
            height: 22
            smooth: true
            mipmap: true
            anchors.verticalCenter: parent.verticalCenter
        }
        Text {
            text: "HOLLOW"
            font.family: app.fontFamily
            font.pixelSize: 13
            font.weight: Font.Bold
            font.letterSpacing: 3
            color: app.text
            anchors.verticalCenter: parent.verticalCenter
        }
        Text {
            text: "CLIENT"
            font.family: app.fontFamily
            font.pixelSize: 13
            font.weight: Font.Medium
            font.letterSpacing: 3
            color: app.accent
            anchors.verticalCenter: parent.verticalCenter
        }
    }

    Row {
        anchors.right: parent.right
        anchors.top: parent.top
        height: parent.height
        Repeater {
            model: [
                { name: "minimize", action: "min" },
                { name: app.maximized ? "restore" : "maximize", action: "max" },
                { name: "close", action: "close" }
            ]
            Item {
                width: 46
                height: bar.height
                readonly property bool isClose: modelData.action === "close"
                Rectangle {
                    anchors.fill: parent
                    color: isClose ? "#e5484d" : Qt.rgba(1, 1, 1, 0.08)
                    opacity: hh.hovered ? 1 : 0
                    radius: isClose && !app.maximized ? 0 : 0
                    Behavior on opacity { NumberAnimation { duration: 120 } }
                }
                Icon {
                    anchors.centerIn: parent
                    name: modelData.name
                    size: 14
                    stroke: 1.3
                    color: hh.hovered ? "#ffffff" : app.dim
                }
                HoverHandler { id: hh }
                TapHandler {
                    onTapped: {
                        if (modelData.action === "min") app.showMinimized()
                        else if (modelData.action === "max") app.toggleMaximized()
                        else app.close()
                    }
                }
            }
        }
    }
}
