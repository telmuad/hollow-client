import QtQuick

// An on/off switch with a springy knob. Emits toggled(newValue); the owner decides what `checked` is.
Item {
    id: sw
    property bool checked: false
    property string text: ""
    property string hint: ""
    signal toggled(bool value)

    implicitWidth: 420
    implicitHeight: Math.max(26, label.implicitHeight + (hint !== "" ? hintText.implicitHeight + 2 : 0))

    Column {
        anchors.left: parent.left
        anchors.right: track.left
        anchors.rightMargin: 16
        anchors.verticalCenter: parent.verticalCenter
        spacing: 2
        Text {
            id: label
            width: parent.width
            text: sw.text
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: 14
            wrapMode: Text.WordWrap
        }
        Text {
            id: hintText
            width: parent.width
            visible: sw.hint !== ""
            text: sw.hint
            color: app.faint
            font.family: app.fontFamily
            font.pixelSize: 12
            wrapMode: Text.WordWrap
        }
    }
    Rectangle {
        id: track
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        width: 42
        height: 24
        radius: 12
        color: sw.checked ? "transparent" : "#353948"
        gradient: sw.checked ? onGradient : null
        property Gradient onGradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: app.start }
            GradientStop { position: 1; color: app.end }
        }
        Rectangle {
            id: knob
            width: 18
            height: 18
            radius: 9
            y: 3
            x: sw.checked ? track.width - width - 3 : 3
            color: "#f4f4fa"
            Behavior on x { SpringAnimation { spring: 6; damping: 0.38; epsilon: 0.2 } }
        }
    }
    HoverHandler { cursorShape: Qt.PointingHandCursor }
    TapHandler {
        onTapped: {
            sounds.play("toggle")
            sw.toggled(!sw.checked)
        }
    }
}
