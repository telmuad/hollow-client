import QtQuick

// A text field with an accent rim when focused. `edited(text)` fires when you press Enter or leave the field.
Rectangle {
    id: f
    property alias text: input.text
    property string placeholder: ""
    property alias validator: input.validator
    property alias inputMethodHints: input.inputMethodHints
    property bool monospace: false
    signal edited(string text)

    implicitWidth: 260
    implicitHeight: 34
    radius: app.radius
    color: input.activeFocus ? Qt.rgba(1, 1, 1, 0.09) : Qt.rgba(1, 1, 1, 0.05)
    border.width: 1
    border.color: input.activeFocus ? app.accent : Qt.rgba(1, 1, 1, 0.1)
    Behavior on color { ColorAnimation { duration: 140 } }

    TextInput {
        id: input
        anchors.fill: parent
        anchors.leftMargin: 11
        anchors.rightMargin: 11
        verticalAlignment: TextInput.AlignVCenter
        color: app.text
        selectionColor: app.accent
        selectedTextColor: "#ffffff"
        font.family: f.monospace ? "Consolas" : app.fontFamily
        font.pixelSize: 13
        clip: true
        selectByMouse: true
        onEditingFinished: f.edited(text)
    }
    Text {
        anchors.fill: input
        verticalAlignment: Text.AlignVCenter
        visible: input.text === "" && !input.activeFocus
        text: f.placeholder
        color: app.faint
        font: input.font
        elide: Text.ElideRight
    }
}
