import QtQuick

// A small glass menu that pops up with a spring. items: [{ text, action, danger, enabled }].
Item {
    id: menu
    anchors.fill: parent
    visible: open || card.opacity > 0.01
    property bool open: false
    property var items: []
    property point at: Qt.point(0, 0)
    signal picked(string action)

    function show(atPoint, list) {
        items = list
        at = atPoint
        open = true
        sounds.play("whoosh")
    }

    MouseArea {
        anchors.fill: parent
        enabled: menu.open
        onPressed: menu.open = false
    }
    GlassPanel {
        id: card
        x: Math.min(menu.width - width - 8, menu.at.x)
        y: Math.max(8, menu.at.y - height)
        width: 230
        height: col.implicitHeight + 12
        opacity: menu.open ? 1 : 0
        scale: menu.open ? 1 : 0.92
        transformOrigin: Item.BottomLeft
        Behavior on opacity { NumberAnimation { duration: 150 } }
        Behavior on scale { SpringAnimation { spring: 5; damping: 0.35; epsilon: 0.002 } }
        Column {
            id: col
            x: 6
            y: 6
            width: parent.width - 12
            Repeater {
                model: menu.items
                Item {
                    width: col.width
                    height: modelData.separator ? 9 : 34
                    readonly property bool usable: !modelData.separator && modelData.enabled !== false
                    Rectangle {
                        visible: modelData.separator === true
                        anchors.centerIn: parent
                        width: parent.width - 10
                        height: 1
                        color: Qt.rgba(1, 1, 1, 0.08)
                    }
                    Rectangle {
                        anchors.fill: parent
                        radius: app.radius
                        visible: !modelData.separator
                        color: modelData.danger ? Qt.rgba(0.9, 0.28, 0.3, hh.hovered ? 0.25 : 0) : Qt.rgba(1, 1, 1, hh.hovered && usable ? 0.08 : 0)
                    }
                    Text {
                        visible: !modelData.separator
                        x: 12
                        anchors.verticalCenter: parent.verticalCenter
                        text: modelData.text || ""
                        color: !usable ? app.faint : (modelData.danger ? "#ff8f93" : app.text)
                        font.family: app.fontFamily
                        font.pixelSize: 13
                    }
                    HoverHandler { id: hh; cursorShape: usable ? Qt.PointingHandCursor : Qt.ArrowCursor }
                    TapHandler {
                        enabled: usable
                        onTapped: {
                            sounds.play("click")
                            menu.open = false
                            menu.picked(modelData.action)
                        }
                    }
                }
            }
        }
    }
}
