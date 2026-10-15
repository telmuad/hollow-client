import QtQuick

// Navigation: a springy marker slides to the page you pick. The account badge sits at the bottom.
Item {
    id: side
    width: 216
    property var pages: [
        { title: "Home", icon: "home" },
        { title: "Wardrobe", icon: "wardrobe" },
        { title: "Stats", icon: "stats" },
        { title: "Settings", icon: "settings" },
        { title: "Logs", icon: "logs" }
    ]
    readonly property int rowH: 44

    Item {
        id: list
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.leftMargin: 12
        anchors.rightMargin: 12
        y: 14
        height: side.pages.length * side.rowH

        Rectangle {
            id: marker
            width: parent.width
            height: side.rowH - 6
            radius: app.radius + 2
            y: app.page * side.rowH + 3
            Behavior on y { SpringAnimation { spring: 3.6; damping: 0.32; epsilon: 0.2 } }
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: Qt.rgba(app.start.r, app.start.g, app.start.b, 0.30) }
                GradientStop { position: 1; color: Qt.rgba(app.end.r, app.end.g, app.end.b, 0.08) }
            }
            border.width: 1
            border.color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.35)
            Rectangle {
                width: 3
                height: parent.height - 16
                radius: 2
                x: 6
                anchors.verticalCenter: parent.verticalCenter
                color: app.accent
            }
        }

        Repeater {
            model: side.pages
            Item {
                y: index * side.rowH
                width: list.width
                height: side.rowH
                readonly property bool active: app.page === index
                property real h: hh.hovered && !active ? 1 : 0
                Behavior on h { NumberAnimation { duration: 140 } }
                Rectangle {
                    anchors.fill: parent
                    anchors.topMargin: 3
                    anchors.bottomMargin: 3
                    radius: app.radius + 2
                    color: Qt.rgba(1, 1, 1, 0.05 * parent.h)
                }
                Row {
                    x: 20 + 4 * parent.h
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 12
                    Icon {
                        name: modelData.icon
                        color: active ? app.text : app.dim
                        anchors.verticalCenter: parent.verticalCenter
                    }
                    Text {
                        text: modelData.title
                        color: active ? app.text : app.dim
                        font.family: app.fontFamily
                        font.pixelSize: 14
                        font.weight: active ? Font.DemiBold : Font.Medium
                        anchors.verticalCenter: parent.verticalCenter
                    }
                }
                HoverHandler {
                    id: hh
                    cursorShape: Qt.PointingHandCursor
                    onHoveredChanged: if (hovered) sounds.play("hover")
                }
                TapHandler {
                    onTapped: app.goTo(index)
                }
            }
        }
    }

    AccountBadge {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.leftMargin: 12
        anchors.rightMargin: 12
        anchors.bottom: version.top
        anchors.bottomMargin: 12
    }
    Text {
        id: version
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.margins: 16
        text: "Launcher " + backend.launcherVersion + "\nNot affiliated with Mojang or Microsoft"
        color: app.faint
        font.family: app.fontFamily
        font.pixelSize: 11
        wrapMode: Text.WordWrap
        lineHeight: 1.15
    }
}
