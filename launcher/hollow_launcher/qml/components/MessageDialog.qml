import QtQuick

// A glass message box over a dimmed window. show(title, text).
Item {
    id: dlg
    anchors.fill: parent
    visible: open || box.opacity > 0.01
    property bool open: false
    property string title: ""
    property string text: ""

    function show(t, msg) {
        title = t
        text = msg
        open = true
        sounds.play("whoosh")
    }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(0, 0, 0, dlg.open ? 0.45 : 0)
        Behavior on color { ColorAnimation { duration: 180 } }
        MouseArea { anchors.fill: parent; enabled: dlg.open; onClicked: dlg.open = false }
    }
    GlassPanel {
        id: box
        anchors.centerIn: parent
        width: Math.min(520, parent.width - 80)
        height: col.implicitHeight + 44
        opacity: dlg.open ? 1 : 0
        scale: dlg.open ? 1 : 0.9
        Behavior on opacity { NumberAnimation { duration: 170 } }
        Behavior on scale { SpringAnimation { spring: 4; damping: 0.32; epsilon: 0.002 } }
        Column {
            id: col
            x: 22
            y: 22
            width: parent.width - 44
            spacing: 14
            Text {
                text: dlg.title
                color: app.text
                font.family: app.fontFamily
                font.pixelSize: 18
                font.weight: Font.Bold
            }
            Text {
                width: parent.width
                text: dlg.text
                color: app.dim
                font.family: app.fontFamily
                font.pixelSize: 13
                wrapMode: Text.WordWrap
                lineHeight: 1.2
            }
            HButton {
                anchors.right: parent.right
                text: "OK"
                primary: true
                onClicked: dlg.open = false
            }
        }
    }
}
